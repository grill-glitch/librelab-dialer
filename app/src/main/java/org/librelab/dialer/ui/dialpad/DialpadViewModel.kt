package org.librelab.dialer.ui.dialpad

import android.media.AudioManager
import android.media.ToneGenerator
import android.telephony.PhoneNumberUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.librelab.dialer.ui.settings.SettingsPrefs
import javax.inject.Inject

/**
 * Dialpad ViewModel — manages digit input, DTMF tones, and call placement.
 * Core logic migrated from DialpadFragment.java and DialpadView.java.
 */
@HiltViewModel
class DialpadViewModel @Inject constructor(
    private val settingsPrefs: SettingsPrefs,
) : ViewModel() {

    // DTMF tone duration in ms — 120ms short, 250ms long (matches crDroid)
    companion object {
        const val DTMF_TONE_DURATION_SHORT_MS = 120L
        const val DTMF_TONE_DURATION_LONG_MS = 250L
    }

    private val _digits = MutableStateFlow("")
    val digits: StateFlow<String> = _digits.asStateFlow()

    private val _isOverflown = MutableStateFlow(false)
    val isOverflown: StateFlow<Boolean> = _isOverflown.asStateFlow()

    private val _showKeypad = MutableStateFlow(true)
    val showKeypad: StateFlow<Boolean> = _showKeypad.asStateFlow()

    private var toneGenerator: ToneGenerator? = null
    private var lastToneTime = 0L

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_DTMF, 80)
        } catch (e: Exception) {
            // ToneGenerator can fail if audio focus is not available
        }
    }

    /**
     * Dialpad keys mapping — matches DialpadCharMappings.java
     */
    val dialpadKeys = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('*', '0', '#'),
    )

    /**
     * Letter mappings for each digit key — matches DialpadCharMappings.java
     */
    val letterMappings = mapOf(
        '2' to "ABC",
        '3' to "DEF",
        '4' to "GHI",
        '5' to "JKL",
        '6' to "MNO",
        '7' to "PQRS",
        '8' to "TUV",
        '9' to "WXYZ",
        '0' to "+",
        '*' to "",
        '#' to "",
    )

    /**
     * Called when a digit key is pressed.
     */
    fun onDigitKey(digit: Char) {
        val current = _digits.value
        val newDigits = current + digit
        _digits.value = newDigits
        _isOverflown.value = newDigits.length > 20
        playDTMF(digit)
    }

    /**
     * Called when backspace/delete is pressed.
     */
    fun onBackspace() {
        val current = _digits.value
        if (current.isNotEmpty()) {
            _digits.value = current.dropLast(1)
            _isOverflown.value = false
        }
    }

    /**
     * Called when long-press backspace to clear all.
     */
    fun onBackspaceLongPress() {
        _digits.value = ""
        _isOverflown.value = false
    }

    /**
     * Toggle keypad visibility.
     */
    fun appendDigit(digit: Char) = onDigitKey(digit)
    fun deleteLastDigit() = onBackspace()
    fun clearDigits() { _digits.value = "" }
    fun toggleKeypad() {
        _showKeypad.value = !_showKeypad.value
    }

    val state: StateFlow<DialpadState> = combine(_digits, _isOverflown, _showKeypad) { digits, isOverflown, showKeypad ->
        DialpadState(digits = digits, isOverflown = isOverflown, showKeypad = showKeypad)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DialpadState())

    data class DialpadState(
        val digits: String = "",
        val isOverflown: Boolean = false,
        val showKeypad: Boolean = true,
    )

    /**
     * Format the display number — for visual display only.
     * Uses the same formatting logic as DialpadFragment.
     */
    fun formatDisplayNumber(digits: String): String {
        if (digits.isEmpty()) return ""
        // Simple formatting: add spaces every 3-4 digits
        // The real formatting uses libphonenumber in production
        return PhoneNumberUtils.formatNumber(digits, null as String?)
            ?: digits
    }

    /**
     * Check if the current digits represent an emergency number.
     */
    fun isEmergency(): Boolean {
        return PhoneNumberUtils.isEmergencyNumber(_digits.value)
    }

    /**
     * Check if the current digits represent a voicemail number.
     */
    fun isVoicemail(): Boolean {
        return _digits.value == "100" || _digits.value == "1" || _digits.value == ""
    }

    private fun playDTMF(digit: Char) {
        // Respect user setting: skip if DTMF tones are disabled
        if (!settingsPrefs.getBoolean("dtmf_tone_enabled", true)) return

        val tone = when (digit) {
            '1' -> ToneGenerator.TONE_DTMF_1
            '2' -> ToneGenerator.TONE_DTMF_2
            '3' -> ToneGenerator.TONE_DTMF_3
            '4' -> ToneGenerator.TONE_DTMF_4
            '5' -> ToneGenerator.TONE_DTMF_5
            '6' -> ToneGenerator.TONE_DTMF_6
            '7' -> ToneGenerator.TONE_DTMF_7
            '8' -> ToneGenerator.TONE_DTMF_8
            '9' -> ToneGenerator.TONE_DTMF_9
            '0' -> ToneGenerator.TONE_DTMF_0
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> ToneGenerator.TONE_DTMF_1
        }

        val duration = if (settingsPrefs.getInt("dtmf_tone_length", 0) == 0) {
            DTMF_TONE_DURATION_SHORT_MS
        } else {
            DTMF_TONE_DURATION_LONG_MS
        }

        val now = System.currentTimeMillis()
        if (now - lastToneTime > duration) {
            toneGenerator?.startTone(tone, duration.toInt())
            lastToneTime = now
        }
    }

    override fun onCleared() {
        super.onCleared()
        toneGenerator?.release()
        toneGenerator = null
    }
}
