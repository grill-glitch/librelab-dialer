package org.librelab.dialer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Handles [Intent.ACTION_SHOW_MISSED_CALLS] — shows the missed calls UI.
 * This receiver is triggered when the user dismisses an incoming-call notification
 * while the app is not the default dialer.
 *
 * Currently a no-op stub since missed-call UI is handled by the CallLog tab.
 * Keeping this avoids ActivityNotFoundException if the system sends this intent.
 */
class MissedCallNotificationReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "MissedCallNotifReceiver"
        private const val ACTION_SHOW_MISSED_CALLS = "android.intent.action.SHOW_MISSED_CALLS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SHOW_MISSED_CALLS) return
        Log.d(TAG, "Received ACTION_SHOW_MISSED_CALLS — no-op (CallLog tab handles this)")
    }
}
