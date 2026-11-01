package com.aiassistant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AccessibilitySetupScreen(vm: AssistantViewModel, onBack: () -> Unit) {
    val on by vm.accessibilityOn.collectAsState()
    val paused by vm.paused.collectAsState()
    val status by vm.actionStatus.collectAsState()
    val log by vm.automationLog.collectAsState()

    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("Phone Control Setup", style = MaterialTheme.typography.titleLarge)
        }
        Section("Status") {
            Text(
                if (on) "Accessibility service: ENABLED" else "Accessibility service: DISABLED",
                color = if (on) Cyan else Amber, style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (on) (if (paused) "Phone control is paused by you." else "Voice commands can control the screen.")
                else "Chat and voice work normally, but screen actions (tap, scroll, type, back, home) are unavailable.",
                color = Muted,
            )
        }
        Section("What this service does") {
            Text(
                "Android only lets an app tap, scroll or type for you through an Accessibility Service. " +
                    "When enabled, the assistant uses it only to carry out actions you ask for by voice: tap a labelled button, " +
                    "scroll, type into a field, or press Back, Home or Recents. It does not read screens in the background and " +
                    "does not store screen content. It never fills password fields, solves CAPTCHAs or taps permission dialogs, " +
                    "and it asks you to confirm anything like sending, paying or deleting.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("Only you can turn it on: Android requires you to enable it yourself in system settings.", color = Muted)
        }
        Section("Controls") {
            Button(onClick = { vm.openAccessibilitySettings() }, Modifier.fillMaxWidth()) { Text("Open Accessibility Settings") }
            OutlinedButton(onClick = { vm.runAccessibilityTest() }, Modifier.fillMaxWidth(), enabled = on && !paused) {
                Text("Test action (go to Home screen)")
            }
            if (status.isNotEmpty()) Text("Last result: $status", color = Muted)
            OutlinedButton(onClick = { vm.setControlPaused(!paused) }, Modifier.fillMaxWidth(), enabled = on) {
                Text(if (paused) "Resume phone control" else "Pause phone control")
            }
            OutlinedButton(onClick = { vm.disableAccessibilityService() }, Modifier.fillMaxWidth(), enabled = on) {
                Text("Turn off the service")
            }
        }
        Section("Recent actions") {
            if (log.isEmpty()) Text("None yet.", color = Muted)
            log.takeLast(6).reversed().forEach {
                Text("${it.action}: ${it.status.userText}", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }
}
