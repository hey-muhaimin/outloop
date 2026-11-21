package com.example.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.OutloopMetrics
import com.example.ui.theme.OutloopTheme

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

enum class BottomTab(
    val label: String,
    val testTag: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME(
        label = "Home",
        testTag = "nav_home",
        selectedIcon = Icons.Rounded.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    INSIGHTS(
        label = "Insights",
        testTag = "nav_insights",
        selectedIcon = Icons.Rounded.BarChart,
        unselectedIcon = Icons.Outlined.BarChart
    ),
    SETTINGS(
        label = "Settings",
        testTag = "nav_settings",
        selectedIcon = Icons.Rounded.Tune,
        unselectedIcon = Icons.Outlined.Tune
    )
}

@Immutable
data class NavAlertBadges(
    val homeNeedsAttention: Boolean = false,
    val settingsNeedsAttention: Boolean = false
)

@Composable
fun FloatingPillNavBar(
    selectedTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
    alerts: NavAlertBadges = NavAlertBadges()
) {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    val tabs = BottomTab.entries
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(
                start = OutloopMetrics.ScreenHorizontalPadding,
                end = OutloopMetrics.ScreenHorizontalPadding,
                bottom = OutloopMetrics.DockBottomPadding
            ),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(OutloopMetrics.DockHeight)
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = colors.dockShadow,
                    spotColor = colors.dockShadow
                )
                .clip(RoundedCornerShape(28.dp))
                .background(colors.surfaceCard)
                .border(1.dp, colors.hairlineBorder, RoundedCornerShape(28.dp))
                .padding(4.dp)
        ) {
            val pillWidth = maxWidth / tabs.size
            val pillOffset by animateDpAsState(
                targetValue = pillWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = 0.84f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "outloopDockPillOffset"
            )

            Box(
                modifier = Modifier
                    .offset(x = pillOffset)
                    .width(pillWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.surfaceSubtle)
                    .border(1.dp, colors.hairlineBorder, RoundedCornerShape(24.dp))
            )

            Row(modifier = Modifier.fillMaxSize()) {
                tabs.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val tint by animateColorAsState(
                        targetValue = if (isSelected) colors.primaryTint else colors.secondaryInk,
                        label = "dockTabTint"
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.08f else 0.96f,
                        animationSpec = spring(
                            dampingRatio = 0.72f,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "dockIconScale"
                    )
                    val showDot = when (tab) {
                        BottomTab.HOME -> alerts.homeNeedsAttention
                        BottomTab.SETTINGS -> alerts.settingsNeedsAttention
                        BottomTab.INSIGHTS -> false
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(
                                role = Role.Tab,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (!isSelected) {
                                    haptic()
                                }
                                onTabSelected(tab)
                            }
                            .semantics {
                                selected = isSelected
                                contentDescription = tab.label
                            }
                            .testTag(tab.testTag),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Box(
                                modifier = Modifier.graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                            ) {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(19.dp)
                                )
                                if (showDot) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 3.dp, y = (-2).dp)
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(colors.warnText)
                                    )
                                }
                            }
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                ),
                                color = tint,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OutloopSideRail(
    selectedTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
    alerts: NavAlertBadges = NavAlertBadges()
) {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    Box(
        modifier = modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 12.dp, top = 20.dp, bottom = 18.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .width(OutloopMetrics.SideRailWidth)
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = colors.dockShadow,
                    spotColor = colors.dockShadow
                )
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surfaceCard)
                .border(1.dp, colors.hairlineBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 6.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutloopMark(size = 20.dp, color = colors.primaryTint)
            Spacer(modifier = Modifier.height(4.dp))

            BottomTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                val tint by animateColorAsState(
                    targetValue = if (isSelected) colors.primaryTint else colors.secondaryInk,
                    label = "railTabTint"
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.08f else 0.96f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
                    label = "railIconScale"
                )
                val showDot = when (tab) {
                    BottomTab.HOME -> alerts.homeNeedsAttention
                    BottomTab.SETTINGS -> alerts.settingsNeedsAttention
                    BottomTab.INSIGHTS -> false
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isSelected) colors.surfaceSubtle else Color.Transparent)
                        .then(
                            if (isSelected) {
                                Modifier.border(1.dp, colors.hairlineBorder, RoundedCornerShape(18.dp))
                            } else {
                                Modifier
                            }
                        )
                        .clickable(role = Role.Tab) {
                            if (!isSelected) haptic()
                            onTabSelected(tab)
                        }
                        .semantics {
                            selected = isSelected
                            contentDescription = tab.label
                        }
                        .testTag(tab.testTag)
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(
                            modifier = Modifier.graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            }
                        ) {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(19.dp)
                            )
                            if (showDot) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 3.dp, y = (-2).dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(colors.warnText)
                                )
                            }
                        }
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = tint,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
