package org.librelab.dialer.data.assisteddialing

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects the user's home country (SIM) and current roaming country (network).
 *
 * Mirrors crDroid's `LocationDetector.java`.
 */
@Singleton
class LocationDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    /**
     * Returns the user's home country ISO code — derived from the SIM card's country.
     *
     * Falls back to network country if SIM country is unavailable.
     * Returns null if neither is available.
     */
    @SuppressLint("MissingPermission")
    fun getHomeCountry(): String? {
        // SIM country
        val simCountry = telephonyManager.simCountryIso
        if (simCountry.isNotEmpty()) {
            return simCountry.uppercase()
        }

        // Network country (when SIM not ready)
        val networkCountry = telephonyManager.networkCountryIso
        if (networkCountry.isNotEmpty()) {
            return networkCountry.uppercase()
        }

        return null
    }

    /**
     * Returns the country ISO code where the user is currently roaming,
     * derived from the current network.
     *
     * Note: This returns the same as getHomeCountry() when the user is not roaming.
     * The actual roaming detection requires comparing home vs. roaming country.
     */
    @SuppressLint("MissingPermission")
    fun getRoamingCountry(): String? {
        return telephonyManager.networkCountryIso.uppercase().ifEmpty { null }
    }

    /**
     * Returns the user's home country in upper-case, wrapped in an Optional-like result.
     */
    fun getUpperCaseUserHomeCountry(): String? = getHomeCountry()

    /**
     * Returns the user's roaming country in upper-case.
     */
    fun getUpperCaseUserRoamingCountry(): String? = getRoamingCountry()
}
