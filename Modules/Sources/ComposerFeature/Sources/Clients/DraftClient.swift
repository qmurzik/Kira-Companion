//
//  DraftClient.swift
//  ComposerFeature
//
//  TCA dependency around the crash-safe `ForumCore.DraftStore`.
//

import Foundation
import ComposableArchitecture
import ForumCore

public struct DraftClient: Sendable {
    public var load: @Sendable (_ key: DraftKey) async -> Draft?
    public var save: @Sendable (_ draft: Draft) async throws -> Void
    public var delete: @Sendable (_ key: DraftKey) async throws -> Void
    public var all: @Sendable () async -> [Draft]
    public var deleteAll: @Sendable () async throws -> Void
    public var storeAttachment: @Sendable (_ data: Data, _ name: String, _ key: DraftKey) async throws -> String
    public var attachmentData: @Sendable (_ name: String, _ key: DraftKey) async -> Data?
    public var removeAttachment: @Sendable (_ name: String, _ key: DraftKey) async -> Void
    
    public init(
        load: @escaping @Sendable (_ key: DraftKey) async -> Draft?,
        save: @escaping @Sendable (_ draft: Draft) async throws -> Void,
        delete: @escaping @Sendable (_ key: DraftKey) async throws -> Void,
        all: @escaping @Sendable () async -> [Draft],
        deleteAll: @escaping @Sendable () async throws -> Void,
        storeAttachment: @escaping @Sendable (_ data: Data, _ name: String, _ key: DraftKey) async throws -> String,
        attachmentData: @escaping @Sendable (_ name: String, _ key: DraftKey) async -> Data?,
        removeAttachment: @escaping @Sendable (_ name: String, _ key: DraftKey) async -> Void
    ) {
        self.load = load
        self.save = save
        self.delete = delete
        self.all = all
        self.deleteAll = deleteAll
        self.storeAttachment = storeAttachment
        self.attachmentData = attachmentData
        self.removeAttachment = removeAttachment
    }
}

extension DraftClient: DependencyKey {
    public static let shared = DraftStore(root: DraftStore.defaultRoot())
    
    public static var liveValue: DraftClient {
        let store = shared
        return DraftClient(
            load: { await store.draft(for: $0) },
            save: { _ = try await store.save($0) },
            delete: { try await store.delete($0) },
            all: { await store.all() },
            deleteAll: { try await store.deleteAll() },
            storeAttachment: { data, name, key in try await store.storeAttachmentData(data, preferredName: name, for: key) },
            attachmentData: { name, key in await store.attachmentData(named: name, for: key) },
            removeAttachment: { name, key in await store.removeAttachmentFile(named: name, for: key) }
        )
    }
    
    /// In-memory implementation for previews and tests.
    public static func inMemory() -> DraftClient {
        let drafts = LockIsolated<[String: Draft]>([:])
        let files = LockIsolated<[String: Data]>([:])
        return DraftClient(
            load: { key in drafts.value[key.storageId] },
            save: { draft in drafts.withValue { $0[draft.id] = draft.isEmpty ? nil : draft } },
            delete: { key in drafts.withValue { $0[key.storageId] = nil } },
            all: { drafts.value.values.sorted { $0.updatedAt > $1.updatedAt } },
            deleteAll: { drafts.setValue([:]) },
            storeAttachment: { data, name, key in
                let stored = UUID().uuidString.prefix(8) + "-" + name
                files.withValue { $0[key.storageId + "/" + stored] = data }
                return String(stored)
            },
            attachmentData: { name, key in files.value[key.storageId + "/" + name] },
            removeAttachment: { name, key in files.withValue { $0[key.storageId + "/" + name] = nil } }
        )
    }
    
    public static var previewValue: DraftClient { inMemory() }
    public static var testValue: DraftClient { inMemory() }
}

extension DependencyValues {
    public var draftClient: DraftClient {
        get { self[DraftClient.self] }
        set { self[DraftClient.self] = newValue }
    }
}
