import XCTest
@testable import ForumCore

final class DraftStoreTests: XCTestCase {
    var root: URL!
    
    override func setUp() {
        root = URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent("drafts-\(UUID().uuidString)")
    }
    
    override func tearDown() {
        try? FileManager.default.removeItem(at: root)
    }
    
    func testSaveLoadAndSeparateKeys() async throws {
        let store = DraftStore(root: root)
        try await store.save(Draft(key: .topicReply(topicId: 1), text: "reply one"))
        try await store.save(Draft(key: .topicReply(topicId: 2), text: "reply two"))
        try await store.save(Draft(key: .qms(dialogId: 1), text: "qms"))
        try await store.save(Draft(key: .newTopic(forumId: 1), title: "Title only"))
        
        // A new instance reads from disk (simulates app relaunch / crash recovery).
        let reopened = DraftStore(root: root)
        let one = await reopened.draft(for: .topicReply(topicId: 1))
        XCTAssertEqual(one?.text, "reply one")
        let qms = await reopened.draft(for: .qms(dialogId: 1))
        XCTAssertEqual(qms?.text, "qms")
        let all = await reopened.all()
        XCTAssertEqual(all.count, 4)
    }
    
    func testEmptyDraftIsDeleted() async throws {
        let store = DraftStore(root: root)
        let key = DraftKey.topicReply(topicId: 9)
        try await store.save(Draft(key: key, text: "x"))
        try await store.save(Draft(key: key, text: "   \n"))
        let draft = await store.draft(for: key)
        XCTAssertNil(draft)
    }
    
    func testPendingSubmissionKeepsEmptyDraft() async throws {
        let store = DraftStore(root: root)
        let key = DraftKey.qms(dialogId: 3)
        let pending = PendingSubmission(startedAt: Date(), contentFingerprint: "abc")
        try await store.save(Draft(key: key, text: "", pendingSubmission: pending))
        let draft = await store.draft(for: key)
        XCTAssertEqual(draft?.pendingSubmission, pending)
    }
    
    func testAttachmentFilesLiveAndDieWithDraft() async throws {
        let store = DraftStore(root: root)
        let key = DraftKey.topicReply(topicId: 5)
        let name = try await store.storeAttachmentData(Data([1, 2, 3]), preferredName: "a/b:c.png", for: key)
        XCTAssertFalse(name.contains("/"))
        let data = await store.attachmentData(named: name, for: key)
        XCTAssertEqual(data, Data([1, 2, 3]))
        try await store.save(Draft(key: key, text: "t", attachments: [
            DraftAttachment(fileName: "b_c.png", byteCount: 3, isImage: true, state: .local, localFileName: name)
        ]))
        try await store.delete(key)
        let gone = await store.attachmentData(named: name, for: key)
        XCTAssertNil(gone)
    }
    
    func testCorruptedFileDoesNotCrash() async throws {
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        try Data("{not json".utf8).write(to: root.appendingPathComponent("topic-1.json"))
        let store = DraftStore(root: root)
        let draft = await store.draft(for: .topicReply(topicId: 1))
        XCTAssertNil(draft)
        let all = await store.all()
        XCTAssertEqual(all, [])
    }
    
    func testSelectionRoundTrip() async throws {
        let store = DraftStore(root: root)
        let key = DraftKey.postEdit(postId: 77)
        try await store.save(Draft(key: key, text: "hello", selection: TextRange(location: 1, length: 3), editReason: "typo"))
        let reopened = DraftStore(root: root)
        let draft = await reopened.draft(for: key)
        XCTAssertEqual(draft?.selection, TextRange(location: 1, length: 3))
        XCTAssertEqual(draft?.editReason, "typo")
    }
}
