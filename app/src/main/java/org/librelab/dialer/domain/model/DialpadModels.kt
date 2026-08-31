package org.librelab.dialer.domain.model

/**
 * Dialpad digit — single key press.
 */
data class DialpadKey(
    val digit: Char,
    val letters: String,
    val dtmfTone: Char,
)

/**
 * Dialpad state for ViewModel.
 */
data class DialpadState(
    val digits: String = "",
    val isDigitblended: Boolean = false, // whether digits exceed display width
    val showKeypad: Boolean = true,
)

/**
 * Phone number with parsed metadata.
 */
data class PhoneNumberParsed(
    val raw: String,
    val normalized: String,
    val isEmergency: Boolean,
    val isVoicemail: Boolean,
    val isInternational: Boolean,
    val countryIso: String?,
)
