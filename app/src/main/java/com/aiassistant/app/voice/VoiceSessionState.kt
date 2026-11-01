package com.aiassistant.app.voice

enum class VoiceSessionState {
    IDLE, CONNECTING, LISTENING, PROCESSING, EXECUTING_ACTION, SPEAKING,
    INTERRUPTED, RECONNECTING,
    /** Continuous mode is off: the assistant finished and the mic is closed until the user taps "talk". */
    PAUSED,
    ERROR;

    val isActive: Boolean get() = this != IDLE && this != ERROR
}
