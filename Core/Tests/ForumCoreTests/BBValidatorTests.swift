import XCTest
@testable import ForumCore

final class BBValidatorTests: XCTestCase {
    func testBalancedTextHasNoIssues() {
        XCTAssertEqual(BBValidator.issues(in: "[B]a[/B] [URL=\"x\"]y[/URL] [LIST][*]1[*]2[/LIST]"), [])
    }
    
    func testUnclosedTag() {
        let issues = BBValidator.issues(in: "[B]bold [I]both[/B]")
        XCTAssertEqual(issues.map(\.tag), ["i"])
        XCTAssertEqual(issues.first?.kind, .unclosed)
    }
    
    func testUnexpectedClose() {
        let issues = BBValidator.issues(in: "text[/QUOTE]")
        XCTAssertEqual(issues, [BBIssue(kind: .unexpectedClose, tag: "quote", range: TextRange(location: 4, length: 8))])
    }
    
    func testCodeContentIsLiteral() {
        XCTAssertEqual(BBValidator.issues(in: "[CODE]array[i] = [b][/CODE]"), [])
    }
    
    func testSquareBracketsInPlainTextAreIgnored() {
        XCTAssertEqual(BBValidator.issues(in: "[1] see [ссылка] and a[0]"), [])
    }
}
