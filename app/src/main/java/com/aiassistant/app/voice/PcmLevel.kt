package com.aiassistant.app.voice

import kotlin.math.sqrt

object PcmLevel {
    /** Rough 0..1 loudness of a 16-bit little-endian PCM buffer, for UI animation only. */
    fun of(b: ByteArray, len: Int = b.size): Float {
        val samples = len / 2
        if (samples == 0) return 0f
        var sum = 0.0
        var i = 0
        var n = 0
        while (i + 1 < len) {
            val s = ((b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xFF)).toShort().toInt()
            sum += s.toDouble() * s
            n++
            i += 8 // stride: every 4th sample is plenty for a visual indicator
        }
        val rms = sqrt(sum / n) / 32768.0
        return (rms * 6).coerceIn(0.0, 1.0).toFloat()
    }
}
