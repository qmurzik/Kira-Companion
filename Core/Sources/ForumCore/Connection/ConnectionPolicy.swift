//
//  ConnectionPolicy.swift
//  ForumCore
//
//  Decides *when* to connect, disconnect and reconnect the forum socket.
//
//  Upstream ForPDA had three failure modes that this policy removes:
//   • a failed `connect()` on foreground was swallowed (`try?`) and never retried → every
//     request failed with "not connected" until the app was restarted;
//   • foreground/background transitions spawned independent connect/disconnect tasks that could
//     finish out of order → the socket was closed while the app was active;
//   • Wi-Fi ↔ cellular switches left a dead socket in the "ready" state → requests hung until
//     the 15 s timeout, over and over.
//
//  The policy is a deterministic value type: events in, commands out. The iOS side
//  (`ConnectionSupervisor`) executes commands serially on an actor.
//

import Foundation

public enum TransportState: String, Equatable, Sendable {
    case disconnected
    case connecting
    case ready
    /// The upstream library reports uploads as separate states; for connectivity they count as ready.
    case busy
}

public struct NetworkPath: Equatable, Sendable {
    public enum Interface: String, Equatable, Sendable {
        case wifi, cellular, wired, other, none
    }
    public var isSatisfied: Bool
    public var interface: Interface
    public var isExpensive: Bool

    public init(isSatisfied: Bool, interface: Interface, isExpensive: Bool = false) {
        self.isSatisfied = isSatisfied
        self.interface = interface
        self.isExpensive = isExpensive
    }

    public static let unknown = NetworkPath(isSatisfied: true, interface: .other)
    public static let offline = NetworkPath(isSatisfied: false, interface: .none)
}

public struct ConnectionPolicy: Equatable, Sendable {

    public enum Event: Equatable, Sendable {
        case appBecameActive
        case appEnteredBackground
        case pathChanged(NetworkPath)
        case transportChanged(TransportState)
        case connectFailed
        case reconnectTimerFired
        /// A request timed out although the transport claimed to be ready → stale socket.
        case staleConnectionDetected(at: Date)
        case userRequestedRetry
    }

    public enum Command: Equatable, Sendable {
        case connect
        case disconnect
        /// Tear down and connect again (stale socket / interface change).
        case reconnect
        case scheduleReconnect(after: TimeInterval)
        case cancelScheduledReconnect
    }

    public private(set) var isForeground: Bool
    public private(set) var path: NetworkPath
    public private(set) var transport: TransportState = .disconnected
    public private(set) var attempt: Int = 0
    public private(set) var isReconnectScheduled = false
    public private(set) var lastStaleReconnect: Date?
    public var backoff: Backoff

    public init(
        isForeground: Bool = true,
        path: NetworkPath = .unknown,
        backoff: Backoff = Backoff(base: 1, multiplier: 2, maximum: 30, jitter: 0.2)
    ) {
        self.isForeground = isForeground
        self.path = path
        self.backoff = backoff
    }

    /// Requests should fail fast with "offline" instead of waiting for a timeout.
    public var isOffline: Bool { !path.isSatisfied }

    public var shouldBeConnected: Bool { isForeground && path.isSatisfied }

    public mutating func handle(_ event: Event, random: Double = Double.random(in: 0...1)) -> [Command] {
        switch event {
        case .appBecameActive:
            isForeground = true
            attempt = 0
            guard path.isSatisfied else { return cancelTimer() }
            if transport == .disconnected { return cancelTimer() + [.connect] }
            return []

        case .appEnteredBackground:
            isForeground = false
            return cancelTimer() + [.disconnect]

        case let .pathChanged(newPath):
            let old = path
            path = newPath
            guard newPath.isSatisfied else { return cancelTimer() }
            guard isForeground else { return [] }
            let cameOnline = !old.isSatisfied
            let interfaceChanged = old.interface != newPath.interface && old.interface != .none
            guard cameOnline || interfaceChanged else { return [] }
            attempt = 0
            switch transport {
            case .disconnected: return cancelTimer() + [.connect]
            // The socket was bound to the previous interface — it will silently die.
            case .ready, .busy, .connecting: return cancelTimer() + [.reconnect]
            }

        case let .transportChanged(state):
            transport = state
            switch state {
            case .ready, .busy:
                attempt = 0
                return cancelTimer()
            case .connecting:
                // The library is (re)connecting on its own; don't compete with it.
                return cancelTimer()
            case .disconnected:
                return scheduleIfNeeded(random: random)
            }

        case .connectFailed:
            transport = .disconnected
            return scheduleIfNeeded(random: random)

        case .reconnectTimerFired:
            isReconnectScheduled = false
            guard shouldBeConnected, transport == .disconnected else { return [] }
            return [.connect]

        case let .staleConnectionDetected(date):
            guard shouldBeConnected else { return [] }
            // At most one forced reconnect per 10 seconds to avoid reconnect storms.
            if let last = lastStaleReconnect, date.timeIntervalSince(last) < 10 { return [] }
            lastStaleReconnect = date
            return cancelTimer() + [.reconnect]

        case .userRequestedRetry:
            attempt = 0
            guard path.isSatisfied else { return [] }
            isForeground = true
            switch transport {
            case .ready, .busy: return []
            case .connecting: return [.reconnect]
            case .disconnected: return cancelTimer() + [.connect]
            }
        }
    }

    private mutating func scheduleIfNeeded(random: Double) -> [Command] {
        guard shouldBeConnected, !isReconnectScheduled else { return [] }
        let delay = backoff.delay(forAttempt: attempt, random: random)
        attempt += 1
        isReconnectScheduled = true
        return [.scheduleReconnect(after: delay)]
    }

    private mutating func cancelTimer() -> [Command] {
        guard isReconnectScheduled else { return [] }
        isReconnectScheduled = false
        return [.cancelScheduledReconnect]
    }
}

// MARK: - Backoff

public struct Backoff: Equatable, Sendable {
    public var base: TimeInterval
    public var multiplier: Double
    public var maximum: TimeInterval
    /// 0.2 = ±20 %.
    public var jitter: Double

    public init(base: TimeInterval, multiplier: Double, maximum: TimeInterval, jitter: Double) {
        self.base = base
        self.multiplier = multiplier
        self.maximum = maximum
        self.jitter = min(max(jitter, 0), 1)
    }

    /// `random` in 0…1 (injected for deterministic tests).
    public func delay(forAttempt attempt: Int, random: Double) -> TimeInterval {
        let exp = base * pow(multiplier, Double(max(0, min(attempt, 30))))
        let capped = min(exp, maximum)
        let factor = 1 + jitter * (min(max(random, 0), 1) * 2 - 1)
        return max(0, capped * factor)
    }
}
