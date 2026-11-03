//
//  BBValidator.swift
//  ForumCore
//
//  Lightweight structural check of BBCode before sending: finds tags that are opened but
//  never closed (or closed without being opened). It never changes the text — the composer
//  only shows a non-blocking warning.
//

import Foundation

public struct BBIssue: Equatable, Sendable {
    public enum Kind: Equatable, Sendable {
        case unclosed
        case unexpectedClose
    }
    public var kind: Kind
    public var tag: String
    public var range: TextRange
}

public enum BBValidator {
    
    /// Tags that must be closed. Self-contained tags like [*], [attachment=…], [snapback] content
    /// tags etc. are handled by being in this list or not.
    public static let pairedTags: Set<String> = [
        "b", "i", "u", "s", "sup", "sub", "color", "size", "font", "url", "img", "quote", "code",
        "spoiler", "list", "center", "left", "right", "offtop", "hide", "cur", "mod", "ex",
        "snapback", "background", "numlist", "anchor"
    ]
    
    public static func issues(in text: String) -> [BBIssue] {
        var stack: [(name: String, range: TextRange)] = []
        var issues: [BBIssue] = []
        var codeDepth = 0
        for token in tokens(in: text) {
            // Everything inside [code] is literal.
            if codeDepth > 0 && !(token.isClosing && token.name == "code") && !(!token.isClosing && token.name == "code") {
                continue
            }
            guard pairedTags.contains(token.name) else { continue }
            if token.isClosing {
                if let idx = stack.lastIndex(where: { $0.name == token.name }) {
                    // Everything opened after the matching tag is unclosed.
                    for open in stack[(idx + 1)...] {
                        issues.append(BBIssue(kind: .unclosed, tag: open.name, range: open.range))
                    }
                    stack.removeSubrange(idx...)
                    if token.name == "code" { codeDepth = max(0, codeDepth - 1) }
                } else {
                    issues.append(BBIssue(kind: .unexpectedClose, tag: token.name, range: token.range))
                }
            } else {
                stack.append((token.name, token.range))
                if token.name == "code" { codeDepth += 1 }
            }
        }
        for open in stack {
            issues.append(BBIssue(kind: .unclosed, tag: open.name, range: open.range))
        }
        return issues.sorted { $0.range.location < $1.range.location }
    }
    
    struct Token {
        var name: String
        var isClosing: Bool
        var range: TextRange
    }
    
    static func tokens(in text: String) -> [Token] {
        let units = Array(text.utf16)
        var result: [Token] = []
        var i = 0
        let open: UInt16 = 91, close: UInt16 = 93, slash: UInt16 = 47 // "[", "]", "/"
        while i < units.count {
            guard units[i] == open else { i += 1; continue }
            var j = i + 1
            var isClosing = false
            if j < units.count, units[j] == slash { isClosing = true; j += 1 }
            let nameStart = j
            while j < units.count, isNameUnit(units[j]) { j += 1 }
            guard j > nameStart else { i += 1; continue }
            let name = String(decoding: units[nameStart..<j], as: UTF16.self).lowercased()
            // Find the end of the tag (attributes may follow) — stop at a newline or a new '['.
            var k = j
            while k < units.count, units[k] != close, units[k] != open, units[k] != 10 { k += 1 }
            guard k < units.count, units[k] == close else { i += 1; continue }
            if isClosing && k != j { i += 1; continue } // "[/b x]" is not a tag
            result.append(Token(name: name, isClosing: isClosing, range: TextRange(location: i, length: k - i + 1)))
            i = k + 1
        }
        return result
    }
    
    static func isNameUnit(_ u: UInt16) -> Bool {
        (u >= 65 && u <= 90) || (u >= 97 && u <= 122) || u == 42 /* * */
    }
}
