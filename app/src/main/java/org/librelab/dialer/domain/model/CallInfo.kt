package org.librelab.dialer.domain.model

import android.telecom.Call
import android.telecom.DisconnectCause

/**
 * Represents a call in any state — mirrors the original Call.java state model.
 */
data class CallInfo(
    val id: String,
    val state: CallState,
    val number: String,
    val displayName: String,
    val photoUri: android.net.Uri?,
    val isConference: Boolean,
    val isVideoCall: Boolean,
    val childrenCallIds: List<String> = emptyList(),
    val parentCallId: String? = null,
    val disconnectCause: DisconnectCause? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val phoneAccount: android.telecom.PhoneAccountHandle? = null,
)

enum class CallState {
    IDLE,
    DIALING,
    RINGING,
    ACTIVE,
    ON_HOLD,
    DISCONNECTED,
    DISCONNECTING,
    CONNECTING,
    SELECT_PHONE_ACCOUNT,
    SWITCHING,
    CONFERENCE;

    companion object {
        fun fromTelecomState(state: Int): CallState = when (state) {
            Call.STATE_ACTIVE -> ACTIVE
            Call.STATE_HOLDING -> ON_HOLD
            Call.STATE_DIALING -> DIALING
            Call.STATE_RINGING -> RINGING
            Call.STATE_DISCONNECTED -> DISCONNECTED
            Call.STATE_DISCONNECTING -> DISCONNECTING
            Call.STATE_CONNECTING -> CONNECTING
            Call.STATE_SELECT_PHONE_ACCOUNT -> SELECT_PHONE_ACCOUNT
            Call.STATE_PULLING_CALL -> SWITCHING
            Call.STATE_AUDIO_PROCESSING -> CONNECTING
            else -> IDLE
        }
    }
}
