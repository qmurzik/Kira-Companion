//
//  ComposerPreviewPane.swift
//  ComposerFeature
//
//  Server-side preview (exactly what the forum will render), shown with the same renderer
//  that displays posts in topics.
//

import SwiftUI
import ForumCore
import SharedUI
import Models

struct ComposerPreviewPane: View {
    let state: ComposerFeature.State.PreviewState
    let issues: [BBIssue]
    let onRetry: () -> Void
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                if !issues.isEmpty {
                    IssuesBanner(issues: issues)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .padding(.bottom, 12)
                }
                switch state {
                case .idle, .loading:
                    ProgressView("Готовим предпросмотр…")
                        .frame(maxWidth: .infinity)
                        .padding(.top, 40)
                case let .failed(message):
                    ContentUnavailableView {
                        Label("Предпросмотр недоступен", systemImage: "wifi.exclamationmark")
                    } description: {
                        Text(message)
                    } actions: {
                        Button("Повторить", action: onRetry)
                            .buttonStyle(.borderedProminent)
                    }
                case let .loaded(preview):
                    if preview.content.isEmpty {
                        Text("Пустое сообщение")
                            .foregroundStyle(.secondary)
                    }
                    ForEach(Array(preview.content.enumerated()), id: \.offset) { _, type in
                        TopicView(
                            type: type,
                            attachments: preview.attachments,
                            userSession: nil,
                            onUrlTap: { _ in }
                        )
                    }
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .accessibilityIdentifier("composer.preview")
    }
}
