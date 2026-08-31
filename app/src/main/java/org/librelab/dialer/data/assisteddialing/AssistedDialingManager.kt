package org.librelab.dialer.data.assisteddialing

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AssistedDialingManager — Kotlin reimplementation of crDroid's AssistedDialingMediator.
 *
 * Orchestrates assisted dialing:
 * 1. [LocationDetector] determines home country (SIM) and roaming country (network)
 * 2. [Constraints] validates the number is eligible for assisted dialing
 * 3. [NumberTransformer] performs the number transformation (adds country prefix)
 *
 * Mirrors `AssistedDialingMediatorImpl.java`.
 */
@Singleton
class AssistedDialingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationDetector: LocationDetector,
    private val constraints: Constraints,
    private val numberTransformer: NumberTransformer,
) {

    /**
     * Returns true if the platform supports assisted dialing (always true for our impl).
     */
    fun isPlatformEligible(): Boolean = true

    /**
     * Returns the user's home country ISO code, or null if undetectable.
     */
    fun userHomeCountryCode(): String? = locationDetector.getUpperCaseUserHomeCountry()

    /**
     * Attempts assisted dialing transformation on the given number.
     *
     * Conditions for transformation (mirrors crDroid):
     * - User must be roaming (home country ≠ roaming country)
     * - Both home and roaming countries must be supported
     * - Number must not already be in international format (+ prefix)
     * - Number must not be an emergency number
     * - Number must be valid and have no extension
     *
     * @param numberToTransform raw local number (e.g. "13812345678")
     * @return TransformationInfo if eligible, null otherwise
     */
    fun attemptAssistedDial(numberToTransform: String): TransformationInfo? {
        val userHomeCountry = locationDetector.getUpperCaseUserHomeCountry()
        val userRoamingCountry = locationDetector.getUpperCaseUserRoamingCountry()

        if (userHomeCountry == null || userRoamingCountry == null) {
            return null
        }

        val eligible = constraints.meetsPreconditions(
            numberToCheck = numberToTransform,
            userHomeCountryCode = userHomeCountry,
            userRoamingCountryCode = userRoamingCountry,
        )

        if (!eligible) {
            return null
        }

        return numberTransformer.doAssistedDialingTransformation(
            numberToTransform = numberToTransform,
            userHomeCountry = userHomeCountry,
            userRoamingCountry = userRoamingCountry,
        )
    }

    /**
     * Formats a number for display in the given country.
     */
    fun formatForDisplay(number: String, countryCode: String): String {
        return numberTransformer.formatForDisplay(number, countryCode)
    }
}
