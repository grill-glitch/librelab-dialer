package org.librelab.dialer.ui.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import org.librelab.dialer.R

// ── State ────────────────────────────────────────────────────────────────────

data class SettingsState(
    // Default dialer
    val isDefaultDialer: Boolean = false,
    // Display options
    val sortOrder: Int = 0,          // 0=given name, 1=family name
    val nameFormat: Int = 0,         // 0=given first, 1=family first
    // Sound & vibration
    val ringtoneUri: String? = null,
    val vibrateOnRing: Boolean = false,
    val dtmfToneEnabled: Boolean = true,
    val dtmfToneLength: Int = 0,     // 0=short, 1=long
    // In-call vibration
    val incallDnd: Boolean = false,
    val incallVibrateOutgoing: Boolean = true,
    val incallVibrateCallWaiting: Boolean = true,
    val incallVibrateHangup: Boolean = true,
    val incallVibrate45: Boolean = false,
    // Call recording
    val recordingFormat: Int = 0,    // 0=ogg, 1=aac, 2=amr
    val recordingAutoStart: Boolean = false,
    // Smart
    val smartMute: Boolean = false,
    // Assisted dialing
    val assistedDialingEnabled: Boolean = true,
    val assistedDialingCountry: Int = 0, // 0=自动/默认, 1+=按国家索引
    // Other
    val postCallEnabled: Boolean = true,
    val proximitySensorDisabled: Boolean = false,
    // Anti-spam
    val antiSpamEnabled: Boolean = true,
    val antiSpamBlockHighRisk: Boolean = true,
    val antiSpamBlockRealEstate: Boolean = false,
    val antiSpamBlockAds: Boolean = false,
    val antiSpamBlockDelivery: Boolean = false,
    val antiSpamBlockLoan: Boolean = false,
    val antiSpamBlockEducation: Boolean = false,
    val antiSpamBlockRepair: Boolean = false,
    val antiSpamBlockInsurance: Boolean = false,
    // Lookup
    val forwardLookup: Boolean = false,
    val forwardLookupProvider: Int = 0,
    val reverseLookup: Boolean = false,
    val reverseLookupProvider: Int = 0,
    // Voicemail
    val visualVoicemail: Boolean = false,
    val vvmAutoArchive: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsPrefs: SettingsPrefs,
) : ViewModel() {

    private val roleManager: RoleManager =
        context.getSystemService(Context.ROLE_SERVICE) as RoleManager

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        refreshDefaultDialer()
    }

    /** Check and update the default-dialer role status. */
    fun refreshDefaultDialer() {
        val isDefault = roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        _state.value = _state.value.copy(isDefaultDialer = isDefault)
    }

    /**
     * Create an intent to request the ROLE_DIALER.
     * Returns null if no activity can handle it.
     */
    fun createRequestDefaultDialerIntent(): Intent? {
        val roleIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
        } else null
        if (roleIntent != null && roleIntent.resolveActivity(context.packageManager) != null) {
            return roleIntent
        }
        val legacy = Intent("android.telecom.action.CHANGE_DEFAULT_DIALER")
            .putExtra("android.telecom.extra.CHANGE_DEFAULT_DIALER_PACKAGE_NAME", context.packageName)
        return legacy.takeIf { it.resolveActivity(context.packageManager) != null }
    }

    fun updateSetting(transform: (SettingsState) -> SettingsState) {
        val newState = transform(_state.value)
        _state.value = newState
        persistState(newState)
    }

    /** Persist every field to SharedPreferences. */
    private fun persistState(s: SettingsState) {
        settingsPrefs.putInt("sort_order", s.sortOrder)
        settingsPrefs.putInt("name_format", s.nameFormat)
        settingsPrefs.putString("ringtone_uri", s.ringtoneUri ?: "")
        settingsPrefs.putBoolean("vibrate_on_ring", s.vibrateOnRing)
        settingsPrefs.putBoolean("dtmf_tone_enabled", s.dtmfToneEnabled)
        settingsPrefs.putInt("dtmf_tone_length", s.dtmfToneLength)
        settingsPrefs.putBoolean("incall_dnd", s.incallDnd)
        settingsPrefs.putBoolean("incall_vibrate_outgoing", s.incallVibrateOutgoing)
        settingsPrefs.putBoolean("incall_vibrate_call_waiting", s.incallVibrateCallWaiting)
        settingsPrefs.putBoolean("incall_vibrate_hangup", s.incallVibrateHangup)
        settingsPrefs.putBoolean("incall_vibrate_45", s.incallVibrate45)
        settingsPrefs.putInt("recording_format", s.recordingFormat)
        settingsPrefs.putBoolean("recording_auto_start", s.recordingAutoStart)
        settingsPrefs.putBoolean("smart_mute", s.smartMute)
        settingsPrefs.putBoolean("assisted_dialing_enabled", s.assistedDialingEnabled)
        settingsPrefs.putInt("assisted_dialing_country", s.assistedDialingCountry)
        settingsPrefs.putBoolean("post_call_enabled", s.postCallEnabled)
        settingsPrefs.putBoolean("proximity_sensor_disabled", s.proximitySensorDisabled)
        settingsPrefs.putBoolean("anti_spam_enabled", s.antiSpamEnabled)
        settingsPrefs.putBoolean("anti_spam_block_high_risk", s.antiSpamBlockHighRisk)
        settingsPrefs.putBoolean("anti_spam_block_real_estate", s.antiSpamBlockRealEstate)
        settingsPrefs.putBoolean("anti_spam_block_ads", s.antiSpamBlockAds)
        settingsPrefs.putBoolean("anti_spam_block_delivery", s.antiSpamBlockDelivery)
        settingsPrefs.putBoolean("anti_spam_block_loan", s.antiSpamBlockLoan)
        settingsPrefs.putBoolean("anti_spam_block_education", s.antiSpamBlockEducation)
        settingsPrefs.putBoolean("anti_spam_block_repair", s.antiSpamBlockRepair)
        settingsPrefs.putBoolean("anti_spam_block_insurance", s.antiSpamBlockInsurance)
        settingsPrefs.putBoolean("forward_lookup", s.forwardLookup)
        settingsPrefs.putInt("forward_lookup_provider", s.forwardLookupProvider)
        settingsPrefs.putBoolean("reverse_lookup", s.reverseLookup)
        settingsPrefs.putInt("reverse_lookup_provider", s.reverseLookupProvider)
        settingsPrefs.putBoolean("visual_voicemail", s.visualVoicemail)
        settingsPrefs.putBoolean("vvm_auto_archive", s.vvmAutoArchive)
    }

    private fun loadState(): SettingsState {
        return SettingsState(
            sortOrder = settingsPrefs.getInt("sort_order", 0),
            nameFormat = settingsPrefs.getInt("name_format", 0),
            ringtoneUri = settingsPrefs.getString("ringtone_uri", null)?.takeIf { it.isNotEmpty() },
            vibrateOnRing = settingsPrefs.getBoolean("vibrate_on_ring", false),
            dtmfToneEnabled = settingsPrefs.getBoolean("dtmf_tone_enabled", true),
            dtmfToneLength = settingsPrefs.getInt("dtmf_tone_length", 0),
            incallDnd = settingsPrefs.getBoolean("incall_dnd", false),
            incallVibrateOutgoing = settingsPrefs.getBoolean("incall_vibrate_outgoing", true),
            incallVibrateCallWaiting = settingsPrefs.getBoolean("incall_vibrate_call_waiting", true),
            incallVibrateHangup = settingsPrefs.getBoolean("incall_vibrate_hangup", true),
            incallVibrate45 = settingsPrefs.getBoolean("incall_vibrate_45", false),
            recordingFormat = settingsPrefs.getInt("recording_format", 0),
            recordingAutoStart = settingsPrefs.getBoolean("recording_auto_start", false),
            smartMute = settingsPrefs.getBoolean("smart_mute", false),
            assistedDialingEnabled = settingsPrefs.getBoolean("assisted_dialing_enabled", true),
            assistedDialingCountry = settingsPrefs.getInt("assisted_dialing_country", 0),
            postCallEnabled = settingsPrefs.getBoolean("post_call_enabled", true),
            proximitySensorDisabled = settingsPrefs.getBoolean("proximity_sensor_disabled", false),
            antiSpamEnabled = settingsPrefs.getBoolean("anti_spam_enabled", true),
            antiSpamBlockHighRisk = settingsPrefs.getBoolean("anti_spam_block_high_risk", true),
            antiSpamBlockRealEstate = settingsPrefs.getBoolean("anti_spam_block_real_estate", false),
            antiSpamBlockAds = settingsPrefs.getBoolean("anti_spam_block_ads", false),
            antiSpamBlockDelivery = settingsPrefs.getBoolean("anti_spam_block_delivery", false),
            antiSpamBlockLoan = settingsPrefs.getBoolean("anti_spam_block_loan", false),
            antiSpamBlockEducation = settingsPrefs.getBoolean("anti_spam_block_education", false),
            antiSpamBlockRepair = settingsPrefs.getBoolean("anti_spam_block_repair", false),
            antiSpamBlockInsurance = settingsPrefs.getBoolean("anti_spam_block_insurance", false),
            forwardLookup = settingsPrefs.getBoolean("forward_lookup", false),
            forwardLookupProvider = settingsPrefs.getInt("forward_lookup_provider", 0),
            reverseLookup = settingsPrefs.getBoolean("reverse_lookup", false),
            reverseLookupProvider = settingsPrefs.getInt("reverse_lookup_provider", 0),
            visualVoicemail = settingsPrefs.getBoolean("visual_voicemail", false),
            vvmAutoArchive = settingsPrefs.getBoolean("vvm_auto_archive", false),
        )
    }
}

// ── Navigation ───────────────────────────────────────────────────────────────

private enum class SettingsPage {
    ROOT,
    DISPLAY_OPTIONS,
    SOUNDS_AND_VIBRATION,
    ASSISTED_DIALING,
    LOOKUP,
    VOICEMAIL,
    OTHER,
    ANTI_SPAM,
}

private data class PageInfo(val title: String, val page: SettingsPage)

// ── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var currentPage by rememberSaveable { mutableStateOf(SettingsPage.ROOT) }
    // Tracks the real-time progress of the predictive back gesture (0 → 1)
    var backProgress by remember { mutableFloatStateOf(0f) }
    var isGesturingBack by remember { mutableStateOf(false) }

    // Refresh default-dialer status whenever the settings screen becomes visible
    LaunchedEffect(Unit) {
        viewModel.refreshDefaultDialer()
    }

    // Predictive back handler for edge-swipe gesture (Android 14+ predictive back).
    // Feeds real-time progress to the UI via BackEventCompat.progress.
    PredictiveBackHandler { backEvents: kotlinx.coroutines.flow.Flow<androidx.activity.BackEventCompat> ->
        coroutineScope {
            backEvents.collect { backEvent ->
                backProgress = backEvent.progress
                isGesturingBack = true
            }
        }
        // Gesture finished: perform navigation
        if (currentPage != SettingsPage.ROOT) {
            currentPage = SettingsPage.ROOT
        } else {
            onBack()
        }
        backProgress = 0f
        isGesturingBack = false
    }

    // BackHandler for non-gesture back (keyboard, nav bar buttons).
    // Does NOT use PredictiveBackHandler — those paths are instantaneous.
    BackHandler {
        if (currentPage != SettingsPage.ROOT) {
            currentPage = SettingsPage.ROOT
        } else {
            onBack()
        }
    }

    val pageTitle = when (currentPage) {
        SettingsPage.ROOT -> "设置"
        SettingsPage.DISPLAY_OPTIONS -> "显示选项"
        SettingsPage.SOUNDS_AND_VIBRATION -> "声音和振动"
        SettingsPage.ASSISTED_DIALING -> "辅助拨号"
        SettingsPage.LOOKUP -> "来电归属查询"
        SettingsPage.VOICEMAIL -> "语音信箱"
        SettingsPage.OTHER -> "其他设置"
        SettingsPage.ANTI_SPAM -> "反垃圾短信"
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(pageTitle) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentPage != SettingsPage.ROOT) {
                                currentPage = SettingsPage.ROOT
                            } else {
                                onBack()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                // Predictive back overlay: subtle parallax during edge-swipe gesture.
                // The overlay is transparent but drives the slide offset in real time.
                .then(
                    if (isGesturingBack) {
                        Modifier.graphicsLayer {
                            val p = backProgress.coerceIn(0f, 1f)
                            translationX = -p * 28f
                            alpha = 1f - (p * 0.12f)
                        }
                    } else Modifier,
                ),
        ) {
            AnimatedContent(
                targetState = currentPage,
                contentKey = { it.name },
                modifier = Modifier.fillMaxSize(),
            ) { targetPage ->
                when (targetPage) {
                    SettingsPage.ROOT -> RootSettings(
                        state = state,
                        viewModel = viewModel,
                        onNavigate = { currentPage = it },
                        onBack = onBack,
                    )
                    SettingsPage.DISPLAY_OPTIONS -> DisplayOptionsSettings(
                        state = state,
                        onUpdate = viewModel::updateSetting,
                    )
                    SettingsPage.SOUNDS_AND_VIBRATION -> SoundsAndVibrationSettings(
                        state = state,
                        onUpdate = viewModel::updateSetting,
                    )
                    SettingsPage.ASSISTED_DIALING -> AssistedDialingSettings(
                        state = state,
                        onUpdate = viewModel::updateSetting,
                    )
                    SettingsPage.LOOKUP -> LookupSettings(
                        state = state,
                        onUpdate = viewModel::updateSetting,
                    )
                    SettingsPage.VOICEMAIL -> VoicemailSettings(
                        state = state,
                        onUpdate = viewModel::updateSetting,
                    )
                    SettingsPage.OTHER -> OtherSettings(
                        state = state,
                        onUpdate = viewModel::updateSetting,
                    )
                    SettingsPage.ANTI_SPAM -> AntiSpamSettings(
                        state = state,
                        onUpdate = viewModel::updateSetting,
                    )
                }
            }
        }
    }
}

// ── Root settings ────────────────────────────────────────────────────────────

@Composable
private fun RootSettings(
    state: SettingsState,
    viewModel: SettingsViewModel,
    onNavigate: (SettingsPage) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Default dialer — links to system role request
        item {
            SettingsNavItem(
                title = "默认拨号器",
                subtitle = if (state.isDefaultDialer) "已设为默认拨号应用" else "点击设为默认拨号应用",
                icon = Icons.Outlined.VerifiedUser,
                onClick = {
                    val intent = viewModel.createRequestDefaultDialerIntent()
                    if (intent != null) {
                        runCatching { context.startActivity(intent) }
                    } else {
                        Toast.makeText(
                            context,
                            R.string.default_app_unavailable,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
            )
        }

        item {
            SettingsNavItem(
                title = "显示选项",
                subtitle = if (state.sortOrder == 0) "按名字排序 · 名字在前" else "按姓氏排序 · 姓氏在前",
                icon = Icons.Outlined.Sort,
                onClick = { onNavigate(SettingsPage.DISPLAY_OPTIONS) },
            )
        }
        item {
            SettingsNavItem(
                title = "声音和振动",
                subtitle = "铃声、拨号键盘音、通话振动",
                icon = Icons.Outlined.Notifications,
                onClick = { onNavigate(SettingsPage.SOUNDS_AND_VIBRATION) },
            )
        }
        item {
            SettingsNavItem(
                title = "辅助拨号",
                subtitle = "出差时自动添加国际拨号前缀",
                icon = Icons.Outlined.Phone,
                onClick = { onNavigate(SettingsPage.ASSISTED_DIALING) },
            )
        }
        item {
            SettingsNavItem(
                title = "短信回复",
                subtitle = "设置拒接来电时快速回复的短信",
                icon = Icons.AutoMirrored.Outlined.Chat,
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(TelecomManager.ACTION_SHOW_RESPOND_VIA_SMS_SETTINGS)
                        )
                    }
                },
            )
        }
        item {
            SettingsNavItem(
                title = "来电归属查询",
                subtitle = if (state.forwardLookup || state.reverseLookup)
                    "已启用号码归属查询" else "查询未知号码的归属地",
                icon = Icons.Outlined.TravelExplore,
                onClick = { onNavigate(SettingsPage.LOOKUP) },
            )
        }
        item {
            SettingsNavItem(
                title = "通话设置",
                subtitle = "运营商呼叫转移、等待、紧急呼叫等",
                icon = Icons.Outlined.PhoneInTalk,
                onClick = {
                    runCatching {
                        val intent = Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                        context.startActivity(intent)
                    }
                },
            )
        }
        item {
            SettingsNavItem(
                title = "黑名单号码",
                subtitle = "管理被阻止的号码",
                icon = Icons.Outlined.Block,
                onClick = {
                    runCatching {
                        val tm = context.getSystemService(TelecomManager::class.java)
                        context.startActivity(tm.createManageBlockedNumbersIntent())
                    }
                },
            )
        }
        item {
            SettingsNavItem(
                title = "反垃圾短信",
                subtitle = "拦截推销、诈骗等来电",
                icon = Icons.Outlined.Shield,
                onClick = { onNavigate(SettingsPage.ANTI_SPAM) },
            )
        }
        item {
            SettingsNavItem(
                title = "语音信箱",
                subtitle = "语音信箱通知、可视语音信箱",
                icon = Icons.Outlined.Voicemail,
                onClick = { onNavigate(SettingsPage.VOICEMAIL) },
            )
        }
        item {
            SettingsNavItem(
                title = "无障碍",
                subtitle = "TTY 模式、助听器兼容",
                icon = Icons.Outlined.Accessibility,
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(TelecomManager.ACTION_SHOW_CALL_ACCESSIBILITY_SETTINGS)
                        )
                    }
                },
            )
        }
        item {
            SettingsNavItem(
                title = "其他设置",
                subtitle = "通话后记录、距离传感器",
                icon = Icons.Outlined.MoreHoriz,
                onClick = { onNavigate(SettingsPage.OTHER) },
            )
        }
    }
}

// ── Assisted dialing ──────────────────────────────────────────────────────────

@Composable
private fun AssistedDialingSettings(
    state: SettingsState,
    onUpdate: ((SettingsState) -> SettingsState) -> Unit,
) {
    val countryNames = listOf(
        "自动检测" to "",
        "中国 (China) +86" to "CN",
        "美国 (USA) +1" to "US",
        "英国 (UK) +44" to "GB",
        "德国 (Germany) +49" to "DE",
        "法国 (France) +33" to "FR",
        "日本 (Japan) +81" to "JP",
        "韩国 (Korea) +82" to "KR",
        "澳大利亚 (Australia) +61" to "AU",
        "加拿大 (Canada) +1" to "CA",
        "印度 (India) +91" to "IN",
        "俄罗斯 (Russia) +7" to "RU",
        "巴西 (Brazil) +55" to "BR",
        "墨西哥 (Mexico) +52" to "MX",
    )

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsSwitch(
                title = "启用辅助拨号",
                subtitle = "出差或旅行时自动为国际号码添加国家代码前缀",
                icon = Icons.Outlined.Phone,
                checked = state.assistedDialingEnabled,
                onCheckedChange = { onUpdate { s -> s.copy(assistedDialingEnabled = it) } },
            )
        }
        item { SectionHeader("国家/地区") }
        countryNames.forEachIndexed { index, (label, code) ->
            item {
                val isSelected = state.assistedDialingCountry == index
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = state.assistedDialingEnabled) {
                            onUpdate { s -> s.copy(assistedDialingCountry = index) }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = {
                            if (state.assistedDialingEnabled) {
                                onUpdate { s -> s.copy(assistedDialingCountry = index) }
                            }
                        },
                        enabled = state.assistedDialingEnabled,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (state.assistedDialingEnabled)
                            MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
            }
        }
        item {
            Text(
                text = "辅助拨号功能会自动检测您当前所在的国家/地区，并在拨打国际电话时为您添加相应的国家代码前缀。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

// ── Display options ──────────────────────────────────────────────────────────

@Composable
private fun DisplayOptionsSettings(
    state: SettingsState,
    onUpdate: ((SettingsState) -> SettingsState) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { SectionHeader("列表排序方式") }
        item {
            SettingsRadio(
                title = "按名字排序",
                selected = state.sortOrder == 0,
                onClick = { onUpdate { it.copy(sortOrder = 0) } },
            )
        }
        item {
            SettingsRadio(
                title = "按姓氏排序",
                selected = state.sortOrder == 1,
                onClick = { onUpdate { it.copy(sortOrder = 1) } },
            )
        }
        item { SectionHeader("姓名显示格式") }
        item {
            SettingsRadio(
                title = "名字在前",
                selected = state.nameFormat == 0,
                onClick = { onUpdate { it.copy(nameFormat = 0) } },
            )
        }
        item {
            SettingsRadio(
                title = "姓氏在前",
                selected = state.nameFormat == 1,
                onClick = { onUpdate { it.copy(nameFormat = 1) } },
            )
        }
    }
}

// ── Sounds & vibration ───────────────────────────────────────────────────────

@Composable
private fun SoundsAndVibrationSettings(
    state: SettingsState,
    onUpdate: ((SettingsState) -> SettingsState) -> Unit,
) {
    val context = LocalContext.current

    // ActivityResultLauncher for ringtone picker — avoids startActivityForResult deprecation
    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        val ringtoneUri = uri?.toString()
        if (ringtoneUri != null) {
            onUpdate { s -> s.copy(ringtoneUri = ringtoneUri) }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsNavItem(
                title = "铃声",
                subtitle = state.ringtoneUri?.let {
                    "已选择铃声"
                } ?: "选择来电铃声",
                icon = Icons.Outlined.MusicNote,
                onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择铃声")
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    }
                    ringtonePickerLauncher.launch(intent)
                },
                showArrow = false,
            )
        }
        item {
            val hasWriteSettings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.System.canWrite(context)
            } else true

            SettingsSwitch(
                title = "响铃时振动",
                icon = Icons.Outlined.Vibration,
                checked = state.vibrateOnRing,
                enabled = hasWriteSettings,
                onCheckedChange = { enabled ->
                    if (hasWriteSettings) {
                        // Write to system setting VIBRATE_WHEN_RINGING
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            Settings.System.putInt(
                                context.contentResolver,
                                Settings.System.VIBRATE_WHEN_RINGING,
                                if (enabled) 1 else 0
                            )
                        }
                        onUpdate { s -> s.copy(vibrateOnRing = enabled) }
                    } else {
                        Toast.makeText(
                            context,
                            "需要授权「修改系统设置」才能使用此功能",
                            Toast.LENGTH_LONG
                        ).show()
                        // Open the Write Settings permission page
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            )
                        }
                    }
                },
            )
        }
        item {
            SettingsSwitch(
                title = "拨号键盘音",
                icon = Icons.Outlined.TouchApp,
                checked = state.dtmfToneEnabled,
                onCheckedChange = { onUpdate { s -> s.copy(dtmfToneEnabled = it) } },
            )
        }
        item {
            SettingsNavItem(
                title = "拨号键盘音时长",
                subtitle = if (state.dtmfToneLength == 0) "短" else "长",
                icon = Icons.Outlined.Timelapse,
                onClick = {
                    onUpdate { s -> s.copy(dtmfToneLength = if (s.dtmfToneLength == 0) 1 else 0) }
                },
                showArrow = false,
            )
        }

        item { SectionHeader("通话中振动") }
        item {
            SettingsSwitch(
                title = "通话中启用勿扰",
                icon = Icons.Outlined.DoNotDisturb,
                checked = state.incallDnd,
                onCheckedChange = { onUpdate { s -> s.copy(incallDnd = it) } },
            )
        }
        item {
            SettingsSwitch(
                title = "拨出电话接通时振动",
                icon = Icons.Outlined.PhoneForwarded,
                checked = state.incallVibrateOutgoing,
                onCheckedChange = { onUpdate { s -> s.copy(incallVibrateOutgoing = it) } },
            )
        }
        item {
            SettingsSwitch(
                title = "呼叫等待振动",
                icon = Icons.Outlined.SwapCalls,
                checked = state.incallVibrateCallWaiting,
                onCheckedChange = { onUpdate { s -> s.copy(incallVibrateCallWaiting = it) } },
            )
        }
        item {
            SettingsSwitch(
                title = "挂断时振动",
                icon = Icons.Outlined.CallEnd,
                checked = state.incallVibrateHangup,
                onCheckedChange = { onUpdate { s -> s.copy(incallVibrateHangup = it) } },
            )
        }
        item {
            SettingsSwitch(
                title = "通话结束前 45 秒振动",
                subtitle = "提醒你通话即将结束",
                icon = Icons.Outlined.Timer,
                checked = state.incallVibrate45,
                onCheckedChange = { onUpdate { s -> s.copy(incallVibrate45 = it) } },
            )
        }

        item { SectionHeader("通话录音") }
        item {
            SettingsNavItem(
                title = "录音格式",
                subtitle = when (state.recordingFormat) {
                    0 -> "OGG"
                    1 -> "AAC"
                    2 -> "AMR"
                    else -> "OGG"
                },
                icon = Icons.Outlined.FiberSmartRecord,
                onClick = {
                    onUpdate { s ->
                        s.copy(recordingFormat = (s.recordingFormat + 1) % 3)
                    }
                },
                showArrow = false,
            )
        }
        item {
            SettingsSwitch(
                title = "自动开始录音",
                subtitle = "通话接通后自动开始录音",
                icon = Icons.Outlined.FiberManualRecord,
                checked = state.recordingAutoStart,
                onCheckedChange = { onUpdate { s -> s.copy(recordingAutoStart = it) } },
            )
        }

        item { SectionHeader("智能") }
        item {
            SettingsSwitch(
                title = "智能静音",
                subtitle = "将手机平放在桌面上时，来电自动静音",
                icon = Icons.Outlined.Smartphone,
                checked = state.smartMute,
                onCheckedChange = { onUpdate { s -> s.copy(smartMute = it) } },
            )
        }
    }
}

// ── Other settings ─────────────────────────────────────────────────────────────

@Composable
private fun OtherSettings(
    state: SettingsState,
    onUpdate: ((SettingsState) -> SettingsState) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsSwitch(
                title = "通话后记录",
                subtitle = "通话结束后显示通话记录",
                icon = Icons.Outlined.History,
                checked = state.postCallEnabled,
                onCheckedChange = { onUpdate { s -> s.copy(postCallEnabled = it) } },
            )
        }
        item {
            SettingsSwitch(
                title = "禁用距离传感器",
                subtitle = "通话时禁用距离感应器",
                icon = Icons.Outlined.Sensors,
                checked = state.proximitySensorDisabled,
                onCheckedChange = { onUpdate { s -> s.copy(proximitySensorDisabled = it) } },
            )
        }
    }
}

// ── Anti-spam ────────────────────────────────────────────────────────────────

@Composable
private fun AntiSpamSettings(
    state: SettingsState,
    onUpdate: ((SettingsState) -> SettingsState) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { SectionHeader("反垃圾短信") }
        item {
            SettingsSwitch(
                title = "启用反垃圾短信",
                subtitle = "自动识别并拦截推销、诈骗等来电",
                icon = Icons.Outlined.Shield,
                checked = state.antiSpamEnabled,
                onCheckedChange = { onUpdate { s -> s.copy(antiSpamEnabled = it) } },
            )
        }
        item { SectionHeader("拦截类型") }
        item {
            SettingsSwitch(
                title = "高风险号码",
                subtitle = "高概率诈骗/推销电话",
                icon = Icons.Outlined.Warning,
                enabled = state.antiSpamEnabled,
                checked = state.antiSpamBlockHighRisk,
                onCheckedChange = { onUpdate { s -> s.copy(antiSpamBlockHighRisk = it) } },
            )
        }
        item {
            SettingsSwitch(
                title = "房产推销",
                enabled = state.antiSpamEnabled,
                checked = state.antiSpamBlockRealEstate,
                onCheckedChange = { onUpdate { s -> s.copy(antiSpamBlockRealEstate = it) } },
            )
        }
        item {
            SettingsSwitch(
                title = "广告推销",
                enabled = state.antiSpamEnabled,
                checked = state.antiSpamBlockAds,
                onCheckedChange = { onUpdate { s -> s.copy(antiSpamBlockAds = it) } },
            )
        }
        item {
            SettingsSwitch(
                title = "快递推销",
                enabled = state.antiSpamEnabled,
                checked = state.antiSpamBlockDelivery,
                onCheckedChange = { onUpdate { s -> s.copy(antiSpamBlockDelivery = it) } },
            )
        }
        item {
            SettingsSwitch(
                title = "贷款推销",
                enabled = state.antiSpamEnabled,
                checked = state.antiSpamBlockLoan,
                onCheckedChange = { onUpdate { s -> s.copy(antiSpamBlockLoan = it) } },
            )
        }
        item {
            SettingsSwitch(
                title = "教育培训",
                enabled = state.antiSpamEnabled,
                checked = state.antiSpamBlockEducation,
                onCheckedChange = { onUpdate { s -> s.copy(antiSpamBlockEducation = it) } },
            )
        }
        item {
            SettingsSwitch(
                title = "维修服务",
                enabled = state.antiSpamEnabled,
                checked = state.antiSpamBlockRepair,
                onCheckedChange = { onUpdate { s -> s.copy(antiSpamBlockRepair = it) } },
            )
        }
        item {
            SettingsSwitch(
                title = "保险推销",
                enabled = state.antiSpamEnabled,
                checked = state.antiSpamBlockInsurance,
                onCheckedChange = { onUpdate { s -> s.copy(antiSpamBlockInsurance = it) } },
            )
        }
    }
}

// ── Lookup ───────────────────────────────────────────────────────────────────

@Composable
private fun LookupSettings(
    state: SettingsState,
    onUpdate: ((SettingsState) -> SettingsState) -> Unit,
) {
    val providerNames = listOf("Google", "Yandex", "OpenCNAM")
    val providerIdx = state.forwardLookupProvider % providerNames.size

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsSwitch(
                title = "启用正向查询",
                subtitle = "在拨号盘中输入号码时显示匹配结果",
                icon = Icons.Outlined.Call,
                checked = state.forwardLookup,
                onCheckedChange = { onUpdate { s -> s.copy(forwardLookup = it) } },
            )
        }
        item {
            SettingsNavItem(
                title = "正向查询提供商",
                subtitle = providerNames[providerIdx],
                icon = Icons.Outlined.Language,
                enabled = state.forwardLookup,
                onClick = {
                    onUpdate { s ->
                        s.copy(forwardLookupProvider = (s.forwardLookupProvider + 1) % providerNames.size)
                    }
                },
                showArrow = false,
            )
        }
        item {
            SettingsSwitch(
                title = "启用反向查询",
                subtitle = "收到来电时显示来电者信息",
                icon = Icons.Outlined.CallReceived,
                checked = state.reverseLookup,
                onCheckedChange = { onUpdate { s -> s.copy(reverseLookup = it) } },
            )
        }
        item {
            SettingsNavItem(
                title = "反向查询提供商",
                subtitle = providerNames[state.reverseLookupProvider % providerNames.size],
                icon = Icons.Outlined.Language,
                enabled = state.reverseLookup,
                onClick = {
                    onUpdate { s ->
                        s.copy(reverseLookupProvider = (s.reverseLookupProvider + 1) % providerNames.size)
                    }
                },
                showArrow = false,
            )
        }
        item {
            Text(
                text = "注意：号码查询服务会将号码发送给第三方提供商。查询结果仅供来电识别参考，无法保证准确性。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

// ── Voicemail ────────────────────────────────────────────────────────────────

@Composable
private fun VoicemailSettings(
    state: SettingsState,
    onUpdate: ((SettingsState) -> SettingsState) -> Unit,
) {
    val context = LocalContext.current

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsNavItem(
                title = "语音信箱通知",
                subtitle = "通知铃声、振动等",
                icon = Icons.Outlined.Notifications,
                onClick = {},
                showArrow = false,
            )
        }
        item {
            SettingsSwitch(
                title = "可视语音信箱",
                subtitle = "在通话记录中直接显示语音信箱内容",
                icon = Icons.Outlined.Voicemail,
                checked = state.visualVoicemail,
                onCheckedChange = { onUpdate { s -> s.copy(visualVoicemail = it) } },
            )
        }
        item {
            SettingsSwitch(
                title = "自动归档",
                subtitle = "自动归档已听过的语音信箱",
                icon = Icons.Outlined.Archive,
                checked = state.vvmAutoArchive,
                enabled = state.visualVoicemail,
                onCheckedChange = { onUpdate { s -> s.copy(vvmAutoArchive = it) } },
            )
        }
        item {
            SettingsNavItem(
                title = "修改语音信箱 PIN",
                icon = Icons.Outlined.Pin,
                onClick = {
                    runCatching {
                        val intent = Intent(
                            TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS
                        )
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                        context.startActivity(intent)
                    }
                },
                showArrow = false,
            )
        }
        item {
            SettingsNavItem(
                title = "高级设置",
                icon = Icons.Outlined.Settings,
                onClick = {},
                showArrow = false,
            )
        }
    }
}

// ── Shared components ────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsNavItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
    showArrow: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showArrow) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

@Composable
private fun SettingsSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = if (icon != null) 56.dp else 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

@Composable
private fun SettingsRadio(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}