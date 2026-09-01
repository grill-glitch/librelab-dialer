package org.librelab.dialer.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import org.librelab.dialer.ui.theme.LibreDialerTheme

/**
 * DialerSettingsActivity — standalone settings entry point.
 * Launched from app launcher or system settings.
 */
@AndroidEntryPoint
class DialerSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LibreDialerTheme {
                SettingsScreen(
                    onBack = { finish() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
