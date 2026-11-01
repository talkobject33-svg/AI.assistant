package com.aiassistant.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import androidx.core.content.ContextCompat
import com.aiassistant.app.ai.ActionParser
import com.aiassistant.app.ai.ActionSchema
import com.aiassistant.app.ai.AssistantInstructions
import com.aiassistant.app.ai.PhoneAction
import com.aiassistant.app.automation.ActionExecutionStatus
import com.aiassistant.app.automation.ActionResult
import com.aiassistant.app.automation.PhoneActionRouter
import com.aiassistant.app.data.ApiKeyStore
import com.aiassistant.app.data.AppSettings
import com.aiassistant.app.data.SettingsRepository
import com.aiassistant.app.data.VoiceLanguage
import com.aiassistant.app.memory.LocalConversationRepository
import com.aiassistant.app.memory.Role
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class Diagnostics(
    val model: String = "",
    val reconnects: Int = 0,
    val lastCloseCode: Int? = null,
    val lastCloseReason: String = "",
    val lastError: String = "",
    val lastTokenCount: Int = 0,
    val turns: Int = 0,
    val audioKbSent: Long = 0,
    val audioKbReceived: Long = 0,
)

/**
 * Real-time voice engine over the Gemini Live API (stateful WebSocket, BidiGenerateContent).
 *  - Mic: 16 kHz PCM16 mono streamed as realtimeInput.audio
 *  - Speaker: 24 kHz PCM16 mono from serverContent.modelTurn
 *  - Phone control: Live API function calling -> ActionParser -> PhoneActionRouter -> toolResponse
 * All session logic runs on one serialized dispatcher, so no locks are needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GeminiLiveManager(
    context: Context,
    private val settingsRepo: SettingsRepository,
    private val apiKeys: ApiKeyStore,
    private val router: PhoneActionRouter,
    private val conversations: LocalConversationRepository,
) {
    companion object {
        private const val WS_BASE =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private const val MAX_ATTEMPTS = 5
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val audioIn = AudioInputManager()
    private val audioOut = AudioOutputManager()

    // ---- observable state ----
    private val _state = MutableStateFlow(VoiceSessionState.IDLE)
    val state: StateFlow<VoiceSessionState> = _state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _liveUser = MutableStateFlow("")
    val liveUser: StateFlow<String> = _liveUser
    private val _liveAssistant = MutableStateFlow("")
    val liveAssistant: StateFlow<String> = _liveAssistant
    private val _callStart = MutableStateFlow<Long?>(null)
    val callStart: StateFlow<Long?> = _callStart
    private val _attempt = MutableStateFlow(0)
    val reconnectAttempt: StateFlow<Int> = _attempt
    private val _level = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _level
    private val _diag = MutableStateFlow(Diagnostics())
    val diagnostics: StateFlow<Diagnostics> = _diag

    // ---- session internals (touched only on `scope`'s dispatcher unless @Volatile) ----
    @Volatile private var socket: WebSocket? = null
    private var socketId = 0
    private var userStopped = true
    private var setupDone = false
    private var attempt = 0
    private var resumeHandle: String? = null
    private var greeted = false
    private var pendingSetup = ""
    private var sessionName: String? = null
    private var sessionModel: String? = null
    private var sessionLang: VoiceLanguage? = null
    private var reconnectJob: Job? = null
    private var drainJob: Job? = null
    private var toolJob: Job? = null
    @Volatile private var micOpen = true
    @Volatile private var cfgBargeIn = true
    @Volatile private var cfgContinuous = true
    private var discardAudio = false
    private val userBuf = StringBuilder()
    private val asstBuf = StringBuilder()
    private val sentBytes = AtomicLong()
    private val recvBytes = AtomicLong()

    init {
        scope.launch {
            settingsRepo.flow.collect { s ->
                cfgBargeIn = s.bargeIn
                cfgContinuous = s.continuous
                val live = socket != null && !userStopped
                if (live && (s.assistantName != sessionName || s.model != sessionModel || s.language != sessionLang)) {
                    reconnectNow() // new name/model/language take effect immediately via a fresh setup message
                }
            }
        }
    }

    // ================= public API =================

    fun startSession() {
        scope.launch {
            if (_state.value != VoiceSessionState.IDLE && _state.value != VoiceSessionState.ERROR) return@launch
            _error.value = null
            if (apiKeys.get().isNullOrBlank()) { fail("No Gemini API key. Add one in Settings."); return@launch }
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                fail("Microphone permission is required for voice conversation."); return@launch
            }
            val s = settingsRepo.flow.first()
            userStopped = false; attempt = 0; _attempt.value = 0
            resumeHandle = null; greeted = false; micOpen = true; discardAudio = false
            sentBytes.set(0); recvBytes.set(0)
            VoiceForegroundService.start(appContext, s.assistantName ?: "Assistant")
            setState(VoiceSessionState.CONNECTING)
            openSocket()
        }
    }

    fun endCall() { scope.launch { teardown(); setState(VoiceSessionState.IDLE) } }

    /** Stops local playback immediately. The server may still finish generating that turn; its audio is discarded. */
    fun stopSpeaking() {
        scope.launch {
            discardAudio = true
            audioOut.flush()
            if (_state.value == VoiceSessionState.SPEAKING) setState(VoiceSessionState.LISTENING)
        }
    }

    /** Non-continuous mode: reopen the mic for the next question. */
    fun tapToTalk() {
        scope.launch {
            if (_state.value == VoiceSessionState.PAUSED) { micOpen = true; setState(VoiceSessionState.LISTENING) }
        }
    }

    fun stopAll() { router.cancelPending(); endCall() }

    fun reportError(message: String) { scope.launch { fail(message) } }
    fun clearError() { scope.launch { _error.value = null; if (_state.value == VoiceSessionState.ERROR) setState(VoiceSessionState.IDLE) } }

    /** Opens a throw-away connection, sends a minimal setup and waits for setupComplete. */
    suspend fun testConnection(keyOverride: String?): String = withContext(Dispatchers.IO) {
        val key = (keyOverride?.takeIf { it.isNotBlank() } ?: apiKeys.get()).orEmpty().trim()
        if (key.isEmpty()) return@withContext "No API key entered."
        if (!isOnline()) return@withContext "No network connection."
        val model = settingsRepo.flow.first().model
        val done = CompletableDeferred<String>()
        val setup = JSONObject().put("setup", JSONObject()
            .put("model", "models/$model")
            .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("AUDIO")))).toString()
        val ws = http.newWebSocket(Request.Builder().url("$WS_BASE?key=$key").build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send(setup) }
            override fun onMessage(webSocket: WebSocket, text: String) = inspect(text)
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = inspect(bytes.utf8())
            private fun inspect(t: String) { if (t.contains("setupComplete")) done.complete("OK. Key accepted and model '$model' is available for Live sessions.") }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                done.complete(classify(code, reason.replace(key, "***"), null, model).second)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                done.complete(classify(null, (t.message ?: "").replace(key, "***"), response?.code, model).second)
            }
        })
        val result = withTimeoutOrNull(12_000) { done.await() } ?: "Timed out waiting for Gemini. Check your network."
        ws.close(1000, null)
        result
    }

    // ================= connection =================

    private suspend fun openSocket() {
        val key = apiKeys.get()
        if (key.isNullOrBlank()) { fail("No Gemini API key. Add one in Settings."); return }
        if (!isOnline()) { recoverable("No network connection."); return }
        val s = settingsRepo.flow.first()
        if (sessionModel != null && sessionModel != s.model) resumeHandle = null // a model cannot change on resume
        sessionName = s.assistantName; sessionModel = s.model; sessionLang = s.language
        cfgBargeIn = s.bargeIn; cfgContinuous = s.continuous
        router.assistantName = s.assistantName ?: "Assistant"
        pendingSetup = buildSetup(s)
        val id = ++socketId
        setupDone = false
        _diag.value = _diag.value.copy(model = s.model)
        socket = http.newWebSocket(Request.Builder().url("$WS_BASE?key=$key").build(), Listener(id, key, s.model))
    }

    private fun buildSetup(s: AppSettings): String {
        val name = s.assistantName ?: "Assistant"
        val setup = JSONObject()
            .put("model", "models/${s.model.removePrefix("models/")}")
            .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("AUDIO")))
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(
                JSONObject().put("text", AssistantInstructions.build(name, s.language)))))
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", ActionSchema.functionDeclarations())))
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
            .put("contextWindowCompression", JSONObject().put("slidingWindow", JSONObject()))
            .put("sessionResumption", JSONObject().also { r -> resumeHandle?.let { r.put("handle", it) } })
        return JSONObject().put("setup", setup).toString()
    }

    private inner class Listener(val id: Int, val key: String, val model: String) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            scope.launch { if (id == socketId) webSocket.send(pendingSetup) }
        }
        override fun onMessage(webSocket: WebSocket, text: String) = post(text)
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = post(bytes.utf8())
        private fun post(t: String) { scope.launch { if (id == socketId) handleServer(t) } }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            scope.launch { if (id == socketId) onGone(code, reason.replace(key, "***"), null, model) }
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            scope.launch { if (id == socketId) onGone(null, (t.message ?: t.javaClass.simpleName).replace(key, "***"), response?.code, model) }
        }
    }

    private fun onGone(code: Int?, reason: String, httpCode: Int?, model: String) {
        val hadSetup = setupDone
        ++socketId
        socket = null
        setupDone = false
        audioIn.stop()          // never leave the mic open while disconnected
        audioOut.flush()
        if (userStopped) return
        _diag.value = _diag.value.copy(lastCloseCode = code, lastCloseReason = reason)
        if (!hadSetup && resumeHandle != null) {
            resumeHandle = null
            recoverable("Previous session could not be resumed. Starting a new one.")
            return
        }
        val (canRetry, msg) = classify(code, reason, httpCode, model)
        if (canRetry) recoverable(msg) else fail(msg)
    }

    /** Maps close codes / HTTP codes / reasons to (recoverable?, actionable message). */
    private fun classify(code: Int?, reason: String, httpCode: Int?, model: String): Pair<Boolean, String> {
        val r = reason.lowercase()
        return when {
            httpCode == 429 || "quota" in r || "resource_exhausted" in r || "exhausted" in r ->
                false to "Gemini quota exhausted or billing limit reached. Check your plan in Google AI Studio, then try again."
            httpCode == 400 || httpCode == 401 || httpCode == 403 || "api key" in r || "permission" in r || "unauthenticated" in r ->
                false to "Gemini rejected the API key, or it has no Live API access. Re-enter the key in Settings."
            httpCode == 404 || "not found" in r || "not supported" in r ->
                false to "Model '$model' was not found or doesn't support Live sessions for this key. Change the model ID in Settings."
            code == 1007 || "invalid argument" in r ->
                false to "Gemini rejected the session configuration${if (reason.isNotBlank()) ": $reason" else "."}"
            else -> true to "Connection lost${if (reason.isNotBlank()) " ($reason)" else "."}"
        }
    }

    private fun recoverable(msg: String) {
        if (userStopped) return
        if (attempt >= MAX_ATTEMPTS) { fail("$msg Gave up after $MAX_ATTEMPTS reconnect attempts."); return }
        attempt++
        _attempt.value = attempt
        _diag.value = _diag.value.copy(reconnects = _diag.value.reconnects + 1, lastError = msg)
        setState(VoiceSessionState.RECONNECTING)
        val wait = minOf(16_000L, 1_000L shl (attempt - 1))
        reconnectJob?.cancel()
        reconnectJob = scope.launch { delay(wait); if (!userStopped) openSocket() }
    }

    /** Graceful re-open (goAway, rename, model/language change). Uses the resumption handle when possible. */
    private fun reconnectNow() {
        reconnectJob?.cancel()
        ++socketId
        socket?.close(1000, "reconfigure")
        socket = null
        setupDone = false
        audioIn.stop()
        audioOut.flush()
        attempt = 0; _attempt.value = 0
        setState(VoiceSessionState.RECONNECTING)
        reconnectJob = scope.launch { if (!userStopped) openSocket() }
    }

    private fun fail(msg: String) {
        teardown()
        _error.value = msg
        _diag.value = _diag.value.copy(lastError = msg)
        setState(VoiceSessionState.ERROR)
    }

    private fun teardown() {
        userStopped = true
        reconnectJob?.cancel(); drainJob?.cancel(); toolJob?.cancel()
        audioIn.stop()                 // mic is released before anything else can fail
        audioOut.stop(appContext)
        ++socketId
        socket?.close(1000, "client ended session")
        socket = null
        setupDone = false
        router.cancelPending()
        flushTranscripts()
        resumeHandle = null
        _callStart.value = null
        _attempt.value = 0
        VoiceForegroundService.stop(appContext)
    }

    // ================= server messages =================

    private fun handleServer(text: String) {
        val o = try { JSONObject(text) } catch (e: JSONException) { return }
        if (o.has("setupComplete")) { onSetupComplete(); return }
        o.optJSONObject("serverContent")?.let { handleContent(it) }
        o.optJSONObject("toolCall")?.let { handleToolCall(it) }
        if (o.has("toolCallCancellation")) { toolJob?.cancel(); router.cancelPending() }
        if (o.has("goAway")) reconnectNow()
        o.optJSONObject("sessionResumptionUpdate")?.let {
            val h = it.optString("newHandle")
            if (it.optBoolean("resumable", false) && h.isNotEmpty()) resumeHandle = h
        }
        o.optJSONObject("usageMetadata")?.let {
            _diag.value = _diag.value.copy(lastTokenCount = it.optInt("totalTokenCount", _diag.value.lastTokenCount))
        }
    }

    private fun onSetupComplete() {
        setupDone = true
        attempt = 0; _attempt.value = 0
        if (_callStart.value == null) _callStart.value = System.currentTimeMillis()
        if (!audioOut.start(appContext, scope)) { fail("Audio playback could not be started on this device."); return }
        val ok = audioIn.start(
            onChunk = { chunk, level -> sendAudio(chunk, level) },
            onError = { msg -> scope.launch { if (!userStopped) fail(msg) } },
        )
        if (!ok) { fail("Audio capture failure. Close other apps that use the microphone and try again."); return }
        setState(VoiceSessionState.LISTENING)
        if (!greeted) {
            greeted = true
            socket?.send(JSONObject().put("realtimeInput", JSONObject()
                .put("text", "The call just started. Greet me in one short sentence.")).toString())
        }
    }

    private fun sendAudio(chunk: ByteArray, level: Float) {
        val ws = socket ?: return
        if (!setupDone || !micOpen) return
        val st = _state.value
        if (st == VoiceSessionState.SPEAKING && !cfgBargeIn) return // half-duplex: never hear ourselves
        if (st == VoiceSessionState.LISTENING) _level.value = level
        val b64 = Base64.encodeToString(chunk, Base64.NO_WRAP)
        ws.send("{\"realtimeInput\":{\"audio\":{\"mimeType\":\"audio/pcm;rate=16000\",\"data\":\"$b64\"}}}")
        sentBytes.addAndGet(chunk.size.toLong())
    }

    private fun handleContent(sc: JSONObject) {
        if (sc.optBoolean("interrupted")) onInterrupted()

        sc.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let {
            userBuf.append(it); _liveUser.value = userBuf.toString()
        }
        sc.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let {
            if (!discardAudio) { asstBuf.append(it); _liveAssistant.value = asstBuf.toString() }
        }
        sc.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
            for (i in 0 until parts.length()) {
                val data = parts.optJSONObject(i)?.optJSONObject("inlineData")?.optString("data").orEmpty()
                if (data.isEmpty() || discardAudio) continue
                val pcm = Base64.decode(data, Base64.DEFAULT)
                recvBytes.addAndGet(pcm.size.toLong())
                if (_state.value != VoiceSessionState.SPEAKING && _state.value != VoiceSessionState.EXECUTING_ACTION) {
                    setState(VoiceSessionState.SPEAKING)
                }
                _level.value = PcmLevel.of(pcm)
                audioOut.enqueue(pcm)
            }
        }
        if (sc.optBoolean("turnComplete")) onTurnComplete()
    }

    private fun onInterrupted() {
        audioOut.flush()
        discardAudio = false
        drainJob?.cancel()
        flushTranscripts()
        setState(VoiceSessionState.INTERRUPTED)
        scope.launch { delay(250); if (_state.value == VoiceSessionState.INTERRUPTED) setState(VoiceSessionState.LISTENING) }
    }

    private fun onTurnComplete() {
        discardAudio = false
        flushTranscripts()
        _diag.value = _diag.value.copy(turns = _diag.value.turns + 1,
            audioKbSent = sentBytes.get() / 1024, audioKbReceived = recvBytes.get() / 1024)
        drainJob?.cancel()
        drainJob = scope.launch {
            while (!audioOut.isDrained()) delay(50)     // wait until the assistant has really finished talking
            val st = _state.value
            if (st == VoiceSessionState.SPEAKING || st == VoiceSessionState.PROCESSING) {
                if (cfgContinuous) {
                    setState(VoiceSessionState.LISTENING)
                } else {
                    micOpen = false
                    socket?.send("{\"realtimeInput\":{\"audioStreamEnd\":true}}")
                    setState(VoiceSessionState.PAUSED)
                }
            }
        }
    }

    private fun flushTranscripts() {
        val u = userBuf.toString().trim()
        val a = asstBuf.toString().trim()
        userBuf.clear(); asstBuf.clear()
        _liveUser.value = ""; _liveAssistant.value = ""
        if (u.isNotEmpty()) conversations.add(Role.USER, u)
        if (a.isNotEmpty()) conversations.add(Role.ASSISTANT, a)
    }

    // ================= tool calling =================

    private fun handleToolCall(tc: JSONObject) {
        val calls = tc.optJSONArray("functionCalls") ?: return
        toolJob?.cancel()
        toolJob = scope.launch {
            setState(VoiceSessionState.EXECUTING_ACTION)
            val responses = JSONArray()
            for (i in 0 until calls.length()) {
                val c = calls.getJSONObject(i)
                val id = c.optString("id")
                val name = c.optString("name")
                val result: ActionResult = when (val p = ActionParser.parse(name, c.optJSONObject("args") ?: JSONObject())) {
                    is ActionParser.ParseResult.Invalid -> ActionResult(ActionExecutionStatus.INVALID, p.reason)
                    is ActionParser.ParseResult.Valid -> when (p.action) {
                        PhoneAction.StopSpeaking -> {
                            discardAudio = true; audioOut.flush()
                            ActionResult(ActionExecutionStatus.COMPLETED, "Stopped speaking.")
                        }
                        else -> router.execute(p.action)
                    }
                }
                responses.put(JSONObject().put("id", id).put("name", name).put("response", JSONObject()
                    .put("success", result.success)
                    .put("status", result.status.name)
                    .put("message", result.message)))
            }
            socket?.send(JSONObject().put("toolResponse", JSONObject().put("functionResponses", responses)).toString())
            if (_state.value == VoiceSessionState.EXECUTING_ACTION) setState(VoiceSessionState.PROCESSING)
        }
    }

    // ================= helpers =================

    private fun setState(s: VoiceSessionState) {
        _state.value = s
        if (s != VoiceSessionState.LISTENING && s != VoiceSessionState.SPEAKING) _level.value = 0f
    }

    private fun isOnline(): Boolean {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
