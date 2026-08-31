package org.librelab.dialer.ui.incall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.librelab.dialer.data.incall.CallManager
import org.librelab.dialer.postcall.PostCallManager
import org.librelab.dialer.domain.model.CallInfo
import org.librelab.dialer.domain.model.CallState
import javax.inject.Inject

/**
 * InCallViewModel — manages the in-call UI state.
 */
@HiltViewModel
class InCallViewModel @Inject constructor(
    private val callManager: CallManager,
    private val postCallManager: PostCallManager,
) : ViewModel() {

    val calls: StateFlow<Map<String, CallInfo>> = callManager.calls
    val audioState = callManager.audioState

    private val _showDialpad = MutableStateFlow(false)
    val showDialpad: StateFlow<Boolean> = _showDialpad.asStateFlow()

    private val _dialpadDigits = MutableStateFlow("")
    val dialpadDigits: StateFlow<String> = _dialpadDigits.asStateFlow()

    private val _showConferenceList = MutableStateFlow(false)
    val showConferenceList: StateFlow<Boolean> = _showConferenceList.asStateFlow()

    val foregroundCall: StateFlow<CallInfo?> = calls
        .map { it.values.firstOrNull { c -> c.state == CallState.RINGING || c.state == CallState.DIALING || c.state == CallState.ACTIVE } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val hasActiveCall: StateFlow<Boolean> = calls
        .map { it.values.any { c -> c.state == CallState.ACTIVE || c.state == CallState.ON_HOLD || c.state == CallState.DIALING || c.state == CallState.RINGING || c.state == CallState.CONNECTING } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Map of phone numbers to contact names (from reverse lookup).
     * Updated after each call disconnect.
     */
    private val _contactNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val contactNames: StateFlow<Map<String, String>> = _contactNames.asStateFlow()

    /**
     * Refresh contact name cache from CallManager.
     */
    fun refreshContactNames() {
        // Pull the current call's number → name from CallManager's cache
        val fg = foregroundCall.value
        if (fg != null) {
            val name = callManager.getContactName(fg.number)
            if (name != null) {
                _contactNames.value = _contactNames.value + (fg.number to name)
            }
        }
    }

    private val _postCallState = MutableStateFlow(PostCallManager.PostCallState(
        number = null,
        messageSent = false,
        shouldPromptSend = false,
        shouldPromptViewSent = false,
        timeSinceDisconnect = -1L,
        callDuration = -1L,
    ))
    val postCallState: StateFlow<PostCallManager.PostCallState> = _postCallState.asStateFlow()

    /**
     * Refresh post-call state from the manager.
     * Called after call disconnect to update the UI prompt.
     */
    fun refreshPostCallState() {
        _postCallState.value = postCallManager.getPostCallState()
    }

    /**
     * Dismiss the post-call prompt (user clicked Send or dismissed the snackbar).
     */
    fun dismissPostCall() {
        postCallManager.clear()
        _postCallState.value = PostCallManager.PostCallState(
            number = null,
            messageSent = false,
            shouldPromptSend = false,
            shouldPromptViewSent = false,
            timeSinceDisconnect = -1L,
            callDuration = -1L,
        )
    }

    fun toggleDialpad() {
        _showDialpad.value = !_showDialpad.value
    }

    fun onDialpadDigit(digit: Char) {
        _dialpadDigits.value += digit
        // Send DTMF through Call.sendDtmfTone()
        val call = foregroundCall.value ?: return
        callManager.getTelecomCall(call.id)?.let { telecomCall ->
            telecomCall.playDtmfTone(digit)
            viewModelScope.launch {
                kotlinx.coroutines.delay(160)
                telecomCall.stopDtmfTone()
            }
        }
    }

    fun clearDialpadDigits() {
        _dialpadDigits.value = ""
    }

    fun answerCall() {
        val call = foregroundCall.value ?: return
        callManager.answerCall(call.id)
    }

    fun rejectCall() {
        val call = foregroundCall.value ?: return
        callManager.rejectCall(call.id)
    }

    fun endCall() {
        postCallManager.onDisconnectPressed()
        val call = foregroundCall.value ?: return
        callManager.disconnect(call.id)
    }

    fun toggleHold() {
        val call = foregroundCall.value ?: return
        callManager.toggleHold(call.id)
    }

    fun toggleMute() {
        callManager.toggleMute()
    }

    fun setAudioRoute(route: Int) {
        callManager.setAudioRoute(route)
    }

    fun toggleConferenceList() {
        _showConferenceList.value = !_showConferenceList.value
    }
}
