//
//  ReadingPreferences.swift
//  ForPDA Next
//
//  User reading / layout preferences stored in UserDefaults (local, exportable).
//  Read synchronously by the BB renderer, so they apply when posts are rendered.
//

import SwiftUI

public enum ReadingPreferences {
    public static let fontScaleKey = "reading.fontScale"
    public static let lineSpacingKey = "reading.lineSpacing"
    public static let contentWidthKey = "reading.contentWidth"
    public static let densityKey = "ui.density"
    public static let avatarSizeKey = "ui.avatarSize"
    
    /// Multiplier for post text (0.8 … 1.6). 1 = system Dynamic Type size.
    public static var fontScale: CGFloat {
        let value = UserDefaults.standard.double(forKey: fontScaleKey)
        return value == 0 ? 1 : CGFloat(min(max(value, 0.8), 1.6))
    }
    
    /// Extra spacing between lines of post text, in points (0 … 10).
    public static var lineSpacing: CGFloat {
        CGFloat(min(max(UserDefaults.standard.double(forKey: lineSpacingKey), 0), 10))
    }
    
    /// Maximum width of post content; `nil` = full width.
    public static var maxContentWidth: CGFloat? {
        let value = UserDefaults.standard.double(forKey: contentWidthKey)
        return value <= 0 ? nil : CGFloat(value)
    }
    
    public static var density: InterfaceDensity {
        InterfaceDensity(rawValue: UserDefaults.standard.string(forKey: densityKey) ?? "") ?? .regular
    }
    
    public static var avatarScale: CGFloat {
        let value = UserDefaults.standard.double(forKey: avatarSizeKey)
        return value == 0 ? 1 : CGFloat(min(max(value, 0.7), 1.5))
    }
}

public enum InterfaceDensity: String, CaseIterable, Sendable, Identifiable {
    case compact, regular, spacious
    
    public var id: String { rawValue }
    
    public var title: String {
        switch self {
        case .compact: "Компактный"
        case .regular: "Обычный"
        case .spacious: "Просторный"
        }
    }
    
    /// Vertical padding of list rows.
    public var rowPadding: CGFloat {
        switch self {
        case .compact: 6
        case .regular: 10
        case .spacious: 16
        }
    }
    
    public var spacing: CGFloat {
        switch self {
        case .compact: 4
        case .regular: 8
        case .spacious: 12
        }
    }
}

// MARK: - Environment

private struct DensityKey: EnvironmentKey {
    static let defaultValue: InterfaceDensity = .regular
}

public extension EnvironmentValues {
    var interfaceDensity: InterfaceDensity {
        get { self[DensityKey.self] }
        set { self[DensityKey.self] = newValue }
    }
}

public extension View {
    /// Limits readable width (useful on large phones in landscape / iPad) and centers it.
    func readableContentWidth() -> some View {
        modifier(ReadableWidthModifier())
    }
}

private struct ReadableWidthModifier: ViewModifier {
    @AppStorage(ReadingPreferences.contentWidthKey) private var width: Double = 0
    
    func body(content: Content) -> some View {
        if width > 0 {
            content
                .frame(maxWidth: width)
                .frame(maxWidth: .infinity)
        } else {
            content
        }
    }
}
