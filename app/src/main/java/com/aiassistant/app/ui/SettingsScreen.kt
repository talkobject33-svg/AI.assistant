package com.aiassistant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aiassistant.app.data.AppSettings
import com.aiassistant.app.data.VoiceLanguage

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SettingsScreen(
    vm: AssistantViewModel,
    settings: AppSettings,
    onBack: () -> Unit,
    onChangeName: () -> Unit,
    onAccessibility: () -> Unit,
    onRequestMic: () -> Unit,
) {
    val perm by vm.permissions.collectAsState()
    val a11y by vm.accessibilityOn.collectAsState()
    val diag by vm.diagnostics.collectAsState()
    val test by vm.testResult.collectAsState()
    val masked by vm.maskedKey.collectAsState()

    var keyDraft by rememberSaveable { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var keyMsg by remember { mutableStateOf<String?>(null) }
    var modelDraft by rememberSaveable(settings.model) { mutableStateOf(settings.model) }
    var modelMsg by remember { mutableStateOf<String?>(null) }
    var cleared by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        Section("Assistant") {
            Text("Name: ${settings.assistantName}")
            OutlinedButton(onClick = onChangeName, Modifier.fillMaxWidth()) { Text("Change Name") }
        }

        Section("AI provider") {
            Text("Google Gemini (Live API)")
            Text("Active model: ${settings.model}", color = Muted)
            OutlinedTextField(
                value = modelDraft, onValueChange = { modelDraft = it; modelMsg = null },
                label = { Text("Model ID") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(modelMsg ?: "") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { modelMsg = vm.saveModel(modelDraft) ?: "Saved. Active sessions restart with it." }) { Text("Save model") }
                OutlinedButton(onClick = { vm.resetModel(); modelMsg = "Reset to default." }) { Text("Reset") }
            }
            Text(
                "Default is ${AppSettings.DEFAULT_MODEL}. Preview model names can change or be unavailable for your key; " +
                    "use Test connection to check.",
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
        }

        Section("Gemini API key") {
            Text(if (masked.isEmpty()) "No key saved" else "Saved key: $masked", color = Muted)
            OutlinedTextField(
                value = keyDraft, onValueChange = { keyDraft = it.trim(); keyMsg = null },
                label = { Text("Paste API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                supportingText = { Text(keyMsg ?: "") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    keyMsg = vm.saveApiKey(keyDraft) ?: "Saved securely on this device."
                    if (keyMsg?.startsWith("Saved") == true) keyDraft = ""
                }) { Text("Save key") }
                OutlinedButton(onClick = { vm.clearApiKey(); keyMsg = "Key removed." }) { Text("Remove") }
                TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") }
            }
            OutlinedButton(onClick = { vm.testConnection(keyDraft.ifBlank { null }) }, Modifier.fillMaxWidth()) { Text("Test connection") }
            test?.let { Text(it, color = if (it.startsWith("OK")) Cyan else Amber) }
            Text(
                "This is a development setup: you paste your own key and it is stored encrypted on this phone. " +
                    "A key embedded in an app that you distribute can be extracted by anyone. For a public release, have your " +
                    "own backend mint short-lived Gemini ephemeral tokens for each session instead. A key does not mean free " +
                    "or unlimited use: quotas and billing apply.",
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
        }

        Section("Voice") {
            Text("Language")
            VoiceLanguage.entries.forEach { l ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = settings.language == l, role = Role.RadioButton) { vm.setLanguage(l) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.language == l, onClick = null)
                    Text(l.label, Modifier.padding(start = 8.dp))
                }
            }
            SwitchRow("Continuous conversation", "Keep listening after each answer", settings.continuous) { vm.setContinuous(it) }
            SwitchRow(
                "Allow interrupting (barge-in)",
                "Talk over the assistant to stop it. Turn off if it hears itself on speaker.",
                settings.bargeIn,
            ) { vm.setBargeIn(it) }
        }

        Section("Permissions") {
            Text("Microphone: ${if (perm.mic) "granted" else "not granted"}")
            if (!perm.mic) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRequestMic) { Text("Grant") }
                OutlinedButton(onClick = { vm.openAppSettings() }) { Text("App settings") }
            }
            Text("Notifications: ${if (perm.notifications) "granted" else "not granted"} (used for the call notification and confirmations)")
            Text("Accessibility (phone control): ${if (a11y) "enabled" else "disabled"}")
            OutlinedButton(onClick = onAccessibility, Modifier.fillMaxWidth()) { Text("Phone control setup") }
        }

        Section("Diagnostics") {
            val d = diag
            Text("Model: ${d.model.ifEmpty { "-" }}", style = MaterialTheme.typography.bodySmall, color = Muted)
            Text("Turns: ${d.turns}   Reconnects: ${d.reconnects}", style = MaterialTheme.typography.bodySmall, color = Muted)
            Text("Audio sent/received: ${d.audioKbSent} / ${d.audioKbReceived} KB", style = MaterialTheme.typography.bodySmall, color = Muted)
            Text("Last reported token count: ${d.lastTokenCount}", style = MaterialTheme.typography.bodySmall, color = Muted)
            Text("Last close: ${d.lastCloseCode ?: "-"} ${d.lastCloseReason}", style = MaterialTheme.typography.bodySmall, color = Muted)
            if (d.lastError.isNotEmpty()) Text("Last error: ${d.lastError}", style = MaterialTheme.typography.bodySmall, color = Amber)
        }

        Section("Privacy & data") {
            SwitchRow("Save conversation history", "Stored only on this phone", settings.saveHistory) { vm.setSaveHistory(it) }
            Text("Stored transcript: ${vm.storedHistoryBytes() / 1024} KB", color = Muted, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { vm.clearHistory(); cleared = true }, Modifier.fillMaxWidth()) {
                Text(if (cleared) "History cleared" else "Clear conversation history")
            }
            Text(
                "During a call your microphone audio is streamed to Google's Gemini service to generate replies. " +
                    "Nothing else (contacts, files, screen content) is sent unless you ask for something that needs it. " +
                    "This app keeps only your assistant name, settings, an encrypted API key and the optional transcript. " +
                    "There is no long-term memory feature yet.",
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
            Button(
                onClick = { vm.stopAll() }, Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Danger),
            ) { Text("Stop all assistant activity") }
        }
    }
}
