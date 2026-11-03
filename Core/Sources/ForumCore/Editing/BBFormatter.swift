//
//  BBFormatter.swift
//  ForumCore
//
//  Pure, deterministic BBCode editing operations used by the composer.
//
//  Contract of every operation:
//   • input: the full current text + the current selection (UTF-16, like UITextView);
//   • output: a minimal `TextEdit` (only the touched range is replaced);
//   • a non-empty selection is wrapped and stays selected (inner text);
//   • an empty selection inserts a tag pair and puts the caret between the tags;
//   • nothing outside the touched range is ever modified — text cannot be lost.
//

import Foundation

public enum BBFormatter {

    // MARK: - Generic wrapping

    /// Wraps the selection with `open`/`close`. If the selection is already wrapped with exactly
    /// these tags (either the tags are part of the selection or directly surround it), the tags
    /// are removed instead (toggle behaviour).
    public static func toggle(
        in text: String,
        selection: TextRange,
        open: String,
        close: String
    ) -> TextEdit {
        let count = text.utf16Length
        let sel = selection.clamped(toUTF16Count: count)
        let selected = text.utf16Substring(sel)
        let openLen = open.utf16Length
        let closeLen = close.utf16Length

        // Case 1: the selection itself contains the tags: "[B]text[/B]".
        if sel.length >= openLen + closeLen,
           selected.utf16HasPrefix(open), selected.utf16HasSuffix(close) {
            let inner = selected.utf16Substring(TextRange(location: openLen, length: sel.length - openLen - closeLen))
            return TextEdit(
                range: sel,
                replacement: inner,
                selectionAfter: TextRange(location: sel.location, length: inner.utf16Length)
            )
        }

        // Case 2: tags directly surround the selection: "[B]|text|[/B]".
        if sel.location >= openLen, sel.end + closeLen <= count {
            let before = text.utf16Substring(TextRange(location: sel.location - openLen, length: openLen))
            let after = text.utf16Substring(TextRange(location: sel.end, length: closeLen))
            if before.caseInsensitiveCompare(open) == .orderedSame,
               after.caseInsensitiveCompare(close) == .orderedSame {
                return TextEdit(
                    range: TextRange(location: sel.location - openLen, length: openLen + sel.length + closeLen),
                    replacement: selected,
                    selectionAfter: TextRange(location: sel.location - openLen, length: sel.length)
                )
            }
        }

        return wrap(in: text, selection: sel, open: open, close: close)
    }

    /// Wraps the selection with `open`/`close` (no toggle).
    public static func wrap(
        in text: String,
        selection: TextRange,
        open: String,
        close: String
    ) -> TextEdit {
        let sel = selection.clamped(toUTF16Count: text.utf16Length)
        let selected = text.utf16Substring(sel)
        let openLen = open.utf16Length
        return TextEdit(
            range: sel,
            replacement: open + selected + close,
            selectionAfter: TextRange(location: sel.location + openLen, length: sel.length)
        )
    }

    /// Inserts a snippet replacing the selection, caret placed at `caretOffset` inside the snippet
    /// (defaults to the end of the snippet).
    public static func insert(
        _ snippet: String,
        in text: String,
        selection: TextRange,
        caretOffset: Int? = nil
    ) -> TextEdit {
        let sel = selection.clamped(toUTF16Count: text.utf16Length)
        let offset = min(max(0, caretOffset ?? snippet.utf16Length), snippet.utf16Length)
        return TextEdit(range: sel, replacement: snippet, selectionAfter: .caret(sel.location + offset))
    }

    // MARK: - Tag based operations

    public static func apply(_ tag: BBTag, in text: String, selection: TextRange) -> TextEdit {
        switch tag {
        case let .color(value):
            return wrap(in: text, selection: selection, open: "[COLOR=\(value)]", close: "[/COLOR]")
        case let .size(value):
            return wrap(in: text, selection: selection, open: "[SIZE=\(value)]", close: "[/SIZE]")
        case let .spoiler(title):
            if let title, !title.isEmpty {
                return wrap(in: text, selection: selection, open: "[SPOILER=\"\(title.bbEscapedAttribute)\"]", close: "[/SPOILER]")
            }
            return toggle(in: text, selection: selection, open: "[SPOILER]", close: "[/SPOILER]")
        case let .link(url):
            return link(in: text, selection: selection, url: url)
        case let .image(url):
            return insertImage(in: text, selection: selection, url: url)
        case .list:
            return list(in: text, selection: selection, ordered: false)
        case .orderedList:
            return list(in: text, selection: selection, ordered: true)
        default:
            let name = tag.name
            return toggle(in: text, selection: selection, open: "[\(name)]", close: "[/\(name)]")
        }
    }

    /// `[URL="…"]title[/URL]`. Without a URL the caret is placed inside the quotes so the
    /// user can paste it. A selection that already is a URL becomes `[URL]…[/URL]`.
    public static func link(in text: String, selection: TextRange, url: String?) -> TextEdit {
        let sel = selection.clamped(toUTF16Count: text.utf16Length)
        let selected = text.utf16Substring(sel)
        let trimmedURL = url?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""

        if trimmedURL.isEmpty {
            if selected.looksLikeURL {
                return wrap(in: text, selection: sel, open: "[URL]", close: "[/URL]")
            }
            let open = "[URL=\""
            let snippet = open + "\"]" + selected + "[/URL]"
            return insert(snippet, in: text, selection: sel, caretOffset: open.utf16Length)
        }
        let title = selected.isEmpty ? trimmedURL : selected
        let snippet = "[URL=\"\(trimmedURL.bbEscapedAttribute)\"]\(title)[/URL]"
        return insert(snippet, in: text, selection: sel)
    }

    public static func insertImage(in text: String, selection: TextRange, url: String?) -> TextEdit {
        let sel = selection.clamped(toUTF16Count: text.utf16Length)
        let selected = text.utf16Substring(sel)
        let value = (url?.isEmpty == false ? url! : selected).trimmingCharacters(in: .whitespacesAndNewlines)
        if value.isEmpty {
            return wrap(in: text, selection: sel, open: "[IMG]", close: "[/IMG]")
        }
        return insert("[IMG]\(value)[/IMG]", in: text, selection: sel)
    }

    /// Converts the selected lines into a list. Common plain-text markers ("- ", "* ", "1. ")
    /// are removed. With an empty selection inserts an empty list with the caret on the first item.
    public static func list(in text: String, selection: TextRange, ordered: Bool) -> TextEdit {
        let open = ordered ? "[LIST=1]" : "[LIST]"
        let close = "[/LIST]"
        let sel = selection.clamped(toUTF16Count: text.utf16Length)

        if sel.isEmpty {
            let prefix = needsLeadingNewline(text, at: sel.location) ? "\n" : ""
            let head = prefix + open + "\n[*]"
            let snippet = head + "\n" + close + "\n"
            return insert(snippet, in: text, selection: sel, caretOffset: head.utf16Length)
        }

        let lineRange = expandToLines(text, sel)
        let block = text.utf16Substring(lineRange)
        let items = block
            .components(separatedBy: "\n")
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
            .map { stripListMarker($0) }
        let body = items.map { "[*]" + $0 }.joined(separator: "\n")
        let snippet = open + "\n" + body + "\n" + close
        return TextEdit(
            range: lineRange,
            replacement: snippet,
            selectionAfter: .caret(lineRange.location + snippet.utf16Length)
        )
    }

    /// Inserts an attachment reference `[ATTACHMENT="id:name"]` at the caret (replacing the selection).
    public static func attachment(id: Int, name: String, in text: String, selection: TextRange) -> TextEdit {
        let safeName = name.replacingOccurrences(of: "\"", with: "'").replacingOccurrences(of: "]", with: ")")
        return insert("[ATTACHMENT=\"\(id):\(safeName)\"]", in: text, selection: selection)
    }

    /// Inserts a reply/mention prefix the same way the forum web UI does.
    public static func reply(postId: Int, authorName: String, in text: String, selection: TextRange) -> TextEdit {
        let sel = selection.clamped(toUTF16Count: text.utf16Length)
        let prefix = needsLeadingNewline(text, at: sel.location) ? "\n" : ""
        return insert(prefix + "[SNAPBACK]\(postId)[/SNAPBACK] [B]\(authorName)[/B], ", in: text, selection: sel)
    }

    // MARK: - Helpers

    static func expandToLines(_ text: String, _ range: TextRange) -> TextRange {
        let units = Array(text.utf16)
        let newline: UTF16.CodeUnit = 10
        var start = min(range.location, units.count)
        while start > 0 && units[start - 1] != newline { start -= 1 }
        var end = min(range.end, units.count)
        // A selection ending right after a newline should not swallow the next line.
        if end > start, end <= units.count, units[end - 1] == newline { end -= 1 }
        while end < units.count && units[end] != newline { end += 1 }
        return TextRange(location: start, length: end - start)
    }

    static func needsLeadingNewline(_ text: String, at location: Int) -> Bool {
        guard location > 0 else { return false }
        let prev = text.utf16Substring(TextRange(location: location - 1, length: 1))
        return prev != "\n"
    }

    static func stripListMarker(_ line: String) -> String {
        for marker in ["- ", "* ", "• ", "[*]"] where line.hasPrefix(marker) {
            return String(line.dropFirst(marker.count)).trimmingCharacters(in: .whitespaces)
        }
        // "1. ", "12) "
        let digits = line.prefix(while: { $0.isNumber })
        if !digits.isEmpty {
            let rest = line.dropFirst(digits.count)
            if rest.hasPrefix(". ") || rest.hasPrefix(") ") {
                return String(rest.dropFirst(2)).trimmingCharacters(in: .whitespaces)
            }
        }
        return line
    }
}

// MARK: - Tag model

public enum BBTag: Hashable, Sendable {
    case bold, italic, underline, strike
    case sup, sub
    case color(String)
    case size(Int)
    case link(String?)
    case image(String?)
    case quote
    case code
    case spoiler(title: String?)
    case list
    case orderedList
    case center, left, right
    case offtop
    case hide
    /// Moderator/curator specific tags.
    case cur, mod, ex

    public var name: String {
        switch self {
        case .bold: "B"
        case .italic: "I"
        case .underline: "U"
        case .strike: "S"
        case .sup: "SUP"
        case .sub: "SUB"
        case .color: "COLOR"
        case .size: "SIZE"
        case .link: "URL"
        case .image: "IMG"
        case .quote: "QUOTE"
        case .code: "CODE"
        case .spoiler: "SPOILER"
        case .list, .orderedList: "LIST"
        case .center: "CENTER"
        case .left: "LEFT"
        case .right: "RIGHT"
        case .offtop: "OFFTOP"
        case .hide: "HIDE"
        case .cur: "CUR"
        case .mod: "MOD"
        case .ex: "EX"
        }
    }
}

// MARK: - String helpers

extension String {
    func utf16HasPrefix(_ prefix: String) -> Bool {
        let p = prefix.utf16Length
        guard utf16Length >= p else { return false }
        return utf16Substring(TextRange(location: 0, length: p)).caseInsensitiveCompare(prefix) == .orderedSame
    }

    func utf16HasSuffix(_ suffix: String) -> Bool {
        let s = suffix.utf16Length
        guard utf16Length >= s else { return false }
        return utf16Substring(TextRange(location: utf16Length - s, length: s)).caseInsensitiveCompare(suffix) == .orderedSame
    }

    var looksLikeURL: Bool {
        let t = trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return (t.hasPrefix("http://") || t.hasPrefix("https://")) && !t.contains(" ")
    }

    /// Escapes a value used inside `attr="…"`.
    public var bbEscapedAttribute: String {
        replacingOccurrences(of: "\"", with: "'")
    }
}
