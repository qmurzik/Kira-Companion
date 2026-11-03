//
//  Draft.swift
//  ForumCore
//

import Foundation

/// Identifies what a draft belongs to. Every context has its own independent draft.
public enum DraftKey: Hashable, Codable, Sendable, CustomStringConvertible {
    case topicReply(topicId: Int)
    case postEdit(postId: Int)
    case newTopic(forumId: Int)
    case qms(dialogId: Int)
    case qmsNewDialog(userId: Int)
    case articleComment(articleId: Int)
    
    /// Stable, filesystem-safe identifier.
    public var storageId: String {
        switch self {
        case let .topicReply(id): "topic-\(id)"
        case let .postEdit(id): "edit-\(id)"
        case let .newTopic(id): "newtopic-\(id)"
        case let .qms(id): "qms-\(id)"
        case let .qmsNewDialog(id): "qmsnew-\(id)"
        case let .articleComment(id): "comment-\(id)"
        }
    }
    
    public var description: String { storageId }
}

/// An attachment referenced by a draft.
public struct DraftAttachment: Hashable, Codable, Sendable, Identifiable {
    public enum State: String, Hashable, Codable, Sendable {
        /// File is stored locally (copied into the drafts folder) and has not been uploaded yet.
        case local
        /// File is uploaded; `serverId` is valid.
        case uploaded
        /// Upload failed; can be retried from `localFileName`.
        case failed
    }
    
    public var id: UUID
    public var fileName: String
    public var byteCount: Int
    public var isImage: Bool
    public var state: State
    public var serverId: Int?
    /// File name inside the draft's attachment folder (nil when only the server copy exists).
    public var localFileName: String?
    
    public init(
        id: UUID = UUID(),
        fileName: String,
        byteCount: Int,
        isImage: Bool,
        state: State,
        serverId: Int? = nil,
        localFileName: String? = nil
    ) {
        self.id = id
        self.fileName = fileName
        self.byteCount = byteCount
        self.isImage = isImage
        self.state = state
        self.serverId = serverId
        self.localFileName = localFileName
    }
}

/// Marker persisted *before* a message is sent. If the app is killed or the network drops while
/// sending, the next launch sees the marker and verifies on the server whether the post was
/// published before allowing another send (prevents duplicates).
public struct PendingSubmission: Hashable, Codable, Sendable {
    public var token: UUID
    public var startedAt: Date
    public var contentFingerprint: String
    
    public init(token: UUID = UUID(), startedAt: Date, contentFingerprint: String) {
        self.token = token
        self.startedAt = startedAt
        self.contentFingerprint = contentFingerprint
    }
}

public struct Draft: Hashable, Codable, Sendable, Identifiable {
    public var key: DraftKey
    public var title: String
    public var text: String
    public var selection: TextRange
    public var attachments: [DraftAttachment]
    public var editReason: String
    /// Human readable context ("Topic name", "Dialog with …") for the drafts list.
    public var contextTitle: String
    public var updatedAt: Date
    public var pendingSubmission: PendingSubmission?
    
    public var id: String { key.storageId }
    
    public init(
        key: DraftKey,
        title: String = "",
        text: String = "",
        selection: TextRange = .caret(0),
        attachments: [DraftAttachment] = [],
        editReason: String = "",
        contextTitle: String = "",
        updatedAt: Date = Date(),
        pendingSubmission: PendingSubmission? = nil
    ) {
        self.key = key
        self.title = title
        self.text = text
        self.selection = selection
        self.attachments = attachments
        self.editReason = editReason
        self.contextTitle = contextTitle
        self.updatedAt = updatedAt
        self.pendingSubmission = pendingSubmission
    }
    
    /// A draft with no meaningful content is not stored.
    public var isEmpty: Bool {
        text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && attachments.isEmpty
            && pendingSubmission == nil
    }
    
    public var preview: String {
        let flat = text.replacingOccurrences(of: "\n", with: " ")
        return String(flat.prefix(160))
    }
}
