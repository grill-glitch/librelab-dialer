package org.librelab.dialer.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import org.librelab.dialer.ui.MainTab
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LastTabPreferences — persists which tab was last selected.
 * Migrated from LastTabController.java.
 */
@Singleton
class LastTabPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLastTab(): MainTab {
        val name = prefs.getString(KEY_LAST_TAB, MainTab.SPEED_DIAL.name) ?: MainTab.SPEED_DIAL.name
        return runCatching { MainTab.valueOf(name) }.getOrDefault(MainTab.SPEED_DIAL)
    }

    fun setLastTab(tab: MainTab) {
        prefs.edit().putString(KEY_LAST_TAB, tab.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "librelab_dialer_prefs"
        private const val KEY_LAST_TAB = "last_tab"
    }
}