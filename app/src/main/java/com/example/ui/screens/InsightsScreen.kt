package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainUiState
import com.example.ui.components.ChartColumnData
import com.example.ui.components.OutloopHairlineDivider
import com.example.ui.components.OutloopInsetWell
import com.example.ui.components.OutloopInsightsLightChartCard
import com.example.ui.components.OutloopSegmentedControl
import com.example.ui.components.OutloopStaggeredEntrance
import com.example.ui.components.OutloopTabHeader
import com.example.ui.components.OutloopTonalCard
import com.example.ui.components.outloopPressScale
import com.example.ui.components.rememberOutloopHaptic
import com.example.ui.theme.OutloopMetrics
import com.example.ui.theme.OutloopTheme
import java.util.Locale

enum class InsightsRange(val label: String) {
    TODAY("Today"),
    SEVEN_DAYS("7 days"),
    THIRTY_DAYS("30 days")
}

@Composable
fun InsightsScreen(
    uiState: MainUiState,
    bottomContentPadding: Dp = OutloopMetrics.BottomNavContentPadding
) {
    val colors = OutloopTheme.colors
    var selectedRange by rememberSaveable { mutableStateOf(InsightsRange.SEVEN_DAYS) }
    var inspectedAppName by rememberSaveable { mutableStateOf<String?>(null) }

    val columns: List<ChartColumnData> = when (selectedRange) {
        InsightsRange.TODAY -> uiState.todayHourBuckets.map {
            ChartColumnData(it.shortLabel, it.detailLabel, it.count, it.fraction)
        }
        InsightsRange.SEVEN_DAYS -> uiState.weeklyDayBars.map {
            ChartColumnData(it.dayLetter, "${it.dayName} (${it.dateLabel})", it.count, it.fraction)
        }
        InsightsRange.THIRTY_DAYS -> uiState.thirtyDayWeekBuckets.map {
            ChartColumnData(it.shortLabel, it.detailLabel, it.count, it.fraction)
        }
    }

    var selectedBarIndex by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(selectedRange, columns.size) {
        selectedBarIndex = (columns.size - 1).coerceAtLeast(0)
        inspectedAppName = null
    }

    val totalCount = when (selectedRange) {
        InsightsRange.TODAY -> uiState.todayCutsCount
        InsightsRange.SEVEN_DAYS -> uiState.weeklyCutsCount
        InsightsRange.THIRTY_DAYS -> uiState.thirtyDayCutsCount
    }
    val reclaimedText = when (selectedRange) {
        InsightsRange.TODAY -> uiState.todayReclaimedText
        InsightsRange.SEVEN_DAYS -> uiState.weeklyReclaimedText
        InsightsRange.THIRTY_DAYS -> uiState.thirtyDayReclaimedText
    }
    val byApp = when (selectedRange) {
        InsightsRange.TODAY -> uiState.todayByAppBreakdown
        InsightsRange.SEVEN_DAYS -> uiState.byAppBreakdown
        InsightsRange.THIRTY_DAYS -> uiState.thirtyDayByAppBreakdown
    }

    val summaryHeadline = when {
        totalCount == 0 && selectedRange == InsightsRange.TODAY -> "Quiet day so far."
        totalCount == 0 -> "No feeds interrupted in this period."
        totalCount in 1..4 && selectedRange == InsightsRange.TODAY -> "Staying mindful today."
        else -> "Your attention had a little more room."
    }

    val thirdMetricValue = when (selectedRange) {
        InsightsRange.TODAY -> {
            val s = uiState.streakDays
            if (s == 1) "1 day" else "$s days"
        }
        InsightsRange.SEVEN_DAYS -> String.format(Locale.US, "%.1f", totalCount / 7f)
        InsightsRange.THIRTY_DAYS -> String.format(Locale.US, "%.1f", totalCount / 28f)
    }
    val thirdMetricLabel = when (selectedRange) {
        InsightsRange.TODAY -> "current streak"
        InsightsRange.SEVEN_DAYS, InsightsRange.THIRTY_DAYS -> "daily average"
    }
    val topApp = byApp.firstOrNull { it.count > 0 }
    val mostInterruptedApp = topApp?.displayName ?: "None yet"
    val topAppShareText = if (topApp != null && totalCount > 0) {
        val pct = ((topApp.count * 100f) / totalCount).toInt().coerceIn(1, 100)
        "$pct% of pauses (${topApp.count})"
    } else {
        "No loops recorded"
    }

    val peakBucket = columns.maxByOrNull { it.count }?.takeIf { it.count > 0 }
    val peakWindowText = if (peakBucket != null) {
        "${peakBucket.detailLabel} \u00B7 ${peakBucket.count}"
    } else {
        "No peak yet"
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = OutloopMetrics.MaxContentWidth)
                .testTag("insights_list"),
            contentPadding = PaddingValues(
                start = OutloopMetrics.ScreenHorizontalPadding,
                end = OutloopMetrics.ScreenHorizontalPadding,
                top = OutloopMetrics.ScreenTopPadding,
                bottom = bottomContentPadding
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                val eyebrowLabel = when (selectedRange) {
                    InsightsRange.TODAY -> "Today \u00B7 Stored in Room"
                    InsightsRange.SEVEN_DAYS -> "Last 7 Days \u00B7 Stored in Room"
                    InsightsRange.THIRTY_DAYS -> "Last 30 Days \u00B7 Stored in Room"
                }
                OutloopTabHeader(
                    eyebrow = eyebrowLabel,
                    title = "Insights"
                )
            }

            item {
                OutloopSegmentedControl(
                    options = listOf(
                        InsightsRange.TODAY to InsightsRange.TODAY.label,
                        InsightsRange.SEVEN_DAYS to InsightsRange.SEVEN_DAYS.label,
                        InsightsRange.THIRTY_DAYS to InsightsRange.THIRTY_DAYS.label
                    ),
                    selected = selectedRange,
                    onSelect = { selectedRange = it },
                    modifier = Modifier.testTag("insights_range_selector")
                )
            }

            // Asymmetric 2x2 Bio-Mint Colored Bento Grid matching insights.html (1.35f / 0.85f & 0.85f / 1.35f)
            item {
                OutloopStaggeredEntrance(index = 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(OutloopMetrics.BentoGap)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(OutloopMetrics.BentoGap)
                        ) {
                            BentoColoredTile(
                                label = if (totalCount == 1) "Feed interrupted" else "Feeds interrupted",
                                value = totalCount.toString(),
                                subtitle = selectedRangeSummaryLabel(selectedRange),
                                containerColor = colors.peachTile,
                                onClick = { inspectedAppName = null },
                                modifier = Modifier
                                    .weight(1.35f)
                                    .fillMaxHeight()
                            )
                            BentoColoredTile(
                                label = "Est. reclaimed",
                                value = "~$reclaimedText",
                                subtitle = "attention kept",
                                containerColor = colors.mintTile,
                                onClick = { inspectedAppName = null },
                                modifier = Modifier
                                    .weight(0.85f)
                                    .fillMaxHeight()
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(OutloopMetrics.BentoGap)
                        ) {
                            BentoColoredTile(
                                label = thirdMetricLabel,
                                value = thirdMetricValue,
                                subtitle = if (selectedRange == InsightsRange.TODAY) "active rhythm" else "loops / day",
                                containerColor = colors.yellowTile,
                                onClick = { inspectedAppName = null },
                                modifier = Modifier
                                    .weight(0.85f)
                                    .fillMaxHeight()
                            )
                            BentoColoredTile(
                                label = "Most interrupted",
                                value = mostInterruptedApp,
                                subtitle = topAppShareText,
                                containerColor = colors.skyTile,
                                onClick = {
                                    if (topApp != null) {
                                        inspectedAppName = topApp.displayName
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.35f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }

            // Peak / Pattern Reflection Card + Measured Feed Time matching insights.html
            item {
                OutloopStaggeredEntrance(index = 1) {
                    OutloopTonalCard(
                        cornerRadius = OutloopMetrics.CardCornerRadius,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        Text(
                            text = summaryHeadline,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = colors.ink
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (inspectedAppName != null && topApp != null) {
                                "${inspectedAppName}: ${topApp.count} feed interruptions recorded while keeping DMs and search open."
                            } else {
                                "Outloop pauses short-form scroll loops on-device while keeping messaging, search, and subscriptions accessible."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryInk
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        OutloopHairlineDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Most active window",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = colors.secondaryInk
                            )
                            Text(
                                text = peakWindowText,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = colors.ink
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val measuredLabel = when {
                            uiState.isGlobalFeedBudgetReached && uiState.globalFeedBudgetMinutes > 0 ->
                                "Time in feeds today \u00B7 ${uiState.globalFeedBudgetMinutes}m budget reached"
                            uiState.globalFeedBudgetMinutes > 0 ->
                                "Time in feeds today \u00B7 of ${uiState.globalFeedBudgetMinutes}m budget"
                            else -> "Time in feeds today"
                        }
                        OutloopInsetWell(
                            containerColor = if (uiState.isGlobalFeedBudgetReached) colors.warnBg else colors.canvas,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("insights_measured_feed_time")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = measuredLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (uiState.isGlobalFeedBudgetReached) colors.warnText else colors.secondaryInk,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = uiState.todayMeasuredFeedText,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = if (uiState.isGlobalFeedBudgetReached) colors.warnText else colors.ink
                                )
                            }
                        }
                    }
                }
            }

            // "Feeds interrupted" rounded capsule bar chart card
            item {
                OutloopStaggeredEntrance(index = 2) {
                    OutloopInsightsLightChartCard(
                        title = "Feeds interrupted",
                        columns = columns,
                        selectedIndex = selectedBarIndex,
                        onSelectIndex = { selectedBarIndex = it }
                    )
                }
            }

            // "Where loops start" progress card matching insights.html drivers-card
            item {
                OutloopStaggeredEntrance(index = 3) {
                    OutloopTonalCard(
                        cornerRadius = OutloopMetrics.CardCornerRadius,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        Text(
                            text = "WHERE LOOPS START",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.mutedInk
                        )

                        val activeBreakdown = byApp.filter {
                            it.count > 0 || it.packageName in uiState.installedPackages
                        }

                        if (totalCount == 0 || activeBreakdown.none { it.count > 0 }) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (selectedRange == InsightsRange.TODAY) {
                                    "No feeds needed interrupting today. Outloop only counts real interruptions."
                                } else {
                                    "Your breakdown appears here once Outloop breaks a feed loop."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.secondaryInk
                            )
                        } else {
                            val barPalette = listOf(
                                colors.heroDark,
                                colors.tealTile,
                                colors.peachTile,
                                colors.sageAccent
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            activeBreakdown.filter { it.count > 0 }.forEachIndexed { idx, stat ->
                                val pct = ((stat.count * 100f) / totalCount.coerceAtLeast(1)).toInt().coerceIn(1, 100)
                                val animatedWidth by animateFloatAsState(
                                    targetValue = stat.fraction.coerceIn(0f, 1f),
                                    animationSpec = spring(
                                        dampingRatio = 0.85f,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    label = "breakdown_${stat.packageName}"
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stat.label,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = colors.secondaryInk
                                    )
                                    Text(
                                        text = "${stat.count} ($pct%)",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFeatureSettings = "tnum"
                                        ),
                                        color = colors.ink
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(colors.surfaceSubtle)
                                ) {
                                    if (animatedWidth > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(animatedWidth)
                                                .height(6.dp)
                                                .clip(CircleShape)
                                                .background(barPalette[idx % barPalette.size])
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BentoColoredTile(
    label: String,
    value: String,
    subtitle: String,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(OutloopMetrics.CardCornerRadius)

    Column(
        modifier = modifier
            .outloopPressScale(interactionSource, pressedScale = 0.98f)
            .shadow(
                elevation = 2.dp,
                shape = shape,
                ambientColor = colors.dockShadow,
                spotColor = colors.dockShadow
            )
            .clip(shape)
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) {
                haptic()
                onClick()
            }
            .heightIn(min = 118.dp)
            .padding(17.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 10.sp,
                letterSpacing = 0.7.sp
            ),
            color = colors.ink.copy(alpha = 0.65f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 25.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                fontFeatureSettings = "tnum"
            ),
            color = colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = colors.ink.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun selectedRangeSummaryLabel(range: InsightsRange): String = when (range) {
    InsightsRange.TODAY -> "today"
    InsightsRange.SEVEN_DAYS -> "last 7 days"
    InsightsRange.THIRTY_DAYS -> "last 30 days"
}
