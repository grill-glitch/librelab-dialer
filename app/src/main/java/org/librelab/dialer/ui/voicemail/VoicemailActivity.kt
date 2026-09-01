package org.librelab.dialer.ui.voicemail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import org.librelab.dialer.ui.theme.LibreDialerTheme

/**
 * VoicemailActivity — standalone voicemail screen entry point.
 */
@AndroidEntryPoint
class VoicemailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LibreDialerTheme {
                VoicemailScreen(
                    onBack = { finish() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
