package org.librelab.dialer.data.assisteddialing

/**
 * Result of a phone number transformation for assisted dialing.
 *
 * @param transformedNumber the number with country code prefix applied
 * @param userHomeCountry ISO 3166-1 alpha-2 country code of the user's home country
 * @param userRoamingCountry ISO 3166-1 alpha-2 country code of the country the user is currently in
 */
data class TransformationInfo(
    val transformedNumber: String,
    val userHomeCountry: String,
    val userRoamingCountry: String,
)
