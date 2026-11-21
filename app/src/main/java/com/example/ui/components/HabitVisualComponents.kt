package com.example.ui.components

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.ui.theme.OutloopTheme

data class ChartColumnData(
    val shortLabel: String,
    val detailLabel: String,
    val count: Int,
    val fraction: Float
)

/**
 * 104dp Outloop open-loop progress ring wrapping the tabular daily count.
 */
@Composable
fun OutloopHeroCounterRing(
    count: Int,
    dailyBudgetCuts: Int = 0,
    modifier: Modifier = Modifier
) {
    val colors = OutloopTheme.colors
    val targetFraction = if (dailyBudgetCuts > 0) {
        (count.toFloat() / dailyBudgetCuts.toFloat()).coerceIn(0f, 1f)
    } else {
        1f
    }
    val animatedFraction by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessLow),
        label = "heroRingProgress"
    )
    Box(
        modifier = modifier.size(104.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 9.dp.toPx()
            val inset = strokePx / 2f + 2.dp.toPx()
            val diameter = (size.minDimension - inset * 2f).coerceAtLeast(1f)
            if (dailyBudgetCuts > 0) {
                drawArc(
                    color = colors.primaryTint.copy(alpha = 0.22f),
                    startAngle = -27f,
                    sweepAngle = -324f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
                if (animatedFraction > 0f) {
                    drawArc(
                        color = if (count >= dailyBudgetCuts) colors.switchOn else colors.primaryTint,
                        startAngle = -27f,
                        sweepAngle = -324f * animatedFraction,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(diameter, diameter),
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
            } else {
                drawArc(
                    color = colors.primaryTint,
                    startAngle = -27f,
                    sweepAngle = -324f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }
        Text(
            text = count.toString(),
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.8).sp,
            color = colors.ink,
            style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum")
        )
    }
}

/**
 * 7-day circular pill strip ("S M T W T F S" ending on today) matching the Outloop Today card.
 */
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
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (day.isToday) colors.primaryTint else colors.subtleFill)
                        .then(
                            if (day.isToday) Modifier
                            else Modifier.border(1.dp, colors.divider, CircleShape)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day.count.toString(),
                        style = MaterialTheme.typography.bodyMedium.copy(
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
 * Interactive capsule bar chart matching the Outloop Insights card:
 * 28% alpha on unselected bars, 100% alpha + numeric caption above selected bar.
 */
@Composable
fun OutloopInsightsBarChart(
    columns: List<ChartColumnData>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    val safeSelected = selectedIndex.coerceIn(0, (columns.size - 1).coerceAtLeast(0))

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(146.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            columns.forEachIndexed { idx, col ->
                val isSelected = idx == safeSelected
                val barHeight = if (col.count == 0) {
                    6.dp
                } else {
                    (14f + 78f * col.fraction.coerceIn(0f, 1f)).dp
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(146.dp)
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
                        modifier = Modifier.height(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Text(
                                text = col.count.toString(),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = colors.ink
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.76f)
                            .height(barHeight)
                            .clip(RoundedCornerShape(17.dp))
                            .background(
                                colors.primaryTint.copy(alpha = if (isSelected) 1f else 0.28f)
                            )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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

        if (columns.isNotEmpty()) {
            val chosen = columns[safeSelected]
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "${chosen.detailLabel}: ${chosen.count}",
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = colors.secondaryInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Interactive simulation pause modal shown from Onboarding Step 3 Preview and App Detail Preview.
 */
@Composable
fun OutloopSimulationOverlay(
    appName: String,
    feedLabel: String,
    onActionFeedback: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = OutloopTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D120E))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            // Subtle simulated blurred video shapes behind the frosted pause card
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .height(280.dp)
                    .align(Alignment.TopStart)
                    .offset(x = 12.dp, y = 44.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF27382D), Color(0xFF141C17))
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                    )
                }
            }

            // Crisp tonal modal card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.sheetSurface)
                    .border(1.dp, colors.hairlineBorder, RoundedCornerShape(28.dp))
                    .clickable(enabled = false, onClick = {})
                    .padding(24.dp)
                    .testTag("simulation_pause_dialog"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutloopStatusPill(
                    text = "Simulation, not a real feed",
                    tone = PillTone.NEUTRAL
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutloopMark(size = 44.dp, color = colors.primaryTint)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Take a moment.",
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp),
                    color = colors.ink,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Is this what you came here for?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.secondaryInk,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$appName \u00B7 $feedLabel",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textAction
                )
                Spacer(modifier = Modifier.height(18.dp))
                OutloopPrimaryButton(
                    text = "Return to my intention",
                    onClick = {
                        onActionFeedback("Simulation: returned to your intention.")
                        onDismiss()
                    },
                    modifier = Modifier.testTag("sim_return_button")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutloopSecondaryButton(
                    text = "Allow once",
                    onClick = {
                        onActionFeedback("Simulation: allowed once for 5 minutes.")
                        onDismiss()
                    },
                    modifier = Modifier.testTag("sim_allow_button")
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutloopTextButton(
                    text = "Snooze for 15 minutes",
                    onClick = {
                        onActionFeedback("Simulation: snoozed for 15 minutes.")
                        onDismiss()
                    },
                    color = colors.secondaryInk,
                    modifier = Modifier.testTag("sim_snooze_button")
                )
            }
        }
    }
}
