//
//  TracePerformanceView+Modifier.swift
//  AnalyticsClient
//
//  Created by Ilia Lubianoi on 18.03.2025.
//  Modified for ForPDA Next: performance tracing is a no-op (no Sentry).
//

import SwiftUI

public struct TracePerformanceView<Content: View>: View {
    let content: Content
    
    public init(_ viewName: String? = nil, @ViewBuilder content: () -> Content) {
        self.content = content()
    }
    
    public var body: some View { content }
}

public extension View {
    func tracePerformance(viewName: String) -> some View { self }
}
