package com.kira.companion.ai

import com.kira.companion.data.SecureKeyStore
import com.kira.companion.data.SettingsRepository
import kotlinx.coroutines.flow.first

/** Resolves the currently configured [AiProvider] based on live user settings. */
class AiProviderFactory(
    private val settingsRepository: SettingsRepository,
    private val secureKeyStore: SecureKeyStore,
) {
    private val mockProvider: AiProvider = MockAiProvider()
    private val openAiProvider: AiProvider = OpenAiProvider(apiKeyProvider = secureKeyStore::getOpenAiApiKey)

    suspend fun current(): AiProvider = when (settingsRepository.aiProvider.first()) {
        com.kira.companion.model.AiProviderType.MOCK -> mockProvider
        com.kira.companion.model.AiProviderType.OPENAI -> openAiProvider
    }
}
