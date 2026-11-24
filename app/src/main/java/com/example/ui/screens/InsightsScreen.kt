package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import com.example.data.model.ProtectionStatus
import com.example.ui.MainUiState
import com.example.ui.components.ChartColumnData
import com.example.ui.components.OutloopBentoStatTile
import com.example.ui.components.OutloopBrandTopBar
import com.example.ui.components.OutloopInsightsDarkChartCard
import com.example.ui.components.OutloopSegmentedControl
import com.example.ui.components.OutloopStaggeredEntrance
import com.example.ui.components.OutloopTonalCard
import com.example.ui.components.PillTone
import com.example.ui.theme.OutloopMetrics
import com.example.ui.theme.OutloopTheme

enum class InsightsRange(val label: String) {
    TODAY("Day"),
    SEVEN_DAYS("Week"),
    THIRTY_DAYS("Month")
}

@Composable
fun InsightsScreen(
    uiState: MainUiState,
    bottomContentPadding: Dp = OutloopMetrics.BottomNavContentPadding,
    onStatusClick: () -> Unit = {}
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
    val chartSubtitle = when (selectedRange) {
        InsightsRange.TODAY -> "Time kept today"
        InsightsRange.SEVEN_DAYS -> "Time kept this week"
        InsightsRange.THIRTY_DAYS -> "Time kept this month"
    }
    val deltaPillText = when (selectedRange) {
        InsightsRange.SEVEN_DAYS -> uiState.weeklyDeltaText
        else -> null
    }
    val byApp = when (selectedRange) {
        InsightsRange.TODAY -> uiState.todayByAppBreakdown
        InsightsRange.SEVEN_DAYS -> uiState.byAppBreakdown
        InsightsRange.THIRTY_DAYS -> uiState.thirtyDayByAppBreakdown
    }

    val statusPill = when (uiState.protectionStatus) {
        ProtectionStatus.OPERATIONAL -> "Guard on" to PillTone.SUCCESS
        ProtectionStatus.PAUSED -> "Guard paused" to PillTone.WARNING
        ProtectionStatus.QUIET_HOURS -> "Quiet hours" to PillTone.NEUTRAL
        ProtectionStatus.NO_APPS_ACTIVE -> "No apps guarded" to PillTone.NEUTRAL
        ProtectionStatus.PERMISSION_NEEDED -> "Setup needed" to PillTone.WARNING
        ProtectionStatus.SERVICE_DISCONNECTED -> "Needs attention" to PillTone.WARNING
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = OutloopMetrics.MaxContentWidth),
            contentPadding = PaddingValues(
                start = OutloopMetrics.ScreenHorizontalPadding,
                end = OutloopMetrics.ScreenHorizontalPadding,
                top = OutloopMetrics.ScreenTopPadding,
                bottom = bottomContentPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutloopBrandTopBar(
                    title = "Insights",
                    statusText = statusPill.first,
                    statusTone = statusPill.second,
                    onStatusClick = onStatusClick,
                    statusTestTag = "insights_status_pill"
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

            // Dark hero chart card ("Time kept this week", big metric, chunky capsule bars)
            item {
                OutloopStaggeredEntrance(index = 0) {
                    OutloopInsightsDarkChartCard(
                        subtitle = chartSubtitle,
                        durationText = reclaimedText,
                        deltaPillText = deltaPillText,
                        columns = columns,
                        selectedIndex = selectedBarIndex,
                        onSelectIndex = { selectedBarIndex = it }
                    )
                }
            }

            // Twin Mint ("loops broken") + Peach ("best streak") Bento Tiles
            item {
                OutloopStaggeredEntrance(index = 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutloopBentoStatTile(
                            value = totalCount.toString(),
                            label = "loops broken",
                            containerColor = colors.mintTile,
                            modifier = Modifier.weight(1f)
                        )
                        val bestStreakVal = uiState.bestStreakDays
                        val bestStreakText = if (bestStreakVal == 1) "1 day" else "$bestStreakVal days"
                        OutloopBentoStatTile(
                            value = bestStreakText,
                            label = "best streak",
                            containerColor = colors.peachTile,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Mint tile: measured time in feeds today (closes the estimation gap)
            item {
                OutloopStaggeredEntrance(index = 3) {
                    val feedMinutes = uiState.totalTodayFeedSeconds.toLong() / 60
                    val feedText = when {
                        feedMinutes <= 0 -> "0m"
                        feedMinutes < 60 -> "${feedMinutes}m"
                        else -> "${feedMinutes / 60}h ${feedMinutes % 60}m"
                    }
                    OutloopBentoStatTile(
                        value = feedText,
                        label = "time in feeds today",
                        containerColor = colors.mintTile
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
