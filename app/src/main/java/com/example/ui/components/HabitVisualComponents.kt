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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.data.local.AppPreferences
import com.example.data.repository.InterventionRepository
import com.example.ui.AppBreakdownStat
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
 * Bio-Mint & Tonal Bento Dark Hero Guard Card (#121312):
 * Top row: mint/peach status tag + mode badge.
 * Middle: bold white loops-stopped metric + clickable reclaimed time estimate + OutloopMark.
 * 4-color progress segment bars (teal, yellow, peach, mint).
 * Nested dark wells for daily target, streak, and measured feed budget progress.
 */
@Composable
fun OutloopHomeProgressCard(
    count: Int,
    reclaimedText: String,
    dayBars: List<DayBarStat>,
    dailyBudgetCuts: Int = 0,
    streakDays: Int = 0,
    todayMeasuredFeedSeconds: Long = 0L,
    globalFeedBudgetMinutes: Int = AppPreferences.DEFAULT_GLOBAL_FEED_BUDGET_MINUTES,
    isGlobalFeedBudgetReached: Boolean = false,
    activeAppsCount: Int = 0,
    statusTagText: String = "\u25CF Outloop Active",
    modeBadgeText: String = "Pause Overlay",
    isStatusWarning: Boolean = false,
    onReclaimedInfoClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = OutloopTheme.colors
    val animatedCount by animateIntAsState(
        targetValue = count,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "homeProgressCount"
    )
    val heroShape = RoundedCornerShape(OutloopMetrics.HeroCornerRadius)
    val wellShape = RoundedCornerShape(OutloopMetrics.WellCornerRadius)
    val loopsWord = if (animatedCount == 1) "loop stopped" else "loops stopped"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = heroShape,
                ambientColor = colors.dockShadow,
                spotColor = colors.dockShadow
            )
            .clip(heroShape)
            .background(colors.heroDark)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top status tag & mode badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = statusTagText.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 10.5.sp,
                    letterSpacing = 0.8.sp
                ),
                color = if (isStatusWarning) colors.peachTile else colors.sageAccent
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.heroChipBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = modeBadgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }
        }

        // Bold Hero KPI + Mark
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {
                        contentDescription = if (count == 1) {
                            "1 feed interrupted today"
                        } else {
                            "$count feeds interrupted today"
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "$animatedCount $loopsWord",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 31.sp,
                        lineHeight = 35.sp,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = Color.White
                )
                val guardingPrefix = if (activeAppsCount > 0) {
                    "Guarding $activeAppsCount app${if (activeAppsCount == 1) "" else "s"} \u00B7 "
                } else {
                    ""
                }
                Text(
                    text = "${guardingPrefix}~$reclaimedText reclaimed",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontFeatureSettings = "tnum"
                    ),
                    color = colors.heroMutedInk,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onReclaimedInfoClick)
                        .padding(vertical = 2.dp)
                        .testTag("home_reclaimed_estimate_text")
                )
            }

            Spacer(modifier = Modifier.size(12.dp))
            OutloopMark(
                size = 46.dp,
                color = if (isStatusWarning) colors.peachTile else colors.sageAccent,
                strokeWidth = 6.dp
            )
        }

        // 4-color Bio-Mint segment progress bars from home.html
        val segmentColors = listOf(
            colors.tealTile,
            colors.yellowTile,
            colors.peachTile,
            colors.sageAccent
        )
        val activeSegments = when {
            count <= 0 -> 1
            dailyBudgetCuts > 0 -> ((count.toFloat() / dailyBudgetCuts.toFloat()) * 4f).toInt().coerceIn(1, 4)
            else -> count.coerceIn(1, 4)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            segmentColors.forEachIndexed { index, segColor ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < activeSegments) segColor else colors.heroTrack
                        )
                )
            }
        }

        // Compact 2-up dark bento wells: Daily target + Streak
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(wellShape)
                    .background(if (targetMet) colors.sageAccent.copy(alpha = 0.16f) else colors.heroChipBg)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = targetValue,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        fontFeatureSettings = "tnum"
                    ),
                    color = if (targetMet) colors.sageAccent else Color.White,
                    maxLines = 1
                )
                Text(
                    text = targetLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.heroMutedInk
                )
            }

            val streakValue = if (streakDays == 1) "1 day" else "$streakDays days"
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(wellShape)
                    .background(colors.heroChipBg)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = streakValue,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        fontFeatureSettings = "tnum"
                    ),
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = "current streak",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.heroMutedInk
                )
            }
        }

        // Measured Feed Time + Global Feed Budget progress bar
        val measuredCompact = InterventionRepository.formatDurationCompact(todayMeasuredFeedSeconds)
        val hasGlobalBudget = globalFeedBudgetMinutes > 0
        val globalBudgetSeconds = (globalFeedBudgetMinutes * 60L).coerceAtLeast(1L)
        val measuredBudgetProgress = if (hasGlobalBudget) {
            (todayMeasuredFeedSeconds.toFloat() / globalBudgetSeconds.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        val measuredValueText = if (hasGlobalBudget) {
            "$measuredCompact / ${globalFeedBudgetMinutes}m"
        } else {
            measuredCompact
        }
        val measuredSubtitleText = when {
            isGlobalFeedBudgetReached && hasGlobalBudget -> "Daily feed budget reached"
            hasGlobalBudget -> "Time in feeds today \u00B7 ${globalFeedBudgetMinutes}m budget"
            else -> "Time in feeds today"
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(wellShape)
                .background(
                    if (isGlobalFeedBudgetReached) colors.peachTile.copy(alpha = 0.16f) else colors.heroChipBg
                )
                .padding(horizontal = 14.dp, vertical = 11.dp)
                .testTag("home_measured_feed_time")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = measuredSubtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isGlobalFeedBudgetReached) colors.peachTile else colors.heroMutedInk,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = measuredValueText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = if (isGlobalFeedBudgetReached) colors.peachTile else Color.White
                )
            }
            if (hasGlobalBudget) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(colors.heroTrack)
                ) {
                    if (todayMeasuredFeedSeconds > 0L) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(measuredBudgetProgress.coerceAtLeast(0.04f))
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(if (isGlobalFeedBudgetReached) colors.peachTile else colors.sageAccent)
                        )
                    }
                }
            }
        }

        if (dayBars.isNotEmpty()) {
            OutloopDailyRhythmStrip(
                dayBars = dayBars,
                onDarkHero = true
            )
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
                        targetValue = if (isSelected) colors.heroDark else colors.tealTile.copy(alpha = 0.55f),
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
    modifier: Modifier = Modifier,
    onDarkHero: Boolean = false
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
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = accessibilityLabel
                }
            ) {
                Text(
                    text = day.dayLetter,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = when {
                        onDarkHero && day.isToday -> colors.sageAccent
                        onDarkHero -> colors.heroMutedInk
                        day.isToday -> colors.ink
                        else -> colors.secondaryInk
                    }
                )
                Box(
                    modifier = Modifier
                        .size(if (onDarkHero) 32.dp else OutloopMetrics.RhythmCircleSize)
                        .clip(CircleShape)
                        .background(
                            when {
                                onDarkHero && day.isToday -> colors.sageAccent
                                onDarkHero -> colors.heroChipBg
                                day.isToday -> colors.heroDark
                                else -> colors.surfaceElevated
                            }
                        )
                        .then(
                            if (day.isToday || onDarkHero) {
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = when {
                            onDarkHero && day.isToday -> colors.ink
                            onDarkHero -> Color.White
                            day.isToday -> colors.onPrimaryTint
                            else -> colors.ink
                        }
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
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF111214).copy(alpha = scrimAlpha))
                .clickable(onClick = onDismiss)
                .systemBarsPadding()
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
                    .verticalScroll(rememberScrollState())
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

                // Row 1: Bio-Mint 2-row action dock: Home | Exit pills inside #F4F5F3 dock
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .clip(RoundedCornerShape(OutloopMetrics.CardCornerRadius))
                        .background(colors.canvas)
                        .border(1.dp, colors.hairlineBorder, RoundedCornerShape(OutloopMetrics.CardCornerRadius))
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val homeInteraction = remember { MutableInteractionSource() }
                    val dockBtnShape = RoundedCornerShape(OutloopMetrics.ButtonCornerRadius)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .outloopPressScale(homeInteraction, pressedScale = 0.96f)
                            .shadow(
                                elevation = 2.dp,
                                shape = dockBtnShape,
                                ambientColor = colors.dockShadow,
                                spotColor = colors.dockShadow
                            )
                            .clip(dockBtnShape)
                            .background(colors.surfaceElevated)
                            .border(0.75.dp, colors.hairlineBorder, dockBtnShape)
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
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Home",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
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
                            .clip(dockBtnShape)
                            .background(colors.surfaceSubtle)
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
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Exit",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(OutloopMetrics.BentoGap))

                // Row 2: Full-width Mint Pause button
                OutloopMintButton(
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

/**
 * Daily summary card displaying how many times the AccessibilityService triggered a 'pause'
 * on supported social media apps based on current Room database logs, using D3-style Canvas graphing
 * with Bio-Mint accent (#9DE8B2).
 */
@Composable
fun OutloopAccessibilityPauseSummaryCard(
    byAppBreakdown: List<AppBreakdownStat>,
    modifier: Modifier = Modifier
) {
    val colors = OutloopTheme.colors
    val totalPauses = byAppBreakdown.sumOf { it.count }

    OutloopTonalCard(
        cornerRadius = OutloopMetrics.CardCornerRadius,
        contentPadding = PaddingValues(18.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Accessibility Pause Triggers",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = colors.ink
                    )
                    Text(
                        text = "$totalPauses total pauses logged by AccessibilityService",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryInk
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(colors.sageAccent.copy(alpha = 0.25f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Live Sync",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.ink
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // D3-style Canvas graphing of pause triggers per app
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceSubtle)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (byAppBreakdown.isEmpty()) {
                    Text(
                        text = "No accessibility pause events logged yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.mutedInk
                    )
                } else {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val maxCount = byAppBreakdown.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                        val barWidth = (size.width / (byAppBreakdown.size * 2f)).coerceIn(12.dp.toPx(), 36.dp.toPx())
                        val spacing = (size.width - (barWidth * byAppBreakdown.size)) / (byAppBreakdown.size + 1).coerceAtLeast(1)
                        val chartHeight = size.height - 16.dp.toPx()

                        byAppBreakdown.forEachIndexed { idx, stat ->
                            val fraction = (stat.count.toFloat() / maxCount.toFloat()).coerceIn(0.1f, 1f)
                            val barHeight = chartHeight * fraction
                            val left = spacing + idx * (barWidth + spacing)
                            val top = size.height - barHeight - 4.dp.toPx()

                            drawRoundRect(
                                color = colors.surfaceCard,
                                topLeft = Offset(left, 4.dp.toPx()),
                                size = Size(barWidth, chartHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                            )
                            drawRoundRect(
                                color = colors.sageAccent,
                                topLeft = Offset(left, top),
                                size = Size(barWidth, barHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                            )
                        }
                    }
                }
            }

            if (byAppBreakdown.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    byAppBreakdown.take(3).forEach { stat ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surfaceSubtle)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(colors.sageAccent)
                            )
                            Text(
                                text = "${stat.displayName}: ${stat.count}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = colors.ink,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

