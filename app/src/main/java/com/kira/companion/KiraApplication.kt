package com.kira.companion

import android.app.Application
import com.kira.companion.ai.AiProviderFactory
import com.kira.companion.behavior.KiraBehaviorController
import com.kira.companion.data.ChatHistoryStore
import com.kira.companion.data.SecureKeyStore
import com.kira.companion.data.SettingsRepository
import com.kira.companion.emotion.EmotionController
import com.kira.companion.render.ModelStatusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Lightweight manual service locator (no DI framework needed for this app's size).
 * Every dependency here is a process-wide singleton so overlay service, chat screen
 * and settings screen all observe/mutate the exact same state.
 */
class KiraApplication : Application() {

    val applicationScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val secureKeyStore: SecureKeyStore by lazy { SecureKeyStore(this) }
    val chatHistoryStore: ChatHistoryStore by lazy { ChatHistoryStore(this) }
    val aiProviderFactory: AiProviderFactory by lazy {
        AiProviderFactory(settingsRepository, secureKeyStore)
    }
    /** Owns Kira's emotion, gaze, blink, random-behavior, speech bubble and touch reaction
     *  state - see [KiraBehaviorController] for why these stay separate concerns. */
    val behaviorController: KiraBehaviorController by lazy { KiraBehaviorController(applicationScope) }

    /** Kept for existing call sites that only need the emotion sub-controller. */
    val emotionController: EmotionController get() = behaviorController.emotion

    val modelStatusRepository: ModelStatusRepository by lazy { ModelStatusRepository(this) }
}
