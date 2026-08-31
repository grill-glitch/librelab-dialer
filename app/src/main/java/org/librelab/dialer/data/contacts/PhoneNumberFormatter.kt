package org.librelab.dialer.data.contacts

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PhoneNumberFormatter — Kotlin replacement for PhoneNumberFormatter.java
 * + phone number utility helpers from org.librelab.dialer.phonenumberutil.
 */
@Singleton
class PhoneNumberFormatter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val MAX_DISPLAY_LENGTH = 20
    }

    /**
     * Format a phone number for display in the dialer.
     * Uses Android's built-in formatter as a first pass; libphonenumber could be added.
     */
    fun formatForDisplay(number: String, defaultCountryIso: String? = null): String {
        if (number.isEmpty()) return ""
        // Naive formatting: split into 3-3-4
        return if (number.length <= 3) {
            number
        } else {
            // Use system formatter for international numbers
            try {
                android.telephony.PhoneNumberUtils.formatNumber(number, defaultCountryIso) ?: number
            } catch (_: Exception) {
                number
            }
        }
    }

    /**
     * Strip a phone number of all non-essential characters.
     */
    fun normalize(number: String): String {
        return number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
    }

    /**
     * Get the last N digits (for matching similar numbers).
     */
    fun lastNDigits(number: String, n: Int): String {
        val digits = normalize(number)
        return if (digits.length <= n) digits else digits.takeLast(n)
    }

    /**
     * Check if the number represents an emergency call.
     */
    fun isEmergencyNumber(number: String): Boolean {
        return android.telephony.PhoneNumberUtils.isEmergencyNumber(number)
    }

    /**
     * Check if a number is a voicemail number.
     */
    fun isVoicemailNumber(number: String): Boolean {
        // Common voicemail numbers: empty, "100", "1", "86"
        return number in listOf("", "100", "1", "86", "*86")
    }

    /**
     * Compare two numbers for similarity (used by smart dial).
     */
    fun isSimilar(a: String, b: String): Boolean {
        return lastNDigits(a, 7) == lastNDigits(b, 7) && lastNDigits(a, 7).isNotEmpty()
    }

    /**
     * Build a contact lookup URI from a phone number.
     */
    fun contactLookupUri(number: String): Uri {
        return Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number),
        )
    }
}
