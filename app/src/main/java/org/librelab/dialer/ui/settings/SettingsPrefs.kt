package org.librelab.dialer.ui.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Typed wrapper around the app's SharedPreferences for settings persistence.
 * Used by Hilt DI (AppModule) and the Settings ViewModel.
 */
class SettingsPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("librelab_settings", Context.MODE_PRIVATE)

    fun getString(key: String, default: String? = null): String? =
        prefs.getString(key, default)

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        prefs.getBoolean(key, default)

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getInt(key: String, default: Int = 0): Int =
        prefs.getInt(key, default)

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }
}
