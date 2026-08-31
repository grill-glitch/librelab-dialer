package org.librelab.dialer.domain.model

import android.net.Uri

/**
 * Represents a call log entry.
 * Migrated from CallLogFragment / CallLogCache Java code.
 */
data class CallLogEntry(
    val id: Long,
    val number: String,
    val displayName: String,
    val photoUri: Uri?,
    val callType: CallType,
    val timestamp: Long,
    val duration: Long, // seconds, 0 for missed
    val simId: Int,
    val isRead: Boolean,
    val countryIso: String?,
    val geocodedLocation: String?,
    val voicemailUri: Uri?,
    val isSpam: Boolean = false,
    val isBlocked: Boolean = false,
)

enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,
    VOICEMAIL,
    BLOCKED,
    BLOCKED_REJECTED,
    BLOCKED_MISSED,
    UNKNOWN;

    companion object {
        fun fromRaw(type: Int): CallType = when (type) {
            1 -> INCOMING
            2 -> OUTGOING
            3 -> MISSED
            4 -> REJECTED
            5 -> VOICEMAIL
            else -> UNKNOWN
        }
    }
}
