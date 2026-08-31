package org.librelab.dialer.postcall

import android.content.Context
import android.content.SharedPreferences
import android.telephony.TelephonyManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PostCallManager — Kotlin reimplementation of crDroid's PostCall.java.
 *
 * Detects when a short call has ended and prompts the user to send an SMS.
 *
 * Conditions for prompt (mirrors crDroid):
 * - Feature is enabled in settings
 * - SIM is ready
 * - Call disconnected within 30 seconds of ending
 * - Call lasted ≤ 35 seconds (or was unanswered)
 * - User pressed the disconnect button (not system timeout)
 * - The number is not a "sensitive" number (emergency/voicemail/etc.)
 *
 * State is persisted to SharedPreferences so it survives Activity restarts.
 */
@Singleton
class PostCallManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val KEY_DISCONNECT_TIME = "post_call_call_disconnect_time"
        private const val KEY_CONNECT_TIME = "post_call_call_connect_time"
        private const val KEY_NUMBER = "post_call_call_number"
        private const val KEY_MESSAGE_SENT = "post_call_message_sent"
        private const val KEY_DISCONNECT_PRESSED = "post_call_disconnect_pressed"

        private const val WINDOW_AFTER_DISCONNECT_MS = 30_000L
        private const val MAX_CALL_DURATION_MS = 35_000L
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("post_call_prefs", Context.MODE_PRIVATE)
    }

    /**
     * Called by InCallService when the user presses the disconnect button.
     */
    fun onDisconnectPressed() {
        prefs.edit().putBoolean(KEY_DISCONNECT_PRESSED, true).apply()
    }

    /**
     * Called by InCallService when the call is disconnected.
     *
     * @param number the phone number
     * @param callConnectedMillis the wall-clock time when the call connected (0 = no connect)
     */
    fun onCallDisconnected(number: String?, callConnectedMillis: Long) {
        prefs.edit()
            .putLong(KEY_CONNECT_TIME, callConnectedMillis)
            .putLong(KEY_DISCONNECT_TIME, System.currentTimeMillis())
            .putString(KEY_NUMBER, number)
            .apply()
    }

    /**
     * Called when a message has been sent to the number.
     */
    fun onMessageSent(number: String?) {
        prefs.edit()
            .putString(KEY_NUMBER, number)
            .putBoolean(KEY_MESSAGE_SENT, true)
            .apply()
    }

    /**
     * Returns the post-call state for UI consumption.
     */
    fun getPostCallState(): PostCallState {
        val disconnectTime = prefs.getLong(KEY_DISCONNECT_TIME, -1)
        val connectTime = prefs.getLong(KEY_CONNECT_TIME, -1)
        val number = prefs.getString(KEY_NUMBER, null)
        val messageSent = prefs.getBoolean(KEY_MESSAGE_SENT, false)
        val disconnectPressed = prefs.getBoolean(KEY_DISCONNECT_PRESSED, false)

        val timeSinceDisconnect = if (disconnectTime != -1L) {
            System.currentTimeMillis() - disconnectTime
        } else {
            -1L
        }

        val callDuration = if (disconnectTime != -1L && connectTime != -1L && connectTime > 0) {
            disconnectTime - connectTime
        } else {
            -1L
        }

        return PostCallState(
            number = number,
            messageSent = messageSent,
            shouldPromptSend = shouldPromptSendMessage(
                disconnectTime = disconnectTime,
                connectTime = connectTime,
                number = number,
                disconnectPressed = disconnectPressed,
            ),
            shouldPromptViewSent = messageSent,
            timeSinceDisconnect = timeSinceDisconnect,
            callDuration = callDuration,
        )
    }

    /**
     * Clear all post-call state.
     */
    fun clear() {
        prefs.edit()
            .remove(KEY_DISCONNECT_TIME)
            .remove(KEY_CONNECT_TIME)
            .remove(KEY_NUMBER)
            .remove(KEY_MESSAGE_SENT)
            .remove(KEY_DISCONNECT_PRESSED)
            .apply()
    }

    /**
     * Whether the feature is enabled in settings.
     */
    fun isEnabled(): Boolean {
        // Reads from the app's shared settings
        val settingsPrefs = context.getSharedPreferences("libredialer_settings", Context.MODE_PRIVATE)
        return settingsPrefs.getBoolean("post_call_enabled", true)
    }

    private fun shouldPromptSendMessage(
        disconnectTime: Long,
        connectTime: Long,
        number: String?,
        disconnectPressed: Boolean,
    ): Boolean {
        if (!isEnabled()) return false
        if (disconnectTime == -1L) return false
        if (connectTime == -1L) return false
        if (number == null) return false
        if (!disconnectPressed) return false
        if (!isSimReady()) return false

        val timeSinceDisconnect = System.currentTimeMillis() - disconnectTime
        val callDuration = disconnectTime - connectTime

        // Check within disconnect window
        if (timeSinceDisconnect > WINDOW_AFTER_DISCONNECT_MS) return false

        // Check short call (or unanswered)
        if (connectTime > 0 && callDuration > MAX_CALL_DURATION_MS) return false

        // Check not a sensitive number (emergency / voicemail / shortcodes)
        if (isSensitiveNumber(number)) return false

        return true
    }

    private fun isSimReady(): Boolean {
        val tm = context.getSystemService<TelephonyManager>() ?: return false
        return tm.simState == TelephonyManager.SIM_STATE_READY
    }

    /**
     * Whether a number is "sensitive" — should not prompt for SMS.
     * Emergency, voicemail, and short service codes are sensitive.
     */
    private fun isSensitiveNumber(number: String): Boolean {
        if (number.isEmpty()) return true

        // Emergency numbers
        if (number == "112" || number == "911" || number == "110" || number == "120" || number == "999") {
            return true
        }

        // Voicemail
        if (number == "100" || number == "1" || number == "99") {
            return true
        }

        // Short codes (typically 3 digits or less for service codes)
        if (number.length <= 3 && number.all { it.isDigit() }) {
            return true
        }

        return false
    }

    data class PostCallState(
        val number: String?,
        val messageSent: Boolean,
        val shouldPromptSend: Boolean,
        val shouldPromptViewSent: Boolean,
        val timeSinceDisconnect: Long,
        val callDuration: Long,
    )
}
