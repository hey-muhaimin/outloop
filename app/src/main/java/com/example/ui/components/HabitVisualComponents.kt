package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
 * Light Tonal Bento Dark Forest Hero Card on Home ("14 loops broken" + 86dp Sage open-loop mark).
 */
@Composable
fun OutloopHeroBentoCard(
    count: Int,
    dailyBudgetCuts: Int = 0,
    modifier: Modifier = Modifier
) {
    val colors = OutloopTheme.colors
    val animatedCount by animateIntAsState(
        targetValue = count,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "heroCountValue"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OutloopMetrics.HeroCornerRadius))
            .background(colors.heroDark)
            .padding(horizontal = 24.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = animatedCount.toString(),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 54.sp,
                    lineHeight = 58.sp,
                    letterSpacing = (-1.4).sp,
                    fontFeatureSettings = "tnum"
                ),
                color = Color(0xFFFAF8F2)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (count == 1) "loop broken" else "loops broken",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFFAF8F2).copy(alpha = 0.80f)
            )
            if (dailyBudgetCuts > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.heroChipBg)
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    val budgetText = if (count >= dailyBudgetCuts) {
                        "Daily intention reached ($dailyBudgetCuts)"
                    } else {
                        "$count of $dailyBudgetCuts daily target"
                    }
                    Text(
                        text = budgetText,
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = colors.sageAccent
                    )
                }
            }
        }
        OutloopMark(
            size = 86.dp,
            color = colors.sageAccent,
            strokeWidth = 11.dp
        )
    }
}

/**
 * Light Tonal Bento Dark Forest Chart Card on Insights ("Time kept this week", big duration,
 * delta pill in #3E4C42, and chunky capsule bars in Sage #A9C7A1 + Ivory highlight).
 */
@Composable
fun OutloopInsightsDarkChartCard(
    subtitle: String,
    durationText: String,
    deltaPillText: String?,
    columns: List<ChartColumnData>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    val safeSelected = selectedIndex.coerceIn(0, (columns.size - 1).coerceAtLeast(0))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OutloopMetrics.HeroCornerRadius))
            .background(colors.heroDark)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFFAF8F2).copy(alpha = 0.84f)
            )
            val badgeCopy = deltaPillText ?: columns.getOrNull(safeSelected)?.let {
                "${it.detailLabel}: ${it.count}"
            }
            if (!badgeCopy.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.heroChipBg)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = badgeCopy,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 11.5.sp,
                            fontFeatureSettings = "tnum"
                        ),
                        color = Color(0xFFFAF8F2)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = durationText,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 40.sp,
                lineHeight = 44.sp,
                letterSpacing = (-1.0).sp,
                fontFeatureSettings = "tnum"
            ),
            color = Color(0xFFFAF8F2)
        )

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(114.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            columns.forEachIndexed { idx, col ->
                val isSelected = idx == safeSelected
                val targetHeight = if (col.count == 0) {
                    12.dp
                } else {
                    (22f + 64f * col.fraction.coerceIn(0f, 1f)).dp
                }
                val barHeight by animateDpAsState(
                    targetValue = targetHeight,
                    animationSpec = spring(
                        dampingRatio = 0.80f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "darkChartBar_$idx"
                )
                val barColor by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFFFAF8F2) else colors.sageAccent,
                    animationSpec = tween(180),
                    label = "darkChartBarColor_$idx"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(114.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            haptic()
                            onSelectIndex(idx)
                        }
                        .semantics(mergeDescendants = true) {
                            contentDescription = "${col.detailLabel}: ${col.count} loops broken"
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barHeight)
                            .clip(RoundedCornerShape(12.dp))
                            .background(barColor)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = col.shortLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = Color(0xFFFAF8F2).copy(alpha = if (isSelected) 1f else 0.72f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Retained helper for secondary / legacy callers (App Detail or compact views).
 */
@Composable
fun OutloopHeroCounterRing(
    count: Int,
    dailyBudgetCuts: Int = 0,
    modifier: Modifier = Modifier
) {
    OutloopHeroBentoCard(
        count = count,
        dailyBudgetCuts = dailyBudgetCuts,
        modifier = modifier
    )
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
                append(": ${day.count} loops broken")
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
                    color = if (day.isToday) colors.ink else colors.secondaryInk
                )
                Box(
                    modifier = Modifier
                        .size(OutloopMetrics.RhythmCircleSize)
                        .clip(CircleShape)
                        .background(if (day.isToday) colors.heroDark else colors.subtleFill)
                        .then(
                            if (day.isToday) Modifier
                            else Modifier.border(1.dp, colors.divider, CircleShape)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day.count.toString(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = if (day.isToday) Color(0xFFFAF8F2) else colors.ink
                    )
                }
            }
        }
    }
}

/**
 * Interactive simulation pause modal matching C · Overlay Feed blocked.png
 * ("Loop broken" hero + twin Mint/Peach stat tiles + primary dark pill + mint pill).
 */
@Composable
fun OutloopSimulationOverlay(
    appName: String,
    feedLabel: String,
    onActionFeedback: (String) -> Unit,
    onDismiss: () -> Unit,
    primaryActionLabel: String = "Open DMs",
    secondaryActionLabel: String = "Go to Search"
) {
    val colors = OutloopTheme.colors
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xD6141C16))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val v = cardEnter.value
                        alpha = v.coerceIn(0.1f, 1f)
                        scaleX = 0.94f + 0.06f * v
                        scaleY = 0.94f + 0.06f * v
                    }
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.surfaceCard)
                    .border(1.dp, colors.hairlineBorder, RoundedCornerShape(28.dp))
                    .clickable(enabled = false, onClick = {})
                    .padding(18.dp)
                    .testTag("simulation_pause_dialog")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.heroDark)
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.heroChipBg)
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "$appName \u00B7 $feedLabel",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFFFAF8F2)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Loop\nbroken",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 27.sp,
                                lineHeight = 31.sp
                            ),
                            color = Color(0xFFFAF8F2)
                        )
                    }
                    OutloopMark(
                        size = 72.dp,
                        color = colors.sageAccent,
                        strokeWidth = 9.5.dp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "$feedLabel is guarded. Useful parts stay open (Preview mode).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryInk
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutloopBentoStatTile(
                        value = "3m",
                        label = "time kept",
                        containerColor = colors.mintTile,
                        modifier = Modifier.weight(1f)
                    )
                    OutloopBentoStatTile(
                        value = "1 day",
                        label = "streak",
                        containerColor = colors.peachTile,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutloopPrimaryButton(
                    text = primaryActionLabel,
                    onClick = {
                        onActionFeedback("Preview: exited $feedLabel.")
                        onDismiss()
                    },
                    modifier = Modifier.testTag("sim_return_button")
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        onActionFeedback("Preview: allowed once for 5 minutes.")
                        onDismiss()
                    },
                    shape = CircleShape,
                    border = BorderStroke(0.dp, Color.Transparent),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.mintTile,
                        contentColor = colors.ink
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("sim_allow_button")
                ) {
                    Text(
                        text = secondaryActionLabel,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                OutloopTextButton(
                    text = "Pause for 15 min",
                    onClick = {
                        onActionFeedback("Preview: paused for 15 minutes.")
                        onDismiss()
                    },
                    color = colors.secondaryInk,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sim_snooze_button")
                )
            }
        }
    }
}
