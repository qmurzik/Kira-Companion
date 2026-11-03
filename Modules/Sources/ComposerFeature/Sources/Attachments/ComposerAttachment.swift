//
//  ComposerAttachment.swift
//  ComposerFeature
//

import Foundation
import ForumCore

public struct ComposerAttachment: Equatable, Identifiable, Sendable {
    
    public enum Status: Equatable, Sendable {
        case queued
        case uploading(progress: Double)
        case uploaded
        case failed(String)
        
        public var isActive: Bool {
            if case .uploading = self { true } else { false }
        }
    }
    
    public var id: UUID
    public var fileName: String
    public var byteCount: Int
    public var isImage: Bool
    /// Small JPEG preview (not persisted with the draft).
    public var thumbnail: Data?
    public var status: Status
    public var serverId: Int?
    public var localFileName: String?
    public var md5: String?
    
    public init(
        id: UUID,
        fileName: String,
        byteCount: Int,
        isImage: Bool,
        thumbnail: Data? = nil,
        status: Status,
        serverId: Int? = nil,
        localFileName: String? = nil
    ) {
        self.id = id
        self.fileName = fileName
        self.byteCount = byteCount
        self.isImage = isImage
        self.thumbnail = thumbnail
        self.status = status
        self.serverId = serverId
        self.localFileName = localFileName
    }
    
    /// Already uploaded attachment (e.g. when editing an existing post).
    public static func existing(serverId: Int, name: String, isImage: Bool) -> ComposerAttachment {
        ComposerAttachment(id: UUID(), fileName: name, byteCount: 0, isImage: isImage, status: .uploaded, serverId: serverId)
    }
    
    init(draft: DraftAttachment) {
        self.id = draft.id
        self.fileName = draft.fileName
        self.byteCount = draft.byteCount
        self.isImage = draft.isImage
        self.thumbnail = nil
        self.serverId = draft.serverId
        self.localFileName = draft.localFileName
        switch draft.state {
        case .uploaded: self.status = draft.serverId != nil ? .uploaded : .queued
        // Interrupted uploads are resumed from the local copy.
        case .local: self.status = .queued
        case .failed: self.status = .failed("Загрузка не завершена")
        }
    }
    
    var draftAttachment: DraftAttachment {
        let state: DraftAttachment.State = switch status {
        case .uploaded: .uploaded
        case .failed: .failed
        case .queued, .uploading: .local
        }
        return DraftAttachment(
            id: id,
            fileName: fileName,
            byteCount: byteCount,
            isImage: isImage,
            state: state,
            serverId: serverId,
            localFileName: localFileName
        )
    }
    
    /// Renames keeping the original extension (the server validates by extension).
    static func renamed(_ original: String, to newName: String) -> String {
        let trimmed = DraftStore.sanitizedFileName(newName.trimmingCharacters(in: .whitespacesAndNewlines))
        guard !trimmed.isEmpty, trimmed != "file" || newName == "file" else { return original }
        let ext = (original as NSString).pathExtension
        let base = (trimmed as NSString).deletingPathExtension
        return ext.isEmpty ? trimmed : "\(base).\(ext)"
    }
    
    public var formattedSize: String {
        guard byteCount > 0 else { return "" }
        return ByteCountFormatter.string(fromByteCount: Int64(byteCount), countStyle: .file)
    }
}

/// A file chosen by the user (photo library, Files, clipboard).
public struct PickedFile: Equatable, Sendable {
    public var fileName: String
    public var data: Data
    public var isImage: Bool
    public var thumbnail: Data?
    
    public init(fileName: String, data: Data, isImage: Bool, thumbnail: Data? = nil) {
        self.fileName = fileName
        self.data = data
        self.isImage = isImage
        self.thumbnail = thumbnail
    }
}
