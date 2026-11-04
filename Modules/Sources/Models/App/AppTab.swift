//
//  Tab.swift
//  ForPDA
//
//  Created by Ilia Lubianoi on 13.11.2024.
//

import SwiftUI
import SFSafeSymbols

public enum AppTab: Int, CaseIterable, Sendable, Codable {
    case articles = 0
    case favorites
    case forum
    case more
    /// QMS dialogs (ForPDA Next). Appended last to keep stored raw values stable.
    case messages
    
    /// Visual order in the tab bar: Главная, Форум, Избранное, Сообщения, Профиль.
    public static let displayOrder: [AppTab] = [.articles, .forum, .favorites, .messages, .more]
    
    public var iconSymbol: SFSymbol {
        switch self {
        case .articles:
            return .docTextImage
        case .favorites:
            return .starBubble
        case .forum:
            return .bubbleLeftAndBubbleRight
        case .more:
            return .personCropCircle
        case .messages:
            return .bubbleLeftAndTextBubbleRight
        }
    }
    
    public var title: LocalizedStringResource {
        switch self {
        case .articles:
            return LocalizedStringResource("Главная")
        case .favorites:
            return LocalizedStringResource("Favorites", bundle: .module)
        case .forum:
            return LocalizedStringResource("Forum", bundle: .module)
        case .more:
            return LocalizedStringResource("Профиль")
        case .messages:
            return LocalizedStringResource("Сообщения")
        }
    }
}
