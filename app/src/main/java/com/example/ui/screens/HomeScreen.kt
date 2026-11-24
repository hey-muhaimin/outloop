package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.local.TargetAppRuleEntity
import com.example.data.model.ProtectionStatus
import com.example.ui.MainUiState
import com.example.ui.components.OutloopAppMonogramBadge
import com.example.ui.components.OutloopBentoStatTile
import com.example.ui.components.OutloopBrandTopBar
import com.example.ui.components.OutloopHairlineDivider
import com.example.ui.components.OutloopHeroBentoCard
import com.example.ui.components.OutloopPauseGuardDialog
import com.example.ui.components.OutloopPrimaryButton
import com.example.ui.components.OutloopSecondaryButton
import com.example.ui.components.OutloopStaggeredEntrance
import com.example.ui.components.OutloopStatusBannerCard
import com.example.ui.components.OutloopTonalCard
import com.example.ui.components.PillTone
import com.example.ui.components.StatusBannerTone
import com.example.ui.components.rememberOutloopHaptic
import com.example.ui.theme.OutloopMetrics
import com.example.ui.theme.OutloopTheme
import java.util.Locale

@Composable
fun HomeScreen(
    uiState: MainUiState,
    onStatusClick: () -> Unit,
    onSetupClick: () -> Unit,
    onManageAppsClick: () -> Unit,
    onToggleGlobalPause: (Boolean) -> Unit,
    onPauseForMinutes: (Int) -> Unit,
    onGlobalBreakExpired: () -> Unit,
    onAppSnoozeExpired: (String) -> Unit,
    onRefreshBlockersClick: () -> Unit,
    onAppClick: (String) -> Unit,
    onToggleApp: (String, Boolean) -> Unit,
    onShowEstimateToast: (String) -> Unit,
    onResumeQuietHoursEarly: () -> Unit = {},
    onRefreshProtectionHealth: () -> Unit = {},
    onSetPreferredPauseMinutes: (Int) -> Unit = {},
    bottomContentPadding: Dp = OutloopMetrics.BottomNavContentPadding
) {
    val context = LocalContext.current
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    var dismissedNotificationBanner by rememberSaveable { mutableStateOf(false) }
    var showPauseGuardDialog by rememberSaveable { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onRefreshProtectionHealth()
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }

    val nowMs = uiState.currentTimeMs
    LaunchedEffect(nowMs, uiState.isGloballyPaused, uiState.globalPauseUntilMs, uiState.rules) {
        if (uiState.isGloballyPaused &&
            uiState.globalPauseUntilMs != Long.MAX_VALUE &&
            uiState.globalPauseUntilMs > 0L &&
            nowMs >= uiState.globalPauseUntilMs
        ) {
            onGlobalBreakExpired()
        }
        uiState.rules.forEach { rule ->
            if (rule.snoozeUntilMs in 1L..nowMs) {
                onAppSnoozeExpired(rule.packageName)
            }
        }
    }

    val installedRules = remember(uiState.rules, uiState.installedPackages, uiState.hasScannedInstalledApps) {
        if (uiState.hasScannedInstalledApps) {
            uiState.rules.filter { it.packageName in uiState.installedPackages || !it.isBuiltIn }
        } else {
            emptyList()
        }
    }
    val notInstalledRules = remember(uiState.rules, uiState.installedPackages, uiState.hasScannedInstalledApps) {
        if (uiState.hasScannedInstalledApps) {
            uiState.rules.filter { it.packageName !in uiState.installedPackages && it.isBuiltIn }
        } else {
            uiState.rules
        }
    }

    val statusPill = when (uiState.protectionStatus) {
        ProtectionStatus.OPERATIONAL -> "Guard on" to PillTone.SUCCESS
        ProtectionStatus.PAUSED -> {
            if (uiState.globalPauseUntilMs == Long.MAX_VALUE) {
                "Guard off" to PillTone.NEUTRAL
            } else {
                "Guard paused" to PillTone.WARNING
            }
        }
        ProtectionStatus.QUIET_HOURS -> "Quiet hours" to PillTone.NEUTRAL
        ProtectionStatus.NO_APPS_ACTIVE -> "No apps guarded" to PillTone.NEUTRAL
        ProtectionStatus.PERMISSION_NEEDED -> "Setup needed" to PillTone.WARNING
        ProtectionStatus.SERVICE_DISCONNECTED -> "Needs attention" to PillTone.WARNING
    }

    val pauseMins = uiState.preferences.preferredPauseMinutes.let {
        if (it == 5 || it == 15 || it == 30) it else 15
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = OutloopMetrics.MaxContentWidth)
                .testTag("home_list"),
            contentPadding = PaddingValues(
                start = OutloopMetrics.ScreenHorizontalPadding,
                end = OutloopMetrics.ScreenHorizontalPadding,
                top = OutloopMetrics.ScreenTopPadding,
                bottom = bottomContentPadding + 68.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Brand top bar + Today heading
            item {
                OutloopBrandTopBar(
                    title = "Today",
                    statusText = statusPill.first,
                    statusTone = statusPill.second,
                    onStatusClick = onStatusClick,
                    statusTestTag = "status_pill"
                )
            }

            // Hero Dark Bento KPI ("14 loops broken" + open-loop mark)
            item {
                OutloopStaggeredEntrance(index = 0) {
                    OutloopHeroBentoCard(
                        count = uiState.todayCutsCount,
                        dailyBudgetCuts = uiState.preferences.dailyBudgetCuts
                    )
                }
            }

            // Twin Mint ("time kept") + Peach ("streak") Bento Tiles
            item {
                OutloopStaggeredEntrance(index = 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutloopBentoStatTile(
                            value = uiState.todayReclaimedText,
                            label = "time kept",
                            containerColor = colors.mintTile,
                            onClick = {
                                onShowEstimateToast(
                                    "Estimated from confirmed exits and your per-visit estimate in Settings. It isn\u2019t measured screen time."
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        val streakText = if (uiState.streakDays == 1) "1 day" else "${uiState.streakDays} days"
                        OutloopBentoStatTile(
                            value = streakText,
                            label = "streak",
                            containerColor = colors.peachTile,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Conditional Status Cards (Permission Needed, Service Disconnected, Paused, Quiet Hours, Notification, Outdated)
            if (uiState.protectionStatus == ProtectionStatus.PERMISSION_NEEDED) {
                item {
                    OutloopStatusBannerCard(
                        tone = StatusBannerTone.WARNING,
                        icon = Icons.Outlined.WarningAmber,
                        title = "One setup step remains",
                        body = "Outloop can\u2019t interrupt feeds until Accessibility access is on.",
                        action = {
                            OutloopPrimaryButton(
                                text = "Finish setup",
                                onClick = onSetupClick,
                                modifier = Modifier.testTag("home_finish_setup_button")
                            )
                        }
                    )
                }
            } else if (uiState.protectionStatus == ProtectionStatus.SERVICE_DISCONNECTED) {
                item {
                    OutloopStatusBannerCard(
                        tone = StatusBannerTone.WARNING,
                        icon = Icons.Outlined.WarningAmber,
                        title = "Android isn\u2019t reporting an active connection",
                        body = "Protection is waiting for the Accessibility service to reconnect.",
                        action = {
                            OutloopPrimaryButton(
                                text = "Check settings",
                                onClick = onSetupClick
                            )
                        }
                    )
                }
            } else if (uiState.protectionStatus == ProtectionStatus.QUIET_HOURS) {
                item {
                    OutloopStatusBannerCard(
                        tone = StatusBannerTone.INFO,
                        icon = Icons.Outlined.Schedule,
                        title = "Quiet hours active",
                        body = "Guard is stepped back until ${uiState.quietHoursEndLabel}.",
                        action = {
                            OutloopSecondaryButton(
                                text = "Resume early",
                                onClick = onResumeQuietHoursEarly,
                                modifier = Modifier.testTag("home_resume_quiet_hours_button")
                            )
                        }
                    )
                }
            } else if (uiState.isGloballyPaused) {
                item {
                    val isIndefinite = uiState.globalPauseUntilMs == Long.MAX_VALUE
                    val remainingText = if (!isIndefinite) {
                        formatRemainingCountdown(uiState.globalPauseUntilMs - nowMs)
                    } else {
                        ""
                    }
                    OutloopTonalCard(
                        cornerRadius = OutloopMetrics.CardCornerRadius,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.peachTile),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Pause,
                                    contentDescription = null,
                                    tint = colors.ink,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isIndefinite) "Guard is turned off" else "Guard paused",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.ink
                                )
                                Text(
                                    text = if (isIndefinite) {
                                        "Feeds won\u2019t be interrupted until you turn Guard back on."
                                    } else {
                                        "Resumes in $remainingText"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = colors.secondaryInk
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutloopPrimaryButton(
                            text = "Resume now",
                            onClick = onGlobalBreakExpired,
                            modifier = Modifier.testTag("home_resume_now_button")
                        )
                    }
                }
            }

            if (!uiState.isNotificationPermissionGranted &&
                uiState.isAccessibilityEnabled &&
                !dismissedNotificationBanner
            ) {
                item {
                    OutloopStatusBannerCard(
                        tone = StatusBannerTone.INFO,
                        icon = Icons.Outlined.NotificationsNone,
                        title = "Allow status bar notifications",
                        body = "Lets you pause or resume Guard right from Android\u2019s shade.",
                        action = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    OutloopSecondaryButton(
                                        text = "Allow notifications",
                                        onClick = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        },
                                        modifier = Modifier.testTag("home_allow_notifications_button")
                                    )
                                }
                                Text(
                                    text = "Dismiss",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.textAction,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { dismissedNotificationBanner = true }
                                        .padding(horizontal = 8.dp, vertical = 14.dp)
                                )
                            }
                        }
                    )
                }
            }

            if (uiState.staleRulesCount > 0) {
                item {
                    OutloopStatusBannerCard(
                        tone = StatusBannerTone.WARNING,
                        icon = Icons.Outlined.WarningAmber,
                        title = "Feed selectors may need a refresh",
                        body = "${uiState.staleRulesCount} app rule reported changed layout IDs.",
                        action = {
                            OutloopSecondaryButton(
                                text = if (uiState.isSyncingRules) "Refreshing\u2026" else "Refresh rules now",
                                onClick = onRefreshBlockersClick,
                                enabled = !uiState.isSyncingRules
                            )
                        }
                    )
                }
            }

            // Weekly time saved chart
            item {
                OutloopStaggeredEntrance(index = 2) {
                    val maxMinutes = (uiState.weeklyDayBars.maxOfOrNull { it.reclaimedMinutes } ?: 1).coerceAtLeast(1)
                    OutloopTonalCard(
                        cornerRadius = OutloopMetrics.CardCornerRadius,
                        contentPadding = PaddingValues(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Time saved this week",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.secondaryInk
                                )
                                Text(
                                    text = uiState.weeklyReclaimedText,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = colors.ink
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            uiState.weeklyDayBars.forEach { bar ->
                                val barHeight = (bar.reclaimedMinutes.toFloat() / maxMinutes.toFloat() * 56f).coerceAtLeast(4f).dp
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.6f)
                                            .height(barHeight)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (bar.isToday) colors.heroDark else colors.mintTile)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = bar.dayLetter,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (bar.isToday) colors.ink else colors.secondaryInk
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Guarded apps ivory bento card matching C · Tonal Bento.png
            item {
                OutloopStaggeredEntrance(index = 3) {
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
                                text = "Guarded apps",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.secondaryInk
                            )
                            Text(
                                text = "Manage",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.textAction,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onManageAppsClick)
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .testTag("home_manage_apps_link")
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        if (installedRules.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                installedRules.forEach { rule ->
                                    val snoozed = rule.snoozeUntilMs > nowMs
                                    val feedSummary = buildString {
                                        append(homeUsefulSubtitle(rule))
                                        val feedText = uiState.todayFeedSecondsByPackage[rule.packageName]?.let { seconds ->
                                            val minutes = seconds / 60
                                            when {
                                                minutes <= 0 -> "1m"
                                                minutes < 60 -> "${minutes}m"
                                                else -> "${minutes / 60}h ${minutes % 60}m"
                                            }
                                        }
                                        if (feedText != null) append(" · $feedText")
                                        if (rule.hasDailyLimit()) append(" / ${rule.dailyLimitMinutes}m")
                                    }
                                    val subtitle = when {
                                        !rule.enabled -> "Off"
                                        snoozed -> "Paused ${formatRemainingCountdown(rule.snoozeUntilMs - nowMs)}"
                                        else -> feedSummary
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 56.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { onAppClick(rule.packageName) }
                                            .padding(vertical = 4.dp)
                                            .testTag("home_app_row_${rule.packageName}"),
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
                                                text = subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.secondaryInk
                                            )
                                        }
                                        // On / Off tonal capsule pill with full 48dp min touch height
                                        Box(
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .clickable {
                                                    haptic()
                                                    onToggleApp(rule.packageName, !rule.enabled)
                                                }
                                                .testTag("home_app_switch_${rule.packageName}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (rule.enabled) colors.mintTile else colors.surfaceSubtle
                                                    )
                                                    .padding(horizontal = 16.dp, vertical = 7.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = if (rule.enabled) "On" else "Off",
                                                    style = MaterialTheme.typography.labelLarge.copy(
                                                        fontWeight = FontWeight.SemiBold
                                                    ),
                                                    color = if (rule.enabled) colors.ink else colors.secondaryInk
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "None of the built-in guarded apps are installed on this phone yet. Tap Manage to add a custom app.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.secondaryInk,
                                modifier = Modifier.clickable(onClick = onManageAppsClick)
                            )
                        }
                    }
                }
            }

            // Uninstalled supported apps tucked calmly below Guarded apps
            if (notInstalledRules.isNotEmpty()) {
                item {
                    OutloopStaggeredEntrance(index = 4) {
                        OutloopTonalCard(
                            cornerRadius = OutloopMetrics.CardCornerRadius,
                            contentPadding = PaddingValues(18.dp),
                            containerColor = colors.surfaceSubtle,
                            modifier = Modifier.alpha(0.78f)
                        ) {
                            Text(
                                text = "Not installed",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.secondaryInk
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            notInstalledRules.forEachIndexed { idx, rule ->
                                if (idx > 0) OutloopHairlineDivider()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
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
        }

        // Floating bottom-right "Pause guard · 15 min" pill FAB matching C · Tonal Bento.png
        if (!uiState.isGloballyPaused && uiState.isAccessibilityEnabled) {
            Button(
                onClick = {
                    haptic()
                    showPauseGuardDialog = true
                },
                shape = CircleShape,
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 2.dp
                ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.heroDark,
                    contentColor = Color(0xFFFAF8F2)
                ),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = OutloopMetrics.ScreenHorizontalPadding,
                        bottom = bottomContentPadding + 14.dp
                    )
                    .heightIn(min = 52.dp)
                    .testTag("home_pause_guard_fab")
            ) {
                Text(
                    text = "Pause guard \u00B7 $pauseMins min",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }

    if (showPauseGuardDialog) {
        OutloopPauseGuardDialog(
            strictModeEnabled = uiState.preferences.strictModeEnabled,
            initialMinutes = pauseMins,
            onConfirmPause = { selectedMinutes ->
                onSetPreferredPauseMinutes(selectedMinutes)
                onPauseForMinutes(selectedMinutes)
                showPauseGuardDialog = false
            },
            onDismiss = { showPauseGuardDialog = false }
        )
    }
}

private fun homeUsefulSubtitle(rule: TargetAppRuleEntity): String = when (rule.packageName) {
    "com.instagram.android" -> "Reels blocked \u00B7 DMs open"
    "com.google.android.youtube" -> "Shorts blocked \u00B7 Search open"
    "com.facebook.katana" -> "Feed blocked \u00B7 Messenger open"
    "com.zhiliaoapp.musically" -> "For You feed \u00B7 Inbox open"
    "com.twitter.android" -> "For You timeline \u00B7 Messages open"
    else -> "${rule.feedLabel} blocked \u00B7 ${rule.homeTabLabel} open"
}

private fun formatRemainingCountdown(remainingMs: Long): String {
    val totalSeconds = (remainingMs / 1000L).coerceAtLeast(0L)
    val mins = totalSeconds / 60L
    val secs = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", mins, secs)
}
