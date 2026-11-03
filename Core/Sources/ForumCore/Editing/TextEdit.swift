//
//  TextEdit.swift
//  ForumCore
//
//  All editor ranges are UTF-16 based, exactly like `UITextView.selectedRange`,
//  so results can be applied to a text view without conversions.
//

import Foundation

/// A UTF-16 range (location + length) — mirrors `NSRange` but is `Sendable`, `Codable`
/// and identical on every platform.
public struct TextRange: Hashable, Codable, Sendable, CustomStringConvertible {
    public var location: Int
    public var length: Int
    
    public init(location: Int, length: Int = 0) {
        self.location = max(0, location)
        self.length = max(0, length)
    }
    
    public static func caret(_ location: Int) -> TextRange { TextRange(location: location, length: 0) }
    
    public var end: Int { location + length }
    public var isEmpty: Bool { length == 0 }
    
    /// Clamps the range so it is valid for a text with `utf16Count` code units.
    public func clamped(toUTF16Count count: Int) -> TextRange {
        let loc = min(max(0, location), count)
        let len = min(max(0, length), count - loc)
        return TextRange(location: loc, length: len)
    }
    
    public var description: String { "{\(location), \(length)}" }
}

/// A single replacement operation: replace `range` of the original text with `replacement`
/// and afterwards select `selectionAfter` (expressed in coordinates of the *resulting* text).
///
/// The editor applies an edit through `UITextView.replace(_:withText:)`, which keeps the
/// system undo stack intact and never resets the whole text (no cursor jumps).
public struct TextEdit: Equatable, Sendable {
    public var range: TextRange
    public var replacement: String
    public var selectionAfter: TextRange
    
    public init(range: TextRange, replacement: String, selectionAfter: TextRange) {
        self.range = range
        self.replacement = replacement
        self.selectionAfter = selectionAfter
    }
    
    /// Applies the edit to a plain string (used by tests, previews and non-UIKit callers).
    public func apply(to text: String) -> String {
        let units = Array(text.utf16)
        let r = range.clamped(toUTF16Count: units.count)
        var result = Array(units[0..<r.location])
        result.append(contentsOf: replacement.utf16)
        result.append(contentsOf: units[r.end..<units.count])
        return String(decoding: result, as: UTF16.self)
    }
}

// MARK: - UTF-16 helpers

extension String {
    /// Substring by UTF-16 range. Out-of-bounds ranges are clamped; never traps.
    public func utf16Substring(_ range: TextRange) -> String {
        let units = Array(utf16)
        let r = range.clamped(toUTF16Count: units.count)
        return String(decoding: units[r.location..<r.end], as: UTF16.self)
    }
    
    var utf16Length: Int { utf16.count }
}
