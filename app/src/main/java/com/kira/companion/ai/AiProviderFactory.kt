package com.kira.companion.ai

import com.kira.companion.data.SecureKeyStore
import com.kira.companion.data.SettingsRepository
import com.kira.companion.model.AiProviderType
import kotlinx.coroutines.flow.first

/**
 * Resolves the currently configured [AiProvider] based on live user settings. Only
 * [AiProviderType.LOCAL_DEMO] and [AiProviderType.CUSTOM_API] ever reach this factory -
 * when the mode is [AiProviderType.CHATGPT] the chat screen never calls an [AiProvider]
 * at all (see ChatScreen), so [current] falls back to the local demo provider there just
 * to keep this function total.
 */
class AiProviderFactory(
    private val settingsRepository: SettingsRepository,
    private val secureKeyStore: SecureKeyStore,
) {
    private val localDemoProvider: AiProvider = LocalDemoAiProvider()
    private val customApiProvider: AiProvider = OpenAiProvider(apiKeyProvider = secureKeyStore::getOpenAiApiKey)

    suspend fun current(): AiProvider = when (settingsRepository.aiProvider.first()) {
        AiProviderType.LOCAL_DEMO -> localDemoProvider
        AiProviderType.CUSTOM_API -> customApiProvider
        AiProviderType.CHATGPT -> localDemoProvider
    }
}
