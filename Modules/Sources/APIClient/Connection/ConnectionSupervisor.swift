//
//  ConnectionSupervisor.swift
//  ForPDA Next
//
//  Owns the lifetime of the forum socket (`PDAPI.API`).
//
//  Root causes fixed compared to upstream ForPDA:
//   1. `connect()` failures on foreground were swallowed (`try?`) and never retried → the app
//      stayed disconnected until restart. Now every failure schedules a reconnect with
//      exponential backoff + jitter while the app is in the foreground.
//   2. Foreground/background produced unordered connect/disconnect tasks → a late `disconnect`
//      could kill a fresh connection. All commands now run serially on this actor and are
//      driven by one deterministic `ConnectionPolicy`.
//   3. Network interface changes (Wi-Fi ↔ LTE) left a dead socket in "ready" state; requests
//      hung for 15 s each. `NWPathMonitor` now triggers a reconnect, and a request timeout on a
//      "ready" socket is treated as a stale connection.
//   4. Requests made while offline waited for a timeout. They now fail immediately with an
//      "offline" error so cached data can be shown.
//   5. Only safe (idempotent) requests are retried; writes are never re-sent automatically.
//   6. `API.stateStream` is a single-consumer `AsyncStream`; the supervisor is now its only
//      consumer and re-broadcasts to any number of subscribers.
//

import Foundation
import Network
import PDAPI
import ForumCore
import OSLog

// MARK: - Public status

public enum ForumConnectionStatus: Equatable, Sendable {
    case online
    case connecting
    case offline
    /// Network is available but the server can't be reached; next attempt in `retryIn` seconds.
    case unreachable(retryIn: TimeInterval)
}

/// Error surfaced to features. `errorDescription` is a short, user-facing Russian message.
public struct ForumNetworkError: LocalizedError, Sendable {
    public let kind: NetworkErrorKind
    public let underlying: String?

    public init(kind: NetworkErrorKind, underlying: String? = nil) {
        self.kind = kind
        self.underlying = underlying
    }

    public var errorDescription: String? { kind.userMessage }
}

// MARK: - Supervisor

public actor ConnectionSupervisor {

    public typealias LoginProvider = @Sendable () -> (login: LoginType, route: ConnectionRoute)

    private let api: API
    private var loginProvider: LoginProvider = { (.anonymous, .primary) }
    private var policy = ConnectionPolicy(isForeground: false)
    private let retryPolicy = RequestRetryPolicy()
    private let logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "forpda", category: "Connection")

    private var isStarted = false
    private var connectTask: Task<Void, Never>?
    private var reconnectTask: Task<Void, Never>?
    private var pathMonitor: NWPathMonitor?

    private var stateSubscribers: [UUID: AsyncStream<ConnectionState>.Continuation] = [:]
    private var statusSubscribers: [UUID: AsyncStream<ForumConnectionStatus>.Continuation] = [:]
    private var lastState: ConnectionState = .disconnected
    private var lastStatus: ForumConnectionStatus = .connecting
    private var scheduledRetryDelay: TimeInterval?

    /// Max time a request waits for the socket to become ready before failing.
    private let readyTimeout: Duration = .seconds(12)

    public init(api: API) {
        self.api = api
    }

    // MARK: Lifecycle

    /// Idempotent. Starts path monitoring and state observation.
    public func start(loginProvider: @escaping LoginProvider) {
        self.loginProvider = loginProvider
        guard !isStarted else { return }
        isStarted = true

        let stream = api.stateStream
        Task { [weak self] in
            for await state in stream {
                await self?.transportChanged(state)
            }
        }

        let monitor = NWPathMonitor()
        monitor.pathUpdateHandler = { [weak self] path in
            let value = NetworkPath(nwPath: path)
            Task { await self?.handle(.pathChanged(value)) }
        }
        monitor.start(queue: DispatchQueue(label: "forpda.path-monitor", qos: .utility))
        pathMonitor = monitor
    }

    public func setForeground(_ isForeground: Bool) {
        handle(isForeground ? .appBecameActive : .appEnteredBackground)
    }

    /// Foreground + wait until the socket is ready (or throw a classified error).
    public func activate() async throws {
        handle(.appBecameActive)
        try await waitUntilReady()
    }

    /// User pressed "Retry" in an error view: reset backoff and connect now.
    public func retryNow() {
        handle(.userRequestedRetry)
    }

    /// Re-authenticate the socket (after login / logout).
    public func reconnectWithCurrentSession() {
        execute(.reconnect)
    }

    /// Connection for BGAppRefreshTask (app is in background; policy stays "background").
    public func connectForBackgroundTask() async throws {
        let (login, route) = loginProvider()
        try await api.connect(as: login, inBackground: true, route: route)
    }

    public func disconnectAfterBackgroundTask() async {
        guard !policy.isForeground else { return }
        await api.disconnect()
    }

    // MARK: Streams

    public func stateStream() -> AsyncStream<ConnectionState> {
        let (stream, continuation) = AsyncStream<ConnectionState>.makeStream(bufferingPolicy: .bufferingNewest(1))
        let id = UUID()
        stateSubscribers[id] = continuation
        continuation.yield(lastState)
        continuation.onTermination = { [weak self] _ in
            Task { await self?.removeStateSubscriber(id) }
        }
        return stream
    }

    public func statusStream() -> AsyncStream<ForumConnectionStatus> {
        let (stream, continuation) = AsyncStream<ForumConnectionStatus>.makeStream(bufferingPolicy: .bufferingNewest(1))
        let id = UUID()
        statusSubscribers[id] = continuation
        continuation.yield(lastStatus)
        continuation.onTermination = { [weak self] _ in
            Task { await self?.removeStatusSubscriber(id) }
        }
        return stream
    }

    public var isOffline: Bool { policy.isOffline }

    // MARK: Requests

    /// Sends a command through the supervised connection.
    /// - Parameter idempotent: `true` for reads. Writes (`false`) are never retried once they
    ///   may have reached the server; the caller gets `ForumNetworkError` with
    ///   `kind.mayHaveBeenProcessed == true` and must verify before re-sending.
    @discardableResult
    public func send(_ command: any Command, idempotent: Bool = true) async throws -> String {
        var attempt = 0
        while true {
            try Task.checkCancellation()
            do {
                try await waitUntilReady()
                return try await api.send(command)
            } catch {
                let kind = Self.classify(error)
                if kind == .cancelled { throw CancellationError() }
                if kind == .timeout, lastState == .ready {
                    handle(.staleConnectionDetected(at: Date()))
                }
                attempt += 1
                switch retryPolicy.decision(for: kind, attempt: attempt, idempotent: idempotent) {
                case let .retry(after):
                    logger.info("retry \(command.title, privacy: .public) #\(attempt) in \(after, format: .fixed(precision: 2))s (\(String(describing: kind), privacy: .public))")
                    try await Task.sleep(for: .milliseconds(Int(after * 1000)))
                case .fail:
                    logger.error("request \(command.title, privacy: .public) failed: \(String(describing: kind), privacy: .public)")
                    throw ForumNetworkError(kind: kind, underlying: String(describing: error))
                }
            }
        }
    }

    /// Uploads are writes: no automatic retries, but they wait for a ready socket first.
    public func upload(_ request: UploadRequest) async -> AsyncThrowingStream<UploadProgressStatus, any Error> {
        do {
            try await waitUntilReady()
        } catch {
            return AsyncThrowingStream { $0.finish(throwing: error) }
        }
        return await api.upload(request: request)
    }

    // MARK: - Internals

    private func waitUntilReady() async throws {
        let deadline = ContinuousClock.now + readyTimeout
        while true {
            try Task.checkCancellation()
            if policy.isOffline { throw ForumNetworkError(kind: .offline) }
            switch lastState {
            case .ready, .uploading, .uploadLock:
                return
            case .disconnected:
                // Nobody is connecting (e.g. request issued right after foreground) → kick it.
                if connectTask == nil, !policy.isReconnectScheduled {
                    if !policy.isForeground { handle(.appBecameActive) } else { execute(.connect) }
                }
            case .connecting:
                break
            @unknown default:
                break
            }
            if ContinuousClock.now >= deadline { throw ForumNetworkError(kind: .notConnected) }
            try await Task.sleep(for: .milliseconds(100))
        }
    }

    private func transportChanged(_ state: ConnectionState) {
        lastState = state
        for continuation in stateSubscribers.values { continuation.yield(state) }
        handle(.transportChanged(TransportState(state)))
    }

    private func handle(_ event: ConnectionPolicy.Event) {
        let commands = policy.handle(event)
        for command in commands { execute(command) }
        publishStatus()
    }

    private func execute(_ command: ConnectionPolicy.Command) {
        switch command {
        case .connect:
            guard connectTask == nil else { return } // single flight
            let (login, route) = loginProvider()
            logger.info("connect (route: \(route.rawValue, privacy: .public))")
            connectTask = Task { [api] in
                do {
                    try await api.connect(as: login, inBackground: false, route: route)
                    await self.connectFinished(error: nil)
                } catch {
                    await self.connectFinished(error: error)
                }
            }

        case .disconnect:
            connectTask?.cancel()
            connectTask = nil
            Task { [api] in await api.disconnect() }

        case .reconnect:
            connectTask?.cancel()
            connectTask = nil
            let (login, route) = loginProvider()
            logger.info("reconnect")
            connectTask = Task { [api] in
                await api.disconnect()
                do {
                    try await api.connect(as: login, inBackground: false, route: route)
                    await self.connectFinished(error: nil)
                } catch {
                    await self.connectFinished(error: error)
                }
            }

        case let .scheduleReconnect(after):
            reconnectTask?.cancel()
            logger.info("reconnect scheduled in \(after, format: .fixed(precision: 1))s")
            reconnectTask = Task {
                try? await Task.sleep(for: .milliseconds(Int(after * 1000)))
                guard !Task.isCancelled else { return }
                await self.handle(.reconnectTimerFired)
            }
            scheduledRetryDelay = after

        case .cancelScheduledReconnect:
            reconnectTask?.cancel()
            reconnectTask = nil
            scheduledRetryDelay = nil
        }
    }

    private func connectFinished(error: Error?) {
        connectTask = nil
        if let error {
            if error is CancellationError { return }
            logger.error("connect failed: \(String(describing: Self.classify(error)), privacy: .public)")
            handle(.connectFailed)
        }
    }

    private func publishStatus() {
        let status: ForumConnectionStatus
        if policy.isOffline {
            status = .offline
        } else {
            switch lastState {
            case .ready, .uploading, .uploadLock: status = .online
            case .connecting: status = .connecting
            case .disconnected:
                if policy.isReconnectScheduled, let delay = scheduledRetryDelay {
                    status = .unreachable(retryIn: delay)
                } else {
                    status = .connecting
                }
            @unknown default:
                status = .connecting
            }
        }
        guard status != lastStatus else { return }
        lastStatus = status
        for continuation in statusSubscribers.values { continuation.yield(status) }
    }

    private func removeStateSubscriber(_ id: UUID) { stateSubscribers[id] = nil }
    private func removeStatusSubscriber(_ id: UUID) { statusSubscribers[id] = nil }

    // MARK: - Error classification

    public static func classify(_ error: Error) -> NetworkErrorKind {
        if let forumError = error as? ForumNetworkError { return forumError.kind }
        if error is CancellationError { return .cancelled }
        if let apiError = error as? APIError {
            switch apiError {
            case .notConnected, .bootstrapFailed: return .notConnected
            case .queueOverflow: return .queueOverflow
            case .requestTimedOut: return .timeout
            case .transportClosed, .reconnectExhausted: return .connectionLost
            case .malformedFrame, .responseDecodingFailed: return .malformedResponse
            case .uploadNotAuthorized: return .unknown
            @unknown default: return .unknown
            }
        }
        if let nwError = error as? NWError {
            switch nwError {
            case .dns: return .dns
            case .tls: return .tls
            case .posix(let code):
                switch code {
                case .ENETDOWN, .ENETUNREACH, .EHOSTUNREACH: return .offline
                case .ETIMEDOUT: return .timeout
                default: return .connectionLost
                }
            @unknown default: return .connectionLost
            }
        }
        return URLErrorClassifier.classify(error)
    }
}

// MARK: - Mapping

extension TransportState {
    init(_ state: ConnectionState) {
        switch state {
        case .disconnected: self = .disconnected
        case .connecting: self = .connecting
        case .ready: self = .ready
        case .uploadLock, .uploading: self = .busy
        @unknown default: self = .disconnected
        }
    }
}

extension NetworkPath {
    init(nwPath: NWPath) {
        let interface: Interface
        if nwPath.usesInterfaceType(.wifi) { interface = .wifi }
        else if nwPath.usesInterfaceType(.cellular) { interface = .cellular }
        else if nwPath.usesInterfaceType(.wiredEthernet) { interface = .wired }
        else if nwPath.status == .satisfied { interface = .other }
        else { interface = .none }
        self.init(isSatisfied: nwPath.status == .satisfied, interface: interface, isExpensive: nwPath.isExpensive)
    }
}

// MARK: - Response helpers (no force unwraps)

public enum ResponseFormatError: LocalizedError, Sendable {
    case missingComponent(index: Int)
    case notANumber(String)

    public var errorDescription: String? { "Сервер вернул неожиданный ответ" }
}

extension String {
    /// Components of a flat array response "[a,b,c]".
    func responseComponents() -> [String] {
        replacingOccurrences(of: "[", with: "")
            .replacingOccurrences(of: "]", with: "")
            .components(separatedBy: ",")
    }

    /// Integer status code at index 1 of the response; throws instead of crashing.
    public func responseStatusCode() throws -> Int {
        try responseInt(at: 1)
    }

    public func responseInt(at index: Int) throws -> Int {
        let parts = responseComponents()
        guard parts.indices.contains(index) else { throw ResponseFormatError.missingComponent(index: index) }
        let raw = parts[index].trimmingCharacters(in: .whitespaces)
        guard let value = Int(raw) else { throw ResponseFormatError.notANumber(raw) }
        return value
    }
}
