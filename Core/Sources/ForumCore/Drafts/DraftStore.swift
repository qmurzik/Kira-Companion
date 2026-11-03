//
//  DraftStore.swift
//  ForumCore
//
//  Crash-safe draft persistence.
//
//  • one JSON file per draft — writing a draft never rewrites other drafts;
//  • writes are atomic (temp file + rename), so a crash mid-write keeps the previous version;
//  • local attachment files live next to the draft in `<root>/<draft-id>.files/`;
//  • empty drafts are removed automatically.
//

import Foundation

public actor DraftStore {
    
    public let root: URL
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder
    private var cache: [String: Draft] = [:]
    private var didLoadAll = false
    
    public init(root: URL) {
        self.root = root
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .millisecondsSince1970
        encoder.outputFormatting = [.sortedKeys]
        self.encoder = encoder
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .millisecondsSince1970
        self.decoder = decoder
    }
    
    /// Default location: Application Support/Drafts (not purged by the system, unlike Caches).
    public static func defaultRoot() -> URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())
        return base.appendingPathComponent("Drafts", isDirectory: true)
    }
    
    // MARK: - Read
    
    public func draft(for key: DraftKey) -> Draft? {
        if let cached = cache[key.storageId] { return cached }
        let url = fileURL(for: key.storageId)
        guard let data = try? Data(contentsOf: url),
              let draft = try? decoder.decode(Draft.self, from: data) else { return nil }
        cache[key.storageId] = draft
        return draft
    }
    
    /// All drafts, most recently edited first.
    public func all() -> [Draft] {
        if !didLoadAll {
            let files = (try? FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: nil)) ?? []
            for file in files where file.pathExtension == "json" {
                guard let data = try? Data(contentsOf: file),
                      let draft = try? decoder.decode(Draft.self, from: data) else { continue }
                cache[draft.id] = draft
            }
            didLoadAll = true
        }
        return cache.values.sorted { $0.updatedAt > $1.updatedAt }
    }
    
    // MARK: - Write
    
    /// Saves a draft (or deletes it when empty). Returns `true` when written to disk.
    @discardableResult
    public func save(_ draft: Draft) throws -> Bool {
        if draft.isEmpty {
            try delete(draft.key)
            return false
        }
        try ensureRoot()
        let data = try encoder.encode(draft)
        try data.write(to: fileURL(for: draft.id), options: [.atomic])
        cache[draft.id] = draft
        return true
    }
    
    public func delete(_ key: DraftKey) throws {
        cache[key.storageId] = nil
        let fm = FileManager.default
        let url = fileURL(for: key.storageId)
        if fm.fileExists(atPath: url.path) { try fm.removeItem(at: url) }
        let files = filesDirectory(for: key)
        if fm.fileExists(atPath: files.path) { try fm.removeItem(at: files) }
    }
    
    public func deleteAll() throws {
        cache.removeAll()
        let fm = FileManager.default
        if fm.fileExists(atPath: root.path) { try fm.removeItem(at: root) }
        didLoadAll = false
    }
    
    // MARK: - Attachments
    
    public func filesDirectory(for key: DraftKey) -> URL {
        root.appendingPathComponent(key.storageId + ".files", isDirectory: true)
    }
    
    /// Copies attachment data into the draft folder and returns the stored file name.
    public func storeAttachmentData(_ data: Data, preferredName: String, for key: DraftKey) throws -> String {
        let dir = filesDirectory(for: key)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let safe = Self.sanitizedFileName(preferredName)
        let name = UUID().uuidString.prefix(8) + "-" + safe
        try data.write(to: dir.appendingPathComponent(String(name)), options: [.atomic])
        return String(name)
    }
    
    public func attachmentData(named name: String, for key: DraftKey) -> Data? {
        try? Data(contentsOf: filesDirectory(for: key).appendingPathComponent(name))
    }
    
    public func removeAttachmentFile(named name: String, for key: DraftKey) {
        try? FileManager.default.removeItem(at: filesDirectory(for: key).appendingPathComponent(name))
    }
    
    // MARK: - Helpers
    
    private func ensureRoot() throws {
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
    }
    
    private func fileURL(for id: String) -> URL {
        root.appendingPathComponent(id + ".json")
    }
    
    public static func sanitizedFileName(_ name: String) -> String {
        let forbidden = CharacterSet(charactersIn: "/\\:?%*|\"<>\0")
        let cleaned = name.unicodeScalars.map { forbidden.contains($0) ? "_" : String($0) }.joined()
        let trimmed = cleaned.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? "file" : String(trimmed.prefix(120))
    }
}
