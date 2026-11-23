package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.TargetAppRuleEntity
import com.example.data.model.InterventionMode
import com.example.data.repository.InterventionRepository
import com.example.ui.components.OutloopAppMonogramBadge
import com.example.ui.components.OutloopHairlineDivider
import com.example.ui.components.OutloopInsetWell
import com.example.ui.components.OutloopModalSheet
import com.example.ui.components.OutloopPrimaryButton
import com.example.ui.components.OutloopRadioOptionCard
import com.example.ui.components.OutloopSecondaryButton
import com.example.ui.components.OutloopSectionHeader
import com.example.ui.components.OutloopSimulationOverlay
import com.example.ui.components.OutloopStaggeredEntrance
import com.example.ui.components.OutloopSubScreenScaffold
import com.example.ui.components.OutloopTonalCard
import com.example.ui.components.rememberOutloopHaptic
import com.example.ui.theme.OutloopMetrics
import com.example.ui.theme.OutloopTheme

@OptIn(ExperimentalLayoutApi::class)
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
    currentTimeMs: Long = System.currentTimeMillis(),
    todayUsageSeconds: Long = 0L,
    reclaimedTodayText: String = "0 min",
    streakDays: Int = 0,
    onChangeDailyLimitMinutes: (Int) -> Unit = {}
) {
    BackHandler(onBack = onBack)
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    var showPreviewOverlay by rememberSaveable { mutableStateOf(false) }
    var showCustomLimitSheet by rememberSaveable { mutableStateOf(false) }

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
    val usedCompact = InterventionRepository.formatDurationCompact(todayUsageSeconds)
    val hasLimit = rule.hasDailyLimit()
    val limitSeconds = rule.dailyLimitSeconds.coerceAtLeast(1L)
    val rawProgress = if (hasLimit) {
        (todayUsageSeconds.toFloat() / limitSeconds.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(durationMillis = 300),
        label = "dailyLimitProgress"
    )
    val isLimitReached = hasLimit && todayUsageSeconds >= rule.dailyLimitSeconds
    val isNearLimit = hasLimit && rawProgress >= 0.85f

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
                        color = colors.onPrimaryTint
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${rule.feedLabel} \u00B7 ${if (rule.enabled) "Guarded" else "Off"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onPrimaryTint.copy(alpha = 0.78f)
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
                            .background(if (rule.enabled) colors.okBg else colors.heroChipBg)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (rule.enabled) "On" else "Off",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (rule.enabled) colors.okText else colors.onPrimaryTint
                        )
                    }
                }
            }
        }

        // 2-up Feed & Utility bento row
        OutloopStaggeredEntrance(index = 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(OutloopMetrics.BentoGap)
            ) {
                OutloopTonalCard(
                    cornerRadius = OutloopMetrics.CardCornerRadius,
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier
                        .weight(0.44f)
                        .fillMaxHeight()
                ) {
                    Text(
                        text = "Watched feed",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.secondaryInk
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = rule.feedLabel,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.ink
                    )
                }
                OutloopTonalCard(
                    cornerRadius = OutloopMetrics.CardCornerRadius,
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier
                        .weight(0.56f)
                        .fillMaxHeight()
                ) {
                    Text(
                        text = "Stays open",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.secondaryInk
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = usefulSurfaceHint(rule),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = colors.ink
                    )
                }
            }
        }

        // Daily time limit & tracked foreground usage card
        OutloopStaggeredEntrance(index = 2) {
            OutloopTonalCard(
                cornerRadius = OutloopMetrics.CardCornerRadius,
                contentPadding = PaddingValues(18.dp),
                modifier = Modifier.testTag("detail_daily_limit_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Daily time limit",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink
                        )
                        Text(
                            text = if (hasLimit) {
                                "$usedCompact of ${rule.dailyLimitMinutes}m used today"
                            } else {
                                "$usedCompact in ${rule.displayName} today \u00B7 No daily limit"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFeatureSettings = "tnum"
                            ),
                            color = colors.secondaryInk
                        )
                    }
                    val (statusBadgeBg, statusBadgeFg) = when {
                        isLimitReached -> colors.warnBg to colors.warnText
                        hasLimit -> colors.okBg to colors.okText
                        else -> colors.surfaceSubtle to colors.secondaryInk
                    }
                    val statusBadgeText = when {
                        isLimitReached -> "Limit reached"
                        hasLimit -> "${rule.dailyLimitMinutes}m / day"
                        else -> "No limit"
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(statusBadgeBg)
                            .border(1.dp, colors.hairlineBorder, CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = statusBadgeText,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = statusBadgeFg
                        )
                    }
                }

                if (hasLimit) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutloopInsetWell(
                        containerColor = colors.canvas,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceSubtle)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedProgress.coerceAtLeast(if (todayUsageSeconds > 0L) 0.04f else 0f))
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(if (isNearLimit) colors.warnText else colors.heroDark)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val presets = listOf(0, 15, 30, 45, 60)
                val isCustomValue = hasLimit && rule.dailyLimitMinutes !in presets

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { minutes ->
                        val selected = rule.dailyLimitMinutes == minutes
                        val label = if (minutes == 0) "No limit" else "${minutes}m"
                        DailyLimitPresetChip(
                            label = label,
                            selected = selected,
                            onClick = {
                                haptic()
                                onChangeDailyLimitMinutes(minutes)
                                val msg = if (minutes == 0) {
                                    "Removed daily time limit for ${rule.displayName}."
                                } else {
                                    "Daily limit for ${rule.displayName} set to ${minutes}m."
                                }
                                onShowToast(msg)
                            },
                            modifier = Modifier.testTag("detail_limit_chip_$minutes")
                        )
                    }
                    DailyLimitPresetChip(
                        label = if (isCustomValue) "Custom (${rule.dailyLimitMinutes}m)" else "Custom",
                        selected = isCustomValue,
                        onClick = {
                            haptic()
                            showCustomLimitSheet = true
                        },
                        modifier = Modifier.testTag("detail_limit_custom_button")
                    )
                }
            }
        }

        // Intervention mode selector
        OutloopStaggeredEntrance(index = 3) {
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
        OutloopStaggeredEntrance(index = 4) {
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

    if (showCustomLimitSheet) {
        var draftMinutes by remember(rule.packageName, rule.dailyLimitMinutes) {
            mutableIntStateOf(if (rule.dailyLimitMinutes > 0) rule.dailyLimitMinutes else 25)
        }
        OutloopModalSheet(
            title = "Daily limit for ${rule.displayName}",
            onDismiss = { showCustomLimitSheet = false }
        ) {
            Text(
                text = "When ${rule.displayName} reaches this daily time cap, Outloop dims the feed and shows Pause Guard.",
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
                    Button(
                        onClick = {
                            haptic()
                            draftMinutes = (draftMinutes - 5).coerceAtLeast(5)
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.surfaceSubtle,
                            contentColor = colors.ink
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .widthIn(min = 64.dp)
                            .testTag("custom_limit_minus_button")
                    ) {
                        Text("-5m", style = MaterialTheme.typography.titleSmall)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${draftMinutes} min",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFeatureSettings = "tnum"
                            ),
                            color = colors.ink
                        )
                        Text(
                            text = "per day",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondaryInk
                        )
                    }

                    Button(
                        onClick = {
                            haptic()
                            draftMinutes = (draftMinutes + 5).coerceAtMost(180)
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.surfaceSubtle,
                            contentColor = colors.ink
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .widthIn(min = 64.dp)
                            .testTag("custom_limit_plus_button")
                    ) {
                        Text("+5m", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            OutloopPrimaryButton(
                text = "Save ${draftMinutes} min limit",
                onClick = {
                    onChangeDailyLimitMinutes(draftMinutes)
                    onShowToast("Daily limit for ${rule.displayName} set to ${draftMinutes}m.")
                    showCustomLimitSheet = false
                },
                modifier = Modifier.testTag("custom_limit_save_button")
            )
            OutloopSecondaryButton(
                text = "Remove daily limit",
                onClick = {
                    onChangeDailyLimitMinutes(0)
                    onShowToast("Removed daily time limit for ${rule.displayName}.")
                    showCustomLimitSheet = false
                },
                modifier = Modifier.testTag("custom_limit_clear_button")
            )
        }
    }

    if (showPreviewOverlay) {
        val (primaryLabel, secondaryLabel) = previewActionLabels(rule)
        OutloopSimulationOverlay(
            appName = rule.displayName,
            feedLabel = rule.feedLabel,
            primaryActionLabel = primaryLabel,
            secondaryActionLabel = secondaryLabel,
            timeKeptText = reclaimedTodayText,
            streakDays = streakDays,
            dailyUsedSeconds = todayUsageSeconds,
            dailyLimitMinutes = rule.dailyLimitMinutes,
            onActionFeedback = onShowToast,
            onDismiss = { showPreviewOverlay = false }
        )
    }
}

@Composable
private fun DailyLimitPresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OutloopTheme.colors
    Box(
        modifier = modifier
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
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
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
