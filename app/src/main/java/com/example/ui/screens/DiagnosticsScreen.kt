package com.example.ui.screens

import androidx.activity.compose.BackHandler
import com.example.service.ProtectionHealthHelper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.local.AppPreferences
import com.example.data.model.ProtectionStatus
import com.example.ui.MainUiState
import com.example.ui.components.OutloopHairlineDivider
import com.example.ui.components.OutloopMetricCell
import com.example.ui.components.OutloopPrimaryButton
import com.example.ui.components.OutloopSecondaryButton
import com.example.ui.components.OutloopSectionHeader
import com.example.ui.components.OutloopStaggeredEntrance
import com.example.ui.components.OutloopStatusBannerCard
import com.example.ui.components.OutloopSubScreenScaffold
import com.example.ui.components.OutloopSwitch
import com.example.ui.components.OutloopTonalCard
import com.example.ui.components.StatusBannerTone
import com.example.ui.theme.OutloopMetrics
import com.example.ui.theme.OutloopTheme

@Composable
fun DiagnosticsScreen(
    uiState: MainUiState,
    onRefreshHealth: () -> Unit,
    onCheckRulesNow: () -> Unit,
    onSetAutoUpdateRules: (Boolean) -> Unit,
    onSetEventCaptureEnabled: (Boolean) -> Unit,
    onShareEventLog: () -> Unit,
    onClearEventLog: () -> Unit,
    onShowToast: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val colors = OutloopTheme.colors
    val nowMs = uiState.currentTimeMs

    val accessibilityStatusLabel = when {
        uiState.isAccessibilityEnabled && uiState.isServiceConnected -> "Connected"
        uiState.isAccessibilityEnabled -> "Enabled, waiting for binding"
        else -> "Not enabled"
    }

    val lastConnectionLabel = when {
        uiState.lastConnectedAtMs > 0L -> {
            val minsAgo = ((nowMs - uiState.lastConnectedAtMs) / 60_000L).coerceAtLeast(0L)
            if (minsAgo == 0L) "Just now" else "$minsAgo min ago"
        }
        uiState.isServiceConnected -> "Just now"
        else -> "Never"
    }

    val protectionLabel = when (uiState.protectionStatus) {
        ProtectionStatus.OPERATIONAL -> "Active"
        ProtectionStatus.PAUSED -> "Paused"
        ProtectionStatus.QUIET_HOURS -> "Quiet hours"
        ProtectionStatus.NO_APPS_ACTIVE -> "No apps selected"
        ProtectionStatus.SERVICE_DISCONNECTED -> "Disconnected"
        ProtectionStatus.PERMISSION_NEEDED -> "Not configured"
    }

    val lastRulesCheckLabel = if (uiState.preferences.lastRulesetCheckMs > 0L) {
        val minsAgo = ((nowMs - uiState.preferences.lastRulesetCheckMs) / 60_000L).coerceAtLeast(0L)
        when {
            minsAgo == 0L -> "Just now"
            minsAgo < 60L -> "$minsAgo min ago"
            else -> "${minsAgo / 60L} h ago"
        }
    } else {
        "Bundled cache"
    }

    val quietHoursSummary = if (uiState.preferences.quietHoursEnabled) {
        "${AppPreferences.formatMinutesOfDay(uiState.preferences.quietStartMinutes)} \u2013 ${AppPreferences.formatMinutesOfDay(uiState.preferences.quietEndMinutes)}"
    } else {
        "Off"
    }

    OutloopSubScreenScaffold(
        onBack = onBack,
        bottomBar = {
            OutloopPrimaryButton(
                text = "Check again",
                onClick = {
                    onRefreshHealth()
                    val msg = when {
                        uiState.isAccessibilityEnabled && uiState.isServiceConnected ->
                            "Service connected."
                        uiState.isAccessibilityEnabled ->
                            "Accessibility enabled, waiting for Android binding."
                        else ->
                            "Still waiting for Accessibility permission."
                    }
                    onShowToast(msg)
                }
            )
            OutloopSecondaryButton(
                text = if (uiState.isSyncingRules) "Checking feed rules\u2026" else "Check feed rules now",
                onClick = onCheckRulesNow,
                enabled = !uiState.isSyncingRules
            )
        }
    ) {
        OutloopStaggeredEntrance(index = 0) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Diagnostics",
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.ink
                )
                Text(
                    text = "Live accessibility health & selector matching.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.secondaryInk
                )
            }
        }

        OutloopStaggeredEntrance(index = 1) {
            if (uiState.isAccessibilityEnabled && uiState.isServiceConnected) {
                OutloopStatusBannerCard(
                    tone = StatusBannerTone.SUCCESS,
                    icon = Icons.Rounded.CheckCircleOutline,
                    title = "Everything looks connected",
                    body = "Android reports an active Accessibility service connection."
                )
            } else {
                OutloopStatusBannerCard(
                    tone = StatusBannerTone.WARNING,
                    icon = Icons.Outlined.WarningAmber,
                    title = if (uiState.isAccessibilityEnabled) {
                        "Service waiting to bind"
                    } else {
                        "Setup isn\u2019t finished"
                    },
                    body = "Turn on Outloop in Android\u2019s Accessibility settings, then check again."
                )
            }
        }

        OutloopStaggeredEntrance(index = 2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(OutloopMetrics.BentoGap)
            ) {
                OutloopMetricCell(
                    value = protectionLabel,
                    label = "Guard status",
                    containerColor = colors.surfaceCard,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                OutloopMetricCell(
                    value = "${uiState.activeInstalledCount}",
                    label = "Guarded apps",
                    containerColor = colors.surfaceCard,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }

        OutloopStaggeredEntrance(index = 3) {
            OutloopTonalCard(
                cornerRadius = OutloopMetrics.CardCornerRadius,
                contentPadding = PaddingValues(0.dp)
            ) {
                DiagnosticRow("Accessibility service", accessibilityStatusLabel)
                OutloopHairlineDivider(modifier = Modifier.padding(horizontal = 18.dp))
                DiagnosticRow("Last connection", lastConnectionLabel)
                OutloopHairlineDivider(modifier = Modifier.padding(horizontal = 18.dp))
                DiagnosticRow("Guarded apps", uiState.activeInstalledCount.toString())
                OutloopHairlineDivider(modifier = Modifier.padding(horizontal = 18.dp))
                DiagnosticRow("Guard status", protectionLabel)
                OutloopHairlineDivider(modifier = Modifier.padding(horizontal = 18.dp))
                DiagnosticRow(
                    "Strict mode",
                    if (uiState.preferences.strictModeEnabled) "On (10s delay)" else "Off"
                )
                OutloopHairlineDivider(modifier = Modifier.padding(horizontal = 18.dp))
                DiagnosticRow("Foreground monitor", uiState.foregroundAppSummary)
                OutloopHairlineDivider(modifier = Modifier.padding(horizontal = 18.dp))
                DiagnosticRow("Last detection", uiState.lastDetectionSummary)
                OutloopHairlineDivider(modifier = Modifier.padding(horizontal = 18.dp))
                DiagnosticRow("Last intervention", uiState.lastInterventionOutcomeSummary)
                OutloopHairlineDivider(modifier = Modifier.padding(horizontal = 18.dp))
                DiagnosticRow(
                    "Battery optimization",
                    if (uiState.isBatteryOptimizationIgnored) "Unrestricted" else "Restricted"
                )
                OutloopHairlineDivider(modifier = Modifier.padding(horizontal = 18.dp))
                DiagnosticRow("Quiet hours", quietHoursSummary)
                OutloopHairlineDivider(modifier = Modifier.padding(horizontal = 18.dp))
                DiagnosticRow("Ruleset version", uiState.preferences.rulesetVersion)
                OutloopHairlineDivider(modifier = Modifier.padding(horizontal = 18.dp))
                DiagnosticRow("Last rules check", lastRulesCheckLabel)
                OutloopHairlineDivider(modifier = Modifier.padding(horizontal = 18.dp))
                DiagnosticRow("App version", BuildConfig.VERSION_NAME)
            }
        }

        if (!uiState.isBatteryOptimizationIgnored) {
            OutloopStaggeredEntrance(index = 2) {
                OutloopSecondaryButton(
                    text = "Open app battery settings",
                    onClick = { ProtectionHealthHelper.openBatterySettings(context) }
                )
            }
        }

        OutloopStaggeredEntrance(index = 3) {
            Column(verticalArrangement = Arrangement.spacedBy(OutloopMetrics.ItemSpacing)) {
                OutloopSectionHeader(text = "Feed rules & logging")
                OutloopTonalCard(
                    cornerRadius = OutloopMetrics.CardCornerRadius,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = OutloopMetrics.NavRowMinHeight)
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Auto-update feed rules",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink
                        )
                        OutloopSwitch(
                            checked = uiState.preferences.autoUpdateRules,
                            onCheckedChange = onSetAutoUpdateRules,
                            contentDescription = "Auto-update feed rules"
                        )
                    }
                    OutloopHairlineDivider(modifier = Modifier.padding(horizontal = 18.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = OutloopMetrics.NavRowMinHeight)
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Troubleshooting log",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.ink
                            )
                            Text(
                                text = "Keeps technical screen IDs locally.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryInk
                            )
                        }
                        OutloopSwitch(
                            checked = uiState.preferences.eventCaptureEnabled,
                            onCheckedChange = onSetEventCaptureEnabled,
                            contentDescription = "Troubleshooting log"
                        )
                    }
                }
            }
        }

        if (uiState.preferences.eventCaptureEnabled || uiState.eventLogSizeBytes > 0L) {
            OutloopStaggeredEntrance(index = 4) {
                OutloopTonalCard(
                    cornerRadius = OutloopMetrics.CardCornerRadius,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Text(
                        text = "Local event log (${uiState.eventLogSizeBytes} bytes)",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.secondaryInk
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = uiState.eventLogPreview.ifBlank { "No events recorded yet." },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = colors.ink
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutloopSecondaryButton(
                                text = "Share log",
                                onClick = onShareEventLog
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            OutloopSecondaryButton(
                                text = "Clear log",
                                onClick = onClearEventLog
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = "Raw screen content is never shown or transmitted.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondaryInk
        )
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String
) {
    val colors = OutloopTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
            color = colors.ink,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.secondaryInk,
            textAlign = TextAlign.End
        )
    }
}
