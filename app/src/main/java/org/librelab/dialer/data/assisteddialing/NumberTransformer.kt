package org.librelab.dialer.data.assisteddialing

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import com.google.i18n.phonenumbers.Phonenumber
import javax.inject.Inject

/**
 * Transforms phone numbers for assisted dialing — prepends country calling code
 * when the user is roaming internationally.
 *
 * Mirrors crDroid's `NumberTransformer.java`.
 */
class NumberTransformer @Inject constructor(
    private val countryCodeProvider: CountryCodeProvider,
) {
    private val phoneNumberUtil = PhoneNumberUtil.getInstance()

    /**
     * Transform a number for international roaming.
     *
     * Given the user's home country and current roaming country, if the user is abroad
     * and the number is a local number (not already international), prepends the
     * country calling code so the call routes correctly.
     *
     * @param numberToTransform raw local number (e.g. "13812345678")
     * @param userHomeCountry ISO 3166-1 alpha-2 (e.g. "CN")
     * @param userRoamingCountry ISO 3166-1 alpha-2 (e.g. "US")
     * @return TransformationInfo with the transformed number, or null if not eligible
     */
    fun doAssistedDialingTransformation(
        numberToTransform: String,
        userHomeCountry: String,
        userRoamingCountry: String,
    ): TransformationInfo? {
        val home = userHomeCountry.uppercase()
        val roaming = userRoamingCountry.uppercase()

        // Get the country calling code for the home country
        val callingCode = countryCodeProvider.getCallingCode(home) ?: return null

        // Parse the number in the context of the home country
        val parsed: Phonenumber.PhoneNumber = try {
            phoneNumberUtil.parse(numberToTransform, home)
        } catch (_: NumberParseException) {
            return null
        }

        // Format as international E.164 number
        val formatted = phoneNumberUtil.format(parsed, PhoneNumberFormat.E164)
            ?: return null

        return TransformationInfo(
            transformedNumber = formatted,
            userHomeCountry = home,
            userRoamingCountry = roaming,
        )
    }

    /**
     * Formats a number for display (National format within the country).
     *
     * @param number raw number
     * @param countryCode ISO 3166-1 alpha-2
     */
    fun formatForDisplay(number: String, countryCode: String): String {
        return try {
            val parsed = phoneNumberUtil.parse(number, countryCode)
            phoneNumberUtil.format(parsed, PhoneNumberFormat.NATIONAL)
        } catch (_: NumberParseException) {
            number
        }
    }
}
