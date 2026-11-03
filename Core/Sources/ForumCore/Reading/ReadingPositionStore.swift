//
//  ReadingPositionStore.swift
//  ForumCore
//
//  Remembers where the user stopped reading each topic (page offset + post id) so the topic
//  reopens at the same place after the app is closed. Bounded LRU, stored as one small JSON.
//

import Foundation

public struct ReadingPosition: Hashable, Codable, Sendable {
    public var topicId: Int
    public var topicTitle: String
    public var offset: Int
    public var postId: Int?
    public var updatedAt: Date
    
    public init(topicId: Int, topicTitle: String, offset: Int, postId: Int?, updatedAt: Date = Date()) {
        self.topicId = topicId
        self.topicTitle = topicTitle
        self.offset = offset
        self.postId = postId
        self.updatedAt = updatedAt
    }
}

/// Locally bookmarked post (4PDA has no per-post bookmark API).
public struct PostBookmark: Hashable, Codable, Sendable, Identifiable {
    public var postId: Int
    public var topicId: Int
    public var topicTitle: String
    public var authorName: String
    public var excerpt: String
    public var createdAt: Date
    public var id: Int { postId }
    
    public init(postId: Int, topicId: Int, topicTitle: String, authorName: String, excerpt: String, createdAt: Date = Date()) {
        self.postId = postId
        self.topicId = topicId
        self.topicTitle = topicTitle
        self.authorName = authorName
        self.excerpt = excerpt
        self.createdAt = createdAt
    }
}

public actor ReadingStore {
    
    struct Payload: Codable {
        var positions: [ReadingPosition] = []
        var bookmarks: [PostBookmark] = []
    }
    
    public let fileURL: URL
    public let capacity: Int
    private var payload: Payload
    
    public init(fileURL: URL, capacity: Int = 500) {
        self.fileURL = fileURL
        self.capacity = capacity
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .millisecondsSince1970
        if let data = try? Data(contentsOf: fileURL),
           let payload = try? decoder.decode(Payload.self, from: data) {
            self.payload = payload
        } else {
            self.payload = Payload()
        }
    }
    
    public static func defaultURL() -> URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())
        return base.appendingPathComponent("Reading/reading.json")
    }
    
    // MARK: Positions
    
    public func position(forTopic id: Int) -> ReadingPosition? {
        payload.positions.first { $0.topicId == id }
    }
    
    public func recentPositions(limit: Int = 20) -> [ReadingPosition] {
        Array(payload.positions.prefix(limit))
    }
    
    public func save(_ position: ReadingPosition) {
        payload.positions.removeAll { $0.topicId == position.topicId }
        payload.positions.insert(position, at: 0)
        if payload.positions.count > capacity {
            payload.positions.removeLast(payload.positions.count - capacity)
        }
        persist()
    }
    
    public func clearPositions() {
        payload.positions.removeAll()
        persist()
    }
    
    // MARK: Bookmarks
    
    public func bookmarks() -> [PostBookmark] { payload.bookmarks }
    
    public func isBookmarked(postId: Int) -> Bool {
        payload.bookmarks.contains { $0.postId == postId }
    }
    
    /// Returns the new bookmark state.
    @discardableResult
    public func toggle(_ bookmark: PostBookmark) -> Bool {
        if let index = payload.bookmarks.firstIndex(where: { $0.postId == bookmark.postId }) {
            payload.bookmarks.remove(at: index)
            persist()
            return false
        }
        payload.bookmarks.insert(bookmark, at: 0)
        persist()
        return true
    }
    
    public func removeBookmark(postId: Int) {
        payload.bookmarks.removeAll { $0.postId == postId }
        persist()
    }
    
    // MARK: Persistence
    
    private func persist() {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .millisecondsSince1970
        do {
            try FileManager.default.createDirectory(at: fileURL.deletingLastPathComponent(), withIntermediateDirectories: true)
            try encoder.encode(payload).write(to: fileURL, options: [.atomic])
        } catch {
            // Non-fatal: positions are a convenience.
        }
    }
}
