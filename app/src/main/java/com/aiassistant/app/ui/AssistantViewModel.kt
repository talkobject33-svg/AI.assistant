package com.aiassistant.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aiassistant.app.AssistantApp
import com.aiassistant.app.ai.PhoneAction
import com.aiassistant.app.automation.AccessibilityAutomationService
import com.aiassistant.app.data.ApiKeyStore
import com.aiassistant.app.data.AppSettings
import com.aiassistant.app.data.VoiceLanguage
import com.aiassistant.app.memory.AssistantNameValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionState(val mic: Boolean = false, val notifications: Boolean = false)

class AssistantViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as AssistantApp).container

    /** null while DataStore is still loading, so the first-launch screen never flashes for existing users. */
    val settings: StateFlow<AppSettings?> =
        c.settings.flow.map<AppSettings, AppSettings?> { it }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state = c.live.state
    val error = c.live.error
    val liveUser = c.live.liveUser
    val liveAssistant = c.live.liveAssistant
    val callStart = c.live.callStart
    val reconnectAttempt = c.live.reconnectAttempt
    val audioLevel = c.live.audioLevel
    val diagnostics = c.live.diagnostics
    val actionStatus = c.router.status
    val confirmation = c.router.pendingConfirmation
    val paused = c.actionPermissions.paused
    val accessibilityOn = AccessibilityAutomationService.connected
    val messages = c.conversations.messages
    val automationLog = c.logger.entries

    private val _perm = MutableStateFlow(readPerms())
    val permissions: StateFlow<PermissionState> = _perm
    private val _masked = MutableStateFlow(c.apiKeys.masked())
    val maskedKey: StateFlow<String> = _masked
    val testResult = MutableStateFlow<String?>(null)

    private fun readPerms() = PermissionState(c.permissions.hasMic(), c.permissions.hasNotifications())
    fun refreshPermissions() { _perm.value = readPerms() }
    fun hasMic() = c.permissions.hasMic()

    // ---- session ----
    fun startSession() = c.live.startSession()
    fun endCall() = c.live.endCall()
    fun stopSpeaking() = c.live.stopSpeaking()
    fun tapToTalk() = c.live.tapToTalk()
    fun stopAll() = c.live.stopAll()
    fun clearError() = c.live.clearError()
    fun onMicDenied() = c.live.reportError(
        "Microphone permission was denied. Voice conversation needs it. Grant it in Settings > Permissions."
    )

    fun respondConfirmation(ok: Boolean) = c.router.respondConfirmation(ok)

    // ---- identity & settings ----
    /** Returns an error message, or null when saved. The new name reaches live sessions automatically. */
    fun saveName(raw: String): String? = when (val r = AssistantNameValidator.validate(raw)) {
        is AssistantNameValidator.Result.Invalid -> r.reason
        is AssistantNameValidator.Result.Valid -> { viewModelScope.launch { c.settings.setAssistantName(r.name) }; null }
    }

    fun saveModel(id: String): String? {
        val clean = id.trim().removePrefix("models/")
        if (!AppSettings.isValidModelId(clean)) return "Model IDs may contain letters, digits, '.', '-' and '_'."
        viewModelScope.launch { c.settings.setModel(clean) }
        return null
    }

    fun resetModel() { viewModelScope.launch { c.settings.setModel(AppSettings.DEFAULT_MODEL) } }
    fun setLanguage(l: VoiceLanguage) { viewModelScope.launch { c.settings.setLanguage(l) } }
    fun setContinuous(v: Boolean) { viewModelScope.launch { c.settings.setContinuous(v) } }
    fun setBargeIn(v: Boolean) { viewModelScope.launch { c.settings.setBargeIn(v) } }
    fun setSaveHistory(v: Boolean) {
        viewModelScope.launch { c.settings.setSaveHistory(v) }
        c.conversations.applyPrivacy(v)
    }

    fun clearHistory() = c.conversations.clear()
    fun storedHistoryBytes() = c.conversations.storedBytes()

    // ---- API key ----
    fun saveApiKey(raw: String): String? {
        if (!ApiKeyStore.looksValid(raw)) return "That doesn't look like a Gemini API key (no spaces, 20+ characters)."
        c.apiKeys.set(raw)
        _masked.value = c.apiKeys.masked()
        return null
    }

    fun clearApiKey() { c.apiKeys.clear(); _masked.value = "" }

    fun testConnection(draft: String?) {
        viewModelScope.launch {
            testResult.value = "Testing…"
            testResult.value = c.live.testConnection(draft)
        }
    }

    // ---- accessibility ----
    fun openAccessibilitySettings() = c.permissions.openAccessibilitySettings()
    fun openAppSettings() = c.permissions.openAppSettings()
    fun disableAccessibilityService() { AccessibilityAutomationService.instance?.disableSelf() }
    fun setControlPaused(p: Boolean) { c.actionPermissions.paused.value = p }
    fun runAccessibilityTest() { viewModelScope.launch { c.router.execute(PhoneAction.Home) } }
}
