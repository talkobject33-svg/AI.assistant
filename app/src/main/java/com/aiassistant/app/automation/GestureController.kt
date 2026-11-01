package com.aiassistant.app.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.aiassistant.app.ai.Direction
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Wraps AccessibilityService.dispatchGesture. Every coordinate is derived from, and bounded by, the real display size. */
class GestureController(private val service: AccessibilityService) {

    private suspend fun dispatch(path: Path, durationMs: Long): Boolean = suspendCancellableCoroutine { cont ->
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(50, 3000)))
            .build()
        val accepted = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { if (cont.isActive) cont.resume(true) }
            override fun onCancelled(g: GestureDescription?) { if (cont.isActive) cont.resume(false) }
        }, null)
        if (!accepted && cont.isActive) cont.resume(false)
    }

    private fun inBounds(x: Float, y: Float): Boolean {
        val dm = service.resources.displayMetrics
        return x in 0f..dm.widthPixels.toFloat() && y in 0f..dm.heightPixels.toFloat()
    }

    suspend fun tap(x: Float, y: Float): Boolean {
        if (!inBounds(x, y)) return false
        return dispatch(Path().apply { moveTo(x, y) }, 60)
    }

    /** Taps the centre of a node only if its bounds are fully on screen. */
    suspend fun tapNode(node: AccessibilityNodeInfo): Boolean {
        val r = Rect()
        node.getBoundsInScreen(r)
        val dm = service.resources.displayMetrics
        if (r.isEmpty || r.left < 0 || r.top < 0 || r.right > dm.widthPixels || r.bottom > dm.heightPixels) return false
        return tap(r.exactCenterX(), r.exactCenterY())
    }

    /** DOWN/RIGHT means "reveal content further down/right", so the finger travels up/left. */
    suspend fun scroll(direction: Direction, durationMs: Long): Boolean {
        val dm = service.resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val (sx, sy, ex, ey) = when (direction) {
            Direction.DOWN -> listOf(w * 0.5f, h * 0.70f, w * 0.5f, h * 0.30f)
            Direction.UP -> listOf(w * 0.5f, h * 0.30f, w * 0.5f, h * 0.70f)
            Direction.RIGHT -> listOf(w * 0.80f, h * 0.5f, w * 0.20f, h * 0.5f)
            Direction.LEFT -> listOf(w * 0.20f, h * 0.5f, w * 0.80f, h * 0.5f)
        }
        if (!inBounds(sx, sy) || !inBounds(ex, ey)) return false
        return dispatch(Path().apply { moveTo(sx, sy); lineTo(ex, ey) }, durationMs)
    }
}
