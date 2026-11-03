//
//  TrackAnalyticsModifier.swift
//  ForPDA
//
//  Created by Ilia Lubianoi on 26.11.2024.
//  Modified for ForPDA Next: screen tracking is a no-op (no third-party analytics).
//

import SwiftUI

public extension View {
    func trackAnalytics(screenName: String, properties: [String: Any]? = nil) -> some View {
        self
    }
}
