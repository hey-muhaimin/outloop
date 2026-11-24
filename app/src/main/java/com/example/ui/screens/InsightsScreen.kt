package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainUiState
import com.example.ui.components.ChartColumnData
import com.example.ui.components.OutloopInsetWell
import com.example.ui.components.OutloopInsightsLightChartCard
import com.example.ui.components.OutloopMetricCell
import com.example.ui.components.OutloopSegmentedControl
import com.example.ui.components.OutloopStaggeredEntrance
import com.example.ui.components.OutloopTabHeader
import com.example.ui.components.OutloopTonalCard
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
    val mostInterruptedApp = byApp.firstOrNull { it.count > 0 }?.displayName ?: "None yet"

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutloopTabHeader(title = "Insights")
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

            // Monochrome Editorial Summary Bento Card with reflection headline + 2x2 nested bento cells
            item {
                OutloopStaggeredEntrance(index = 0) {
                    OutloopTonalCard(
                        cornerRadius = OutloopMetrics.CardCornerRadius,
                        contentPadding = PaddingValues(18.dp)
                    ) {
                        Text(
                            text = summaryHeadline,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = colors.ink
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(OutloopMetrics.BentoGap)
                        ) {
                            OutloopMetricCell(
                                value = totalCount.toString(),
                                label = if (totalCount == 1) "feed interrupted" else "feeds interrupted",
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                            OutloopMetricCell(
                                value = "~$reclaimedText",
                                label = "est. time reclaimed",
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }

                        Spacer(modifier = Modifier.height(OutloopMetrics.BentoGap))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(OutloopMetrics.BentoGap)
                        ) {
                            OutloopMetricCell(
                                value = thirdMetricValue,
                                label = thirdMetricLabel,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                            OutloopMetricCell(
                                value = mostInterruptedApp,
                                label = "most interrupted",
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }

                        Spacer(modifier = Modifier.height(OutloopMetrics.BentoGap))

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

            // Ivory "Feeds interrupted" rounded capsule bar chart card
            item {
                OutloopStaggeredEntrance(index = 1) {
                    OutloopInsightsLightChartCard(
                        title = "Feeds interrupted",
                        columns = columns,
                        selectedIndex = selectedBarIndex,
                        onSelectIndex = { selectedBarIndex = it }
                    )
                }
            }

            // "Where loops start" breakdown card
            item {
                OutloopStaggeredEntrance(index = 2) {
                    OutloopTonalCard(
                        cornerRadius = OutloopMetrics.CardCornerRadius,
                        contentPadding = PaddingValues(18.dp)
                    ) {
                        Text(
                            text = "Where loops start",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.secondaryInk
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
                            Spacer(modifier = Modifier.height(4.dp))
                            activeBreakdown.filter { it.count > 0 }.forEach { stat ->
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
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = colors.ink
                                    )
                                    Text(
                                        text = stat.count.toString(),
                                        style = MaterialTheme.typography.titleMedium.copy(
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
                                        .height(10.dp)
                                        .clip(CircleShape)
                                        .background(colors.surfaceSubtle)
                                ) {
                                    if (animatedWidth > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(animatedWidth)
                                                .height(10.dp)
                                                .clip(CircleShape)
                                                .background(colors.heroDark)
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
