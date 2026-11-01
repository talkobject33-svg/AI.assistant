package com.aiassistant.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "assistant_settings")

enum class VoiceLanguage(val label: String) {
    ENGLISH("English"),
    HINDI("Hindi"),
    HINGLISH("Hinglish"),
    AUTO("Auto (match my language)"),
}

data class AppSettings(
    val assistantName: String? = null,
    val model: String = DEFAULT_MODEL,
    val language: VoiceLanguage = VoiceLanguage.ENGLISH,
    val continuous: Boolean = true,
    val bargeIn: Boolean = true,
    val saveHistory: Boolean = true,
) {
    companion object {
        /** Model ID verified in Google's Live API docs (March 2026 launch). Always user-configurable. */
        const val DEFAULT_MODEL = "gemini-3.1-flash-live-preview"
        private val MODEL_REGEX = Regex("^[A-Za-z0-9._\\-]{1,80}$")
        fun isValidModelId(id: String) = MODEL_REGEX.matches(id)
    }
}

/** Assistant identity + user preferences. Stored with DataStore so they survive app/phone restarts. */
class SettingsRepository(context: Context) {
    private val ds = context.applicationContext.dataStore

    private object K {
        val NAME = stringPreferencesKey("assistant_name")
        val MODEL = stringPreferencesKey("model_id")
        val LANG = stringPreferencesKey("voice_language")
        val CONTINUOUS = booleanPreferencesKey("continuous")
        val BARGE_IN = booleanPreferencesKey("barge_in")
        val SAVE_HISTORY = booleanPreferencesKey("save_history")
    }

    val flow: Flow<AppSettings> = ds.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p: Preferences ->
            AppSettings(
                assistantName = p[K.NAME],
                model = p[K.MODEL] ?: AppSettings.DEFAULT_MODEL,
                language = runCatching { VoiceLanguage.valueOf(p[K.LANG] ?: "") }.getOrDefault(VoiceLanguage.ENGLISH),
                continuous = p[K.CONTINUOUS] ?: true,
                bargeIn = p[K.BARGE_IN] ?: true,
                saveHistory = p[K.SAVE_HISTORY] ?: true,
            )
        }

    suspend fun setAssistantName(name: String) = ds.edit { it[K.NAME] = name }
    suspend fun setModel(id: String) = ds.edit { it[K.MODEL] = id.removePrefix("models/") }
    suspend fun setLanguage(l: VoiceLanguage) = ds.edit { it[K.LANG] = l.name }
    suspend fun setContinuous(v: Boolean) = ds.edit { it[K.CONTINUOUS] = v }
    suspend fun setBargeIn(v: Boolean) = ds.edit { it[K.BARGE_IN] = v }
    suspend fun setSaveHistory(v: Boolean) = ds.edit { it[K.SAVE_HISTORY] = v }
}
