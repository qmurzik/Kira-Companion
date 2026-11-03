import XCTest
@testable import ForumCore

final class BBFormatterTests: XCTestCase {
    
    func testWrapSelectionKeepsInnerSelection() {
        let text = "Hello world"
        let edit = BBFormatter.apply(.bold, in: text, selection: TextRange(location: 6, length: 5))
        XCTAssertEqual(edit.apply(to: text), "Hello [B]world[/B]")
        XCTAssertEqual(edit.selectionAfter, TextRange(location: 9, length: 5))
        // Only the selected range is replaced.
        XCTAssertEqual(edit.range, TextRange(location: 6, length: 5))
    }
    
    func testEmptySelectionInsertsPairAndPutsCaretInside() {
        let text = "ab"
        let edit = BBFormatter.apply(.italic, in: text, selection: .caret(1))
        XCTAssertEqual(edit.apply(to: text), "a[I][/I]b")
        XCTAssertEqual(edit.selectionAfter, .caret(4))
    }
    
    func testToggleRemovesSurroundingTags() {
        let text = "x [B]bold[/B] y"
        let edit = BBFormatter.apply(.bold, in: text, selection: TextRange(location: 5, length: 4))
        XCTAssertEqual(edit.apply(to: text), "x bold y")
        XCTAssertEqual(edit.selectionAfter, TextRange(location: 2, length: 4))
    }
    
    func testToggleRemovesTagsInsideSelectionCaseInsensitive() {
        let text = "[b]bold[/b]"
        let edit = BBFormatter.apply(.bold, in: text, selection: TextRange(location: 0, length: text.utf16.count))
        XCTAssertEqual(edit.apply(to: text), "bold")
        XCTAssertEqual(edit.selectionAfter, TextRange(location: 0, length: 4))
    }
    
    func testEmojiAndCyrillicUseUTF16Ranges() {
        let text = "Привет 👋 мир"
        // "мир" starts after "Привет " (7) + "👋" (2 UTF-16 units) + " " (1) = 10
        let edit = BBFormatter.apply(.underline, in: text, selection: TextRange(location: 10, length: 3))
        XCTAssertEqual(edit.apply(to: text), "Привет 👋 [U]мир[/U]")
        XCTAssertEqual(edit.selectionAfter, TextRange(location: 13, length: 3))
    }
    
    func testOutOfBoundsSelectionIsClampedNeverLosesText() {
        let text = "keep me"
        let edit = BBFormatter.apply(.strike, in: text, selection: TextRange(location: 100, length: 50))
        XCTAssertEqual(edit.apply(to: text), "keep me[S][/S]")
    }
    
    func testColorSizeSpoiler() {
        let text = "abc"
        let all = TextRange(location: 0, length: 3)
        XCTAssertEqual(BBFormatter.apply(.color("red"), in: text, selection: all).apply(to: text), "[COLOR=red]abc[/COLOR]")
        XCTAssertEqual(BBFormatter.apply(.size(4), in: text, selection: all).apply(to: text), "[SIZE=4]abc[/SIZE]")
        XCTAssertEqual(BBFormatter.apply(.spoiler(title: "Скрин \"1\""), in: text, selection: all).apply(to: text), "[SPOILER=\"Скрин '1'\"]abc[/SPOILER]")
        XCTAssertEqual(BBFormatter.apply(.spoiler(title: nil), in: text, selection: all).apply(to: text), "[SPOILER]abc[/SPOILER]")
    }
    
    func testLinkVariants() {
        let url = "https://4pda.to"
        var edit = BBFormatter.link(in: url, selection: TextRange(location: 0, length: url.utf16.count), url: nil)
        XCTAssertEqual(edit.apply(to: url), "[URL]https://4pda.to[/URL]")
        
        let text = "сайт"
        edit = BBFormatter.link(in: text, selection: TextRange(location: 0, length: 4), url: "https://4pda.to")
        XCTAssertEqual(edit.apply(to: text), "[URL=\"https://4pda.to\"]сайт[/URL]")
        
        edit = BBFormatter.link(in: text, selection: TextRange(location: 0, length: 4), url: nil)
        XCTAssertEqual(edit.apply(to: text), "[URL=\"\"]сайт[/URL]")
        XCTAssertEqual(edit.selectionAfter, .caret(6)) // inside the quotes
    }
    
    func testListFromLines() {
        let text = "Intro\n- one\n* two\n3. three\nOutro"
        let start = ("Intro\n" as String).utf16.count
        let len = ("- one\n* two\n3. three" as String).utf16.count
        let edit = BBFormatter.list(in: text, selection: TextRange(location: start + 2, length: len - 4), ordered: false)
        XCTAssertEqual(edit.apply(to: text), "Intro\n[LIST]\n[*]one\n[*]two\n[*]three\n[/LIST]\nOutro")
    }
    
    func testEmptyOrderedListPlacesCaretOnFirstItem() {
        let text = "abc"
        let edit = BBFormatter.list(in: text, selection: .caret(3), ordered: true)
        let result = edit.apply(to: text)
        XCTAssertEqual(result, "abc\n[LIST=1]\n[*]\n[/LIST]\n")
        XCTAssertEqual(result.utf16Substring(TextRange(location: edit.selectionAfter.location - 3, length: 3)), "[*]")
    }
    
    func testAttachmentInsertion() {
        let text = "see  here"
        let edit = BBFormatter.attachment(id: 42, name: "photo \"1\".jpg", in: text, selection: .caret(4))
        XCTAssertEqual(edit.apply(to: text), "see [ATTACHMENT=\"42:photo '1'.jpg\"] here")
    }
    
    func testReplyPrefix() {
        let edit = BBFormatter.reply(postId: 7, authorName: "Nick", in: "", selection: .caret(0))
        XCTAssertEqual(edit.apply(to: ""), "[SNAPBACK]7[/SNAPBACK] [B]Nick[/B], ")
        let edit2 = BBFormatter.reply(postId: 7, authorName: "Nick", in: "text", selection: .caret(4))
        XCTAssertEqual(edit2.apply(to: "text"), "text\n[SNAPBACK]7[/SNAPBACK] [B]Nick[/B], ")
    }
    
    func testLongTextPerformanceIsLinear() {
        let text = String(repeating: "Длинный текст сообщения. ", count: 20_000) // ~500k UTF-16 units
        let start = Date()
        let edit = BBFormatter.apply(.bold, in: text, selection: TextRange(location: 1000, length: 20))
        _ = edit.apply(to: text)
        XCTAssertLessThan(Date().timeIntervalSince(start), 2.0)
    }
}
