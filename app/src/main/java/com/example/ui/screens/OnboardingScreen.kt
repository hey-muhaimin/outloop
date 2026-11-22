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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.AppPreferences
import com.example.data.local.TargetAppRuleEntity
import com.example.service.AccessibilityStatusHelper
import com.example.ui.components.OutloopAppMonogramBadge
import com.example.ui.components.OutloopBentoCard
import com.example.ui.components.OutloopCheckbox
import com.example.ui.components.OutloopHairlineDivider
import com.example.ui.components.OutloopMark
import com.example.ui.components.OutloopPrimaryButton
import com.example.ui.components.OutloopSecondaryButton
import com.example.ui.components.OutloopStatusBannerCard
import com.example.ui.components.OutloopStatusPill
import com.example.ui.components.OutloopStepCircleBadge
import com.example.ui.components.OutloopTextButton
import com.example.ui.components.PillTone
import com.example.ui.components.StatusBannerTone
import com.example.ui.theme.OutloopTheme
import kotlinx.coroutines.delay

enum class OnboardingStep {
    WELCOME,
    CHOOSE_APPS,
    ALLOW_ACCESS,
    CHECKING,
    PROBLEM,
    READY
}

@Composable
fun OnboardingScreen(
    rules: List<TargetAppRuleEntity>,
    installedPackages: Set<String>,
    preferences: AppPreferences = AppPreferences(),
    isAccessibilityEnabled: Boolean,
    isServiceConnected: Boolean,
    isBatteryOptimizationIgnored: Boolean,
    isNotificationPermissionGranted: Boolean,
    onRefreshProtectionHealth: () -> Unit,
    onToggleApp: (String, Boolean) -> Unit,
    onAcceptDisclosure: () -> Unit,
    onDeclineDisclosure: () -> Unit,
    onOpenPrivacyDetails: () -> Unit,
    onCompleteOnboarding: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = OutloopTheme.colors

    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }
    var awaitingPermissionReturn by rememberSaveable { mutableStateOf(false) }
    var checkAttemptKey by rememberSaveable { mutableIntStateOf(0) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onRefreshProtectionHealth()
    }

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

    LaunchedEffect(step, checkAttemptKey) {
        if (step == OnboardingStep.CHECKING) {
            var waitedMs = 0L
            while (waitedMs < 1800L) {
                delay(250L)
                waitedMs += 250L
                val enabledNow = AccessibilityStatusHelper.isServiceEnabled(context)
                if (enabledNow && isServiceConnected) {
                    delay(450L)
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
            OnboardingStep.CHOOSE_APPS -> OnboardingStep.WELCOME
            OnboardingStep.ALLOW_ACCESS -> OnboardingStep.CHOOSE_APPS
            OnboardingStep.CHECKING -> OnboardingStep.ALLOW_ACCESS
            OnboardingStep.PROBLEM -> OnboardingStep.ALLOW_ACCESS
            OnboardingStep.READY -> OnboardingStep.ALLOW_ACCESS
        }
    }

    val openAccessibilitySettings = {
        onAcceptDisclosure()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isNotificationPermissionGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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

    val stepIndex = when (step) {
        OnboardingStep.WELCOME -> 1
        OnboardingStep.CHOOSE_APPS -> 2
        OnboardingStep.ALLOW_ACCESS,
        OnboardingStep.CHECKING,
        OnboardingStep.PROBLEM -> 3
        OnboardingStep.READY -> 4
    }

    val selectedCount = rules.count {
        it.enabled && (installedPackages.isEmpty() || it.packageName in installedPackages)
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
                .widthIn(max = 600.dp)
        ) {
            OnboardingProgressBarHeader(
                currentStep = stepIndex,
                totalSteps = 4,
                showSkip = step != OnboardingStep.READY && step != OnboardingStep.CHECKING,
                onSkip = onDeclineDisclosure
            )

            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "outloopOnboardingStep",
                modifier = Modifier.weight(1f)
            ) { currentStep ->
                when (currentStep) {
                    OnboardingStep.WELCOME -> {
                        WelcomeBentoStep(
                            onGetStarted = { step = OnboardingStep.CHOOSE_APPS }
                        )
                    }

                    OnboardingStep.CHOOSE_APPS -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                ChooseAppsBentoContent(
                                    rules = rules,
                                    installedPackages = installedPackages,
                                    selectedCount = selectedCount,
                                    onToggleApp = onToggleApp
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutloopSecondaryButton(
                                    text = "Back",
                                    onClick = { step = OnboardingStep.WELCOME },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("onboarding_apps_back")
                                )
                                OutloopPrimaryButton(
                                    text = "Continue",
                                    onClick = {
                                        if (AccessibilityStatusHelper.isServiceEnabled(context) && isServiceConnected) {
                                            onAcceptDisclosure()
                                            step = OnboardingStep.READY
                                        } else {
                                            step = OnboardingStep.ALLOW_ACCESS
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1.35f)
                                        .testTag("onboarding_apps_continue")
                                )
                            }
                        }
                    }

                    OnboardingStep.ALLOW_ACCESS -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                AccessibilityBentoContent(
                                    onOpenPrivacyDetails = onOpenPrivacyDetails
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutloopSecondaryButton(
                                    text = "Not now",
                                    onClick = onDeclineDisclosure,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("onboarding_not_now")
                                )
                                OutloopPrimaryButton(
                                    text = "Open settings",
                                    onClick = openAccessibilitySettings,
                                    modifier = Modifier
                                        .weight(1.35f)
                                        .testTag("onboarding_open_settings")
                                )
                            }
                        }
                    }

                    OnboardingStep.CHECKING -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                AllowAccessCheckingContent(
                                    rules = rules,
                                    installedPackages = installedPackages
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutloopPrimaryButton(
                                    text = "Checking\u2026",
                                    onClick = {},
                                    enabled = false
                                )
                                OutloopTextButton(
                                    text = "Open settings",
                                    onClick = openAccessibilitySettings,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    OnboardingStep.PROBLEM -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                AllowAccessProblemContent(
                                    isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
                                    onOpenBatterySettings = openBatterySettings
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
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
                                    text = "Open settings",
                                    onClick = openAccessibilitySettings
                                )
                                OutloopTextButton(
                                    text = "Continue without Guard",
                                    onClick = onCompleteOnboarding,
                                    color = colors.secondaryInk,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    OnboardingStep.READY -> {
                        val scheduleSummary = if (preferences.quietHoursEnabled) {
                            "${AppPreferences.formatMinutesOfDay(preferences.quietStartMinutes)} \u2013 ${AppPreferences.formatMinutesOfDay(preferences.quietEndMinutes)}"
                        } else {
                            "All day"
                        }
                        Column(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                ReadyBentoContent(
                                    selectedCount = selectedCount,
                                    isAccessibilityActive = isAccessibilityEnabled && isServiceConnected,
                                    scheduleSummary = scheduleSummary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp)
                            ) {
                                OutloopPrimaryButton(
                                    text = "Go to Home",
                                    onClick = onCompleteOnboarding,
                                    modifier = Modifier.testTag("onboarding_finish")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingProgressBarHeader(
    currentStep: Int,
    totalSteps: Int,
    showSkip: Boolean,
    onSkip: () -> Unit
) {
    val colors = OutloopTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 1..totalSteps) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (i <= currentStep) colors.primaryTint else colors.progressTrack)
                )
            }
        }
        if (showSkip) {
            Text(
                text = "Skip",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = colors.secondaryInk,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 10.dp, vertical = 12.dp)
                    .testTag("onboarding_skip")
            )
        }
    }
}

/**
 * Step 1: Balanced ~40% upper rounded dark-forest hero and ~60% breathing room copy fold.
 */
@Composable
private fun WelcomeBentoStep(
    onGetStarted: () -> Unit
) {
    val colors = OutloopTheme.colors
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val availableHeight = maxHeight
        val heroHeight = (availableHeight * 0.37f).coerceIn(152.dp, 250.dp)
        val lowerMinHeight = (availableHeight - heroHeight - 44.dp).coerceAtLeast(0.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            // Upper ~40% fold: compact dark hero box containing the Outloop open-loop mark
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.heroSurface),
                contentAlignment = Alignment.Center
            ) {
                OutloopMark(
                    size = if (heroHeight < 180.dp) 76.dp else 98.dp,
                    color = colors.heroAccent,
                    strokeWidth = if (heroHeight < 180.dp) 11.dp else 14.dp
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Lower ~60% fold: generous breathing room for headline, body, tonal badges, and CTA
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = lowerMinHeight),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Break the loop.\nKeep the apps.",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 35.sp,
                            lineHeight = 41.sp
                        ),
                        color = colors.ink
                    )
                    Text(
                        text = "Outloop blocks endless feeds like Reels and Shorts, and leaves messages, search and everything else open.",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            lineHeight = 24.sp
                        ),
                        color = colors.secondaryInk
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .clip(CircleShape)
                                .background(colors.mintBg)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Feeds blocked",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = colors.mintInk,
                                maxLines = 1
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .clip(CircleShape)
                                .background(colors.peachBg)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Useful parts open",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = colors.peachInk,
                                maxLines = 1
                            )
                        }
                    }
                }

                Column {
                    Spacer(modifier = Modifier.height(20.dp))
                    OutloopPrimaryButton(
                        text = "Get started",
                        onClick = onGetStarted,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .testTag("onboarding_get_started")
                    )
                }
            }
        }
    }
}

@Composable
private fun ChooseAppsBentoContent(
    rules: List<TargetAppRuleEntity>,
    installedPackages: Set<String>,
    selectedCount: Int,
    onToggleApp: (String, Boolean) -> Unit
) {
    val colors = OutloopTheme.colors
    Text(
        text = "Pick the apps to guard",
        style = MaterialTheme.typography.headlineLarge,
        color = colors.ink
    )
    Text(
        text = "Only the feeds are blocked. Messages, search and the rest stay open.",
        style = MaterialTheme.typography.bodyLarge,
        color = colors.secondaryInk
    )

    val installedRules = if (installedPackages.isEmpty()) rules else rules.filter { it.packageName in installedPackages }
    val notInstalledRules = if (installedPackages.isEmpty()) emptyList() else rules.filter { it.packageName !in installedPackages }

    if (installedRules.isNotEmpty()) {
        OutloopBentoCard(cornerRadius = 24.dp, contentPadding = PaddingValues(0.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Feeds to block",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.secondaryInk
                )
                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = colors.ink
                )
            }

            installedRules.forEachIndexed { index, rule ->
                if (index > 0) OutloopHairlineDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 66.dp)
                        .clickable { onToggleApp(rule.packageName, !rule.enabled) }
                        .padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutloopAppMonogramBadge(
                        appName = rule.displayName,
                        packageName = rule.packageName,
                        preferMonogram = true
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rule.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink
                        )
                        Text(
                            text = appFeedSummary(rule),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryInk
                        )
                    }
                    OutloopCheckbox(
                        checked = rule.enabled,
                        onCheckedChange = { enabled -> onToggleApp(rule.packageName, enabled) },
                        contentDescription = "Guard ${rule.displayName}",
                        modifier = Modifier.testTag("onboarding_app_switch_${rule.packageName}")
                    )
                }
            }
        }
    }

    if (notInstalledRules.isNotEmpty()) {
        Text(
            text = "Not installed",
            style = MaterialTheme.typography.labelMedium,
            color = colors.secondaryInk,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        OutloopBentoCard(
            cornerRadius = 24.dp,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.alpha(0.65f)
        ) {
            notInstalledRules.forEachIndexed { index, rule ->
                if (index > 0) OutloopHairlineDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutloopAppMonogramBadge(
                        appName = rule.displayName,
                        preferMonogram = true
                    )
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
                }
            }
        }
    }
}

@Composable
private fun AccessibilityBentoContent(
    onOpenPrivacyDetails: () -> Unit
) {
    val colors = OutloopTheme.colors
    Text(
        text = "Let Outloop spot feeds",
        style = MaterialTheme.typography.headlineLarge,
        color = colors.ink
    )
    Text(
        text = "Android needs your permission before Outloop can recognise feed screens.",
        style = MaterialTheme.typography.bodyLarge,
        color = colors.secondaryInk
    )

    // Mint Accessibility hero card
    OutloopBentoCard(
        cornerRadius = 22.dp,
        containerColor = colors.mintBg,
        borderColor = null,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.AccessibilityNew,
                contentDescription = null,
                tint = colors.mintInk,
                modifier = Modifier.size(34.dp)
            )
            Column {
                Text(
                    text = "Accessibility access",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.mintInk
                )
                Text(
                    text = "Required for Guard to work",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mintInk.copy(alpha = 0.86f)
                )
            }
        }
    }

    // Cream numbered instructions card
    OutloopBentoCard(cornerRadius = 24.dp, contentPadding = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "How to turn it on",
                style = MaterialTheme.typography.labelLarge,
                color = colors.secondaryInk
            )
            NumberedStepRow("1", "Open Accessibility settings")
            NumberedStepRow("2", "Choose Outloop under Installed apps")
            NumberedStepRow("3", "Switch on Use Outloop")
        }
    }

    // Peach prominent Play compliance disclosure callout
    OutloopBentoCard(
        cornerRadius = 20.dp,
        containerColor = colors.peachBg,
        borderColor = null,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Used only on this device to recognise feed screens, like Reels and Shorts. Outloop never reads messages, passwords, or sends screen data off your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.peachInk
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Read privacy details",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.warnText,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenPrivacyDetails)
                .padding(vertical = 4.dp)
                .testTag("onboarding_privacy_details")
        )
    }
}

@Composable
private fun AllowAccessCheckingContent(
    rules: List<TargetAppRuleEntity>,
    installedPackages: Set<String>
) {
    val colors = OutloopTheme.colors
    val selectedNames = rules
        .filter { it.enabled && (installedPackages.isEmpty() || it.packageName in installedPackages) }
        .joinToString(", ") { it.displayName }
        .ifEmpty { "Selected apps" }

    Text(
        text = "Checking your guard",
        style = MaterialTheme.typography.headlineLarge,
        color = colors.ink
    )
    Text(
        text = "Android says Outloop is switched on. Confirming that the Accessibility service is connected.",
        style = MaterialTheme.typography.bodyLarge,
        color = colors.secondaryInk
    )
    OutloopStatusBannerCard(
        tone = StatusBannerTone.INFO,
        icon = Icons.Outlined.Schedule,
        title = "Connecting Guard service",
        body = "This usually takes a couple of seconds.",
        action = {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = colors.primaryTint,
                trackColor = colors.progressTrack
            )
        }
    )
    OutloopBentoCard(cornerRadius = 24.dp, contentPadding = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutloopStepCircleBadge(
                    label = "",
                    tone = StatusBannerTone.SUCCESS,
                    icon = Icons.Rounded.Check
                )
                Text(
                    text = "Accessibility permission granted",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.ink
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutloopStepCircleBadge(label = "2")
                Text(
                    text = "Preparing guard for $selectedNames",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryInk
                )
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
    Text(
        text = "Almost there",
        style = MaterialTheme.typography.headlineLarge,
        color = colors.ink
    )
    Text(
        text = "Outloop is switched on, but Android isn\u2019t reporting an active connection yet.",
        style = MaterialTheme.typography.bodyLarge,
        color = colors.secondaryInk
    )
    OutloopStatusBannerCard(
        tone = StatusBannerTone.WARNING,
        icon = Icons.Outlined.WarningAmber,
        title = "Guard isn\u2019t running yet",
        body = "Feeds won\u2019t be blocked until the Accessibility connection completes."
    )
    OutloopBentoCard(cornerRadius = 24.dp, contentPadding = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "Try this",
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink
            )
            NumberedStepRow("1", "Switch Outloop off and back on in Accessibility settings")
            NumberedStepRow(
                "2",
                if (isBatteryOptimizationIgnored) {
                    "Battery optimization is already unrestricted"
                } else {
                    "Allow unrestricted background battery activity"
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

@Composable
private fun ReadyBentoContent(
    selectedCount: Int,
    isAccessibilityActive: Boolean,
    scheduleSummary: String
) {
    val colors = OutloopTheme.colors

    // Top dark-forest hero card with sage check ring
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(colors.heroSurface),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(116.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = colors.heroAccent,
                    style = Stroke(width = 13.dp.toPx())
                )
            }
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.onHeroSurface,
                modifier = Modifier.size(54.dp)
            )
        }
    }

    Text(
        text = "You\u2019re all set",
        style = MaterialTheme.typography.displayLarge.copy(fontSize = 33.sp),
        color = colors.ink
    )
    Text(
        text = "Guard is on for $selectedCount app${if (selectedCount == 1) "" else "s"}. The loop is closed, and your useful apps stay open.",
        style = MaterialTheme.typography.bodyLarge,
        color = colors.secondaryInk
    )

    OutloopBentoCard(cornerRadius = 24.dp, contentPadding = PaddingValues(0.dp)) {
        ReadySummaryRow(label = "Guarded apps") {
            Text(
                text = selectedCount.toString(),
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                color = colors.ink
            )
        }
        OutloopHairlineDivider()
        ReadySummaryRow(label = "Accessibility") {
            OutloopStatusPill(
                text = if (isAccessibilityActive) "Active" else "Setup pending",
                tone = if (isAccessibilityActive) PillTone.SUCCESS else PillTone.WARNING
            )
        }
        OutloopHairlineDivider()
        ReadySummaryRow(label = "Schedule") {
            Text(
                text = scheduleSummary,
                style = MaterialTheme.typography.titleSmall,
                color = colors.ink
            )
        }
    }

    OutloopBentoCard(
        cornerRadius = 20.dp,
        containerColor = colors.peachBg,
        borderColor = null,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Need a break? Pause Guard for 15 minutes from Home.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.peachInk
        )
    }
}

@Composable
private fun ReadySummaryRow(
    label: String,
    trailing: @Composable () -> Unit
) {
    val colors = OutloopTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.secondaryInk
        )
        trailing()
    }
}

@Composable
private fun NumberedStepRow(
    number: String,
    title: String
) {
    val colors = OutloopTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutloopStepCircleBadge(label = number)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.ink
        )
    }
}

private fun appFeedSummary(rule: TargetAppRuleEntity): String = when (rule.packageName) {
    "com.instagram.android" -> "Reels, Explore"
    "com.google.android.youtube" -> "Shorts"
    "com.facebook.katana" -> "Reels, Feed"
    "com.snapchat.android" -> "Spotlight"
    "com.reddit.frontpage" -> "Watch"
    else -> rule.feedLabel
}
