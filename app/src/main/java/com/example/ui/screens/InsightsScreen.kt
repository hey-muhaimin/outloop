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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ProtectionStatus
import com.example.ui.MainUiState
import com.example.ui.components.ChartColumnData
import com.example.ui.components.OutloopBentoCard
import com.example.ui.components.OutloopBentoHeader
import com.example.ui.components.OutloopBentoStatTile
import com.example.ui.components.OutloopInsightsBarChart
import com.example.ui.components.OutloopMark
import com.example.ui.components.OutloopSegmentedControl
import com.example.ui.components.PillTone
import com.example.ui.theme.OutloopTheme

enum class InsightsRange(val label: String) {
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month")
}

@Composable
fun InsightsScreen(
    uiState: MainUiState,
    onStatusClick: () -> Unit = {},
    bottomContentPadding: Dp = 116.dp
) {
    val colors = OutloopTheme.colors
    var selectedRange by rememberSaveable { mutableStateOf(InsightsRange.WEEK) }

    val statusPill = when (uiState.protectionStatus) {
        ProtectionStatus.OPERATIONAL -> "Guard on" to PillTone.SUCCESS
        ProtectionStatus.PAUSED -> "Paused" to PillTone.WARNING
        ProtectionStatus.QUIET_HOURS -> "Quiet hours" to PillTone.NEUTRAL
        ProtectionStatus.NO_APPS_ACTIVE -> "Guard off" to PillTone.NEUTRAL
        ProtectionStatus.PERMISSION_NEEDED -> "Setup needed" to PillTone.WARNING
        ProtectionStatus.SERVICE_DISCONNECTED -> "Needs attention" to PillTone.WARNING
    }

    val columns: List<ChartColumnData> = when (selectedRange) {
        InsightsRange.DAY -> uiState.todayHourBuckets.map {
            ChartColumnData(it.shortLabel, it.detailLabel, it.count, it.fraction)
        }
        InsightsRange.WEEK -> uiState.weeklyDayBars.map {
            ChartColumnData(it.dayLetter, "${it.dayName} (${it.dateLabel})", it.count, it.fraction)
        }
        InsightsRange.MONTH -> uiState.thirtyDayWeekBuckets.map {
            ChartColumnData(it.shortLabel, it.detailLabel, it.count, it.fraction)
        }
    }

    var selectedBarIndex by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(selectedRange, columns.size) {
        selectedBarIndex = (columns.size - 1).coerceAtLeast(0)
    }

    val totalCount = when (selectedRange) {
        InsightsRange.DAY -> uiState.todayCutsCount
        InsightsRange.WEEK -> uiState.weeklyCutsCount
        InsightsRange.MONTH -> uiState.thirtyDayCutsCount
    }
    val reclaimedText = when (selectedRange) {
        InsightsRange.DAY -> uiState.todayReclaimedText
        InsightsRange.WEEK -> uiState.weeklyReclaimedText
        InsightsRange.MONTH -> uiState.thirtyDayReclaimedText
    }
    val chartTitle = when (selectedRange) {
        InsightsRange.DAY -> "Time kept today"
        InsightsRange.WEEK -> "Time kept this week"
        InsightsRange.MONTH -> "Time kept this month"
    }
    val byApp = when (selectedRange) {
        InsightsRange.DAY -> uiState.todayByAppBreakdown
        InsightsRange.WEEK -> uiState.byAppBreakdown
        InsightsRange.MONTH -> uiState.thirtyDayByAppBreakdown
    }
    val bestStreak = uiState.bestStreakDays.coerceAtLeast(uiState.streakDays)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .testTag("insights_list"),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 18.dp,
                bottom = bottomContentPadding
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                OutloopBentoHeader(
                    title = "Insights",
                    statusText = statusPill.first,
                    statusTone = statusPill.second,
                    onStatusClick = onStatusClick
                )
            }

            item {
                OutloopSegmentedControl(
                    options = listOf(
                        InsightsRange.DAY to InsightsRange.DAY.label,
                        InsightsRange.WEEK to InsightsRange.WEEK.label,
                        InsightsRange.MONTH to InsightsRange.MONTH.label
                    ),
                    selected = selectedRange,
                    onSelect = { selectedRange = it },
                    modifier = Modifier.testTag("insights_range_selector")
                )
            }

            if (totalCount == 0) {
                item {
                    OutloopBentoCard(
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 36.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutloopMark(size = 56.dp, color = colors.primaryTint)
                            Text(
                                text = if (selectedRange == InsightsRange.DAY) {
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
                // Dark Forest Chart Hero Card
                item {
                    OutloopBentoCard(
                        cornerRadius = 28.dp,
                        containerColor = colors.heroSurface,
                        borderColor = null,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = chartTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onHeroSurface.copy(alpha = 0.78f)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = reclaimedText,
                                    fontSize = 40.sp,
                                    lineHeight = 46.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-1.2).sp,
                                    color = colors.onHeroSurface,
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        fontFeatureSettings = "tnum"
                                    )
                                )
                            }

                            val pillBadgeText = when {
                                selectedRange == InsightsRange.WEEK && !uiState.weeklyDeltaLabel.isNullOrBlank() ->
                                    uiState.weeklyDeltaLabel
                                selectedRange == InsightsRange.DAY ->
                                    "Peak: ${uiState.todayBusiestTimeLabel}"
                                else ->
                                    "$totalCount broken"
                            }

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(colors.heroTrack)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = pillBadgeText,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = colors.onHeroSurface
                                )
                            }
                        }

                        if (columns.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            OutloopInsightsBarChart(
                                columns = columns,
                                selectedIndex = selectedBarIndex,
                                onSelectIndex = { selectedBarIndex = it }
                            )
                        }
                    }
                }

                // Mint "loops broken" + Peach "best streak" 2-column Bento row
                item {
                    val streakLabel = "$bestStreak ${if (bestStreak == 1) "day" else "days"}"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutloopBentoStatTile(
                            value = totalCount.toString(),
                            label = "loops broken",
                            containerColor = colors.mintBg,
                            contentColor = colors.mintInk,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("insights_loops_tile")
                        )
                        OutloopBentoStatTile(
                            value = streakLabel,
                            label = "best streak",
                            containerColor = colors.peachBg,
                            contentColor = colors.peachInk,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("insights_streak_tile")
                        )
                    }
                }

                // "Where loops start" ranked progress bar breakdown
                item {
                    OutloopBentoCard(
                        cornerRadius = 24.dp,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        Text(
                            text = "Where loops start",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            color = colors.secondaryInk
                        )

                        val activeBreakdown = byApp
                            .filter { it.count > 0 || it.packageName in uiState.installedPackages }
                            .ifEmpty { byApp }

                        activeBreakdown.forEach { stat ->
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stat.label,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = colors.ink
                                )
                                Text(
                                    text = stat.count.toString(),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFeatureSettings = "tnum",
                                        fontWeight = FontWeight.SemiBold
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
                                    .background(colors.progressTrack)
                            ) {
                                if (stat.fraction > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(stat.fraction.coerceIn(0f, 1f))
                                            .height(10.dp)
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
