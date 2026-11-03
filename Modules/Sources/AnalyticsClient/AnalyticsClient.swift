//
//  AnalyticsClient.swift
//  ForPDA
//
//  Created by Ilia Lubianoi on 24.03.2024.
//

import Foundation
import ComposableArchitecture
import PersistenceKeys
import LoggerClient
import Models
import OSLog
import UserNotifications

// MARK: - Client

@DependencyClient
public struct AnalyticsClient: Sendable {
    public var configure: @Sendable (AnalyticsConfiguration) -> Void
    public var identify: @Sendable (_ id: String, _ userProperties: [String: Any]?) -> Void
    public var setUserProperties: @Sendable (_ properties: [String: Any]?) -> Void
    public var logout: @Sendable () -> Void
    public var log: @Sendable (any Event) -> Void
    public var registerPushNotificationToken: @Sendable (_ deviceToken: String) -> Void
    public var capturePushNotificationOpened: @Sendable (_ response: UNNotificationResponse) -> Void
    
    public var capture: @Sendable (any Error) -> Void
    public var addBreadcrumb: @Sendable (_ category: String, _ message: String?, _ data: [String: Any]?, _ type: String?) -> Void
    public var reportFullyDisplayed: @Sendable () -> Void
}

// MARK: - Dependency Keys

extension AnalyticsClient: DependencyKey {
    
    /// ForPDA Next ships without third-party analytics or crash reporting.
    /// All "analytics" calls are reduced to local, privacy-safe OSLog entries:
    /// no identifiers, no user properties and nothing leaves the device.
    public static var liveValue: Self {
        @Dependency(\.logger[.analytics]) var logger
        
        return AnalyticsClient(
            configure: { _ in
                logger.info("Analytics disabled: local logging only")
            },
            identify: { _, _ in },
            setUserProperties: { _ in },
            logout: { },
            log: { event in
                logger.debug("event: \(event.name, privacy: .public)")
            },
            registerPushNotificationToken: { _ in },
            capturePushNotificationOpened: { _ in },
            capture: { error in
                // Only the error *type* is logged publicly; the payload may contain user content.
                logger.error("error: \(String(describing: type(of: error)), privacy: .public) \(String(describing: error), privacy: .private)")
            },
            addBreadcrumb: { category, _, _, _ in
                logger.debug("breadcrumb: \(category, privacy: .public)")
            },
            reportFullyDisplayed: { }
        )
    }
    
    public static let previewValue = Self(
        configure: { _ in },
        identify: { _, _ in },
        setUserProperties: { _ in },
        logout: { },
        log: { event in
            if let properties = event.properties {
                print("[Analytics] \(event.name) (\(properties))")
            } else {
                print("[Analytics] \(event.name)")
            }
        },
        registerPushNotificationToken: { _ in },
        capturePushNotificationOpened: { _ in },
        capture: { error in
            print("[Sentry] \(error)")
        },
        addBreadcrumb: { _, _, _, _ in },
        reportFullyDisplayed: { }
    )
    
    public static let testValue = Self(
        configure: { _ in },
        identify: { _, _ in },
        setUserProperties: { _ in },
        logout: { },
        log: { _ in },
        registerPushNotificationToken: { _ in },
        capturePushNotificationOpened: { _ in },
        capture: { _ in },
        addBreadcrumb: { _, _, _, _ in },
        reportFullyDisplayed: { }
    )
}

// MARK: - Extension

public extension DependencyValues {
    var analyticsClient: AnalyticsClient {
        get { self[AnalyticsClient.self] }
        set { self[AnalyticsClient.self] = newValue }
    }
}

// TODO: Took out from posthog, not public anymore for some reason
extension UUID {
    static func v7() -> Self {
        TimeBasedEpochGenerator.shared.v7()
    }
}

final class TimeBasedEpochGenerator {
    nonisolated(unsafe) static let shared = TimeBasedEpochGenerator()

    // Private initializer to prevent multiple instances
    private init() {}

    private var lastEntropy = [UInt8](repeating: 0, count: 10)
    private var lastTimestamp: UInt64 = 0

    private let lock = NSLock()

    func v7() -> UUID {
        var uuid: UUID?

        lock.withLock {
            uuid = generateUUID()
        }

        // or fallback to UUID v4
        return uuid ?? UUID()
    }

    private func generateUUID() -> UUID? {
        let timestamp = Date().timeIntervalSince1970
        let unixTimeMilliseconds = UInt64(timestamp * 1000)

        var uuidBytes = [UInt8]()

        let timeBytes = unixTimeMilliseconds.bigEndianData.suffix(6) // First 6 bytes for the timestamp
        uuidBytes.append(contentsOf: timeBytes)

        if unixTimeMilliseconds == lastTimestamp {
            var check = true
            for index in (0 ..< 10).reversed() where check {
                var temp = lastEntropy[index]
                temp = temp &+ 0x01
                check = lastEntropy[index] == 0xFF
                lastEntropy[index] = temp
            }
        } else {
            lastTimestamp = unixTimeMilliseconds

            // Prepare the random part (10 bytes to complete the UUID)
            let status = SecRandomCopyBytes(kSecRandomDefault, lastEntropy.count, &lastEntropy)
            // If we can't generate secure random bytes, use a fallback
            if status != errSecSuccess {
                let randomData = (0 ..< 10).map { _ in UInt8.random(in: 0 ... 255) }
                lastEntropy = randomData
            }
        }
        uuidBytes.append(contentsOf: lastEntropy)

        // Set version (7) in the version byte
        uuidBytes[6] = (uuidBytes[6] & 0x0F) | 0x70

        // Set the UUID variant (10xx for standard UUIDs)
        uuidBytes[8] = (uuidBytes[8] & 0x3F) | 0x80

        // Ensure we have a total of 16 bytes
        if uuidBytes.count == 16 {
            return UUID(uuid: (uuidBytes[0], uuidBytes[1], uuidBytes[2], uuidBytes[3],
                               uuidBytes[4], uuidBytes[5], uuidBytes[6], uuidBytes[7],
                               uuidBytes[8], uuidBytes[9], uuidBytes[10], uuidBytes[11],
                               uuidBytes[12], uuidBytes[13], uuidBytes[14], uuidBytes[15]))
        }

        return nil
    }
}

extension UInt64 {
    // Correctly generate Data representation in big endian format
    var bigEndianData: Data {
        var bigEndianValue = bigEndian
        return Data(bytes: &bigEndianValue, count: MemoryLayout<UInt64>.size)
    }
}
