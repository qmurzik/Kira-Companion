//
//  ComposerScreen.swift
//  ComposerFeature
//

import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import ComposableArchitecture
import ForumCore
import SharedUI

public struct ComposerScreen: View {

    @Perception.Bindable public var store: StoreOf<ComposerFeature>
    @StateObject private var controller = ComposerTextController()
    @Environment(\.tintColor) private var tintColor
    @Environment(\.scenePhase) private var scenePhase

    @State private var photoSelection: [PhotosPickerItem] = []
    @State private var isPhotoPickerPresented = false
    @State private var isFileImporterPresented = false
    @State private var prompt: FormatPrompt?
    @State private var promptText = ""
    @State private var renaming: ComposerAttachment?
    @State private var renameText = ""

    public init(store: StoreOf<ComposerFeature>) {
        self.store = store
    }

    public var body: some View {
        WithPerceptionTracking {
            NavigationStack {
                VStack(spacing: 0) {
                    if store.isLoaded {
                        content
                    } else {
                        ProgressView()
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                }
                .background(Color(uiColor: .systemBackground))
                .safeAreaInset(edge: .bottom, spacing: 0) {
                    if store.isLoaded && store.tab == .editor {
                        FormatBar(
                            controller: controller,
                            isQMS: store.target.isQMS,
                            canSend: store.canSend,
                            isBusy: store.isBusy,
                            onPrompt: { kind in
                                // Don't read the clipboard here: it would trigger iOS's paste-permission prompt.
                                promptText = ""
                                prompt = kind
                            },
                            onAttachPhoto: { isPhotoPickerPresented = true },
                            onAttachFile: { isFileImporterPresented = true },
                            onSend: { store.send(.sendTapped) }
                        )
                    }
                }
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { toolbar }
                .alert($store.scope(state: \.alert, action: \.alert))
                .alert(prompt?.title ?? "", isPresented: Binding(get: { prompt != nil }, set: { if !$0 { prompt = nil } })) {
                    TextField(prompt?.placeholder ?? "", text: $promptText)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    Button("Отмена", role: .cancel) { prompt = nil }
                    Button("Вставить") { applyPrompt() }
                }
                .alert("Переименовать файл", isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })) {
                    TextField("Имя файла", text: $renameText)
                    Button("Отмена", role: .cancel) { renaming = nil }
                    Button("Готово") {
                        if let renaming { store.send(.renameAttachment(renaming.id, renameText)) }
                        renaming = nil
                    }
                } message: {
                    Text("Расширение файла сохранится")
                }
                .photosPicker(isPresented: $isPhotoPickerPresented, selection: $photoSelection, maxSelectionCount: 10, matching: .any(of: [.images, .screenshots]))
                .fileImporter(isPresented: $isFileImporterPresented, allowedContentTypes: [.item], allowsMultipleSelection: true) { result in
                    guard case let .success(urls) = result else { return }
                    Task {
                        let files = await Task.detached(priority: .userInitiated) {
                            urls.compactMap { AttachmentProcessing.file(url: $0) }
                        }.value
                        if !files.isEmpty { store.send(.filesPicked(files)) }
                    }
                }
                .onChange(of: photoSelection) { items in
                    guard !items.isEmpty else { return }
                    photoSelection = []
                    loadPhotos(items)
                }
                .onChange(of: store.pendingInsertions) { insertions in
                    applyInsertions(insertions)
                }
                .onChange(of: store.isLoaded) { loaded in
                    // Insertions queued before the editor existed (reply / quote from a post).
                    guard loaded else { return }
                    DispatchQueue.main.async { applyInsertions(store.pendingInsertions) }
                }
                .onChange(of: scenePhase) { phase in
                    if phase != .active { store.send(.scenePhaseBackground) }
                }
            }
            .presentationDetents(store.isExpanded ? [.large] : [.medium, .large])
            .presentationDragIndicator(.visible)
            .presentationBackgroundInteraction(.disabled)
            .interactiveDismissDisabled(store.isBusy)
            .onAppear { store.send(.onAppear) }
            .onDisappear { store.send(.onDisappear) }
        }
    }

    // MARK: - Content

    @ViewBuilder
    private var content: some View {
        if store.tab == .editor {
            VStack(spacing: 0) {
                if store.restoredFromDraft {
                    Label("Восстановлен черновик", systemImage: "clock.arrow.circlepath")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 16)
                        .padding(.top, 8)
                        .transition(.opacity)
                }

                if store.isEdit {
                    TextField("Причина редактирования (необязательно)", text: $store.editReason)
                        .textFieldStyle(.roundedBorder)
                        .padding(.horizontal, 12)
                        .padding(.top, 8)
                }

                ComposerTextView(
                    initialText: store.text,
                    initialSelection: store.selection,
                    placeholder: store.target.isQMS ? "Сообщение" : "Текст сообщения… Выделите текст и нажмите кнопку форматирования",
                    controller: controller,
                    fontScale: CGFloat(store.fontScale),
                    highlightTags: store.highlightTags,
                    autofocus: true,
                    onChange: { text, selection in
                        store.send(.textChanged(text, selection))
                    },
                    onPasteImages: { images in
                        let files = images.enumerated().compactMap { AttachmentProcessing.pasted($0.element, index: $0.offset + 1) }
                        store.send(.filesPicked(files))
                    }
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)

                if !store.issues.isEmpty {
                    IssuesBanner(issues: store.issues)
                }

                if !store.attachments.isEmpty {
                    AttachmentStrip(
                        attachments: store.attachments.elements,
                        onInsert: { store.send(.insertAttachmentTapped($0)) },
                        onRemove: { store.send(.removeAttachment($0)) },
                        onRetry: { store.send(.retryAttachment($0)) },
                        onCancel: { store.send(.cancelAttachment($0)) },
                        onRename: { attachment in
                            renameText = (attachment.fileName as NSString).deletingPathExtension
                            renaming = attachment
                        }
                    )
                }

                if store.showCharacterCount || store.lastSavedAt != nil {
                    statusLine
                }
            }
        } else {
            ComposerPreviewPane(state: store.preview, issues: store.issues) {
                store.send(.tabChanged(.preview))
            }
        }
    }

    private var statusLine: some View {
        HStack {
            if store.lastSavedAt != nil {
                Label("Черновик сохранён", systemImage: "checkmark.circle")
                    .labelStyle(.titleAndIcon)
            }
            Spacer()
            if store.showCharacterCount {
                Text("\(store.characterCount) симв.")
                    .monospacedDigit()
            }
        }
        .font(.caption2)
        .foregroundStyle(.secondary)
        .padding(.horizontal, 16)
        .padding(.vertical, 4)
    }

    // MARK: - Toolbar

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .topBarLeading) {
            Button {
                controller.dismissKeyboard()
                store.send(.closeTapped)
            } label: {
                Image(systemName: "xmark")
            }
            .accessibilityLabel("Закрыть")
            .disabled(store.isBusy)
        }
        ToolbarItem(placement: .principal) {
            Picker("Режим", selection: Binding(get: { store.tab }, set: { store.send(.tabChanged($0)) })) {
                Text("Редактор").tag(ComposerFeature.State.Tab.editor)
                Text("Просмотр").tag(ComposerFeature.State.Tab.preview)
            }
            .pickerStyle(.segmented)
            .frame(maxWidth: 240)
            .accessibilityIdentifier("composer.modePicker")
        }
        ToolbarItemGroup(placement: .topBarTrailing) {
            Button {
                withAnimation(.snappy) { _ = store.send(.expandToggled) }
            } label: {
                Image(systemName: store.isExpanded ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right")
            }
            .accessibilityLabel(store.isExpanded ? "Свернуть" : "Развернуть")

            SendButton(isBusy: store.isBusy, isEnabled: store.canSend) {
                store.send(.sendTapped)
            }
        }
    }

    // MARK: - Actions

    private func applyInsertions(_ insertions: [ComposerFeature.State.PendingInsertion]) {
        guard controller.textView != nil else { return }
        for insertion in insertions {
            switch insertion.kind {
            case let .snippet(snippet):
                controller.insert(snippet)
            case let .quote(text, source):
                controller.apply(QuoteBuilder.insert(quote: text, from: source, into: controller.text, selection: controller.selection))
            case let .reply(postId, authorName):
                if controller.text.contains("[SNAPBACK]\(postId)[/SNAPBACK]") == false {
                    controller.apply(BBFormatter.reply(postId: postId, authorName: authorName, in: controller.text, selection: .caret(controller.text.utf16.count)))
                }
            case let .removeAttachment(serverId):
                for range in ComposerFeature.attachmentReferenceRanges(serverId, in: controller.text) {
                    controller.apply(TextEdit(range: range, replacement: "", selectionAfter: .caret(range.location)))
                }
            }
            store.send(.insertionApplied(insertion.id))
        }
    }

    private func applyPrompt() {
        guard let kind = prompt else { return }
        let value = promptText.trimmingCharacters(in: .whitespacesAndNewlines)
        switch kind {
        case .link: controller.format(.link(value.isEmpty ? nil : value))
        case .image: controller.format(.image(value.isEmpty ? nil : value))
        case .spoiler: controller.format(.spoiler(title: value.isEmpty ? nil : value))
        }
        prompt = nil
        controller.focus()
    }

    private func loadPhotos(_ items: [PhotosPickerItem]) {
        Task {
            var files: [PickedFile] = []
            for item in items {
                guard let data = try? await item.loadTransferable(type: Data.self) else { continue }
                let type = item.supportedContentTypes.first
                let file = await Task.detached(priority: .userInitiated) {
                    AttachmentProcessing.photo(data: data, suggestedName: nil, contentType: type)
                }.value
                if let file { files.append(file) }
            }
            if !files.isEmpty { store.send(.filesPicked(files)) }
        }
    }
}

// MARK: - Prompts

enum FormatPrompt: Identifiable, Equatable {
    case link, image, spoiler
    var id: Self { self }

    var title: String {
        switch self {
        case .link: "Ссылка"
        case .image: "Изображение по ссылке"
        case .spoiler: "Заголовок спойлера"
        }
    }

    var placeholder: String {
        switch self {
        case .link: "https://"
        case .image: "https://…/image.jpg"
        case .spoiler: "Необязательно"
        }
    }
}

// MARK: - Send button

struct SendButton: View {
    let isBusy: Bool
    let isEnabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            if isBusy {
                ProgressView()
            } else {
                Image(systemName: "paperplane.fill")
                    .fontWeight(.semibold)
            }
        }
        .disabled(!isEnabled || isBusy)
        .accessibilityLabel("Отправить")
        .accessibilityIdentifier("composer.send")
    }
}

// MARK: - Issues banner

struct IssuesBanner: View {
    let issues: [BBIssue]

    var body: some View {
        let tags = Array(Set(issues.map { $0.tag.uppercased() })).sorted().joined(separator: ", ")
        Label("Проверьте теги: \(tags)", systemImage: "exclamationmark.triangle")
            .font(.footnote)
            .foregroundStyle(.orange)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.vertical, 6)
            .background(Color.orange.opacity(0.08))
            .accessibilityIdentifier("composer.issues")
    }
}
