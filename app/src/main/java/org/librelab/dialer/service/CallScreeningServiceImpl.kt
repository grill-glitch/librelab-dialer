package org.librelab.dialer.service

import android.telecom.Call
import android.telecom.CallScreeningService
import dagger.hilt.android.AndroidEntryPoint
import org.librelab.dialer.data.antispam.SpamRepository
import javax.inject.Inject

/**
 * CallScreeningService — integrates with the MIUI Yellow Page anti-spam API
 * (via SpamRepository) to block or allow incoming calls based on real
 * crowd-sourced marking data.
 *
 * Called by the Android system when a call arrives (only if the user has
 * set this app as the "Call Screening App" in system settings).
 *
 * Block strategy:
 * 1. Explicitly blocked numbers (user blocklist) → silently reject
 * 2. MIUI API mark (catId + count >= 3) + category enabled by user → silently reject
 * 3. Emergency numbers → always allow
 * 4. Everything else → allow
 */
@AndroidEntryPoint
class CallScreeningServiceImpl : CallScreeningService() {

    @Inject
    lateinit var spamRepository: SpamRepository

    override fun onScreenCall(details: Call.Details) {
        val handle = details.handle
            ?: // No handle — cannot identify caller
            return respondAllow(details)

        if (handle.scheme != "tel") {
            // Non-tel scheme (e.g. sip:), skip
            return respondAllow(details)
        }

        val number = handle.schemeSpecificPart ?: return respondAllow(details)

        // Emergency numbers — always allow
        if (android.telephony.PhoneNumberUtils.isEmergencyNumber(number)) {
            return respondAllow(details)
        }

        // Check explicit user blocklist
        if (spamRepository.shouldRejectCall(number)) {
            return respondBlock(details)
        }

        // Check MIUI API anti-spam database (async, cached 1 hour)
        // If the lookup has already been cached, isSpamByMiui returns immediately.
        // Otherwise it returns false (the async lookup will populate the cache for next time).
        if (spamRepository.isSpamByMiui(number)) {
            return respondBlock(details)
        }

        respondAllow(details)
    }

    private fun respondAllow(details: Call.Details) {
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
        respondToCall(details, response.build())
    }

    private fun respondBlock(details: Call.Details) {
        val response = CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipCallLog(true)
            .setSkipNotification(true)
        respondToCall(details, response.build())
    }
}
