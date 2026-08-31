package org.librelab.dialer.service

import android.content.Context
import android.os.Build
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * PhoneAccountRegistrationService — registers the LibreDialer PhoneAccount
 * with the system at startup. Replaces PhoneAccountRegistrar.java.
 */
@AndroidEntryPoint
class PhoneAccountRegistrationService : android.app.Service() {

    @Inject
    @dagger.hilt.android.qualifiers.ApplicationContext
    lateinit var context: Context

    private val telecomManager: TelecomManager by lazy {
        context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    }

    override fun onBind(intent: android.content.Intent?): android.os.IBinder? = null

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        registerPhoneAccount()
        return START_NOT_STICKY
    }

    private fun registerPhoneAccount() {
        val componentName = android.content.ComponentName(context, InCallServiceImpl::class.java)
        val handle = PhoneAccountHandle(componentName, "librelab_default")

        val builder = PhoneAccount.builder(handle, "LibreDialer")
            .setCapabilities(
                PhoneAccount.CAPABILITY_SELF_MANAGED or
                    PhoneAccount.CAPABILITY_VIDEO_CALLING or
                    PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING,
            )
            .setShortDescription("LibreDialer")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setSupportedUriSchemes(listOf("tel"))
        } else {
            @Suppress("DEPRECATION")
            builder.setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
        }

        try {
            telecomManager.registerPhoneAccount(builder.build())
        } catch (_: SecurityException) {
            // Permission not granted — caller should not register
        }
    }
}
