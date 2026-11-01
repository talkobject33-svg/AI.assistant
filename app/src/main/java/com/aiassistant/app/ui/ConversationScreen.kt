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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aiassistant.app.memory.Role
import com.aiassistant.app.voice.VoiceSessionState
import kotlinx.coroutines.delay

@Composable
fun ConversationScreen(vm: AssistantViewModel, name: String, onBack: () -> Unit, onStart: () -> Unit) {
    val state by vm.state.collectAsState()
    val msgs by vm.messages.collectAsState()
    val liveU by vm.liveUser.collectAsState()
    val liveA by vm.liveAssistant.collectAsState()
    val level by vm.audioLevel.collectAsState()
    val start by vm.callStart.collectAsState()
    val attempt by vm.reconnectAttempt.collectAsState()
    val actionStatus by vm.actionStatus.collectAsState()
    val error by vm.error.collectAsState()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(start) { while (start != null) { now = System.currentTimeMillis(); delay(1000) } }
    val secs = start?.let { ((now - it) / 1000).coerceAtLeast(0) } ?: 0
    val duration = "%02d:%02d".format(secs / 60, secs % 60)

    val listState = rememberLazyListState()
    val extra = (if (liveU.isNotEmpty()) 1 else 0) + (if (liveA.isNotEmpty()) 1 else 0)
    LaunchedEffect(msgs.size, liveU, liveA) {
        val total = msgs.size + extra
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text(name.uppercase(), style = MaterialTheme.typography.titleMedium, color = Cyan)
        }
        VoiceOrb(state, level, size = 130.dp)
        SoundWave(
            active = state == VoiceSessionState.LISTENING || state == VoiceSessionState.SPEAKING,
            level = level, color = orbColor(state),
        )
        Text(
            "${state.label()}${if (start != null) "  ·  $duration" else ""}",
            color = orbColor(state), style = MaterialTheme.typography.titleSmall,
        )
        if (state == VoiceSessionState.RECONNECTING) Text("Reconnecting, attempt $attempt of 5", color = Amber)
        if (actionStatus.isNotEmpty() && state.isActive) Text(actionStatus, color = Muted, style = MaterialTheme.typography.bodySmall)
        error?.let { Spacer(Modifier.height(6.dp)); ErrorBanner(it) { vm.clearError() } }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
            state = listState, verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(msgs) { Bubble(if (it.role == Role.USER) "You" else name, it.text, it.role == Role.USER) }
            if (liveU.isNotEmpty()) item { Bubble("You", liveU, mine = true, partial = true) }
            if (liveA.isNotEmpty()) item { Bubble(name, liveA, mine = false, partial = true) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!state.isActive) {
                Button(onClick = onStart, Modifier.weight(1f)) { Text("Start Call") }
            } else {
                OutlinedButton(
                    onClick = { vm.stopSpeaking() }, Modifier.weight(1f),
                    enabled = state == VoiceSessionState.SPEAKING,
                ) { Text("Interrupt") }
                if (state == VoiceSessionState.PAUSED) {
                    Button(onClick = { vm.tapToTalk() }, Modifier.weight(1f)) { Text("Talk") }
                }
                Button(
                    onClick = { vm.endCall() }, Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Danger),
                ) { Text("End Call") }
            }
        }
    }
}
