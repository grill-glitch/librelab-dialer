package org.librelab.dialer.data.incall

import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.PhoneAccountHandle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.librelab.dialer.domain.model.CallInfo
import org.librelab.dialer.domain.model.CallState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CallManager — central singleton holding all active calls.
 * Mirrors CallList.java from the original incallui package.
 *
 * The system InCallService (Java) writes to this manager via updateCalls().
 * The Compose UI observes the StateFlows to render the in-call screen.
 */
@Singleton
class CallManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _calls = MutableStateFlow<Map<String, CallInfo>>(emptyMap())
    val calls: StateFlow<Map<String, CallInfo>> = _calls.asStateFlow()

    private val _audioState = MutableStateFlow<CallAudioState?>(null)
    val audioState: StateFlow<CallAudioState?> = _audioState.asStateFlow()

    // Contact name cache (number → name) for reverse lookup
    private val contactNameCache = mutableMapOf<String, String>()

    // InCallService instance — set by InCallServiceImpl once Telecom binds to it.
    // Using a setter instead of constructor injection to break the CallManager↔InCallServiceImpl cycle.
    @Volatile
    var incallService: InCallService? = null
        private set

    /**
     * Registers the live InCallServiceImpl instance so CallManager can delegate
     * audio control (mute, speaker) to it. Called from InCallServiceImpl.onCreate.
     */
    fun registerInCallService(service: InCallService) {
        this.incallService = service
    }
    private val telecomCalls = mutableMapOf<String, Call>()

    fun getTelecomCall(callId: String): Call? = telecomCalls[callId]

    fun setTelecomCall(callId: String, call: Call) {
        telecomCalls[callId] = call
    }

    fun removeTelecomCall(callId: String) {
        telecomCalls.remove(callId)
    }

    /**
     * Update cached contact info for a number (from contact lookup).
     */
    fun updateContactInfo(number: String, name: String, photoUri: String?, numberTypeLabel: String?) {
        contactNameCache[number] = name
    }

    /**
     * Get cached contact name for a number.
     */
    fun getContactName(number: String): String? = contactNameCache[number]

    /**
     * Called by InCallService whenever the call list changes.
     */
    fun updateCalls(calls: List<Call>) {
        val newCalls = calls.associate { call ->
            val info = CallInfo(
                id = call.details?.handle?.toString() ?: call.hashCode().toString(),
                state = CallState.fromTelecomState(call.state),
                number = call.details?.handle?.schemeSpecificPart ?: "",
                displayName = call.details?.contactDisplayName ?: call.details?.handle?.schemeSpecificPart ?: "",
                photoUri = call.details?.contactPhotoUri,
                isConference = call.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true,
                isVideoCall = runCatching {
                    call.details?.let { details ->
                        val propField = Call.Details::class.java.getField("PROPERTY_VIDEO_CALL")
                        val propValue = propField.get(null) as Int
                        details.hasProperty(propValue)
                    } ?: false
                }.getOrDefault(false),
                childrenCallIds = call.children.map { it.hashCode().toString() },
                parentCallId = call.parent?.hashCode()?.toString(),
                disconnectCause = null,
                timestamp = call.details?.creationTimeMillis ?: System.currentTimeMillis(),
                phoneAccount = call.details?.accountHandle,
            )
            info.id to info
        }
        _calls.value = newCalls
    }

    fun updateAudioState(audioState: CallAudioState) {
        _audioState.value = audioState
    }

    /**
     * Returns the primary active call, if any.
     */
    fun getActiveCall(): CallInfo? {
        return _calls.value.values.firstOrNull {
            it.state == CallState.ACTIVE
        }
    }

    /**
     * Returns the call to display — outgoing/incoming first, then any active.
     */
    fun getForegroundCall(): CallInfo? {
        val calls = _calls.value.values
        return calls.firstOrNull { it.state == CallState.RINGING || it.state == CallState.DIALING }
            ?: calls.firstOrNull { it.state == CallState.ACTIVE }
            ?: calls.firstOrNull { it.state == CallState.ON_HOLD }
            ?: calls.firstOrNull()
    }

    fun isInCall(): Boolean {
        return _calls.value.values.any {
            it.state == CallState.ACTIVE ||
                it.state == CallState.ON_HOLD ||
                it.state == CallState.DIALING ||
                it.state == CallState.RINGING ||
                it.state == CallState.CONNECTING
        }
    }

    /**
     * Answer a ringing call.
     */
    fun answerCall(callId: String) {
        telecomCalls[callId]?.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
    }

    /**
     * Reject / hang up a call.
     */
    fun rejectCall(callId: String) {
        telecomCalls[callId]?.reject(false, null)
    }

    /**
     * Disconnect active call.
     */
    fun disconnect(callId: String) {
        telecomCalls[callId]?.disconnect()
    }

    /**
     * Toggle hold.
     */
    fun toggleHold(callId: String) {
        val call = telecomCalls[callId] ?: return
        if (call.state == Call.STATE_ACTIVE) {
            call.hold()
        } else if (call.state == Call.STATE_HOLDING) {
            call.unhold()
        }
    }

    /**
     * Toggle mute state of the current active call.
     * Delegates to InCallServiceImpl.setMuted() which passes the request to Telecom.
     */
    fun toggleMute() {
        val audioState = _audioState.value ?: return
        incallService?.setMuted(!audioState.isMuted)
    }

    fun isMuted(): Boolean {
        return _audioState.value?.isMuted == true
    }

    /**
     * Set audio route (speaker, earpiece, bluetooth, etc.).
     * Delegates to InCallServiceImpl.setAudioRoute() which passes to Telecom.
     */
    fun setAudioRoute(route: Int) {
        incallService?.setAudioRoute(route)
    }
}
