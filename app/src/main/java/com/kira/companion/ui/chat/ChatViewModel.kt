package com.kira.companion.ui.chat

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kira.companion.KiraApplication
import com.kira.companion.ai.AiProviderException
import com.kira.companion.model.ChatMessage
import com.kira.companion.model.ChatRole
import com.kira.companion.model.KiraEmotion
import com.kira.companion.notifications.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(private val app: KiraApplication) : ViewModel() {

    val messages: StateFlow<List<ChatMessage>> = app.chatHistoryStore.messages
    val emotion: StateFlow<KiraEmotion> = app.emotionController.emotion

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    init {
        viewModelScope.launch { app.chatHistoryStore.load() }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            val userMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = ChatRole.USER,
                text = trimmed,
                timestampMillis = System.currentTimeMillis(),
            )
            app.chatHistoryStore.append(userMessage)
            app.emotionController.onUserMessage(trimmed)

            _isTyping.value = true
            app.emotionController.onWaitingForReply()

            val history = app.chatHistoryStore.messages.value.map { it.role == ChatRole.USER to it.text }
            val provider = app.aiProviderFactory.current()
            val replyText = try {
                provider.sendMessage(trimmed, history)
            } catch (e: AiProviderException) {
                e.message ?: DEFAULT_ERROR
            } catch (e: Exception) {
                DEFAULT_ERROR
            }

            _isTyping.value = false
            app.emotionController.onKiraReply(replyText)

            val kiraMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = ChatRole.KIRA,
                text = replyText,
                timestampMillis = System.currentTimeMillis(),
            )
            app.chatHistoryStore.append(kiraMessage)

            val appInForeground = ProcessLifecycleOwner.get().lifecycle.currentState
                .isAtLeast(Lifecycle.State.STARTED)
            if (!appInForeground && app.settingsRepository.notificationsEnabled.first()) {
                NotificationHelper.showChatReplyNotification(app, replyText)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch { app.chatHistoryStore.clear() }
    }

    private companion object {
        const val DEFAULT_ERROR = "Ой, что-то пошло не так. Попробуй написать ещё раз чуть позже."
    }
}
