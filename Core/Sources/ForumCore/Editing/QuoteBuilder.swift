//
//  QuoteBuilder.swift
//  ForumCore
//
//  Builds forum quotes in the format accepted by 4PDA:
//  [quote name="Nick" date="dd.MM.yy, HH:mm" post="123"]text[/quote]
//

import Foundation

public struct QuoteSource: Equatable, Sendable {
    public var postId: Int
    public var authorName: String
    public var date: Date
    
    public init(postId: Int, authorName: String, date: Date) {
        self.postId = postId
        self.authorName = authorName
        self.date = date
    }
}

public enum QuoteBuilder {
    
    /// The forum shows quote dates in Moscow time.
    public static let forumTimeZone = TimeZone(identifier: "Europe/Moscow") ?? .current
    
    public static func header(for source: QuoteSource, timeZone: TimeZone = forumTimeZone) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = timeZone
        formatter.dateFormat = "dd.MM.yy, HH:mm"
        let name = source.authorName.bbEscapedAttribute
        return "[quote name=\"\(name)\" date=\"\(formatter.string(from: source.date))\" post=\"\(source.postId)\"]"
    }
    
    /// Builds a full quote block ending with a newline.
    public static func quote(_ text: String, from source: QuoteSource, timeZone: TimeZone = forumTimeZone) -> String {
        let body = text.trimmingCharacters(in: .whitespacesAndNewlines)
        return header(for: source, timeZone: timeZone) + body + "[/quote]\n"
    }
    
    /// Inserts a quote at the caret (or replaces the selection). Ensures the quote starts on its
    /// own line and places the caret on a fresh line right after it, so the user can answer
    /// immediately. Several quotes can be inserted one after another.
    public static func insert(
        quote text: String,
        from source: QuoteSource,
        into current: String,
        selection: TextRange,
        timeZone: TimeZone = forumTimeZone
    ) -> TextEdit {
        let sel = selection.clamped(toUTF16Count: current.utf16.count)
        let block = quote(text, from: source, timeZone: timeZone)
        let prefix = BBFormatter.needsLeadingNewline(current, at: sel.location) ? "\n" : ""
        // `block` ends with "\n", so the caret lands on a fresh line after the quote.
        let snippet = prefix + block
        return TextEdit(
            range: sel,
            replacement: snippet,
            selectionAfter: .caret(sel.location + snippet.utf16.count)
        )
    }
    
    /// Appends a quote to the end of a draft (used when the composer is not on screen).
    public static func append(quote text: String, from source: QuoteSource, to current: String, timeZone: TimeZone = forumTimeZone) -> (text: String, caret: Int) {
        let end = TextRange.caret(current.utf16.count)
        let edit = insert(quote: text, from: source, into: current, selection: end, timeZone: timeZone)
        return (edit.apply(to: current), edit.selectionAfter.location)
    }
    
    // MARK: - Locating quotes (for edit / remove)
    
    public struct QuoteBlock: Equatable, Sendable {
        public var range: TextRange
        public var postId: Int?
        public var authorName: String?
        public var body: String
    }
    
    /// Finds top-level `[quote …]…[/quote]` blocks (nested quotes are part of their parent).
    public static func quotes(in text: String) -> [QuoteBlock] {
        let units = Array(text.utf16)
        let lower = Array(text.lowercased().utf16)
        guard lower.count == units.count else { return [] } // exotic case mapping changed length
        let openToken = Array("[quote".utf16)
        let closeToken = Array("[/quote]".utf16)
        var result: [QuoteBlock] = []
        var i = 0
        var depth = 0
        var start = 0
        var headerEnd = 0
        while i < lower.count {
            let closeBracket: UInt16 = 93 // "]"
            let terminators: Set<UInt16> = [93, 32, 61] // "]", " ", "="
            if matches(lower, openToken, at: i),
               i + openToken.count < lower.count,
               terminators.contains(lower[i + openToken.count]) {
                guard let close = lower[i...].firstIndex(of: closeBracket) else { break }
                if depth == 0 { start = i; headerEnd = close + 1 }
                depth += 1
                i = close + 1
                continue
            }
            if depth > 0, matches(lower, closeToken, at: i) {
                depth -= 1
                if depth == 0 {
                    let end = i + closeToken.count
                    let header = String(decoding: units[start..<headerEnd], as: UTF16.self)
                    let body = String(decoding: units[headerEnd..<i], as: UTF16.self)
                    result.append(QuoteBlock(
                        range: TextRange(location: start, length: end - start),
                        postId: attribute("post", in: header).flatMap(Int.init),
                        authorName: attribute("name", in: header),
                        body: body
                    ))
                }
                i += closeToken.count
                continue
            }
            i += 1
        }
        return result
    }
    
    /// Edit that removes a quote block (and one trailing newline, if any).
    public static func remove(_ block: QuoteBlock, from text: String) -> TextEdit {
        var range = block.range
        if text.utf16Substring(TextRange(location: range.end, length: 1)) == "\n" {
            range.length += 1
        }
        return TextEdit(range: range, replacement: "", selectionAfter: .caret(range.location))
    }
    
    static func matches(_ haystack: [UInt16], _ needle: [UInt16], at index: Int) -> Bool {
        guard index + needle.count <= haystack.count else { return false }
        for k in 0..<needle.count where haystack[index + k] != needle[k] { return false }
        return true
    }
    
    static func attribute(_ name: String, in header: String) -> String? {
        guard let r = header.range(of: name + "=\"", options: .caseInsensitive) else {
            // unquoted: post=123
            guard let r2 = header.range(of: name + "=", options: .caseInsensitive) else { return nil }
            let rest = header[r2.upperBound...]
            let value = rest.prefix(while: { $0 != " " && $0 != "]" })
            return value.isEmpty ? nil : String(value)
        }
        let rest = header[r.upperBound...]
        guard let end = rest.firstIndex(of: "\"") else { return nil }
        return String(rest[..<end])
    }
}
