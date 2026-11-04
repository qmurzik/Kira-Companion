//
//  DraftsListView.swift
//  ComposerFeature
//
//  Lists every saved draft (replies, edits, QMS) with swipe-to-delete.
//

import SwiftUI
import ComposableArchitecture
import ForumCore

public struct DraftsListView: View {
    
    @Dependency(\.draftClient) private var draftClient
    @State private var drafts: [Draft] = []
    @State private var isLoaded = false
    @State private var confirmDeleteAll = false
    
    public init() {}
    
    public var body: some View {
        List {
            if isLoaded && drafts.isEmpty {
                ContentUnavailableView(
                    "Черновиков нет",
                    systemImage: "doc.text",
                    description: Text("Незаконченные сообщения сохраняются здесь автоматически.")
                )
                .listRowBackground(Color.clear)
            }
            ForEach(drafts) { draft in
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Label(kindTitle(draft.key), systemImage: kindIcon(draft.key))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Spacer()
                        Text(draft.updatedAt, style: .relative)
                            .font(.caption2)
                            .foregroundStyle(.tertiary)
                    }
                    if !draft.contextTitle.isEmpty {
                        Text(draft.contextTitle)
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(2)
                    }
                    Text(draft.preview.isEmpty ? "Без текста" : draft.preview)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(3)
                    HStack(spacing: 12) {
                        if !draft.attachments.isEmpty {
                            Label("\(draft.attachments.count)", systemImage: "paperclip")
                        }
                        if draft.pendingSubmission != nil {
                            Label("Отправка не подтверждена", systemImage: "exclamationmark.arrow.triangle.2.circlepath")
                                .foregroundStyle(.orange)
                        }
                    }
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }
                .padding(.vertical, 4)
                .swipeActions {
                    Button(role: .destructive) {
                        delete(draft)
                    } label: {
                        Label("Удалить", systemImage: "trash")
                    }
                }
                .contextMenu {
                    Button {
                        UIPasteboard.general.string = draft.text
                    } label: {
                        Label("Скопировать текст", systemImage: "doc.on.doc")
                    }
                    Button(role: .destructive) {
                        delete(draft)
                    } label: {
                        Label("Удалить", systemImage: "trash")
                    }
                }
            }
        }
        .navigationTitle("Черновики")
        .toolbar {
            if !drafts.isEmpty {
                Button("Удалить все", role: .destructive) { confirmDeleteAll = true }
            }
        }
        .confirmationDialog("Удалить все черновики?", isPresented: $confirmDeleteAll, titleVisibility: .visible) {
            Button("Удалить все", role: .destructive) {
                Task {
                    try? await draftClient.deleteAll()
                    await reload()
                }
            }
        }
        .task { await reload() }
        .refreshable { await reload() }
    }
    
    private func reload() async {
        drafts = await draftClient.all()
        isLoaded = true
    }
    
    private func delete(_ draft: Draft) {
        drafts.removeAll { $0.id == draft.id }
        Task { try? await draftClient.delete(draft.key) }
    }
    
    private func kindTitle(_ key: DraftKey) -> String {
        switch key {
        case .topicReply: "Ответ в теме"
        case .postEdit: "Редактирование поста"
        case .newTopic: "Новая тема"
        case .qms, .qmsNewDialog: "QMS"
        case .articleComment: "Комментарий к статье"
        }
    }
    
    private func kindIcon(_ key: DraftKey) -> String {
        switch key {
        case .topicReply: "arrowshape.turn.up.left"
        case .postEdit: "pencil"
        case .newTopic: "plus.bubble"
        case .qms, .qmsNewDialog: "bubble.left.and.bubble.right"
        case .articleComment: "newspaper"
        }
    }
}
