package org.librelab.dialer.ui.calllog

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import org.librelab.dialer.data.TelecomAdapter
import org.librelab.dialer.domain.model.CallLogGroup
import org.librelab.dialer.domain.model.CallType
import java.text.SimpleDateFormat
import java.util.*

/**
 * Compose CallLog list with internal tabs (全部 / 未接电话 / 统计).
 *
 * Layout:
 *  - MainActivity's Scaffold provides the global top bar
 *  - CallLogScreen renders its own TabRow + HorizontalPager
 *  - Each pager page is a filtered list of call log entries
 *  - Swipe left/right between pages; tap tab to jump
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogScreen(
    viewModel: CallLogViewModel = hiltViewModel(),
    telecomAdapter: TelecomAdapter,
    onCallDetails: (CallLogGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups by viewModel.callLogGroups.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val accentColor = Color(0xFF4DD0E1) // stock crDroid cyan accent
    val tabs = listOf("全部", "未接电话", "统计")
    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
    val scope = rememberCoroutineScope()
    var selectedGroup by remember { mutableStateOf<CallLogGroup?>(null) }

    // Sync pager position with tab selection
    val currentPage = pagerState.currentPage

    Column(modifier = modifier.fillMaxSize()) {
        // TabRow — matches stock calllog style
        TabRow(
            selectedTabIndex = currentPage,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = accentColor,
            indicator = { tabPositions ->
                if (currentPage < tabPositions.size) {
                    val pos = tabPositions[currentPage]
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .wrapContentSize(Alignment.BottomStart)
                            .offset(x = pos.left)
                            .width(pos.width)
                            .height(3.dp)
                            .background(accentColor)
                    )
                }
            },
            divider = {},
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = currentPage == index,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (currentPage == index) FontWeight.Medium else FontWeight.Normal,
                            color = if (currentPage == index) accentColor
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }

        // HorizontalPager — swipe between tab pages
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val pageGroups = when (page) {
                1 -> groups.filter { group ->
                    group.entries.any { it.callType == CallType.MISSED }
                }
                else -> groups
            }

            CallLogPageContent(
                groups = pageGroups,
                isLoading = isLoading,
                isStatsPage = page == 2,
                allGroups = groups,
                onItemClick = { selectedGroup = it },
                onCall = { telecomAdapter.placeCall(it.displayNumber) },
                onMessage = { number ->
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("sms:$number")
                    }
                    context.startActivity(intent)
                },
            )
        }
    }

    // QuickContact bottom sheet
    selectedGroup?.let { group ->
        QuickContactBottomSheet(
            group = group,
            telecomAdapter = telecomAdapter,
            onDismiss = { selectedGroup = null },
        )
    }
}

@Composable
private fun CallLogPageContent(
    groups: List<CallLogGroup>,
    isLoading: Boolean,
    isStatsPage: Boolean,
    allGroups: List<CallLogGroup>,
    onItemClick: (CallLogGroup) -> Unit,
    onCall: (CallLogGroup) -> Unit,
    onMessage: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            isStatsPage -> {
                StatsContent(allGroups = allGroups, modifier = Modifier.align(Alignment.TopCenter))
            }
            groups.isEmpty() -> {
                EmptyCallLog(modifier = Modifier.align(Alignment.Center))
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    val grouped = groups.groupBy { group ->
                        groupDayBucket(group.entries.firstOrNull()?.timestamp ?: 0L)
                    }

                    grouped.forEach { (bucket, bucketGroups) ->
                        item(key = "header-$bucket") {
                            DayHeader(label = bucket)
                        }
                        items(items = bucketGroups, key = { it.id }) { group ->
                            CallLogItem(
                                group = group,
                                onItemClick = { onItemClick(group) },
                                onCall = { onCall(group) },
                                onMessage = { onMessage(group.displayNumber) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsContent(
    allGroups: List<CallLogGroup>,
    modifier: Modifier = Modifier,
) {
    val all = allGroups.flatMap { it.entries }
    val total = all.size
    val missed = all.count { it.callType == CallType.MISSED }
    val incoming = all.count { it.callType == CallType.INCOMING }
    val outgoing = all.count { it.callType == CallType.OUTGOING }
    val totalDuration = all.filter { it.callType != CallType.MISSED }.sumOf { it.duration }

    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "通话统计",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(16.dp))

        StatRow("总通话", "$total")
        StatRow("未接来电", "$missed")
        StatRow("已接来电", "$incoming")
        StatRow("已拨电话", "$outgoing")
        StatRow("累计时长", formatDuration(totalDuration))
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun groupDayBucket(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    return when {
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "今天"
        cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "昨天"
        diff < 7 * 86400_000 -> {
            SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
        }
        else -> {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
        }
    }
}

@Composable
private fun DayHeader(label: String) {
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickContactBottomSheet(
    group: CallLogGroup,
    telecomAdapter: TelecomAdapter,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (group.contact?.photoUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(group.contact.photoUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(
                            text = group.displayName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = group.displayNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        telecomAdapter.placeCall(group.displayNumber)
                        onDismiss()
                    },
                ) {
                    FilledIconButton(
                        onClick = { },
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(Icons.Outlined.Call, contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("拨号", style = MaterialTheme.typography.labelMedium)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("sms:${group.displayNumber}")
                        }
                        context.startActivity(intent)
                        onDismiss()
                    },
                ) {
                    FilledIconButton(
                        onClick = { },
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Message,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("短信", style = MaterialTheme.typography.labelMedium)
                }

                group.contact?.lookupKey?.let { lookupKey ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            val uri = Uri.withAppendedPath(
                                android.provider.ContactsContract.Contacts.CONTENT_URI,
                                lookupKey
                            )
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            onDismiss()
                        },
                    ) {
                        FilledIconButton(
                            onClick = { },
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("联系人", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CallLogItem(
    group: CallLogGroup,
    onItemClick: () -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit,
) {
    val latest = group.entries.firstOrNull() ?: return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(avatarColorFor(group.displayName)),
            contentAlignment = Alignment.Center,
        ) {
            if (group.contact?.photoUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(group.contact.photoUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Contact photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.displayNumber,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                CallTypeArrow(callType = latest.callType)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatTimestamp(latest.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (latest.callType != CallType.MISSED && latest.duration > 0) {
                    Text(
                        text = " · ${formatDuration(latest.duration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        IconButton(onClick = onCall) {
            Icon(
                imageVector = Icons.Outlined.Call,
                contentDescription = "回拨",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 88.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

@Composable
private fun CallTypeArrow(callType: CallType) {
    val (icon, tint) = when (callType) {
        CallType.INCOMING -> Icons.Filled.CallReceived to Color(0xFF4CAF50)
        CallType.OUTGOING -> Icons.Filled.CallMade to Color(0xFF4CAF50)
        CallType.MISSED -> Icons.Filled.CallMissed to Color(0xFFF44336)
        CallType.REJECTED -> Icons.Filled.CallMissed to Color(0xFFF44336)
        CallType.VOICEMAIL -> Icons.Outlined.Voicemail to Color(0xFF9E9E9E)
        else -> Icons.Outlined.Call to Color(0xFF9E9E9E)
    }
    Icon(
        imageVector = icon,
        contentDescription = callType.name,
        modifier = Modifier.size(16.dp),
        tint = tint,
    )
}

@Composable
private fun EmptyCallLog(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.History,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "暂无通话记录",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun avatarColorFor(name: String): Color {
    val palette = listOf(
        Color(0xFF7B8FA1),
        Color(0xFF78909C),
        Color(0xFF8D6E63),
        Color(0xFF7E57C2),
        Color(0xFF26A69A),
        Color(0xFFEF6C00),
        Color(0xFFEC407A),
        Color(0xFF5C6BC0),
    )
    val hash = name.hashCode().toUInt().toInt()
    return palette[(hash and 0x7FFFFFFF) % palette.size]
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()

    return when {
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
        diff < 86400_000 -> "昨天"
        diff < 7 * 86400_000 -> {
            SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
        }
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun formatDuration(seconds: Long): String = when {
    seconds < 60 -> "${seconds}秒"
    seconds < 3600 -> "${seconds / 60}分${seconds % 60}秒"
    else -> "${seconds / 3600}时${(seconds % 3600) / 60}分"
}