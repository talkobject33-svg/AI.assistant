package com.aiassistant.app.automation

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aiassistant.app.AssistantApp
import com.aiassistant.app.MainActivity
import com.aiassistant.app.R

/** Shows Approve/Deny on a notification so the user can confirm even while another app is on screen. */
object ConfirmationNotifier {
    const val CHANNEL = "confirmations"
    const val ID = 42
    const val ACTION_APPROVE = "com.aiassistant.app.APPROVE"
    const val ACTION_DENY = "com.aiassistant.app.DENY"

    @SuppressLint("MissingPermission")
    fun show(ctx: Context, assistantName: String, message: String) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Action confirmations", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        fun pi(action: String, code: Int) = PendingIntent.getBroadcast(
            ctx, code, Intent(ctx, ConfirmationReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$assistantName needs your confirmation")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Approve", pi(ACTION_APPROVE, 1))
            .addAction(0, "Deny", pi(ACTION_DENY, 2))
            .build()
        nm.notify(ID, n)
    }

    fun clear(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID)
}

class ConfirmationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val approve = intent.action == ConfirmationNotifier.ACTION_APPROVE
        (context.applicationContext as AssistantApp).container.router.respondConfirmation(approve)
        ConfirmationNotifier.clear(context)
    }
}
