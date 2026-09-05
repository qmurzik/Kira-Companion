package com.kira.companion.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kira.companion.model.AiProviderType
import com.kira.companion.model.AppTheme
import com.kira.companion.model.KiraPosition
import com.kira.companion.model.KiraSize
import com.kira.companion.model.ReactionFrequency
import com.kira.companion.model.ScreenEdge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "kira_settings")

/**
 * Single source of truth for all user-facing app settings, backed by Jetpack DataStore.
 * Every value is exposed as a [Flow] so the overlay service, chat screen and settings
 * screen all react live to changes made from anywhere in the app.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val RANDOM_REACTIONS_ENABLED = booleanPreferencesKey("random_reactions_enabled")
        val REACTION_FREQUENCY = stringPreferencesKey("reaction_frequency")
        val KIRA_SIZE = stringPreferencesKey("kira_size")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val THEME = stringPreferencesKey("theme")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val POSITION_EDGE = stringPreferencesKey("position_edge")
        val POSITION_VERTICAL_FRACTION = floatPreferencesKey("position_vertical_fraction")
    }

    val overlayEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.OVERLAY_ENABLED] ?: false }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.OVERLAY_ENABLED] = enabled }
    }

    val randomReactionsEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.RANDOM_REACTIONS_ENABLED] ?: true }

    suspend fun setRandomReactionsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.RANDOM_REACTIONS_ENABLED] = enabled }
    }

    val reactionFrequency: Flow<ReactionFrequency> = context.dataStore.data.map {
        it[Keys.REACTION_FREQUENCY]?.let { name -> enumOrNull<ReactionFrequency>(name) } ?: ReactionFrequency.NORMAL
    }

    suspend fun setReactionFrequency(frequency: ReactionFrequency) {
        context.dataStore.edit { it[Keys.REACTION_FREQUENCY] = frequency.name }
    }

    val kiraSize: Flow<KiraSize> = context.dataStore.data.map {
        it[Keys.KIRA_SIZE]?.let { name -> enumOrNull<KiraSize>(name) } ?: KiraSize.MEDIUM
    }

    suspend fun setKiraSize(size: KiraSize) {
        context.dataStore.edit { it[Keys.KIRA_SIZE] = size.name }
    }

    val soundEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.SOUND_ENABLED] ?: true }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }

    val notificationsEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: true }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    val theme: Flow<AppTheme> = context.dataStore.data.map {
        it[Keys.THEME]?.let { name -> enumOrNull<AppTheme>(name) } ?: AppTheme.SYSTEM
    }

    suspend fun setTheme(theme: AppTheme) {
        context.dataStore.edit { it[Keys.THEME] = theme.name }
    }

    val aiProvider: Flow<AiProviderType> = context.dataStore.data.map {
        it[Keys.AI_PROVIDER]?.let { name -> enumOrNull<AiProviderType>(name) } ?: AiProviderType.MOCK
    }

    suspend fun setAiProvider(provider: AiProviderType) {
        context.dataStore.edit { it[Keys.AI_PROVIDER] = provider.name }
    }

    val kiraPosition: Flow<KiraPosition> = context.dataStore.data.map { prefs ->
        KiraPosition(
            edge = prefs[Keys.POSITION_EDGE]?.let { name -> enumOrNull<ScreenEdge>(name) } ?: ScreenEdge.RIGHT,
            verticalFraction = prefs[Keys.POSITION_VERTICAL_FRACTION] ?: 0.35f,
        )
    }

    suspend fun setKiraPosition(position: KiraPosition) {
        context.dataStore.edit {
            it[Keys.POSITION_EDGE] = position.edge.name
            it[Keys.POSITION_VERTICAL_FRACTION] = position.verticalFraction.coerceIn(0f, 1f)
        }
    }

    suspend fun resetKiraPosition() {
        setKiraPosition(KiraPosition())
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
        try {
            enumValueOf<T>(name)
        } catch (e: IllegalArgumentException) {
            null
        }
}
