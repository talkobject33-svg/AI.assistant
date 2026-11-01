package com.aiassistant.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun AssistantRoot(vm: AssistantViewModel) {
    val loaded by vm.settings.collectAsState()
    val settings = loaded
    var screen by rememberSaveable { mutableStateOf("home") }
    val confirmation by vm.confirmation.collectAsState()

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        vm.refreshPermissions()
        if (res[Manifest.permission.RECORD_AUDIO] == true) { vm.startSession(); screen = "conversation" }
        else vm.onMicDenied()
    }

    /** Permissions are requested only when the user actually starts a conversation. */
    val startConversation: () -> Unit = {
        if (vm.hasMic()) {
            vm.startSession(); screen = "conversation"
        } else {
            val wanted = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permLauncher.launch(wanted.toTypedArray())
        }
    }
    val requestMicOnly: () -> Unit = { permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) }

    BackHandler(enabled = screen != "home" && settings?.assistantName != null) {
        screen = if (screen == "name") "settings" else "home"
    }

    if (settings == null) {
        Box(Modifier.fillMaxSize().background(Navy))
    } else if (settings.assistantName == null) {
        AssistantNameScreen(initial = "", firstLaunch = true, onSave = { vm.saveName(it) }, onCancel = null)
    } else {
        val name = settings.assistantName
        when (screen) {
            "conversation" -> ConversationScreen(vm, name, onBack = { screen = "home" }, onStart = startConversation)
            "settings" -> SettingsScreen(
                vm, settings, onBack = { screen = "home" }, onChangeName = { screen = "name" },
                onAccessibility = { screen = "accessibility" }, onRequestMic = requestMicOnly,
            )
            "accessibility" -> AccessibilitySetupScreen(vm, onBack = { screen = "settings" })
            "name" -> AssistantNameScreen(
                initial = name, firstLaunch = false,
                onSave = { raw -> vm.saveName(raw).also { err -> if (err == null) screen = "settings" } },
                onCancel = { screen = "settings" },
            )
            else -> HomeScreen(
                vm, name, onStart = startConversation, onOpenCall = { screen = "conversation" },
                onSettings = { screen = "settings" }, onAccessibility = { screen = "accessibility" },
            )
        }
    }

    confirmation?.let { req ->
        AlertDialog(
            onDismissRequest = { vm.respondConfirmation(false) },
            title = { Text("Confirm action") },
            text = { Text(req.message) },
            confirmButton = { TextButton(onClick = { vm.respondConfirmation(true) }) { Text("Approve") } },
            dismissButton = { TextButton(onClick = { vm.respondConfirmation(false) }) { Text("Deny") } },
        )
    }
}
