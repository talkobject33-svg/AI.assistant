package com.aiassistant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiassistant.app.memory.Greeting
import com.aiassistant.app.memory.Role
import com.aiassistant.app.voice.VoiceSessionState

@Composable
fun HomeScreen(
    vm: AssistantViewModel,
    name: String,
    onStart: () -> Unit,
    onOpenCall: () -> Unit,
    onSettings: () -> Unit,
    onAccessibility: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val level by vm.audioLevel.collectAsState()
    val msgs by vm.messages.collectAsState()
    val a11y by vm.accessibilityOn.collectAsState()
    val paused by vm.paused.collectAsState()
    val error by vm.error.collectAsState()

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(name.uppercase(), style = MaterialTheme.typography.titleLarge, color = Cyan)
            TextButton(onClick = onSettings) { Text("Settings") }
        }
        Spacer(Modifier.height(8.dp))
        Text("${Greeting.forNow()}. I'm $name.", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text("Gemini Live: ${connectionLabel(state)}", color = Muted)
        Text("Phone Control: ${phoneControlLabel(a11y, paused)}", color = Muted)
        if (!a11y) TextButton(onClick = onAccessibility) { Text("Set up phone control") }

        Spacer(Modifier.height(12.dp))
        VoiceOrb(state, level)
        SoundWave(
            active = state == VoiceSessionState.LISTENING || state == VoiceSessionState.SPEAKING,
            level = level, color = orbColor(state),
        )
        Text(state.label(), color = orbColor(state), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(12.dp))

        error?.let { ErrorBanner(it) { vm.clearError() }; Spacer(Modifier.height(8.dp)) }

        if (!state.isActive) {
            Button(onClick = onStart, Modifier.fillMaxWidth()) { Text("Start Conversation") }
        } else {
            Button(onClick = onOpenCall, Modifier.fillMaxWidth()) { Text("Open Conversation") }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.endCall() }, Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Danger),
            ) { Text("End Conversation") }
            if (state == VoiceSessionState.SPEAKING) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.stopSpeaking() }, Modifier.fillMaxWidth()) { Text("Stop Speaking") }
            }
        }

        Spacer(Modifier.height(16.dp))
        Section("Recent conversation") {
            if (msgs.isEmpty()) Text("Nothing yet.", color = Muted)
            msgs.takeLast(3).forEach {
                Text(
                    "${if (it.role == Role.USER) "You" else name}: ${it.text}", maxLines = 2,
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFCFE8F7),
                )
            }
        }
    }
}
