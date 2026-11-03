//
//  ComposerTextView.swift
//  ComposerFeature
//
//  Native UITextView-based editor.
//
//  Why the upstream editor lost text / jumped the cursor, and what is different here:
//   • Upstream pushed the whole text back into the UITextView on every SwiftUI update
//     (`uiView.text = content`). That resets the selection, breaks IME composition (marked
//     text, dictation, predictive input) and wipes the undo stack. Here the UITextView is the
//     single source of truth: SwiftUI never assigns `text` after creation.
//   • Formatting used a selection mirrored through TCA state that could be stale; when it was
//     nil, the entire message was replaced by an empty tag pair. Here formatting reads the
//     live selection from the text view and applies a minimal `TextEdit` through
//     `replace(_:withText:)` — undo/redo keeps working and nothing outside the range changes.
//   • The placeholder was stored as real text. It is a separate label now.
//   • Upstream used a non-scrolling text view sized by SwiftUI, which re-laid out the whole
//     screen on each keystroke (slow on long posts). This view scrolls itself.
//

import SwiftUI
import UIKit
import ForumCore

// MARK: - Controller (imperative bridge used by toolbar & reducer insertions)

@MainActor
public final class ComposerTextController: ObservableObject {

    weak var textView: ComposerUITextView?
    @Published public private(set) var canUndo = false
    @Published public private(set) var canRedo = false
    @Published public private(set) var hasSelection = false

    public init() {}

    public var text: String { textView?.text ?? "" }

    public var selection: TextRange {
        guard let textView else { return .caret(0) }
        let r = textView.selectedRange
        return TextRange(location: r.location, length: r.length)
    }

    public var selectedText: String { text.utf16Substring(selection) }

    /// Applies an edit computed by `BBFormatter` / `QuoteBuilder`.
    public func apply(_ edit: TextEdit) {
        guard let textView else { return }
        let count = (textView.text as NSString).length
        let range = edit.range.clamped(toUTF16Count: count)
        // Commit IME composition first; otherwise UIKit may duplicate marked text.
        if textView.markedTextRange != nil { textView.unmarkText() }
        guard let start = textView.position(from: textView.beginningOfDocument, offset: range.location),
              let end = textView.position(from: start, offset: range.length),
              let textRange = textView.textRange(from: start, to: end) else { return }
        textView.replace(textRange, withText: edit.replacement)
        let newCount = (textView.text as NSString).length
        let sel = edit.selectionAfter.clamped(toUTF16Count: newCount)
        textView.selectedRange = NSRange(location: sel.location, length: sel.length)
        textView.scrollRangeToVisible(textView.selectedRange)
        textView.notifyChange()
    }

    public func format(_ tag: BBTag) {
        apply(BBFormatter.apply(tag, in: text, selection: selection))
    }

    public func insert(_ snippet: String, caretOffset: Int? = nil) {
        apply(BBFormatter.insert(snippet, in: text, selection: selection, caretOffset: caretOffset))
    }

    public func undo() {
        textView?.undoManager?.undo()
        textView?.notifyChange()
    }

    public func redo() {
        textView?.undoManager?.redo()
        textView?.notifyChange()
    }

    public func focus() {
        textView?.becomeFirstResponder()
    }

    public func dismissKeyboard() {
        textView?.resignFirstResponder()
    }

    func refreshState() {
        canUndo = textView?.undoManager?.canUndo ?? false
        canRedo = textView?.undoManager?.canRedo ?? false
        hasSelection = (textView?.selectedRange.length ?? 0) > 0
    }
}

// MARK: - UITextView subclass

final class ComposerUITextView: UITextView {

    var onChange: ((String, TextRange) -> Void)?
    var onSelectionChange: ((TextRange) -> Void)?
    /// Images pasted from the clipboard become attachments.
    var onPasteImages: (([UIImage]) -> Void)?
    var onQuoteSelection: ((String) -> Void)?

    let placeholderLabel = UILabel()

    func notifyChange() {
        updatePlaceholder()
        onChange?(text, TextRange(location: selectedRange.location, length: selectedRange.length))
    }

    func updatePlaceholder() {
        placeholderLabel.isHidden = !text.isEmpty
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let inset = textContainerInset
        let padding = textContainer.lineFragmentPadding
        let width = bounds.width - inset.left - inset.right - padding * 2
        let size = placeholderLabel.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude))
        placeholderLabel.frame = CGRect(x: inset.left + padding, y: inset.top, width: width, height: size.height)
    }

    // Paste: images (with no text) become attachments; text pastes go through UIKit normally.
    override func canPerformAction(_ action: Selector, withSender sender: Any?) -> Bool {
        if action == #selector(paste(_:)), UIPasteboard.general.hasImages { return true }
        return super.canPerformAction(action, withSender: sender)
    }

    override func paste(_ sender: Any?) {
        let pasteboard = UIPasteboard.general
        if pasteboard.hasImages, !pasteboard.hasStrings, let images = pasteboard.images, !images.isEmpty {
            onPasteImages?(images)
            return
        }
        super.paste(sender)
    }
}

// MARK: - SwiftUI wrapper

struct ComposerTextView: UIViewRepresentable {

    let initialText: String
    let initialSelection: TextRange
    let placeholder: String
    let controller: ComposerTextController
    let fontScale: CGFloat
    let highlightTags: Bool
    let autofocus: Bool
    let onChange: (String, TextRange) -> Void
    let onPasteImages: ([UIImage]) -> Void

    func makeUIView(context: Context) -> ComposerUITextView {
        let view = ComposerUITextView(frame: .zero, textContainer: nil)
        view.backgroundColor = .clear
        view.font = Self.font(scale: fontScale)
        view.adjustsFontForContentSizeCategory = true
        view.textColor = .label
        view.isScrollEnabled = true
        view.alwaysBounceVertical = true
        view.keyboardDismissMode = .interactive
        view.textContainerInset = UIEdgeInsets(top: 12, left: 12, bottom: 24, right: 12)
        view.autocorrectionType = .default
        view.spellCheckingType = .default
        view.smartQuotesType = .no      // BBCode attributes need straight quotes
        view.smartDashesType = .no
        view.smartInsertDeleteType = .no
        view.dataDetectorTypes = []
        view.allowsEditingTextAttributes = false
        view.accessibilityIdentifier = "composer.textView"

        view.placeholderLabel.text = placeholder
        view.placeholderLabel.font = view.font
        view.placeholderLabel.textColor = .placeholderText
        view.placeholderLabel.numberOfLines = 0
        view.placeholderLabel.adjustsFontForContentSizeCategory = true
        view.placeholderLabel.isAccessibilityElement = false
        view.addSubview(view.placeholderLabel)

        // Text is assigned exactly once.
        view.text = initialText
        let count = (initialText as NSString).length
        let sel = initialSelection.clamped(toUTF16Count: count)
        view.selectedRange = NSRange(location: sel.location, length: sel.length)
        view.updatePlaceholder()

        view.delegate = context.coordinator
        if highlightTags {
            view.textStorage.delegate = context.coordinator
            context.coordinator.highlightAll(view.textStorage)
        }
        view.onChange = { text, selection in
            context.coordinator.parent.onChange(text, selection)
            context.coordinator.parent.controller.refreshState()
        }
        view.onPasteImages = { images in context.coordinator.parent.onPasteImages(images) }
        controller.textView = view

        if autofocus {
            DispatchQueue.main.async { view.becomeFirstResponder() }
        }
        return view
    }

    func updateUIView(_ uiView: ComposerUITextView, context: Context) {
        context.coordinator.parent = self
        if controller.textView !== uiView { controller.textView = uiView }
        // Never touch `text` here. Only presentation properties may change.
        let font = Self.font(scale: fontScale)
        if uiView.font?.pointSize != font.pointSize {
            uiView.font = font
            uiView.placeholderLabel.font = font
        }
    }

    static func font(scale: CGFloat) -> UIFont {
        let base = UIFont.preferredFont(forTextStyle: .body)
        return base.withSize(base.pointSize * scale)
    }

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    @MainActor
    final class Coordinator: NSObject, UITextViewDelegate, NSTextStorageDelegate {
        var parent: ComposerTextView

        init(parent: ComposerTextView) {
            self.parent = parent
        }

        func textViewDidChange(_ textView: UITextView) {
            (textView as? ComposerUITextView)?.notifyChange()
        }

        func textViewDidChangeSelection(_ textView: UITextView) {
            let r = textView.selectedRange
            parent.controller.refreshState()
            // Selection is reported together with text; a pure selection change is only
            // needed for draft restore, so it is sent cheaply without copying the text.
            if textView.markedTextRange == nil {
                (textView as? ComposerUITextView)?.onSelectionChange?(TextRange(location: r.location, length: r.length))
            }
        }

        // MARK: BB tag highlighting (only the edited paragraphs are re-scanned)

        nonisolated(unsafe) private static let tagRegex = try? NSRegularExpression(pattern: #"\[/?[A-Za-z*]+(?:=[^\]\n]*)?(?: [^\]\n]*)?\]"#)

        nonisolated func textStorage(
            _ textStorage: NSTextStorage,
            didProcessEditing editedMask: NSTextStorage.EditActions,
            range editedRange: NSRange,
            changeInLength delta: Int
        ) {
            guard editedMask.contains(.editedCharacters) else { return }
            let paragraph = (textStorage.string as NSString).paragraphRange(for: editedRange)
            Self.highlight(textStorage, in: paragraph)
        }

        func highlightAll(_ storage: NSTextStorage) {
            storage.beginEditing()
            Self.highlight(storage, in: NSRange(location: 0, length: storage.length))
            storage.endEditing()
        }

        nonisolated private static func highlight(_ storage: NSTextStorage, in range: NSRange) {
            guard range.length > 0, NSMaxRange(range) <= storage.length else { return }
            storage.addAttribute(.foregroundColor, value: UIColor.label, range: range)
            tagRegex?.enumerateMatches(in: storage.string, range: range) { match, _, _ in
                guard let match else { return }
                storage.addAttribute(.foregroundColor, value: UIColor.secondaryLabel, range: match.range)
            }
        }
    }
}
