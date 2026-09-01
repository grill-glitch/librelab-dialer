package org.librelab.dialer.ui.calllog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import org.librelab.dialer.data.TelecomAdapter
import org.librelab.dialer.ui.theme.LibreDialerTheme

/**
 * CallLogDetailActivity — shows detailed call log entry for a specific call group.
 * Launched from QuickContactBottomSheet or external intent.
 */
@AndroidEntryPoint
class CallLogDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LibreDialerTheme {
                CallLogDetailScreen(
                    onBack = { finish() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
