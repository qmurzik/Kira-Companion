package com.kira.companion.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the user's own OpenAI API key encrypted-at-rest on the device, using the
 * Android Keystore-backed [MasterKey]. The key never leaves the device except in
 * requests the user explicitly configured to the AI provider they chose.
 *
 * IMPORTANT: storing a raw API key on a client device is inherently riskier than a
 * server-side proxy (a rooted/compromised device could still extract it from memory
 * while the app runs). This is acceptable for a personal-use v1 app; for production
 * distribution, route requests through your own backend that holds the real key and
 * exchange it for a scoped, revocable user token instead.
 */
class SecureKeyStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "kira_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getOpenAiApiKey(): String = prefs.getString(KEY_OPENAI_API_KEY, "") ?: ""

    fun setOpenAiApiKey(apiKey: String) {
        prefs.edit().putString(KEY_OPENAI_API_KEY, apiKey.trim()).apply()
    }

    fun clearOpenAiApiKey() {
        prefs.edit().remove(KEY_OPENAI_API_KEY).apply()
    }

    private companion object {
        const val KEY_OPENAI_API_KEY = "openai_api_key"
    }
}
