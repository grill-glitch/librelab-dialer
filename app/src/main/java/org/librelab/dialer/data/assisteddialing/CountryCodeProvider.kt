package org.librelab.dialer.data.assisteddialing

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides the set of country codes supported by assisted dialing.
 *
 * Mirrors crDroid's `CountryCodeProvider.java`.
 */
@Singleton
class CountryCodeProvider @Inject constructor() {
    companion object {
        /** Default supported country codes (same as crDroid). */
        val DEFAULT_SUPPORTED = setOf(
            "US", "CA", // North America
            "MX", // Mexico
            "BR", // Brazil
            "GB", "DE", "FR", "IT", "ES", "NL", "PL", "SE", "NO", "DK", "FI", // Europe
            "RU", // Russia
            "IN", // India
            "CN", "JP", "KR", "TW", "HK", "SG", // Asia-Pacific
            "AU", "NZ", // Oceania
            "ZA", // South Africa
        )

        /** Country calling codes mapped to ISO 3166-1 alpha-2. */
        val COUNTRY_CALLING_CODES = mapOf(
            "1" to "US",   // US/CA (shared, North American Numbering Plan)
            "1" to "CA",
            "1" to "MX",   // Mexico
            "52" to "MX",
            "55" to "BR",  // Brazil
            "44" to "GB",  // UK
            "49" to "DE",  // Germany
            "33" to "FR",  // France
            "39" to "IT",  // Italy
            "34" to "ES",  // Spain
            "31" to "NL",  // Netherlands
            "48" to "PL",  // Poland
            "46" to "SE",  // Sweden
            "47" to "NO",  // Norway
            "45" to "DK",  // Denmark
            "358" to "FI", // Finland
            "7" to "RU",   // Russia
            "7" to "KZ",   // Kazakhstan
            "91" to "IN",  // India
            "86" to "CN",  // China
            "81" to "JP",  // Japan
            "82" to "KR",  // South Korea
            "886" to "TW", // Taiwan
            "852" to "HK", // Hong Kong
            "65" to "SG",  // Singapore
            "61" to "AU",  // Australia
            "64" to "NZ",  // New Zealand
            "27" to "ZA",  // South Africa
        )
    }

    /**
     * Returns true if the given ISO 3166-1 alpha-2 country code is supported.
     */
    fun isSupportedCountryCode(countryCode: String): Boolean {
        return DEFAULT_SUPPORTED.contains(countryCode.uppercase())
    }

    /**
     * Get the country calling code for a given ISO country code.
     */
    fun getCallingCode(isoCountry: String): String? {
        return COUNTRY_CALLING_CODES.entries
            .firstOrNull { it.value.equals(isoCountry, ignoreCase = true) }
            ?.key
    }

    /**
     * Get the ISO country code for a given country calling code.
     */
    fun getIsoCountry(callingCode: String): String? {
        return COUNTRY_CALLING_CODES[callingCode]
    }
}
