package org.librelab.dialer

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * LibreDialer Application class.
 * All system services (InCallService, CallScreeningService, etc.) are started from here.
 */
@HiltAndroidApp
class LibreDialerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Hilt handles dependency graph — no manual service registration needed.
        // Telecom system binds to our service stubs declared in AndroidManifest.
    }
}
