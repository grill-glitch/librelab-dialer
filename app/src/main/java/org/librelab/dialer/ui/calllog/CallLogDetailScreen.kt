package org.librelab.dialer.ui.calllog

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Voicemail
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.librelab.dialer.data.TelecomAdapter
import org.librelab.dialer.domain.model.CallLogGroup
import org.librelab.dialer.domain.model.CallType
import java.text.SimpleDateFormat
import java.util.*

/**
 * Detail screen for a call log group.
 * Shows all entries in the group with timestamps and durations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CallLogDetailViewModel = hiltViewModel(),
) {
    val group by viewModel.callLogGroup.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("通话详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        group?.let { g ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
            ) {
                // Contact header
                item {
                    ContactHeader(
                        name = g.displayName,
                        displayNumber = g.displayNumber,
                        contact = g.contact,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Action buttons
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        ActionButton(
                            icon = Icons.Outlined.Call,
                            label = "拨打电话",
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${g.displayNumber}")
                                }
                                context.startActivity(intent)
                            },
                        )
                        ActionButton(
                            icon = Icons.AutoMirrored.Outlined.Message,
                            label = "发送短信",
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("sms:${g.displayNumber}")
                                }
                                context.startActivity(intent)
                            },
                        )
                        ActionButton(
                            icon = Icons.Filled.Block,
                            label = "屏蔽",
                            onClick = { /* TODO: block */ },
                        )
                        ActionButton(
                            icon = Icons.Filled.Delete,
                            label = "删除",
                            onClick = { viewModel.deleteGroup(); onBack() },
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // All entries in group
                items(g.entries) { entry ->
                    CallLogEntryRow(entry = entry)
                }
            }
        } ?: run {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun ContactHeader(
    name: String,
    displayNumber: String,
    contact: org.librelab.dialer.domain.model.Contact?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(avatarColorFor(name)),
            contentAlignment = Alignment.Center,
        ) {
            if (contact?.photoUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(contact.photoUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Contact photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = name.ifEmpty { displayNumber },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
        )
        if (name.isNotEmpty()) {
            Text(
                text = displayNumber,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CallLogEntryRow(entry: org.librelab.dialer.domain.model.CallLogEntry) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(callTypeColor(entry.callType).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = callTypeIcon(entry.callType),
                contentDescription = null,
                tint = callTypeColor(entry.callType),
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateFormat.format(Date(entry.timestamp)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = callTypeLabel(entry.callType, entry.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = formatDuration(entry.duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}

private fun callTypeIcon(type: CallType) = when (type) {
    CallType.INCOMING -> Icons.Filled.CallReceived
    CallType.OUTGOING -> Icons.Filled.CallMade
    CallType.MISSED -> Icons.Filled.CallMissed
    CallType.REJECTED -> Icons.Filled.CallMissed
    CallType.VOICEMAIL -> Icons.Outlined.Voicemail
    else -> Icons.Outlined.Call
}

private fun callTypeColor(type: CallType) = when (type) {
    CallType.INCOMING -> Color(0xFF4CAF50)
    CallType.OUTGOING -> Color(0xFF4CAF50)
    CallType.MISSED -> Color(0xFFF44336)
    CallType.REJECTED -> Color(0xFFF44336)
    CallType.VOICEMAIL -> Color(0xFF9E9E9E)
    else -> Color(0xFF9E9E9E)
}

private fun callTypeLabel(type: CallType, duration: Long): String = when (type) {
    CallType.INCOMING -> "接入"
    CallType.OUTGOING -> "拨出"
    CallType.MISSED -> "未接"
    CallType.REJECTED -> "已拒绝"
    CallType.VOICEMAIL -> "语音邮件"
    else -> "未知"
}

private fun formatDuration(seconds: Long): String = when {
    seconds <= 0 -> ""
    seconds < 60 -> "${seconds}秒"
    seconds < 3600 -> "${seconds / 60}分${seconds % 60}秒"
    else -> "${seconds / 3600}小时${(seconds % 3600) / 60}分"
}

private fun avatarColorFor(name: String): Color {
    val colors = listOf(
        Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF5350),
        Color(0xFFAB47BC), Color(0xFF42A5F5), Color(0xFFEC407A),
        Color(0xFF7E57C2), Color(0xFF66BB6A),
    )
    return colors[name.hashCode().let { if (it < 0) -it else it } % colors.size]
}
