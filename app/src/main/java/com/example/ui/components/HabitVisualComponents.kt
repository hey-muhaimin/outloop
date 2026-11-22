package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.OutloopTheme
import kotlinx.coroutines.delay

data class ChartColumnData(
    val shortLabel: String,
    val detailLabel: String,
    val count: Int,
    val fraction: Float
)

/**
 * Deep Forest bento hero card shown on Today: giant ivory tabular count + "loops broken"
 * on the left and a bold sage open-loop ring on the right.
 */
@Composable
fun OutloopTodayHeroCard(
    count: Int,
    dailyBudgetCuts: Int = 0,
    subtitleOverride: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = OutloopTheme.colors
    val targetFraction = if (dailyBudgetCuts > 0) {
        (count.toFloat() / dailyBudgetCuts.toFloat()).coerceIn(0f, 1f)
    } else {
        1f
    }
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "todayHeroRingProgress"
    )

    OutloopBentoCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 28.dp,
        containerColor = colors.heroSurface,
        borderColor = null,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = count.toString(),
                    fontSize = 52.sp,
                    lineHeight = 56.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.5).sp,
                    color = colors.onHeroSurface,
                    style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum")
                )
                Text(
                    text = subtitleOverride ?: "loops broken",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    ),
                    color = colors.onHeroSurface.copy(alpha = 0.86f)
                )
                if (dailyBudgetCuts > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val budgetText = if (count >= dailyBudgetCuts) {
                        "Daily intention reached ($dailyBudgetCuts)"
                    } else {
                        "$count of $dailyBudgetCuts daily target"
                    }
                    Text(
                        text = budgetText,
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        color = colors.heroAccent
                    )
                }
            }

            Box(
                modifier = Modifier.size(92.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = 11.dp.toPx()
                    val inset = strokePx / 2f + 2.dp.toPx()
                    val diameter = (size.minDimension - inset * 2f).coerceAtLeast(1f)
                    if (dailyBudgetCuts > 0) {
                        drawArc(
                            color = colors.heroTrack,
                            startAngle = -27f,
                            sweepAngle = -324f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(diameter, diameter),
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                        if (animatedFraction > 0f) {
                            drawArc(
                                color = colors.heroAccent,
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
                            color = colors.heroAccent,
                            startAngle = -27f,
                            sweepAngle = -324f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(diameter, diameter),
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reusable Mint or Peach tonal bento metric tile (used on Today, Insights, and Feed Blocked Overlay).
 */
@Composable
fun OutloopBentoStatTile(
    value: String,
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    OutloopBentoCard(
        modifier = modifier,
        cornerRadius = if (compact) 20.dp else 24.dp,
        containerColor = containerColor,
        borderColor = null,
        contentPadding = PaddingValues(
            horizontal = if (compact) 16.dp else 20.dp,
            vertical = if (compact) 14.dp else 18.dp
        ),
        onClick = onClick
    ) {
        Text(
            text = value,
            fontSize = if (compact) 22.sp else 28.sp,
            lineHeight = if (compact) 26.sp else 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.6).sp,
            color = contentColor,
            style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = if (compact) 13.sp else 14.sp
            ),
            color = contentColor.copy(alpha = 0.85f),
            maxLines = 1
        )
    }
}

/**
 * Interactive capsule bar chart rendered inside the Deep Forest Insights hero card:
 * Sage capsules for unselected columns, crisp ivory capsule for the selected/active column.
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
                .height(132.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            columns.forEachIndexed { idx, col ->
                val isSelected = idx == safeSelected
                val barHeight = if (col.count == 0) {
                    18.dp
                } else {
                    (28f + 72f * col.fraction.coerceIn(0f, 1f)).dp
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(132.dp)
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
                            .fillMaxWidth(0.92f)
                            .height(barHeight)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) colors.onHeroSurface else colors.heroAccent
                            )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = col.shortLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) {
                            colors.onHeroSurface
                        } else {
                            colors.onHeroSurface.copy(alpha = 0.72f)
                        },
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Interactive "Loop broken" Tonal Bento overlay modal (with inline transition to Strict Mode
 * "Pause Guard?" 10-second countdown when Pause is tapped).
 */
@Composable
fun OutloopSimulationOverlay(
    appName: String,
    feedLabel: String,
    loopsToday: Int = 14,
    timeKeptText: String = "1h 52m",
    streakDays: Int = 6,
    strictModeEnabled: Boolean = false,
    onActionFeedback: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = OutloopTheme.colors
    var showStrictPauseStep by remember { mutableStateOf(false) }

    if (showStrictPauseStep) {
        OutloopPauseGuardDialog(
            initialMinutes = 15,
            onKeepGuarding = {
                onActionFeedback("Kept guarding $appName.")
                onDismiss()
            },
            onConfirmPause = { minutes ->
                onActionFeedback("Simulation: paused guard for $minutes min.")
                onDismiss()
            },
            onDismiss = onDismiss
        )
        return
    }

    val safeActions = when {
        appName.contains("Instagram", ignoreCase = true) -> "Open DMs" to "Go to Search"
        appName.contains("YouTube", ignoreCase = true) -> "Go to Home" to "Go to Search"
        appName.contains("Facebook", ignoreCase = true) -> "Open Messenger" to "Go to Home"
        else -> "Return to app" to "Allow once"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF141D16).copy(alpha = 0.92f))
                    .clickable(onClick = onDismiss)
            )
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp))
                    .background(colors.cardSurface)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .testTag("simulation_pause_dialog")
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top dark hero header box inside the cream overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.heroSurface)
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(colors.heroTrack)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "$appName \u00B7 $feedLabel",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.onHeroSurface
                                )
                            }
                            Text(
                                text = "Loop\nbroken",
                                fontSize = 30.sp,
                                lineHeight = 33.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.8).sp,
                                color = colors.onHeroSurface
                            )
                        }
                        OutloopMark(
                            size = 64.dp,
                            color = colors.heroAccent,
                            strokeWidth = 9.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "$feedLabel is guarded, and that is $loopsToday loops broken today. Messages and Search are still open.",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = colors.secondaryInk,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutloopBentoStatTile(
                        value = timeKeptText,
                        label = "time kept",
                        containerColor = colors.mintBg,
                        contentColor = colors.mintInk,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutloopBentoStatTile(
                        value = "$streakDays ${if (streakDays == 1) "day" else "days"}",
                        label = "streak",
                        containerColor = colors.peachBg,
                        contentColor = colors.peachInk,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutloopPrimaryButton(
                    text = safeActions.first,
                    onClick = {
                        onActionFeedback("Simulation: opened safe area in $appName.")
                        onDismiss()
                    },
                    modifier = Modifier.testTag("sim_return_button")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutloopTonalButton(
                    text = safeActions.second,
                    onClick = {
                        onActionFeedback("Simulation: allowed once for 5 minutes.")
                        onDismiss()
                    },
                    modifier = Modifier.testTag("sim_allow_button")
                )

                Spacer(modifier = Modifier.height(4.dp))

                OutloopTextButton(
                    text = "Pause for 15 min",
                    onClick = {
                        if (strictModeEnabled) {
                            showStrictPauseStep = true
                        } else {
                            onActionFeedback("Simulation: paused guard for 15 minutes.")
                            onDismiss()
                        }
                    },
                    color = colors.secondaryInk,
                    modifier = Modifier.testTag("sim_snooze_button")
                )
            }
        }
    }
}

/**
 * Functional Strict Mode "Pause Guard?" 10-second countdown overlay dialog matching the mockup.
 */
@Composable
fun OutloopPauseGuardDialog(
    initialMinutes: Int = 15,
    waitSeconds: Int = 10,
    onKeepGuarding: () -> Unit,
    onConfirmPause: (Int) -> Unit,
    onDismiss: () -> Unit = onKeepGuarding
) {
    val colors = OutloopTheme.colors
    var selectedMinutes by rememberSaveable { mutableIntStateOf(initialMinutes) }
    var secondsLeft by rememberSaveable { mutableIntStateOf(waitSeconds.coerceAtLeast(1)) }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000L)
            secondsLeft--
        }
    }

    val ringProgress by animateFloatAsState(
        targetValue = (secondsLeft.toFloat() / waitSeconds.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pauseGuardRingSweep"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF141D16).copy(alpha = 0.92f))
                    .clickable(onClick = onDismiss)
            )
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp))
                    .background(colors.cardSurface)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .testTag("pause_guard_dialog")
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.heroSurface)
                        .padding(horizontal = 18.dp, vertical = 18.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Start)
                                .clip(CircleShape)
                                .background(colors.heroTrack)
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Strict mode",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.onHeroSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier.size(116.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokePx = 10.dp.toPx()
                                val inset = strokePx / 2f + 2.dp.toPx()
                                val diameter = (size.minDimension - inset * 2f).coerceAtLeast(1f)
                                drawArc(
                                    color = colors.heroTrack,
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    topLeft = Offset(inset, inset),
                                    size = Size(diameter, diameter),
                                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                                )
                                if (ringProgress > 0f) {
                                    drawArc(
                                        color = colors.heroAccent,
                                        startAngle = -90f,
                                        sweepAngle = 360f * ringProgress,
                                        useCenter = false,
                                        topLeft = Offset(inset, inset),
                                        size = Size(diameter, diameter),
                                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = secondsLeft.toString(),
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onHeroSurface,
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontFeatureSettings = "tnum"
                                    ),
                                    modifier = Modifier.testTag("pause_guard_countdown")
                                )
                                Text(
                                    text = if (secondsLeft == 1) "second" else "seconds",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onHeroSurface.copy(alpha = 0.78f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Pause Guard?",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = colors.ink
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Strict mode asks you to wait $waitSeconds seconds first. Still want a break?",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = colors.secondaryInk
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutloopSegmentedControl(
                    options = listOf(
                        5 to "5 min",
                        15 to "15 min",
                        30 to "30 min"
                    ),
                    selected = selectedMinutes,
                    onSelect = { selectedMinutes = it },
                    modifier = Modifier.testTag("pause_guard_duration_selector")
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutloopPrimaryButton(
                    text = "Keep guarding",
                    onClick = onKeepGuarding,
                    modifier = Modifier.testTag("pause_guard_keep_button")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutloopSecondaryButton(
                    text = if (secondsLeft > 0) {
                        "Pause in $secondsLeft s"
                    } else {
                        "Pause for $selectedMinutes min"
                    },
                    enabled = secondsLeft == 0,
                    onClick = { onConfirmPause(selectedMinutes) },
                    modifier = Modifier.testTag("pause_guard_confirm_button")
                )
            }
        }
    }
}
