package org.librelab.dialer.ui

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.librelab.dialer.data.LastTabPreferences
import org.librelab.dialer.data.VoicemailAvailability
import org.librelab.dialer.data.calllog.MissedCallCountRepository
import org.librelab.dialer.data.voicemail.VoicemailRepository
import javax.inject.Inject

/**
 * Main screen tab state — matches crDroid's 4-tab bottom navigation.
 *
 *  SPEED_DIAL  — Favorites + dialpad (the dialpad is shown as overlay when FAB tapped)
 *  CALL_LOG    — Recent calls
 *  CONTACTS    — All contacts
 *  VOICEMAIL   — Voicemails (conditional)
 */
enum class MainTab { SPEED_DIAL, CALL_LOG, CONTACTS, VOICEMAIL }

data class MainUiState(
    val currentTab: MainTab = MainTab.SPEED_DIAL,
    val voicemailTabVisible: Boolean = false,
    val isSearchShown: Boolean = false,
    val searchQuery: String = "",
    val missedCallCount: Int = 0,
    val voicemailCount: Int = 0,
    val isDefaultDialer: Boolean = false,
    val showDefaultDialerBanner: Boolean = true,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lastTabPrefs: LastTabPreferences,
    private val voicemailAvailability: VoicemailAvailability,
    private val missedCallRepo: MissedCallCountRepository,
    private val voicemailRepo: VoicemailRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        refreshDefaultDialer()

        // Restore last tab on startup
        val lastTab = lastTabPrefs.getLastTab()
        _state.value = _state.value.copy(currentTab = lastTab)

        // Determine voicemail tab visibility
        viewModelScope.launch {
            val visible = voicemailAvailability.isVoicemailTabShown()
            _state.value = _state.value.copy(voicemailTabVisible = visible)

            if (visible) {
                val vms = voicemailRepo.getVoicemails(limit = 50)
                _state.value = _state.value.copy(voicemailCount = vms.count { !it.isRead })
            }
        }

        // Register missed-call observer
        missedCallRepo.register()

        // Subscribe to missed-call badge updates
        viewModelScope.launch {
            missedCallRepo.count.collect { count ->
                _state.value = _state.value.copy(missedCallCount = count)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        missedCallRepo.unregister()
    }

    /**
     * Switch to a tab — mirrors BottomNavBar.selectTab().
     */
    fun selectTab(tab: MainTab) {
        if (tab == MainTab.VOICEMAIL && !_state.value.voicemailTabVisible) return
        _state.value = _state.value.copy(currentTab = tab)
        lastTabPrefs.setLastTab(tab)
    }

    fun toggleSearch() {
        _state.value = _state.value.copy(
            isSearchShown = !_state.value.isSearchShown,
            searchQuery = if (!_state.value.isSearchShown) "" else _state.value.searchQuery,
        )
    }

    fun onSearchQueryChange(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun showSettings() {
        // Navigation handled by NavController in MainScreen — this method is
        // kept as a no-op to avoid breaking any external callers.
    }

    fun closeSettings() {
        // Navigation handled by NavController.popBackStack() in MainScreen.
    }

    /** Check whether this app currently holds the ROLE_DIALER (default phone app) role. */
    fun refreshDefaultDialer() {
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
        val isDefault = roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        _state.value = _state.value.copy(isDefaultDialer = isDefault)
    }

    /**
     * Open the system Default Apps settings where the user can select this app
     * as the default phone/dialer app. Works on all Android versions and ROMs.
     */
    fun createRequestDefaultDialerIntent(): android.content.Intent {
        // First try the role-request intent (Android 10+), but skip the
        // resolveActivity check — it fails on crDroid/MIUI even when the
        // intent is structurally valid.
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
        val roleIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            roleManager?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
        } else {
            null
        }
        if (roleIntent != null) {
            return roleIntent
        }
        // Fallback: open the system Default Apps settings page
        return android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    }

    /** Dismiss the default-dialer setup banner (user permanently closed it). */
    fun dismissDefaultDialerBanner() {
        _state.value = _state.value.copy(showDefaultDialerBanner = false)
    }
}