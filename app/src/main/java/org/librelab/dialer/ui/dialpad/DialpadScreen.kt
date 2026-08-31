package org.librelab.dialer.ui.dialpad

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Backspace
import com.composables.icons.materialsymbols.outlined.Call
import org.librelab.dialer.data.TelecomAdapter

/**
 * Dialpad screen — mimics crDroid DialpadFragment layout.
 * Key specs from crDroid resources:
 *   - Key height: 64dp
 *   - Number size: 36sp
 *   - Letter size: 12sp
 *   - Bottom space: 80dp
 *   - Digits area height: 60dp
 */
@Composable
fun DialpadScreen(
    telecomAdapter: TelecomAdapter,
    modifier: Modifier = Modifier,
    viewModel: DialpadViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Digits display area — matches crDroid dialpad_digits_height=60dp
        DialpadDisplay(
            digits = state.digits,
            hasCallAbility = telecomAdapter.hasCallAbility(),
            onCall = {
                if (state.digits.isNotEmpty()) {
                    telecomAdapter.placeCall(state.digits)
                }
            },
            onDelete = { viewModel.deleteLastDigit() },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 12-key grid — matches crDroid dialpad_key_height=64dp
        DialpadGrid(
            onDigit = { digit ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.appendDigit(digit)
            },
            onStar = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.appendDigit('*')
            },
            onPound = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.appendDigit('#')
            },
            onBackspace = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.deleteLastDigit()
            },
            onCall = {
                if (state.digits.isNotEmpty()) {
                    telecomAdapter.placeCall(state.digits)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        // Bottom space — matches crDroid dialpad_bottom_space_height=80dp
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun DialpadDisplay(
    digits: String,
    hasCallAbility: Boolean,
    onCall: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = digits.ifEmpty { "" },
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 34.sp),
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )

        // Backspace button
        if (digits.isNotEmpty()) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Backspace,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Call button
        FilledIconButton(
            onClick = onCall,
            enabled = hasCallAbility && digits.isNotEmpty(),
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                imageVector = MaterialSymbols.Outlined.Call,
                contentDescription = "Call",
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * 12-key dialpad grid matching crDroid dialpad layout.
 * Keys: 1-9, *, 0, #
 * Each key shows digit (36sp) and letters (12sp) below.
 */
@Composable
private fun DialpadGrid(
    onDigit: (Char) -> Unit,
    onStar: () -> Unit,
    onPound: () -> Unit,
    onBackspace: () -> Unit,
    onCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Key layout: 3 columns × 4 rows
    // Row 1: 1  2  3
    // Row 2: 4  5  6
    // Row 3: 7  8  9
    // Row 4: *  0  #

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        // Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            DialpadKey(digit = '1', letters = "", onClick = { onDigit('1') })
            DialpadKey(digit = '2', letters = "ABC", onClick = { onDigit('2') })
            DialpadKey(digit = '3', letters = "DEF", onClick = { onDigit('3') })
        }

        // Row 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            DialpadKey(digit = '4', letters = "GHI", onClick = { onDigit('4') })
            DialpadKey(digit = '5', letters = "JKL", onClick = { onDigit('5') })
            DialpadKey(digit = '6', letters = "MNO", onClick = { onDigit('6') })
        }

        // Row 3
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            DialpadKey(digit = '7', letters = "PQRS", onClick = { onDigit('7') })
            DialpadKey(digit = '8', letters = "TUV", onClick = { onDigit('8') })
            DialpadKey(digit = '9', letters = "WXYZ", onClick = { onDigit('9') })
        }

        // Row 4: *  0  #
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // Star key
            Box(
                modifier = Modifier
                    .size(72.dp, 64.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .clickable(onClick = onStar),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✱",
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // 0 key (adds + or dial)
            Box(
                modifier = Modifier
                    .size(72.dp, 64.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .clickable(onClick = { onDigit('0') }),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "0",
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp),
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Pound key
            Box(
                modifier = Modifier
                    .size(72.dp, 64.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .clickable(onClick = onPound),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "#",
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 23.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * Individual dialpad key — matches crDroid dialpad_key.xml specs:
 *   - Number: 36sp, normal weight
 *   - Letters: 12sp (single alphabet) / 10sp (dual alphabet)
 */
@Composable
private fun DialpadKey(
    digit: Char,
    letters: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(72.dp, 64.dp)
            .clip(RoundedCornerShape(36.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit.toString(),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp),
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
