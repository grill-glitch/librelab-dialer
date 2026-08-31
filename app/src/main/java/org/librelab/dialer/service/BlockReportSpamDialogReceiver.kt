package org.librelab.dialer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Handles [ACTION_SHOW_BLOCK_REPORT_DIALOG] —
 * shows the block / report-spam dialog after an incoming call.
 *
 * Currently a no-op stub. The actual block/report flow is handled by
 * [CallScreeningServiceImpl] which silently blocks spam calls before they reach the user.
 * A future improvement would show an in-call UI prompt after a blocked call.
 *
 * Keeping this avoids ActivityNotFoundException if any code sends this intent.
 */
class BlockReportSpamDialogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SHOW_BLOCK_REPORT_DIALOG) return
        Log.d(TAG, "Received ACTION_SHOW_BLOCK_REPORT_DIALOG — no-op (CallScreeningService handles blocking)")
    }

    companion object {
        private const val TAG = "BlockReportSpamDialog"
        const val ACTION_SHOW_BLOCK_REPORT_DIALOG = "org.librelab.dialer.ACTION_SHOW_BLOCK_REPORT_DIALOG"
    }
}
