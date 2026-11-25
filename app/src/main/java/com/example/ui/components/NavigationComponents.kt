package com.example.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.unit.sp
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

/**
 * Bio-Mint & Tonal Bento Floating Bottom Nav:
 * Pure White (#FFFFFF) 22dp-rounded card with 6dp inner padding,
 * sliding 16dp #EAECE9 active tab capsule, 18dp outline/filled icons,
 * and #141514 active vs #8E928E muted ink.
 */
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
    val navShape = RoundedCornerShape(OutloopMetrics.NavCornerRadius)
    val itemShape = RoundedCornerShape(OutloopMetrics.ButtonCornerRadius)
    val itemGap = 6.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.42f to colors.canvas.copy(alpha = 0.90f),
                        1f to colors.canvas
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(
                start = OutloopMetrics.ScreenHorizontalPadding,
                end = OutloopMetrics.ScreenHorizontalPadding,
                top = 6.dp,
                bottom = OutloopMetrics.DockBottomPadding
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .height(70.dp)
                .testTag("floating_segmented_nav")
                .shadow(
                    elevation = 6.dp,
                    shape = navShape,
                    ambientColor = colors.dockShadow,
                    spotColor = colors.dockShadow
                )
                .clip(navShape)
                .background(colors.surfaceCard)
                .border(1.dp, colors.hairlineBorder, navShape)
                .padding(6.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val totalGaps = itemGap * (tabs.size - 1)
                val tabWidth = ((maxWidth - totalGaps) / tabs.size).coerceAtLeast(1.dp)
                val indicatorOffset by animateDpAsState(
                    targetValue = (tabWidth + itemGap) * selectedIndex,
                    animationSpec = spring(
                        dampingRatio = 0.84f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "navSlidingPillOffset"
                )

                // Active tab #EAECE9 capsule
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .clip(itemShape)
                        .background(colors.surfaceSubtle)
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(itemGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { tab ->
                        val isSelected = tab == selectedTab
                        val tint by animateColorAsState(
                            targetValue = if (isSelected) colors.ink else colors.mutedInk,
                            label = "navTint"
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.04f else 0.98f,
                            animationSpec = spring(
                                dampingRatio = 0.74f,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "navIconScale"
                        )
                        val showDot = when (tab) {
                            BottomTab.HOME -> alerts.homeNeedsAttention
                            BottomTab.SETTINGS -> alerts.settingsNeedsAttention
                            BottomTab.INSIGHTS -> false
                        }
                        val interactionSource = remember { MutableInteractionSource() }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .outloopPressScale(interactionSource, pressedScale = 0.96f)
                                .clip(itemShape)
                                .clickable(
                                    role = Role.Tab,
                                    interactionSource = interactionSource,
                                    indication = LocalIndication.current
                                ) {
                                    if (!isSelected) haptic()
                                    onTabSelected(tab)
                                }
                                .semantics {
                                    selected = isSelected
                                    contentDescription = tab.label
                                }
                                .testTag(tab.testTag)
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier.graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                            ) {
                                Icon(
                                    imageVector = tab.unselectedIcon,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(18.dp)
                                )
                                if (showDot) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 3.dp, y = (-2).dp)
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(colors.destructive)
                                            .border(
                                                width = 1.5.dp,
                                                color = if (isSelected) colors.surfaceSubtle else colors.surfaceCard,
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = tint,
                                maxLines = 1,
                                softWrap = false
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
    val railShape = RoundedCornerShape(OutloopMetrics.NavCornerRadius)
    val itemShape = RoundedCornerShape(OutloopMetrics.ButtonCornerRadius)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 14.dp, top = 20.dp, bottom = 18.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .width(OutloopMetrics.SideRailWidth)
                .testTag("side_rail")
                .shadow(
                    elevation = 6.dp,
                    shape = railShape,
                    ambientColor = colors.dockShadow,
                    spotColor = colors.dockShadow
                )
                .clip(railShape)
                .background(colors.surfaceCard)
                .border(1.dp, colors.hairlineBorder, railShape)
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            OutloopMark(size = 20.dp, color = colors.heroDark)
            HorizontalDivider(
                modifier = Modifier.width(28.dp),
                thickness = 1.dp,
                color = colors.hairlineBorder
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BottomTab.entries.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val pillBg by animateColorAsState(
                        targetValue = if (isSelected) colors.surfaceSubtle else Color.Transparent,
                        label = "railPillBg"
                    )
                    val tint by animateColorAsState(
                        targetValue = if (isSelected) colors.ink else colors.mutedInk,
                        label = "railTabTint"
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.04f else 0.98f,
                        animationSpec = spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMedium),
                        label = "railIconScale"
                    )
                    val showDot = when (tab) {
                        BottomTab.HOME -> alerts.homeNeedsAttention
                        BottomTab.SETTINGS -> alerts.settingsNeedsAttention
                        BottomTab.INSIGHTS -> false
                    }
                    val interactionSource = remember { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .outloopPressScale(interactionSource, pressedScale = 0.96f)
                            .clip(itemShape)
                            .background(pillBg)
                            .clickable(
                                role = Role.Tab,
                                interactionSource = interactionSource,
                                indication = LocalIndication.current
                            ) {
                                if (!isSelected) haptic()
                                onTabSelected(tab)
                            }
                            .semantics {
                                selected = isSelected
                                contentDescription = tab.label
                            }
                            .testTag(tab.testTag)
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier.graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                            ) {
                                Icon(
                                    imageVector = tab.unselectedIcon,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(18.dp)
                                )
                                if (showDot) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 3.dp, y = (-2).dp)
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(colors.destructive)
                                            .border(
                                                width = 1.5.dp,
                                                color = if (isSelected) colors.surfaceSubtle else colors.surfaceCard,
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = tint,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}
