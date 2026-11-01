package com.aiassistant.app.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.aiassistant.app.automation.AccessibilityAutomationService

class PermissionManager(context: Context) {
    private val ctx = context.applicationContext

    fun hasMic() =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun hasNotifications() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** True only while the system has actually bound our accessibility service. */
    fun isAccessibilityEnabled() = AccessibilityAutomationService.connected.value

    fun openAccessibilitySettings() = launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openAppSettings() = launch(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
    )

    private fun launch(i: Intent) {
        runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
