package org.librelab.dialer.data.calllog

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.librelab.dialer.domain.model.CallLogEntry
import org.librelab.dialer.domain.model.CallLogGroup
import org.librelab.dialer.domain.model.CallType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for reading/writing the system CallLog.
 * Core query logic migrated from CallLogFragment.java / CallLogAsyncTaskUtil.java
 */
@Singleton
class CallLogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val contentResolver: ContentResolver = context.contentResolver

    companion object {
        // Projection for CallLog query — mirrors CallLogFragment's projection
        // Note: SIM_ID / CACHED_DISPLAY_NAME removed from CallLog.Calls public surface.
        // Use subscription_component_name / subscription_id as runtime-resolved equivalents.
        val CALL_LOG_PROJECTION = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.CACHED_PHOTO_URI,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            "subscription_id",          // SIM subscription id (int)
            CallLog.Calls.IS_READ,
            CallLog.Calls.COUNTRY_ISO,
            CallLog.Calls.GEOCODED_LOCATION,
            CallLog.Calls.VOICEMAIL_URI,
            CallLog.Calls.CACHED_LOOKUP_URI,
        )

        private const val CALL_LOG_SORT_ORDER = "${CallLog.Calls.DATE} DESC"

        // Grouping: calls to same number within 4 hours are grouped
        private const val GROUP_TIME_THRESHOLD_MS = 4 * 60 * 60 * 1000L
    }

    /**
     * Query recent call log entries.
     * @param limit Max entries to return (0 = unlimited)
     */
    suspend fun getCallLog(limit: Int = 500): List<CallLogEntry> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<CallLogEntry>()
        val uri = CallLog.Calls.CONTENT_URI
        val selection = "${CallLog.Calls.TYPE} != ?"
        val selectionArgs = arrayOf(CallType.BLOCKED.ordinal.toString())

        // SDK 37 changed the query() signatures — limit must go into the query Bundle.
        val queryArgs = Bundle().apply {
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, CALL_LOG_SORT_ORDER)
            if (limit > 0) {
                putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
            }
        }
        val cursor: Cursor? = contentResolver.query(
            uri,
            CALL_LOG_PROJECTION,
            queryArgs,
            null,
        )

        cursor?.use {
            val idIdx = it.getColumnIndexOrThrow(CallLog.Calls._ID)
            val nameIdx = it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val photoIdx = it.getColumnIndexOrThrow(CallLog.Calls.CACHED_PHOTO_URI)
            val numIdx = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val typeIdx = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateIdx = it.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durIdx = it.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            val simIdIdx = it.getColumnIndexOrThrow("subscription_id")
            val readIdx = it.getColumnIndexOrThrow(CallLog.Calls.IS_READ)
            val countryIdx = it.getColumnIndexOrThrow(CallLog.Calls.COUNTRY_ISO)
            val geoIdx = it.getColumnIndexOrThrow(CallLog.Calls.GEOCODED_LOCATION)
            val vmIdx = it.getColumnIndexOrThrow(CallLog.Calls.VOICEMAIL_URI)
            val lookupUriIdx = it.getColumnIndexOrThrow(CallLog.Calls.CACHED_LOOKUP_URI)

            while (it.moveToNext()) {
                entries.add(
                    CallLogEntry(
                        id = it.getLong(idIdx),
                        number = it.getString(numIdx) ?: "",
                        displayName = it.getString(nameIdx) ?: it.getString(numIdx) ?: "Unknown",
                        photoUri = it.getString(photoIdx)?.let { uri -> Uri.parse(uri) },
                        callType = CallType.fromRaw(it.getInt(typeIdx)),
                        timestamp = it.getLong(dateIdx),
                        duration = it.getLong(durIdx),
                        simId = it.getInt(simIdIdx),
                        isRead = it.getInt(readIdx) == 1,
                        countryIso = it.getString(countryIdx),
                        geocodedLocation = it.getString(geoIdx),
                        voicemailUri = it.getString(vmIdx)?.let { uri -> Uri.parse(uri) },
                    )
                )
            }
        }
        entries
    }

    /**
     * Group call log entries by number (calls to same number within time window = one group).
     * Core logic migrated from CallLogGroupBuilder.java
     */
    fun groupCallLogEntries(entries: List<CallLogEntry>): List<CallLogGroup> {
        if (entries.isEmpty()) return emptyList()

        val groups = mutableListOf<CallLogGroup>()
        var currentGroup = mutableListOf<CallLogEntry>()
        var lastNumber: String? = null
        var lastTime = 0L

        for (entry in entries) {
            val timeDelta = entry.timestamp - lastTime
            if (entry.number != lastNumber || timeDelta > GROUP_TIME_THRESHOLD_MS) {
                // Finish previous group
                if (currentGroup.isNotEmpty()) {
                    groups.add(buildGroup(currentGroup, lastNumber))
                }
                // Start new group
                currentGroup = mutableListOf(entry)
                lastNumber = entry.number
                lastTime = entry.timestamp
            } else {
                currentGroup.add(entry)
                lastTime = entry.timestamp
            }
        }
        // Flush last group
        if (currentGroup.isNotEmpty()) {
            groups.add(buildGroup(currentGroup, lastNumber))
        }
        return groups
    }

    private fun buildGroup(entries: MutableList<CallLogEntry>, number: String?): CallLogGroup {
        val first = entries.first()
        // Compose key: must be unique across all rows. Use ID list joined with "-".
        val uniqueId = entries.joinToString("-") { it.id.toString() }
        return CallLogGroup(
            id = uniqueId,
            entries = entries,
            // Contact resolved lazily by the UI layer via ContactRepository
            contact = null,
            isSpam = entries.any { it.isSpam },
            isBlocked = entries.any { it.isBlocked },
        )
    }

    /**
     * Delete a single call log entry.
     */
    suspend fun deleteCallLogEntry(id: Long) = withContext(Dispatchers.IO) {
        contentResolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls._ID} = ?", arrayOf(id.toString()))
    }

    /**
     * Clear all call log.
     */
    suspend fun clearAllCallLog() = withContext(Dispatchers.IO) {
        contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
    }

    /**
     * Mark a call log entry as read.
     */
    suspend fun markAsRead(id: Long) = withContext(Dispatchers.IO) {
        val values = android.content.ContentValues().apply {
            put(CallLog.Calls.IS_READ, 1)
        }
        contentResolver.update(CallLog.Calls.CONTENT_URI, values, "${CallLog.Calls._ID} = ?", arrayOf(id.toString()))
    }
}
