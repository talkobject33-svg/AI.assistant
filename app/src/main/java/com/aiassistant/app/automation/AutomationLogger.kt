package com.aiassistant.app.automation

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class LogEntry(val timeMs: Long, val action: String, val status: ActionExecutionStatus, val message: String)

/** In-memory ring buffer of recent automation results. Never records typed text or screen content. */
class AutomationLogger {
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries

    fun log(action: String, result: ActionResult) {
        _entries.update { (it + LogEntry(System.currentTimeMillis(), action, result.status, result.message)).takeLast(100) }
        Log.d("Automation", "$action -> ${result.status}")
    }
}
