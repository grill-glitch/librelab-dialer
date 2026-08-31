package org.librelab.dialer

import android.app.Application
import android.content.Intent
import dagger.hilt.android.HiltAndroidApp
import org.librelab.dialer.service.PhoneAccountRegistrationService

/**
 * LibreDialer Application class.
 * All system services (InCallService, CallScreeningService, etc.) are started from here.
 */
@HiltAndroidApp
class LibreDialerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Register our PhoneAccount with TelecomManager so we appear in the
        // system default-dialer picker and can place/receive calls.
        startService(Intent(this, PhoneAccountRegistrationService::class.java))
    }
}
