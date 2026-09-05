package com.kira.companion.ai

/**
 * Abstraction over "however Kira produces a reply". Swappable so the rest of the app
 * (chat screen, emotion engine) never needs to know whether the answer came from a
 * local mock, OpenAI, or some future on-device/remote LLM.
 */
interface AiProvider {
    /**
     * @param message the user's latest message.
     * @param history prior turns, oldest first, as (isUser, text) pairs, for short context.
     * @return Kira's reply text.
     */
    suspend fun sendMessage(message: String, history: List<Pair<Boolean, String>>): String
}

class AiProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)
