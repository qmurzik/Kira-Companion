package com.kira.companion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kira.companion.ui.chat.ChatViewModel
import com.kira.companion.ui.home.HomeViewModel
import com.kira.companion.ui.settings.SettingsViewModel

/** Tiny manual ViewModel factory - the app has exactly three ViewModels, no DI framework needed. */
class KiraViewModelFactory(private val app: KiraApplication) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(app) as T
        modelClass.isAssignableFrom(ChatViewModel::class.java) -> ChatViewModel(app) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(app) as T
        else -> throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
    }
}
