package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.local.AppPreferences
import com.example.data.model.InterventionMode
import com.example.data.repository.OutloopBackupPreview
import com.example.service.LaunchableAppInfo
import com.example.ui.MainUiState
import com.example.ui.components.OutloopHairlineDivider
import com.example.ui.components.OutloopIconBadge
import com.example.ui.components.OutloopListNavRow
import com.example.ui.components.OutloopModalSheet
import com.example.ui.components.OutloopPrimaryButton
import com.example.ui.components.OutloopRadioOptionCard
import com.example.ui.components.OutloopRoundedCheckbox
import com.example.ui.components.OutloopSecondaryButton
import com.example.ui.components.OutloopSegmentedControl
import com.example.ui.components.OutloopStaggeredEntrance
import com.example.ui.components.OutloopStatusPill
import com.example.ui.components.OutloopSwitch
import com.example.ui.components.OutloopTabHeader
import com.example.ui.components.OutloopTonalCard
import com.example.ui.components.PillTone
import com.example.ui.components.outloopPressScale
import com.example.ui.components.rememberOutloopHaptic
import com.example.ui.theme.OutloopMetrics
import com.example.ui.theme.OutloopTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    uiState: MainUiState,
    onOpenManageApps: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenWalkthrough: () -> Unit = {},
    onSelectDefaultMode: (InterventionMode) -> Unit,
    onSetReclaimedSeconds: (Int) -> Unit,
    onSetHapticFeedback: (Boolean) -> Unit,
    onSetShowBanner: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onShowToast: (String) -> Unit,
    onSetQuietHours: (Boolean, Int, Int) -> Unit = { _, _, _ -> },
    onSetDailyBudget: (Int) -> Unit = {},
    onSetGlobalFeedBudget: (Int) -> Unit = {},
    onExportBackupUri: (Uri, (String) -> Unit) -> Unit = { _, cb -> cb("Backup exported cleanly.") },
    onInspectBackupUri: (Uri, (OutloopBackupPreview?, String?, String?) -> Unit) -> Unit = { _, cb ->
        cb(null, null, "Backup unavailable")
    },
    onRestoreBackupJson: (String, (String) -> Unit) -> Unit = { _, cb -> cb("Backup restored.") },
    onAcceptDisclosure: () -> Unit = {},
    onToggleGlobalPause: (Boolean) -> Unit = {},
    onSetStrictModeEnabled: (Boolean) -> Unit = {},
    onToggleAlwaysOpenApp: (String, Boolean) -> Unit = { _, _ -> },
    bottomContentPadding: Dp = OutloopMetrics.BottomNavContentPadding
) {
    val context = LocalContext.current
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()

    var showScheduleSheet by rememberSaveable { mutableStateOf(false) }
    var showAlwaysOpenSheet by rememberSaveable { mutableStateOf(false) }
    var showAboutSheet by rememberSaveable { mutableStateOf(false) }
    var showDefaultResponseSheet by rememberSaveable { mutableStateOf(false) }
    var showDailyBudgetSheet by rememberSaveable { mutableStateOf(false) }
    var showGlobalFeedBudgetSheet by rememberSaveable { mutableStateOf(false) }
    var showClearHistorySheet by rememberSaveable { mutableStateOf(false) }
    var showAccessibilityDisclosureSheet by rememberSaveable { mutableStateOf(false) }
    var pendingBackupPreview by remember { mutableStateOf<OutloopBackupPreview?>(null) }
    var pendingBackupJson by remember { mutableStateOf<String?>(null) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            onExportBackupUri(uri, onShowToast)
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onInspectBackupUri(uri) { preview, rawJson, error ->
                if (preview != null && rawJson != null) {
                    pendingBackupPreview = preview
                    pendingBackupJson = rawJson
                } else {
                    onShowToast(error ?: "Couldn\u2019t read that backup file.")
                }
            }
        }
    }

    val scheduleSubtitle = if (uiState.preferences.quietHoursEnabled) {
        "Quiet ${AppPreferences.formatMinutesOfDay(uiState.preferences.quietStartMinutes)} to ${AppPreferences.formatMinutesOfDay(uiState.preferences.quietEndMinutes)}"
    } else {
        "All day"
    }

    val alwaysOpenSummary = remember(
        uiState.preferences.alwaysOpenApps,
        uiState.utilityApps,
        uiState.launchableApps
    ) {
        val utilityMap = uiState.utilityApps.associateBy { it.packageName }
        val launchableMap = uiState.launchableApps.associateBy { it.packageName }
        val list = uiState.preferences.alwaysOpenApps.map { id ->
            utilityMap[id]?.displayName
                ?: launchableMap[id]?.displayName
                ?: AppPreferences.ALWAYS_OPEN_FRIENDLY_NAMES[id]
                ?: id
        }.distinct().sorted()
        when {
            list.isEmpty() -> "None"
            list.size <= 3 -> list.joinToString(", ")
            else -> "${list.take(3).joinToString(", ")} and ${list.size - 3} more"
        }
    }

    val selectedSeconds = when (uiState.preferences.reclaimedSecondsPerCut) {
        in 0..90 -> 60
        in 91..150 -> 120
        in 151..240 -> 180
        else -> 300
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = OutloopMetrics.MaxContentWidth)
                .testTag("settings_list"),
            contentPadding = PaddingValues(
                start = OutloopMetrics.ScreenHorizontalPadding,
                end = OutloopMetrics.ScreenHorizontalPadding,
                top = OutloopMetrics.ScreenTopPadding,
                bottom = bottomContentPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutloopTabHeader(title = "Settings")
            }

            // Card 1: Guard
            item {
                OutloopStaggeredEntrance(index = 0) {
                    OutloopTonalCard(
                        cornerRadius = OutloopMetrics.CardCornerRadius,
                        contentPadding = PaddingValues(18.dp)
                    ) {
                        Text(
                            text = "Guard",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.secondaryInk
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SettingsToggleBentoRow(
                                icon = Icons.Outlined.Shield,
                                title = "Guard",
                                subtitle = "Blocks feeds in guarded apps",
                                checked = !uiState.isGloballyPaused,
                                onCheckedChange = { guardOn -> onToggleGlobalPause(!guardOn) },
                                switchTestTag = "settings_guard_switch"
                            )
                            SettingsToggleBentoRow(
                                icon = Icons.Outlined.Lock,
                                title = "Strict mode",
                                subtitle = "Wait 10 s before pausing",
                                checked = uiState.preferences.strictModeEnabled,
                                onCheckedChange = onSetStrictModeEnabled,
                                switchTestTag = "settings_strict_mode_switch"
                            )
                            SettingsNavBentoRow(
                                icon = Icons.Outlined.Schedule,
                                title = "Schedule",
                                subtitle = scheduleSubtitle,
                                onClick = { showScheduleSheet = true },
                                modifier = Modifier.testTag("settings_quiet_hours")
                            )
                            OutloopHairlineDivider()
                            OutloopListNavRow(
                                title = "Default response",
                                valueText = uiState.preferences.defaultInterventionMode.shortLabel,
                                onClick = { showDefaultResponseSheet = true },
                                modifier = Modifier.testTag("settings_default_response")
                            )
                            OutloopHairlineDivider()
                            OutloopListNavRow(
                                title = "Daily pause target",
                                valueText = if (uiState.preferences.dailyBudgetCuts > 0) {
                                    "${uiState.preferences.dailyBudgetCuts} / day"
                                } else {
                                    "Off"
                                },
                                onClick = { showDailyBudgetSheet = true },
                                modifier = Modifier.testTag("settings_daily_budget")
                            )
                            OutloopHairlineDivider()
                            OutloopListNavRow(
                                title = "Global feed budget",
                                valueText = if (uiState.preferences.globalFeedTimeBudgetMinutes > 0) {
                                    "${uiState.preferences.globalFeedTimeBudgetMinutes}m / day"
                                } else {
                                    "Off"
                                },
                                onClick = { showGlobalFeedBudgetSheet = true },
                                modifier = Modifier.testTag("settings_global_feed_budget")
                            )
                        }
                    }
                }
            }

            // Card 2: Apps and access
            item {
                OutloopStaggeredEntrance(index = 1) {
                    OutloopTonalCard(
                        cornerRadius = OutloopMetrics.CardCornerRadius,
                        contentPadding = PaddingValues(18.dp)
                    ) {
                        Text(
                            text = "Apps and access",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.secondaryInk
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SettingsNavBentoRow(
                                icon = Icons.Outlined.GridView,
                                title = "Guarded apps",
                                subtitle = "${uiState.activeInstalledCount} app${if (uiState.activeInstalledCount == 1) "" else "s"}",
                                onClick = onOpenManageApps,
                                modifier = Modifier.testTag("settings_protected_apps")
                            )
                            SettingsNavBentoRow(
                                icon = Icons.Outlined.CheckCircle,
                                title = "Always open",
                                subtitle = alwaysOpenSummary,
                                onClick = { showAlwaysOpenSheet = true },
                                modifier = Modifier.testTag("settings_always_open")
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 58.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        if (uiState.preferences.disclosureAccepted || uiState.isAccessibilityEnabled) {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                )
                                            }
                                        } else {
                                            showAccessibilityDisclosureSheet = true
                                        }
                                    }
                                    .padding(vertical = 4.dp)
                                    .testTag("settings_accessibility_permission"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutloopIconBadge(icon = Icons.Outlined.AccessibilityNew)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Accessibility service",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.ink
                                    )
                                    Text(
                                        text = "Needed to spot feeds",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.secondaryInk
                                    )
                                }
                                when {
                                    uiState.isAccessibilityEnabled && uiState.isServiceConnected -> {
                                        OutloopStatusPill(text = "Active", tone = PillTone.SUCCESS)
                                    }
                                    uiState.isAccessibilityEnabled -> {
                                        OutloopStatusPill(text = "Waiting", tone = PillTone.WARNING)
                                    }
                                    else -> {
                                        OutloopStatusPill(text = "Needed", tone = PillTone.WARNING)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Card 3: Tracking and display
            item {
                OutloopStaggeredEntrance(index = 2) {
                    OutloopTonalCard(
                        cornerRadius = OutloopMetrics.CardCornerRadius,
                        contentPadding = PaddingValues(18.dp)
                    ) {
                        Text(
                            text = "Tracking and display",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.secondaryInk
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Time estimate per loop",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.ink
                            )
                            OutloopSegmentedControl(
                                options = listOf(60 to "1m", 120 to "2m", 180 to "3m", 300 to "5m"),
                                selected = selectedSeconds,
                                onSelect = onSetReclaimedSeconds,
                                modifier = Modifier.testTag("settings_estimate_selector")
                            )
                            OutloopHairlineDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Haptic feedback",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                                    color = colors.ink
                                )
                                OutloopSwitch(
                                    checked = uiState.preferences.hapticFeedbackEnabled,
                                    onCheckedChange = onSetHapticFeedback,
                                    contentDescription = "Haptic feedback",
                                    modifier = Modifier.testTag("settings_haptics_switch")
                                )
                            }
                            OutloopHairlineDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Intervention banner",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                                    color = colors.ink
                                )
                                OutloopSwitch(
                                    checked = uiState.preferences.showInterventionBanner,
                                    onCheckedChange = onSetShowBanner,
                                    contentDescription = "Intervention banner",
                                    modifier = Modifier.testTag("settings_notifications_switch")
                                )
                            }
                        }
                    }
                }
            }

            // Card 4: Data and diagnostics
            item {
                OutloopStaggeredEntrance(index = 3) {
                    OutloopTonalCard(
                        cornerRadius = OutloopMetrics.CardCornerRadius,
                        contentPadding = PaddingValues(18.dp)
                    ) {
                        Text(
                            text = "Data and diagnostics",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.secondaryInk
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutloopListNavRow(
                            title = "How Guard works",
                            valueText = "4-step tour",
                            onClick = onOpenWalkthrough,
                            modifier = Modifier.testTag("settings_walkthrough")
                        )
                        OutloopHairlineDivider()
                        OutloopListNavRow(
                            title = "How detection works",
                            onClick = onOpenPrivacy,
                            modifier = Modifier.testTag("settings_privacy")
                        )
                        OutloopHairlineDivider()
                        OutloopListNavRow(
                            title = "Diagnostics",
                            onClick = onOpenDiagnostics,
                            modifier = Modifier.testTag("settings_diagnostics")
                        )
                        OutloopHairlineDivider()
                        OutloopListNavRow(
                            title = "Export local backup",
                            valueText = "JSON",
                            onClick = {
                                haptic()
                                val dateStamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                exportBackupLauncher.launch("outloop-backup-$dateStamp.json")
                            },
                            modifier = Modifier.testTag("settings_export_backup")
                        )
                        OutloopHairlineDivider()
                        OutloopListNavRow(
                            title = "Restore from backup",
                            onClick = {
                                haptic()
                                importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                            modifier = Modifier.testTag("settings_restore_backup")
                        )
                        OutloopHairlineDivider()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    haptic()
                                    showClearHistorySheet = true
                                }
                                .testTag("settings_clear_history_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Clear activity history",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.destructive
                            )
                        }
                    }
                }
            }

            // Card 5: About
            item {
                OutloopStaggeredEntrance(index = 4) {
                    OutloopTonalCard(
                        cornerRadius = OutloopMetrics.CardCornerRadius,
                        contentPadding = PaddingValues(18.dp)
                    ) {
                        Text(
                            text = "About",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.secondaryInk
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsNavBentoRow(
                            icon = Icons.Outlined.Info,
                            title = "About Outloop",
                            subtitle = "Version ${BuildConfig.VERSION_NAME}",
                            onClick = { showAboutSheet = true },
                            modifier = Modifier.testTag("settings_about_row")
                        )
                    }
                }
            }
        }
    }

    if (showScheduleSheet) {
        QuietHoursSheet(
            initialEnabled = uiState.preferences.quietHoursEnabled,
            initialStartMinutes = uiState.preferences.quietStartMinutes,
            initialEndMinutes = uiState.preferences.quietEndMinutes,
            onSave = { enabled, startMin, endMin ->
                onSetQuietHours(enabled, startMin, endMin)
                showScheduleSheet = false
                onShowToast(
                    if (enabled) {
                        "Quiet hours set for ${AppPreferences.formatMinutesOfDay(startMin)} \u2013 ${AppPreferences.formatMinutesOfDay(endMin)}."
                    } else {
                        "Quiet hours turned off."
                    }
                )
            },
            onDismiss = { showScheduleSheet = false }
        )
    }

    if (showAlwaysOpenSheet) {
        AlwaysOpenSheet(
            alwaysOpenApps = uiState.preferences.alwaysOpenApps,
            utilityApps = uiState.utilityApps,
            launchableApps = uiState.launchableApps,
            monitoredPackages = uiState.rules.map { it.packageName }.toSet(),
            onToggleApp = onToggleAlwaysOpenApp,
            onDismiss = { showAlwaysOpenSheet = false }
        )
    }

    if (showAboutSheet) {
        OutloopModalSheet(
            title = "About Outloop",
            onDismiss = { showAboutSheet = false }
        ) {
            Text(
                text = "Outloop ${BuildConfig.VERSION_NAME} \u00B7 On-device digital wellbeing",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink
            )
            Text(
                text = "All feed detection, usage tracking, and activity insights run strictly on your device. Outloop never uploads screen activity, browsing habits, or account data. Optional rule updates only check public selector definitions over HTTPS.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryInk
            )
            OutloopTonalCard(
                cornerRadius = OutloopMetrics.SubCardCornerRadius,
                contentPadding = PaddingValues(0.dp)
            ) {
                OutloopListNavRow(
                    title = "How detection works",
                    onClick = {
                        showAboutSheet = false
                        onOpenPrivacy()
                    },
                    modifier = Modifier.testTag("about_privacy_row")
                )
            }
            OutloopPrimaryButton(
                text = "Done",
                onClick = { showAboutSheet = false },
                modifier = Modifier.testTag("about_done_button")
            )
        }
    }

    if (showDefaultResponseSheet) {
        OutloopModalSheet(
            title = "Default response",
            onDismiss = { showDefaultResponseSheet = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(OutloopMetrics.ItemSpacing)) {
                listOf(
                    InterventionMode.OVERLAY,
                    InterventionMode.BACK,
                    InterventionMode.HOME_TAB
                ).forEach { mode ->
                    OutloopRadioOptionCard(
                        title = mode.shortLabel,
                        subtitle = mode.explanation,
                        selected = uiState.preferences.defaultInterventionMode == mode,
                        onClick = { onSelectDefaultMode(mode) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutloopPrimaryButton(
                text = "Done",
                onClick = { showDefaultResponseSheet = false }
            )
        }
    }

    if (showDailyBudgetSheet) {
        DailyBudgetSheet(
            selectedBudget = uiState.preferences.dailyBudgetCuts,
            onSelectBudget = { cuts ->
                onSetDailyBudget(cuts)
                showDailyBudgetSheet = false
                onShowToast(
                    if (cuts > 0) "Daily pause target set to $cuts feeds." else "Daily pause target turned off."
                )
            },
            onDismiss = { showDailyBudgetSheet = false }
        )
    }

    if (showGlobalFeedBudgetSheet) {
        GlobalFeedBudgetSheet(
            initialMinutes = uiState.preferences.globalFeedTimeBudgetMinutes,
            onSave = { minutes ->
                onSetGlobalFeedBudget(minutes)
                showGlobalFeedBudgetSheet = false
                onShowToast(
                    if (minutes > 0) {
                        "Global feed budget set to ${minutes}m per day."
                    } else {
                        "Global feed budget turned off."
                    }
                )
            },
            onDismiss = { showGlobalFeedBudgetSheet = false }
        )
    }

    if (pendingBackupPreview != null && pendingBackupJson != null) {
        val preview = pendingBackupPreview!!
        val formattedDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            .format(Date(preview.exportedAtMs))
        OutloopModalSheet(
            title = "Restore this backup?",
            onDismiss = {
                pendingBackupPreview = null
                pendingBackupJson = null
            }
        ) {
            Text(
                text = "Exported on $formattedDate. Contains ${preview.ruleCount} app rules" +
                    (if (preview.customRuleCount > 0) " (${preview.customRuleCount} custom)" else "") +
                    " and ${preview.eventCount} recorded interruptions.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.secondaryInk
            )
            OutloopPrimaryButton(
                text = "Restore backup",
                onClick = {
                    val jsonToRestore = pendingBackupJson!!
                    pendingBackupPreview = null
                    pendingBackupJson = null
                    onRestoreBackupJson(jsonToRestore, onShowToast)
                },
                modifier = Modifier.testTag("confirm_restore_backup_button")
            )
            OutloopSecondaryButton(
                text = "Cancel",
                onClick = {
                    pendingBackupPreview = null
                    pendingBackupJson = null
                }
            )
        }
    }

    if (showClearHistorySheet) {
        OutloopModalSheet(
            title = "Clear activity history?",
            onDismiss = { showClearHistorySheet = false }
        ) {
            Text(
                text = "This removes your counts and insights from this device. It can\u2019t be undone.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.secondaryInk
            )
            val destructiveInteraction = remember { MutableInteractionSource() }
            Button(
                onClick = {
                    haptic()
                    onClearHistory()
                    showClearHistorySheet = false
                    onShowToast("Activity history cleared.")
                },
                interactionSource = destructiveInteraction,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.destructive,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .outloopPressScale(destructiveInteraction)
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("confirm_clear_history_button")
            ) {
                Text(
                    text = "Clear history",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            OutloopSecondaryButton(
                text = "Cancel",
                onClick = { showClearHistorySheet = false }
            )
        }
    }

    if (showAccessibilityDisclosureSheet) {
        OutloopModalSheet(
            title = "Accessibility disclosure",
            onDismiss = { showAccessibilityDisclosureSheet = false }
        ) {
            Text(
                text = "Outloop uses Android\u2019s Accessibility Service to check on-screen view IDs, active app names, and navigation tab labels only in the apps you select, so it can pause Shorts, Reels, or Watch feeds, return to the app\u2019s Home tab, and track your local daily limits.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.secondaryInk
            )
            Text(
                text = "It never reads your messages, passwords, or video content. All detection runs strictly on your device\u2014no personal or screen data is collected, stored off-device, or shared with third parties. You can turn access off anytime in Android Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryInk
            )
            Text(
                text = "How detection works",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textAction,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        showAccessibilityDisclosureSheet = false
                        onOpenPrivacy()
                    }
                    .padding(vertical = 4.dp)
            )
            OutloopPrimaryButton(
                text = "Agree and continue",
                onClick = {
                    onAcceptDisclosure()
                    showAccessibilityDisclosureSheet = false
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }
                },
                modifier = Modifier.testTag("settings_disclosure_agree")
            )
            OutloopSecondaryButton(
                text = "Not now",
                onClick = { showAccessibilityDisclosureSheet = false },
                modifier = Modifier.testTag("settings_disclosure_decline")
            )
        }
    }
}

@Composable
private fun SettingsToggleBentoRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTestTag: String
) {
    val colors = OutloopTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutloopIconBadge(icon = icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryInk
            )
        }
        OutloopSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            contentDescription = title,
            modifier = Modifier.testTag(switchTestTag)
        )
    }
}

@Composable
private fun SettingsNavBentoRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OutloopTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutloopIconBadge(icon = icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryInk
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.secondaryInk,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun AlwaysOpenSheet(
    alwaysOpenApps: Set<String>,
    utilityApps: List<LaunchableAppInfo>,
    launchableApps: List<LaunchableAppInfo>,
    monitoredPackages: Set<String>,
    onToggleApp: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = OutloopTheme.colors
    val essentialTools = remember(utilityApps) {
        if (utilityApps.isNotEmpty()) {
            utilityApps
        } else {
            AppPreferences.DEFAULT_ALWAYS_OPEN_APPS
                .map { pkg ->
                    LaunchableAppInfo(
                        packageName = pkg,
                        displayName = AppPreferences.ALWAYS_OPEN_FRIENDLY_NAMES[pkg] ?: pkg
                    )
                }
                .sortedBy { it.displayName.lowercase() }
        }
    }
    val essentialPackages = remember(essentialTools) { essentialTools.map { it.packageName }.toSet() }
    val installedOthers = launchableApps
        .filter { it.packageName !in monitoredPackages && it.packageName !in essentialPackages }
        .take(20)

    OutloopModalSheet(
        title = "Always open",
        onDismiss = onDismiss
    ) {
        Text(
            text = "These everyday utility apps are never touched or paused by Outloop Guard.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.secondaryInk
        )

        OutloopTonalCard(
            cornerRadius = OutloopMetrics.SubCardCornerRadius,
            contentPadding = PaddingValues(14.dp)
        ) {
            Text(
                text = "Essential tools",
                style = MaterialTheme.typography.labelMedium,
                color = colors.secondaryInk
            )
            Spacer(modifier = Modifier.height(6.dp))
            essentialTools.forEachIndexed { idx, utility ->
                if (idx > 0) OutloopHairlineDivider()
                val isChecked = utility.packageName in alwaysOpenApps
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { onToggleApp(utility.packageName, !isChecked) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = utility.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.ink,
                        modifier = Modifier.weight(1f)
                    )
                    OutloopRoundedCheckbox(
                        checked = isChecked,
                        onCheckedChange = { onToggleApp(utility.packageName, it) },
                        contentDescription = "Keep ${utility.displayName} always open"
                    )
                }
            }
        }

        if (installedOthers.isNotEmpty()) {
            OutloopTonalCard(
                cornerRadius = OutloopMetrics.SubCardCornerRadius,
                contentPadding = PaddingValues(14.dp)
            ) {
                Text(
                    text = "Installed apps",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.secondaryInk
                )
                Spacer(modifier = Modifier.height(6.dp))
                installedOthers.forEachIndexed { idx, app ->
                    if (idx > 0) OutloopHairlineDivider()
                    val isChecked = app.packageName in alwaysOpenApps
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onToggleApp(app.packageName, !isChecked) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = app.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.ink,
                            modifier = Modifier.weight(1f)
                        )
                        OutloopRoundedCheckbox(
                            checked = isChecked,
                            onCheckedChange = { onToggleApp(app.packageName, it) },
                            contentDescription = "Keep ${app.displayName} always open"
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        OutloopPrimaryButton(
            text = "Done",
            onClick = onDismiss,
            modifier = Modifier.testTag("settings_always_open_done_button")
        )
    }
}

@Composable
private fun QuietHoursSheet(
    initialEnabled: Boolean,
    initialStartMinutes: Int,
    initialEndMinutes: Int,
    onSave: (Boolean, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = OutloopTheme.colors
    var enabled by rememberSaveable { mutableStateOf(initialEnabled) }
    var startMinutes by rememberSaveable { mutableIntStateOf(initialStartMinutes) }
    var endMinutes by rememberSaveable { mutableIntStateOf(initialEndMinutes) }

    OutloopModalSheet(
        title = "Schedule & quiet hours",
        onDismiss = onDismiss
    ) {
        Text(
            text = "Guard runs all day by default, or steps back automatically during your chosen quiet hours.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.secondaryInk
        )

        OutloopTonalCard(
            cornerRadius = OutloopMetrics.SubCardCornerRadius,
            contentPadding = PaddingValues(horizontal = OutloopMetrics.ListRowPaddingHorizontal, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Enable quiet hours",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink
                )
                OutloopSwitch(
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                    contentDescription = "Enable quiet hours",
                    modifier = Modifier.testTag("quiet_hours_switch")
                )
            }
        }

        if (enabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "10p\u20137a" to (22 * 60 to 7 * 60),
                    "11p\u20138a" to (23 * 60 to 8 * 60),
                    "9p\u20136a" to (21 * 60 to 6 * 60)
                ).forEach { (label, range) ->
                    val active = startMinutes == range.first && endMinutes == range.second
                    Surface(
                        shape = CircleShape,
                        color = if (active) colors.heroDark else colors.surfaceElevated,
                        border = BorderStroke(1.dp, colors.hairlineBorder),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(CircleShape)
                            .clickable {
                                startMinutes = range.first
                                endMinutes = range.second
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) colors.onPrimaryTint else colors.ink
                            )
                        }
                    }
                }
            }

            TimeStepperRow(
                label = "Starts at",
                minutesOfDay = startMinutes,
                onChange = { startMinutes = it }
            )
            TimeStepperRow(
                label = "Ends at",
                minutesOfDay = endMinutes,
                onChange = { endMinutes = it }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        OutloopPrimaryButton(
            text = "Save schedule",
            onClick = { onSave(enabled, startMinutes, endMinutes) },
            modifier = Modifier.testTag("save_quiet_hours_button")
        )
    }
}

@Composable
private fun TimeStepperRow(
    label: String,
    minutesOfDay: Int,
    onChange: (Int) -> Unit
) {
    val colors = OutloopTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OutloopMetrics.SubCardCornerRadius))
            .background(colors.surfaceCard)
            .border(1.dp, colors.hairlineBorder, RoundedCornerShape(OutloopMetrics.SubCardCornerRadius))
            .padding(horizontal = OutloopMetrics.ListRowPaddingHorizontal, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.secondaryInk
            )
            Text(
                text = AppPreferences.formatMinutesOfDay(minutesOfDay),
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = colors.ink
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            StepCircleIconButton(
                icon = Icons.Rounded.Remove,
                contentDescription = "Earlier $label",
                onClick = { onChange((minutesOfDay - 30 + 1440) % 1440) }
            )
            StepCircleIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "Later $label",
                onClick = { onChange((minutesOfDay + 30) % 1440) }
            )
        }
    }
}

@Composable
private fun StepCircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .outloopPressScale(interactionSource, 0.92f)
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.surfaceElevated)
                .border(1.dp, colors.hairlineBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colors.ink,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun DailyBudgetSheet(
    selectedBudget: Int,
    onSelectBudget: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = OutloopTheme.colors
    OutloopModalSheet(
        title = "Daily pause target",
        onDismiss = onDismiss
    ) {
        Text(
            text = "Track your daily loop breaks on Home with a calm target badge.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.secondaryInk
        )
        Column(verticalArrangement = Arrangement.spacedBy(OutloopMetrics.ItemSpacing)) {
            listOf(
                0 to ("Off" to "Count loops broken without a daily target."),
                5 to ("5 loops" to "Light daily awareness."),
                10 to ("10 loops" to "Balanced everyday intention."),
                15 to ("15 loops" to "Steady redirection throughout the day."),
                20 to ("20 loops" to "High-frequency habit reset.")
            ).forEach { (value, copy) ->
                OutloopRadioOptionCard(
                    title = copy.first,
                    subtitle = copy.second,
                    selected = selectedBudget == value,
                    onClick = { onSelectBudget(value) },
                    modifier = Modifier.testTag("budget_option_$value")
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GlobalFeedBudgetSheet(
    initialMinutes: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    var draftMinutes by rememberSaveable(initialMinutes) {
        mutableIntStateOf(
            initialMinutes.coerceIn(
                AppPreferences.MIN_GLOBAL_FEED_BUDGET_MINUTES,
                AppPreferences.MAX_GLOBAL_FEED_BUDGET_MINUTES
            )
        )
    }

    OutloopModalSheet(
        title = "Global feed budget",
        onDismiss = onDismiss
    ) {
        Text(
            text = "Set a combined daily time cap across all guarded apps. When reached, Outloop guards entire monitored apps with Pause Guard.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.secondaryInk
        )

        OutloopTonalCard(
            cornerRadius = OutloopMetrics.SubCardCornerRadius,
            contentPadding = PaddingValues(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepCircleIconButton(
                    icon = Icons.Rounded.Remove,
                    contentDescription = "Decrease global feed budget by 5 minutes",
                    onClick = {
                        draftMinutes = (draftMinutes - 5).coerceAtLeast(AppPreferences.MIN_GLOBAL_FEED_BUDGET_MINUTES)
                    },
                    modifier = Modifier.testTag("global_budget_minus_button")
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (draftMinutes == 0) "Off" else "$draftMinutes min",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        color = colors.ink,
                        modifier = Modifier.testTag("global_budget_draft_value")
                    )
                    Text(
                        text = if (draftMinutes == 0) "No combined cap" else "per day across guarded apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryInk
                    )
                }

                StepCircleIconButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "Increase global feed budget by 5 minutes",
                    onClick = {
                        draftMinutes = (draftMinutes + 5).coerceAtMost(AppPreferences.MAX_GLOBAL_FEED_BUDGET_MINUTES)
                    },
                    modifier = Modifier.testTag("global_budget_plus_button")
                )
            }
        }

        val presets = listOf(0 to "Off", 30 to "30m", 60 to "60m", 90 to "90m", 120 to "2h")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { (minutes, label) ->
                val selected = draftMinutes == minutes
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(CircleShape)
                        .background(if (selected) colors.heroDark else colors.surfaceSubtle)
                        .border(
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (selected) colors.heroDark else colors.hairlineBorder
                            ),
                            shape = CircleShape
                        )
                        .clickable {
                            haptic()
                            draftMinutes = minutes
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("global_budget_preset_$minutes"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontFeatureSettings = "tnum"
                        ),
                        color = if (selected) colors.onPrimaryTint else colors.ink
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        OutloopPrimaryButton(
            text = "Save budget",
            onClick = { onSave(draftMinutes) },
            modifier = Modifier.testTag("save_global_feed_budget_button")
        )
    }
}
