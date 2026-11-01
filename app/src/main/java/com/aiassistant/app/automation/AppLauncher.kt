package com.aiassistant.app.automation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** Resolves spoken app names against what is actually installed on this device. */
class AppLauncher(context: Context) {
    private val appContext = context.applicationContext

    data class AppInfo(val label: String, val pkg: String)

    sealed interface Resolution {
        data class Found(val app: AppInfo) : Resolution
        data class Ambiguous(val options: List<AppInfo>) : Resolution
        data object NotFound : Resolution
    }

    /** Needs the <queries> MAIN/LAUNCHER entry in the manifest (Android 11+ package visibility). */
    fun installedApps(): List<AppInfo> {
        val pm = appContext.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
        }
        return infos.map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }.distinctBy { it.pkg }
    }

    fun resolve(spoken: String): Resolution {
        val q = normalizeQuery(spoken)
        if (q.isEmpty()) return Resolution.NotFound
        val scored = installedApps().mapNotNull { app ->
            val s = score(compact(app.label), q)
            if (s > 0) s to app else null
        }
        if (scored.isEmpty()) return Resolution.NotFound
        val top = scored.maxOf { it.first }
        val best = scored.filter { it.first == top }.map { it.second }
        return if (best.size == 1) Resolution.Found(best[0]) else Resolution.Ambiguous(best.take(5))
    }

    /** Returns true if a launch intent was dispatched. Does NOT prove the app reached the foreground. */
    fun launch(from: Context, pkg: String): Boolean {
        val intent = appContext.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        from.startActivity(intent)
        return true
    }

    private val filler = setOf("the", "app", "application", "browser", "please", "open", "launch", "start")

    private fun compact(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun normalizeQuery(spoken: String): String {
        val tokens = spoken.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        val kept = tokens.filter { it !in filler }
        return (if (kept.isEmpty()) tokens else kept).joinToString("")
    }

    private fun score(label: String, q: String): Int = when {
        label.isEmpty() -> 0
        label == q -> 100
        label.length >= 4 && (label.startsWith(q) || q.startsWith(label)) -> 80
        q.length >= 3 && label.contains(q) -> 60
        label.length >= 5 && levenshtein(label, q) <= 1 -> 50
        else -> 0
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]; dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }
}
