package com.aiassistant.app.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.aiassistant.app.ai.PhoneAction
import com.aiassistant.app.ai.describe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeoutOrNull

data class ConfirmationRequest(val message: String)

/**
 * The ONLY place where validated PhoneActions turn into real Android operations.
 * Flow: policy check -> (optional) user confirmation -> execute -> honest result.
 */
class PhoneActionRouter(
    context: Context,
    private val launcher: AppLauncher,
    private val policy: ActionPermissionManager,
    private val logger: AutomationLogger,
) {
    private val ctx = context.applicationContext

    /** Short live status line for the UI ("Opening YouTube…", then the final result). */
    val status = MutableStateFlow("")
    val pendingConfirmation = MutableStateFlow<ConfirmationRequest?>(null)

    @Volatile var assistantName: String = "Assistant"
    private var confirmation: CompletableDeferred<Boolean>? = null

    private fun service() = AccessibilityAutomationService.instance

    suspend fun execute(action: PhoneAction): ActionResult {
        val result = try {
            if (policy.paused.value && action != PhoneAction.StopControl) {
                ActionResult(ActionExecutionStatus.BLOCKED, "Phone control is paused. The user must resume it in the app.")
            } else {
                dispatch(action)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ActionResult(ActionExecutionStatus.FAILED, "Unexpected error (${e.javaClass.simpleName}).")
        }
        status.value = result.status.userText
        logger.log(action.describe(), result)
        return result
    }

    private suspend fun dispatch(a: PhoneAction): ActionResult = when (a) {
        is PhoneAction.OpenApp -> openApp(a)
        PhoneAction.Back -> global(AccessibilityService.GLOBAL_ACTION_BACK, "Going back…")
        PhoneAction.Home -> global(AccessibilityService.GLOBAL_ACTION_HOME, "Going to the home screen…")
        PhoneAction.Recents -> global(AccessibilityService.GLOBAL_ACTION_RECENTS, "Showing recent apps…")
        is PhoneAction.Scroll -> scroll(a)
        is PhoneAction.ClickElement -> click(a)
        is PhoneAction.TypeText -> type(a)
        PhoneAction.StopControl -> {
            policy.paused.value = true
            cancelPending()
            ActionResult(ActionExecutionStatus.COMPLETED, "Phone control paused. Say nothing more until the user resumes it.")
        }
        PhoneAction.StopSpeaking ->
            ActionResult(ActionExecutionStatus.UNSUPPORTED, "Handled by the voice engine, not the phone router.")
    }

    private fun disabled() = ActionResult(
        ActionExecutionStatus.SERVICE_DISABLED,
        "The accessibility service is disabled, so screen actions are unavailable. The user can enable it in the app's Accessibility Setup."
    )

    // ---------- OPEN APP ----------
    private suspend fun openApp(a: PhoneAction.OpenApp): ActionResult {
        status.value = "Opening ${a.appName}…"
        val resolved = launcher.resolve(a.appName)
        if (resolved is AppLauncher.Resolution.NotFound) {
            return ActionResult(ActionExecutionStatus.APP_NOT_INSTALLED, "No installed app matches '${a.appName}'.")
        }
        if (resolved is AppLauncher.Resolution.Ambiguous) {
            return ActionResult(ActionExecutionStatus.AMBIGUOUS, "Several apps match: ${resolved.options.joinToString { it.label }}. Ask which one.")
        }
        val app = (resolved as AppLauncher.Resolution.Found).app
        val svc = service()
        val ok = try { launcher.launch(svc ?: ctx, app.pkg) } catch (e: Exception) { false }
        if (!ok) return ActionResult(ActionExecutionStatus.FAILED, "Could not launch ${app.label}.")
        if (svc == null) {
            return ActionResult(ActionExecutionStatus.COMPLETED_UNVERIFIED,
                "Launch of ${app.label} was requested. Without the accessibility service I cannot verify it opened.")
        }
        repeat(15) {
            if (svc.rootInActiveWindow?.packageName?.toString() == app.pkg) {
                return ActionResult(ActionExecutionStatus.COMPLETED, "${app.label} is open.")
            }
            delay(200)
        }
        return ActionResult(ActionExecutionStatus.FAILED,
            "Asked Android to open ${app.label} but it did not come to the foreground. Android may block opening apps from the background; the user can bring this app to the front and try again.")
    }

    // ---------- GLOBAL ACTIONS ----------
    private fun global(code: Int, statusText: String): ActionResult {
        val svc = service() ?: return disabled()
        status.value = statusText
        return if (svc.performGlobalAction(code)) ActionResult(ActionExecutionStatus.COMPLETED, "Done.")
        else ActionResult(ActionExecutionStatus.FAILED, "Android rejected the action.")
    }

    // ---------- SCROLL ----------
    private suspend fun scroll(a: PhoneAction.Scroll): ActionResult {
        val svc = service() ?: return disabled()
        val root = svc.rootInActiveWindow
        if (policy.isBlockedPackage(root?.packageName?.toString())) return blockedScreen()
        status.value = "Scrolling ${a.direction.name.lowercase()}…"
        if (svc.gestures.scroll(a.direction, a.durationMs)) {
            return ActionResult(ActionExecutionStatus.COMPLETED, "Scrolled ${a.direction.name.lowercase()}.")
        }
        // Fallback: ask the largest scrollable view to scroll itself.
        val node = root?.let { ScreenNodeInspector.findScrollable(it) }
            ?: return ActionResult(ActionExecutionStatus.FAILED, "The scroll gesture was cancelled and no scrollable view was found.")
        val forward = a.direction == com.aiassistant.app.ai.Direction.DOWN || a.direction == com.aiassistant.app.ai.Direction.RIGHT
        val act = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return if (node.performAction(act)) ActionResult(ActionExecutionStatus.COMPLETED, "Scrolled.")
        else ActionResult(ActionExecutionStatus.FAILED, "Nothing further to scroll, or the view refused.")
    }

    private fun blockedScreen() = ActionResult(
        ActionExecutionStatus.BLOCKED, "A system permission or installer screen is showing. The user must handle it personally."
    )

    // ---------- CLICK ----------
    private suspend fun click(a: PhoneAction.ClickElement): ActionResult {
        val svc = service() ?: return disabled()
        status.value = "Searching accessible screen elements…"

        fun lookup(): Pair<ScreenNodeInspector.Match, String?> {
            val root = svc.rootInActiveWindow ?: return ScreenNodeInspector.Match.NotFound to null
            return ScreenNodeInspector.findClickable(root, a.targetText) to root.packageName?.toString()
        }

        var (match, pkg) = lookup()
        if (policy.isBlockedPackage(pkg)) return blockedScreen()
        when (match) {
            ScreenNodeInspector.Match.NotFound ->
                return ActionResult(ActionExecutionStatus.TARGET_NOT_FOUND, "No visible tappable element matches '${a.targetText}'.")
            is ScreenNodeInspector.Match.Ambiguous ->
                return ActionResult(ActionExecutionStatus.AMBIGUOUS, "Multiple elements match '${a.targetText}': ${match.labels.joinToString()}. Ask the user which one.")
            is ScreenNodeInspector.Match.Found -> Unit
        }
        val label = (match as ScreenNodeInspector.Match.Found).label

        when (val d = policy.evaluateClick(a.targetText, label)) {
            is ActionPermissionManager.Decision.Block -> return ActionResult(ActionExecutionStatus.BLOCKED, d.reason)
            is ActionPermissionManager.Decision.Confirm -> {
                if (!confirm("Tap \"$label\"? ${d.reason}")) {
                    return ActionResult(ActionExecutionStatus.CONFIRMATION_DENIED, "The user did not confirm.")
                }
                // The screen may have changed while waiting. Re-resolve before acting.
                val again = lookup().first
                if (again !is ScreenNodeInspector.Match.Found) {
                    return ActionResult(ActionExecutionStatus.TARGET_NOT_FOUND, "The screen changed while waiting for confirmation.")
                }
                match = again
            }
            ActionPermissionManager.Decision.Allow -> Unit
        }

        val node = (match as ScreenNodeInspector.Match.Found).node
        status.value = "Tapping $label…"
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK) || svc.gestures.tapNode(node)
        return if (ok) ActionResult(ActionExecutionStatus.COMPLETED, "Tapped '$label'.")
        else ActionResult(ActionExecutionStatus.FAILED, "The element refused the tap.")
    }

    // ---------- TYPE ----------
    private suspend fun type(a: PhoneAction.TypeText): ActionResult {
        val svc = service() ?: return disabled()
        val root = svc.rootInActiveWindow ?: return ActionResult(ActionExecutionStatus.FAILED, "No active window to inspect.")
        if (policy.isBlockedPackage(root.packageName?.toString())) return blockedScreen()
        status.value = "Typing…"
        val node = ScreenNodeInspector.findEditable(root, a.fieldLabel)
            ?: return ActionResult(ActionExecutionStatus.TARGET_NOT_FOUND, "No single, clearly identifiable text field. Ask the user to tap the field first.")
        if (node.isPassword) {
            return ActionResult(ActionExecutionStatus.BLOCKED, "Password fields are never filled by the assistant.")
        }
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val existing = if (node.isShowingHintText) "" else node.text?.toString().orEmpty()
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, existing + a.text)
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            return ActionResult(ActionExecutionStatus.FAILED, "The field did not accept text.")
        }
        node.refresh()
        return if (node.text?.toString()?.contains(a.text) == true)
            ActionResult(ActionExecutionStatus.COMPLETED, "Typed the text.")
        else ActionResult(ActionExecutionStatus.FAILED, "Tried to type, but the field does not show the text.")
    }

    // ---------- CONFIRMATION ----------
    private suspend fun confirm(message: String): Boolean {
        val d = CompletableDeferred<Boolean>()
        confirmation = d
        pendingConfirmation.value = ConfirmationRequest(message)
        status.value = "Waiting for your confirmation…"
        ConfirmationNotifier.show(ctx, assistantName, message)
        val answer = withTimeoutOrNull(30_000) { d.await() } ?: false
        pendingConfirmation.value = null
        confirmation = null
        ConfirmationNotifier.clear(ctx)
        return answer
    }

    fun respondConfirmation(approved: Boolean) { confirmation?.complete(approved) }
    fun cancelPending() { confirmation?.complete(false) }
}
