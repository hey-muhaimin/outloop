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
import androidx.compose.foundation.layout.padding
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
import com.example.ui.components.OutloopHairlineDivider
import com.example.ui.components.OutloopPrimaryButton
import com.example.ui.components.OutloopRadioOptionCard
import com.example.ui.components.OutloopSectionHeader
import com.example.ui.components.OutloopSimulationOverlay
import com.example.ui.components.OutloopStaggeredEntrance
import com.example.ui.components.OutloopSubScreenScaffold
import com.example.ui.components.OutloopSwitch
import com.example.ui.components.OutloopTonalCard
import com.example.ui.theme.OutloopMetrics
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
    currentTimeMs: Long = System.currentTimeMillis()
) {
    BackHandler(onBack = onBack)
    val colors = OutloopTheme.colors
    var showPreviewOverlay by rememberSaveable { mutableStateOf(false) }

    if (rule == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(OutloopMetrics.ScreenHorizontalPadding),
            contentAlignment = Alignment.Center
        ) {
            OutloopTonalCard {
                Text(
                    text = "App rule not found.",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink
                )
                Spacer(modifier = Modifier.height(OutloopMetrics.SectionSpacing))
                OutloopPrimaryButton(text = "Back", onClick = onBack)
            }
        }
        return
    }

    val isSnoozed = rule.snoozeUntilMs > currentTimeMs

    OutloopSubScreenScaffold(
        onBack = onBack,
        bottomBar = {
            OutloopPrimaryButton(
                text = "Preview pause screen",
                onClick = { showPreviewOverlay = true },
                modifier = Modifier.testTag("detail_preview_button")
            )
        }
    ) {
        OutloopStaggeredEntrance(index = 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutloopAppMonogramBadge(
                    appName = rule.displayName,
                    packageName = rule.packageName,
                    size = OutloopMetrics.AppDetailBadgeSize,
                    cornerRadius = OutloopMetrics.AppDetailBadgeCornerRadius
                )
                Column {
                    Text(
                        text = rule.displayName,
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.ink
                    )
                    Text(
                        text = "${rule.feedLabel} \u00B7 ${if (rule.enabled) "Protected" else "Off"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryInk
                    )
                }
            }
        }

        OutloopStaggeredEntrance(index = 1) {
            OutloopTonalCard(
                cornerRadius = OutloopMetrics.CardCornerRadius,
                contentPadding = PaddingValues(0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = OutloopMetrics.NavRowMinHeight)
                        .padding(horizontal = OutloopMetrics.ListRowPaddingHorizontal, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Protect ${rule.displayName}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                        color = colors.ink
                    )
                    OutloopSwitch(
                        checked = rule.enabled,
                        onCheckedChange = onToggleEnabled,
                        contentDescription = "Protect ${rule.displayName}",
                        modifier = Modifier.testTag("detail_protect_switch")
                    )
                }
                OutloopHairlineDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = OutloopMetrics.NavRowMinHeight)
                        .padding(horizontal = OutloopMetrics.ListRowPaddingHorizontal, vertical = 10.dp),
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
        }

        OutloopStaggeredEntrance(index = 2) {
            Column(verticalArrangement = Arrangement.spacedBy(OutloopMetrics.ItemSpacing)) {
                OutloopSectionHeader(text = "When a feed opens")
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
        }

        OutloopStaggeredEntrance(index = 3) {
            Column(verticalArrangement = Arrangement.spacedBy(OutloopMetrics.ItemSpacing)) {
                OutloopSectionHeader(text = "Just for now")
                OutloopTonalCard(
                    cornerRadius = OutloopMetrics.CardCornerRadius,
                    contentPadding = PaddingValues(0.dp)
                ) {
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
                            title = "Resume protection",
                            subtitle = "Ends the active pause for ${rule.displayName}.",
                            onClick = {
                                onResumeSnooze()
                                onShowToast("${rule.displayName} protection resumed.")
                            }
                        )
                    } else {
                        DetailActionRow(
                            title = "Snooze for 15 minutes",
                            subtitle = "Pauses protection for ${rule.displayName} only.",
                            onClick = {
                                onSnoozeMinutes(15)
                                onShowToast("Snoozed ${rule.displayName} for 15 minutes.")
                            }
                        )
                    }
                    OutloopHairlineDivider()
                    DetailActionRow(
                        title = if (rule.enabled) "Turn off for ${rule.displayName}" else "Turn on for ${rule.displayName}",
                        subtitle = if (rule.enabled) {
                            "Stops protecting it until you turn it back on."
                        } else {
                            "Resumes protecting ${rule.feedLabel}."
                        },
                        titleColor = if (rule.enabled) colors.destructive else colors.primaryTint,
                        onClick = {
                            val nextEnabled = !rule.enabled
                            onToggleEnabled(nextEnabled)
                            if (!nextEnabled) {
                                onShowToast("${rule.displayName} is off. You can turn it back on any time.")
                                onBack()
                            } else {
                                onShowToast("${rule.displayName} protection is on.")
                            }
                        }
                    )
                }
            }
        }
    }

    if (showPreviewOverlay) {
        OutloopSimulationOverlay(
            appName = rule.displayName,
            feedLabel = rule.feedLabel,
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
            .heightIn(min = OutloopMetrics.RowMinHeight)
            .clickable(onClick = onClick)
            .padding(
                horizontal = OutloopMetrics.ListRowPaddingHorizontal,
                vertical = OutloopMetrics.ListRowPaddingVertical
            )
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
