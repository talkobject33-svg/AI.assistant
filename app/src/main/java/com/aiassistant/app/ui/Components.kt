package com.aiassistant.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.aiassistant.app.voice.VoiceSessionState
import com.aiassistant.app.voice.VoiceSessionState.*

fun VoiceSessionState.label() = when (this) {
    IDLE -> "Idle"
    CONNECTING -> "Connecting…"
    LISTENING -> "Listening"
    PROCESSING -> "Thinking…"
    EXECUTING_ACTION -> "Working on your phone…"
    SPEAKING -> "Speaking"
    INTERRUPTED -> "Interrupted"
    RECONNECTING -> "Reconnecting…"
    PAUSED -> "Tap to talk"
    ERROR -> "Error"
}

/** Only states where a Live session is genuinely open say "Connected". */
fun connectionLabel(s: VoiceSessionState) = when (s) {
    IDLE -> "Disconnected"
    CONNECTING -> "Connecting"
    RECONNECTING -> "Reconnecting"
    ERROR -> "Error"
    else -> "Connected"
}

fun phoneControlLabel(accessibilityOn: Boolean, paused: Boolean) = when {
    !accessibilityOn -> "Disabled (enable in Accessibility Setup)"
    paused -> "Paused"
    else -> "Enabled"
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Cyan)
            content()
        }
    }
}

@Composable
fun Bubble(who: String, text: String, mine: Boolean, partial: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.clip(RoundedCornerShape(14.dp))
                .background(if (mine) Color(0xFF123A66) else PanelHigh)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(who, style = MaterialTheme.typography.labelSmall, color = Muted)
            Text(
                text, style = MaterialTheme.typography.bodyMedium,
                fontStyle = if (partial) FontStyle.Italic else FontStyle.Normal,
            )
        }
    }
}

@Composable
fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF3A1215)).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, Modifier.weight(1f), color = Color(0xFFFFB4B4), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onDismiss) { Text("Dismiss") }
    }
}
