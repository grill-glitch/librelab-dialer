package org.librelab.dialer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Handles [Intent.ACTION_NEW_OUTGOING_CALL] — intercepts outgoing calls before they are placed.
 *
 * This is triggered for ALL outgoing calls, regardless of which dialer initiated them.
 * The intent contains [Intent.EXTRA_PHONE_NUMBER] with the dialed number.
 *
 * Returnring `RESULT_LOCAL_ABORT` aborts the call (must hold CALL_PRIVILEGED permission).
 * We currently use this for assisted-dialing integration where needed, but since
 * TelecomAdapter applies assisted dialing at [TelecomManager.placeCall] time,
 * no transformation is needed here.
 *
 * Keeping this avoids ActivityNotFoundException if the system sends this intent.
 */
class OutgoingCallReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "OutgoingCallReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_NEW_OUTGOING_CALL) return
        val number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER) ?: return
        Log.d(TAG, "Intercepted outgoing call to: $number — passing through")
        // Pass through — assisted dialing is handled by TelecomAdapter at placeCall() time
    }
}
