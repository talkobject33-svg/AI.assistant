package com.aiassistant.app.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiassistant.app.memory.AssistantNameValidator
import com.aiassistant.app.voice.VoiceSessionState

@Composable
fun AssistantNameScreen(
    initial: String,
    firstLaunch: Boolean,
    onSave: (String) -> String?,   // returns an error message or null on success
    onCancel: (() -> Unit)?,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    fun submit() { error = onSave(text) }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        VoiceOrb(VoiceSessionState.CONNECTING, 0f, size = 160.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            if (firstLaunch) "Create Your AI Assistant" else "Change Assistant Name",
            style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
        )
        Text("Give your personal AI assistant a name.", color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(40); error = null },
            placeholder = { Text("Enter assistant name") },
            singleLine = true,
            isError = error != null,
            supportingText = { Text(error ?: "${text.trim().length}/${AssistantNameValidator.MAX_LENGTH}") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("JARVIS", "YESMIN", "FRIDAY", "ALEX", "MYRA").forEach { n ->
                AssistChip(onClick = { text = n; error = null }, label = { Text(n) })
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = { submit() }, Modifier.fillMaxWidth()) {
            Text(if (firstLaunch) "Create Assistant" else "Change Name")
        }
        if (onCancel != null) TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

