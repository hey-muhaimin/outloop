package com.example.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        selectedIcon = Icons.Outlined.Home,
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
 * Full-width warm linen bottom navigation bar with mint capsule behind the active icon,
 * matching the Tonal Bento mockups and Material 3 NavigationBar ergonomics.
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.navBarBg)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        OutloopHairlineDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == selectedTab
                val capsuleColor by animateColorAsState(
                    targetValue = if (isSelected) colors.mintBg else Color.Transparent,
                    label = "navCapsuleColor"
                )
                val iconTint by animateColorAsState(
                    targetValue = if (isSelected) colors.mintInk else colors.secondaryInk,
                    label = "navIconTint"
                )
                val showDot = when (tab) {
                    BottomTab.HOME -> alerts.homeNeedsAttention
                    BottomTab.SETTINGS -> alerts.settingsNeedsAttention
                    BottomTab.INSIGHTS -> false
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!isSelected) haptic()
                            onTabSelected(tab)
                        }
                        .semantics {
                            selected = isSelected
                            contentDescription = tab.label
                        }
                        .testTag(tab.testTag),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 62.dp, height = 32.dp)
                            .clip(CircleShape)
                            .background(capsuleColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Box {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(21.dp)
                            )
                            if (showDot) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 5.dp, y = (-2).dp)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(colors.warnText)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = if (isSelected) colors.ink else colors.secondaryInk,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun ObsidianSideRail(
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
            .padding(start = 16.dp, top = 24.dp, bottom = 24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .width(92.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(colors.navBarBg)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(28.dp))
                .padding(horizontal = 8.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutloopMark(size = 24.dp, color = colors.ink)
            Spacer(modifier = Modifier.height(6.dp))

            BottomTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                val tint = if (isSelected) colors.mintInk else colors.secondaryInk
                val showDot = when (tab) {
                    BottomTab.HOME -> alerts.homeNeedsAttention
                    BottomTab.SETTINGS -> alerts.settingsNeedsAttention
                    BottomTab.INSIGHTS -> false
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (isSelected) colors.mintBg else Color.Transparent)
                        .clickable(role = Role.Tab) {
                            if (!isSelected) haptic()
                            onTabSelected(tab)
                        }
                        .semantics {
                            selected = isSelected
                            contentDescription = tab.label
                        }
                        .testTag(tab.testTag)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(21.dp)
                            )
                            if (showDot) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-2).dp)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(colors.warnText)
                                )
                            }
                        }
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = if (isSelected) colors.ink else colors.secondaryInk,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
