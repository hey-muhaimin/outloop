package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.example.ui.components.OutloopTonalCard
import com.example.ui.components.rememberOutloopHaptic
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
    val haptic = rememberOutloopHaptic()
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
                text = "Preview overlay",
                onClick = { showPreviewOverlay = true },
                modifier = Modifier.testTag("detail_preview_button")
            )
        }
    ) {
        // Dark Forest Hero Bento Header
        OutloopStaggeredEntrance(index = 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(OutloopMetrics.HeroCornerRadius))
                    .background(colors.heroDark)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.heroChipBg)
                        .padding(4.dp)
                ) {
                    OutloopAppMonogramBadge(
                        appName = rule.displayName,
                        packageName = rule.packageName,
                        size = 48.dp,
                        cornerRadius = 12.dp,
                        preferDarkMonogram = true
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.displayName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color(0xFFFAF8F2)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${rule.feedLabel} \u00B7 ${if (rule.enabled) "Guarded" else "Off"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFAF8F2).copy(alpha = 0.78f)
                    )
                }
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics(mergeDescendants = true) {
                            this.contentDescription = "Guard ${rule.displayName}"
                            this.stateDescription = if (rule.enabled) "On" else "Off"
                        }
                        .clickable(role = Role.Switch) {
                            haptic()
                            onToggleEnabled(!rule.enabled)
                        }
                        .testTag("detail_protect_switch"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (rule.enabled) colors.mintTile else colors.heroChipBg)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (rule.enabled) "On" else "Off",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (rule.enabled) colors.ink else Color(0xFFFAF8F2)
                        )
                    }
                }
            }
        }

        // Feed summary bento card
        OutloopStaggeredEntrance(index = 1) {
            OutloopTonalCard(
                cornerRadius = OutloopMetrics.CardCornerRadius,
                contentPadding = PaddingValues(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Feed Outloop watches",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink
                        )
                        Text(
                            text = usefulSurfaceHint(rule),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryInk
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.mintTile)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = rule.feedLabel,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.ink
                        )
                    }
                }
            }
        }

        // Intervention mode selector
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

        // Quick temporary actions
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
                    OutloopHairlineDivider(modifier = Modifier.padding(horizontal = 18.dp))
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
                    OutloopHairlineDivider(modifier = Modifier.padding(horizontal = 18.dp))
                    DetailActionRow(
                        title = if (rule.enabled) "Turn off for ${rule.displayName}" else "Turn on for ${rule.displayName}",
                        subtitle = if (rule.enabled) {
                            "Stops guarding it until you turn it back on."
                        } else {
                            "Resumes guarding ${rule.feedLabel}."
                        },
                        titleColor = if (rule.enabled) colors.destructive else colors.textAction,
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
        }
    }

    if (showPreviewOverlay) {
        val (primaryLabel, secondaryLabel) = previewActionLabels(rule)
        OutloopSimulationOverlay(
            appName = rule.displayName,
            feedLabel = rule.feedLabel,
            primaryActionLabel = primaryLabel,
            secondaryActionLabel = secondaryLabel,
            onActionFeedback = onShowToast,
            onDismiss = { showPreviewOverlay = false }
        )
    }
}

private fun usefulSurfaceHint(rule: TargetAppRuleEntity): String = when (rule.packageName) {
    "com.instagram.android" -> "DMs, Search, and Profile stay open"
    "com.google.android.youtube" -> "Search, Subscriptions, and Library stay open"
    "com.facebook.katana" -> "Messenger, Groups, and Profile stay open"
    "com.zhiliaoapp.musically" -> "Inbox and Profile stay open"
    "com.twitter.android" -> "Messages and Search stay open"
    else -> "${rule.homeTabLabel} and other tabs stay open"
}

private fun previewActionLabels(rule: TargetAppRuleEntity): Pair<String, String> = when (rule.packageName) {
    "com.instagram.android" -> "Open DMs" to "Go to Search"
    "com.google.android.youtube" -> "Open Search" to "Go to Subscriptions"
    "com.facebook.katana" -> "Open Messenger" to "Go to Home"
    "com.zhiliaoapp.musically" -> "Open Inbox" to "Leave TikTok"
    "com.twitter.android" -> "Open Messages" to "Go to Search"
    else -> "Go to ${rule.homeTabLabel}" to "Allow for 5 min"
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
                horizontal = 18.dp,
                vertical = 12.dp
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
