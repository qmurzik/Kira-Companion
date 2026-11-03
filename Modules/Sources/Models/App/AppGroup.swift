//
//  AppGroup.swift
//  ForPDA Next
//
//  Resolves the shared App Group container without crashing.
//
//  Upstream force-unwrapped `containerURL(forSecurityApplicationGroupIdentifier:)`.
//  When the IPA is re-signed by a sideloading tool, the App Group identifier is often
//  rewritten or dropped, the lookup returns nil and the app crashed on launch.
//

import Foundation

public enum AppGroup {
    /// Identifier is injected into Info.plist by Project.swift (`FORPDA_APP_GROUP`).
    public static var identifier: String {
        (Bundle.main.object(forInfoDictionaryKey: "FORPDA_APP_GROUP") as? String)
            ?? "group.com.qmurzik.forpdanext"
    }
    
    /// Shared container when available, otherwise the app's own Application Support directory.
    public static var containerURL: URL {
        if let url = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: identifier) {
            return url
        }
        let fallback = URL.applicationSupportDirectory.appending(component: "SharedFallback", directoryHint: .isDirectory)
        try? FileManager.default.createDirectory(at: fallback, withIntermediateDirectories: true)
        return fallback
    }
}
