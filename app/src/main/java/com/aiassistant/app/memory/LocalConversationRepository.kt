package com.aiassistant.app.memory

import android.content.Context
import com.aiassistant.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class Role { USER, ASSISTANT }

data class ChatMessage(val role: Role, val text: String, val timeMs: Long = System.currentTimeMillis())

/**
 * Local transcript store. Persisted to app-private storage only when "Save conversation history" is on.
 * This is a transcript, NOT a long-term memory system, and nothing here is sent to Gemini automatically.
 */
class LocalConversationRepository(context: Context, private val settings: SettingsRepository) {
    private val file = File(context.applicationContext.filesDir, "conversation.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private val _messages = MutableStateFlow(load())
    val messages: StateFlow<List<ChatMessage>> = _messages

    fun add(role: Role, text: String) {
        _messages.update { (it + ChatMessage(role, text)).takeLast(MAX_MESSAGES) }
        scope.launch { if (settings.flow.first().saveHistory) write() }
    }

    fun clear() {
        _messages.value = emptyList()
        scope.launch { synchronized(lock) { file.delete() } }
    }

    /** Called when the user flips the "save history" switch. */
    fun applyPrivacy(save: Boolean) {
        scope.launch { if (save) write() else synchronized(lock) { file.delete() } }
    }

    fun storedBytes(): Long = if (file.exists()) file.length() else 0L

    private fun write() = synchronized(lock) {
        val arr = JSONArray()
        _messages.value.forEach {
            arr.put(JSONObject().put("r", it.role.name).put("t", it.text).put("ts", it.timeMs))
        }
        file.writeText(arr.toString())
    }

    private fun load(): List<ChatMessage> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            ChatMessage(Role.valueOf(o.getString("r")), o.getString("t"), o.optLong("ts"))
        }
    }.getOrDefault(emptyList())

    companion object { private const val MAX_MESSAGES = 200 }
}
