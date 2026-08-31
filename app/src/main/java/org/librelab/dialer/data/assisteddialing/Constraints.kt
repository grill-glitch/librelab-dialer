package org.librelab.dialer.data.assisteddialing

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import android.telephony.TelephonyManager
import android.text.TextUtils
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates whether a phone number is eligible for assisted dialing transformation.
 *
 * Checks (in order):
 * 1. Number is non-empty
 * 2. Both country codes are non-empty
 * 3. Both country codes are in the supported list
 * 4. User is currently roaming (different country from home)
 * 5. Number is not already in international format (+ prefix)
 * 6. Number is not an emergency number
 * 7. Number is valid (parseable and plausible)
 * 8. Number has no extension
 *
 * Mirrors crDroid's `Constraints.java`.
 */
@Singleton
class Constraints @Inject constructor(
    @ApplicationContext private val context: Context,
    private val countryCodeProvider: CountryCodeProvider,
) {
    private val phoneNumberUtil = PhoneNumberUtil.getInstance()
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    /**
     * Returns true if the number is eligible for assisted dialing transformation.
     */
    fun meetsPreconditions(
        numberToCheck: String,
        userHomeCountryCode: String,
        userRoamingCountryCode: String,
    ): Boolean {
        if (TextUtils.isEmpty(numberToCheck)) return false
        if (TextUtils.isEmpty(userHomeCountryCode)) return false
        if (TextUtils.isEmpty(userRoamingCountryCode)) return false

        val home = userHomeCountryCode.uppercase()
        val roaming = userRoamingCountryCode.uppercase()

        val parsedNumber = parsePhoneNumber(numberToCheck, home) ?: return false

        return areSupportedCountryCodes(home, roaming)
            && isUserRoaming(home, roaming)
            && isNotInternationalNumber(parsedNumber)
            && isNotEmergencyNumber(numberToCheck)
            && isValidNumber(parsedNumber)
            && doesNotHaveExtension(parsedNumber)
    }

    private fun isUserRoaming(home: String, roaming: String): Boolean {
        return home != roaming
    }

    private fun areSupportedCountryCodes(home: String, roaming: String): Boolean {
        return countryCodeProvider.isSupportedCountryCode(home)
            && countryCodeProvider.isSupportedCountryCode(roaming)
    }

    private fun parsePhoneNumber(
        number: String,
        homeCountry: String,
    ): Phonenumber.PhoneNumber? {
        return try {
            phoneNumberUtil.parse(number, homeCountry)
        } catch (_: NumberParseException) {
            null
        }
    }

    private fun isNotInternationalNumber(parsed: Phonenumber.PhoneNumber): Boolean {
        // If the number already has an explicit country code (not from default country),
        // it's already international — don't transform.
        if (parsed.hasCountryCode()
            && parsed.countryCodeSource
            != Phonenumber.PhoneNumber.CountryCodeSource.FROM_DEFAULT_COUNTRY
        ) {
            return false
        }
        return true
    }

    private fun doesNotHaveExtension(parsed: Phonenumber.PhoneNumber): Boolean {
        return !parsed.hasExtension() || TextUtils.isEmpty(parsed.extension)
    }

    private fun isValidNumber(parsed: Phonenumber.PhoneNumber): Boolean {
        return phoneNumberUtil.isValidNumber(parsed)
    }

    private fun isNotEmergencyNumber(number: String): Boolean {
        // Use TelephonyManager.isEmergencyNumber (system API, respects network state)
        @Suppress("DEPRECATION")
        return !telephonyManager.isEmergencyNumber(number)
    }
}
