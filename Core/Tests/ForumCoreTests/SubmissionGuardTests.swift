import XCTest
@testable import ForumCore

final class SubmissionGuardTests: XCTestCase {
    
    func testDoubleTapIsIgnored() {
        var guardState = SubmissionGuard()
        let first = guardState.begin(fingerprint: "a")
        XCTAssertNotNil(first)
        XCTAssertNil(guardState.begin(fingerprint: "a"))
        XCTAssertTrue(guardState.phase.isBusy)
    }
    
    func testSuccess() {
        var g = SubmissionGuard()
        let t = g.begin(fingerprint: "a")!
        g.succeeded(token: t, postId: 10)
        XCTAssertEqual(g.phase, .published(postId: 10))
        // No second publication of the same composer.
        XCTAssertNil(g.begin(fingerprint: "a"))
    }
    
    func testRejectedAllowsRetryAfterReset() {
        var g = SubmissionGuard()
        let t = g.begin(fingerprint: "a")!
        g.failed(token: t, .rejected(reason: "tooLong"), fingerprint: "a")
        XCTAssertEqual(g.phase, .failed(.rejected(reason: "tooLong")))
        XCTAssertNotNil(g.begin(fingerprint: "b"))
    }
    
    func testUncertainRequiresVerificationBeforeResend() {
        var g = SubmissionGuard()
        let t = g.begin(fingerprint: "a")!
        g.failed(token: t, .uncertain(reason: "timeout"), fingerprint: "a")
        XCTAssertEqual(g.phase, .verifying(token: t))
        XCTAssertNil(g.begin(fingerprint: "a"), "must not resend while verifying")
        
        g.verified(token: t, foundPostId: 99)
        XCTAssertEqual(g.phase, .published(postId: 99))
    }
    
    func testUncertainVerifiedMissingAllowsResend() {
        var g = SubmissionGuard()
        let t = g.begin(fingerprint: "a")!
        g.failed(token: t, .uncertain(reason: "timeout"), fingerprint: "a")
        g.verified(token: t, foundPostId: nil)
        XCTAssertEqual(g.phase, .failed(.notSent(reason: "verified-missing")))
        XCTAssertNotNil(g.begin(fingerprint: "a"))
    }
    
    func testStaleTokenIsIgnored() {
        var g = SubmissionGuard()
        let t = g.begin(fingerprint: "a")!
        g.failed(token: t, .notSent(reason: "offline"), fingerprint: "a")
        let t2 = g.begin(fingerprint: "a")!
        g.succeeded(token: t, postId: 1) // late answer for the first attempt
        XCTAssertEqual(g.phase, .sending(token: t2))
    }
    
    func testRestoreFromPendingMarker() {
        let pending = PendingSubmission(startedAt: Date(), contentFingerprint: "fp")
        let g = SubmissionGuard(restoring: pending)
        XCTAssertEqual(g.phase, .verifying(token: pending.token))
        XCTAssertEqual(g.uncertainFingerprint, "fp")
    }
    
    func testFingerprintIgnoresWhitespaceAndAttachmentOrder() {
        XCTAssertEqual(
            SubmissionGuard.fingerprint(text: "Hello   world\n", attachments: [2, 1]),
            SubmissionGuard.fingerprint(text: "hello world", attachments: [1, 2])
        )
        XCTAssertNotEqual(
            SubmissionGuard.fingerprint(text: "hello world", attachments: [1]),
            SubmissionGuard.fingerprint(text: "hello world", attachments: [])
        )
    }
    
    func testVerifierFindsOwnPost() {
        let start = Date(timeIntervalSince1970: 1_000_000)
        let candidates = [
            PublishedPostCandidate(postId: 1, authorId: 5, createdAt: start.addingTimeInterval(-3600), content: "Мой текст"),
            PublishedPostCandidate(postId: 2, authorId: 6, createdAt: start.addingTimeInterval(5), content: "Мой текст"),
            PublishedPostCandidate(postId: 3, authorId: 5, createdAt: start.addingTimeInterval(4), content: "[b]Мой[/b]  &quot;текст&quot;"),
        ]
        let found = PublicationVerifier.find(submittedText: "[B]Мой[/B] \"текст\"", authorId: 5, startedAt: start, among: candidates)
        XCTAssertEqual(found?.postId, 3)
    }
    
    func testVerifierDoesNotMatchOldOrForeignPosts() {
        let start = Date(timeIntervalSince1970: 1_000_000)
        let candidates = [
            PublishedPostCandidate(postId: 1, authorId: 5, createdAt: start.addingTimeInterval(-3600), content: "same"),
            PublishedPostCandidate(postId: 2, authorId: 6, createdAt: start, content: "same"),
        ]
        XCTAssertNil(PublicationVerifier.find(submittedText: "same", authorId: 5, startedAt: start, among: candidates))
    }
    
    func testVerifierAcceptsMergedPost() {
        let start = Date(timeIntervalSince1970: 1_000_000)
        let merged = PublishedPostCandidate(postId: 8, authorId: 5, createdAt: start.addingTimeInterval(-60),
                                            content: "earlier text\n[mergetime]1000[/mergetime]\nnew reply")
        XCTAssertEqual(PublicationVerifier.find(submittedText: "new reply", authorId: 5, startedAt: start, among: [merged])?.postId, 8)
    }
}
