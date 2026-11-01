package com.aiassistant.app.automation

import kotlinx.coroutines.flow.MutableStateFlow

/** Local safety policy. This decides independently of the AI whether an action may run, needs the user's
 *  explicit approval, or is refused outright. */
class ActionPermissionManager {
    /** True after the user says "stop controlling the phone" (or taps pause). Blocks all further actions. */
    val paused = MutableStateFlow(false)

    sealed interface Decision {
        data object Allow : Decision
        data class Confirm(val reason: String) : Decision
        data class Block(val reason: String) : Decision
    }

    private val blockedPackages = setOf(
        "com.google.android.permissioncontroller", "com.android.permissioncontroller",
        "com.android.packageinstaller", "com.google.android.packageinstaller",
    )

    private val blockedWords = Regex(
        "\\b(allow|deny|while using the app|only this time|i agree|captcha|not a robot|verify you are human|i am human)\\b"
    )

    private val sensitiveWords = Regex(
        "\\b(send|post|publish|tweet|share|buy|purchase|pay|checkout|order|delete|remove|erase|uninstall|install|reset|" +
            "transfer|call|dial|submit|confirm|subscribe|sign out|log out|logout|format)\\b"
    )

    fun isBlockedPackage(pkg: String?) = pkg != null && pkg in blockedPackages

    fun evaluateClick(requested: String, actualLabel: String): Decision {
        val text = "$requested $actualLabel".lowercase()
        if (blockedWords.containsMatchIn(text)) {
            return Decision.Block("Permission, consent and human-verification prompts must be handled by the user.")
        }
        if (sensitiveWords.containsMatchIn(text)) {
            return Decision.Confirm("This looks like a sensitive or irreversible action.")
        }
        return Decision.Allow
    }
}
