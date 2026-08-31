package org.librelab.dialer.service

import android.net.Uri
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import dagger.hilt.android.AndroidEntryPoint
import org.librelab.dialer.data.antispam.SpamRepository
import javax.inject.Inject

/**
 * CallScreeningService — antispam integration.
 * Replaces BlockReportSpamServiceImpl.java / CallScreeningServiceImpl.java.
 *
 * Called by Android system when a call arrives — we can either:
 * - Allow the call (default)
 * - Block the call (silently disconnect)
 * - Disconnect with a reason
 *
 * We consult the SpamRepository for known spam numbers.
 */
@AndroidEntryPoint
class CallScreeningServiceImpl : CallScreeningService() {

    @Inject
    lateinit var spamRepository: SpamRepository

    override fun onScreenCall(details: Call.Details) {
        val response = CallResponse.Builder()

        val handle = details.handle
        val number = handle?.schemeSpecificPart ?: return

        // Skip non-tel schemes and emergency numbers
        if (handle?.scheme != "tel") return
        if (android.telephony.PhoneNumberUtils.isEmergencyNumber(number)) {
            response.setDisallowCall(false)
            response.setRejectCall(false)
            respondToCall(details, response.build())
            return
        }

        // Check spam repository — explicit blocklist
        val isBlocked = spamRepository.isBlockedNumber(number)
        // Check category-based blocking (user-enabled categories)
        val isBlockedByCategory = spamRepository.isBlockedByCategory(number)
        // Mark as spam only (allow ring but could show warning)
        val isSpam = spamRepository.isSpamNumber(number)

        when {
            isBlocked || isBlockedByCategory -> {
                // Block silently — don't ring, don't log, don't notify
                response.setDisallowCall(true)
                response.setRejectCall(true)
                response.setSkipCallLog(true)
                response.setSkipNotification(true)
            }
            isSpam -> {
                // Mark as potential spam but still allow ring
                response.setDisallowCall(false)
                response.setRejectCall(false)
            }
            else -> {
                response.setDisallowCall(false)
                response.setRejectCall(false)
            }
        }

        respondToCall(details, response.build())
    }
}
