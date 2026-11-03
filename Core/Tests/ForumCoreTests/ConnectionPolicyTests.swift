import XCTest
@testable import ForumCore

final class ConnectionPolicyTests: XCTestCase {
    let wifi = NetworkPath(isSatisfied: true, interface: .wifi)
    let cell = NetworkPath(isSatisfied: true, interface: .cellular)
    
    func testForegroundConnectsWhenDisconnected() {
        var p = ConnectionPolicy(isForeground: false, path: wifi)
        XCTAssertEqual(p.handle(.appBecameActive), [.connect])
    }
    
    func testBackgroundDisconnectsAndCancelsTimer() {
        var p = ConnectionPolicy(path: wifi)
        _ = p.handle(.transportChanged(.disconnected), random: 0.5)
        XCTAssertTrue(p.isReconnectScheduled)
        XCTAssertEqual(p.handle(.appEnteredBackground), [.cancelScheduledReconnect, .disconnect])
        // Reconnect timer firing late in background must not connect.
        XCTAssertEqual(p.handle(.reconnectTimerFired), [])
    }
    
    func testFailedConnectIsRetriedWithBackoffInsteadOfSwallowed() {
        var p = ConnectionPolicy(path: wifi)
        XCTAssertEqual(p.handle(.connectFailed, random: 0.5), [.scheduleReconnect(after: 1)])
        XCTAssertEqual(p.handle(.reconnectTimerFired), [.connect])
        XCTAssertEqual(p.handle(.connectFailed, random: 0.5), [.scheduleReconnect(after: 2)])
        XCTAssertEqual(p.handle(.reconnectTimerFired), [.connect])
        XCTAssertEqual(p.handle(.connectFailed, random: 0.5), [.scheduleReconnect(after: 4)])
        _ = p.handle(.transportChanged(.ready))
        XCTAssertEqual(p.attempt, 0)
    }
    
    func testBackoffIsCapped() {
        var p = ConnectionPolicy(path: wifi)
        var last: TimeInterval = 0
        for _ in 0..<20 {
            if case let .scheduleReconnect(after)? = p.handle(.connectFailed, random: 0.5).last { last = after }
            _ = p.handle(.reconnectTimerFired)
        }
        XCTAssertEqual(last, 30)
    }
    
    func testNoDuplicateTimers() {
        var p = ConnectionPolicy(path: wifi)
        XCTAssertEqual(p.handle(.transportChanged(.disconnected), random: 0.5).count, 1)
        XCTAssertEqual(p.handle(.connectFailed, random: 0.5), [])
    }
    
    func testLibraryReconnectingCancelsOurTimer() {
        var p = ConnectionPolicy(path: wifi)
        _ = p.handle(.transportChanged(.disconnected), random: 0.5)
        XCTAssertEqual(p.handle(.transportChanged(.connecting)), [.cancelScheduledReconnect])
    }
    
    func testOfflineDoesNotScheduleAndOnlineConnectsImmediately() {
        var p = ConnectionPolicy(path: wifi)
        _ = p.handle(.pathChanged(.offline))
        XCTAssertTrue(p.isOffline)
        XCTAssertEqual(p.handle(.transportChanged(.disconnected), random: 0.5), [])
        XCTAssertEqual(p.handle(.pathChanged(cell)), [.connect])
    }
    
    func testInterfaceSwitchForcesReconnectOfReadySocket() {
        var p = ConnectionPolicy(path: wifi)
        _ = p.handle(.transportChanged(.ready))
        XCTAssertEqual(p.handle(.pathChanged(cell)), [.reconnect])
        // Same interface update (e.g. expensive flag) does nothing.
        XCTAssertEqual(p.handle(.pathChanged(NetworkPath(isSatisfied: true, interface: .cellular, isExpensive: true))), [])
    }
    
    func testStaleSocketReconnectIsRateLimited() {
        var p = ConnectionPolicy(path: wifi)
        _ = p.handle(.transportChanged(.ready))
        let t0 = Date(timeIntervalSince1970: 0)
        XCTAssertEqual(p.handle(.staleConnectionDetected(at: t0)), [.reconnect])
        XCTAssertEqual(p.handle(.staleConnectionDetected(at: t0.addingTimeInterval(3))), [])
        XCTAssertEqual(p.handle(.staleConnectionDetected(at: t0.addingTimeInterval(11))), [.reconnect])
    }
    
    func testUserRetryResetsAttempts() {
        var p = ConnectionPolicy(path: wifi)
        for _ in 0..<5 { _ = p.handle(.connectFailed, random: 0.5); _ = p.handle(.reconnectTimerFired) }
        _ = p.handle(.connectFailed, random: 0.5)
        XCTAssertEqual(p.handle(.userRequestedRetry), [.cancelScheduledReconnect, .connect])
        XCTAssertEqual(p.attempt, 0)
    }
    
    func testJitterBounds() {
        let b = Backoff(base: 1, multiplier: 2, maximum: 30, jitter: 0.2)
        XCTAssertEqual(b.delay(forAttempt: 2, random: 0), 3.2, accuracy: 0.0001)
        XCTAssertEqual(b.delay(forAttempt: 2, random: 1), 4.8, accuracy: 0.0001)
    }
}

final class RequestRetryPolicyTests: XCTestCase {
    let policy = RequestRetryPolicy(maxAttempts: 3, backoff: Backoff(base: 1, multiplier: 2, maximum: 10, jitter: 0))
    
    func testReadsRetryOnTimeout() {
        XCTAssertEqual(policy.decision(for: .timeout, attempt: 1, idempotent: true), .retry(after: 1))
        XCTAssertEqual(policy.decision(for: .timeout, attempt: 2, idempotent: true), .retry(after: 2))
        XCTAssertEqual(policy.decision(for: .timeout, attempt: 3, idempotent: true), .fail)
    }
    
    func testWritesNeverRetryWhenServerMayHaveProcessed() {
        XCTAssertEqual(policy.decision(for: .timeout, attempt: 1, idempotent: false), .fail)
        XCTAssertEqual(policy.decision(for: .connectionLost, attempt: 1, idempotent: false), .fail)
        XCTAssertEqual(policy.decision(for: .http(status: 503), attempt: 1, idempotent: false), .fail)
    }
    
    func testWritesRetryWhenNotSent() {
        XCTAssertEqual(policy.decision(for: .notConnected, attempt: 1, idempotent: false), .retry(after: 1))
        XCTAssertFalse(NetworkErrorKind.notConnected.mayHaveBeenProcessed)
        XCTAssertTrue(NetworkErrorKind.timeout.mayHaveBeenProcessed)
    }
    
    func testOfflineAndCancelledFailFast() {
        XCTAssertEqual(policy.decision(for: .offline, attempt: 1, idempotent: true), .fail)
        XCTAssertEqual(policy.decision(for: .cancelled, attempt: 1, idempotent: true), .fail)
    }
    
    func testURLErrorClassification() {
        XCTAssertEqual(URLErrorClassifier.classify(domain: "NSURLErrorDomain", code: -1009), .offline)
        XCTAssertEqual(URLErrorClassifier.classify(domain: "NSURLErrorDomain", code: -1001), .timeout)
        XCTAssertEqual(URLErrorClassifier.classify(domain: "NSURLErrorDomain", code: -1003), .dns)
        XCTAssertEqual(URLErrorClassifier.classify(domain: "NSURLErrorDomain", code: -1202), .tls)
        XCTAssertEqual(URLErrorClassifier.classify(domain: "NSURLErrorDomain", code: -999), .cancelled)
        XCTAssertEqual(URLErrorClassifier.classify(CancellationError()), .cancelled)
    }
}
