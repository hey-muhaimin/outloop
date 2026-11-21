package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.TargetAppRuleEntity
import com.example.data.model.InterventionMode
import com.example.service.AccessibilityStatusHelper
import com.example.ui.components.OutloopAppMonogramBadge
import com.example.ui.components.OutloopBackButton
import com.example.ui.components.OutloopHairlineDivider
import com.example.ui.components.OutloopIconBadge
import com.example.ui.components.OutloopMark
import com.example.ui.components.OutloopModalSheet
import com.example.ui.components.OutloopPrimaryButton
import com.example.ui.components.OutloopRadioOptionCard
import com.example.ui.components.OutloopSecondaryButton
import com.example.ui.components.OutloopSimulationOverlay
import com.example.ui.components.OutloopStaggeredEntrance
import com.example.ui.components.OutloopStatusBannerCard
import com.example.ui.components.OutloopStatusPill
import com.example.ui.components.OutloopStepCircleBadge
import com.example.ui.components.OutloopSwitch
import com.example.ui.components.OutloopTextButton
import com.example.ui.components.OutloopTonalCard
import com.example.ui.components.OutloopWelcomeArtwork
import com.example.ui.components.PillTone
import com.example.ui.components.StatusBannerTone
import com.example.ui.components.outloopPressScale
import com.example.ui.theme.OutloopMetrics
import com.example.ui.theme.OutloopTheme
import kotlinx.coroutines.delay

enum class OnboardingStep {
    WELCOME,
    PRIVACY,
    CHOOSE_APPS,
    STYLE,
    ALLOW_ACCESS,
    CHECKING,
    PROBLEM,
    READY
}

@Composable
fun OnboardingScreen(
    rules: List<TargetAppRuleEntity>,
    installedPackages: Set<String>,
    defaultMode: InterventionMode,
    isAccessibilityEnabled: Boolean,
    isServiceConnected: Boolean,
    isBatteryOptimizationIgnored: Boolean,
    isNotificationPermissionGranted: Boolean,
    onRefreshProtectionHealth: () -> Unit,
    onToggleApp: (String, Boolean) -> Unit,
    onSelectDefaultMode: (InterventionMode) -> Unit,
    onAcceptDisclosure: () -> Unit,
    onDeclineDisclosure: () -> Unit,
    onOpenPrivacyDetails: () -> Unit,
    onCompleteOnboarding: () -> Unit,
    onGoToSettings: () -> Unit,
    hasScannedInstalledApps: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = OutloopTheme.colors
    val easing = remember { CubicBezierEasing(0.2f, 0f, 0f, 1f) }

    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }
    var awaitingPermissionReturn by rememberSaveable { mutableStateOf(false) }
    var checkAttemptKey by rememberSaveable { mutableIntStateOf(0) }
    var showWhySheet by rememberSaveable { mutableStateOf(false) }
    var showSimulationOverlay by rememberSaveable { mutableStateOf(false) }
    var simulationFeedback by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onRefreshProtectionHealth()
    }

    // Re-check Accessibility permission when returning from Android Settings
    DisposableEffect(lifecycleOwner, step, awaitingPermissionReturn) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshProtectionHealth()
                val enabledNow = AccessibilityStatusHelper.isServiceEnabled(context)
                if ((step == OnboardingStep.ALLOW_ACCESS || step == OnboardingStep.PROBLEM) && awaitingPermissionReturn) {
                    awaitingPermissionReturn = false
                    if (enabledNow) {
                        step = OnboardingStep.CHECKING
                        checkAttemptKey++
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Real-time transition if Accessibility turns on while in ALLOW_ACCESS or PROBLEM (e.g. split-screen)
    LaunchedEffect(step, isAccessibilityEnabled, isServiceConnected) {
        if ((step == OnboardingStep.ALLOW_ACCESS || step == OnboardingStep.PROBLEM) && isAccessibilityEnabled) {
            awaitingPermissionReturn = false
            step = if (isServiceConnected) OnboardingStep.READY else OnboardingStep.CHECKING
            if (step == OnboardingStep.CHECKING) checkAttemptKey++
        } else if (step == OnboardingStep.CHECKING && isAccessibilityEnabled && isServiceConnected) {
            delay(350L)
            step = OnboardingStep.READY
        }
    }

    // Probe real service connection while in CHECKING state
    LaunchedEffect(step, checkAttemptKey) {
        if (step == OnboardingStep.CHECKING) {
            var waitedMs = 0L
            while (waitedMs < 1800L) {
                delay(250L)
                waitedMs += 250L
                val enabledNow = AccessibilityStatusHelper.isServiceEnabled(context)
                if (enabledNow && isServiceConnected) {
                    delay(400L)
                    step = OnboardingStep.READY
                    return@LaunchedEffect
                }
            }
            val finalEnabled = AccessibilityStatusHelper.isServiceEnabled(context)
            step = if (finalEnabled && isServiceConnected) {
                OnboardingStep.READY
            } else {
                OnboardingStep.PROBLEM
            }
        }
    }

    BackHandler(enabled = step != OnboardingStep.WELCOME) {
        step = when (step) {
            OnboardingStep.WELCOME -> OnboardingStep.WELCOME
            OnboardingStep.PRIVACY -> OnboardingStep.WELCOME
            OnboardingStep.CHOOSE_APPS -> OnboardingStep.PRIVACY
            OnboardingStep.STYLE -> OnboardingStep.CHOOSE_APPS
            OnboardingStep.ALLOW_ACCESS -> OnboardingStep.STYLE
            OnboardingStep.CHECKING -> OnboardingStep.ALLOW_ACCESS
            OnboardingStep.PROBLEM -> OnboardingStep.ALLOW_ACCESS
            OnboardingStep.READY -> OnboardingStep.ALLOW_ACCESS
        }
    }

    val openAccessibilitySettings = {
        awaitingPermissionReturn = true
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
        Unit
    }

    val openBatterySettings = {
        runCatching {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(PowerManager::class.java)
                if (pm?.isIgnoringBatteryOptimizations(context.packageName) == false) {
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                } else {
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                }
            } else {
                Intent(Settings.ACTION_SETTINGS)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
        Unit
    }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val forward = targetState.ordinal >= initialState.ordinal
            (fadeIn(tween(220, easing = easing)) + slideInHorizontally(
                animationSpec = tween(260, easing = easing)
            ) { width -> if (forward) width / 5 else -width / 5 }) togetherWith
                (fadeOut(tween(180, easing = easing)) + slideOutHorizontally(
                    animationSpec = tween(240, easing = easing)
                ) { width -> if (forward) -width / 5 else width / 5 })
        },
        label = "outloopOnboardingStep",
        modifier = Modifier.fillMaxSize()
    ) { currentStep ->
        when (currentStep) {
            OnboardingStep.WELCOME -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.welcomeHero),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Upper 40% hero fold with compact wordmark and breathing open-loop mark
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.40f)
                            .windowInsetsPadding(WindowInsets.statusBars)
                    ) {
                        OutloopWelcomeArtwork(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 36.dp, bottom = 8.dp)
                        )

                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(
                                    horizontal = OutloopMetrics.ScreenHorizontalPadding,
                                    vertical = 12.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            OutloopMark(size = 20.dp, color = Color(0xFFFAF9F5))
                            Text(
                                text = "outloop",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color(0xFFFAF9F5)
                            )
                        }
                    }

                    // Lower 60% crisp tonal copy fold aligned directly with all other screens
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.60f),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        val sheetShape = RoundedCornerShape(
                            topStart = OutloopMetrics.SheetCornerRadius,
                            topEnd = OutloopMetrics.SheetCornerRadius
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = OutloopMetrics.MaxContentWidth)
                                .clip(sheetShape)
                                .background(colors.sheetSurface)
                                .border(1.dp, colors.hairlineBorder, sheetShape)
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .verticalScroll(rememberScrollState())
                                .padding(
                                    horizontal = OutloopMetrics.ScreenHorizontalPadding,
                                    vertical = 20.dp
                                ),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutloopStaggeredEntrance(index = 0) {
                                Column(verticalArrangement = Arrangement.spacedBy(OutloopMetrics.ItemSpacing)) {
                                    Text(
                                        text = "Keep your apps. Reclaim your attention.",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.textAction
                                    )
                                    Text(
                                        text = "Make room for what matters.",
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = colors.ink
                                    )
                                    Text(
                                        text = "Stay connected to the apps you need without getting pulled into feeds you didn\u2019t come for.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colors.secondaryInk
                                    )
                                }
                            }

                            OutloopStaggeredEntrance(index = 1) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 18.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    OutloopPrimaryButton(
                                        text = "Get started",
                                        onClick = { step = OnboardingStep.PRIVACY },
                                        modifier = Modifier.testTag("onboarding_get_started")
                                    )
                                    OutloopTextButton(
                                        text = "Not now",
                                        onClick = onDeclineDisclosure,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("onboarding_decline")
                                    )
                                    Text(
                                        text = "Not now leaves protection off until you turn it on.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.secondaryInk,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                val stepIndex = when (currentStep) {
                    OnboardingStep.PRIVACY -> 1
                    OnboardingStep.CHOOSE_APPS -> 2
                    OnboardingStep.STYLE -> 3
                    OnboardingStep.ALLOW_ACCESS,
                    OnboardingStep.CHECKING,
                    OnboardingStep.PROBLEM,
                    OnboardingStep.READY -> 4
                    OnboardingStep.WELCOME -> 1
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = OutloopMetrics.MaxContentWidth)
                    ) {
                        if (currentStep != OnboardingStep.READY) {
                            OnboardingTopBar(
                                currentStep = stepIndex,
                                totalSteps = 4,
                                showBack = currentStep != OnboardingStep.CHECKING,
                                onBack = {
                                    step = when (currentStep) {
                                        OnboardingStep.PRIVACY -> OnboardingStep.WELCOME
                                        OnboardingStep.CHOOSE_APPS -> OnboardingStep.PRIVACY
                                        OnboardingStep.STYLE -> OnboardingStep.CHOOSE_APPS
                                        OnboardingStep.ALLOW_ACCESS,
                                        OnboardingStep.PROBLEM -> OnboardingStep.STYLE
                                        else -> OnboardingStep.ALLOW_ACCESS
                                    }
                                }
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(
                                    horizontal = OutloopMetrics.ScreenHorizontalPadding,
                                    vertical = OutloopMetrics.SectionSpacing
                                ),
                            verticalArrangement = Arrangement.spacedBy(OutloopMetrics.SectionSpacing)
                        ) {
                            when (currentStep) {
                                OnboardingStep.PRIVACY -> {
                                    PrivacyConsentContent(onOpenPrivacyDetails = onOpenPrivacyDetails)
                                }

                                OnboardingStep.CHOOSE_APPS -> {
                                    ChooseAppsContent(
                                        rules = rules,
                                        installedPackages = installedPackages,
                                        hasScannedInstalledApps = hasScannedInstalledApps,
                                        onToggleApp = onToggleApp
                                    )
                                }

                                OnboardingStep.STYLE -> {
                                    HowItStepsInContent(
                                        selectedMode = defaultMode,
                                        onSelectMode = onSelectDefaultMode,
                                        onOpenSimulation = { showSimulationOverlay = true }
                                    )
                                }

                                OnboardingStep.ALLOW_ACCESS -> {
                                    AllowAccessWaitingContent()
                                }

                                OnboardingStep.CHECKING -> {
                                    AllowAccessCheckingContent(
                                        rules = rules,
                                        installedPackages = installedPackages
                                    )
                                }

                                OnboardingStep.PROBLEM -> {
                                    AllowAccessProblemContent(
                                        isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
                                        onOpenBatterySettings = openBatterySettings
                                    )
                                }

                                OnboardingStep.READY -> {
                                    ReadyContent(
                                        rules = rules,
                                        installedPackages = installedPackages,
                                        selectedMode = defaultMode
                                    )
                                }

                                OnboardingStep.WELCOME -> Unit
                            }
                        }

                        // Sticky bottom action footer using shared compact metrics
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = OutloopMetrics.ScreenHorizontalPadding,
                                    vertical = 12.dp
                                ),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            when (currentStep) {
                                OnboardingStep.PRIVACY -> {
                                    OutloopPrimaryButton(
                                        text = "Agree and continue",
                                        onClick = {
                                            onAcceptDisclosure()
                                            step = OnboardingStep.CHOOSE_APPS
                                        },
                                        modifier = Modifier.testTag("onboarding_agree")
                                    )
                                    OutloopTextButton(
                                        text = "Not now",
                                        onClick = onDeclineDisclosure,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                OnboardingStep.CHOOSE_APPS -> {
                                    val selectedCount = rules.count {
                                        it.enabled && (it.packageName in installedPackages || !it.isBuiltIn)
                                    }
                                    Text(
                                        text = "$selectedCount app${if (selectedCount == 1) "" else "s"} selected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.secondaryInk
                                    )
                                    OutloopPrimaryButton(
                                        text = "Continue",
                                        onClick = { step = OnboardingStep.STYLE },
                                        modifier = Modifier.testTag("onboarding_apps_continue")
                                    )
                                }

                                OnboardingStep.STYLE -> {
                                    OutloopPrimaryButton(
                                        text = "Continue",
                                        onClick = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isNotificationPermissionGranted) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                            if (AccessibilityStatusHelper.isServiceEnabled(context) && isServiceConnected) {
                                                step = OnboardingStep.READY
                                            } else {
                                                step = OnboardingStep.ALLOW_ACCESS
                                            }
                                        },
                                        modifier = Modifier.testTag("onboarding_style_continue")
                                    )
                                }

                                OnboardingStep.ALLOW_ACCESS -> {
                                    OutloopPrimaryButton(
                                        text = "Open Android Settings",
                                        onClick = openAccessibilitySettings,
                                        modifier = Modifier.testTag("onboarding_open_settings")
                                    )
                                    OutloopTextButton(
                                        text = "Why does Android ask for this?",
                                        onClick = { showWhySheet = true },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutloopTextButton(
                                        text = "Continue without protection",
                                        onClick = onCompleteOnboarding,
                                        color = colors.secondaryInk,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("onboarding_skip_protection")
                                    )
                                }

                                OnboardingStep.CHECKING -> {
                                    OutloopPrimaryButton(
                                        text = "Checking\u2026",
                                        onClick = {},
                                        enabled = false
                                    )
                                    OutloopTextButton(
                                        text = "Open Android Settings",
                                        onClick = openAccessibilitySettings,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                OnboardingStep.PROBLEM -> {
                                    OutloopPrimaryButton(
                                        text = "Check again",
                                        onClick = {
                                            if (AccessibilityStatusHelper.isServiceEnabled(context)) {
                                                step = OnboardingStep.CHECKING
                                                checkAttemptKey++
                                            } else {
                                                openAccessibilitySettings()
                                            }
                                        },
                                        modifier = Modifier.testTag("onboarding_check_again")
                                    )
                                    OutloopSecondaryButton(
                                        text = "Open Android Settings",
                                        onClick = openAccessibilitySettings
                                    )
                                    OutloopTextButton(
                                        text = "Continue without protection",
                                        onClick = onCompleteOnboarding,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                OnboardingStep.READY -> {
                                    OutloopPrimaryButton(
                                        text = "Go to Home",
                                        onClick = onCompleteOnboarding,
                                        modifier = Modifier.testTag("onboarding_finish")
                                    )
                                    OutloopTextButton(
                                        text = "Change these in Settings",
                                        onClick = {
                                            onCompleteOnboarding()
                                            onGoToSettings()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                OnboardingStep.WELCOME -> Unit
                            }
                        }
                    }
                }
            }
        }
    }

    if (showWhySheet) {
        OutloopModalSheet(
            title = "Why does Android ask?",
            onDismiss = { showWhySheet = false }
        ) {
            Text(
                text = "Short-form feeds can\u2019t be recognised any other way. Android requires your explicit permission before any app can observe which screen is open, and you can withdraw it at any time in Android Settings.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.secondaryInk
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutloopSecondaryButton(
                text = "Done",
                onClick = { showWhySheet = false }
            )
        }
    }

    if (showSimulationOverlay) {
        val sampleRule = rules.firstOrNull { it.enabled && (it.packageName in installedPackages || !it.isBuiltIn) }
            ?: rules.firstOrNull { it.enabled }
            ?: rules.firstOrNull()
        OutloopSimulationOverlay(
            appName = sampleRule?.displayName ?: "Instagram",
            feedLabel = sampleRule?.feedLabel ?: "Reels",
            onActionFeedback = { msg -> simulationFeedback = msg },
            onDismiss = { showSimulationOverlay = false }
        )
    }

    if (simulationFeedback != null) {
        OutloopModalSheet(
            title = "Preview complete",
            onDismiss = { simulationFeedback = null }
        ) {
            Text(
                text = simulationFeedback.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.secondaryInk
            )
            OutloopSecondaryButton(
                text = "Done",
                onClick = { simulationFeedback = null }
            )
        }
    }
}

@Composable
private fun OnboardingTopBar(
    currentStep: Int,
    totalSteps: Int,
    showBack: Boolean,
    onBack: () -> Unit
) {
    val colors = OutloopTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = OutloopMetrics.TopBarStartPadding,
                end = OutloopMetrics.ScreenHorizontalPadding,
                top = OutloopMetrics.TopBarTopPadding
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showBack) {
            OutloopBackButton(onClick = onBack)
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            for (i in 1..totalSteps) {
                val segColor by animateColorAsState(
                    targetValue = if (i <= currentStep) colors.primaryTint else colors.divider,
                    animationSpec = tween(220),
                    label = "stepBar_$i"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.5.dp)
                        .clip(CircleShape)
                        .background(segColor)
                )
            }
        }
        Text(
            text = "Step $currentStep of $totalSteps",
            style = MaterialTheme.typography.labelLarge,
            color = colors.secondaryInk
        )
    }
}

@Composable
private fun PrivacyConsentContent(onOpenPrivacyDetails: () -> Unit) {
    val colors = OutloopTheme.colors
    OutloopStaggeredEntrance(index = 0) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Here\u2019s exactly what Outloop can see.",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.ink
            )
            Text(
                text = "Outloop uses Android\u2019s Accessibility Service. Before you turn it on, here\u2019s what that means.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.secondaryInk
            )
        }
    }
    OutloopStaggeredEntrance(index = 1) {
        OutloopTonalCard(contentPadding = OutloopMetrics.CardPadding) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                InfoPointRow(
                    icon = Icons.Outlined.Visibility,
                    title = "What it looks at",
                    body = "Technical screen labels in the apps you choose, only to tell whether a Shorts, Reels or Watch feed is open."
                )
                InfoPointRow(
                    icon = Icons.Outlined.Block,
                    title = "What it doesn\u2019t read",
                    body = "Your messages, your passwords or the videos you watch."
                )
                InfoPointRow(
                    icon = Icons.Outlined.PhoneAndroid,
                    title = "Where it stays",
                    body = "Detection runs strictly on your phone. Nothing is collected off-device or shared with third parties. Troubleshooting logs remain off unless you turn them on."
                )
            }
        }
    }
    OutloopStaggeredEntrance(index = 2) {
        val context = LocalContext.current
        val policyUrl = androidx.compose.ui.res.stringResource(com.example.R.string.privacy_policy_url)
        OutloopStatusBannerCard(
            tone = StatusBannerTone.INFO,
            icon = Icons.Outlined.Info,
            title = "You\u2019re in control",
            body = "Turn this off any time in Android Settings.",
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Read the full privacy details",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.infoText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenPrivacyDetails)
                            .padding(vertical = 4.dp)
                    )
                    Text(
                        text = "Privacy policy",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.infoText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(policyUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                            }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        )
    }
}

@Composable
private fun ChooseAppsContent(
    rules: List<TargetAppRuleEntity>,
    installedPackages: Set<String>,
    hasScannedInstalledApps: Boolean,
    onToggleApp: (String, Boolean) -> Unit
) {
    val colors = OutloopTheme.colors
    OutloopStaggeredEntrance(index = 0) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Choose your apps",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.ink
            )
            Text(
                text = "Outloop only steps in on short-form feeds in the apps installed on your phone.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.secondaryInk
            )
        }
    }

    val installedRules = if (hasScannedInstalledApps) {
        rules.filter { it.packageName in installedPackages || !it.isBuiltIn }
    } else {
        emptyList()
    }
    val notInstalledRules = if (hasScannedInstalledApps) {
        rules.filter { it.packageName !in installedPackages && it.isBuiltIn }
    } else {
        rules
    }

    OutloopStaggeredEntrance(index = 1) {
        Column(verticalArrangement = Arrangement.spacedBy(OutloopMetrics.ItemSpacing)) {
            Text(
                text = "On your phone",
                style = MaterialTheme.typography.labelMedium,
                color = colors.secondaryInk,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            if (installedRules.isNotEmpty()) {
                OutloopTonalCard(cornerRadius = OutloopMetrics.CardCornerRadius, contentPadding = PaddingValues(0.dp)) {
                    installedRules.forEachIndexed { index, rule ->
                        if (index > 0) OutloopHairlineDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = OutloopMetrics.RowMinHeight)
                                .padding(
                                    horizontal = OutloopMetrics.ListRowPaddingHorizontal,
                                    vertical = OutloopMetrics.ListRowPaddingVertical
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutloopAppMonogramBadge(
                                appName = rule.displayName,
                                packageName = rule.packageName
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rule.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.ink
                                )
                                Text(
                                    text = "Interrupts ${rule.feedLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryInk
                                )
                            }
                            OutloopSwitch(
                                checked = rule.enabled,
                                onCheckedChange = { enabled -> onToggleApp(rule.packageName, enabled) },
                                contentDescription = "Protect ${rule.displayName}",
                                modifier = Modifier.testTag("onboarding_app_switch_${rule.packageName}")
                            )
                        }
                    }
                }
            } else {
                OutloopTonalCard(
                    cornerRadius = OutloopMetrics.SubCardCornerRadius,
                    contentPadding = OutloopMetrics.CompactCardPadding
                ) {
                    Text(
                        text = if (hasScannedInstalledApps) {
                            "No supported short-form video apps were detected on this phone yet. You can install one anytime or add a custom app later from Manage Apps."
                        } else {
                            "Checking installed apps on this phone\u2026"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.secondaryInk
                    )
                }
            }
        }
    }

    if (notInstalledRules.isNotEmpty()) {
        OutloopStaggeredEntrance(index = 2) {
            Column(verticalArrangement = Arrangement.spacedBy(OutloopMetrics.ItemSpacing)) {
                Text(
                    text = "Not installed",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.secondaryInk,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
                OutloopTonalCard(
                    cornerRadius = OutloopMetrics.CardCornerRadius,
                    contentPadding = PaddingValues(0.dp),
                    containerColor = colors.surfaceSubtle,
                    modifier = Modifier.alpha(0.75f)
                ) {
                    notInstalledRules.forEachIndexed { index, rule ->
                        if (index > 0) OutloopHairlineDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = OutloopMetrics.RowMinHeight)
                                .padding(
                                    horizontal = OutloopMetrics.ListRowPaddingHorizontal,
                                    vertical = OutloopMetrics.ListRowPaddingVertical
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutloopAppMonogramBadge(appName = rule.displayName)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rule.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.ink
                                )
                                Text(
                                    text = "${rule.feedLabel} \u00B7 not installed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryInk
                                )
                            }
                            OutloopSwitch(
                                checked = false,
                                enabled = false,
                                onCheckedChange = {},
                                contentDescription = "${rule.displayName} not installed"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HowItStepsInContent(
    selectedMode: InterventionMode,
    onSelectMode: (InterventionMode) -> Unit,
    onOpenSimulation: () -> Unit
) {
    val colors = OutloopTheme.colors
    OutloopStaggeredEntrance(index = 0) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "How should Outloop step in?",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.ink
            )
            Text(
                text = "Pick what happens when a feed opens. You can change this per app later.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.secondaryInk
            )
        }
    }

    OutloopStaggeredEntrance(index = 1) {
        Column(verticalArrangement = Arrangement.spacedBy(OutloopMetrics.ItemSpacing)) {
            listOf(InterventionMode.OVERLAY, InterventionMode.BACK, InterventionMode.HOME_TAB).forEach { mode ->
                OutloopRadioOptionCard(
                    title = mode.shortLabel,
                    subtitle = mode.explanation,
                    selected = selectedMode == mode,
                    badgeText = if (mode == InterventionMode.OVERLAY) "Recommended" else null,
                    onClick = { onSelectMode(mode) },
                    modifier = Modifier.testTag("onboarding_mode_${mode.name.lowercase()}")
                )
            }
        }
    }

    OutloopStaggeredEntrance(index = 2) {
        if (selectedMode == InterventionMode.OVERLAY) {
            OutloopTonalCard(
                cornerRadius = OutloopMetrics.CardCornerRadius,
                contentPadding = OutloopMetrics.CompactCardPadding
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Preview",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.ink
                    )
                    OutloopStatusPill(
                        text = "Simulation, not a real feed",
                        tone = PillTone.NEUTRAL
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(OutloopMetrics.SubCardCornerRadius))
                        .background(colors.surfaceSubtle)
                        .border(1.dp, colors.hairlineBorder, RoundedCornerShape(OutloopMetrics.SubCardCornerRadius))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutloopMark(size = 22.dp, color = colors.primaryTint)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Take a moment.",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.ink
                    )
                    Text(
                        text = "Is this what you came here for?",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryInk
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val primaryInteraction = remember { MutableInteractionSource() }
                        val allowInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .outloopPressScale(primaryInteraction)
                                .heightIn(min = OutloopMetrics.ButtonMinHeight)
                                .clip(CircleShape)
                                .background(colors.primaryTint)
                                .clickable(
                                    interactionSource = primaryInteraction,
                                    indication = null,
                                    onClick = onOpenSimulation
                                )
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Return to my intention",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.onPrimaryTint,
                                textAlign = TextAlign.Center
                            )
                        }
                        Box(
                            modifier = Modifier
                                .outloopPressScale(allowInteraction)
                                .heightIn(min = OutloopMetrics.ButtonMinHeight)
                                .clip(CircleShape)
                                .background(colors.surfaceElevated)
                                .border(1.dp, colors.hairlineBorder, CircleShape)
                                .clickable(
                                    interactionSource = allowInteraction,
                                    indication = null,
                                    onClick = onOpenSimulation
                                )
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Allow once",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.ink
                            )
                        }
                    }
                }
            }
        } else {
            OutloopTonalCard(
                cornerRadius = OutloopMetrics.CardCornerRadius,
                contentPadding = OutloopMetrics.CompactCardPadding
            ) {
                InfoPointRow(
                    icon = if (selectedMode == InterventionMode.BACK) {
                        Icons.AutoMirrored.Rounded.KeyboardArrowLeft
                    } else {
                        Icons.Outlined.Home
                    },
                    title = selectedMode.shortLabel,
                    body = if (selectedMode == InterventionMode.BACK) {
                        "You\u2019ll be returned to the previous screen automatically."
                    } else {
                        "You\u2019ll land on the app\u2019s main tab instead of the feed."
                    }
                )
            }
        }
    }
}

@Composable
private fun AllowAccessWaitingContent() {
    val colors = OutloopTheme.colors
    OutloopStaggeredEntrance(index = 0) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "One small setup step",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.ink
            )
            Text(
                text = "Outloop uses Android\u2019s Accessibility Service to recognise the feeds you chose and respond the way you picked.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.secondaryInk
            )
        }
    }
    OutloopStaggeredEntrance(index = 1) {
        OutloopStatusBannerCard(
            tone = StatusBannerTone.WARNING,
            icon = Icons.Outlined.Schedule,
            title = "Waiting for permission",
            body = "Outloop will check the connection automatically."
        )
    }
    OutloopStaggeredEntrance(index = 2) {
        OutloopTonalCard(contentPadding = OutloopMetrics.CardPadding) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "In Android Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink
                )
                NumberedStepRow("1", "Open Accessibility settings", "The button below takes you there.")
                NumberedStepRow("2", "Choose Outloop", "Usually under Downloaded apps. Names vary by phone.")
                NumberedStepRow("3", "Turn on Use Outloop", "Then tap Allow.")
            }
        }
    }
}

@Composable
private fun AllowAccessCheckingContent(
    rules: List<TargetAppRuleEntity>,
    installedPackages: Set<String>
) {
    val colors = OutloopTheme.colors
    val selectedNames = rules
        .filter { it.enabled && (it.packageName in installedPackages || !it.isBuiltIn) }
        .joinToString(", ") { it.displayName }
        .ifEmpty { "No installed apps selected yet." }

    OutloopStaggeredEntrance(index = 0) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Checking your protection",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.ink
            )
            Text(
                text = "Android says Outloop is switched on. We\u2019re confirming that the service is connected.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.secondaryInk
            )
        }
    }
    OutloopStaggeredEntrance(index = 1) {
        OutloopStatusBannerCard(
            tone = StatusBannerTone.INFO,
            icon = Icons.Outlined.Schedule,
            title = "Checking your protection service",
            body = "This usually takes a few seconds.",
            action = {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape),
                    color = colors.infoText,
                    trackColor = colors.infoText.copy(alpha = 0.25f)
                )
            }
        )
    }
    OutloopStaggeredEntrance(index = 2) {
        OutloopTonalCard(contentPadding = OutloopMetrics.CardPadding) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    OutloopStepCircleBadge(
                        label = "",
                        tone = StatusBannerTone.SUCCESS,
                        icon = Icons.Rounded.Check
                    )
                    Column {
                        Text(
                            text = "Permission granted",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.ink
                        )
                        Text(
                            text = "Outloop is switched on in Accessibility settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryInk
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    OutloopStepCircleBadge(
                        label = "",
                        tone = StatusBannerTone.INFO,
                        icon = Icons.Outlined.Schedule
                    )
                    Column {
                        Text(
                            text = "Confirming the connection",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.ink
                        )
                        Text(
                            text = "Waiting for Android to report the service as running.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryInk
                        )
                    }
                }
                NumberedStepRow("3", "Getting your apps ready", selectedNames)
            }
        }
    }
}

@Composable
private fun AllowAccessProblemContent(
    isBatteryOptimizationIgnored: Boolean,
    onOpenBatterySettings: () -> Unit
) {
    val colors = OutloopTheme.colors
    OutloopStaggeredEntrance(index = 0) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Almost there",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.ink
            )
            Text(
                text = "Outloop is switched on, but Android isn\u2019t reporting an active connection.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.secondaryInk
            )
        }
    }
    OutloopStaggeredEntrance(index = 1) {
        OutloopStatusBannerCard(
            tone = StatusBannerTone.WARNING,
            icon = Icons.Outlined.WarningAmber,
            title = "Protection isn\u2019t running yet",
            body = "Nothing will be interrupted until the connection is restored."
        )
    }
    OutloopStaggeredEntrance(index = 2) {
        OutloopTonalCard(contentPadding = OutloopMetrics.CardPadding) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Try this",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink
                )
                NumberedStepRow(
                    number = "1",
                    title = "Switch Outloop off, then on again",
                    body = "In Accessibility settings."
                )
                NumberedStepRow(
                    number = "2",
                    title = "Allow background activity",
                    body = if (isBatteryOptimizationIgnored) {
                        "Battery optimization is already unrestricted."
                    } else {
                        "Some phones stop services to save battery. Check Outloop\u2019s battery setting."
                    }
                )
                if (!isBatteryOptimizationIgnored) {
                    OutloopSecondaryButton(
                        text = "Allow unrestricted battery",
                        onClick = onOpenBatterySettings
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadyContent(
    rules: List<TargetAppRuleEntity>,
    installedPackages: Set<String>,
    selectedMode: InterventionMode
) {
    val colors = OutloopTheme.colors
    val selectedList = rules.filter {
        it.enabled && (it.packageName in installedPackages || !it.isBuiltIn)
    }
    val names = selectedList.joinToString(", ") { it.displayName }.ifEmpty { "None yet" }

    OutloopStaggeredEntrance(index = 0) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.size(96.dp)) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceCard)
                        .border(1.dp, colors.hairlineBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    OutloopMark(size = 50.dp, color = colors.primaryTint)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(colors.switchOn),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = "Outloop is ready.",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Protection is on for ${selectedList.size} app${if (selectedList.size == 1) "" else "s"}. You can pause any time from Home.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.secondaryInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            OutloopStatusPill(
                text = "Service connected",
                tone = PillTone.SUCCESS,
                leadingIcon = Icons.Rounded.Check
            )
        }
    }

    OutloopStaggeredEntrance(index = 1) {
        OutloopTonalCard(cornerRadius = OutloopMetrics.CardCornerRadius, contentPadding = PaddingValues(0.dp)) {
            ReadySummaryRow("Apps", names)
            OutloopHairlineDivider()
            ReadySummaryRow("When a feed opens", selectedMode.shortLabel)
            OutloopHairlineDivider()
            ReadySummaryRow("Pause", "5 min, 15 min or 1 hour from Home")
            OutloopHairlineDivider()
            ReadySummaryRow("Privacy", "Detection stays on your device")
        }
    }
}

@Composable
private fun ReadySummaryRow(label: String, value: String) {
    val colors = OutloopTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OutloopMetrics.ListRowPaddingHorizontal, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondaryInk
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = colors.ink
        )
    }
}

@Composable
private fun InfoPointRow(
    icon: ImageVector,
    title: String,
    body: String
) {
    val colors = OutloopTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        OutloopIconBadge(icon = icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.ink
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryInk
            )
        }
    }
}

@Composable
private fun NumberedStepRow(
    number: String,
    title: String,
    body: String
) {
    val colors = OutloopTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        OutloopStepCircleBadge(label = number)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.ink
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryInk
            )
        }
    }
}
