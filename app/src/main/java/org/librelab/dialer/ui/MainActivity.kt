package org.librelab.dialer.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.librelab.dialer.R
import org.librelab.dialer.data.TelecomAdapter
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_drop_down
import com.composables.icons.materialsymbols.outlined.Call
import com.composables.icons.materialsymbols.outlined.History
import com.composables.icons.materialsymbols.outlined.Person
import com.composables.icons.materialsymbols.outlined.Search
import com.composables.icons.materialsymbols.outlined.Settings
import com.composables.icons.materialsymbols.outlined.Star
import com.composables.icons.materialsymbols.outlined.Voicemail
import org.librelab.dialer.ui.calllog.CallLogScreen
import org.librelab.dialer.ui.contacts.ContactsScreen
import org.librelab.dialer.ui.dialpad.DialpadActivity
import org.librelab.dialer.ui.settings.SettingsScreen
import org.librelab.dialer.ui.speeddial.SpeedDialScreen
import org.librelab.dialer.ui.theme.LibreDialerTheme
import org.librelab.dialer.ui.voicemail.VoicemailScreen
import javax.inject.Inject

/**
 * Main Activity — hosts the Compose UI with bottom tab navigation,
 * matching the crDroid Dialer BottomNavBar.
 *
 * Navigation architecture:
 * - singleTask Activity: serves as the root of the main task
 * - Tab switching: MainUiState.currentTab (page-internal state)
 * - Settings overlay: MainUiState.isSettingsShown (closed via Back press)
 * - Search bar: MainUiState.isSearchShown (closed via Back press)
 * - Dialpad: DialpadActivity (separate Activity, floating window)
 *
 * Back handling:
 * - System Back → intercepts if overlay (Settings/Search) is active
 * - System Back → closes overlay first, then exits app
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val REQUEST_CODE_SET_DEFAULT_DIALER = 1
    }

    @Inject
    lateinit var telecomAdapter: TelecomAdapter

    private lateinit var mainViewModel: MainViewModel

    private val requiredPermissions = arrayOf(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE,
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* result handled by recomposition */ }

    /** Launcher for the ROLE_DIALER system dialog (Android 10+). */
    private lateinit var defaultDialerLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register the role-request result handler before any Composable runs
        defaultDialerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { /* system dialog handles RESULT_OK / cancellation; refresh on resume */ }

        // Capture launcher reference for use inside Compose lambdas
        val launcher = defaultDialerLauncher

        // Request runtime permissions
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }

        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContent {
            LibreDialerTheme {
                mainViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                MainScreen(
                    telecomAdapter = telecomAdapter,
                    modifier = Modifier.fillMaxSize(),
                    defaultDialerLauncher = defaultDialerLauncher,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Check and broadcast default-dialer status; the ViewModel will recompose
        // whenever the RoleManager state changes (onResume covers role-change scenarios).
        if (::mainViewModel.isInitialized) {
            mainViewModel.refreshDefaultDialer()
        }
        @Suppress("DEPRECATION")
        if (window.decorView.visibility != android.view.View.VISIBLE) {
            @Suppress("DEPRECATION")
            window.decorView.visibility = android.view.View.VISIBLE
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SET_DEFAULT_DIALER) {
            // Refresh dialer state after returning from the system role dialog
            mainViewModel.refreshDefaultDialer()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    telecomAdapter: TelecomAdapter,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
    defaultDialerLauncher: ActivityResultLauncher<Intent>? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Navigation Compose handles the Settings subtree Back Stack automatically:
    //  Root → DisplayOptions / Sound / Lookup / Voicemail / AssistedDialing
    //  Back pops one level at a time. The TopAppBar navigation arrow + system
    //  Back both work through the same NavController.
    val settingsNavController = rememberNavController()
    val currentRoute by settingsNavController.currentBackStackEntryAsState()
    val isOnSettingsRoute = currentRoute?.destination?.route?.startsWith("settings") == true

    val tabs = buildList {
        add(TabItem(MainTab.SPEED_DIAL, "Favorites", MaterialSymbols.Outlined.Star))
        add(TabItem(MainTab.CALL_LOG, "通话记录", MaterialSymbols.Outlined.History, state.missedCallCount))
        add(TabItem(MainTab.CONTACTS, "Contacts", MaterialSymbols.Outlined.Person))
        if (state.voicemailTabVisible) {
            add(TabItem(MainTab.VOICEMAIL, "Voicemail", MaterialSymbols.Outlined.Voicemail, state.voicemailCount))
        }
    }

    val tabTitle = when (state.currentTab) {
        MainTab.SPEED_DIAL -> "Favorites"
        MainTab.CALL_LOG -> "通话记录"
        MainTab.CONTACTS -> "Contacts"
        MainTab.VOICEMAIL -> "Voicemail"
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            // Hide the global topBar when navigating inside Settings — each
            // sub-page renders its own TopAppBar with a back arrow.
            if (!isOnSettingsRoute) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = tabTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Normal,
                        )
                    },
                    actions = {
                        if (state.isSearchShown) {
                            TextField(
                                value = state.searchQuery,
                                onValueChange = { viewModel.onSearchQueryChange(it) },
                                placeholder = { Text("Search") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            )
                            IconButton(onClick = { viewModel.toggleSearch() }) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Close search",
                                )
                            }
                        } else {
                            IconButton(onClick = { viewModel.toggleSearch() }) {
                                Icon(
                                    imageVector = MaterialSymbols.Outlined.Search,
                                    contentDescription = "Search",
                                )
                            }
                            IconButton(onClick = {
                                settingsNavController.navigate(SettingsRoute.Root.route)
                            }) {
                                Icon(
                                    imageVector = MaterialSymbols.Outlined.Settings,
                                    contentDescription = "Settings",
                                )
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!isOnSettingsRoute) {
                val ctx = LocalContext.current
                FloatingActionButton(
                    onClick = {
                        ctx.startActivity(Intent(ctx, DialpadActivity::class.java))
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Dialpad,
                        contentDescription = "Dialpad",
                    )
                }
            }
        },
        bottomBar = {
            if (!isOnSettingsRoute) {
                BottomNavBar(
                    tabs = tabs,
                    selectedTab = state.currentTab,
                    onTabSelected = { viewModel.selectTab(it) },
                )
            }
        },
    ) { paddingValues ->
        val ctx = LocalContext.current
        Box(modifier = Modifier.padding(paddingValues)) {
            // Default-dialer banner: shown when not the default phone app and not dismissed
            if (!state.isDefaultDialer && !isOnSettingsRoute && state.showDefaultDialerBanner) {
                SetupCard(
                    title = stringResource(R.string.setup_default_title),
                    body = stringResource(R.string.setup_default_body),
                    buttonText = stringResource(R.string.action_go_settings),
                    onClick = {
                        val intent = viewModel.createRequestDefaultDialerIntent()
                        defaultDialerLauncher?.launch(intent)
                    },
                    onDismiss = { viewModel.dismissDefaultDialerBanner() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            NavHost(
                navController = settingsNavController,
                startDestination = SettingsRoute.Home.route,
            ) {
                // "Home" route shows the current tab content — this is the default destination
                composable(SettingsRoute.Home.route) {
                    when (state.currentTab) {
                        MainTab.SPEED_DIAL -> {
                            SpeedDialScreen(telecomAdapter = telecomAdapter)
                        }
                        MainTab.CALL_LOG -> {
                            CallLogScreen(
                                telecomAdapter = telecomAdapter,
                                onCallDetails = { /* TODO */ },
                            )
                        }
                        MainTab.CONTACTS -> {
                            ContactsScreen(telecomAdapter = telecomAdapter)
                        }
                        MainTab.VOICEMAIL -> {
                            VoicemailScreen(onBack = { viewModel.selectTab(MainTab.CALL_LOG) })
                        }
                    }
                }
                // Multi-level settings — SettingsScreen manages its own sub-page navigation.
                composable(SettingsRoute.Root.route) {
                    SettingsScreen(
                        onBack = { settingsNavController.popBackStack() },
                    )
                }
            }
        }
    }
}

/**
 * NavHost routes used inside MainScreen. The "Home" route is the start destination
 * (the current tab content); the others are nested Settings pages. Each settings
 * route is rendered by SettingsScreen with the parent NavController for back-stack
 * management.
 */
private sealed class SettingsRoute(val route: String) {
    data object Home : SettingsRoute("home")
    data object Root : SettingsRoute("settings")
}

private data class TabItem(
    val tab: MainTab,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val badgeCount: Int = 0,
)

@Composable
private fun BottomNavBar(
    tabs: List<TabItem>,
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (tab in tabs) {
                val isSelected = tab.tab == selectedTab
                BottomNavItem(
                    icon = tab.icon,
                    label = tab.label,
                    isSelected = isSelected,
                    badgeCount = tab.badgeCount,
                    onClick = { onTabSelected(tab.tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconTint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
    val textColor = if (isSelected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(64.dp, 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Pill background (active indicator)
            if (isSelected) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(128.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                )
            }

            // Icon
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )

            // Badge
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 8.dp)
                        .clip(RoundedCornerShape(128.dp))
                        .background(MaterialTheme.colorScheme.error)
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                        .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        color = MaterialTheme.colorScheme.onError,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Compact banner prompting the user to set this app as the default dialer.
 * Material 3 Banner style: single-row layout, Surface background, no Card wrapper.
 * Height ~56-64dp (not 100+dp like the previous Card version).
 */
@Composable
fun SetupCard(
    title: String,
    body: String,
    buttonText: String,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(8.dp))

            // Primary action button
            FilledTonalButton(onClick = onClick) {
                Text(buttonText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            // Close button — standard 48dp touch target, 24dp icon
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}