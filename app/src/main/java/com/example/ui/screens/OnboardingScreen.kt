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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.local.TargetAppRuleEntity
import com.example.data.model.InterventionMode
import com.example.service.AccessibilityStatusHelper
import com.example.ui.components.OutloopAppMonogramBadge
import com.example.ui.components.OutloopHairlineDivider
import com.example.ui.components.OutloopMark
import com.example.ui.components.OutloopModalSheet
import com.example.ui.components.OutloopPrimaryButton
import com.example.ui.components.OutloopRoundedCheckbox
import com.example.ui.components.OutloopSecondaryButton
import com.example.ui.components.OutloopStaggeredEntrance
import com.example.ui.components.OutloopStatusBannerCard
import com.example.ui.components.OutloopStatusPill
import com.example.ui.components.OutloopStepCircleBadge
import com.example.ui.components.OutloopTonalCard
import com.example.ui.components.PillTone
import com.example.ui.components.StatusBannerTone
import com.example.ui.theme.OutloopMetrics
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
    hasScannedInstalledApps: Boolean = true,
    disclosureAccepted: Boolean = false,
    preferredPauseMinutes: Int = 15,
    quietHoursSummary: String = "All day"
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = OutloopTheme.colors
    val easing = remember { CubicBezierEasing(0.2f, 0f, 0f, 1f) }
    val privacyPolicyUrl = stringResource(R.string.privacy_policy_url)

    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }
    var awaitingPermissionReturn by rememberSaveable { mutableStateOf(false) }
    var checkAttemptKey by rememberSaveable { mutableIntStateOf(0) }
    var showDisclosureSheet by rememberSaveable { mutableStateOf(false) }

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
            OnboardingStep.CHOOSE_APPS -> OnboardingStep.WELCOME
            OnboardingStep.ALLOW_ACCESS -> OnboardingStep.CHOOSE_APPS
            OnboardingStep.CHECKING -> OnboardingStep.ALLOW_ACCESS
            OnboardingStep.PROBLEM -> OnboardingStep.ALLOW_ACCESS
            OnboardingStep.READY -> OnboardingStep.ALLOW_ACCESS
        }
    }

    val launchAccessibilitySettings = {
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

    val installedRules = remember(rules, installedPackages, hasScannedInstalledApps) {
        if (hasScannedInstalledApps) {
            rules.filter { it.packageName in installedPackages || !it.isBuiltIn }
        } else {
            emptyList()
        }
    }
    val notInstalledRules = remember(rules, installedPackages, hasScannedInstalledApps) {
        if (hasScannedInstalledApps) {
            rules.filter { it.packageName !in installedPackages && it.isBuiltIn }
        } else {
            rules
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = OutloopMetrics.MaxContentWidth)
        ) {
            OnboardingBentoTopBar(
                currentStep = stepIndex,
                totalSteps = 4,
                showSkip = step != OnboardingStep.READY,
                onSkip = onDeclineDisclosure
            )

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
                modifier = Modifier.weight(1f)
            ) { currentStep ->
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(
                                horizontal = OutloopMetrics.ScreenHorizontalPadding,
                                vertical = OutloopMetrics.SectionSpacing
                            ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (currentStep) {
                            OnboardingStep.WELCOME -> {
                                OutloopStaggeredEntrance(index = 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(246.dp)
                                            .clip(RoundedCornerShape(28.dp))
                                            .background(colors.heroDark),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        OutloopMark(
                                            size = 112.dp,
                                            color = colors.sageAccent,
                                            strokeWidth = 15.dp
                                        )
                                    }
                                }

                                OutloopStaggeredEntrance(index = 1) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            text = "Break the loop.\nKeep the apps.",
                                            style = MaterialTheme.typography.displayLarge.copy(
                                                fontSize = 32.sp,
                                                lineHeight = 36.sp
                                            ),
                                            color = colors.ink
                                        )
                                        Text(
                                            text = "Outloop blocks endless feeds like Reels and Shorts, and leaves messages, search and everything else open.",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = colors.secondaryInk
                                        )
                                    }
                                }

                                OutloopStaggeredEntrance(index = 2) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(CircleShape)
                                                .background(colors.mintTile)
                                                .padding(vertical = 13.dp, horizontal = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Feeds blocked",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = colors.ink
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(CircleShape)
                                                .background(colors.peachTile)
                                                .padding(vertical = 13.dp, horizontal = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Useful parts open",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = colors.ink
                                            )
                                        }
                                    }
                                }
                            }

                            OnboardingStep.CHOOSE_APPS -> {
                                val selectedCount = rules.count {
                                    it.enabled && (it.packageName in installedPackages || !it.isBuiltIn)
                                }
                                OutloopStaggeredEntrance(index = 0) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Pick the apps to\nguard",
                                            style = MaterialTheme.typography.displayLarge.copy(
                                                fontSize = 31.sp,
                                                lineHeight = 36.sp
                                            ),
                                            color = colors.ink
                                        )
                                        Text(
                                            text = "Only the feeds are blocked. Messages, search and the rest stay open.",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = colors.secondaryInk
                                        )
                                    }
                                }

                                OutloopStaggeredEntrance(index = 1) {
                                    OutloopTonalCard(
                                        cornerRadius = OutloopMetrics.CardCornerRadius,
                                        contentPadding = PaddingValues(18.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Feeds to block",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = colors.secondaryInk
                                            )
                                            Text(
                                                text = "$selectedCount selected",
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontFeatureSettings = "tnum"
                                                ),
                                                color = colors.ink
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))

                                        if (installedRules.isNotEmpty()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                installedRules.forEach { rule ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .heightIn(min = 56.dp)
                                                            .clip(RoundedCornerShape(14.dp))
                                                            .clickable {
                                                                onToggleApp(rule.packageName, !rule.enabled)
                                                            }
                                                            .padding(vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        OutloopAppMonogramBadge(
                                                            appName = rule.displayName,
                                                            packageName = rule.packageName,
                                                            preferDarkMonogram = true
                                                        )
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = rule.displayName,
                                                                style = MaterialTheme.typography.titleMedium,
                                                                color = colors.ink
                                                            )
                                                            Text(
                                                                text = onboardingAppSubtitle(rule),
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = colors.secondaryInk
                                                            )
                                                        }
                                                        OutloopRoundedCheckbox(
                                                            checked = rule.enabled,
                                                            onCheckedChange = { enabled ->
                                                                onToggleApp(rule.packageName, enabled)
                                                            },
                                                            contentDescription = "Protect ${rule.displayName}",
                                                            modifier = Modifier.testTag("onboarding_app_switch_${rule.packageName}")
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            Text(
                                                text = if (hasScannedInstalledApps) {
                                                    "No supported short-form feed apps were detected on this phone yet. You can add custom apps later from Home."
                                                } else {
                                                    "Checking installed apps\u2026"
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = colors.secondaryInk
                                            )
                                        }
                                    }
                                }

                                if (notInstalledRules.isNotEmpty()) {
                                    OutloopStaggeredEntrance(index = 2) {
                                        OutloopTonalCard(
                                            cornerRadius = OutloopMetrics.CardCornerRadius,
                                            contentPadding = PaddingValues(18.dp),
                                            containerColor = colors.surfaceSubtle,
                                            modifier = Modifier.alpha(0.78f)
                                        ) {
                                            Text(
                                                text = "Not installed",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = colors.secondaryInk
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            notInstalledRules.forEachIndexed { idx, rule ->
                                                if (idx > 0) OutloopHairlineDivider()
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 50.dp)
                                                        .padding(vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    OutloopAppMonogramBadge(
                                                        appName = rule.displayName,
                                                        preferDarkMonogram = true
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
                            }

                            OnboardingStep.ALLOW_ACCESS -> {
                                OutloopStaggeredEntrance(index = 0) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Let Outloop spot\nfeeds",
                                            style = MaterialTheme.typography.displayLarge.copy(
                                                fontSize = 31.sp,
                                                lineHeight = 36.sp
                                            ),
                                            color = colors.ink
                                        )
                                        Text(
                                            text = "Android needs your permission before Outloop can recognise feed screens.",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = colors.secondaryInk
                                        )
                                    }
                                }

                                OutloopStaggeredEntrance(index = 1) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(colors.mintTile)
                                            .padding(horizontal = 20.dp, vertical = 22.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AccessibilityNew,
                                            contentDescription = null,
                                            tint = colors.ink,
                                            modifier = Modifier.size(44.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Accessibility access",
                                                style = MaterialTheme.typography.titleLarge,
                                                color = colors.ink
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Required for Guard to work",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = colors.secondaryInk
                                            )
                                        }
                                    }
                                }

                                OutloopStaggeredEntrance(index = 2) {
                                    OutloopTonalCard(
                                        cornerRadius = OutloopMetrics.CardCornerRadius,
                                        contentPadding = PaddingValues(18.dp)
                                    ) {
                                        Text(
                                            text = "How to turn it on",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colors.ink
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                            NumberedMintStep("1", "Open Accessibility settings")
                                            NumberedMintStep("2", "Choose Outloop under Installed apps")
                                            NumberedMintStep("3", "Switch on Use Outloop")
                                        }
                                    }
                                }

                                OutloopStaggeredEntrance(index = 3) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(colors.peachTile)
                                            .clickable(onClick = onOpenPrivacyDetails)
                                            .padding(horizontal = 18.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Used only to recognise feed screens, like Reels and Shorts.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.ink,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            OnboardingStep.CHECKING -> {
                                OutloopStaggeredEntrance(index = 0) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Checking your connection",
                                            style = MaterialTheme.typography.displayLarge,
                                            color = colors.ink
                                        )
                                        Text(
                                            text = "Android says Outloop is switched on. Confirming that Guard is ready.",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = colors.secondaryInk
                                        )
                                    }
                                }
                                OutloopStatusBannerCard(
                                    tone = StatusBannerTone.INFO,
                                    icon = Icons.Outlined.Schedule,
                                    title = "Checking accessibility service",
                                    body = "This usually takes a couple of seconds.",
                                    action = {
                                        LinearProgressIndicator(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(5.dp)
                                                .clip(CircleShape),
                                            color = colors.heroDark,
                                            trackColor = colors.heroDark.copy(alpha = 0.20f)
                                        )
                                    }
                                )
                            }

                            OnboardingStep.PROBLEM -> {
                                OutloopStaggeredEntrance(index = 0) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Almost there",
                                            style = MaterialTheme.typography.displayLarge,
                                            color = colors.ink
                                        )
                                        Text(
                                            text = "Outloop is switched on, but Android isn\u2019t reporting an active connection yet.",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = colors.secondaryInk
                                        )
                                    }
                                }
                                OutloopStatusBannerCard(
                                    tone = StatusBannerTone.WARNING,
                                    icon = Icons.Outlined.WarningAmber,
                                    title = "Protection isn\u2019t running yet",
                                    body = "Toggle Outloop off and back on in Accessibility settings, or allow unrestricted battery usage."
                                )
                                if (!isBatteryOptimizationIgnored) {
                                    OutloopSecondaryButton(
                                        text = "Allow unrestricted battery",
                                        onClick = openBatterySettings
                                    )
                                }
                            }

                            OnboardingStep.READY -> {
                                val selectedCount = rules.count {
                                    it.enabled && (it.packageName in installedPackages || !it.isBuiltIn)
                                }
                                OutloopStaggeredEntrance(index = 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(228.dp)
                                            .clip(RoundedCornerShape(28.dp))
                                            .background(colors.heroDark),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier.size(116.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                val strokePx = 12.dp.toPx()
                                                drawCircle(
                                                    color = colors.sageAccent,
                                                    radius = size.minDimension / 2f - strokePx / 2f,
                                                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = Color(0xFFFAF8F2),
                                                modifier = Modifier.size(54.dp)
                                            )
                                        }
                                    }
                                }

                                OutloopStaggeredEntrance(index = 1) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "You\u2019re all set",
                                            style = MaterialTheme.typography.displayLarge.copy(
                                                fontSize = 31.sp,
                                                lineHeight = 36.sp
                                            ),
                                            color = colors.ink
                                        )
                                        Text(
                                            text = "Guard is on for $selectedCount app${if (selectedCount == 1) "" else "s"}. The loop is closed, and your useful apps stay open.",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = colors.secondaryInk
                                        )
                                    }
                                }

                                OutloopStaggeredEntrance(index = 2) {
                                    OutloopTonalCard(
                                        cornerRadius = OutloopMetrics.CardCornerRadius,
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        ReadyMetricRow(
                                            label = "Guarded apps",
                                            trailing = {
                                                Text(
                                                    text = selectedCount.toString(),
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontFeatureSettings = "tnum"
                                                    ),
                                                    color = colors.ink
                                                )
                                            }
                                        )
                                        OutloopHairlineDivider(modifier = Modifier.padding(horizontal = 18.dp))
                                        ReadyMetricRow(
                                            label = "Accessibility",
                                            trailing = {
                                                OutloopStatusPill(
                                                    text = if (isAccessibilityEnabled) "Active" else "Needed",
                                                    tone = if (isAccessibilityEnabled) PillTone.SUCCESS else PillTone.WARNING
                                                )
                                            }
                                        )
                                        OutloopHairlineDivider(modifier = Modifier.padding(horizontal = 18.dp))
                                        ReadyMetricRow(
                                            label = "Schedule",
                                            trailing = {
                                                Text(
                                                    text = quietHoursSummary,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = colors.ink
                                                )
                                            }
                                        )
                                    }
                                }

                                OutloopStaggeredEntrance(index = 3) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(colors.peachTile)
                                            .padding(horizontal = 18.dp, vertical = 14.dp)
                                    ) {
                                        Text(
                                            text = "Need a break? Pause Guard for $preferredPauseMinutes minutes from Home.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.ink
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Sticky bottom action footer matching Outloop-ui screenshots
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = OutloopMetrics.ScreenHorizontalPadding,
                                vertical = 14.dp
                            ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (currentStep) {
                            OnboardingStep.WELCOME -> {
                                OutloopPrimaryButton(
                                    text = "Get started",
                                    onClick = { step = OnboardingStep.CHOOSE_APPS },
                                    modifier = Modifier.testTag("onboarding_get_started")
                                )
                            }

                            OnboardingStep.CHOOSE_APPS -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(modifier = Modifier.weight(0.36f)) {
                                        OutloopSecondaryButton(
                                            text = "Back",
                                            onClick = { step = OnboardingStep.WELCOME }
                                        )
                                    }
                                    Box(modifier = Modifier.weight(0.64f)) {
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
                                            modifier = Modifier.testTag("onboarding_apps_continue")
                                        )
                                    }
                                }
                            }

                            OnboardingStep.ALLOW_ACCESS -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(modifier = Modifier.weight(0.36f)) {
                                        OutloopSecondaryButton(
                                            text = "Not now",
                                            onClick = onCompleteOnboarding,
                                            modifier = Modifier.testTag("onboarding_skip_protection")
                                        )
                                    }
                                    Box(modifier = Modifier.weight(0.64f)) {
                                        OutloopPrimaryButton(
                                            text = "Open settings",
                                            onClick = {
                                                if (disclosureAccepted || isAccessibilityEnabled) {
                                                    launchAccessibilitySettings()
                                                } else {
                                                    showDisclosureSheet = true
                                                }
                                            },
                                            modifier = Modifier.testTag("onboarding_open_settings")
                                        )
                                    }
                                }
                            }

                            OnboardingStep.CHECKING -> {
                                OutloopPrimaryButton(
                                    text = "Checking\u2026",
                                    onClick = {},
                                    enabled = false
                                )
                                OutloopSecondaryButton(
                                    text = "Open settings",
                                    onClick = launchAccessibilitySettings
                                )
                            }

                            OnboardingStep.PROBLEM -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(modifier = Modifier.weight(0.38f)) {
                                        OutloopSecondaryButton(
                                            text = "Not now",
                                            onClick = onCompleteOnboarding
                                        )
                                    }
                                    Box(modifier = Modifier.weight(0.62f)) {
                                        OutloopPrimaryButton(
                                            text = "Check again",
                                            onClick = {
                                                if (AccessibilityStatusHelper.isServiceEnabled(context)) {
                                                    step = OnboardingStep.CHECKING
                                                    checkAttemptKey++
                                                } else {
                                                    launchAccessibilitySettings()
                                                }
                                            },
                                            modifier = Modifier.testTag("onboarding_check_again")
                                        )
                                    }
                                }
                            }

                            OnboardingStep.READY -> {
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

    if (showDisclosureSheet) {
        OutloopModalSheet(
            title = "Accessibility disclosure",
            onDismiss = { showDisclosureSheet = false }
        ) {
            Text(
                text = "Outloop uses Android\u2019s Accessibility Service to recognise technical screen labels and view IDs in the apps you choose so it can block Shorts, Reels, or Watch feeds.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.secondaryInk
            )
            Text(
                text = "It never reads your messages, passwords, or videos. Detection runs strictly on your phone \u2014 no screen data is collected or shared.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryInk
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "How detection works",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textAction,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            showDisclosureSheet = false
                            onOpenPrivacyDetails()
                        }
                        .padding(vertical = 4.dp)
                )
                Text(
                    text = "Privacy policy",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textAction,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyUrl)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }
                        }
                        .padding(vertical = 4.dp)
                )
            }
            OutloopPrimaryButton(
                text = "Agree and continue",
                onClick = {
                    onAcceptDisclosure()
                    showDisclosureSheet = false
                    launchAccessibilitySettings()
                },
                modifier = Modifier.testTag("onboarding_agree")
            )
            OutloopSecondaryButton(
                text = "Not now",
                onClick = { showDisclosureSheet = false },
                modifier = Modifier.testTag("onboarding_decline")
            )
        }
    }
}

@Composable
private fun OnboardingBentoTopBar(
    currentStep: Int,
    totalSteps: Int,
    showSkip: Boolean,
    onSkip: () -> Unit
) {
    val colors = OutloopTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = OutloopMetrics.ScreenHorizontalPadding,
                end = OutloopMetrics.ScreenHorizontalPadding,
                top = 14.dp,
                bottom = 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (i in 1..totalSteps) {
                val segColor by animateColorAsState(
                    targetValue = if (i <= currentStep) colors.heroDark else Color(0xFFDCD8CC),
                    animationSpec = tween(220),
                    label = "stepBar_$i"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(segColor)
                )
            }
        }
        if (showSkip) {
            Text(
                text = "Skip",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.secondaryInk,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .testTag("onboarding_skip")
            )
        }
    }
}

@Composable
private fun NumberedMintStep(
    number: String,
    text: String
) {
    val colors = OutloopTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutloopStepCircleBadge(label = number)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.ink
        )
    }
}

@Composable
private fun ReadyMetricRow(
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

private fun onboardingAppSubtitle(rule: TargetAppRuleEntity): String = when (rule.packageName) {
    "com.instagram.android" -> "Reels, Explore"
    "com.google.android.youtube" -> "Shorts"
    "com.facebook.katana" -> "Reels, Feed"
    "com.zhiliaoapp.musically" -> "For You feed"
    "com.twitter.android" -> "For You timeline"
    else -> rule.feedLabel
}
