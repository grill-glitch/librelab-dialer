package org.librelab.dialer.ui.dialpad

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import org.librelab.dialer.data.TelecomAdapter
import org.librelab.dialer.ui.settings.SettingsPrefs
import org.librelab.dialer.ui.theme.LibreDialerTheme
import javax.inject.Inject

private val KEY_SIZE = 80.dp
private val KEY_SPACING = 8.dp
private val DIGIT_SIZE = 32.sp
private val LETTER_SIZE = 10.sp
// Bottom-row icons match the digit key size for visual consistency
private val ICON_KEY_SIZE = KEY_SIZE

// ── Color palette (dynamic Material 3 colors) ────────────────────────────────
private data class DialpadPalette(
    val bg: Color,
    val keyFace: Color,
    val digit: Color,
    val letter: Color,
    val callEnabled: Color,
    val callDisabled: Color,
    val backspace: Color,
    val digitsText: Color,
)

private val LocalDialpadPalette = staticCompositionLocalOf<DialpadPalette> {
    error("DialpadPalette not provided")
}

@Composable
private fun ProvideDialpadPalette(content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val palette = DialpadPalette(
        bg = cs.surfaceContainerHigh,
        keyFace = cs.surfaceContainerHighest,
        digit = cs.onSurface,
        letter = cs.onSurfaceVariant,
        callEnabled = cs.primary,
        callDisabled = cs.surfaceContainerLow,
        backspace = cs.onSurfaceVariant,
        digitsText = cs.onSurface,
    )
    CompositionLocalProvider(LocalDialpadPalette provides palette, content = content)
}

// ── Activity ────────────────────────────────────────────────────────────────
@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
class DialpadActivity : ComponentActivity() {

    @Inject
    lateinit var telecomAdapter: TelecomAdapter

    @Inject
    lateinit var settingsPrefs: SettingsPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Window background matches dark surface — overridden by dynamic theme inside setContent
        window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#1C1B1F"))
        )

        setContent {
            BackHandler(enabled = true) { finish() }

            val viewModel: DialpadViewModel = hiltViewModel()
            val dialpadState by viewModel.state.collectAsState()
            val haptic = LocalHapticFeedback.current

            // Dynamic Material 3 color scheme based on system wallpaper (Android 12+)
            // Falls back to static dark scheme on older devices
            val colorScheme = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (isSystemInDarkTheme()) dynamicDarkColorScheme(this)
                else dynamicLightColorScheme(this)
            } else {
                darkColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
            ProvideDialpadPalette {

            LaunchedEffect(Unit) {
                viewModel.clearDigits()
                // WRAP_CONTENT lets the Compose content (Column wrapContentHeight) determine
                // the actual popup height instead of hardcoding 70% of screen.
                window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                )
                window.setGravity(Gravity.BOTTOM)
            }

            // Fixed-height bottom-aligned popup (~55% screen, like stock crDroid)
            // Window layout params are set in LaunchedEffect below

            // Popup surface — fills window width, wraps content height
            // Window is opaque + bottom-anchored, so this Column IS the popup
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .background(
                        color = LocalDialpadPalette.current.bg,
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    )
                    .padding(horizontal = KEY_SPACING, vertical = KEY_SPACING),
                verticalArrangement = Arrangement.spacedBy(KEY_SPACING),
            ) {
                    DigitsDisplay(digits = dialpadState.digits)

                    KeyRow(keys = listOf("1" to "", "2" to "ABC", "3" to "DEF")) { digit ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.appendDigit(digit.first())
                    }
                    KeyRow(keys = listOf("4" to "GHI", "5" to "JKL", "6" to "MNO")) { digit ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.appendDigit(digit.first())
                    }
                    KeyRow(keys = listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ")) { digit ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.appendDigit(digit.first())
                    }
                    KeyRow(keys = listOf("*" to "", "0" to "+", "#" to "")) { digit ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.appendDigit(digit.first())
                    }
                    BottomRow(
                        onDelete = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.deleteLastDigit()
                        },
                        onCall = {
                            val digits = viewModel.state.value.digits
                            if (digits.isNotEmpty()) {
                                val assistedEnabled = settingsPrefs.getBoolean("assisted_dialing_enabled", true)
                                val assistedCountry = settingsPrefs.getInt("assisted_dialing_country", 0)
                                telecomAdapter.placeCall(
                                    number = digits,
                                    assistedDialingEnabled = assistedEnabled,
                                    assistedDialingCountryIndex = assistedCountry,
                                )
                            }
                        },
                    )
            }
            } // MaterialTheme
            }
        }
    }
}

// ── Digits display ────────────────────────────────────────────────────────────
@Composable
private fun DigitsDisplay(digits: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = digits.ifEmpty { " " },
            color = LocalDialpadPalette.current.digitsText,
            fontSize = 36.sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = KEY_SPACING),
        )
    }
}

// ── Key row ──────────────────────────────────────────────────────────────────
@Composable
private fun KeyRow(
    keys: List<Pair<String, String>>,
    onKey: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KEY_SPACING, Alignment.CenterHorizontally),
    ) {
        for ((digit, letters) in keys) {
            DialpadKey(digit = digit, letters = letters) { onKey(digit) }
        }
    }
}

// ── Bottom row ────────────────────────────────────────────────────────────────
@Composable
private fun BottomRow(
    onDelete: () -> Unit,
    onCall: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Spacers weighted so Call button is centered in the popup
        // Spacer_left = Spacer_middle + 220 (Backspace) + Spacer_right
        Spacer(modifier = Modifier.weight(13f))
        IconKey(
            backgroundColor = LocalDialpadPalette.current.callEnabled,
            iconTint = Color.Black,
            icon = Icons.Filled.Call,
            contentDescription = "Call",
            onClick = onCall,
        )
        Spacer(modifier = Modifier.weight(4f))
        IconKey(
            backgroundColor = Color.Transparent,
            iconTint = LocalDialpadPalette.current.backspace,
            icon = Icons.Filled.Backspace,
            contentDescription = "Delete",
            onClick = onDelete,
        )
        Spacer(modifier = Modifier.weight(2f))
    }
}

// ── Key button ───────────────────────────────────────────────────────────────
@Composable
private fun DialpadKey(
    digit: String,
    letters: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .size(KEY_SIZE)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = CircleShape,
        color = LocalDialpadPalette.current.keyFace,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = digit,
                    color = LocalDialpadPalette.current.digit,
                    fontSize = DIGIT_SIZE,
                    fontWeight = FontWeight.Medium,
                )
                if (letters.isNotEmpty()) {
                    Text(
                        text = letters,
                        color = LocalDialpadPalette.current.letter,
                        fontSize = LETTER_SIZE,
                        fontWeight = FontWeight.Light,
                    )
                }
            }
        }
    }
}

// ── Icon button ──────────────────────────────────────────────────────────────
@Composable
private fun IconKey(
    backgroundColor: Color,
    iconTint: Color,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .size(ICON_KEY_SIZE)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = CircleShape,
        color = backgroundColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}
