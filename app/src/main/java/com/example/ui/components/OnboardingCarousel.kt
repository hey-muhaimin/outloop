package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutloopMetrics
import com.example.ui.theme.OutloopTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val ONBOARDING_CAROUSEL_PAGE_COUNT = 4

private enum class InspectableTarget(
    val label: String,
    val viewIdTag: String,
    val badgeText: String,
    val isBlockedFeed: Boolean,
    val explanation: String
) {
    REELS_PAGER(
        label = "Reels / Shorts feed",
        viewIdTag = "viewId: ...:id/reels_pager · ViewPager2",
        badgeText = "Paused by Guard",
        isBlockedFeed = true,
        explanation = "Matched full-screen vertical feed tag. Outloop steps in immediately."
    ),
    DM_INBOX(
        label = "Direct messages",
        viewIdTag = "viewId: ...:id/direct_inbox · Exclusion",
        badgeText = "Always open",
        isBlockedFeed = false,
        explanation = "Excluded chat tag. Messages and threads are never blocked or read."
    ),
    SEARCH_BAR(
        label = "Search bar",
        viewIdTag = "viewId: ...:id/search_edit · Utility",
        badgeText = "Always open",
        isBlockedFeed = false,
        explanation = "Intentional search action. Outloop leaves search completely untouched."
    ),
    HOME_TAB(
        label = "Home tab",
        viewIdTag = "label: \"Home\" · Navigation tab",
        badgeText = "Safe destination",
        isBlockedFeed = false,
        explanation = "Target Home tab node. Outloop can click here to exit a feed cleanly."
    )
}

private enum class StrictDemoPhase {
    IDLE,
    COUNTING,
    UNLOCKED,
    PAUSED_PREVIEW
}

private enum class DockPreviewOutcome {
    ON_FEED,
    AT_HOME_TAB,
    EXITED_APP
}

private enum class PrivacyAuditTab {
    INSPECTED,
    NEVER_READ
}

@Composable
fun AccessibilityOnboardingCarousel(
    pagerState: PagerState,
    preferredPauseMinutes: Int,
    isReplayMode: Boolean,
    onOpenPrivacyDetails: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("onboarding_carousel_pager")
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = OutloopMetrics.ScreenHorizontalPadding,
                        vertical = OutloopMetrics.SectionSpacing
                    ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (page) {
                    0 -> DetectionInspectorSlide()
                    1 -> StrictOverlaySlide(preferredPauseMinutes = preferredPauseMinutes)
                    2 -> HomeExitDockSlide()
                    else -> PrivacyBoundarySlide(onOpenPrivacyDetails = onOpenPrivacyDetails)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = OutloopMetrics.ScreenHorizontalPadding,
                    vertical = 12.dp
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Interactive pill-dot slide indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("carousel_page_indicator"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (index in 0 until ONBOARDING_CAROUSEL_PAGE_COUNT) {
                    val isSelected = pagerState.currentPage == index
                    val dotWidth by animateDpAsState(
                        targetValue = if (isSelected) 24.dp else 8.dp,
                        animationSpec = tween(220),
                        label = "carouselDotWidth_$index"
                    )
                    val dotColor by animateColorAsState(
                        targetValue = if (isSelected) colors.heroDark else colors.switchOff,
                        animationSpec = tween(220),
                        label = "carouselDotColor_$index"
                    )
                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (!isSelected) {
                                    haptic()
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                }
                            }
                            .padding(horizontal = 5.dp)
                            .semantics {
                                contentDescription = "Slide ${index + 1} of $ONBOARDING_CAROUSEL_PAGE_COUNT"
                            }
                            .testTag("carousel_dot_$index"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(dotWidth)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }
            }

            val currentPage = pagerState.currentPage
            val isLastPage = currentPage >= ONBOARDING_CAROUSEL_PAGE_COUNT - 1

            if (currentPage == 0) {
                OutloopPrimaryButton(
                    text = "Next",
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(1) }
                    },
                    modifier = Modifier.testTag("carousel_next_button")
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(0.36f)) {
                        OutloopSecondaryButton(
                            text = "Back",
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage((currentPage - 1).coerceAtLeast(0))
                                }
                            },
                            modifier = Modifier.testTag("carousel_back_button")
                        )
                    }
                    Box(modifier = Modifier.weight(0.64f)) {
                        if (isLastPage) {
                            OutloopPrimaryButton(
                                text = if (isReplayMode) "Done" else "Get started",
                                onClick = onFinish,
                                modifier = Modifier.testTag(
                                    if (isReplayMode) "carousel_replay_done" else "onboarding_get_started"
                                )
                            )
                        } else {
                            OutloopPrimaryButton(
                                text = "Next",
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(
                                            (currentPage + 1).coerceAtMost(ONBOARDING_CAROUSEL_PAGE_COUNT - 1)
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("carousel_next_button")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetectionInspectorSlide() {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    var selectedTarget by rememberSaveable { mutableStateOf(InspectableTarget.REELS_PAGER) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutloopStatusPill(
            text = "Step 1 \u00B7 Feed detection",
            tone = PillTone.NEUTRAL
        )
        Text(
            text = "Spot feeds.\nSkip the rest.",
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 31.sp,
                lineHeight = 35.sp
            ),
            color = colors.ink
        )
        Text(
            text = "Tap any area in the app preview below to see which Accessibility labels Outloop checks and which stay untouched.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.secondaryInk
        )
    }

    OutloopTonalCard(
        cornerRadius = OutloopMetrics.CardCornerRadius,
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.testTag("onboarding_preview_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutloopMark(
                    size = 18.dp,
                    color = colors.heroDark,
                    strokeWidth = 2.8.dp
                )
                Text(
                    text = "Tap to inspect view tags",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.ink
                )
            }
            OutloopStatusPill(
                text = "On-device",
                tone = PillTone.SUCCESS
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Mini phone screen mockup with 4 tappable elements
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(OutloopMetrics.SubCardCornerRadius))
                .background(colors.canvas)
                .border(1.dp, colors.hairlineBorder, RoundedCornerShape(OutloopMetrics.SubCardCornerRadius))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InspectableZoneChip(
                    label = "Search",
                    icon = Icons.Rounded.Search,
                    selected = selectedTarget == InspectableTarget.SEARCH_BAR,
                    onClick = {
                        haptic()
                        selectedTarget = InspectableTarget.SEARCH_BAR
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("carousel_inspect_search_bar")
                )
                InspectableZoneChip(
                    label = "Messages",
                    icon = null,
                    selected = selectedTarget == InspectableTarget.DM_INBOX,
                    onClick = {
                        haptic()
                        selectedTarget = InspectableTarget.DM_INBOX
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("carousel_inspect_dm_inbox")
                )
            }

            // Full-bleed vertical feed target
            val feedSelected = selectedTarget == InspectableTarget.REELS_PAGER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 84.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.heroDark)
                    .border(
                        width = if (feedSelected) 2.dp else 1.dp,
                        color = if (feedSelected) colors.sageAccent else colors.heroTrack,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable {
                        haptic()
                        selectedTarget = InspectableTarget.REELS_PAGER
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("carousel_inspect_reels_pager"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Reels / Shorts feed",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onPrimaryTint
                    )
                    Text(
                        text = "Autoplay video pager",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.sageAccent
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.heroChipBg)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "reels_pager",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = colors.onPrimaryTint
                    )
                }
            }

            // Bottom navigation inside app wireframe
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InspectableZoneChip(
                    label = "Home tab",
                    icon = Icons.Rounded.Home,
                    selected = selectedTarget == InspectableTarget.HOME_TAB,
                    onClick = {
                        haptic()
                        selectedTarget = InspectableTarget.HOME_TAB
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("carousel_inspect_home_tab")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Live Accessibility Tag Inspector output
        OutloopInsetWell(
            containerColor = colors.surfaceElevated,
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier
                .semantics(mergeDescendants = true) {}
                .testTag("carousel_tag_inspector")
        ) {
            AnimatedContent(
                targetState = selectedTarget,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(150)) },
                label = "tagInspectorContent"
            ) { target ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = target.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.ink
                        )
                        OutloopStatusPill(
                            text = target.badgeText,
                            tone = if (target.isBlockedFeed) PillTone.WARNING else PillTone.SUCCESS
                        )
                    }
                    Text(
                        text = target.viewIdTag,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = colors.secondaryInk
                    )
                    Text(
                        text = target.explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink
                    )
                }
            }
        }
    }
}

@Composable
private fun InspectableZoneChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OutloopTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) colors.surfaceElevated else colors.surfaceSubtle)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.heroDark else colors.hairlineBorder,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.ink,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StrictOverlaySlide(preferredPauseMinutes: Int) {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    var demoPhase by rememberSaveable { mutableStateOf(StrictDemoPhase.IDLE) }
    var secondsLeft by rememberSaveable { mutableIntStateOf(10) }
    var selectedMinutes by rememberSaveable {
        mutableIntStateOf(if (preferredPauseMinutes in listOf(5, 15, 30)) preferredPauseMinutes else 15)
    }

    LaunchedEffect(demoPhase) {
        if (demoPhase == StrictDemoPhase.COUNTING) {
            while (secondsLeft > 0) {
                delay(1_000L)
                secondsLeft = (secondsLeft - 1).coerceAtLeast(0)
            }
            demoPhase = StrictDemoPhase.UNLOCKED
        }
    }

    val ringProgress by animateFloatAsState(
        targetValue = when (demoPhase) {
            StrictDemoPhase.IDLE -> 1f
            StrictDemoPhase.COUNTING -> secondsLeft / 10f
            StrictDemoPhase.UNLOCKED, StrictDemoPhase.PAUSED_PREVIEW -> 0f
        },
        animationSpec = tween(220),
        label = "strictDemoRing"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutloopStatusPill(
            text = "Step 2 \u00B7 Pause Guard",
            tone = PillTone.NEUTRAL
        )
        Text(
            text = "Break the loop\nwith Strict mode.",
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 31.sp,
                lineHeight = 35.sp
            ),
            color = colors.ink
        )
        Text(
            text = "When a feed opens, Outloop dims the reel and asks you to wait 10 seconds before unlocking a timed break.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.secondaryInk
        )
    }

    OutloopTonalCard(
        cornerRadius = OutloopMetrics.CardCornerRadius,
        contentPadding = PaddingValues(16.dp)
    ) {
        // Dark hero ring preview matching OutloopPauseGuardDialog
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.heroDark)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.heroChipBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when (demoPhase) {
                            StrictDemoPhase.PAUSED_PREVIEW -> "Break active"
                            StrictDemoPhase.UNLOCKED -> "Ready to pause"
                            else -> "Strict mode"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onPrimaryTint
                    )
                }
                Text(
                    text = when (demoPhase) {
                        StrictDemoPhase.PAUSED_PREVIEW -> "Paused $selectedMinutes min"
                        else -> "Pause Guard?"
                    },
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 22.sp,
                        lineHeight = 26.sp
                    ),
                    color = colors.onPrimaryTint
                )
            }

            Box(
                modifier = Modifier.size(76.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = 7.dp.toPx()
                    val inset = strokePx / 2f + 2.dp.toPx()
                    val diameter = (size.minDimension - inset * 2f).coerceAtLeast(1f)
                    drawArc(
                        color = colors.heroTrack,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(diameter, diameter),
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                    if (ringProgress > 0f) {
                        drawArc(
                            color = colors.sageAccent,
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
                        text = when (demoPhase) {
                            StrictDemoPhase.PAUSED_PREVIEW -> "$selectedMinutes"
                            else -> secondsLeft.toString()
                        },
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 24.sp,
                            lineHeight = 26.sp,
                            fontFeatureSettings = "tnum"
                        ),
                        color = colors.onPrimaryTint,
                        modifier = Modifier.testTag("carousel_countdown_number")
                    )
                    Text(
                        text = if (demoPhase == StrictDemoPhase.PAUSED_PREVIEW) {
                            "min"
                        } else if (secondsLeft == 1) {
                            "sec"
                        } else {
                            "secs"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.sageAccent
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutloopSegmentedControl(
            options = listOf(5 to "5 min", 15 to "15 min", 30 to "30 min"),
            selected = selectedMinutes,
            onSelect = { selectedMinutes = it },
            modifier = Modifier.testTag("carousel_duration_selector")
        )

        Spacer(modifier = Modifier.height(12.dp))

        when (demoPhase) {
            StrictDemoPhase.IDLE -> {
                OutloopPrimaryButton(
                    text = "Preview 10s Strict timer",
                    onClick = {
                        secondsLeft = 10
                        demoPhase = StrictDemoPhase.COUNTING
                    },
                    modifier = Modifier.testTag("carousel_try_pause_button")
                )
            }

            StrictDemoPhase.COUNTING -> {
                Button(
                    onClick = {},
                    enabled = false,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, colors.hairlineBorder),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = colors.surfaceCard,
                        disabledContentColor = colors.secondaryInk
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("carousel_preview_break_button")
                ) {
                    Text(
                        text = "Pause in $secondsLeft s",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutloopSecondaryButton(
                            text = "Reset",
                            onClick = {
                                secondsLeft = 10
                                demoPhase = StrictDemoPhase.IDLE
                            },
                            modifier = Modifier.testTag("carousel_countdown_reset")
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        OutloopSecondaryButton(
                            text = "Fast-forward",
                            onClick = {
                                secondsLeft = 0
                                demoPhase = StrictDemoPhase.UNLOCKED
                            },
                            modifier = Modifier.testTag("carousel_countdown_fast_forward")
                        )
                    }
                }
            }

            StrictDemoPhase.UNLOCKED -> {
                OutloopPrimaryButton(
                    text = "Pause for $selectedMinutes min",
                    onClick = {
                        demoPhase = StrictDemoPhase.PAUSED_PREVIEW
                    },
                    modifier = Modifier.testTag("carousel_preview_break_button")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutloopSecondaryButton(
                    text = "Reset preview",
                    onClick = {
                        haptic()
                        secondsLeft = 10
                        demoPhase = StrictDemoPhase.IDLE
                    },
                    modifier = Modifier.testTag("carousel_countdown_reset")
                )
            }

            StrictDemoPhase.PAUSED_PREVIEW -> {
                OutloopStatusBannerCard(
                    tone = StatusBannerTone.SUCCESS,
                    icon = Icons.Rounded.Check,
                    title = "Temporary break simulated",
                    body = "Guard automatically resumes after $selectedMinutes minutes (or whenever you tap Resume)."
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutloopSecondaryButton(
                    text = "Reset preview",
                    onClick = {
                        secondsLeft = 10
                        demoPhase = StrictDemoPhase.IDLE
                    },
                    modifier = Modifier.testTag("carousel_countdown_reset")
                )
            }
        }
    }
}

@Composable
private fun HomeExitDockSlide() {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    var outcome by rememberSaveable { mutableStateOf(DockPreviewOutcome.ON_FEED) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutloopStatusPill(
            text = "Step 3 \u00B7 Clean exit",
            tone = PillTone.NEUTRAL
        )
        Text(
            text = "Leave the feed,\nkeep the app.",
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 31.sp,
                lineHeight = 35.sp
            ),
            color = colors.ink
        )
        Text(
            text = "Tap Home or Exit on Outloop\u2019s action dock to see how Accessibility steps you back out of autoplay feeds.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.secondaryInk
        )
    }

    OutloopTonalCard(
        cornerRadius = OutloopMetrics.CardCornerRadius,
        contentPadding = PaddingValues(16.dp)
    ) {
        AnimatedContent(
            targetState = outcome,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(160)) },
            label = "exitDockPreview",
            modifier = Modifier.testTag("carousel_exit_preview")
        ) { currentOutcome ->
            when (currentOutcome) {
                DockPreviewOutcome.ON_FEED -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.heroDark)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutloopStatusPill(
                            text = "Overlay active · Feed dimmed",
                            tone = PillTone.WARNING
                        )
                        Text(
                            text = "Loop broken",
                            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 22.sp),
                            color = colors.onPrimaryTint
                        )
                        Text(
                            text = "Short-form video playback is blocked behind the dim shield.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.sageAccent
                        )
                    }
                }

                DockPreviewOutcome.AT_HOME_TAB -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.surfaceElevated)
                            .border(1.dp, colors.hairlineBorder, RoundedCornerShape(18.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutloopStatusPill(
                            text = "Returned to Home tab",
                            tone = PillTone.SUCCESS
                        )
                        Text(
                            text = "Subscriptions & Search open",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.ink
                        )
                        Text(
                            text = "Action: Outloop tapped the app\u2019s Home tab node via Accessibility (no force-close).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryInk
                        )
                    }
                }

                DockPreviewOutcome.EXITED_APP -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.canvas)
                            .border(1.dp, colors.hairlineBorder, RoundedCornerShape(18.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutloopStatusPill(
                            text = "Exited to phone Home",
                            tone = PillTone.NEUTRAL
                        )
                        Text(
                            text = "Stepped away cleanly",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.ink
                        )
                        Text(
                            text = "Action: Outloop dismissed the feed with Global Back / Home.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryInk
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Try the 2-row action dock",
            style = MaterialTheme.typography.labelMedium,
            color = colors.secondaryInk
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Row 1: Inline Home | Exit dock
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(colors.canvas)
                .border(1.dp, colors.hairlineBorder, CircleShape)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DockDemoButton(
                label = "Home",
                icon = Icons.Rounded.Home,
                selected = outcome == DockPreviewOutcome.AT_HOME_TAB,
                onClick = {
                    haptic()
                    outcome = DockPreviewOutcome.AT_HOME_TAB
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("carousel_dock_home")
            )
            DockDemoButton(
                label = "Exit",
                icon = Icons.AutoMirrored.Rounded.ExitToApp,
                selected = outcome == DockPreviewOutcome.EXITED_APP,
                onClick = {
                    haptic()
                    outcome = DockPreviewOutcome.EXITED_APP
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("carousel_dock_exit")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Row 2: Full-width reset / fallback control
        val isReset = outcome == DockPreviewOutcome.ON_FEED
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(CircleShape)
                .background(if (isReset) colors.surfaceSubtle else colors.heroDark)
                .clickable(enabled = !isReset) {
                    haptic()
                    outcome = DockPreviewOutcome.ON_FEED
                }
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("carousel_dock_reset"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Pause,
                contentDescription = null,
                tint = if (isReset) colors.secondaryInk else colors.onPrimaryTint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isReset) "Tap Home or Exit above" else "Reset blocked feed preview",
                style = MaterialTheme.typography.labelLarge,
                color = if (isReset) colors.secondaryInk else colors.onPrimaryTint
            )
        }
    }
}

@Composable
private fun DockDemoButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OutloopTheme.colors
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(if (selected) colors.surfaceElevated else Color.Transparent)
            .then(
                if (selected) Modifier.border(1.dp, colors.hairlineBorder, CircleShape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.ink,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
            ),
            color = colors.ink
        )
    }
}

@Composable
private fun PrivacyBoundarySlide(
    onOpenPrivacyDetails: () -> Unit
) {
    val colors = OutloopTheme.colors
    var activeTab by rememberSaveable { mutableStateOf(PrivacyAuditTab.INSPECTED) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutloopStatusPill(
            text = "Step 4 \u00B7 Strict zero-data",
            tone = PillTone.SUCCESS
        )
        Text(
            text = "Zero content read.\n100% on-device.",
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 31.sp,
                lineHeight = 35.sp
            ),
            color = colors.ink
        )
        Text(
            text = "Outloop inspects only structural layout tags inside apps you choose. Nothing personal is ever logged or uploaded.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.secondaryInk
        )
    }

    OutloopTonalCard(
        cornerRadius = OutloopMetrics.CardCornerRadius,
        contentPadding = PaddingValues(16.dp)
    ) {
        OutloopSegmentedControl(
            options = listOf(
                PrivacyAuditTab.INSPECTED to "Checked locally",
                PrivacyAuditTab.NEVER_READ to "Never read"
            ),
            selected = activeTab,
            onSelect = { activeTab = it },
            modifier = Modifier.testTag("carousel_privacy_switcher")
        )

        Spacer(modifier = Modifier.height(12.dp))

        AnimatedContent(
            targetState = activeTab,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(150)) },
            label = "privacyAuditContent"
        ) { tab ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tab == PrivacyAuditTab.INSPECTED) {
                    PrivacyBoundaryItem(
                        icon = Icons.Rounded.Check,
                        title = "Selected app package",
                        detail = "Only triggers inside apps you switch on",
                        isAllowed = true
                    )
                    PrivacyBoundaryItem(
                        icon = Icons.Rounded.Check,
                        title = "Feed container view IDs",
                        detail = "Structural tags like reels_pager or shorts_container",
                        isAllowed = true
                    )
                    PrivacyBoundaryItem(
                        icon = Icons.Rounded.Check,
                        title = "Home tab & local limits",
                        detail = "Navigation tab labels and on-device time totals",
                        isAllowed = true
                    )
                } else {
                    PrivacyBoundaryItem(
                        icon = Icons.Outlined.VisibilityOff,
                        title = "Messages & chats",
                        detail = "DM text, notifications, and replies are skipped",
                        isAllowed = false
                    )
                    PrivacyBoundaryItem(
                        icon = Icons.Rounded.Close,
                        title = "Passwords & keystrokes",
                        detail = "Input text and form fields are never inspected",
                        isAllowed = false
                    )
                    PrivacyBoundaryItem(
                        icon = Icons.Outlined.Lock,
                        title = "Videos & cloud logs",
                        detail = "0 bytes of screen activity ever leave your phone",
                        isAllowed = false
                    )
                }
            }
        }
    }

    OutloopInsetWell(
        containerColor = colors.surfaceSubtle,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        onClick = onOpenPrivacyDetails,
        modifier = Modifier.testTag("carousel_open_privacy")
    ) {
        Text(
            text = "Read the full Accessibility & Privacy architecture \u203A",
            style = MaterialTheme.typography.titleSmall,
            color = colors.ink
        )
    }
}

@Composable
private fun PrivacyBoundaryItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    isAllowed: Boolean
) {
    val colors = OutloopTheme.colors
    OutloopInsetWell(
        containerColor = colors.surfaceElevated,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isAllowed) colors.okBg else colors.surfaceSubtle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isAllowed) colors.okText else colors.secondaryInk,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.ink
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryInk
                )
            }
        }
    }
}
