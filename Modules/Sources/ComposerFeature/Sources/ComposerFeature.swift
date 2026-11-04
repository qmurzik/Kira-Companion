//
//  ComposerFeature.swift
//  ComposerFeature
//
//  ForPDA Next message composer (forum replies, post edits, QMS).
//
//  Guarantees:
//   • the draft is saved continuously (debounced) and on every lifecycle exit;
//   • the draft is deleted only after the server *confirmed* the publication;
//   • a publish is never repeated automatically; after an uncertain result the topic/dialog is
//     checked for the message first (`ComposerBackend.findPublished`);
//   • uploads run one at a time (the socket is locked during upload), each can be cancelled,
//     retried or removed; local copies live in the draft folder so they survive relaunches.
//

import Foundation
import ComposableArchitecture
import ForumCore
import Models
import PersistenceKeys
import SharedUI
import APIClient

@Reducer
public struct ComposerFeature: Sendable {

    public init() {}

    // MARK: - State

    @ObservableState
    public struct State: Equatable {

        public enum Tab: String, Equatable, Sendable, CaseIterable {
            case editor, preview
        }

        public enum PreviewState: Equatable {
            case idle
            case loading
            case loaded(ComposerPreview)
            case failed(String)
        }

        /// Text that the reducer wants inserted at the caret (quotes added while the composer is
        /// open, attachment references). Applied by the view through the text controller.
        public struct PendingInsertion: Equatable, Identifiable, Sendable {
            public enum Kind: Equatable, Sendable {
                case snippet(String)
                case quote(text: String, source: QuoteSource)
                /// Remove every `[attachment="id:…"]` reference to a deleted attachment.
                case removeAttachment(serverId: Int)
                /// `[SNAPBACK]id[/SNAPBACK] [B]Nick[/B], ` on its own line.
                case reply(postId: Int, authorName: String)
            }
            public var id: UUID
            public var kind: Kind
            
            public init(id: UUID = UUID(), kind: Kind) {
                self.id = id
                self.kind = kind
            }
        }

        public let target: ComposerTarget
        public let contextTitle: String
        /// Initial content for a new draft (edit: original post; reply: quote / snapback prefix).
        let seedText: String
        let seedAttachments: [ComposerAttachment]

        public var isLoaded = false
        public var text = ""
        public var selection = TextRange.caret(0)
        public var editReason = ""
        public var attachments: IdentifiedArrayOf<ComposerAttachment> = []
        public var tab: Tab = .editor
        public var preview: PreviewState = .idle
        public var isExpanded: Bool
        public var submission = SubmissionGuard()
        public var pendingSubmission: PendingSubmission?
        /// Can be pre-filled by the presenter (reply / quote); applied once the editor is on screen.
        public var pendingInsertions: [PendingInsertion] = []
        public var issues: [BBIssue] = []
        public var lastSavedAt: Date?
        public var restoredFromDraft = false
        @Presents public var alert: AlertState<Action.Alert>?
        @Shared(.appStorage("composer.showCharacterCount")) public var showCharacterCount = false
        @Shared(.appStorage("composer.highlightTags")) public var highlightTags = true
        @Shared(.appStorage("composer.fontScale")) public var fontScale: Double = 1.0
        @Shared(.userSession) var userSession: UserSession?

        public var draftKey: DraftKey { target.draftKey }

        public var isBusy: Bool { submission.phase.isBusy }

        public var isUploading: Bool { attachments.contains { $0.status.isActive } }

        public var canSend: Bool {
            guard isLoaded, !isBusy, !isUploading else { return false }
            if case .published = submission.phase { return false }
            let hasText = !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            return hasText || (!target.isQMS && !uploadedIds.isEmpty)
        }

        public var uploadedIds: [Int] {
            attachments.compactMap(\.serverId)
        }

        public var characterCount: Int { text.count }

        public var isEdit: Bool { if case .edit = target { true } else { false } }

        public init(
            target: ComposerTarget,
            contextTitle: String,
            seedText: String = "",
            seedAttachments: [ComposerAttachment] = [],
            expanded: Bool = false
        ) {
            self.target = target
            self.contextTitle = contextTitle
            self.seedText = seedText
            self.seedAttachments = seedAttachments
            self.isExpanded = expanded
        }
    }

    // MARK: - Action

    public enum Action: BindableAction, Equatable {
        case binding(BindingAction<State>)
        case alert(PresentationAction<Alert>)
        case onAppear
        case onDisappear
        case textChanged(String, TextRange)
        case selectionChanged(TextRange)
        case tabChanged(State.Tab)
        case expandToggled
        case sendTapped
        case closeTapped
        case insertionApplied(UUID)
        case addQuote(text: String, source: QuoteSource)
        case addReply(postId: Int, authorName: String)
        case insertSnippet(String)
        case scenePhaseBackground

        // Attachments
        case filesPicked([PickedFile])
        case removeAttachment(ComposerAttachment.ID)
        case retryAttachment(ComposerAttachment.ID)
        case cancelAttachment(ComposerAttachment.ID)
        case renameAttachment(ComposerAttachment.ID, String)
        case insertAttachmentTapped(ComposerAttachment.ID)

        case `internal`(Internal)
        case delegate(Delegate)

        @CasePathable
        public enum Alert: Equatable, Sendable {
            case keepDraft
            case discardDraft
            case resendAfterVerification
            case ok
        }

        @CasePathable
        public enum Internal: Equatable {
            case draftLoaded(Draft?)
            case saveDraft
            case draftSaved(Date)
            case fileStored(ComposerAttachment.ID, String)
            case startNextUpload
            case uploadEvent(ComposerAttachment.ID, UploadEvent)
            case uploadFailed(ComposerAttachment.ID, String)
            case previewResult(Result<ComposerPreview, ComposerError>)
            case publishResult(token: UUID, Result<PublishOutcome, ComposerError>)
            case verificationResult(token: UUID, Result<Int?, ComposerError>)
        }

        @CasePathable
        public enum Delegate: Equatable {
            case published(postId: Int?, topicId: Int?, offset: Int?)
            case close
        }
    }

    // MARK: - Dependencies

    @Dependency(\.draftClient) var draftClient
    @Dependency(\.composerBackend) var backend
    @Dependency(\.continuousClock) var clock
    @Dependency(\.date.now) var now
    @Dependency(\.uuid) var uuid

    enum CancelID: Hashable {
        case saveDraft
        case upload
        case preview
    }

    // MARK: - Body

    public var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce<State, Action> { state, action in
            switch action {

            // MARK: Lifecycle

            case .onAppear:
                guard !state.isLoaded else { return .none }
                return .run { [key = state.draftKey] send in
                    await send(.internal(.draftLoaded(await draftClient.load(key))))
                }

            case let .internal(.draftLoaded(draft)):
                state.isLoaded = true
                if let draft {
                    state.restoredFromDraft = !draft.text.isEmpty
                    state.text = draft.text
                    state.selection = draft.selection
                    state.editReason = draft.editReason
                    state.attachments = IdentifiedArray(uniqueElements: draft.attachments.map(ComposerAttachment.init(draft:)))
                    state.pendingSubmission = draft.pendingSubmission
                    if let pending = draft.pendingSubmission {
                        // The app was closed while sending: verify before anything else.
                        state.submission = SubmissionGuard(restoring: pending)
                        return verify(token: pending.token, state: &state)
                    }
                } else {
                    state.text = state.seedText
                    state.selection = .caret(state.seedText.utf16.count)
                    state.attachments = IdentifiedArray(uniqueElements: state.seedAttachments)
                }
                state.issues = BBValidator.issues(in: state.text)
                return .send(.internal(.startNextUpload))

            case .onDisappear, .scenePhaseBackground:
                return saveNow(state)

            case let .textChanged(text, selection):
                guard text != state.text || selection != state.selection else { return .none }
                state.text = text
                state.selection = selection
                return scheduleSave()

            case let .selectionChanged(selection):
                state.selection = selection
                return .none

            case .internal(.saveDraft):
                return saveNow(state)

            case let .internal(.draftSaved(date)):
                state.lastSavedAt = date
                state.issues = BBValidator.issues(in: state.text)
                return .none

            // MARK: Tabs & preview

            case let .tabChanged(tab):
                state.tab = tab
                guard tab == .preview else { return .cancel(id: CancelID.preview) }
                state.preview = .loading
                state.issues = BBValidator.issues(in: state.text)
                return .run { [target = state.target, text = state.text, ids = state.uploadedIds] send in
                    let result = await Result { try await backend.preview(target, text, ids) }
                        .mapError(ComposerError.init)
                    await send(.internal(.previewResult(result)))
                }
                .cancellable(id: CancelID.preview, cancelInFlight: true)

            case let .internal(.previewResult(.success(preview))):
                state.preview = .loaded(preview)
                return .none

            case let .internal(.previewResult(.failure(error))):
                state.preview = .failed(error.message)
                return .none

            case .expandToggled:
                state.isExpanded.toggle()
                return .none

            // MARK: Insertions

            case let .addQuote(text, source):
                state.pendingInsertions.append(.init(id: uuid(), kind: .quote(text: text, source: source)))
                return .none

            case let .addReply(postId, authorName):
                state.pendingInsertions.append(.init(id: uuid(), kind: .reply(postId: postId, authorName: authorName)))
                return .none
                
            case let .insertSnippet(snippet):
                state.pendingInsertions.append(.init(id: uuid(), kind: .snippet(snippet)))
                return .none

            case let .insertionApplied(id):
                state.pendingInsertions.removeAll { $0.id == id }
                return .none

            // MARK: Attachments

            case let .filesPicked(files):
                var effects: [Effect<Action>] = []
                for file in files {
                    let attachment = ComposerAttachment(
                        id: uuid(),
                        fileName: file.fileName,
                        byteCount: file.data.count,
                        isImage: file.isImage,
                        thumbnail: file.thumbnail,
                        status: .queued
                    )
                    state.attachments.append(attachment)
                    effects.append(.run { [key = state.draftKey] send in
                        // Keep a local copy with the draft so the file survives app restarts.
                        if let stored = try? await draftClient.storeAttachment(file.data, file.fileName, key) {
                            await send(.internal(.fileStored(attachment.id, stored)))
                        }
                    })
                }
                effects.append(.send(.internal(.startNextUpload)))
                return .concatenate(effects)

            case let .internal(.fileStored(id, name)):
                state.attachments[id: id]?.localFileName = name
                return .merge(scheduleSave(), .send(.internal(.startNextUpload)))

            case .internal(.startNextUpload):
                guard !state.attachments.contains(where: { $0.status.isActive }),
                      let next = state.attachments.first(where: { $0.status == .queued && $0.localFileName != nil })
                else { return .none }
                state.attachments[id: next.id]?.status = .uploading(progress: 0)
                return .run { [key = state.draftKey, isQMS = state.target.isQMS, next] send in
                    guard let name = next.localFileName, let data = await draftClient.attachmentData(name, key) else {
                        await send(.internal(.uploadFailed(next.id, "Файл недоступен")))
                        return
                    }
                    do {
                        for try await event in backend.upload(next.fileName, data, isQMS) {
                            await send(.internal(.uploadEvent(next.id, event)))
                        }
                    } catch is CancellationError {
                        // handled by cancelAttachment
                    } catch {
                        await send(.internal(.uploadFailed(next.id, ComposerError(error).message)))
                    }
                }
                .cancellable(id: CancelID.upload, cancelInFlight: true)

            case let .internal(.uploadEvent(id, event)):
                guard state.attachments[id: id] != nil else { return .none }
                switch event.kind {
                case let .progress(value):
                    state.attachments[id: id]?.status = .uploading(progress: min(max(value, 0), 1))
                    return .none
                case let .initialized(md5):
                    // The same file is already attached: drop this copy.
                    if state.attachments.contains(where: { $0.id != id && $0.md5 == md5 && $0.serverId != nil }) {
                        state.attachments.remove(id: id)
                        return .merge(.cancel(id: CancelID.upload), .send(.internal(.startNextUpload)), scheduleSave())
                    }
                    state.attachments[id: id]?.md5 = md5
                    return .none
                case let .done(serverId):
                    state.attachments[id: id]?.serverId = serverId
                    state.attachments[id: id]?.status = .uploaded
                    return .merge(scheduleSave(), .send(.internal(.startNextUpload)))
                case let .failed(message):
                    state.attachments[id: id]?.status = .failed(message)
                    return .merge(scheduleSave(), .send(.internal(.startNextUpload)))
                }

            case let .internal(.uploadFailed(id, message)):
                state.attachments[id: id]?.status = .failed(message)
                return .merge(scheduleSave(), .send(.internal(.startNextUpload)))

            case let .cancelAttachment(id):
                guard state.attachments[id: id]?.status.isActive == true else { return .none }
                state.attachments[id: id]?.status = .failed("Загрузка отменена")
                return .merge(.cancel(id: CancelID.upload), .send(.internal(.startNextUpload)))

            case let .retryAttachment(id):
                guard let attachment = state.attachments[id: id], case .failed = attachment.status else { return .none }
                state.attachments[id: id]?.status = .queued
                return .send(.internal(.startNextUpload))

            case let .removeAttachment(id):
                guard let attachment = state.attachments.remove(id: id) else { return .none }
                var effects: [Effect<Action>] = [scheduleSave()]
                if attachment.status.isActive {
                    effects.append(.cancel(id: CancelID.upload))
                    effects.append(.send(.internal(.startNextUpload)))
                }
                if let serverId = attachment.serverId {
                    // Remove references from the text as well (applied by the text view, which owns the text).
                    state.pendingInsertions.append(.init(id: uuid(), kind: .removeAttachment(serverId: serverId)))
                }
                if let name = attachment.localFileName {
                    effects.append(.run { [key = state.draftKey] _ in await draftClient.removeAttachment(name, key) })
                }
                return .merge(effects)

            case let .renameAttachment(id, newName):
                guard let attachment = state.attachments[id: id], attachment.serverId == nil else { return .none }
                state.attachments[id: id]?.fileName = ComposerAttachment.renamed(attachment.fileName, to: newName)
                return scheduleSave()

            case let .insertAttachmentTapped(id):
                guard let attachment = state.attachments[id: id], let serverId = attachment.serverId else { return .none }
                let edit = BBFormatter.attachment(id: serverId, name: attachment.fileName, in: "", selection: .caret(0))
                state.pendingInsertions.append(.init(id: uuid(), kind: .snippet(edit.replacement)))
                return .none

            // MARK: Sending

            case .sendTapped:
                guard state.canSend else { return .none }
                let fingerprint = SubmissionGuard.fingerprint(text: state.text, attachments: state.uploadedIds)
                guard let token = state.submission.begin(fingerprint: fingerprint) else { return .none }
                let pending = PendingSubmission(token: token, startedAt: now, contentFingerprint: fingerprint)
                state.pendingSubmission = pending
                let draft = makeDraft(state)
                return .run { [target = state.target, text = state.text, ids = state.uploadedIds, reason = state.editReason] send in
                    // 1. Persist the marker *before* sending (crash/kill during send is detectable).
                    try? await draftClient.save(draft)
                    // 2. Send exactly once.
                    let result = await Result { try await backend.publish(target, text, ids, reason) }
                        .mapError(ComposerError.init)
                    await send(.internal(.publishResult(token: token, result)))
                }

            case let .internal(.publishResult(token, .success(.published(postId, topicId, offset)))):
                state.submission.succeeded(token: token, postId: postId)
                state.pendingSubmission = nil
                return .run { [key = state.draftKey] send in
                    // Draft is removed only now — after server confirmation.
                    try? await draftClient.delete(key)
                    await send(.delegate(.published(postId: postId, topicId: topicId, offset: offset)))
                }

            case let .internal(.publishResult(token, .success(.rejected(message)))):
                let fingerprint = SubmissionGuard.fingerprint(text: state.text, attachments: state.uploadedIds)
                state.submission.failed(token: token, .rejected(reason: message), fingerprint: fingerprint)
                state.pendingSubmission = nil
                state.alert = .error(title: "Не отправлено", message: message + "\nТекст сохранён в черновике.")
                return saveNow(state)

            case let .internal(.publishResult(token, .failure(error))):
                let fingerprint = SubmissionGuard.fingerprint(text: state.text, attachments: state.uploadedIds)
                if error.mayHaveBeenProcessed {
                    // Unknown outcome: do NOT resend — check the server first.
                    state.submission.failed(token: token, .uncertain(reason: error.message), fingerprint: fingerprint)
                    return verify(token: token, state: &state)
                }
                state.submission.failed(token: token, .notSent(reason: error.message), fingerprint: fingerprint)
                state.pendingSubmission = nil
                state.alert = .error(title: "Не отправлено", message: error.message + "\nТекст сохранён, можно отправить ещё раз.")
                return saveNow(state)

            case let .internal(.verificationResult(token, .success(found))):
                state.submission.verified(token: token, foundPostId: found)
                state.pendingSubmission = nil
                if let found {
                    return .run { [key = state.draftKey, target = state.target] send in
                        try? await draftClient.delete(key)
                        let topicId: Int? = if case let .reply(id) = target { id } else { nil }
                        await send(.delegate(.published(postId: found, topicId: topicId, offset: nil)))
                    }
                }
                state.alert = .error(
                    title: "Сообщение не опубликовано",
                    message: "Сервер не получил сообщение. Текст сохранён — можно отправить снова."
                )
                return saveNow(state)

            case let .internal(.verificationResult(token, .failure(error))):
                state.submission.verificationUnavailable(token: token)
                // Keep the pending marker: we still don't know.
                state.alert = AlertState {
                    TextState("Не удалось проверить отправку")
                } actions: {
                    ButtonState(role: .cancel, action: .ok) { TextState("Подождать") }
                    ButtonState(role: .destructive, action: .resendAfterVerification) { TextState("Отправить всё равно") }
                } message: {
                    TextState("\(error.message). Сообщение могло уже появиться в теме. Проверьте тему или отправьте ещё раз — возможен дубль.")
                }
                return saveNow(state)

            // MARK: Closing

            case .closeTapped:
                if state.isBusy { return .none }
                let hasContent = !state.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !state.attachments.isEmpty
                guard hasContent, state.text != state.seedText else {
                    return .merge(saveNow(state), .send(.delegate(.close)))
                }
                state.alert = AlertState {
                    TextState("Закрыть редактор?")
                } actions: {
                    ButtonState(action: .keepDraft) { TextState("Сохранить черновик") }
                    ButtonState(role: .destructive, action: .discardDraft) { TextState("Удалить черновик") }
                    ButtonState(role: .cancel, action: .ok) { TextState("Продолжить") }
                } message: {
                    TextState("Черновик сохраняется автоматически и откроется при следующем ответе.")
                }
                return .none

            case .alert(.presented(.keepDraft)):
                return .merge(saveNow(state), .send(.delegate(.close)))

            case .alert(.presented(.discardDraft)):
                return .merge(
                    .cancel(id: CancelID.saveDraft),
                    .cancel(id: CancelID.upload),
                    .run { [key = state.draftKey] send in
                        try? await draftClient.delete(key)
                        await send(.delegate(.close))
                    }
                )

            case .alert(.presented(.resendAfterVerification)):
                state.submission.reset()
                state.pendingSubmission = nil
                return .send(.sendTapped)

            case .alert(.presented(.ok)):
                state.submission.reset()
                return .none

            case .alert(.dismiss):
                state.submission.reset()
                return .none

            case .binding, .alert, .delegate:
                return .none
            }
        }
        .ifLet(\.$alert, action: \.alert)
    }

    // MARK: - Helpers

    private func scheduleSave() -> Effect<Action> {
        .run { send in
            try await clock.sleep(for: .milliseconds(600))
            await send(.internal(.saveDraft))
        }
        .cancellable(id: CancelID.saveDraft, cancelInFlight: true)
    }

    private func saveNow(_ state: State) -> Effect<Action> {
        guard state.isLoaded else { return .none }
        let draft = makeDraft(state)
        return .merge(
            .cancel(id: CancelID.saveDraft),
            .run { [now] send in
                do {
                    try await draftClient.save(draft)
                    await send(.internal(.draftSaved(now)))
                } catch {
                    // Disk full etc. Text is still in memory; next save retries.
                }
            }
        )
    }

    private func makeDraft(_ state: State) -> Draft {
        Draft(
            key: state.draftKey,
            text: state.text,
            selection: state.selection,
            attachments: state.attachments.map(\.draftAttachment),
            editReason: state.editReason,
            contextTitle: state.contextTitle,
            updatedAt: now,
            pendingSubmission: state.pendingSubmission
        )
    }

    private func verify(token: UUID, state: inout State) -> Effect<Action> {
        guard let userId = state.userSession?.userId else {
            return .send(.internal(.verificationResult(token: token, .failure(ComposerError(message: "Нет активной сессии", mayHaveBeenProcessed: true)))))
        }
        let startedAt = state.pendingSubmission?.startedAt ?? now
        return .run { [target = state.target, text = state.text] send in
            // Give the server a moment to process the request before looking for it.
            try? await clock.sleep(for: .seconds(2))
            var lastError: ComposerError?
            for attempt in 0..<3 {
                do {
                    let found = try await backend.findPublished(target, text, userId, startedAt)
                    await send(.internal(.verificationResult(token: token, .success(found))))
                    return
                } catch {
                    lastError = ComposerError(error)
                    try? await clock.sleep(for: .seconds(Double(2 << attempt)))
                }
            }
            await send(.internal(.verificationResult(token: token, .failure(lastError ?? ComposerError(message: "Нет связи", mayHaveBeenProcessed: true)))))
        }
    }

    /// UTF-16 ranges of `[attachment="id:…"]` references (last first, ready for removal).
    public static func attachmentReferenceRanges(_ serverId: Int, in text: String) -> [TextRange] {
        guard let regex = try? NSRegularExpression(pattern: #"\[attachment="?\#(serverId):[^\]]*\]"#, options: [.caseInsensitive]) else { return [] }
        let range = NSRange(location: 0, length: (text as NSString).length)
        return regex.matches(in: text, range: range)
            .map { TextRange(location: $0.range.location, length: $0.range.length) }
            .reversed()
    }
}

// MARK: - Errors

public struct ComposerError: Error, Equatable, Sendable {
    public var message: String
    public var mayHaveBeenProcessed: Bool

    public init(message: String, mayHaveBeenProcessed: Bool) {
        self.message = message
        self.mayHaveBeenProcessed = mayHaveBeenProcessed
    }

    public init(_ error: any Error) {
        if let composer = error as? ComposerError {
            self = composer
            return
        }
        let kind = ConnectionSupervisor.classify(error)
        self.message = kind == .unknown ? error.localizedDescription : kind.userMessage
        // Unknown errors are treated as "may have been processed" — the safe side.
        self.mayHaveBeenProcessed = kind.mayHaveBeenProcessed
    }
}

// MARK: - Alerts

extension AlertState where Action == ComposerFeature.Action.Alert {
    static func error(title: String, message: String) -> Self {
        AlertState {
            TextState(title)
        } actions: {
            ButtonState(action: .ok) { TextState("OK") }
        } message: {
            TextState(message)
        }
    }
}
