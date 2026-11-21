package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.ProtectionStatus
import com.example.ui.MainUiState
import com.example.ui.components.OutloopAppMonogramBadge
import com.example.ui.components.OutloopDailyRhythmStrip
import com.example.ui.components.OutloopHairlineDivider
import com.example.ui.components.OutloopHeroCounterRing
import com.example.ui.components.OutloopMark
import com.example.ui.components.OutloopPrimaryButton
import com.example.ui.components.OutloopSecondaryButton
import com.example.ui.components.OutloopSectionHeader
import com.example.ui.components.OutloopStatusBannerCard
import com.example.ui.components.OutloopStatusPill
import com.example.ui.components.OutloopSwitch
import com.example.ui.components.OutloopTonalCard
import com.example.ui.components.PillTone
import com.example.ui.components.StatusBannerTone
import com.example.ui.components.rememberOutloopHaptic
import com.example.ui.theme.OutloopTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
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
    bottomContentPadding: Dp = 116.dp
) {
    val context = LocalContext.current
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    var dismissedNotificationBanner by rememberSaveable { mutableStateOf(false) }

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

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val anyTimedSnoozeActive = remember(uiState.rules, uiState.isGloballyPaused, uiState.globalPauseUntilMs) {
        (uiState.isGloballyPaused && uiState.globalPauseUntilMs != Long.MAX_VALUE) ||
            uiState.rules.any { it.snoozeUntilMs > System.currentTimeMillis() }
    }

    LaunchedEffect(anyTimedSnoozeActive, uiState.globalPauseUntilMs) {
        while (anyTimedSnoozeActive) {
            delay(1_000L)
            val current = System.currentTimeMillis()
            nowMs = current
            if (uiState.isGloballyPaused &&
                uiState.globalPauseUntilMs != Long.MAX_VALUE &&
                current >= uiState.globalPauseUntilMs
            ) {
                onGlobalBreakExpired()
            }
            uiState.rules.forEach { rule ->
                if (rule.snoozeUntilMs in 1L..current) {
                    onAppSnoozeExpired(rule.packageName)
                }
            }
        }
    }

    val dateLabel = remember {
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
    }

    val displayedRules = remember(uiState.rules, uiState.installedPackages) {
        val installedOnly = uiState.rules.filter { it.packageName in uiState.installedPackages }
        installedOnly.ifEmpty { uiState.rules }
    }

    val statusPill = when (uiState.protectionStatus) {
        ProtectionStatus.OPERATIONAL -> "Protection active" to PillTone.ACTIVE_DOT
        ProtectionStatus.PAUSED -> "Taking a little space" to PillTone.WARNING
        ProtectionStatus.QUIET_HOURS -> "Quiet hours" to PillTone.NEUTRAL
        ProtectionStatus.NO_APPS_ACTIVE -> "Protection off" to PillTone.NEUTRAL
        ProtectionStatus.PERMISSION_NEEDED -> "Setup needed" to PillTone.WARNING
        ProtectionStatus.SERVICE_DISCONNECTED -> "Needs attention" to PillTone.WARNING
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 24.dp,
                bottom = bottomContentPadding
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header block matching 1:1 with Today mockup
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.secondaryInk
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.displayLarge,
                            color = colors.ink
                        )
                        OutloopStatusPill(
                            text = statusPill.first,
                            tone = statusPill.second,
                            onClick = onStatusClick,
                            modifier = Modifier.testTag("status_pill")
                        )
                    }
                }
            }

            // Hero ring + daily rhythm strip
            item {
                OutloopTonalCard(contentPadding = PaddingValues(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutloopHeroCounterRing(
                            count = uiState.todayCutsCount,
                            dailyBudgetCuts = uiState.preferences.dailyBudgetCuts
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (uiState.todayCutsCount > 0) {
                                    "feeds interrupted today"
                                } else {
                                    "Nothing to interrupt today."
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.ink
                            )
                            if (uiState.preferences.dailyBudgetCuts > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val budgetText = if (uiState.todayCutsCount >= uiState.preferences.dailyBudgetCuts) {
                                    "Daily intention reached (${uiState.preferences.dailyBudgetCuts})"
                                } else {
                                    "${uiState.todayCutsCount} of ${uiState.preferences.dailyBudgetCuts} daily target"
                                }
                                Text(
                                    text = budgetText,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = if (uiState.todayCutsCount >= uiState.preferences.dailyBudgetCuts) {
                                        colors.okText
                                    } else {
                                        colors.textAction
                                    }
                                )
                            }
                            if (uiState.todayCutsCount > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${uiState.todayReclaimedText} est. time reclaimed",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFeatureSettings = "tnum"
                                        ),
                                        color = colors.secondaryInk,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    IconButton(
                                        onClick = {
                                            haptic()
                                            onShowEstimateToast(
                                                "Estimated from confirmed exits and your per-visit estimate in Settings. It isn\u2019t measured screen time."
                                            )
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Info,
                                            contentDescription = "How this is estimated",
                                            tint = colors.secondaryInk,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.weeklyDayBars.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutloopDailyRhythmStrip(dayBars = uiState.weeklyDayBars)
                    }
                }
            }

            // Protection control card or setup/reconnect prompt
            item {
                when (uiState.protectionStatus) {
                    ProtectionStatus.PERMISSION_NEEDED -> {
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

                    ProtectionStatus.SERVICE_DISCONNECTED -> {
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

                    ProtectionStatus.QUIET_HOURS -> {
                        OutloopStatusBannerCard(
                            tone = StatusBannerTone.INFO,
                            icon = Icons.Outlined.Schedule,
                            title = "Quiet hours active",
                            body = "Outloop is stepped back until ${uiState.quietHoursEndLabel}.",
                            action = {
                                OutloopSecondaryButton(
                                    text = "Resume early",
                                    onClick = onResumeQuietHoursEarly,
                                    modifier = Modifier.testTag("home_resume_quiet_hours_button")
                                )
                            }
                        )
                    }

                    else -> {
                        val isPaused = uiState.isGloballyPaused
                        val isIndefiniteOff = isPaused && uiState.globalPauseUntilMs == Long.MAX_VALUE
                        val remainingText = if (isPaused && !isIndefiniteOff) {
                            formatRemainingCountdown(uiState.globalPauseUntilMs - nowMs)
                        } else {
                            ""
                        }

                        OutloopTonalCard(
                            cornerRadius = 22.dp,
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colors.surfaceSubtle)
                                        .border(1.dp, colors.hairlineBorder, RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    OutloopMark(size = 24.dp, color = colors.primaryTint)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = when {
                                            isIndefiniteOff -> "Protection is off"
                                            isPaused -> "Taking a little space"
                                            else -> "Protection is on"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.ink
                                    )
                                    Text(
                                        text = when {
                                            isIndefiniteOff -> "Nothing will be interrupted."
                                            isPaused -> "Resumes in $remainingText"
                                            else -> "Guarding ${uiState.activeInstalledCount} selected app${if (uiState.activeInstalledCount == 1) "" else "s"}"
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFeatureSettings = "tnum"
                                        ),
                                        color = colors.secondaryInk
                                    )
                                }
                                OutloopSwitch(
                                    checked = !isIndefiniteOff,
                                    onCheckedChange = { turnOn ->
                                        onToggleGlobalPause(!turnOn)
                                    },
                                    contentDescription = "Protection switch",
                                    modifier = Modifier.testTag("home_protection_switch")
                                )
                            }

                            if (isPaused && !isIndefiniteOff) {
                                Spacer(modifier = Modifier.height(12.dp))
                                OutloopSecondaryButton(
                                    text = "Resume now",
                                    onClick = onGlobalBreakExpired,
                                    modifier = Modifier.testTag("home_resume_now_button")
                                )
                            } else if (!isPaused && uiState.activeInstalledCount > 0) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Take a little space",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.secondaryInk
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PauseDurationPill("5 min", { onPauseForMinutes(5) }, Modifier.weight(1f))
                                    PauseDurationPill("15 min", { onPauseForMinutes(15) }, Modifier.weight(1f))
                                    PauseDurationPill("1 hour", { onPauseForMinutes(60) }, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // Notification permission notice (Android 13+, non-blocking)
            if (!uiState.isNotificationPermissionGranted &&
                uiState.isAccessibilityEnabled &&
                !dismissedNotificationBanner
            ) {
                item {
                    OutloopStatusBannerCard(
                        tone = StatusBannerTone.INFO,
                        icon = Icons.Outlined.NotificationsNone,
                        title = "Allow status bar notifications",
                        body = "Lets you pause or resume protection right from Android\u2019s shade.",
                        action = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                                    color = colors.infoText,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { dismissedNotificationBanner = true }
                                        .padding(horizontal = 8.dp, vertical = 12.dp)
                                )
                            }
                        }
                    )
                }
            }

            // No apps selected prompt if permission is ready but 0 apps toggled
            if (uiState.activeInstalledCount == 0 &&
                uiState.protectionStatus != ProtectionStatus.PERMISSION_NEEDED &&
                uiState.protectionStatus != ProtectionStatus.SERVICE_DISCONNECTED
            ) {
                item {
                    OutloopStatusBannerCard(
                        tone = StatusBannerTone.INFO,
                        icon = Icons.Outlined.Info,
                        title = "No apps selected",
                        body = "Choose the apps you want Outloop to protect.",
                        action = {
                            Text(
                                text = "Choose apps",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.infoText,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onManageAppsClick)
                                    .padding(vertical = 4.dp)
                            )
                        }
                    )
                }
            }

            // Ruleset outdated callout (only when real Gist/selector health flags an issue)
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

            // "Your apps" section header + grouped tonal list
            item {
                OutloopSectionHeader(
                    text = "Your apps",
                    trailing = {
                        Text(
                            text = "${uiState.activeInstalledCount} selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryInk
                        )
                    }
                )
            }

            item {
                OutloopTonalCard(cornerRadius = 24.dp, contentPadding = PaddingValues(0.dp)) {
                    displayedRules.forEachIndexed { idx, rule ->
                        if (idx > 0) OutloopHairlineDivider()
                        val snoozed = rule.snoozeUntilMs > nowMs
                        val statusSubtitle = when {
                            !rule.enabled -> "Off"
                            snoozed -> "${rule.feedLabel} \u00B7 Snoozed ${formatRemainingCountdown(rule.snoozeUntilMs - nowMs)}"
                            else -> "${rule.feedLabel} \u00B7 ${rule.interventionMode.shortLabel}"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 68.dp)
                                .clickable { onAppClick(rule.packageName) }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .testTag("home_app_row_${rule.packageName}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                    text = statusSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryInk
                                )
                            }
                            OutloopSwitch(
                                checked = rule.enabled,
                                onCheckedChange = { enabled -> onToggleApp(rule.packageName, enabled) },
                                contentDescription = "Toggle protection for ${rule.displayName}",
                                modifier = Modifier.testTag("home_app_switch_${rule.packageName}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PauseDurationPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(colors.surfaceSubtle)
            .border(1.dp, colors.hairlineBorder, CircleShape)
            .clickable {
                haptic()
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.ink
        )
    }
}

private fun formatRemainingCountdown(remainingMs: Long): String {
    val totalSeconds = (remainingMs / 1000L).coerceAtLeast(0L)
    val mins = totalSeconds / 60L
    val secs = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", mins, secs)
}
