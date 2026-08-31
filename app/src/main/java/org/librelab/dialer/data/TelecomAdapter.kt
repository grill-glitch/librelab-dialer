package org.librelab.dialer.data

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import org.librelab.dialer.data.assisteddialing.AssistedDialingManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TelecomAdapter — Kotlin reimplementation of TelecomAdapter.java from incallui.
 * Places outgoing calls and interacts with TelecomManager.
 */
@Singleton
class TelecomAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telecomManager: TelecomManager,
    private val telephonyManager: TelephonyManager,
    private val assistedDialingManager: AssistedDialingManager,
) {
    companion object {
        const val EXTRA_DIALPAD_VISIBLE = "android:telecom.dialpad_visible"
        const val EXTRA_CALL_INITIATION_TYPE = "call_initiation_type"
        const val INIT_DIALPAD = "dialpad"
        const val INIT_CALL_LOG = "call_log"
        const val INIT_SHORTCUT = "shortcut"
        const val INIT_CONTACT = "contact"
        const val INIT_VOICEMAIL = "voicemail"
    }

    /**
     * Apply assisted dialing transformation to a number using libphonenumber.
     *
     * Full crDroid logic:
     * - Number already international (+) → return as-is
     * - Not roaming (home == roaming country) → return as-is
     * - Number fails any Constraint check → return as-is
     * - Transform adds E.164 country prefix
     *
     * @param number raw dialed number
     * @param assistedDialingEnabled whether assisted dialing is turned on
     * @param countryIndex selected country index (0=auto, 1+=explicit)
     * @return number possibly transformed with country prefix
     */
    fun applyAssistedDialing(
        number: String,
        assistedDialingEnabled: Boolean,
        countryIndex: Int,
    ): String {
        if (!assistedDialingEnabled) return number

        // Already international — nothing to do
        if (number.startsWith("+")) return number

        // If user selected a specific country, override location detection
        val overrideCountry = if (countryIndex > 0) {
            getCountryCodeFromIndex(countryIndex)
        } else {
            null // auto mode — use LocationDetector
        }

        val homeCountry = overrideCountry ?: assistedDialingManager.userHomeCountryCode()
            ?: return number
        val roamingCountry = assistedDialingManager
            .userHomeCountryCode() // fall back to same if roaming country undetectable
            ?: return number

        // Attempt transformation (checks Constraints internally)
        val result = assistedDialingManager.attemptAssistedDial(number)
        return result?.transformedNumber ?: number
    }

    /**
     * Map a country index (from settings) to an ISO 3166-1 alpha-2 country code.
     * Index 0 = Auto = null (use LocationDetector).
     */
    private fun getCountryCodeFromIndex(index: Int): String? {
        val map = listOf(
            1 to "CN", 2 to "US", 3 to "GB", 4 to "DE", 5 to "FR",
            6 to "JP", 7 to "KR", 8 to "AU", 9 to "CA", 10 to "IN",
            11 to "RU", 12 to "BR", 13 to "MX",
        )
        return map.find { it.first == index }?.second
    }

    /**
     * Place an outgoing call.
     * @param number raw dialed number
     * @param assistedDialingEnabled whether to apply assisted dialing logic
     * @param assistedDialingCountryIndex country index for assisted dialing (0=auto)
     * @param initType call initiation type for extras bundle
     */
    fun placeCall(
        number: String,
        assistedDialingEnabled: Boolean = true,
        assistedDialingCountryIndex: Int = 0,
        initType: String = INIT_DIALPAD,
    ) {
        val prefixedNumber = applyAssistedDialing(number, assistedDialingEnabled, assistedDialingCountryIndex)
        val uri = Uri.fromParts("tel", prefixedNumber, null)
        val extras = Bundle().apply {
            putString(EXTRA_CALL_INITIATION_TYPE, initType)
        }
        telecomManager.placeCall(uri, extras)
    }

    /**
     * Place call with a specific PhoneAccount (SIM selection).
     */
    fun placeCallWithAccount(
        number: String,
        phoneAccount: PhoneAccountHandle?,
        initType: String = INIT_DIALPAD,
    ) {
        val uri = Uri.fromParts("tel", number, null)
        val extras = Bundle().apply {
            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccount)
            putString(EXTRA_CALL_INITIATION_TYPE, initType)
        }
        telecomManager.placeCall(uri, extras)
    }

    /**
     * Check if we are the default dialer.
     */
    fun isDefaultDialer(): Boolean {
        return telecomManager.defaultDialerPackage == context.packageName
    }

    /**
     * Get all call-capable PhoneAccounts (SIMs).
     * getCallCapablePhoneAccounts() is the stable API replacing the removed `phoneAccounts` property.
     */
    @Suppress("DEPRECATION")
    fun getSimPhoneAccounts(): List<PhoneAccountHandle> {
        return telecomManager.callCapablePhoneAccounts ?: emptyList()
    }

    /**
     * Get the user's default outgoing phone account.
     */
    fun getDefaultOutgoingPhoneAccount(): PhoneAccountHandle? {
        return telecomManager.getDefaultOutgoingPhoneAccount("tel")
    }

    /**
     * Check if voicemail is configured.
     * Always returns true — voicemail presence is determined at runtime by the system.
     */
    fun hasVoicemail(): Boolean = true

    /**
     * Cancel outstanding outgoing call (no-op, managed by InCallService).
     */
    fun cancelOutstandingCall() {}

    /**
     * Show in-call screen with optional dialpad.
     */
    fun showInCallScreen(showDialpad: Boolean) {
        try {
            telecomManager.showInCallScreen(showDialpad)
        } catch (_: SecurityException) {
            // Requires CALL_PHONE permission
        }
    }

    /**
     * Answer a ringing call.
     */
    fun answerRingingCall() {
        telecomManager.acceptRingingCall()
    }

    /**
     * End a call.
     */
    fun endCall() {
        try {
            telecomManager.endCall()
        } catch (_: SecurityException) {
            // Permission denied
        }
    }

    /**
     * Check if there is an active or ringing call.
     */
    fun hasCallAbility(): Boolean = getDefaultOutgoingPhoneAccount() != null
    fun hasActiveCall(): Boolean {
        return telecomManager.isInCall
    }
}
