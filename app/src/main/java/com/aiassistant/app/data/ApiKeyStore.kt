package com.aiassistant.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the user's own Gemini API key encrypted with an Android Keystore-backed master key.
 * The key is never logged and never compiled into the APK.
 */
class ApiKeyStore(context: Context) {
    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        val master = MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            appContext, "secure_keys", master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun get(): String? = runCatching { prefs.getString(KEY, null) }.getOrNull()?.takeIf { it.isNotBlank() }
    fun set(key: String) { prefs.edit().putString(KEY, key.trim()).apply() }
    fun clear() { prefs.edit().remove(KEY).apply() }

    fun masked(): String {
        val k = get() ?: return ""
        return if (k.length <= 8) "••••" else "${k.take(4)}••••••••${k.takeLast(4)}"
    }

    companion object {
        private const val KEY = "gemini_api_key"
        private val FORMAT = Regex("^[A-Za-z0-9_\\-]{20,128}$")
        /** Local sanity check only. The real validation is the connection test. */
        fun looksValid(raw: String) = FORMAT.matches(raw.trim())
    }
}
