package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.ui.components.OutloopBackButton
import com.example.ui.components.OutloopHairlineDivider
import com.example.ui.components.OutloopPrimaryButton
import com.example.ui.components.OutloopSecondaryButton
import com.example.ui.components.OutloopSectionHeader
import com.example.ui.components.OutloopStatusBannerCard
import com.example.ui.components.OutloopSwitch
import com.example.ui.components.OutloopTonalCard
import com.example.ui.components.StatusBannerTone
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

    val accessibilityStatusLabel = when {
        uiState.isAccessibilityEnabled && uiState.isServiceConnected -> "Connected"
        uiState.isAccessibilityEnabled -> "Enabled, waiting for binding"
        else -> "Not enabled"
    }

    val lastConnectionLabel = when {
        uiState.lastConnectedAtMs > 0L -> {
            val minsAgo = ((System.currentTimeMillis() - uiState.lastConnectedAtMs) / 60_000L).coerceAtLeast(0L)
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
        val minsAgo = ((System.currentTimeMillis() - uiState.preferences.lastRulesetCheckMs) / 60_000L).coerceAtLeast(0L)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 20.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutloopBackButton(onClick = onBack)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Diagnostics",
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.ink
                )

                OutloopTonalCard(cornerRadius = 24.dp, contentPadding = PaddingValues(0.dp)) {
                    DiagnosticRow("Accessibility service", accessibilityStatusLabel)
                    OutloopHairlineDivider()
                    DiagnosticRow("Last connection", lastConnectionLabel)
                    OutloopHairlineDivider()
                    DiagnosticRow("Protected apps", uiState.activeInstalledCount.toString())
                    OutloopHairlineDivider()
                    DiagnosticRow("Protection", protectionLabel)
                    OutloopHairlineDivider()
                    DiagnosticRow("Last detection", uiState.lastDetectionSummary)
                    OutloopHairlineDivider()
                    DiagnosticRow("Last intervention", uiState.lastInterventionOutcomeSummary)
                    OutloopHairlineDivider()
                    DiagnosticRow(
                        "Battery optimization",
                        if (uiState.isBatteryOptimizationIgnored) "Unrestricted" else "Restricted"
                    )
                    OutloopHairlineDivider()
                    DiagnosticRow("Quiet hours", quietHoursSummary)
                    OutloopHairlineDivider()
                    DiagnosticRow("Ruleset version", uiState.preferences.rulesetVersion)
                    OutloopHairlineDivider()
                    DiagnosticRow("Last rules check", lastRulesCheckLabel)
                    OutloopHairlineDivider()
                    DiagnosticRow("App version", BuildConfig.VERSION_NAME)
                }

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

                if (!uiState.isBatteryOptimizationIgnored) {
                    OutloopSecondaryButton(
                        text = "Allow unrestricted background activity",
                        onClick = {
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
                        }
                    )
                }

                OutloopSectionHeader(text = "Feed rules & logging")
                OutloopTonalCard(cornerRadius = 24.dp, contentPadding = PaddingValues(0.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Auto-update feed rules",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.ink
                        )
                        OutloopSwitch(
                            checked = uiState.preferences.autoUpdateRules,
                            onCheckedChange = onSetAutoUpdateRules,
                            contentDescription = "Auto-update feed rules"
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Troubleshooting log",
                                style = MaterialTheme.typography.titleSmall,
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

                if (uiState.preferences.eventCaptureEnabled || uiState.eventLogSizeBytes > 0L) {
                    OutloopTonalCard(cornerRadius = 22.dp, contentPadding = PaddingValues(16.dp)) {
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
                        Spacer(modifier = Modifier.height(10.dp))
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

                Text(
                    text = "Raw screen content is never shown or transmitted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryInk
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
        }
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
            .heightIn(min = 50.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
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
