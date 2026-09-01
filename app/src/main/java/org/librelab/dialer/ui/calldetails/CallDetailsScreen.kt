package org.librelab.dialer.ui.calldetails

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.NetworkCell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * CallDetailsScreen — shows per-call metadata: location, carrier, SIM, timestamps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallDetailsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CallDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
        if (state == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        val entry = state!!
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                DetailSection(title = "基本信息") {
                    DetailRow(icon = Icons.Filled.Person, label = "号码", value = entry.number)
                    DetailRow(icon = Icons.Filled.Call, label = "类型", value = callTypeLabel(entry.callType))
                    DetailRow(icon = Icons.Filled.Schedule, label = "时长", value = formatDuration(entry.duration))
                    entry.geocodedLocation?.let {
                        DetailRow(icon = Icons.Filled.LocationOn, label = "位置", value = it)
                    }
                    entry.countryIso?.let {
                        DetailRow(icon = Icons.Outlined.NetworkCell, label = "国家", value = it)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                DetailSection(title = "通话记录") {
                    DetailRow(label = "通话时间", value = formatTimestamp(entry.timestamp))
                    DetailRow(label = "SIM卡", value = "SIM ${entry.simId + 1}")
                }
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector? = null,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun callTypeLabel(type: org.librelab.dialer.domain.model.CallType) = when (type) {
    org.librelab.dialer.domain.model.CallType.INCOMING -> "已接听"
    org.librelab.dialer.domain.model.CallType.OUTGOING -> "已拨出"
    org.librelab.dialer.domain.model.CallType.MISSED -> "未接来电"
    org.librelab.dialer.domain.model.CallType.REJECTED -> "已拒绝"
    org.librelab.dialer.domain.model.CallType.VOICEMAIL -> "语音邮件"
    else -> "未知"
}

private fun formatDuration(seconds: Long): String = when {
    seconds <= 0 -> "0秒"
    seconds < 60 -> "${seconds}秒"
    seconds < 3600 -> "${seconds / 60}分${seconds % 60}秒"
    else -> "${seconds / 3600}小时${(seconds % 3600) / 60}分"
}

private fun formatTimestamp(ts: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(ts))
}

// ── ViewModel ──────────────────────────────────────────────────────────────────

data class CallDetailEntry(
    val number: String,
    val callType: org.librelab.dialer.domain.model.CallType,
    val timestamp: Long,
    val duration: Long,
    val simId: Int,
    val geocodedLocation: String?,
    val countryIso: String?,
)

@HiltViewModel
class CallDetailsViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = kotlinx.coroutines.flow.MutableStateFlow<CallDetailEntry?>(null)
    val state: kotlinx.coroutines.flow.StateFlow<CallDetailEntry?> = _state

    init {
        val number = savedStateHandle.get<String>("number")
        val timestamp = savedStateHandle.get<Long>("timestamp") ?: 0L
        if (number != null) {
            load(number, timestamp)
        }
    }

    private fun load(number: String, timestamp: Long) {
        val cr = context.contentResolver
        val cursor = cr.query(
            android.provider.CallLog.Calls.CONTENT_URI,
            arrayOf(
                android.provider.CallLog.Calls._ID,
                android.provider.CallLog.Calls.NUMBER,
                android.provider.CallLog.Calls.DATE,
                android.provider.CallLog.Calls.TYPE,
                android.provider.CallLog.Calls.DURATION,
                android.provider.CallLog.Calls.GEOCODED_LOCATION,
                android.provider.CallLog.Calls.COUNTRY_ISO,
            ),
            "${android.provider.CallLog.Calls.NUMBER} = ? AND ${android.provider.CallLog.Calls.DATE} = ?",
            arrayOf(number, timestamp.toString()),
            null,
        )
        cursor?.use {
            if (it.moveToFirst()) {
                _state.value = CallDetailEntry(
                    number = it.getString(1) ?: "",
                    callType = org.librelab.dialer.domain.model.CallType.fromRaw(it.getInt(3)),
                    timestamp = it.getLong(2),
                    duration = it.getLong(4),
                    simId = 0,
                    geocodedLocation = it.getString(5),
                    countryIso = it.getString(6),
                )
            }
        }
    }
}
