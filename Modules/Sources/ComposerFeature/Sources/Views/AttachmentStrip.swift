//
//  AttachmentStrip.swift
//  ComposerFeature
//

import SwiftUI

struct AttachmentStrip: View {
    let attachments: [ComposerAttachment]
    let onInsert: (ComposerAttachment.ID) -> Void
    let onRemove: (ComposerAttachment.ID) -> Void
    let onRetry: (ComposerAttachment.ID) -> Void
    let onCancel: (ComposerAttachment.ID) -> Void
    let onRename: (ComposerAttachment) -> Void
    
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(attachments) { attachment in
                    AttachmentTile(attachment: attachment)
                        .contextMenu { menu(for: attachment) }
                        .onTapGesture {
                            switch attachment.status {
                            case .uploaded: onInsert(attachment.id)
                            case .failed: onRetry(attachment.id)
                            default: break
                            }
                        }
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
        }
        .background(Color(uiColor: .secondarySystemBackground))
        .accessibilityIdentifier("composer.attachments")
    }
    
    @ViewBuilder
    private func menu(for attachment: ComposerAttachment) -> some View {
        switch attachment.status {
        case .uploaded:
            Button { onInsert(attachment.id) } label: { Label("Вставить в текст", systemImage: "text.insert") }
        case .failed:
            Button { onRetry(attachment.id) } label: { Label("Повторить загрузку", systemImage: "arrow.clockwise") }
        case .uploading:
            Button { onCancel(attachment.id) } label: { Label("Отменить загрузку", systemImage: "stop.circle") }
        case .queued:
            EmptyView()
        }
        if attachment.serverId == nil {
            Button { onRename(attachment) } label: { Label("Переименовать", systemImage: "pencil") }
        }
        Button(role: .destructive) { onRemove(attachment.id) } label: { Label("Удалить", systemImage: "trash") }
    }
}

struct AttachmentTile: View {
    let attachment: ComposerAttachment
    
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ZStack {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(Color(uiColor: .tertiarySystemBackground))
                if let data = attachment.thumbnail, let image = UIImage(data: data) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                } else {
                    Image(systemName: attachment.isImage ? "photo" : "doc")
                        .font(.title2)
                        .foregroundStyle(.secondary)
                }
                overlay
            }
            .frame(width: 72, height: 72)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            
            Text(attachment.fileName)
                .font(.caption2)
                .lineLimit(1)
                .truncationMode(.middle)
                .frame(width: 72, alignment: .leading)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityText)
    }
    
    @ViewBuilder
    private var overlay: some View {
        switch attachment.status {
        case .queued:
            Color.black.opacity(0.35)
            Image(systemName: "clock").foregroundStyle(.white)
        case let .uploading(progress):
            Color.black.opacity(0.35)
            ProgressView(value: progress)
                .progressViewStyle(.circular)
                .tint(.white)
        case .uploaded:
            VStack {
                HStack {
                    Spacer()
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(.white, .green)
                        .padding(4)
                }
                Spacer()
            }
        case .failed:
            Color.red.opacity(0.35)
            Image(systemName: "arrow.clockwise.circle.fill")
                .font(.title2)
                .foregroundStyle(.white)
        }
    }
    
    private var accessibilityText: String {
        switch attachment.status {
        case .queued: "\(attachment.fileName), в очереди"
        case let .uploading(p): "\(attachment.fileName), загрузка \(Int(p * 100))%"
        case .uploaded: "\(attachment.fileName), загружен. Нажмите, чтобы вставить в текст"
        case let .failed(message): "\(attachment.fileName), ошибка: \(message). Нажмите, чтобы повторить"
        }
    }
}
