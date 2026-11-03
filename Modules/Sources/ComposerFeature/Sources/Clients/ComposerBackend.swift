//
//  ComposerBackend.swift
//  ComposerFeature
//
//  Everything the composer needs from the server, behind one small, mockable interface.
//  Live implementation uses the existing, proven APIClient / QMSClient protocol code.
//

import Foundation
import ComposableArchitecture
import APIClient
import QMSClient
import Models
import ForumCore
import TopicBuilder
import SharedUI

public enum ComposerTarget: Equatable, Sendable {
    case reply(topicId: Int)
    case edit(postId: Int, topicId: Int)
    case qms(dialogId: Int)
    
    public var draftKey: DraftKey {
        switch self {
        case let .reply(topicId): .topicReply(topicId: topicId)
        case let .edit(postId, _): .postEdit(postId: postId)
        case let .qms(dialogId): .qms(dialogId: dialogId)
        }
    }
    
    public var isQMS: Bool { if case .qms = self { true } else { false } }
}

/// Server answer to a publish attempt.
public enum PublishOutcome: Equatable, Sendable {
    case published(postId: Int?, topicId: Int?, offset: Int?)
    /// Server refused the content (not retryable as-is). Message is user facing.
    case rejected(String)
}

public struct UploadEvent: Equatable, Sendable {
    public enum Kind: Equatable, Sendable {
        case progress(Double)
        /// Server computed the file hash (used to detect duplicates).
        case initialized(md5: String)
        case done(serverId: Int)
        case failed(String)
    }
    public var kind: Kind
}

public struct ComposerPreview: Equatable, Sendable {
    public var content: [UITopicType]
    public var attachments: [Attachment]
}

public struct ComposerBackend: Sendable {
    public var publish: @Sendable (_ target: ComposerTarget, _ text: String, _ attachments: [Int], _ editReason: String) async throws -> PublishOutcome
    public var preview: @Sendable (_ target: ComposerTarget, _ text: String, _ attachments: [Int]) async throws -> ComposerPreview
    /// Looks for our message on the server after an uncertain send. Returns the post/message id.
    public var findPublished: @Sendable (_ target: ComposerTarget, _ text: String, _ authorId: Int, _ startedAt: Date) async throws -> Int?
    public var upload: @Sendable (_ fileName: String, _ data: Data, _ isQMS: Bool) -> AsyncThrowingStream<UploadEvent, any Error>
    
    public init(
        publish: @escaping @Sendable (_ target: ComposerTarget, _ text: String, _ attachments: [Int], _ editReason: String) async throws -> PublishOutcome,
        preview: @escaping @Sendable (_ target: ComposerTarget, _ text: String, _ attachments: [Int]) async throws -> ComposerPreview,
        findPublished: @escaping @Sendable (_ target: ComposerTarget, _ text: String, _ authorId: Int, _ startedAt: Date) async throws -> Int?,
        upload: @escaping @Sendable (_ fileName: String, _ data: Data, _ isQMS: Bool) -> AsyncThrowingStream<UploadEvent, any Error>
    ) {
        self.publish = publish
        self.preview = preview
        self.findPublished = findPublished
        self.upload = upload
    }
}

extension ComposerBackend: DependencyKey {
    
    public static var liveValue: ComposerBackend {
        @Dependency(\.apiClient) var api
        @Dependency(\.qmsClient) var qms
        
        return ComposerBackend(
            publish: { target, text, attachments, reason in
                switch target {
                case let .reply(topicId):
                    let response = try await api.sendPost(PostRequest(topicId: topicId, content: text, flag: 0, attachments: attachments))
                    return Self.outcome(response)
                case let .edit(postId, topicId):
                    let response = try await api.editPost(PostEditRequest(
                        postId: postId,
                        reason: reason,
                        data: PostRequest(topicId: topicId, content: text, flag: 0, attachments: attachments)
                    ))
                    return Self.outcome(response)
                case let .qms(dialogId):
                    if attachments.isEmpty {
                        try await qms.sendMessage(dialogId, text)
                    } else {
                        try await qms.sendMessageWithAttachments(dialogId, text, attachments)
                    }
                    return .published(postId: nil, topicId: nil, offset: nil)
                }
            },
            preview: { target, text, attachments in
                let topicId: Int
                let postId: Int
                switch target {
                case let .reply(id): topicId = id; postId = 0
                case let .edit(pid, tid): topicId = tid; postId = pid
                case .qms:
                    // QMS has no server preview: render locally with the same post renderer.
                    let nodes = TopicNodeBuilder(text: text, attachments: []).build()
                    return ComposerPreview(content: nodes, attachments: [])
                }
                let response = try await api.previewPost(PostPreviewRequest(
                    id: postId,
                    post: PostRequest(topicId: topicId, content: text, flag: 0, attachments: attachments)
                ))
                let nodes = TopicNodeBuilder(text: response.content, attachments: response.attachments).build()
                return ComposerPreview(content: nodes, attachments: response.attachments)
            },
            findPublished: { target, text, authorId, startedAt in
                switch target {
                case let .reply(topicId):
                    @Shared(.appSettings) var settings
                    let perPage = max(1, settings.topicPerPage)
                    let jump = try await api.jumpForum(JumpForumRequest(postId: 0, topicId: topicId, postsFilter: .all, type: .last))
                    let topic = try await api.getTopic(topicId, jump.offset, perPage, .all)
                    let candidates = topic.posts.map {
                        PublishedPostCandidate(postId: $0.id, authorId: $0.author.id, createdAt: $0.createdAt, content: $0.content)
                    }
                    return PublicationVerifier.find(submittedText: text, authorId: authorId, startedAt: startedAt, among: candidates)?.postId
                case let .edit(postId, _):
                    // Editing is idempotent: sending the same content twice is harmless,
                    // so "not found" simply allows a re-send.
                    _ = postId
                    return nil
                case let .qms(dialogId):
                    let chat = try await qms.loadChat(dialogId, 0, -30) // newest 30 messages
                    let candidates = chat.messages.map {
                        PublishedPostCandidate(postId: $0.id, authorId: $0.senderId, createdAt: $0.date, content: $0.text)
                    }
                    return PublicationVerifier.find(submittedText: text, authorId: authorId, startedAt: startedAt, among: candidates)?.postId
                }
            },
            upload: { name, data, isQMS in
                AsyncThrowingStream { continuation in
                    let task = Task {
                        do {
                            for try await status in await api.upload(UploadRequest(fileName: name, fileData: data, isQms: isQMS)) {
                                switch status {
                                case let .initialized(md5, _):
                                    continuation.yield(UploadEvent(kind: .initialized(md5: md5)))
                                case let .uploading(percent):
                                    continuation.yield(UploadEvent(kind: .progress(Double(percent) / 100)))
                                case let .done(response):
                                    if let id = try? response.responseInt(at: 2) {
                                        continuation.yield(UploadEvent(kind: .done(serverId: id)))
                                    } else {
                                        continuation.yield(UploadEvent(kind: .failed("Сервер не вернул идентификатор файла")))
                                    }
                                case let .error(reason):
                                    continuation.yield(UploadEvent(kind: .failed(Self.message(for: reason))))
                                @unknown default:
                                    continuation.yield(UploadEvent(kind: .failed("Неизвестная ошибка загрузки")))
                                }
                            }
                            continuation.finish()
                        } catch {
                            continuation.finish(throwing: error)
                        }
                    }
                    continuation.onTermination = { _ in task.cancel() }
                }
            }
        )
    }
    
    static func outcome(_ response: PostSendResponse) -> PublishOutcome {
        switch response {
        case let .success(post):
            return .published(postId: post.id, topicId: post.topicId, offset: post.offset)
        case let .failure(error):
            switch error {
            case .premoderation: return .rejected("Сообщение отправлено на премодерацию")
            case .tooLong: return .rejected("Сообщение слишком длинное")
            case .alreadySent: return .rejected("Такое сообщение уже отправлено")
            case .attach: return .rejected("Сообщение будет присоединено к предыдущему")
            case .unknown: return .rejected("Сервер отклонил сообщение")
            }
        }
    }
    
    static func message(for reason: UploadFailureReason) -> String {
        switch reason {
        case .serverDenied: return "Сервер отклонил загрузку (нет прав)"
        case .fileSizeTooBig: return "Файл слишком большой для 4PDA"
        case .fileNotAllowed, .fileTypeNotAllowed: return "Этот тип файлов запрещён на форуме"
        case let .responseStatus(code): return "Ошибка загрузки (код \(code))"
        case let .other(error): return error.localizedDescription
        @unknown default: return "Ошибка загрузки"
        }
    }
    
    public static var testValue: ComposerBackend {
        ComposerBackend(
            publish: { _, _, _, _ in .published(postId: 1, topicId: 1, offset: 0) },
            preview: { _, text, _ in ComposerPreview(content: TopicNodeBuilder(text: text, attachments: []).build(), attachments: []) },
            findPublished: { _, _, _, _ in nil },
            upload: { _, _, _ in AsyncThrowingStream { $0.yield(UploadEvent(kind: .done(serverId: 1))); $0.finish() } }
        )
    }
    
    public static var previewValue: ComposerBackend { testValue }
}

extension DependencyValues {
    public var composerBackend: ComposerBackend {
        get { self[ComposerBackend.self] }
        set { self[ComposerBackend.self] = newValue }
    }
}
