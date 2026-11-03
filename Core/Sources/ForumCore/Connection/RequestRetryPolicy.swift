//
//  RequestRetryPolicy.swift
//  ForumCore
//
//  Per-request retry decisions. Only *idempotent* requests (reads) are retried, a bounded
//  number of times, with exponential backoff. Writes (posting, voting, reputation …) are never
//  retried automatically — the caller receives an "uncertain" error instead.
//

import Foundation

public enum NetworkErrorKind: Equatable, Sendable {
    /// No network path at all.
    case offline
    /// Socket is not (yet) connected; request was not sent.
    case notConnected
    /// Local queue is full; request was not sent.
    case queueOverflow
    /// Request was written but no answer arrived in time.
    case timeout
    /// Socket closed while waiting for the answer.
    case connectionLost
    case dns
    case tls
    case http(status: Int)
    case cancelled
    /// Response could not be decoded (server changed format / partial frame).
    case malformedResponse
    case unknown

    /// Whether the request could have reached the server.
    public var mayHaveBeenProcessed: Bool {
        switch self {
        case .offline, .notConnected, .queueOverflow, .dns, .tls, .cancelled: false
        case .timeout, .connectionLost, .http, .malformedResponse, .unknown: true
        }
    }

    /// Short user facing description (Russian UI is the primary locale of the forum).
    public var userMessage: String {
        switch self {
        case .offline: "Нет подключения к интернету"
        case .notConnected: "Нет соединения с сервером 4PDA"
        case .queueOverflow: "Слишком много запросов, попробуйте ещё раз"
        case .timeout: "Сервер не ответил вовремя"
        case .connectionLost: "Соединение прервано"
        case .dns: "Не удалось найти сервер (DNS)"
        case .tls: "Ошибка защищённого соединения (TLS)"
        case let .http(status): "Ошибка сервера (HTTP \(status))"
        case .cancelled: "Запрос отменён"
        case .malformedResponse: "Сервер вернул неожиданный ответ"
        case .unknown: "Неизвестная ошибка сети"
        }
    }
}

public struct RequestRetryPolicy: Equatable, Sendable {
    public enum Decision: Equatable, Sendable {
        case retry(after: TimeInterval)
        case fail
    }

    public var maxAttempts: Int
    public var backoff: Backoff

    public init(maxAttempts: Int = 3, backoff: Backoff = Backoff(base: 0.4, multiplier: 2.5, maximum: 4, jitter: 0.25)) {
        self.maxAttempts = maxAttempts
        self.backoff = backoff
    }

    /// - Parameters:
    ///   - attempt: number of attempts already made (1 after the first failure).
    public func decision(for kind: NetworkErrorKind, attempt: Int, idempotent: Bool, random: Double = Double.random(in: 0...1)) -> Decision {
        guard attempt < maxAttempts else { return .fail }
        switch kind {
        case .offline, .cancelled, .malformedResponse, .tls:
            return .fail
        case .notConnected, .queueOverflow, .dns:
            // Never reached the server: safe even for writes.
            return .retry(after: backoff.delay(forAttempt: attempt - 1, random: random))
        case .timeout, .connectionLost, .unknown:
            return idempotent ? .retry(after: backoff.delay(forAttempt: attempt - 1, random: random)) : .fail
        case let .http(status):
            let transient = status == 408 || status == 429 || (500...599).contains(status)
            return idempotent && transient ? .retry(after: backoff.delay(forAttempt: attempt - 1, random: random)) : .fail
        }
    }
}

// MARK: - URLSession error classification (images, attachments, web pages)

public enum URLErrorClassifier {
    // Values of NSURLError* constants (stable public ABI).
    public static func classify(domain: String, code: Int) -> NetworkErrorKind {
        guard domain == "NSURLErrorDomain" else { return .unknown }
        switch code {
        case -1009, -1020, -1018: return .offline              // notConnectedToInternet, dataNotAllowed, internationalRoamingOff
        case -1001: return .timeout                            // timedOut
        case -1005, -1004, -1011: return .connectionLost       // networkConnectionLost, cannotConnectToHost, badServerResponse
        case -1003, -1006: return .dns                         // cannotFindHost, dnsLookupFailed
        case -1200, -1201, -1202, -1203, -1204, -1205, -1206: return .tls
        case -999: return .cancelled
        default: return .unknown
        }
    }

    public static func classify(_ error: Error) -> NetworkErrorKind {
        let ns = error as NSError
        if ns.domain == "NSURLErrorDomain" { return classify(domain: ns.domain, code: ns.code) }
        if error is CancellationError { return .cancelled }
        return .unknown
    }
}
