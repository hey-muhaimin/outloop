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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.local.AppPreferences
import com.example.data.local.AppearanceMode
import com.example.data.model.InterventionMode
import com.example.data.repository.OutloopBackupPreview
import com.example.ui.MainUiState
import com.example.ui.components.OutloopHairlineDivider
import com.example.ui.components.OutloopListNavRow
import com.example.ui.components.OutloopModalSheet
import com.example.ui.components.OutloopPrimaryButton
import com.example.ui.components.OutloopRadioOptionCard
import com.example.ui.components.OutloopSecondaryButton
import com.example.ui.components.OutloopSectionHeader
import com.example.ui.components.OutloopSegmentedControl
import com.example.ui.components.OutloopStatusPill
import com.example.ui.components.OutloopSwitch
import com.example.ui.components.OutloopTonalCard
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

    var showDefaultResponseSheet by rememberSaveable { mutableStateOf(false) }
    var showQuietHoursSheet by rememberSaveable { mutableStateOf(false) }
    var showDailyBudgetSheet by rememberSaveable { mutableStateOf(false) }
    var showClearHistorySheet by rememberSaveable { mutableStateOf(false) }
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

    val selectedSeconds = when (uiState.preferences.reclaimedSecondsPerCut) {
        in 0..90 -> 60
        in 91..150 -> 120
        in 151..240 -> 180
        else -> 300
    }

    val quietHoursValueText = if (uiState.preferences.quietHoursEnabled) {
        "${AppPreferences.formatMinutesOfDay(uiState.preferences.quietStartMinutes)} \u2013 ${AppPreferences.formatMinutesOfDay(uiState.preferences.quietEndMinutes)}"
    } else {
        "Off"
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
                top = 24.dp,
                bottom = bottomContentPadding
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.ink
                )
            }

            // Group 1: Protection
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutloopSectionHeader(text = "Protection")
                    OutloopTonalCard(cornerRadius = 24.dp, contentPadding = PaddingValues(0.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Accessibility service",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Normal
                                ),
                                color = colors.ink
                            )
                            when {
                                uiState.isAccessibilityEnabled && uiState.isServiceConnected -> {
                                    OutloopStatusPill(
                                        text = "Connected",
                                        tone = PillTone.SUCCESS,
                                        leadingIcon = Icons.Rounded.Check
                                    )
                                }
                                uiState.isAccessibilityEnabled -> {
                                    OutloopStatusPill(
                                        text = "Disconnected",
                                        tone = PillTone.WARNING
                                    )
                                }
                                else -> {
                                    OutloopStatusPill(
                                        text = "Not enabled",
                                        tone = PillTone.WARNING
                                    )
                                }
                            }
                        }
                        OutloopHairlineDivider()
                        OutloopListNavRow(
                            title = "Protected apps",
                            valueText = uiState.activeInstalledCount.toString(),
                            onClick = onOpenManageApps,
                            modifier = Modifier.testTag("settings_protected_apps")
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
                            title = "Quiet hours",
                            valueText = quietHoursValueText,
                            onClick = { showQuietHoursSheet = true },
                            modifier = Modifier.testTag("settings_quiet_hours")
                        )
                        OutloopHairlineDivider()
                        OutloopListNavRow(
                            title = "Daily pause target",
                            valueText = dailyTargetValueText,
                            onClick = { showDailyBudgetSheet = true },
                            modifier = Modifier.testTag("settings_daily_budget")
                        )
                    }
                }
            }

            // Group 2: Appearance & Preferences
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutloopSectionHeader(text = "Appearance")
                    OutloopTonalCard(cornerRadius = 24.dp, contentPadding = PaddingValues(0.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
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
                        }
                        OutloopHairlineDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Time estimate per interruption",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Normal
                                ),
                                color = colors.ink,
                                modifier = Modifier.padding(horizontal = 2.dp)
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
                                text = "Haptic feedback",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Normal
                                ),
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
                                text = "Notifications",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Normal
                                ),
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
                }
            }

            // Group 3: Privacy and permissions
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutloopSectionHeader(text = "Privacy and permissions")
                    OutloopTonalCard(cornerRadius = 24.dp, contentPadding = PaddingValues(0.dp)) {
                        OutloopListNavRow(
                            title = "Accessibility permission",
                            trailingBadge = if (!uiState.isAccessibilityEnabled) {
                                {
                                    OutloopStatusPill(text = "Needed", tone = PillTone.WARNING)
                                }
                            } else {
                                null
                            },
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                            }
                        )
                        OutloopHairlineDivider()
                        OutloopListNavRow(
                            title = "How detection works",
                            onClick = onOpenPrivacy,
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
                            onClick = onOpenDiagnostics,
                            modifier = Modifier.testTag("settings_diagnostics")
                        )
                        OutloopHairlineDivider()
                        OutloopListNavRow(
                            title = "About Outloop",
                            valueText = BuildConfig.VERSION_NAME,
                            onClick = {
                                onShowToast("Outloop ${BuildConfig.VERSION_NAME} \u00B7 On-device digital wellbeing")
                            }
                        )
                    }
                }
            }

            // Group 4: Data & Local Backups
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutloopSectionHeader(text = "Data")
                    OutloopTonalCard(cornerRadius = 24.dp, contentPadding = PaddingValues(0.dp)) {
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
                                    showClearHistorySheet = true
                                }
                                .testTag("settings_clear_history_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Clear activity history",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Normal
                                ),
                                color = colors.destructive
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDefaultResponseSheet) {
        OutloopModalSheet(
            title = "Default response",
            onDismiss = { showDefaultResponseSheet = false }
        ) {
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
            OutloopPrimaryButton(
                text = "Done",
                onClick = { showDefaultResponseSheet = false }
            )
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
        title = "Quiet hours",
        onDismiss = onDismiss
    ) {
        Text(
            text = "Outloop steps back automatically during your chosen rest window.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.secondaryInk
        )

        OutloopTonalCard(cornerRadius = 20.dp, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
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
                        color = if (active) colors.primaryTint else colors.surfaceElevated,
                        border = BorderStroke(1.dp, colors.hairlineBorder),
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
            .background(colors.surfaceCard)
            .border(1.dp, colors.hairlineBorder, RoundedCornerShape(18.dp))
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(colors.surfaceElevated)
            .border(1.dp, colors.hairlineBorder, CircleShape)
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
            text = "Track your mindful redirections each day with a calm goal ring on Home.",
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
