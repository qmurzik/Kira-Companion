//
//  ComposerFeatureTests.swift
//  ComposerFeature
//
//  Reducer tests with a mock backend and in-memory drafts. Nothing is sent to 4PDA.
//

import XCTest
import ComposableArchitecture
import ForumCore
import Models
@testable import ComposerFeature

@MainActor
final class ComposerFeatureTests: XCTestCase {

    let target = ComposerTarget.reply(topicId: 42)

    private func makeStore(
        backend: ComposerBackend,
        drafts: DraftClient = .inMemory(),
        initial: ComposerFeature.State? = nil
    ) -> TestStoreOf<ComposerFeature> {
        let store = TestStore(initialState: initial ?? ComposerFeature.State(target: target, contextTitle: "Topic")) {
            ComposerFeature()
        } withDependencies: {
            $0.composerBackend = backend
            $0.draftClient = drafts
            $0.continuousClock = ImmediateClock()
            $0.date.now = Date(timeIntervalSince1970: 1_000)
            $0.uuid = .incrementing
        }
        store.exhaustivity = .off(showSkippedAssertions: false)
        return store
    }

    private func loaded(_ store: TestStoreOf<ComposerFeature>, text: String = "") async {
        await store.send(.onAppear)
        await store.receive(\.internal.draftLoaded)
        if !text.isEmpty {
            await store.send(.textChanged(text, .caret(text.utf16.count)))
        }
    }

    // MARK: - Drafts

    func testDraftIsRestoredOnOpen() async {
        let drafts = DraftClient.inMemory()
        try? await drafts.save(Draft(key: target.draftKey, text: "unfinished", selection: .caret(3)))
        let store = makeStore(backend: .testValue, drafts: drafts)
        await store.send(.onAppear)
        await store.receive(\.internal.draftLoaded) {
            $0.isLoaded = true
            $0.text = "unfinished"
            $0.selection = .caret(3)
            $0.restoredFromDraft = true
        }
    }

    func testSeedTextUsedWhenNoDraft() async {
        let store = makeStore(backend: .testValue, initial: ComposerFeature.State(target: target, contextTitle: "", seedText: "seed"))
        await store.send(.onAppear)
        await store.receive(\.internal.draftLoaded) {
            $0.isLoaded = true
            $0.text = "seed"
            $0.selection = .caret(4)
        }
    }

    func testTypingAutosavesDraft() async {
        let drafts = DraftClient.inMemory()
        let store = makeStore(backend: .testValue, drafts: drafts)
        await loaded(store, text: "hello")
        await store.receive(\.internal.saveDraft)
        await store.receive(\.internal.draftSaved)
        let saved = await drafts.load(target.draftKey)
        XCTAssertEqual(saved?.text, "hello")
    }

    // MARK: - Sending

    func testSuccessfulSendDeletesDraftAndReportsPost() async {
        let drafts = DraftClient.inMemory()
        let sent = LockIsolated<[String]>([])
        let backend = ComposerBackend(
            publish: { _, text, _, _ in sent.withValue { $0.append(text) }; return .published(postId: 7, topicId: 42, offset: 20) },
            preview: ComposerBackend.testValue.preview,
            findPublished: { _, _, _, _ in XCTFail("no verification expected"); return nil },
            upload: ComposerBackend.testValue.upload
        )
        let store = makeStore(backend: backend, drafts: drafts)
        await loaded(store, text: "Answer")
        await store.send(.sendTapped)
        await store.receive(\.internal.publishResult)
        await store.receive(\.delegate.published)
        XCTAssertEqual(sent.value, ["Answer"])
        // The sheet disappearing after publication must not resurrect the draft.
        await store.send(.onDisappear)
        let draft = await drafts.load(target.draftKey)
        XCTAssertNil(draft, "draft is removed after confirmation")
    }

    func testDoubleTapSendsOnce() async {
        let calls = LockIsolated(0)
        let gate = AsyncStream<Void>.makeStream()
        let backend = ComposerBackend(
            publish: { _, _, _, _ in
                calls.withValue { $0 += 1 }
                for await _ in gate.stream { break }
                return .published(postId: 1, topicId: 42, offset: 0)
            },
            preview: ComposerBackend.testValue.preview,
            findPublished: ComposerBackend.testValue.findPublished,
            upload: ComposerBackend.testValue.upload
        )
        let store = makeStore(backend: backend)
        await loaded(store, text: "x")
        await store.send(.sendTapped)
        await store.send(.sendTapped) // ignored: busy
        XCTAssertTrue(store.state.isBusy)
        gate.continuation.yield()
        await store.receive(\.internal.publishResult)
        XCTAssertEqual(calls.value, 1)
    }

    func testRejectionKeepsDraftAndText() async {
        let drafts = DraftClient.inMemory()
        let backend = ComposerBackend(
            publish: { _, _, _, _ in .rejected("Сообщение слишком длинное") },
            preview: ComposerBackend.testValue.preview,
            findPublished: ComposerBackend.testValue.findPublished,
            upload: ComposerBackend.testValue.upload
        )
        let store = makeStore(backend: backend, drafts: drafts)
        await loaded(store, text: "long text")
        await store.send(.sendTapped)
        await store.receive(\.internal.publishResult)
        XCTAssertEqual(store.state.text, "long text")
        XCTAssertNotNil(store.state.alert)
        await store.receive(\.internal.draftSaved)
        let draft = await drafts.load(target.draftKey)
        XCTAssertEqual(draft?.text, "long text")
        XCTAssertNil(draft?.pendingSubmission)
    }

    func testUncertainResultIsVerifiedInsteadOfResent() async {
        let publishCalls = LockIsolated(0)
        let backend = ComposerBackend(
            publish: { _, _, _, _ in
                publishCalls.withValue { $0 += 1 }
                throw ComposerError(message: "timeout", mayHaveBeenProcessed: true)
            },
            preview: ComposerBackend.testValue.preview,
            findPublished: { _, text, _, _ in text == "maybe sent" ? 99 : nil },
            upload: ComposerBackend.testValue.upload
        )
        var initial = ComposerFeature.State(target: target, contextTitle: "")
        initial.$userSession.withLock { $0 = .init(userId: 5, token: "t", isHidden: false) }
        let store = makeStore(backend: backend, initial: initial)
        await loaded(store, text: "maybe sent")
        await store.send(.sendTapped)
        await store.receive(\.internal.publishResult)
        if case .verifying = store.state.submission.phase {} else { XCTFail("expected verifying") }
        await store.receive(\.internal.verificationResult)
        await store.receive(\.delegate.published)
        XCTAssertEqual(publishCalls.value, 1, "never re-sent automatically")
        XCTAssertEqual(store.state.submission.phase, .published(postId: 99))
    }

    func testUncertainButMissingAllowsManualResend() async {
        let backend = ComposerBackend(
            publish: { _, _, _, _ in throw ComposerError(message: "connection lost", mayHaveBeenProcessed: true) },
            preview: ComposerBackend.testValue.preview,
            findPublished: { _, _, _, _ in nil },
            upload: ComposerBackend.testValue.upload
        )
        var initial = ComposerFeature.State(target: target, contextTitle: "")
        initial.$userSession.withLock { $0 = .init(userId: 5, token: "t", isHidden: false) }
        let store = makeStore(backend: backend, initial: initial)
        await loaded(store, text: "lost")
        await store.send(.sendTapped)
        await store.receive(\.internal.publishResult)
        await store.receive(\.internal.verificationResult)
        XCTAssertEqual(store.state.submission.phase, .failed(.notSent(reason: "verified-missing")))
        XCTAssertNotNil(store.state.alert)
        XCTAssertEqual(store.state.text, "lost")
    }

    func testNotSentErrorKeepsTextAndAllowsRetry() async {
        let backend = ComposerBackend(
            publish: { _, _, _, _ in throw ComposerError(message: "offline", mayHaveBeenProcessed: false) },
            preview: ComposerBackend.testValue.preview,
            findPublished: { _, _, _, _ in XCTFail("not needed"); return nil },
            upload: ComposerBackend.testValue.upload
        )
        let store = makeStore(backend: backend)
        await loaded(store, text: "retry me")
        await store.send(.sendTapped)
        await store.receive(\.internal.publishResult)
        await store.send(.alert(.presented(.ok)))
        XCTAssertTrue(store.state.canSend)
    }

    func testPendingMarkerAfterCrashTriggersVerification() async {
        let drafts = DraftClient.inMemory()
        let pending = PendingSubmission(startedAt: Date(timeIntervalSince1970: 900), contentFingerprint: "fp")
        try? await drafts.save(Draft(key: target.draftKey, text: "sent before crash", pendingSubmission: pending))
        let backend = ComposerBackend(
            publish: { _, _, _, _ in XCTFail("must not resend"); return .rejected("") },
            preview: ComposerBackend.testValue.preview,
            findPublished: { _, _, _, _ in 123 },
            upload: ComposerBackend.testValue.upload
        )
        var initial = ComposerFeature.State(target: target, contextTitle: "")
        initial.$userSession.withLock { $0 = .init(userId: 5, token: "t", isHidden: false) }
        let store = makeStore(backend: backend, drafts: drafts, initial: initial)
        await store.send(.onAppear)
        await store.receive(\.internal.draftLoaded)
        await store.receive(\.internal.verificationResult)
        await store.receive(\.delegate.published)
    }

    // MARK: - Attachments

    func testAttachmentUploadAndInsert() async {
        let backend = ComposerBackend(
            publish: ComposerBackend.testValue.publish,
            preview: ComposerBackend.testValue.preview,
            findPublished: ComposerBackend.testValue.findPublished,
            upload: { _, _, _ in
                AsyncThrowingStream {
                    $0.yield(UploadEvent(kind: .initialized(md5: "abc")))
                    $0.yield(UploadEvent(kind: .progress(0.5)))
                    $0.yield(UploadEvent(kind: .done(serverId: 555)))
                    $0.finish()
                }
            }
        )
        let store = makeStore(backend: backend)
        await loaded(store)
        await store.send(.filesPicked([PickedFile(fileName: "a.jpg", data: Data([1, 2]), isImage: true)]))
        await store.receive(\.internal.fileStored)
        await store.receive(\.internal.uploadEvent)
        await store.receive(\.internal.uploadEvent)
        await store.receive(\.internal.uploadEvent)
        XCTAssertEqual(store.state.uploadedIds, [555])
        let id = store.state.attachments[0].id
        await store.send(.insertAttachmentTapped(id))
        XCTAssertEqual(store.state.pendingInsertions.last?.kind, .snippet("[ATTACHMENT=\"555:a.jpg\"]"))
    }

    func testFailedUploadCanBeRetried() async {
        let attempts = LockIsolated(0)
        let backend = ComposerBackend(
            publish: ComposerBackend.testValue.publish,
            preview: ComposerBackend.testValue.preview,
            findPublished: ComposerBackend.testValue.findPublished,
            upload: { _, _, _ in
                let attempt = attempts.withValue { $0 += 1; return $0 }
                return AsyncThrowingStream {
                    if attempt == 1 {
                        $0.yield(UploadEvent(kind: .failed("Файл слишком большой для 4PDA")))
                    } else {
                        $0.yield(UploadEvent(kind: .done(serverId: 9)))
                    }
                    $0.finish()
                }
            }
        )
        let store = makeStore(backend: backend)
        await loaded(store)
        await store.send(.filesPicked([PickedFile(fileName: "big.zip", data: Data([0]), isImage: false)]))
        await store.receive(\.internal.fileStored)
        await store.receive(\.internal.uploadEvent)
        XCTAssertEqual(store.state.attachments[0].status, .failed("Файл слишком большой для 4PDA"))
        await store.send(.retryAttachment(store.state.attachments[0].id))
        await store.receive(\.internal.uploadEvent)
        XCTAssertEqual(store.state.attachments[0].status, .uploaded)
    }

    func testSendDisabledWhileUploading() async {
        let store = makeStore(backend: .testValue)
        await loaded(store, text: "text")
        await store.send(.binding(.set(\.attachments, [
            ComposerAttachment(id: UUID(), fileName: "a", byteCount: 1, isImage: true, status: .uploading(progress: 0.2))
        ])))
        XCTAssertFalse(store.state.canSend)
    }

    func testRemovingUploadedAttachmentRemovesReferences() async {
        let store = makeStore(backend: .testValue)
        await loaded(store)
        let attachment = ComposerAttachment.existing(serverId: 77, name: "x.png", isImage: true)
        await store.send(.binding(.set(\.attachments, [attachment])))
        await store.send(.removeAttachment(attachment.id))
        XCTAssertEqual(store.state.pendingInsertions.last?.kind, .removeAttachment(serverId: 77))
        let ranges = ComposerFeature.attachmentReferenceRanges(77, in: "a [attachment=\"77:x.png\"] b [ATTACHMENT=\"77:x.png\"]")
        XCTAssertEqual(ranges.count, 2)
        XCTAssertGreaterThan(ranges[0].location, ranges[1].location, "removal goes from the end")
    }

    func testRenameKeepsExtension() {
        XCTAssertEqual(ComposerAttachment.renamed("IMG_0001.jpg", to: "скриншот"), "скриншот.jpg")
        XCTAssertEqual(ComposerAttachment.renamed("archive.zip", to: "new.exe"), "new.zip")
        XCTAssertEqual(ComposerAttachment.renamed("a.txt", to: "   "), "a.txt")
    }

    // MARK: - Preview

    func testPreviewLoadsAndFails() async {
        let fail = LockIsolated(false)
        let backend = ComposerBackend(
            publish: ComposerBackend.testValue.publish,
            preview: { _, text, _ in
                if fail.value { throw ComposerError(message: "Нет связи", mayHaveBeenProcessed: false) }
                return try await ComposerBackend.testValue.preview(.reply(topicId: 1), text, [])
            },
            findPublished: ComposerBackend.testValue.findPublished,
            upload: ComposerBackend.testValue.upload
        )
        let store = makeStore(backend: backend)
        await loaded(store, text: "[B]bold[/B]")
        await store.send(.tabChanged(.preview))
        await store.receive(\.internal.previewResult)
        if case .loaded = store.state.preview {} else { XCTFail("expected loaded preview") }
        fail.setValue(true)
        await store.send(.tabChanged(.editor))
        await store.send(.tabChanged(.preview))
        await store.receive(\.internal.previewResult)
        XCTAssertEqual(store.state.preview, .failed("Нет связи"))
        XCTAssertEqual(store.state.text, "[B]bold[/B]", "text untouched by preview round-trip")
    }
}
