package com.aiassistant.app

import android.app.Application
import android.content.Context
import com.aiassistant.app.automation.ActionPermissionManager
import com.aiassistant.app.automation.AppLauncher
import com.aiassistant.app.automation.AutomationLogger
import com.aiassistant.app.automation.PhoneActionRouter
import com.aiassistant.app.data.ApiKeyStore
import com.aiassistant.app.data.SettingsRepository
import com.aiassistant.app.memory.LocalConversationRepository
import com.aiassistant.app.permissions.PermissionManager
import com.aiassistant.app.voice.GeminiLiveManager

class AssistantApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual dependency container. Lives as long as the process, so a voice session survives Activity recreation. */
class AppContainer(context: Context) {
    private val app = context.applicationContext
    val settings = SettingsRepository(app)
    val apiKeys = ApiKeyStore(app)
    val conversations = LocalConversationRepository(app, settings)
    val logger = AutomationLogger()
    val permissions = PermissionManager(app)
    val actionPermissions = ActionPermissionManager()
    val launcher = AppLauncher(app)
    val router = PhoneActionRouter(app, launcher, actionPermissions, logger)
    val live = GeminiLiveManager(app, settings, apiKeys, router, conversations)
}
