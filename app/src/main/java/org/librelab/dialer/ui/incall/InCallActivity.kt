package org.librelab.dialer.ui.incall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import org.librelab.dialer.ui.theme.LibreDialerTheme

/**
 * InCallActivity — full-screen in-call UI entry point.
 * Replaces InCallActivity.java with Compose.
 *
 * The actual call state is driven by InCallService (Java stub) which writes
 * to CallManager — the UI just observes the StateFlow.
 */
@AndroidEntryPoint
class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // enableEdgeToEdge()
        setContent {
            LibreDialerTheme {
                InCallScreen(
                    onDismiss = { finish() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
