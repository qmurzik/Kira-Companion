//
//  FormatBar.swift
//  ComposerFeature
//
//  Formatting panel shown right above the keyboard: a horizontally scrolling row of tools
//  plus fixed attach / send buttons that are never covered by the keyboard.
//

import SwiftUI
import ForumCore

struct FormatBar: View {
    
    @ObservedObject var controller: ComposerTextController
    let isQMS: Bool
    let canSend: Bool
    let isBusy: Bool
    let onPrompt: (FormatPrompt) -> Void
    let onAttachPhoto: () -> Void
    let onAttachFile: () -> Void
    let onSend: () -> Void
    
    static let colors: [(name: String, value: String, color: Color)] = [
        ("Красный", "red", .red), ("Оранжевый", "orange", .orange), ("Зелёный", "green", .green),
        ("Синий", "blue", .blue), ("Фиолетовый", "purple", .purple), ("Серый", "gray", .gray)
    ]
    
    var body: some View {
        HStack(spacing: 0) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 2) {
                    tool("arrow.uturn.backward", "Отменить", enabled: controller.canUndo) { controller.undo() }
                    tool("arrow.uturn.forward", "Повторить", enabled: controller.canRedo) { controller.redo() }
                    divider
                    tool("bold", "Жирный") { controller.format(.bold) }
                    tool("italic", "Курсив") { controller.format(.italic) }
                    tool("underline", "Подчёркнутый") { controller.format(.underline) }
                    tool("strikethrough", "Зачёркнутый") { controller.format(.strike) }
                    colorMenu
                    sizeMenu
                    divider
                    tool("link", "Ссылка") { onPrompt(.link) }
                    tool("text.quote", "Цитата") { controller.format(.quote) }
                    tool("chevron.left.forwardslash.chevron.right", "Код") { controller.format(.code) }
                    tool("eye.slash", "Спойлер") { controller.format(.spoiler(title: nil)) }
                    tool("list.bullet", "Список") { controller.format(.list) }
                    tool("list.number", "Нумерованный список") { controller.format(.orderedList) }
                    alignMenu
                    moreMenu
                }
                .padding(.horizontal, 6)
            }
            
            divider
            
            Menu {
                Button { onAttachPhoto() } label: { Label("Фото и скриншоты", systemImage: "photo.on.rectangle") }
                Button { onAttachFile() } label: { Label("Файлы", systemImage: "folder") }
            } label: {
                Image(systemName: "paperclip")
                    .font(.body.weight(.medium))
                    .frame(width: 40, height: 40)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel("Прикрепить")
            .accessibilityIdentifier("composer.attach")
            
            Button(action: onSend) {
                ZStack {
                    Circle()
                        .fill(canSend ? Color.accentColor : Color.secondary.opacity(0.25))
                    if isBusy {
                        ProgressView().tint(.white)
                    } else {
                        Image(systemName: "arrow.up")
                            .font(.body.weight(.bold))
                            .foregroundStyle(.white)
                    }
                }
                .frame(width: 36, height: 36)
            }
            .disabled(!canSend || isBusy)
            .padding(.trailing, 8)
            .padding(.leading, 2)
            .accessibilityLabel("Отправить")
            .accessibilityIdentifier("composer.sendInline")
        }
        .frame(height: 48)
        .background(.bar)
        .overlay(alignment: .top) { Divider() }
    }
    
    private var divider: some View {
        Rectangle()
            .fill(Color.secondary.opacity(0.25))
            .frame(width: 1, height: 22)
            .padding(.horizontal, 4)
    }
    
    private func tool(_ symbol: String, _ label: String, enabled: Bool = true, action: @escaping () -> Void) -> some View {
        Button {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            action()
        } label: {
            Image(systemName: symbol)
                .font(.body.weight(.medium))
                .frame(width: 40, height: 40)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .foregroundStyle(enabled ? Color.primary : Color.secondary.opacity(0.4))
        .disabled(!enabled)
        .accessibilityLabel(label)
    }
    
    private var colorMenu: some View {
        Menu {
            ForEach(Self.colors, id: \.value) { item in
                Button {
                    controller.format(.color(item.value))
                } label: {
                    Label(item.name, systemImage: "circle.fill")
                }
                .tint(item.color)
            }
        } label: {
            Image(systemName: "paintpalette")
                .font(.body.weight(.medium))
                .frame(width: 40, height: 40)
        }
        .foregroundStyle(Color.primary)
        .accessibilityLabel("Цвет текста")
    }
    
    private var sizeMenu: some View {
        Menu {
            ForEach(1...7, id: \.self) { size in
                Button("Размер \(size)") { controller.format(.size(size)) }
            }
        } label: {
            Image(systemName: "textformat.size")
                .font(.body.weight(.medium))
                .frame(width: 40, height: 40)
        }
        .foregroundStyle(Color.primary)
        .accessibilityLabel("Размер текста")
    }
    
    private var alignMenu: some View {
        Menu {
            Button { controller.format(.left) } label: { Label("По левому краю", systemImage: "text.alignleft") }
            Button { controller.format(.center) } label: { Label("По центру", systemImage: "text.aligncenter") }
            Button { controller.format(.right) } label: { Label("По правому краю", systemImage: "text.alignright") }
        } label: {
            Image(systemName: "text.aligncenter")
                .font(.body.weight(.medium))
                .frame(width: 40, height: 40)
        }
        .foregroundStyle(Color.primary)
        .accessibilityLabel("Выравнивание")
    }
    
    private var moreMenu: some View {
        Menu {
            Button { onPrompt(.spoiler) } label: { Label("Спойлер с заголовком", systemImage: "eye.slash.circle") }
            Button { onPrompt(.image) } label: { Label("Изображение по ссылке", systemImage: "photo") }
            if !isQMS {
                Button { controller.format(.offtop) } label: { Label("Оффтоп", systemImage: "cup.and.saucer") }
                Button { controller.format(.hide) } label: { Label("Скрытый текст [HIDE]", systemImage: "lock") }
            }
            Button { controller.format(.sup) } label: { Label("Верхний индекс", systemImage: "textformat.superscript") }
            Button { controller.format(.sub) } label: { Label("Нижний индекс", systemImage: "textformat.subscript") }
            if !isQMS {
                Section("Только для кураторов и модераторов") {
                    Button("[CUR]") { controller.format(.cur) }
                    Button("[MOD]") { controller.format(.mod) }
                    Button("[EX]") { controller.format(.ex) }
                }
            }
        } label: {
            Image(systemName: "ellipsis.circle")
                .font(.body.weight(.medium))
                .frame(width: 40, height: 40)
        }
        .foregroundStyle(Color.primary)
        .accessibilityLabel("Другие теги")
    }
}
