package com.kira.companion.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kira.companion.KiraApplication
import com.kira.companion.model.AiProviderType
import com.kira.companion.model.AppTheme
import com.kira.companion.model.KiraSize
import com.kira.companion.model.ReactionFrequency
import com.kira.companion.overlay.OverlayControl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val app: KiraApplication) : ViewModel() {

    private fun <T> stateOf(flow: Flow<T>, initial: T): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)

    val overlayEnabled = stateOf(app.settingsRepository.overlayEnabled, false)
    val reactionFrequency = stateOf(app.settingsRepository.reactionFrequency, ReactionFrequency.NORMAL)
    val kiraSize = stateOf(app.settingsRepository.kiraSize, KiraSize.MEDIUM)
    val soundEnabled = stateOf(app.settingsRepository.soundEnabled, true)
    val notificationsEnabled = stateOf(app.settingsRepository.notificationsEnabled, true)
    val theme = stateOf(app.settingsRepository.theme, AppTheme.SYSTEM)
    val aiProvider = stateOf(app.settingsRepository.aiProvider, AiProviderType.CHATGPT)

    private val _apiKey = MutableStateFlow(app.secureKeyStore.getOpenAiApiKey())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    fun setOverlayEnabled(context: Context, enabled: Boolean) {
        viewModelScope.launch { OverlayControl.setEnabled(context, app, enabled) }
    }

    fun setReactionFrequency(frequency: ReactionFrequency) {
        viewModelScope.launch { app.settingsRepository.setReactionFrequency(frequency) }
    }

    fun setKiraSize(size: KiraSize) {
        viewModelScope.launch { app.settingsRepository.setKiraSize(size) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { app.settingsRepository.setSoundEnabled(enabled) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { app.settingsRepository.setNotificationsEnabled(enabled) }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { app.settingsRepository.setTheme(theme) }
    }

    fun setAiProvider(provider: AiProviderType) {
        viewModelScope.launch { app.settingsRepository.setAiProvider(provider) }
    }

    fun setApiKey(key: String) {
        app.secureKeyStore.setOpenAiApiKey(key)
        _apiKey.value = key
    }

    fun clearChatHistory() {
        viewModelScope.launch { app.chatHistoryStore.clear() }
    }

    fun resetKiraPosition() {
        viewModelScope.launch { app.settingsRepository.resetKiraPosition() }
    }
}
