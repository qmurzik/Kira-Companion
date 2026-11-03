import XCTest
@testable import ForumCore

final class PageMathTests: XCTestCase {
    func testPages() {
        let m = PageMath(perPage: 20, totalItems: 101)
        XCTAssertEqual(m.pageCount, 6)
        XCTAssertEqual(m.page(forOffset: 0), 1)
        XCTAssertEqual(m.page(forOffset: 20), 2)
        XCTAssertEqual(m.page(forOffset: 100), 6)
        XCTAssertEqual(m.offset(forPage: 3), 40)
        XCTAssertEqual(m.offset(forPage: 99), 100)
        XCTAssertEqual(m.page(forItemNumber: 21), 2)
        XCTAssertEqual(m.lastPageOffset, 100)
    }
    
    func testEmptyTopicHasOnePage() {
        XCTAssertEqual(PageMath(perPage: 20, totalItems: 0).pageCount, 1)
    }
    
    func testPaginatorItems() {
        let m = PageMath(perPage: 10, totalItems: 100)
        XCTAssertEqual(m.paginatorItems(current: 5), [1, nil, 4, 5, 6, nil, 10])
        XCTAssertEqual(m.paginatorItems(current: 1), [1, 2, nil, 10])
        XCTAssertEqual(PageMath(perPage: 10, totalItems: 5).paginatorItems(current: 1), [1])
    }
}

final class ReadingStoreTests: XCTestCase {
    func testPositionsAndBookmarksPersist() async {
        let url = URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent("reading-\(UUID().uuidString).json")
        defer { try? FileManager.default.removeItem(at: url) }
        let store = ReadingStore(fileURL: url, capacity: 2)
        await store.save(ReadingPosition(topicId: 1, topicTitle: "A", offset: 20, postId: 5))
        await store.save(ReadingPosition(topicId: 2, topicTitle: "B", offset: 0, postId: nil))
        await store.save(ReadingPosition(topicId: 3, topicTitle: "C", offset: 40, postId: 9))
        let added = await store.toggle(PostBookmark(postId: 9, topicId: 3, topicTitle: "C", authorName: "x", excerpt: "e"))
        XCTAssertTrue(added)
        
        let reopened = ReadingStore(fileURL: url, capacity: 2)
        let evicted = await reopened.position(forTopic: 1)
        XCTAssertNil(evicted, "LRU capacity respected")
        let pos = await reopened.position(forTopic: 3)
        XCTAssertEqual(pos?.offset, 40)
        let recent = await reopened.recentPositions().map(\.topicId)
        XCTAssertEqual(recent, [3, 2])
        let isBookmarked = await reopened.isBookmarked(postId: 9)
        XCTAssertTrue(isBookmarked)
        let removed = await reopened.toggle(PostBookmark(postId: 9, topicId: 3, topicTitle: "C", authorName: "x", excerpt: "e"))
        XCTAssertFalse(removed)
    }
}
