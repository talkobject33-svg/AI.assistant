package com.aiassistant.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.aiassistant.app.ui.AssistantRoot
import com.aiassistant.app.ui.AssistantTheme
import com.aiassistant.app.ui.AssistantViewModel

class MainActivity : ComponentActivity() {
    private val vm: AssistantViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AssistantTheme { AssistantRoot(vm) } }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPermissions() // re-check mic/notification/accessibility after returning from system settings
    }
}
