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
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WbSunny
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.data.local.AppPreferences
import com.example.data.local.AppearanceMode
import com.example.data.model.InterventionMode
import com.example.data.model.ProtectionStatus
import com.example.data.repository.OutloopBackupPreview
import com.example.ui.MainUiState
import com.example.ui.components.OutloopBentoCard
import com.example.ui.components.OutloopBentoHeader
import com.example.ui.components.OutloopHairlineDivider
import com.example.ui.components.OutloopIconBadge
import com.example.ui.components.OutloopListNavRow
import com.example.ui.components.OutloopModalSheet
import com.example.ui.components.OutloopPauseGuardDialog
import com.example.ui.components.OutloopPrimaryButton
import com.example.ui.components.OutloopRadioOptionCard
import com.example.ui.components.OutloopSecondaryButton
import com.example.ui.components.OutloopSegmentedControl
import com.example.ui.components.OutloopStatusPill
import com.example.ui.components.OutloopSwitch
import com.example.ui.components.PillTone
import com.example.ui.components.rememberOutloopHaptic
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
    onSelectDefaultMode: (InterventionMode) -> Unit,
    onSelectAppearanceMode: (AppearanceMode) -> Unit,
    onSetReclaimedSeconds: (Int) -> Unit,
    onSetHapticFeedback: (Boolean) -> Unit,
    onSetShowBanner: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onShowToast: (String) -> Unit,
    onSetQuietHours: (Boolean, Int, Int) -> Unit = { _, _, _ -> },
    onSetDailyBudget: (Int) -> Unit = {},
    onSetStrictModeEnabled: (Boolean) -> Unit = {},
    onToggleGlobalPause: (Boolean) -> Unit = {},
    onPauseForMinutes: (Int) -> Unit = {},
    onStatusClick: () -> Unit = {},
    onExportBackupUri: (Uri, (String) -> Unit) -> Unit = { _, cb -> cb("Backup exported cleanly.") },
    onInspectBackupUri: (Uri, (OutloopBackupPreview?, String?, String?) -> Unit) -> Unit = { _, cb ->
        cb(null, null, "Backup unavailable")
    },
    onRestoreBackupJson: (String, (String) -> Unit) -> Unit = { _, cb -> cb("Backup restored.") },
    bottomContentPadding: Dp = 116.dp
) {
    val context = LocalContext.current
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    val privacyPolicyUrl = stringResource(R.string.privacy_policy_url)

    var showQuietHoursSheet by rememberSaveable { mutableStateOf(false) }
    var showAlwaysOpenSheet by rememberSaveable { mutableStateOf(false) }
    var showDailyBudgetSheet by rememberSaveable { mutableStateOf(false) }
    var showThemeSheet by rememberSaveable { mutableStateOf(false) }
    var showAboutSheet by rememberSaveable { mutableStateOf(false) }
    var showClearHistorySheet by rememberSaveable { mutableStateOf(false) }
    var showStrictPauseDialog by rememberSaveable { mutableStateOf(false) }
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

    val statusPill = when (uiState.protectionStatus) {
        ProtectionStatus.OPERATIONAL -> "Guard on" to PillTone.SUCCESS
        ProtectionStatus.PAUSED -> "Paused" to PillTone.WARNING
        ProtectionStatus.QUIET_HOURS -> "Quiet hours" to PillTone.NEUTRAL
        ProtectionStatus.NO_APPS_ACTIVE -> "Guard off" to PillTone.NEUTRAL
        ProtectionStatus.PERMISSION_NEEDED -> "Setup needed" to PillTone.WARNING
        ProtectionStatus.SERVICE_DISCONNECTED -> "Needs attention" to PillTone.WARNING
    }

    val selectedSeconds = when (uiState.preferences.reclaimedSecondsPerCut) {
        in 0..90 -> 60
        in 91..150 -> 120
        in 151..240 -> 180
        else -> 300
    }

    val scheduleSubtitle = if (uiState.preferences.quietHoursEnabled) {
        "Rest ${AppPreferences.formatMinutesOfDay(uiState.preferences.quietStartMinutes)} to ${AppPreferences.formatMinutesOfDay(uiState.preferences.quietEndMinutes)}"
    } else {
        "All day \u00B7 Tap to schedule rest"
    }

    val dailyTargetValueText = if (uiState.preferences.dailyBudgetCuts > 0) {
        "${uiState.preferences.dailyBudgetCuts} / day"
    } else {
        "Off"
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .testTag("settings_list"),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 18.dp,
                bottom = bottomContentPadding
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                OutloopBentoHeader(
                    title = "Settings",
                    statusText = statusPill.first,
                    statusTone = statusPill.second,
                    onStatusClick = onStatusClick
                )
            }

            // Card 1: Guard
            item {
                OutloopBentoCard(
                    cornerRadius = 24.dp,
                    contentPadding = PaddingValues(top = 16.dp, bottom = 10.dp)
                ) {
                    SettingsCardTitle("Guard")
                    SettingsSwitchRow(
                        icon = Icons.Outlined.Shield,
                        title = "Guard",
                        subtitle = "Blocks feeds in guarded apps",
                        checked = !uiState.isGloballyPaused,
                        onCheckedChange = { turnOn ->
                            if (!turnOn && uiState.preferences.strictModeEnabled) {
                                showStrictPauseDialog = true
                            } else {
                                onToggleGlobalPause(!turnOn)
                            }
                        },
                        switchTestTag = "settings_guard_switch"
                    )
                    SettingsSwitchRow(
                        icon = Icons.Outlined.Lock,
                        title = "Strict mode",
                        subtitle = "Wait 10 s before pausing",
                        checked = uiState.preferences.strictModeEnabled,
                        onCheckedChange = onSetStrictModeEnabled,
                        switchTestTag = "settings_strict_mode_switch"
                    )
                    OutloopListNavRow(
                        leadingIcon = Icons.Outlined.Schedule,
                        title = "Schedule",
                        subtitle = scheduleSubtitle,
                        onClick = { showQuietHoursSheet = true },
                        modifier = Modifier.testTag("settings_quiet_hours")
                    )
                }
            }

            // Card 2: Apps and access
            item {
                OutloopBentoCard(
                    cornerRadius = 24.dp,
                    contentPadding = PaddingValues(top = 16.dp, bottom = 10.dp)
                ) {
                    SettingsCardTitle("Apps and access")
                    OutloopListNavRow(
                        leadingIcon = Icons.Outlined.GridView,
                        title = "Guarded apps",
                        subtitle = "${uiState.activeInstalledCount} app${if (uiState.activeInstalledCount == 1) "" else "s"}",
                        onClick = onOpenManageApps,
                        modifier = Modifier.testTag("settings_protected_apps")
                    )
                    OutloopListNavRow(
                        leadingIcon = Icons.Outlined.CheckCircleOutline,
                        title = "Always open",
                        subtitle = "Messages, DMs, Search \u00B7 ${uiState.preferences.defaultInterventionMode.shortLabel}",
                        onClick = { showAlwaysOpenSheet = true },
                        modifier = Modifier.testTag("settings_always_open")
                    )
                    OutloopListNavRow(
                        leadingIcon = Icons.Outlined.AccessibilityNew,
                        title = "Accessibility service",
                        subtitle = "Needed to spot feeds",
                        trailingBadge = {
                            when {
                                uiState.isAccessibilityEnabled && uiState.isServiceConnected -> {
                                    OutloopStatusPill(text = "Active", tone = PillTone.SUCCESS)
                                }
                                uiState.isAccessibilityEnabled -> {
                                    OutloopStatusPill(text = "Waiting", tone = PillTone.WARNING)
                                }
                                else -> {
                                    OutloopStatusPill(text = "Off", tone = PillTone.WARNING)
                                }
                            }
                        },
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }
                        },
                        modifier = Modifier.testTag("settings_accessibility_row")
                    )
                }
            }

            // Card 3: General
            item {
                OutloopBentoCard(
                    cornerRadius = 24.dp,
                    contentPadding = PaddingValues(top = 16.dp, bottom = 10.dp)
                ) {
                    SettingsCardTitle("General")
                    OutloopListNavRow(
                        leadingIcon = Icons.Outlined.WbSunny,
                        title = "Theme",
                        subtitle = uiState.preferences.appearanceMode.label,
                        onClick = { showThemeSheet = true },
                        modifier = Modifier.testTag("settings_theme_row")
                    )
                    OutloopListNavRow(
                        leadingIcon = Icons.Outlined.Info,
                        title = "About Outloop",
                        subtitle = "Version ${BuildConfig.VERSION_NAME}",
                        onClick = { showAboutSheet = true },
                        modifier = Modifier.testTag("settings_about_row")
                    )
                }
            }
        }
    }

    if (showAlwaysOpenSheet) {
        OutloopModalSheet(
            title = "Guard behavior",
            onDismiss = { showAlwaysOpenSheet = false }
        ) {
            Text(
                text = "Messages, Search, and other useful screens stay open. Customize how Outloop responds when a guarded feed appears.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryInk
            )

            Text(
                text = "Default response",
                style = MaterialTheme.typography.labelLarge,
                color = colors.secondaryInk
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Text(
                text = "Time estimate per loop",
                style = MaterialTheme.typography.labelLarge,
                color = colors.secondaryInk
            )
            OutloopSegmentedControl(
                options = listOf(
                    60 to "1 min",
                    120 to "2 min",
                    180 to "3 min",
                    300 to "5 min"
                ),
                selected = selectedSeconds,
                onSelect = onSetReclaimedSeconds,
                modifier = Modifier.testTag("settings_estimate_selector")
            )

            OutloopBentoCard(
                cornerRadius = 20.dp,
                contentPadding = PaddingValues(0.dp)
            ) {
                OutloopListNavRow(
                    title = "Daily pause target",
                    valueText = dailyTargetValueText,
                    onClick = {
                        showAlwaysOpenSheet = false
                        showDailyBudgetSheet = true
                    },
                    modifier = Modifier.testTag("settings_daily_budget")
                )
                OutloopHairlineDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Haptic feedback",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
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
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Quick-pass banner",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                        color = colors.ink
                    )
                    OutloopSwitch(
                        checked = uiState.preferences.showInterventionBanner,
                        onCheckedChange = onSetShowBanner,
                        contentDescription = "Notifications",
                        modifier = Modifier.testTag("settings_notifications_switch")
                    )
                }
            }

            OutloopPrimaryButton(
                text = "Done",
                onClick = { showAlwaysOpenSheet = false }
            )
        }
    }

    if (showThemeSheet) {
        OutloopModalSheet(
            title = "Theme",
            onDismiss = { showThemeSheet = false }
        ) {
            Text(
                text = "Choose how Outloop looks on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryInk
            )
            OutloopSegmentedControl(
                options = listOf(
                    AppearanceMode.LIGHT to AppearanceMode.LIGHT.label,
                    AppearanceMode.DARK to AppearanceMode.DARK.label,
                    AppearanceMode.SYSTEM to AppearanceMode.SYSTEM.label
                ),
                selected = uiState.preferences.appearanceMode,
                onSelect = onSelectAppearanceMode,
                modifier = Modifier.testTag("settings_appearance_selector")
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutloopPrimaryButton(
                text = "Done",
                onClick = { showThemeSheet = false }
            )
        }
    }

    if (showAboutSheet) {
        OutloopModalSheet(
            title = "About Outloop",
            onDismiss = { showAboutSheet = false }
        ) {
            Text(
                text = "Outloop ${BuildConfig.VERSION_NAME} \u00B7 On-device feed guard. Everything runs locally on your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryInk
            )

            OutloopBentoCard(cornerRadius = 20.dp, contentPadding = PaddingValues(0.dp)) {
                OutloopListNavRow(
                    title = "How detection works",
                    onClick = {
                        showAboutSheet = false
                        onOpenPrivacy()
                    },
                    modifier = Modifier.testTag("settings_privacy")
                )
                OutloopHairlineDivider()
                OutloopListNavRow(
                    title = "Privacy policy",
                    onClick = {
                        val opened = runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                            true
                        }.getOrDefault(false)
                        if (!opened) {
                            onShowToast("No browser available. See How detection works for our full local privacy guarantee.")
                        }
                    }
                )
                OutloopHairlineDivider()
                OutloopListNavRow(
                    title = "Diagnostics",
                    onClick = {
                        showAboutSheet = false
                        onOpenDiagnostics()
                    },
                    modifier = Modifier.testTag("settings_diagnostics")
                )
            }

            OutloopBentoCard(cornerRadius = 20.dp, contentPadding = PaddingValues(0.dp)) {
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
                        .heightIn(min = 54.dp)
                        .clickable {
                            haptic()
                            showAboutSheet = false
                            showClearHistorySheet = true
                        }
                        .testTag("settings_clear_history_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Clear activity history",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                        color = colors.destructive
                    )
                }
            }
        }
    }

    if (showQuietHoursSheet) {
        QuietHoursSheet(
            initialEnabled = uiState.preferences.quietHoursEnabled,
            initialStartMinutes = uiState.preferences.quietStartMinutes,
            initialEndMinutes = uiState.preferences.quietEndMinutes,
            onSave = { enabled, startMin, endMin ->
                onSetQuietHours(enabled, startMin, endMin)
                showQuietHoursSheet = false
                onShowToast(
                    if (enabled) {
                        "Quiet hours set for ${AppPreferences.formatMinutesOfDay(startMin)} \u2013 ${AppPreferences.formatMinutesOfDay(endMin)}."
                    } else {
                        "Quiet hours turned off."
                    }
                )
            },
            onDismiss = { showQuietHoursSheet = false }
        )
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
            Button(
                onClick = {
                    haptic()
                    onClearHistory()
                    showClearHistorySheet = false
                    onShowToast("Activity history cleared.")
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.destructive,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
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

    if (showStrictPauseDialog) {
        OutloopPauseGuardDialog(
            initialMinutes = 15,
            onKeepGuarding = { showStrictPauseDialog = false },
            onConfirmPause = { minutes ->
                showStrictPauseDialog = false
                onPauseForMinutes(minutes)
                onShowToast("Guard paused for $minutes minutes.")
            },
            onDismiss = { showStrictPauseDialog = false }
        )
    }
}

@Composable
private fun SettingsCardTitle(title: String) {
    val colors = OutloopTheme.colors
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        ),
        color = colors.secondaryInk,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
    )
}

@Composable
private fun SettingsSwitchRow(
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
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutloopIconBadge(icon = icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
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
        title = "Schedule",
        onDismiss = onDismiss
    ) {
        Text(
            text = "Outloop steps back automatically during your chosen rest window.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.secondaryInk
        )

        OutloopBentoCard(
            cornerRadius = 20.dp,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
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
                        color = if (active) colors.primaryTint else colors.cardSurfaceElevated,
                        border = BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
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
            .clip(RoundedCornerShape(18.dp))
            .background(colors.cardSurface)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    onClick: () -> Unit
) {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(colors.cardSurfaceElevated)
            .border(1.dp, colors.cardBorder, CircleShape)
            .clickable {
                haptic()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.ink,
            modifier = Modifier.size(20.dp)
        )
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
            text = "Track your mindful redirections each day with your goal ring on Home.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.secondaryInk
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                0 to ("Off" to "Count pauses without a daily target."),
                5 to ("5 pauses" to "Light daily awareness."),
                10 to ("10 pauses" to "Balanced everyday intention."),
                15 to ("15 pauses" to "Steady redirection throughout the day."),
                20 to ("20 pauses" to "High-frequency habit reset.")
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
