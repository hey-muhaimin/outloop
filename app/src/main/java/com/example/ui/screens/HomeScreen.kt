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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.sp
import com.example.data.local.TargetAppRuleEntity
import com.example.data.model.ProtectionStatus
import com.example.ui.MainUiState
import com.example.ui.components.OutloopAppMonogramBadge
import com.example.ui.components.OutloopBentoCard
import com.example.ui.components.OutloopBentoHeader
import com.example.ui.components.OutloopBentoStatTile
import com.example.ui.components.OutloopHairlineDivider
import com.example.ui.components.OutloopPauseGuardDialog
import com.example.ui.components.OutloopPrimaryButton
import com.example.ui.components.OutloopSecondaryButton
import com.example.ui.components.OutloopStatusBannerCard
import com.example.ui.components.OutloopStatusPill
import com.example.ui.components.OutloopTodayHeroCard
import com.example.ui.components.OutloopTonalButton
import com.example.ui.components.PillTone
import com.example.ui.components.StatusBannerTone
import com.example.ui.components.rememberOutloopHaptic
import com.example.ui.theme.OutloopTheme
import kotlinx.coroutines.delay
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
    bottomContentPadding: Dp = 116.dp,
    floatingBottomOffset: Dp = 80.dp
) {
    val context = LocalContext.current
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    var dismissedNotificationBanner by rememberSaveable { mutableStateOf(false) }
    var showStrictPauseDialog by rememberSaveable { mutableStateOf(false) }

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

    val displayedRules = remember(uiState.rules, uiState.installedPackages) {
        val installedOnly = uiState.rules.filter { it.packageName in uiState.installedPackages }
        installedOnly.ifEmpty { uiState.rules }
    }

    val statusPill = when (uiState.protectionStatus) {
        ProtectionStatus.OPERATIONAL -> "Guard on" to PillTone.SUCCESS
        ProtectionStatus.PAUSED -> "Paused" to PillTone.WARNING
        ProtectionStatus.QUIET_HOURS -> "Quiet hours" to PillTone.NEUTRAL
        ProtectionStatus.NO_APPS_ACTIVE -> "Guard off" to PillTone.NEUTRAL
        ProtectionStatus.PERMISSION_NEEDED -> "Setup needed" to PillTone.WARNING
        ProtectionStatus.SERVICE_DISCONNECTED -> "Needs attention" to PillTone.WARNING
    }

    val showFloatingPauseButton = uiState.protectionStatus != ProtectionStatus.PERMISSION_NEEDED &&
        uiState.protectionStatus != ProtectionStatus.SERVICE_DISCONNECTED &&
        (uiState.isGloballyPaused || uiState.activeInstalledCount > 0)

    val listBottomPadding = if (showFloatingPauseButton) {
        bottomContentPadding + 72.dp
    } else {
        bottomContentPadding
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .testTag("home_list"),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 18.dp,
                bottom = listBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Wordmark + Guard pill + "Today" display title
            item {
                OutloopBentoHeader(
                    title = "Today",
                    statusText = statusPill.first,
                    statusTone = statusPill.second,
                    onStatusClick = onStatusClick
                )
            }

            // Deep Forest dark hero card: loops broken + sage open loop
            item {
                OutloopTodayHeroCard(
                    count = uiState.todayCutsCount,
                    dailyBudgetCuts = uiState.preferences.dailyBudgetCuts
                )
            }

            // Mint "time kept" + Peach "streak" 2-column Bento row
            item {
                val streakText = "${uiState.streakDays} ${if (uiState.streakDays == 1) "day" else "days"}"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutloopBentoStatTile(
                        value = uiState.todayReclaimedText,
                        label = "time kept",
                        containerColor = colors.mintBg,
                        contentColor = colors.mintInk,
                        onClick = {
                            onShowEstimateToast(
                                "Estimated from confirmed exits and your per-visit estimate in Settings. It isn\u2019t measured screen time."
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_time_kept_tile")
                    )
                    OutloopBentoStatTile(
                        value = streakText,
                        label = "streak",
                        containerColor = colors.peachBg,
                        contentColor = colors.peachInk,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_streak_tile")
                    )
                }
            }

            // Contextual status callouts (setup, disconnected, quiet hours, or active timed pause)
            when (uiState.protectionStatus) {
                ProtectionStatus.PERMISSION_NEEDED -> {
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
                }

                ProtectionStatus.SERVICE_DISCONNECTED -> {
                    item {
                        OutloopStatusBannerCard(
                            tone = StatusBannerTone.WARNING,
                            icon = Icons.Outlined.WarningAmber,
                            title = "Android isn\u2019t reporting an active connection",
                            body = "Guard is waiting for the Accessibility service to reconnect.",
                            action = {
                                OutloopPrimaryButton(
                                    text = "Check settings",
                                    onClick = onSetupClick
                                )
                            }
                        )
                    }
                }

                ProtectionStatus.QUIET_HOURS -> {
                    item {
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
                }

                ProtectionStatus.PAUSED -> {
                    item {
                        val isIndefinite = uiState.globalPauseUntilMs == Long.MAX_VALUE
                        val bodyText = if (isIndefinite) {
                            "Guard is switched off until you resume it."
                        } else {
                            "Guard resumes in ${formatRemainingCountdown(uiState.globalPauseUntilMs - nowMs)}."
                        }
                        OutloopStatusBannerCard(
                            tone = StatusBannerTone.WARNING,
                            icon = Icons.Outlined.PauseCircleOutline,
                            title = "Guard is paused",
                            body = bodyText,
                            action = {
                                OutloopTonalButton(
                                    text = "Resume now",
                                    onClick = onGlobalBreakExpired,
                                    modifier = Modifier.testTag("home_resume_now_button")
                                )
                            }
                        )
                    }
                }

                else -> Unit
            }

            // Android 13+ notification permission prompt (non-blocking)
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

            if (uiState.activeInstalledCount == 0 &&
                uiState.protectionStatus != ProtectionStatus.PERMISSION_NEEDED &&
                uiState.protectionStatus != ProtectionStatus.SERVICE_DISCONNECTED
            ) {
                item {
                    OutloopStatusBannerCard(
                        tone = StatusBannerTone.INFO,
                        icon = Icons.Outlined.Info,
                        title = "No apps guarded",
                        body = "Choose the apps you want Outloop to guard.",
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

            // "Guarded apps" Bento Card matching the Home mockup
            item {
                OutloopBentoCard(cornerRadius = 24.dp, contentPadding = PaddingValues(0.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 14.dp, top = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Guarded apps",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            color = colors.secondaryInk
                        )
                        Text(
                            text = "Manage",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.textAction,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    haptic()
                                    onManageAppsClick()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    displayedRules.forEachIndexed { idx, rule ->
                        if (idx > 0) OutloopHairlineDivider()
                        val snoozed = rule.snoozeUntilMs > nowMs
                        val statusSubtitle = homeAppSubtitle(
                            rule = rule,
                            snoozed = snoozed,
                            remainingText = if (snoozed) formatRemainingCountdown(rule.snoozeUntilMs - nowMs) else ""
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 68.dp)
                                .clickable { onAppClick(rule.packageName) }
                                .padding(start = 18.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
                                .testTag("home_app_row_${rule.packageName}"),
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
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = colors.ink
                                )
                                Text(
                                    text = statusSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondaryInk
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        haptic()
                                        onToggleApp(rule.packageName, !rule.enabled)
                                    }
                                    .padding(horizontal = 4.dp, vertical = 8.dp)
                                    .testTag("home_app_switch_${rule.packageName}"),
                                contentAlignment = Alignment.Center
                            ) {
                                OutloopStatusPill(
                                    text = when {
                                        !rule.enabled -> "Off"
                                        snoozed -> "Snoozed"
                                        else -> "On"
                                    },
                                    tone = when {
                                        !rule.enabled -> PillTone.NEUTRAL
                                        snoozed -> PillTone.PEACH
                                        else -> PillTone.SUCCESS
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        // Floating dark pill CTA anchored bottom-right above the bottom navigation bar
        if (showFloatingPauseButton) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(end = 20.dp, bottom = floatingBottomOffset),
                contentAlignment = Alignment.CenterEnd
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.primaryTint,
                    contentColor = colors.onPrimaryTint,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptic()
                            when {
                                uiState.isGloballyPaused -> onGlobalBreakExpired()
                                uiState.preferences.strictModeEnabled -> showStrictPauseDialog = true
                                else -> {
                                    onPauseForMinutes(15)
                                    onShowEstimateToast("Guard paused for 15 minutes.")
                                }
                            }
                        }
                        .testTag(
                            if (uiState.isGloballyPaused) {
                                "home_floating_resume_button"
                            } else {
                                "home_floating_pause_button"
                            }
                        )
                ) {
                    Text(
                        text = if (uiState.isGloballyPaused) {
                            "Resume guard"
                        } else {
                            "Pause guard \u00B7 15 min"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colors.onPrimaryTint,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                    )
                }
            }
        }
    }

    if (showStrictPauseDialog) {
        OutloopPauseGuardDialog(
            initialMinutes = 15,
            onKeepGuarding = { showStrictPauseDialog = false },
            onConfirmPause = { minutes ->
                showStrictPauseDialog = false
                onPauseForMinutes(minutes)
                onShowEstimateToast("Guard paused for $minutes minutes.")
            },
            onDismiss = { showStrictPauseDialog = false }
        )
    }
}

private fun homeAppSubtitle(
    rule: TargetAppRuleEntity,
    snoozed: Boolean,
    remainingText: String
): String {
    if (!rule.enabled) return "${rule.feedLabel} \u00B7 Off"
    if (snoozed) return "${rule.feedLabel} \u00B7 Snoozed $remainingText"
    return when (rule.packageName) {
        "com.instagram.android" -> "Reels blocked \u00B7 DMs open"
        "com.google.android.youtube" -> "Shorts blocked \u00B7 Search open"
        "com.facebook.katana" -> "Feed blocked \u00B7 Messenger open"
        "com.snapchat.android" -> "Spotlight blocked \u00B7 Chat open"
        "com.reddit.frontpage" -> "Watch blocked \u00B7 Search open"
        else -> "${rule.feedLabel} blocked \u00B7 ${rule.homeTabLabel} open"
    }
}

private fun formatRemainingCountdown(remainingMs: Long): String {
    val totalSeconds = (remainingMs / 1000L).coerceAtLeast(0L)
    val mins = totalSeconds / 60L
    val secs = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", mins, secs)
}
