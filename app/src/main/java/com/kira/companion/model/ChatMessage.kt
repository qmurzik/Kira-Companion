package com.kira.companion.model

enum class ChatRole { USER, KIRA }

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val timestampMillis: Long,
)
