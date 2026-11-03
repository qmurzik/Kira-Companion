//
//  SubmissionGuard.swift
//  ForumCore
//
//  Safe publishing of posts / messages.
//
//  Rules implemented here:
//   1. Only one submission per draft may be in flight (double taps are ignored).
//   2. The draft is kept until the server *confirms* the publication.
//   3. If the outcome is unknown (timeout / connection dropped after the request was written),
//      the message is NOT re-sent automatically. The composer first checks the topic for the
//      post (`PublicationVerifier`) and only offers a manual re-send when it is not there.
//

import Foundation

/// Coarse classification of a send failure.
public enum SubmissionFailure: Equatable, Sendable {
    /// The request certainly did not reach the server (offline, not connected, queue full).
    /// Safe to retry.
    case notSent(reason: String)
    /// The server answered with an error (too long, premoderation, flood …). Not retryable as-is.
    case rejected(reason: String)
    /// We don't know whether the server processed the request.
    case uncertain(reason: String)
}

public enum SubmissionPhase: Equatable, Sendable {
    case idle
    case sending(token: UUID)
    /// Outcome unknown; verifying on the server before allowing another attempt.
    case verifying(token: UUID)
    case published(postId: Int?)
    case failed(SubmissionFailure)

    public var isBusy: Bool {
        switch self {
        case .sending, .verifying: true
        default: false
        }
    }
}

/// A tiny value-type state machine; the TCA reducer owns one per composer.
public struct SubmissionGuard: Equatable, Sendable {

    public private(set) var phase: SubmissionPhase = .idle
    /// Fingerprint of the content that was being sent when the outcome became uncertain.
    public private(set) var uncertainFingerprint: String?

    public init() {}

    /// Restores the guard after relaunch when a draft carries a pending-submission marker.
    public init(restoring pending: PendingSubmission?) {
        if let pending {
            phase = .verifying(token: pending.token)
            uncertainFingerprint = pending.contentFingerprint
        }
    }

    /// Starts a submission. Returns `nil` when another submission is in flight or when the
    /// previous attempt with the *same* content is still unresolved.
    public mutating func begin(fingerprint: String) -> UUID? {
        guard !phase.isBusy else { return nil }
        if case .published = phase { return nil }
        let token = UUID()
        phase = .sending(token: token)
        uncertainFingerprint = nil
        return token
    }

    public mutating func succeeded(token: UUID, postId: Int?) {
        guard isCurrent(token) else { return }
        phase = .published(postId: postId)
        uncertainFingerprint = nil
    }

    public mutating func failed(token: UUID, _ failure: SubmissionFailure, fingerprint: String) {
        guard isCurrent(token) else { return }
        switch failure {
        case .uncertain:
            phase = .verifying(token: token)
            uncertainFingerprint = fingerprint
        default:
            phase = .failed(failure)
        }
    }

    /// Result of server-side verification after an uncertain outcome.
    public mutating func verified(token: UUID, foundPostId: Int?) {
        guard case .verifying(let current) = phase, current == token else { return }
        if let foundPostId {
            phase = .published(postId: foundPostId)
        } else {
            phase = .failed(.notSent(reason: "verified-missing"))
        }
        uncertainFingerprint = nil
    }

    /// Verification itself could not be completed (still offline). The user may decide.
    public mutating func verificationUnavailable(token: UUID) {
        guard case .verifying(let current) = phase, current == token else { return }
        phase = .failed(.uncertain(reason: "verification-unavailable"))
    }

    /// Back to editing (e.g. after the user reads an error).
    public mutating func reset() {
        guard !phase.isBusy else { return }
        phase = .idle
    }

    private func isCurrent(_ token: UUID) -> Bool {
        switch phase {
        case let .sending(t), let .verifying(t): t == token
        default: false
        }
    }

    /// Stable fingerprint for duplicate detection (whitespace-insensitive).
    public static func fingerprint(text: String, attachments: [Int]) -> String {
        let normalized = PublicationVerifier.normalize(text)
        var hasher = FNV1a()
        hasher.combine(normalized)
        hasher.combine(attachments.sorted().map(String.init).joined(separator: ","))
        return String(hasher.value, radix: 16)
    }
}

// MARK: - Verification

/// Minimal description of a post as seen on the server.
public struct PublishedPostCandidate: Equatable, Sendable {
    public var postId: Int
    public var authorId: Int
    public var createdAt: Date
    public var content: String

    public init(postId: Int, authorId: Int, createdAt: Date, content: String) {
        self.postId = postId
        self.authorId = authorId
        self.createdAt = createdAt
        self.content = content
    }
}

public enum PublicationVerifier {

    /// Finds our post among the newest posts of the topic.
    ///  - must be written by us,
    ///  - must be created not earlier than shortly before the submission started (clock skew),
    ///  - normalized content must match (the server may merge consecutive posts, so a
    ///    containment check is accepted as well).
    public static func find(
        submittedText: String,
        authorId: Int,
        startedAt: Date,
        among candidates: [PublishedPostCandidate],
        clockSkew: TimeInterval = 10 * 60
    ) -> PublishedPostCandidate? {
        let needle = normalize(submittedText)
        guard !needle.isEmpty else { return nil }
        return candidates
            .filter { $0.authorId == authorId && $0.createdAt >= startedAt.addingTimeInterval(-clockSkew) }
            .sorted { $0.createdAt > $1.createdAt }
            .first { candidate in
                let hay = normalize(candidate.content)
                return hay == needle || hay.contains(needle) || (needle.count > 40 && hay.hasPrefix(String(needle.prefix(40))))
            }
    }

    /// Lower-cases, removes BB tags, collapses whitespace. Makes the comparison resilient to
    /// the server re-formatting tags (e.g. `[b]` → `[B]`, `&quot;` entities).
    public static func normalize(_ text: String) -> String {
        var out = ""
        out.reserveCapacity(text.count)
        var inTag = false
        for ch in text {
            if ch == "[" { inTag = true; continue }
            if ch == "]" && inTag { inTag = false; out.append(" "); continue }
            if !inTag { out.append(ch) }
        }
        let decoded = out
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
            .replacingOccurrences(of: "&#39;", with: "'")
            .replacingOccurrences(of: "<br>", with: " ")
            .replacingOccurrences(of: "<br />", with: " ")
        return decoded
            .lowercased()
            .split(whereSeparator: { $0.isWhitespace })
            .joined(separator: " ")
    }
}

// MARK: - FNV-1a (stable across launches, unlike Hasher)

struct FNV1a {
    private(set) var value: UInt64 = 0xcbf29ce484222325
    mutating func combine(_ string: String) {
        for byte in string.utf8 {
            value ^= UInt64(byte)
            value = value &* 0x100000001b3
        }
        value ^= 0xff
        value = value &* 0x100000001b3
    }
}
