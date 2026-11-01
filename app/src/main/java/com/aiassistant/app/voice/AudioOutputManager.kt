package com.aiassistant.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Plays the Live API's output: 16-bit PCM, 24 kHz, mono. Supports instant flush for barge-in / stop speaking. */
class AudioOutputManager {
    companion object { const val SAMPLE_RATE = 24000 }

    private class Chunk(val data: ByteArray, val gen: Int)

    private var track: AudioTrack? = null
    private var job: Job? = null
    private var queue = Channel<Chunk>(Channel.UNLIMITED)
    @Volatile private var generation = 0
    @Volatile private var playUntil = 0L
    private var audioManager: AudioManager? = null
    private var previousMode = AudioManager.MODE_NORMAL

    fun start(context: Context, scope: CoroutineScope): Boolean {
        if (track != null) return true
        val min = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return false
        val t = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                )
                .setAudioFormat(
                    AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                )
                .setBufferSizeInBytes(maxOf(min, SAMPLE_RATE / 5 * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) { return false }
        if (t.state != AudioTrack.STATE_INITIALIZED) { t.release(); return false }

        routeToSpeakerIfNoHeadset(context)
        t.play()
        track = t
        queue = Channel(Channel.UNLIMITED)
        val q = queue
        job = scope.launch(Dispatchers.IO) {
            for (c in q) {
                if (c.gen != generation) continue // dropped by flush()
                t.write(c.data, 0, c.data.size)
            }
        }
        return true
    }

    fun enqueue(data: ByteArray) {
        val now = SystemClock.elapsedRealtime()
        playUntil = maxOf(playUntil, now) + data.size / 2 * 1000L / SAMPLE_RATE
        queue.trySend(Chunk(data, generation))
    }

    /** True when everything queued has (approximately) finished playing. */
    fun isDrained() = SystemClock.elapsedRealtime() >= playUntil + 150

    /** Drops all queued audio immediately. Used for barge-in and the Stop Speaking button. */
    fun flush() {
        generation++
        playUntil = 0
        track?.let { runCatching { it.pause(); it.flush(); it.play() } }
    }

    fun stop(context: Context) {
        job?.cancel(); job = null
        queue.close()
        track?.let { runCatching { it.pause(); it.flush() }; it.release() }
        track = null
        playUntil = 0
        restoreRouting()
    }

    @Suppress("DEPRECATION")
    private fun routeToSpeakerIfNoHeadset(context: Context) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        audioManager = am
        previousMode = am.mode
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        val headsetTypes = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        )
        val headset = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in headsetTypes }
        if (headset) return
        if (Build.VERSION.SDK_INT >= 31) {
            am.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                ?.let { am.setCommunicationDevice(it) }
        } else {
            am.isSpeakerphoneOn = true
        }
    }

    @Suppress("DEPRECATION")
    private fun restoreRouting() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice()
        else am.isSpeakerphoneOn = false
        am.mode = previousMode
        audioManager = null
    }
}
