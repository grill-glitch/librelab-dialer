package org.librelab.dialer.data.calllog

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes the CallLog for missed calls and exposes a badge count.
 * Migrated from MissedCallCountObserver.java — now uses StateFlow for Compose.
 */
@Singleton
class MissedCallCountRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            refresh()
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            refresh()
        }
    }

    private var registered = false

    fun register() {
        if (registered) return
        try {
            context.contentResolver.registerContentObserver(
                CallLog.Calls.CONTENT_URI,
                true,
                observer,
            )
            registered = true
            refresh()
        } catch (_: SecurityException) {
            // READ_CALL_LOG not granted — badge stays at 0
        }
    }

    fun unregister() {
        if (!registered) return
        try {
            context.contentResolver.unregisterContentObserver(observer)
            registered = false
        } catch (_: Exception) {
            // already unregistered
        }
    }

    /**
     * Recount unread missed calls.
     */
    fun refresh() {
        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID),
                "(${CallLog.Calls.IS_READ} = 0 OR ${CallLog.Calls.IS_READ} IS NULL) AND ${CallLog.Calls.TYPE} = ?",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
                null,
            )
            _count.value = cursor?.use { it.count } ?: 0
        } catch (_: SecurityException) {
            _count.value = 0
        }
    }
}