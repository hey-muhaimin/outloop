package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.MainUiState
import com.example.ui.components.ChartColumnData
import com.example.ui.components.OutloopAppMonogramBadge
import com.example.ui.components.OutloopInsightsBarChart
import com.example.ui.components.OutloopMark
import com.example.ui.components.OutloopSegmentedControl
import com.example.ui.components.OutloopTonalCard
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
    bottomContentPadding: Dp = 116.dp
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
    val topAppName = byApp.firstOrNull { it.count > 0 }?.displayName ?: "\u2014"

    val narrativeTitle = when (selectedRange) {
        InsightsRange.TODAY -> "Small moments of awareness add up."
        InsightsRange.SEVEN_DAYS -> "Your attention had a little more room this week."
        InsightsRange.THIRTY_DAYS -> "You paused before entering $totalCount feeds."
    }

    val thirdKpiValue = when (selectedRange) {
        InsightsRange.TODAY -> uiState.todayBusiestTimeLabel
        InsightsRange.SEVEN_DAYS -> String.format(Locale.US, "%.1f", uiState.weeklyCutsCount / 7f)
        InsightsRange.THIRTY_DAYS -> String.format(Locale.US, "%.1f", uiState.thirtyDayCutsCount / 28f)
    }
    val thirdKpiLabel = when (selectedRange) {
        InsightsRange.TODAY -> "busiest time"
        InsightsRange.SEVEN_DAYS, InsightsRange.THIRTY_DAYS -> "daily average"
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 24.dp,
                bottom = bottomContentPadding
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Insights",
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.ink
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

            if (totalCount == 0) {
                item {
                    OutloopTonalCard(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 36.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutloopMark(size = 56.dp, color = colors.primaryTint)
                            Text(
                                text = if (selectedRange == InsightsRange.TODAY) {
                                    "Nothing to interrupt today."
                                } else {
                                    "Your insights will appear as you use Outloop."
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.ink,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Outloop only counts feeds it actually interrupted.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.secondaryInk,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // Narrative 2x2 KPI tonal card
                item {
                    OutloopTonalCard(contentPadding = PaddingValues(20.dp)) {
                        Text(
                            text = narrativeTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            InsightMetricTile(
                                value = totalCount.toString(),
                                label = "feeds interrupted",
                                modifier = Modifier.weight(1f)
                            )
                            InsightMetricTile(
                                value = reclaimedText,
                                label = "est. time reclaimed",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            InsightMetricTile(
                                value = thirdKpiValue,
                                label = thirdKpiLabel,
                                modifier = Modifier.weight(1f)
                            )
                            InsightMetricTile(
                                value = topAppName,
                                label = "most interrupted",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Interactive bar chart card
                item {
                    OutloopTonalCard(contentPadding = PaddingValues(20.dp)) {
                        Text(
                            text = "Feeds interrupted",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutloopInsightsBarChart(
                            columns = columns,
                            selectedIndex = selectedBarIndex,
                            onSelectIndex = { selectedBarIndex = it }
                        )
                    }
                }

                // By app breakdown
                item {
                    OutloopTonalCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)) {
                        Text(
                            text = "By app",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink
                        )
                        val activeBreakdown = byApp.filter { it.count > 0 || it.packageName in uiState.installedPackages }
                            .ifEmpty { byApp }
                        activeBreakdown.forEach { stat ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutloopAppMonogramBadge(
                                        appName = stat.displayName,
                                        packageName = stat.packageName,
                                        size = 28.dp,
                                        cornerRadius = 8.dp
                                    )
                                    Text(
                                        text = stat.displayName,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = colors.ink
                                    )
                                }
                                Text(
                                    text = stat.count.toString(),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = colors.secondaryInk
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape)
                                    .background(colors.divider)
                            ) {
                                if (stat.fraction > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(stat.fraction.coerceIn(0f, 1f))
                                            .height(8.dp)
                                            .clip(CircleShape)
                                            .background(colors.primaryTint)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Estimated from confirmed exits, not measured screen time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryInk,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightMetricTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = OutloopTheme.colors
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFeatureSettings = "tnum"
            ),
            color = colors.ink
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.secondaryInk
        )
    }
}
