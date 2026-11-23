package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.repository.InterventionRepository
import com.example.ui.DayBarStat
import com.example.ui.theme.OutloopMetrics
import com.example.ui.theme.OutloopTheme

data class ChartColumnData(
    val shortLabel: String,
    val detailLabel: String,
    val count: Int,
    val fraction: Float
)

/**
 * Monochrome Editorial Home summary bento card:
 * Top row: circular Graphite arc around today's count + reclaimed-time estimate.
 * Middle 2-up nested bento wells: daily intention/target + streak.
 * Bottom inset well: 7-day M-S rhythm strip.
 */
@Composable
fun OutloopHomeProgressCard(
    count: Int,
    reclaimedText: String,
    dayBars: List<DayBarStat>,
    dailyBudgetCuts: Int = 0,
    streakDays: Int = 0,
    onReclaimedInfoClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = OutloopTheme.colors
    val animatedCount by animateIntAsState(
        targetValue = count,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "homeProgressCount"
    )
    val targetFraction = when {
        count <= 0 -> 0f
        dailyBudgetCuts > 0 -> (count.toFloat() / dailyBudgetCuts.toFloat()).coerceIn(0.08f, 1f)
        else -> (count.toFloat() / 5.5f).coerceIn(0.18f, 0.88f)
    }
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 450),
        label = "homeProgressSweep"
    )

    OutloopTonalCard(
        cornerRadius = OutloopMetrics.CardCornerRadius,
        contentPadding = PaddingValues(18.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(OutloopMetrics.HeroRingSize)
                    .semantics(mergeDescendants = true) {
                        contentDescription = if (count == 1) {
                            "1 feed interrupted today"
                        } else {
                            "$count feeds interrupted today"
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = 8.5.dp.toPx()
                    val inset = strokePx / 2f + 2.dp.toPx()
                    val diameter = (size.minDimension - inset * 2f).coerceAtLeast(1f)
                    drawArc(
                        color = colors.surfaceSubtle,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(diameter, diameter),
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                    if (animatedFraction > 0.01f) {
                        drawArc(
                            color = colors.heroDark,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedFraction,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(diameter, diameter),
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    }
                }
                Text(
                    text = animatedCount.toString(),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 28.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = colors.ink
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (count == 1) {
                        "feed interrupted\ntoday"
                    } else {
                        "feeds interrupted\ntoday"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 21.sp,
                        lineHeight = 25.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = colors.ink
                )
                Text(
                    text = "~$reclaimedText est. time reclaimed",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFeatureSettings = "tnum"
                    ),
                    color = colors.secondaryInk,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onReclaimedInfoClick)
                        .padding(vertical = 2.dp)
                        .testTag("home_reclaimed_estimate_text")
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2-up nested bento metric row: Daily target + Streak
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(OutloopMetrics.BentoGap)
        ) {
            val targetMet = dailyBudgetCuts > 0 && count >= dailyBudgetCuts
            val targetValue = if (dailyBudgetCuts > 0) "$count / $dailyBudgetCuts" else "Open"
            val targetLabel = when {
                dailyBudgetCuts <= 0 -> "No daily target"
                targetMet -> "Daily target reached ($dailyBudgetCuts)"
                else -> "$count of $dailyBudgetCuts daily target"
            }
            OutloopMetricCell(
                value = targetValue,
                label = targetLabel,
                containerColor = if (targetMet) colors.okBg else colors.canvas,
                valueColor = if (targetMet) colors.okText else colors.ink,
                labelColor = if (targetMet) colors.okText else colors.secondaryInk,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            val streakValue = if (streakDays == 1) "1 day" else "$streakDays days"
            OutloopMetricCell(
                value = streakValue,
                label = "current streak",
                containerColor = colors.canvas,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        if (dayBars.isNotEmpty()) {
            Spacer(modifier = Modifier.height(OutloopMetrics.BentoGap))
            OutloopInsetWell(
                containerColor = colors.canvas,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
            ) {
                OutloopDailyRhythmStrip(dayBars = dayBars)
            }
        }
    }
}

/**
 * Monochrome Editorial Insights bar chart card with a nested tonal plot well
 * and capsule bar columns (Pewter inactive, Deep Graphite active).
 */
@Composable
fun OutloopInsightsLightChartCard(
    title: String,
    columns: List<ChartColumnData>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    val safeSelected = selectedIndex.coerceIn(0, (columns.size - 1).coerceAtLeast(0))
    val selectedColumn = columns.getOrNull(safeSelected)

    OutloopTonalCard(
        cornerRadius = OutloopMetrics.CardCornerRadius,
        contentPadding = PaddingValues(18.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = colors.ink
            )
            if (selectedColumn != null && columns.any { it.count > 0 }) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.surfaceSubtle)
                        .border(1.dp, colors.hairlineBorder, CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${selectedColumn.detailLabel}: ${selectedColumn.count}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        color = colors.ink
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutloopInsetWell(
            containerColor = colors.canvas,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                columns.forEachIndexed { idx, col ->
                    val isSelected = idx == safeSelected
                    val targetHeight = if (col.count == 0) {
                        14.dp
                    } else {
                        (26f + 72f * col.fraction.coerceIn(0f, 1f)).dp
                    }
                    val barHeight by animateDpAsState(
                        targetValue = targetHeight,
                        animationSpec = spring(
                            dampingRatio = 0.80f,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "lightChartBar_$idx"
                    )
                    val barColor by animateColorAsState(
                        targetValue = if (isSelected) colors.heroDark else Color(0xFFC5C6C8),
                        animationSpec = tween(180),
                        label = "lightChartBarColor_$idx"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptic()
                                onSelectIndex(idx)
                            }
                            .semantics(mergeDescendants = true) {
                                contentDescription = "${col.detailLabel}: ${col.count} feeds interrupted"
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(min = 20.dp, max = 30.dp)
                                .fillMaxWidth(0.72f)
                                .height(barHeight)
                                .clip(CircleShape)
                                .background(barColor)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = col.shortLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) colors.ink else colors.secondaryInk,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OutloopDailyRhythmStrip(
    dayBars: List<DayBarStat>,
    modifier: Modifier = Modifier
) {
    val colors = OutloopTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        dayBars.forEach { day ->
            val accessibilityLabel = buildString {
                append(day.dayName)
                if (day.isToday) append(", today")
                append(": ${day.count} feeds interrupted")
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = accessibilityLabel
                }
            ) {
                Text(
                    text = day.dayLetter,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (day.isToday) colors.ink else colors.secondaryInk
                )
                Box(
                    modifier = Modifier
                        .size(OutloopMetrics.RhythmCircleSize)
                        .clip(CircleShape)
                        .background(if (day.isToday) colors.heroDark else colors.surfaceElevated)
                        .then(
                            if (day.isToday) {
                                Modifier
                            } else {
                                Modifier.border(1.dp, colors.hairlineBorder, CircleShape)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day.count.toString(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = if (day.isToday) colors.onPrimaryTint else colors.ink
                    )
                }
            }
        }
    }
}

/**
 * Interactive simulation pause modal matching the AccessibilityService translucent dim scrim
 * in the unified Monochrome Editorial palette.
 */
@Composable
fun OutloopSimulationOverlay(
    appName: String,
    feedLabel: String,
    onActionFeedback: (String) -> Unit,
    onDismiss: () -> Unit,
    primaryActionLabel: String = "Open DMs",
    secondaryActionLabel: String = "Go to Search",
    dailyUsedSeconds: Long = 0L,
    dailyLimitMinutes: Int = 0,
    timeKeptText: String = "0 min",
    streakDays: Int = 0
) {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    val cardEnter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        cardEnter.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    val scrimAlpha = (0.80f * cardEnter.value).coerceIn(0.05f, 0.80f)
    val usedText = InterventionRepository.formatDurationCompact(dailyUsedSeconds)
    val limitReached = dailyLimitMinutes > 0 && dailyUsedSeconds >= dailyLimitMinutes * 60L
    val streakValueText = if (streakDays == 1) "1 day" else "$streakDays days"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF111214).copy(alpha = scrimAlpha))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .graphicsLayer {
                        val v = cardEnter.value
                        alpha = v.coerceIn(0.1f, 1f)
                        scaleX = 0.94f + 0.06f * v
                        scaleY = 0.94f + 0.06f * v
                    }
                    .clip(RoundedCornerShape(OutloopMetrics.SheetCornerRadius))
                    .background(colors.surfaceCard)
                    .border(1.dp, colors.hairlineBorder, RoundedCornerShape(OutloopMetrics.SheetCornerRadius))
                    .clickable(enabled = false, onClick = {})
                    .padding(OutloopMetrics.CardPadding)
                    .testTag("simulation_pause_dialog")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(OutloopMetrics.HeroCornerRadius))
                        .background(colors.heroDark)
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (limitReached) colors.warnBg else colors.heroChipBg)
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = if (limitReached) "$appName \u00B7 Daily limit" else "$appName \u00B7 $feedLabel",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (limitReached) colors.warnText else colors.onPrimaryTint
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (limitReached) "Daily limit\nreached" else "Loop\nbroken",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 27.sp,
                                lineHeight = 31.sp
                            ),
                            color = colors.onPrimaryTint
                        )
                    }
                    OutloopMark(
                        size = 74.dp,
                        color = if (limitReached) colors.warnBg else colors.sageAccent,
                        strokeWidth = 9.5.dp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = if (limitReached) {
                        "You reached your ${dailyLimitMinutes}m daily budget for $appName ($usedText used). Return to Home or exit $appName (Preview mode)."
                    } else {
                        "$feedLabel is dimmed and guarded. Return to Home or exit $appName (Preview mode)."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryInk
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(OutloopMetrics.BentoGap)
                ) {
                    if (dailyLimitMinutes > 0) {
                        OutloopBentoStatTile(
                            value = "$usedText / ${dailyLimitMinutes}m",
                            label = "used today",
                            containerColor = if (limitReached) colors.warnBg else colors.canvas,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        OutloopBentoStatTile(
                            value = timeKeptText,
                            label = "time kept",
                            containerColor = colors.surfaceSubtle,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    } else {
                        OutloopBentoStatTile(
                            value = timeKeptText,
                            label = "time kept",
                            containerColor = colors.canvas,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        OutloopBentoStatTile(
                            value = streakValueText,
                            label = "streak",
                            containerColor = colors.surfaceSubtle,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Row 1: Nav-dock-style inline Home | Exit bar matching FloatingPillNavBar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(OutloopMetrics.DockHeight)
                        .shadow(
                            elevation = 8.dp,
                            shape = CircleShape,
                            ambientColor = colors.dockShadow,
                            spotColor = colors.dockShadow
                        )
                        .clip(CircleShape)
                        .background(colors.surfaceCard)
                        .border(1.dp, colors.hairlineBorder, CircleShape)
                        .padding(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(colors.surfaceSubtle)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val homeInteraction = remember { MutableInteractionSource() }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .outloopPressScale(homeInteraction, pressedScale = 0.96f)
                                .shadow(
                                    elevation = 2.dp,
                                    shape = CircleShape,
                                    ambientColor = colors.dockShadow,
                                    spotColor = colors.dockShadow
                                )
                                .clip(CircleShape)
                                .background(colors.surfaceElevated)
                                .border(0.75.dp, colors.hairlineBorder, CircleShape)
                                .clickable(
                                    role = Role.Button,
                                    interactionSource = homeInteraction,
                                    indication = LocalIndication.current
                                ) {
                                    haptic()
                                    onActionFeedback("Preview: returned to $appName Home ($primaryActionLabel).")
                                    onDismiss()
                                }
                                .semantics {
                                    contentDescription = "Return to Home in $appName"
                                }
                                .testTag("sim_home_button"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Home,
                                contentDescription = null,
                                tint = colors.ink,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = "Home",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.15.sp
                                ),
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        val exitInteraction = remember { MutableInteractionSource() }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .outloopPressScale(exitInteraction, pressedScale = 0.96f)
                                .clip(CircleShape)
                                .clickable(
                                    role = Role.Button,
                                    interactionSource = exitInteraction,
                                    indication = LocalIndication.current
                                ) {
                                    haptic()
                                    onActionFeedback("Preview: minimized $appName ($secondaryActionLabel).")
                                    onDismiss()
                                }
                                .semantics {
                                    contentDescription = "Exit and minimize $appName"
                                }
                                .testTag("sim_exit_button"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
                                contentDescription = null,
                                tint = colors.ink,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = "Exit",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.15.sp
                                ),
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(OutloopMetrics.BentoGap))

                // Row 2: Full-width Pause button
                OutloopPrimaryButton(
                    text = if (limitReached) "Extend limit \u00B7 15 min" else "Pause for 15 min",
                    onClick = {
                        onActionFeedback("Preview: paused for 15 minutes.")
                        onDismiss()
                    },
                    modifier = Modifier.testTag("sim_snooze_button")
                )
            }
        }
    }
}
