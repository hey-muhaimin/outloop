package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.TargetAppRuleEntity
import com.example.data.model.InterventionMode
import com.example.ui.components.OutloopAppMonogramBadge
import com.example.ui.components.OutloopBackButton
import com.example.ui.components.OutloopBentoCard
import com.example.ui.components.OutloopHairlineDivider
import com.example.ui.components.OutloopPrimaryButton
import com.example.ui.components.OutloopRadioOptionCard
import com.example.ui.components.OutloopSectionHeader
import com.example.ui.components.OutloopSimulationOverlay
import com.example.ui.components.OutloopSwitch
import com.example.ui.theme.OutloopTheme

@Composable
fun AppDetailScreen(
    rule: TargetAppRuleEntity?,
    onBack: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectMode: (InterventionMode) -> Unit,
    onChangeCooldown: (Int) -> Unit,
    onAllowOnce: () -> Unit,
    onSnoozeMinutes: (Int) -> Unit,
    onResumeSnooze: () -> Unit,
    onShowToast: (String) -> Unit,
    loopsToday: Int = 14,
    timeKeptText: String = "0m",
    streakDays: Int = 0,
    strictModeEnabled: Boolean = false
) {
    BackHandler(onBack = onBack)
    val colors = OutloopTheme.colors
    var showPreviewOverlay by rememberSaveable { mutableStateOf(false) }

    if (rule == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            OutloopBentoCard {
                Text(
                    text = "App rule not found.",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutloopPrimaryButton(text = "Back", onClick = onBack)
            }
        }
        return
    }

    val isSnoozed = rule.snoozeUntilMs > System.currentTimeMillis()

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutloopAppMonogramBadge(
                        appName = rule.displayName,
                        packageName = rule.packageName,
                        size = 56.dp,
                        cornerRadius = 17.dp,
                        preferMonogram = true
                    )
                    Column {
                        Text(
                            text = rule.displayName,
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.ink
                        )
                        Text(
                            text = "${rule.feedLabel} \u00B7 ${if (rule.enabled) "Guarded" else "Off"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryInk
                        )
                    }
                }

                OutloopBentoCard(cornerRadius = 24.dp, contentPadding = PaddingValues(0.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Guard ${rule.displayName}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                            color = colors.ink
                        )
                        OutloopSwitch(
                            checked = rule.enabled,
                            onCheckedChange = onToggleEnabled,
                            contentDescription = "Guard ${rule.displayName}",
                            modifier = Modifier.testTag("detail_protect_switch")
                        )
                    }
                    OutloopHairlineDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Feed Outloop watches",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                            color = colors.ink
                        )
                        Text(
                            text = rule.feedLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.secondaryInk
                        )
                    }
                }

                OutloopSectionHeader(text = "When a feed opens")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        InterventionMode.OVERLAY,
                        InterventionMode.BACK,
                        InterventionMode.HOME_TAB
                    ).forEach { mode ->
                        OutloopRadioOptionCard(
                            title = mode.shortLabel,
                            subtitle = mode.explanation,
                            selected = rule.interventionMode == mode,
                            onClick = { onSelectMode(mode) },
                            modifier = Modifier.testTag("detail_mode_${mode.name.lowercase()}")
                        )
                    }
                }

                OutloopSectionHeader(text = "Just for now")
                OutloopBentoCard(cornerRadius = 24.dp, contentPadding = PaddingValues(0.dp)) {
                    DetailActionRow(
                        title = "Allow once",
                        subtitle = "Lets the next feed open without a pause.",
                        onClick = {
                            onAllowOnce()
                            onShowToast("Allowed once. The next ${rule.feedLabel} opens normally.")
                        }
                    )
                    OutloopHairlineDivider()
                    if (isSnoozed) {
                        DetailActionRow(
                            title = "Resume guard",
                            subtitle = "Ends the active pause for ${rule.displayName}.",
                            onClick = {
                                onResumeSnooze()
                                onShowToast("${rule.displayName} guard resumed.")
                            }
                        )
                    } else {
                        DetailActionRow(
                            title = "Pause for 15 minutes",
                            subtitle = "Pauses Guard for ${rule.displayName} only.",
                            onClick = {
                                onSnoozeMinutes(15)
                                onShowToast("Paused ${rule.displayName} for 15 minutes.")
                            }
                        )
                    }
                    OutloopHairlineDivider()
                    DetailActionRow(
                        title = if (rule.enabled) "Turn off for ${rule.displayName}" else "Turn on for ${rule.displayName}",
                        subtitle = if (rule.enabled) {
                            "Stops guarding it until you turn it back on."
                        } else {
                            "Resumes guarding ${rule.feedLabel}."
                        },
                        titleColor = if (rule.enabled) colors.destructive else colors.primaryTint,
                        onClick = {
                            val nextEnabled = !rule.enabled
                            onToggleEnabled(nextEnabled)
                            if (!nextEnabled) {
                                onShowToast("${rule.displayName} is off. You can turn it back on any time.")
                                onBack()
                            } else {
                                onShowToast("${rule.displayName} guard is on.")
                            }
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                OutloopPrimaryButton(
                    text = "Preview loop broken screen",
                    onClick = { showPreviewOverlay = true },
                    modifier = Modifier.testTag("detail_preview_button")
                )
            }
        }
    }

    if (showPreviewOverlay) {
        OutloopSimulationOverlay(
            appName = rule.displayName,
            feedLabel = rule.feedLabel,
            loopsToday = loopsToday,
            timeKeptText = timeKeptText,
            streakDays = streakDays,
            strictModeEnabled = strictModeEnabled,
            onActionFeedback = onShowToast,
            onDismiss = { showPreviewOverlay = false }
        )
    }
}

@Composable
private fun DetailActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    titleColor: Color? = null
) {
    val colors = OutloopTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = titleColor ?: colors.ink
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondaryInk
        )
    }
}
