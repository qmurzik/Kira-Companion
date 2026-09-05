package com.kira.companion.data

import android.content.Context
import com.kira.companion.model.ChatMessage
import com.kira.companion.model.ChatRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local chat history persistence. Stored as a small JSON array on disk (no database
 * dependency needed for a simple, bounded message log) and mirrored in an in-memory
 * [StateFlow] so the chat screen updates live.
 */
class ChatHistoryStore(context: Context) {

    private val file = File(context.filesDir, "kira_chat_history.json")
    private val mutex = Mutex()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    suspend fun load() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                _messages.value = readFromDisk()
            }
        }
    }

    suspend fun append(message: ChatMessage) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val updated = (_messages.value + message).takeLast(MAX_MESSAGES)
                writeToDisk(updated)
                _messages.value = updated
            }
        }
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                file.delete()
                _messages.value = emptyList()
            }
        }
    }

    private fun readFromDisk(): List<ChatMessage> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        ChatMessage(
                            id = obj.getString("id"),
                            role = ChatRole.valueOf(obj.getString("role")),
                            text = obj.getString("text"),
                            timestampMillis = obj.getLong("timestamp"),
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeToDisk(messages: List<ChatMessage>) {
        val array = JSONArray()
        for (message in messages) {
            val obj = JSONObject()
            obj.put("id", message.id)
            obj.put("role", message.role.name)
            obj.put("text", message.text)
            obj.put("timestamp", message.timestampMillis)
            array.put(obj)
        }
        file.writeText(array.toString())
    }

    private companion object {
        const val MAX_MESSAGES = 500
    }
}
