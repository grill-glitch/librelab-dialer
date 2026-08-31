package org.librelab.dialer.data

import android.content.Context
import android.provider.VoicemailContract
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Determines whether the voicemail tab should be shown.
 * Migrated from OldMainActivityPeer.canVoicemailTabBeShown().
 */
@Singleton
class VoicemailAvailability @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isVoicemailTabShown(): Boolean {
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            ?: return false

        @Suppress("DEPRECATION")
        val account = try {
            telecom.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_VOICEMAIL)
        } catch (_: Exception) {
            null
        }

        // Check for unread voicemails (proxy for "voicemail exists")
        return try {
            val cursor = context.contentResolver.query(
                VoicemailContract.Voicemails.CONTENT_URI,
                arrayOf(VoicemailContract.Voicemails._ID),
                null,
                null,
                null,
            )
            cursor?.use { it.count > 0 } ?: false
        } catch (_: SecurityException) {
            false
        }
    }
}