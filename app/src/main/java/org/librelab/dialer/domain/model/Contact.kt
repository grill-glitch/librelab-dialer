package org.librelab.dialer.domain.model

import android.net.Uri

/**
 * Represents a contact for display in call log / favorites.
 * Minimal contact data used by the dialer UI.
 */
data class Contact(
    val id: Long,
    val lookupKey: String,
    val displayName: String,
    val photoUri: Uri?,
    val photoThumbnailUri: Uri?,
    val phoneNumbers: List<PhoneNumber> = emptyList(),
    val isFavorite: Boolean = false,
)

data class PhoneNumber(
    val number: String,
    val label: String,
    val type: Int, // Phone.CONTENT_ITEM_TYPE
    val normalizedNumber: String? = null,
)

/**
 * Call log group — multiple calls to the same number within a time window.
 */
data class CallLogGroup(
    val id: String, // unique key for the group
    val entries: List<CallLogEntry>,
    val contact: Contact? = null,
    val isSpam: Boolean = false,
    val isBlocked: Boolean = false,
) {
    val latestTimestamp: Long get() = entries.maxOf { it.timestamp }
    val totalDuration: Long get() = entries.filter { it.callType != CallType.MISSED }.sumOf { it.duration }
    val count: Int get() = entries.size
    val displayNumber: String get() = entries.firstOrNull()?.number ?: ""
    val displayName: String get() = contact?.displayName ?: entries.firstOrNull()?.displayName ?: ""
    val isMultiCall: Boolean get() = entries.size > 1
}
