package com.aiassistant.app.automation

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Thin service shell. It does nothing on its own: it only exposes the capabilities (global actions, gestures,
 * window root) that PhoneActionRouter uses AFTER validating and authorising a request. It does not observe,
 * store or transmit screen content.
 */
class AccessibilityAutomationService : AccessibilityService() {
    lateinit var gestures: GestureController
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        gestures = GestureController(this)
        instance = this
        connected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* intentionally unused */ }
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        connected.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        connected.value = false
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: AccessibilityAutomationService? = null
        val connected = MutableStateFlow(false)
    }
}
