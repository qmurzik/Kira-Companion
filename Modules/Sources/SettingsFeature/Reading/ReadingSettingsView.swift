//
//  ReadingSettingsView.swift
//  SettingsFeature
//
//  ForPDA Next: reading, density and editor preferences + import/export of local settings.
//

import SwiftUI
import UniformTypeIdentifiers
import ComposableArchitecture
import SharedUI
import Models
import PersistenceKeys

public struct ReadingSettingsView: View {
    
    @AppStorage(ReadingPreferences.fontScaleKey) private var fontScale: Double = 1
    @AppStorage(ReadingPreferences.lineSpacingKey) private var lineSpacing: Double = 0
    @AppStorage(ReadingPreferences.contentWidthKey) private var contentWidth: Double = 0
    @AppStorage(ReadingPreferences.densityKey) private var density: String = InterfaceDensity.regular.rawValue
    @AppStorage(ReadingPreferences.avatarSizeKey) private var avatarScale: Double = 1
    @AppStorage("composer.showCharacterCount") private var showCharacterCount = false
    @AppStorage("composer.highlightTags") private var highlightTags = true
    @AppStorage("composer.fontScale") private var composerFontScale: Double = 1
    
    @Shared(.appSettings) private var appSettings: AppSettings
    
    @State private var exportURL: URL?
    @State private var isImporting = false
    @State private var importMessage: String?
    
    public init() {}
    
    public var body: some View {
        Form {
            Section {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Пример текста поста. Съешь же ещё этих мягких французских булок, да выпей чаю.")
                        .font(.system(size: 16 * fontScale))
                        .lineSpacing(lineSpacing)
                        .padding(.vertical, 4)
                    LabeledContent("Размер текста", value: "\(Int(fontScale * 100))%")
                    Slider(value: $fontScale, in: 0.8...1.6, step: 0.05)
                    LabeledContent("Межстрочный интервал", value: "\(Int(lineSpacing)) pt")
                    Slider(value: $lineSpacing, in: 0...10, step: 1)
                }
                Picker("Ширина текста", selection: $contentWidth) {
                    Text("Во всю ширину").tag(0.0)
                    Text("Широкая").tag(720.0)
                    Text("Узкая").tag(560.0)
                }
            } header: {
                Text("Чтение тем")
            } footer: {
                Text("Применяется к страницам, загруженным после изменения. Учитывается системный размер шрифта (Dynamic Type).")
            }
            
            Section("Интерфейс") {
                Picker("Плотность списков", selection: $density) {
                    ForEach(InterfaceDensity.allCases) { Text($0.title).tag($0.rawValue) }
                }
                VStack(alignment: .leading) {
                    LabeledContent("Размер аватаров", value: "\(Int(avatarScale * 100))%")
                    Slider(value: $avatarScale, in: 0.7...1.5, step: 0.1)
                }
            }
            
            Section("Редактор сообщений") {
                Toggle("Подсветка BB-тегов", isOn: $highlightTags)
                Toggle("Счётчик символов", isOn: $showCharacterCount)
                VStack(alignment: .leading) {
                    LabeledContent("Размер текста в редакторе", value: "\(Int(composerFontScale * 100))%")
                    Slider(value: $composerFontScale, in: 0.8...1.6, step: 0.05)
                }
            }
            
            Section {
                Button {
                    exportURL = SettingsBackup.export(appSettings: appSettings)
                } label: {
                    Label("Экспортировать настройки", systemImage: "square.and.arrow.up")
                }
                if let exportURL {
                    ShareLink(item: exportURL) {
                        Label("Поделиться файлом настроек", systemImage: "doc.badge.arrow.up")
                    }
                }
                Button {
                    isImporting = true
                } label: {
                    Label("Импортировать настройки", systemImage: "square.and.arrow.down")
                }
            } header: {
                Text("Резервная копия")
            } footer: {
                Text("Файл содержит только локальные настройки приложения — без логина, пароля и токенов.")
            }
        }
        .navigationTitle("Чтение и редактор")
        .fileImporter(isPresented: $isImporting, allowedContentTypes: [.json]) { result in
            guard case let .success(url) = result else { return }
            do {
                let settings = try SettingsBackup.import(from: url)
                if let settings {
                    $appSettings.withLock { $0 = settings }
                }
                importMessage = "Настройки импортированы"
            } catch {
                importMessage = "Не удалось прочитать файл: \(error.localizedDescription)"
            }
        }
        .alert(importMessage ?? "", isPresented: Binding(get: { importMessage != nil }, set: { if !$0 { importMessage = nil } })) {
            Button("OK", role: .cancel) {}
        }
    }
}

// MARK: - Backup

enum SettingsBackup {
    static let defaultsKeys = [
        ReadingPreferences.fontScaleKey, ReadingPreferences.lineSpacingKey, ReadingPreferences.contentWidthKey,
        ReadingPreferences.densityKey, ReadingPreferences.avatarSizeKey,
        "composer.showCharacterCount", "composer.highlightTags", "composer.fontScale"
    ]
    
    struct Payload: Codable {
        var version = 1
        var appSettings: AppSettings?
        var defaults: [String: Double]
        var strings: [String: String]
    }
    
    static func export(appSettings: AppSettings) -> URL? {
        var numbers: [String: Double] = [:]
        var strings: [String: String] = [:]
        for key in defaultsKeys {
            switch UserDefaults.standard.object(forKey: key) {
            case let value as Double: numbers[key] = value
            case let value as Bool: numbers[key] = value ? 1 : 0
            case let value as String: strings[key] = value
            default: break
            }
        }
        let payload = Payload(appSettings: appSettings, defaults: numbers, strings: strings)
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        guard let data = try? encoder.encode(payload) else { return nil }
        let url = FileManager.default.temporaryDirectory.appending(component: "ForPDA-Next-settings.json")
        do {
            try data.write(to: url, options: .atomic)
            return url
        } catch {
            return nil
        }
    }
    
    /// Applies UserDefaults values and returns AppSettings to store (if present).
    static func `import`(from url: URL) throws -> AppSettings? {
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        let data = try Data(contentsOf: url)
        let payload = try JSONDecoder().decode(Payload.self, from: data)
        for (key, value) in payload.defaults where defaultsKeys.contains(key) {
            if key.hasPrefix("composer.show") || key == "composer.highlightTags" {
                UserDefaults.standard.set(value != 0, forKey: key)
            } else {
                UserDefaults.standard.set(value, forKey: key)
            }
        }
        for (key, value) in payload.strings where defaultsKeys.contains(key) {
            UserDefaults.standard.set(value, forKey: key)
        }
        return payload.appSettings
    }
}
