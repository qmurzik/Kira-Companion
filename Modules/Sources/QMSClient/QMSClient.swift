//
//  QMSClient.swift
//  ForPDA
//
//  Created by Ilia Lubianoi on 28.11.2025.
//

import APIClient
import Dependencies
import DependenciesMacros
import Foundation
import Models
import PDAPI
import ParsingClient

@DependencyClient
public struct QMSClient: Sendable {
    public var loadChatList: @Sendable () async throws -> QMSList
    public var loadUser: @Sendable (_ id: Int) async throws -> QMSUser
    public var loadChat: @Sendable (_ id: Int, _ lastMessageId: Int, _ offset: Int) async throws -> QMSChat
    public var createChat: @Sendable (_ opponentId: Int, _ title: String, _ message: String) async throws -> String
    public var deleteAllChats: @Sendable (_ userId: Int) async throws -> String
    public var deleteChat: @Sendable (_ chatId: Int) async throws -> String
    public var sendMessage: @Sendable (_ chatId: Int, _ message: String) async throws -> Void
    /// The server accepts uploaded attachment ids together with the message (PDAPI `attaches`).
    public var sendMessageWithAttachments: @Sendable (_ chatId: Int, _ message: String, _ attachments: [Int]) async throws -> Void = { _, _, _ in }
    public var deleteMessage: @Sendable (_ chatId: Int, _ messageId: Int, _ forAll: Bool) async throws -> String
    public var blacklist: @Sendable () async throws -> String
    public var addToBlacklist: @Sendable (_ id: Int, _ add: Bool) async throws -> String
}

extension QMSClient: DependencyKey {
    
    /// Routed through the connection supervisor (reconnects, offline detection, safe retries).
    private static var api: ConnectionSupervisor {
        return APIClient.supervisor
    }
    
    // MARK: - Live Value
    
    public static var liveValue: QMSClient {
        @Dependency(\.parsingClient) var parser
        
        return QMSClient(
            loadChatList: {
                let response = try await api.send(QMSCommand.list)
                return try await parser.parseQmsList(response)
            },
            
            loadUser: { id in
                let response = try await api.send(QMSCommand.info(id: id))
                return try await parser.parseQmsUser(response)
            },
            
            loadChat: { id, lastMessageId, offset in
                let response = try await api.send(QMSCommand.Dialog.view(id: id, messageId: lastMessageId, offset: offset))
                return try await parser.parseQmsChat(response)
            },
            
            createChat: { opponentId, title, message in
                let response = try await api.send(QMSCommand.Dialog.create(title: title, message: message, opponentId: opponentId), idempotent: false)
                return response
            },
            
            deleteAllChats: { userId in
                let response = try await api.send(QMSCommand.delete(dialogId: 0, messageId: userId, forAll: false), idempotent: false)
                return response
            },
            
            deleteChat: { chatId in
                let response = try await api.send(QMSCommand.delete(dialogId: chatId, messageId: 0, forAll: false), idempotent: false)
                return response
            },
            
            sendMessage: { chatId, message in
                let _ = try await api.send(QMSCommand.Message.send(message: message, dialogId: chatId, attaches: []), idempotent: false)
            },
            
            sendMessageWithAttachments: { chatId, message, attachments in
                let _ = try await api.send(QMSCommand.Message.send(message: message, dialogId: chatId, attaches: attachments), idempotent: false)
            },
            
            deleteMessage: { chatId, messageId, forAll in
                let response = try await api.send(QMSCommand.delete(dialogId: chatId, messageId: messageId, forAll: forAll), idempotent: false)
                return response
            },
            
            blacklist: {
                let response = try await api.send(QMSCommand.blacklist)
                return response
            },
            
            addToBlacklist: { id, add in
                let response = try await api.send(QMSCommand.toBlackList(opponentId: id, add: add), idempotent: false)
                return response
            }
        )
    }
    
    // MARK: - Preview Value
    
    public static var previewValue: QMSClient {
        let mock = QMSClientMock()
        
        return QMSClient(
            loadChatList: {
                try await Task.sleep(for: .seconds(2))
                return .mock
            },
            loadUser: { _ in
                try await Task.sleep(for: .seconds(2))
                return .mock
            },
            loadChat: { id, lastMessageId, offset in
                try await Task.sleep(for: .seconds(2))
                return await mock.loadQMSChat()
            },
            createChat: { _, _, _ in
                return ""
            },
            deleteAllChats: { _ in
                return ""
            },
            deleteChat: { _ in
                return ""
            },
            sendMessage: { chatId, message in
                return try await mock.sendQMSMessage(chatId: chatId, message: message)
            },
            sendMessageWithAttachments: { chatId, message, _ in
                return try await mock.sendQMSMessage(chatId: chatId, message: message)
            },
            deleteMessage: { _, _, _ in
                return ""
            },
            blacklist: {
                return ""
            },
            addToBlacklist: { _, _ in
                return ""
            }
        )
    }
    
    // MARK: - Error On Send
    
    public static var errorOnSend: QMSClient {
        let mock = QMSClientMock(retries: 1)
        
        return QMSClient(
            loadChatList: unimplemented(),
            loadUser: unimplemented(),
            loadChat: { id, lastMessageId, offset in
                return await mock.loadQMSChat()
            },
            createChat: unimplemented(),
            deleteAllChats: unimplemented(),
            deleteChat: unimplemented(),
            sendMessage: { chatId, message in
                return try await mock.sendQMSMessage(chatId: chatId, message: message)
            },
            sendMessageWithAttachments: { chatId, message, _ in
                return try await mock.sendQMSMessage(chatId: chatId, message: message)
            },
            deleteMessage: unimplemented(),
            blacklist: unimplemented(),
            addToBlacklist: unimplemented()
        )
    }
}

extension DependencyValues {
    public var qmsClient: QMSClient {
        get { self[QMSClient.self] }
        set { self[QMSClient.self] = newValue }
    }
}
