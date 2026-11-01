package com.aiassistant.app.voice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.aiassistant.app.AssistantApp
import com.aiassistant.app.MainActivity
import com.aiassistant.app.R

/** Keeps the process alive and shows a visible notification (with a Stop button) while the microphone is in use. */
class VoiceForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            (application as AssistantApp).container.live.endCall()
            stopSelf()
            return START_NOT_STICKY
        }
        val name = intent?.getStringExtra(EXTRA_NAME) ?: "Assistant"
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Voice session", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stop = PendingIntent.getService(
            this, 1, Intent(this, VoiceForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$name is listening")
            .setContentText("Microphone is active. Tap Stop to end.")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        try {
            ServiceCompat.startForeground(this, ID, n, type)
        } catch (e: Exception) {
            stopSelf() // The session continues while the app is on screen; it just cannot run in the background.
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "voice_session"
        private const val ID = 7
        private const val ACTION_STOP = "com.aiassistant.app.STOP_SESSION"
        private const val EXTRA_NAME = "name"

        fun start(ctx: Context, name: String) {
            runCatching {
                ContextCompat.startForegroundService(ctx, Intent(ctx, VoiceForegroundService::class.java).putExtra(EXTRA_NAME, name))
            }
        }

        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, VoiceForegroundService::class.java)) }
        }
    }
}
