package com.aiassistant.app.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor

/**
 * Microphone capture: 16-bit PCM, 16 kHz, mono (the Live API's required input format), in 100 ms chunks.
 * Uses VOICE_COMMUNICATION + hardware AEC/NS when the device offers them, which is what keeps the assistant's own
 * speaker output from being heard as a new user command. Headphones remain the most reliable fix.
 */
class AudioInputManager {
    companion object {
        const val SAMPLE_RATE = 16000
        const val CHUNK_BYTES = 3200 // 100 ms
    }

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    @Volatile private var running = false

    val isRunning get() = running

    @SuppressLint("MissingPermission") // The caller verifies RECORD_AUDIO before starting a session.
    fun start(onChunk: (ByteArray, Float) -> Unit, onError: (String) -> Unit): Boolean {
        if (running) return true
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) { onError("This device does not support 16 kHz microphone capture."); return false }
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, CHUNK_BYTES * 4)
            )
        } catch (e: Exception) { onError("Could not open the microphone: ${e.javaClass.simpleName}."); return false }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release(); onError("Microphone could not be initialised. Another app may be using it."); return false
        }
        if (AcousticEchoCanceler.isAvailable()) aec = runCatching { AcousticEchoCanceler.create(rec.audioSessionId)?.also { it.enabled = true } }.getOrNull()
        if (NoiseSuppressor.isAvailable()) ns = runCatching { NoiseSuppressor.create(rec.audioSessionId)?.also { it.enabled = true } }.getOrNull()
        try { rec.startRecording() } catch (e: IllegalStateException) {
            rec.release(); onError("Microphone failed to start."); return false
        }
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            rec.release(); onError("Microphone is busy (possibly in a call)."); return false
        }
        record = rec
        running = true
        thread = Thread({
            val buf = ByteArray(CHUNK_BYTES)
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) {
                    val chunk = if (n == buf.size) buf.copyOf() else buf.copyOf(n)
                    onChunk(chunk, PcmLevel.of(chunk))
                } else if (n < 0) {
                    if (running) onError("Audio capture failed (error $n).")
                    break
                }
            }
        }, "audio-in").also { it.start() }
        return true
    }

    /** Synchronous: when this returns the microphone is released. */
    fun stop() {
        running = false
        runCatching { thread?.join(800) }
        thread = null
        record?.let { r ->
            runCatching { r.stop() }
            r.release()
        }
        record = null
        aec?.release(); aec = null
        ns?.release(); ns = null
    }
}
