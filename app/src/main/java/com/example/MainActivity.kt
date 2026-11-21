package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.local.AppearanceMode
import com.example.data.model.InterventionMode
import com.example.data.model.InterventionToastState
import com.example.data.model.ProtectionStatus
import com.example.data.repository.OutloopBackupPreview
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.BottomTab
import com.example.ui.components.FloatingPillNavBar
import com.example.ui.components.InterventionBanner
import com.example.ui.components.LocalAnimatedVisibilityScope
import com.example.ui.components.LocalSharedTransitionScope
import com.example.ui.components.NavAlertBadges
import com.example.ui.components.OutloopCanvas
import com.example.ui.components.OutloopSideRail
import com.example.ui.components.OutloopToastPill
import com.example.ui.screens.AppDetailScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.ManageAppsScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PrivacyScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.FeedSurgeonTheme
import com.example.ui.theme.isDarkThemeForAppearance
import kotlinx.coroutines.delay

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"
    const val MANAGE_APPS = "manage_apps"
    const val PRIVACY = "privacy"
    const val DIAGNOSTICS = "diagnostics"
    const val APP_DETAIL = "app_detail/{packageName}"

    fun appDetail(packageName: String): String = "app_detail/$packageName"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FeedSurgeonRoot(activity = this)
        }
    }
}

@Composable
fun FeedSurgeonRoot(
    activity: ComponentActivity? = null,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val useDarkTheme = isDarkThemeForAppearance(uiState.preferences.appearanceMode)

    if (activity != null) {
        DisposableEffect(useDarkTheme) {
            val transparent = Color.Transparent.toArgb()
            activity.enableEdgeToEdge(
                statusBarStyle = if (useDarkTheme) {
                    SystemBarStyle.dark(transparent)
                } else {
                    SystemBarStyle.light(transparent, transparent)
                },
                navigationBarStyle = if (useDarkTheme) {
                    SystemBarStyle.dark(transparent)
                } else {
                    SystemBarStyle.light(transparent, transparent)
                }
            )
            onDispose {}
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshAccessibilityStatus(context)
        viewModel.refreshInstalledApps(context)
        viewModel.triggerAutoSyncIfNeeded()
    }

    LaunchedEffect(uiState.recentToast?.eventId) {
        if (uiState.recentToast != null) {
            delay(8_000L)
            viewModel.dismissToast()
        }
    }

    FeedSurgeonTheme(
        appearanceMode = uiState.preferences.appearanceMode,
        hapticsEnabled = uiState.preferences.hapticFeedbackEnabled
    ) {
        if (uiState.isInitialLoading) {
            OutloopCanvas {
                Box(modifier = Modifier.fillMaxSize())
            }
            return@FeedSurgeonTheme
        }

        OutloopNavigationHost(
            uiState = uiState,
            onRefreshProtectionHealth = { viewModel.refreshAccessibilityStatus(context) },
            onToggleApp = viewModel::setRuleEnabled,
            onSelectDefaultMode = { mode, applyToAll ->
                viewModel.setDefaultInterventionMode(mode, applyToAllRules = applyToAll)
            },
            onSelectAppearanceMode = viewModel::setAppearanceMode,
            onSetHapticFeedback = viewModel::setHapticFeedbackEnabled,
            onAcceptDisclosure = viewModel::acceptDisclosure,
            onDeclineDisclosure = viewModel::declineDisclosure,
            onCompleteOnboarding = viewModel::completeOnboarding,
            onToggleGlobalPause = viewModel::setGlobalProtectionPaused,
            onPauseForMinutes = viewModel::pauseGloballyForMinutes,
            onGlobalBreakExpired = viewModel::resumeGlobalProtection,
            onAppSnoozeExpired = viewModel::resumeFromSnooze,
            onRefreshBlockersClick = viewModel::updateRemoteRules,
            onSetAutoUpdateRules = viewModel::setAutoUpdateRules,
            onSetEventCaptureEnabled = viewModel::setEventCaptureEnabled,
            onClearEventLog = viewModel::clearEventLog,
            onShareEventLog = {
                viewModel.createShareLogIntent()?.let { shareIntent ->
                    context.startActivity(
                        Intent.createChooser(shareIntent, "Share diagnostic log")
                    )
                }
            },
            onSetReclaimedSeconds = viewModel::setReclaimedSecondsPerCut,
            onSetShowBanner = viewModel::setShowInterventionBanner,
            onClearHistory = viewModel::clearHistory,
            onSelectMode = viewModel::setInterventionMode,
            onChangeCooldown = viewModel::setCooldownMs,
            onAllowOnceForApp = viewModel::allowOnceTemporary,
            onSnoozeMinutes = viewModel::snoozeForMinutes,
            onAllowOnceFromToast = viewModel::allowOnceFromToast,
            onUndoFromToast = viewModel::undoFromToast,
            onDismissToast = viewModel::dismissToast,
            onSetQuietHours = viewModel::setQuietHours,
            onResumeQuietHoursEarly = viewModel::resumeFromQuietHoursEarly,
            onSetDailyBudget = viewModel::setDailyBudgetCuts,
            onSaveCustomRule = viewModel::upsertCustomRule,
            onDeleteCustomRule = viewModel::deleteCustomRule,
            onExportBackupUri = { uri, cb -> viewModel.exportBackupToUri(context, uri, cb) },
            onInspectBackupUri = { uri, cb -> viewModel.inspectBackupUri(context, uri, cb) },
            onRestoreBackupJson = viewModel::restoreBackupJson
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun OutloopNavigationHost(
    uiState: MainUiState,
    onRefreshProtectionHealth: () -> Unit = {},
    onToggleApp: (String, Boolean) -> Unit = { _, _ -> },
    onSelectDefaultMode: (InterventionMode, Boolean) -> Unit = { _, _ -> },
    onSelectAppearanceMode: (AppearanceMode) -> Unit = {},
    onSetHapticFeedback: (Boolean) -> Unit = {},
    onAcceptDisclosure: () -> Unit = {},
    onDeclineDisclosure: () -> Unit = {},
    onCompleteOnboarding: () -> Unit = {},
    onToggleGlobalPause: (Boolean) -> Unit = {},
    onPauseForMinutes: (Int) -> Unit = {},
    onGlobalBreakExpired: () -> Unit = {},
    onAppSnoozeExpired: (String) -> Unit = {},
    onRefreshBlockersClick: () -> Unit = {},
    onSetAutoUpdateRules: (Boolean) -> Unit = {},
    onSetEventCaptureEnabled: (Boolean) -> Unit = {},
    onClearEventLog: () -> Unit = {},
    onShareEventLog: () -> Unit = {},
    onSetReclaimedSeconds: (Int) -> Unit = {},
    onSetShowBanner: (Boolean) -> Unit = {},
    onClearHistory: () -> Unit = {},
    onSelectMode: (String, InterventionMode) -> Unit = { _, _ -> },
    onChangeCooldown: (String, Int) -> Unit = { _, _ -> },
    onAllowOnceForApp: (String) -> Unit = {},
    onSnoozeMinutes: (String, Int) -> Unit = { _, _ -> },
    onAllowOnceFromToast: (InterventionToastState) -> Unit = {},
    onUndoFromToast: (InterventionToastState) -> Unit = {},
    onDismissToast: () -> Unit = {},
    onSetQuietHours: (Boolean, Int, Int) -> Unit = { _, _, _ -> },
    onResumeQuietHoursEarly: () -> Unit = {},
    onSetDailyBudget: (Int) -> Unit = {},
    onSaveCustomRule: (String, String, String, List<String>, String, String, InterventionMode) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeleteCustomRule: (String) -> Unit = {},
    onExportBackupUri: (Uri, (String) -> Unit) -> Unit = { _, _ -> },
    onInspectBackupUri: (Uri, (OutloopBackupPreview?, String?, String?) -> Unit) -> Unit = { _, _ -> },
    onRestoreBackupJson: (String, (String) -> Unit) -> Unit = { _, _ -> }
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var uiToastMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uiToastMessage) {
        if (uiToastMessage != null) {
            delay(2800L)
            uiToastMessage = null
        }
    }
    LaunchedEffect(uiState.syncStatusMessage) {
        if (!uiState.syncStatusMessage.isNullOrBlank()) {
            uiToastMessage = uiState.syncStatusMessage
        }
    }

    val startDestination = remember {
        if (uiState.preferences.onboardingCompleted) Routes.HOME else Routes.ONBOARDING
    }

    val selectedBottomTab = when (currentRoute) {
        Routes.HOME -> BottomTab.HOME
        Routes.INSIGHTS -> BottomTab.INSIGHTS
        Routes.SETTINGS -> BottomTab.SETTINGS
        else -> null
    }

    val navAlerts = remember(uiState.protectionStatus, uiState.staleRulesCount) {
        NavAlertBadges(
            homeNeedsAttention = uiState.protectionStatus == ProtectionStatus.PERMISSION_NEEDED ||
                uiState.protectionStatus == ProtectionStatus.SERVICE_DISCONNECTED ||
                uiState.protectionStatus == ProtectionStatus.NO_APPS_ACTIVE,
            settingsNeedsAttention = uiState.staleRulesCount > 0
        )
    }

    fun navigateToTopLevel(tab: BottomTab) {
        val route = when (tab) {
            BottomTab.HOME -> Routes.HOME
            BottomTab.INSIGHTS -> Routes.INSIGHTS
            BottomTab.SETTINGS -> Routes.SETTINGS
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateToHomeClearingOnboarding() {
        navController.navigate(Routes.HOME) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    fun tabOrder(route: String?): Int = when (route) {
        Routes.HOME -> 0
        Routes.INSIGHTS -> 1
        Routes.SETTINGS -> 2
        else -> -1
    }

    val m3StandardEasing = remember { CubicBezierEasing(0.2f, 0f, 0f, 1f) }

    OutloopCanvas {
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isWideScreen = maxWidth >= 600.dp
                    val bottomContentPadding = if (!isWideScreen && selectedBottomTab != null) {
                        com.example.ui.theme.OutloopMetrics.BottomNavContentPadding
                    } else {
                        28.dp
                    }

                    Row(modifier = Modifier.fillMaxSize()) {
                        if (isWideScreen && selectedBottomTab != null) {
                            OutloopSideRail(
                                selectedTab = selectedBottomTab,
                                onTabSelected = ::navigateToTopLevel,
                                alerts = navAlerts
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = startDestination,
                                enterTransition = {
                                    val fromTab = tabOrder(initialState.destination.route)
                                    val toTab = tabOrder(targetState.destination.route)
                                    if (fromTab >= 0 && toTab >= 0) {
                                        val forward = toTab >= fromTab
                                        fadeIn(tween(220, easing = m3StandardEasing)) + slideInHorizontally(
                                            animationSpec = tween(280, easing = m3StandardEasing)
                                        ) { if (forward) it / 5 else -it / 5 }
                                    } else {
                                        fadeIn(tween(240, easing = m3StandardEasing)) + slideInHorizontally(
                                            animationSpec = tween(300, easing = m3StandardEasing)
                                        ) { it / 3 }
                                    }
                                },
                                exitTransition = {
                                    val fromTab = tabOrder(initialState.destination.route)
                                    val toTab = tabOrder(targetState.destination.route)
                                    if (fromTab >= 0 && toTab >= 0) {
                                        val forward = toTab >= fromTab
                                        fadeOut(tween(180, easing = m3StandardEasing)) + slideOutHorizontally(
                                            animationSpec = tween(260, easing = m3StandardEasing)
                                        ) { if (forward) -it / 5 else it / 5 }
                                    } else {
                                        fadeOut(tween(180, easing = m3StandardEasing)) + slideOutHorizontally(
                                            animationSpec = tween(280, easing = m3StandardEasing)
                                        ) { -it / 5 }
                                    }
                                },
                                popEnterTransition = {
                                    fadeIn(tween(240, easing = m3StandardEasing)) + slideInHorizontally(
                                        animationSpec = tween(300, easing = m3StandardEasing)
                                    ) { -it / 5 }
                                },
                                popExitTransition = {
                                    fadeOut(tween(180, easing = m3StandardEasing)) + slideOutHorizontally(
                                        animationSpec = tween(300, easing = m3StandardEasing)
                                    ) { it / 3 }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (currentRoute != Routes.ONBOARDING) {
                                            Modifier.windowInsetsPadding(WindowInsets.statusBars)
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                composable(Routes.ONBOARDING) {
                                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                                        OnboardingScreen(
                                            rules = uiState.rules,
                                            installedPackages = uiState.installedPackages,
                                            defaultMode = uiState.preferences.defaultInterventionMode,
                                            isAccessibilityEnabled = uiState.isAccessibilityEnabled,
                                            isServiceConnected = uiState.isServiceConnected,
                                            isBatteryOptimizationIgnored = uiState.isBatteryOptimizationIgnored,
                                            isNotificationPermissionGranted = uiState.isNotificationPermissionGranted,
                                            onRefreshProtectionHealth = onRefreshProtectionHealth,
                                            onToggleApp = onToggleApp,
                                            onSelectDefaultMode = { mode ->
                                                onSelectDefaultMode(mode, true)
                                            },
                                            onAcceptDisclosure = onAcceptDisclosure,
                                            onDeclineDisclosure = {
                                                onDeclineDisclosure()
                                                navigateToHomeClearingOnboarding()
                                            },
                                            onOpenPrivacyDetails = {
                                                navController.navigate(Routes.PRIVACY)
                                            },
                                            onCompleteOnboarding = {
                                                onCompleteOnboarding()
                                                navigateToHomeClearingOnboarding()
                                            },
                                            onGoToSettings = {
                                                onCompleteOnboarding()
                                                navigateToHomeClearingOnboarding()
                                                navigateToTopLevel(BottomTab.SETTINGS)
                                            },
                                            hasScannedInstalledApps = uiState.hasScannedInstalledApps
                                        )
                                    }
                                }

                                composable(Routes.HOME) {
                                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                                        HomeScreen(
                                            uiState = uiState,
                                            onStatusClick = {
                                                when (uiState.protectionStatus) {
                                                    ProtectionStatus.PERMISSION_NEEDED ->
                                                        navController.navigate(Routes.ONBOARDING)
                                                    ProtectionStatus.SERVICE_DISCONNECTED ->
                                                        navController.navigate(Routes.DIAGNOSTICS)
                                                    ProtectionStatus.NO_APPS_ACTIVE ->
                                                        navController.navigate(Routes.MANAGE_APPS)
                                                    else ->
                                                        navigateToTopLevel(BottomTab.SETTINGS)
                                                }
                                            },
                                            onSetupClick = {
                                                if (uiState.isAccessibilityEnabled && !uiState.isServiceConnected) {
                                                    navController.navigate(Routes.DIAGNOSTICS)
                                                } else {
                                                    navController.navigate(Routes.ONBOARDING)
                                                }
                                            },
                                            onManageAppsClick = {
                                                navController.navigate(Routes.MANAGE_APPS)
                                            },
                                            onToggleGlobalPause = onToggleGlobalPause,
                                            onPauseForMinutes = onPauseForMinutes,
                                            onGlobalBreakExpired = onGlobalBreakExpired,
                                            onAppSnoozeExpired = onAppSnoozeExpired,
                                            onRefreshBlockersClick = onRefreshBlockersClick,
                                            onAppClick = { pkg ->
                                                navController.navigate(Routes.appDetail(pkg))
                                            },
                                            onToggleApp = onToggleApp,
                                            onShowEstimateToast = { msg -> uiToastMessage = msg },
                                            onResumeQuietHoursEarly = onResumeQuietHoursEarly,
                                            onRefreshProtectionHealth = onRefreshProtectionHealth,
                                            bottomContentPadding = bottomContentPadding
                                        )
                                    }
                                }

                                composable(Routes.INSIGHTS) {
                                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                                        InsightsScreen(
                                            uiState = uiState,
                                            bottomContentPadding = bottomContentPadding
                                        )
                                    }
                                }

                                composable(Routes.SETTINGS) {
                                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                                        SettingsScreen(
                                            uiState = uiState,
                                            onOpenManageApps = {
                                                navController.navigate(Routes.MANAGE_APPS)
                                            },
                                            onOpenPrivacy = {
                                                navController.navigate(Routes.PRIVACY)
                                            },
                                            onOpenDiagnostics = {
                                                navController.navigate(Routes.DIAGNOSTICS)
                                            },
                                            onSelectDefaultMode = { mode ->
                                                onSelectDefaultMode(mode, true)
                                            },
                                            onSelectAppearanceMode = onSelectAppearanceMode,
                                            onSetReclaimedSeconds = onSetReclaimedSeconds,
                                            onSetHapticFeedback = onSetHapticFeedback,
                                            onSetShowBanner = onSetShowBanner,
                                            onClearHistory = onClearHistory,
                                            onShowToast = { msg -> uiToastMessage = msg },
                                            onSetQuietHours = onSetQuietHours,
                                            onSetDailyBudget = onSetDailyBudget,
                                            onExportBackupUri = onExportBackupUri,
                                            onInspectBackupUri = onInspectBackupUri,
                                            onRestoreBackupJson = onRestoreBackupJson,
                                            bottomContentPadding = bottomContentPadding
                                        )
                                    }
                                }

                                composable(Routes.MANAGE_APPS) {
                                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                                        ManageAppsScreen(
                                            rules = uiState.rules,
                                            installedPackages = uiState.installedPackages,
                                            onToggleApp = onToggleApp,
                                            onBack = { navController.popBackStack() },
                                            launchableApps = uiState.launchableApps,
                                            onSaveCustomRule = onSaveCustomRule,
                                            onDeleteCustomRule = onDeleteCustomRule,
                                            onShowToast = { msg -> uiToastMessage = msg },
                                            hasScannedInstalledApps = uiState.hasScannedInstalledApps
                                        )
                                    }
                                }

                                composable(Routes.PRIVACY) {
                                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                                        PrivacyScreen(onBack = { navController.popBackStack() })
                                    }
                                }

                                composable(Routes.DIAGNOSTICS) {
                                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                                        DiagnosticsScreen(
                                            uiState = uiState,
                                            onRefreshHealth = onRefreshProtectionHealth,
                                            onCheckRulesNow = onRefreshBlockersClick,
                                            onSetAutoUpdateRules = onSetAutoUpdateRules,
                                            onSetEventCaptureEnabled = onSetEventCaptureEnabled,
                                            onShareEventLog = onShareEventLog,
                                            onClearEventLog = onClearEventLog,
                                            onShowToast = { msg -> uiToastMessage = msg },
                                            onBack = { navController.popBackStack() }
                                        )
                                    }
                                }

                                composable(
                                    route = Routes.APP_DETAIL,
                                    arguments = listOf(navArgument("packageName") { type = NavType.StringType })
                                ) { entry ->
                                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                                        val pkg = entry.arguments?.getString("packageName").orEmpty()
                                        val rule = uiState.rules.firstOrNull { it.packageName == pkg }
                                        AppDetailScreen(
                                            rule = rule,
                                            onBack = { navController.popBackStack() },
                                            onToggleEnabled = { enabled -> onToggleApp(pkg, enabled) },
                                            onSelectMode = { mode -> onSelectMode(pkg, mode) },
                                            onChangeCooldown = { ms -> onChangeCooldown(pkg, ms) },
                                            onAllowOnce = { onAllowOnceForApp(pkg) },
                                            onSnoozeMinutes = { minutes -> onSnoozeMinutes(pkg, minutes) },
                                            onResumeSnooze = { onAppSnoozeExpired(pkg) },
                                            onShowToast = { msg -> uiToastMessage = msg },
                                            currentTimeMs = uiState.currentTimeMs
                                        )
                                    }
                                }
                            }

                            // Bottom overlay container (toasts + intervention quick-pass bar + floating pill dock)
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .widthIn(max = com.example.ui.theme.OutloopMetrics.MaxContentWidth - 20.dp)
                                    .then(
                                        if (isWideScreen) Modifier.padding(bottom = 12.dp) else Modifier
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AnimatedVisibility(
                                    visible = uiToastMessage != null,
                                    enter = slideInVertically { it } + fadeIn(),
                                    exit = slideOutVertically { it } + fadeOut()
                                ) {
                                    uiToastMessage?.let { msg ->
                                        OutloopToastPill(
                                            message = msg,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                    }
                                }

                                if (selectedBottomTab != null) {
                                    AnimatedVisibility(
                                        visible = uiState.recentToast != null,
                                        enter = slideInVertically { it } + fadeIn(),
                                        exit = slideOutVertically { it } + fadeOut()
                                    ) {
                                        uiState.recentToast?.let { toast ->
                                            InterventionBanner(
                                                toast = toast,
                                                onAllowOnce = { onAllowOnceFromToast(toast) },
                                                onUndo = { onUndoFromToast(toast) },
                                                onDismiss = onDismissToast
                                            )
                                        }
                                    }

                                    if (!isWideScreen) {
                                        FloatingPillNavBar(
                                            selectedTab = selectedBottomTab,
                                            onTabSelected = ::navigateToTopLevel,
                                            alerts = navAlerts
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
