//
//  TopicOpeningStrategy.swift
//  Models
//
//  Created by Ilia Lubianoi on 16.08.2025.
//

import SwiftUI

public enum TopicOpeningStrategy: CaseIterable, Codable, Sendable {
    
    case first
    case unread
    case last
    /// ForPDA Next: reopen the page where the user stopped reading (stored locally);
    /// falls back to the server "first unread" jump when the topic was never opened here.
    case lastRead
    
    public var _rawValue: String {
        switch self {
        case .first:  return "first"
        case .unread: return "unread"
        case .last:   return "last"
        case .lastRead: return "lastRead"
        }
    }
    
    public var text: LocalizedStringResource {
        switch self {
        case .first:  return .init("First page", bundle: .module)
        case .unread: return .init("Unread",     bundle: .module)
        case .last:   return .init("Last page",  bundle: .module)
        case .lastRead: return "Где остановился"
        }
    }
    
    public var asGoTo: GoTo {
        switch self {
        case .first:  return .first
        case .unread: return .unread
        case .last:   return .last
        case .lastRead: return .unread
        }
    }
}
