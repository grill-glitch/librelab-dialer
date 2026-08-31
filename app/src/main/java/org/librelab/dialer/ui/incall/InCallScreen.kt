package org.librelab.dialer.ui.incall

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.librelab.dialer.domain.model.CallInfo
import org.librelab.dialer.domain.model.CallState
import org.librelab.dialer.postcall.PostCallManager
import java.util.concurrent.TimeUnit

/**
 * InCallScreen — full-screen in-call UI.
 * Replaces InCallActivity.java + InCallFragment.java with Compose.
 *
 * Layout migrated from activity_call.xml:
 * - Top: contact photo + name + number + state + duration
 * - Middle: secondary calls list (held calls, conference)
 * - Bottom: action buttons (mute / speaker / hold / dialpad / add / merge / end)
 */
@Composable
fun InCallScreen(
    viewModel: InCallViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val foregroundCall by viewModel.foregroundCall.collectAsStateWithLifecycle()
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val hasActiveCall by viewModel.hasActiveCall.collectAsStateWithLifecycle()
    val showDialpad by viewModel.showDialpad.collectAsStateWithLifecycle()
    val dialpadDigits by viewModel.dialpadDigits.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // ── Main content column: does NOT fill height, so overlay can sit at bottom ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Top),
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Top contact area
            ContactHeader(
                call = foregroundCall,
                onClose = onDismiss,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            // Fills remaining space above OtherCallsList and ActionButtons,
            // but does NOT push the overlay dialpad — dialpad is now in a separate overlay layer.
            Spacer(modifier = Modifier.weight(1f, fill = false))

            OtherCallsList(calls = calls, modifier = Modifier.fillMaxWidth())

            // Action buttons (only when dialpad is hidden)
            if (!showDialpad) {
                CallActionButtons(
                    foregroundCall = foregroundCall,
                    onMute = viewModel::toggleMute,
                    onSpeaker = { viewModel.setAudioRoute(android.telecom.CallAudioState.ROUTE_SPEAKER) },
                    onHold = viewModel::toggleHold,
                    onDialpad = viewModel::toggleDialpad,
                    onAdd = { /* TODO: launch second call */ },
                    onEnd = viewModel::endCall,
                    onAnswer = viewModel::answerCall,
                    onReject = viewModel::rejectCall,
                )
            }
        }

        // ── Dialpad overlay: independent of Column layout, anchored to bottom ──
        AnimatedVisibility(
            visible = showDialpad,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Bottom)
                .align(Alignment.BottomCenter),
        ) {
            InCallDialpad(
                digits = dialpadDigits,
                onDigit = viewModel::onDialpadDigit,
                onClose = viewModel::toggleDialpad,
            )
        }

        // Post-call snackbar (shown after short call ends)
        val postCallState by viewModel.postCallState.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }
        val context = LocalContext.current

        LaunchedEffect(postCallState) {
            viewModel.refreshPostCallState()
            viewModel.refreshContactNames()
            val state = viewModel.postCallState.value
            if (state.shouldPromptSend && state.number != null) {
                val result = snackbarHostState.showSnackbar(
                    message = "Call ended — send a message?",
                    actionLabel = "Send",
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    val uri = android.net.Uri.parse("smsto:${state.number}")
                    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, uri)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        viewModel.dismissPostCall()
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        viewModel.dismissPostCall()
                    }
                } else {
                    viewModel.dismissPostCall()
                }
            } else if (state.shouldPromptViewSent && state.number != null) {
                snackbarHostState.showSnackbar(
                    message = "Message sent",
                    actionLabel = "View",
                    duration = SnackbarDuration.Long,
                )
                viewModel.dismissPostCall()
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

@Composable
private fun ContactHeader(
    call: CallInfo?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Close button at top
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Minimize",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Contact photo or initials
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (call?.photoUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(call.photoUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Contact photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = (call?.displayName?.take(1) ?: call?.number?.take(1) ?: "?")
                        .uppercase(),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Contact name / number
        Text(
            text = call?.displayName?.takeIf { it.isNotEmpty() } ?: call?.number ?: "Unknown",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Call state + duration
        CallStateLabel(call = call)
    }
}

@Composable
private fun CallStateLabel(call: CallInfo?) {
    if (call == null) return
    val stateText = when (call.state) {
        CallState.RINGING -> "Incoming call"
        CallState.DIALING -> "Dialing..."
        CallState.CONNECTING -> "Connecting..."
        CallState.ACTIVE -> "Connected"
        CallState.ON_HOLD -> "On hold"
        CallState.DISCONNECTED -> "Disconnected"
        else -> ""
    }
    Text(
        text = stateText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun OtherCallsList(
    calls: Map<String, CallInfo>,
    modifier: Modifier = Modifier,
) {
    val otherCalls = calls.values.filter {
        it.state == CallState.ON_HOLD || it.state == CallState.CONFERENCE
    }
    if (otherCalls.isEmpty()) return

    Column(modifier = modifier) {
        otherCalls.forEach { call ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Pause,
                    contentDescription = "On hold",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = call.displayName.takeIf { it.isNotEmpty() } ?: call.number,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun CallActionButtons(
    foregroundCall: CallInfo?,
    onMute: () -> Unit,
    onSpeaker: () -> Unit,
    onHold: () -> Unit,
    onDialpad: () -> Unit,
    onAdd: () -> Unit,
    onEnd: () -> Unit,
    onAnswer: () -> Unit,
    onReject: () -> Unit,
) {
    val isRinging = foregroundCall?.state == CallState.RINGING

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isRinging) {
            // Ringing state: only Answer + Decline visible
            CircleActionButton(
                icon = Icons.Outlined.Call,
                label = "Answer",
                onClick = onAnswer,
                background = Color(0xFF4CAF50),
            )
            CircleActionButton(
                icon = Icons.Outlined.CallEnd,
                label = "Decline",
                onClick = onReject,
                background = Color(0xFFF44336),
            )
        } else {
            // Active call: full control layout
            CircleActionButton(
                icon = Icons.Outlined.MicOff,
                label = "Mute",
                onClick = onMute,
                background = MaterialTheme.colorScheme.surfaceVariant,
            )
            CircleActionButton(
                icon = Icons.Outlined.VolumeUp,
                label = "Speaker",
                onClick = onSpeaker,
                background = MaterialTheme.colorScheme.surfaceVariant,
            )
            CircleActionButton(
                icon = Icons.Outlined.Dialpad,
                label = "Keypad",
                onClick = onDialpad,
                background = MaterialTheme.colorScheme.surfaceVariant,
            )
            CircleActionButton(
                icon = Icons.Outlined.PersonAdd,
                label = "Add call",
                onClick = onAdd,
                background = MaterialTheme.colorScheme.surfaceVariant,
            )
            CircleActionButton(
                icon = Icons.Outlined.CallEnd,
                label = "End",
                onClick = onEnd,
                background = Color(0xFFF44336),
            )
        }
    }
}

@Composable
private fun CircleActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    background: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun InCallDialpad(
    digits: String,
    onDigit: (Char) -> Unit,
    onClose: () -> Unit,
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('*', '0', '#'),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(align = Alignment.Top)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        // Digit display
        Text(
            text = digits,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Keypad
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { digit ->
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .clickable { onDigit(digit) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = digit.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Close button
        TextButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Hide keypad")
        }
    }
}
