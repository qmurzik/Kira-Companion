import XCTest
@testable import ForumCore

final class QuoteBuilderTests: XCTestCase {
    
    let source = QuoteSource(postId: 123456, authorName: "Some \"User\"", date: Date(timeIntervalSince1970: 1_700_000_000))
    let utc = TimeZone(identifier: "UTC")!
    
    func testQuoteFormat() {
        let q = QuoteBuilder.quote("  text  ", from: source, timeZone: utc)
        XCTAssertEqual(q, "[quote name=\"Some 'User'\" date=\"14.11.23, 22:13\" post=\"123456\"]text[/quote]\n")
    }
    
    func testInsertPutsQuoteOnOwnLineAndCaretAfter() {
        let current = "Hello"
        let edit = QuoteBuilder.insert(quote: "q", from: source, into: current, selection: .caret(5), timeZone: utc)
        let result = edit.apply(to: current)
        XCTAssertTrue(result.hasPrefix("Hello\n[quote name="))
        XCTAssertTrue(result.hasSuffix("[/quote]\n"))
        XCTAssertEqual(edit.selectionAfter.location, result.utf16.count)
    }
    
    func testMultipleQuotesAppend() {
        var text = ""
        text = QuoteBuilder.append(quote: "first", from: source, to: text, timeZone: utc).text
        text = QuoteBuilder.append(quote: "second", from: QuoteSource(postId: 2, authorName: "B", date: source.date), to: text, timeZone: utc).text
        let quotes = QuoteBuilder.quotes(in: text)
        XCTAssertEqual(quotes.count, 2)
        XCTAssertEqual(quotes[0].postId, 123456)
        XCTAssertEqual(quotes[0].body, "first")
        XCTAssertEqual(quotes[1].postId, 2)
        XCTAssertEqual(quotes[1].authorName, "B")
    }
    
    func testNestedQuotesAreOneBlockAndCanBeRemoved() {
        let text = "a\n[quote name=\"X\" post=\"1\"]outer [quote]inner[/quote] tail[/quote]\nb"
        let quotes = QuoteBuilder.quotes(in: text)
        XCTAssertEqual(quotes.count, 1)
        XCTAssertEqual(quotes[0].postId, 1)
        let edit = QuoteBuilder.remove(quotes[0], from: text)
        XCTAssertEqual(edit.apply(to: text), "a\nb")
    }
    
    func testUnquotedPostAttribute() {
        let quotes = QuoteBuilder.quotes(in: "[QUOTE name=\"A\" post=55]x[/QUOTE]")
        XCTAssertEqual(quotes.first?.postId, 55)
    }
}
