package com.kira.companion.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to the OpenAI Chat Completions API using an API key the user entered themselves
 * in Settings. The key is read lazily via [apiKeyProvider] (backed by [com.kira.companion.data.SecureKeyStore])
 * so it is never hard-coded and never bundled into the APK.
 */
class OpenAiProvider(private val apiKeyProvider: () -> String) : AiProvider {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun sendMessage(message: String, history: List<Pair<Boolean, String>>): String =
        withContext(Dispatchers.IO) {
            val apiKey = apiKeyProvider()
            if (apiKey.isBlank()) {
                throw AiProviderException(
                    "API-ключ OpenAI не задан. Добавь его в Настройки → Настройка ИИ.",
                )
            }

            val messagesJson = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                history.takeLast(10).forEach { (isUser, text) ->
                    put(
                        JSONObject()
                            .put("role", if (isUser) "user" else "assistant")
                            .put("content", text),
                    )
                }
                put(JSONObject().put("role", "user").put("content", message))
            }

            val bodyJson = JSONObject()
                .put("model", "gpt-4o-mini")
                .put("messages", messagesJson)
                .put("temperature", 0.8)
                .put("max_tokens", 400)

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val errorMessage = runCatching {
                            JSONObject(bodyText).optJSONObject("error")?.optString("message")
                        }.getOrNull()
                        throw AiProviderException(
                            errorMessage?.takeIf { it.isNotBlank() }
                                ?: "OpenAI вернул ошибку ${response.code}",
                        )
                    }
                    val content = JSONObject(bodyText)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    content.trim()
                }
            } catch (e: IOException) {
                throw AiProviderException("Не удалось связаться с OpenAI. Проверь интернет-соединение.", e)
            }
        }

    private companion object {
        const val SYSTEM_PROMPT = "Ты — Кира, дружелюбный виртуальный AI-компаньон в мобильном " +
            "приложении. Отвечай тепло, кратко (1-3 предложения), на языке пользователя, с лёгкой " +
            "эмпатией и без излишней формальности."
    }
}
