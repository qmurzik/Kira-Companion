package com.kira.companion.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kira.companion.KiraApplication
import com.kira.companion.model.KiraEmotion
import com.kira.companion.overlay.OverlayControl
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val app: KiraApplication) : ViewModel() {

    val overlayEnabled: StateFlow<Boolean> = app.settingsRepository.overlayEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val emotion: StateFlow<KiraEmotion> = app.emotionController.emotion

    fun setOverlayEnabled(context: Context, enabled: Boolean) {
        viewModelScope.launch { OverlayControl.setEnabled(context, app, enabled) }
    }
}
