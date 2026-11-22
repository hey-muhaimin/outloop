package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.InstalledAppDetector
import com.example.ui.theme.LocalHapticsEnabled
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.OutloopTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberOutloopHaptic(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(haptic, enabled) {
        {
            if (enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
}

/**
 * Calm uniform warm linen canvas for the Tonal Bento design system (zero glass or atmospheric blobs).
 */
@Composable
fun OutloopBentoCanvas(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = OutloopTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas),
        content = content
    )
}

/**
 * Shared top bar for Home, Insights, and Settings:
 * Open-loop mark + "Outloop" wordmark on left, dynamic guard status pill on right,
 * and display title ("Today", "Insights", "Settings") beneath.
 */
@Composable
fun OutloopBentoHeader(
    title: String,
    statusText: String,
    statusTone: PillTone,
    onStatusClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = OutloopTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutloopMark(
                    size = 24.dp,
                    color = colors.ink,
                    strokeWidth = 3.2.dp
                )
                Text(
                    text = "Outloop",
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    letterSpacing = (-0.3).sp,
                    color = colors.ink
                )
            }
            OutloopStatusPill(
                text = statusText,
                tone = statusTone,
                onClick = onStatusClick,
                modifier = Modifier.testTag("status_pill")
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.displayLarge,
            color = colors.ink
        )
    }
}

/**
 * Exact Outloop "One open loop" vector geometry matching -27 deg start, -324 deg sweep.
 */
@Composable
fun OutloopMark(
    size: Dp = 26.dp,
    color: Color = OutloopTheme.colors.primaryTint,
    strokeWidth: Dp? = null,
    modifier: Modifier = Modifier
) {
    val resolvedStroke = strokeWidth ?: (size * 0.145f)
    Canvas(modifier = modifier.size(size)) {
        val strokePx = resolvedStroke.toPx()
        val inset = strokePx / 2f + 1.dp.toPx()
        val diameter = (this.size.minDimension - inset * 2f).coerceAtLeast(1f)
        drawArc(
            color = color,
            startAngle = -27f,
            sweepAngle = -324f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(diameter, diameter),
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
    }
}

/**
 * Crisp matte Tonal Bento card with optional subtle hairline border and zero drop shadows.
 */
@Composable
fun OutloopBentoCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    containerColor: Color? = null,
    borderColor: Color? = OutloopTheme.colors.cardBorder,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = OutloopTheme.colors
    val shape = RoundedCornerShape(cornerRadius)
    val fill = containerColor ?: colors.cardSurface

    val cardModifier = modifier
        .clip(shape)
        .background(fill)
        .then(
            if (borderColor != null) {
                Modifier.border(1.dp, borderColor, shape)
            } else {
                Modifier
            }
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )

    Column(
        modifier = cardModifier.padding(contentPadding),
        content = content
    )
}

enum class StatusBannerTone {
    SUCCESS,
    WARNING,
    INFO
}

@Composable
fun OutloopStatusBannerCard(
    tone: StatusBannerTone,
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable ColumnScope.() -> Unit)? = null
) {
    val colors = OutloopTheme.colors
    val (bg, fg) = when (tone) {
        StatusBannerTone.SUCCESS -> colors.mintBg to colors.mintInk
        StatusBannerTone.WARNING -> colors.peachBg to colors.warnText
        StatusBannerTone.INFO -> colors.infoBg to colors.infoText
    }
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .padding(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = fg
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = fg.copy(alpha = 0.90f)
                )
                if (action != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    action()
                }
            }
        }
    }
}

@Composable
fun OutloopPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color? = null,
    contentColor: Color? = null
) {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    Button(
        onClick = {
            haptic()
            onClick()
        },
        enabled = enabled,
        shape = CircleShape,
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor ?: colors.primaryTint,
            contentColor = contentColor ?: colors.onPrimaryTint,
            disabledContainerColor = (containerColor ?: colors.primaryTint).copy(alpha = 0.40f),
            disabledContentColor = (contentColor ?: colors.onPrimaryTint).copy(alpha = 0.72f)
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun OutloopSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    Button(
        onClick = {
            haptic()
            onClick()
        },
        enabled = enabled,
        shape = CircleShape,
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        border = BorderStroke(1.dp, colors.strokeStrong),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.cardSurface,
            contentColor = colors.ink,
            disabledContainerColor = colors.cardSurface.copy(alpha = 0.7f),
            disabledContentColor = colors.secondaryInk
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun OutloopTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = OutloopTheme.colors.mintBg,
    contentColor: Color = OutloopTheme.colors.mintInk
) {
    val haptic = rememberOutloopHaptic()
    Button(
        onClick = {
            haptic()
            onClick()
        },
        enabled = enabled,
        shape = CircleShape,
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun OutloopTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    TextButton(
        onClick = {
            haptic()
            onClick()
        },
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = color ?: colors.textAction
        )
    }
}

@Composable
fun OutloopBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Back"
) {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(colors.cardSurface)
            .border(1.dp, colors.cardBorder, CircleShape)
            .clickable {
                haptic()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            contentDescription = contentDescription,
            tint = colors.ink,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
fun OutloopSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    val trackColor by animateColorAsState(
        targetValue = if (checked) colors.switchOn else colors.switchOff,
        label = "switchTrack"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 3.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "switchThumb"
    )

    Box(
        modifier = modifier
            .size(width = 52.dp, height = 48.dp)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                this.stateDescription = if (checked) "On" else "Off"
            }
            .clickable(
                enabled = enabled,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    haptic()
                    onCheckedChange(!checked)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 50.dp, height = 30.dp)
                .clip(CircleShape)
                .background(if (enabled) trackColor else colors.switchOff.copy(alpha = 0.55f))
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset, y = 3.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (checked) colors.switchOnThumb else Color.White)
            )
        }
    }
}

/**
 * Rounded-square checkbox matching the Choose Apps Tonal Bento mockup.
 */
@Composable
fun OutloopCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .size(48.dp)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                this.stateDescription = if (checked) "Selected" else "Not selected"
            }
            .clickable(
                role = Role.Checkbox,
                onClick = {
                    haptic()
                    onCheckedChange(!checked)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(shape)
                .background(if (checked) colors.primaryTint else colors.cardSurface)
                .then(
                    if (checked) Modifier
                    else Modifier.border(1.5.dp, colors.strokeStrong, shape)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = colors.onPrimaryTint,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Outlined capsule segmented control with mint active segment fill matching Insights & Pause Guard mockups.
 */
@Composable
fun <T> OutloopSegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(CircleShape)
            .background(colors.cardSurface)
            .border(1.dp, colors.strokeStrong, CircleShape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, (value, label) ->
            val isSelected = value == selected
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(colors.divider)
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (isSelected) colors.segmentThumb else Color.Transparent)
                    .clickable {
                        if (!isSelected) {
                            haptic()
                            onSelect(value)
                        }
                    }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isSelected) colors.mintInk else colors.ink,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

enum class PillTone {
    NEUTRAL,
    SUCCESS,
    WARNING,
    PEACH,
    ACTIVE_DOT
}

@Composable
fun OutloopStatusPill(
    text: String,
    tone: PillTone = PillTone.NEUTRAL,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = OutloopTheme.colors
    val (bg, fg) = when (tone) {
        PillTone.NEUTRAL -> colors.cardSurface to colors.ink
        PillTone.SUCCESS, PillTone.ACTIVE_DOT -> colors.mintBg to colors.mintInk
        PillTone.PEACH -> colors.peachBg to colors.peachInk
        PillTone.WARNING -> colors.warnBg to colors.warnText
    }
    Row(
        modifier = modifier
            .heightIn(min = 32.dp)
            .clip(CircleShape)
            .background(bg)
            .then(
                if (tone == PillTone.NEUTRAL) {
                    Modifier.border(1.dp, colors.cardBorder, CircleShape)
                } else {
                    Modifier
                }
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = fg
        )
    }
}

@Composable
fun OutloopHairlineDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = OutloopTheme.colors.divider
    )
}

@Composable
fun OutloopSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = OutloopTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.secondaryInk
        )
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun OutloopIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    cornerRadius: Dp = 13.dp,
    containerColor: Color = OutloopTheme.colors.mintBg,
    tint: Color? = OutloopTheme.colors.mintInk
) {
    val colors = OutloopTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint ?: colors.mintInk,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Dark-forest rounded-square monogram badge matching the Tonal Bento Home & Onboarding mockups.
 */
@Composable
fun OutloopAppMonogramBadge(
    appName: String,
    modifier: Modifier = Modifier,
    packageName: String? = null,
    size: Dp = 44.dp,
    cornerRadius: Dp = 13.dp,
    preferMonogram: Boolean = false
) {
    val colors = OutloopTheme.colors
    val context = LocalContext.current
    val appIcon: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, packageName, preferMonogram) {
        value = if (!preferMonogram && !packageName.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                InstalledAppDetector.loadCachedAppIcon(context, packageName)
            }
        } else {
            null
        }
    }

    val letter = appName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "A"
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(colors.badgeDarkBg),
        contentAlignment = Alignment.Center
    ) {
        if (appIcon != null) {
            Image(
                bitmap = appIcon!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
            )
        } else {
            Text(
                text = letter,
                fontSize = (size.value * 0.40f).sp,
                fontWeight = FontWeight.Bold,
                color = colors.badgeDarkInk
            )
        }
    }
}

@Composable
fun OutloopStepCircleBadge(
    label: String,
    modifier: Modifier = Modifier,
    tone: StatusBannerTone? = null,
    icon: ImageVector? = null
) {
    val colors = OutloopTheme.colors
    val (bg, fg) = when (tone) {
        StatusBannerTone.SUCCESS -> colors.mintBg to colors.mintInk
        StatusBannerTone.WARNING -> colors.peachBg to colors.warnText
        StatusBannerTone.INFO -> colors.infoBg to colors.infoText
        null -> colors.mintBg to colors.mintInk
    }
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = fg
            )
        }
    }
}

@Composable
fun OutloopRadioOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeText: String? = null
) {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.mintBg.copy(alpha = 0.55f) else colors.cardSurface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.primaryTint else colors.cardBorder,
                shape = shape
            )
            .clickable(role = Role.RadioButton) {
                haptic()
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (selected) colors.primaryTint else colors.strokeStrong,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(colors.primaryTint)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.ink
                )
                if (!badgeText.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.mintBg)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.mintInk
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryInk
            )
        }
    }
}

@Composable
fun OutloopListNavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    valueText: String? = null,
    titleColor: Color? = null,
    trailingBadge: (@Composable RowScope.() -> Unit)? = null
) {
    val colors = OutloopTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (leadingIcon != null) {
            OutloopIconBadge(icon = leadingIcon)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = titleColor ?: colors.ink
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryInk
                )
            }
        }
        if (trailingBadge != null) {
            trailingBadge()
        } else {
            if (valueText != null) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.secondaryInk
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.secondaryInk,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun OutloopToastPill(
    message: String,
    modifier: Modifier = Modifier
) {
    val colors = OutloopTheme.colors
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.heroSurface,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.onHeroSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
fun InterventionBanner(
    toast: com.example.data.model.InterventionToastState,
    onAllowOnce: () -> Unit,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = OutloopTheme.colors
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.cardSurfaceElevated,
        border = BorderStroke(1.dp, colors.strokeStrong),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("intervention_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutloopMark(size = 18.dp, color = colors.primaryTint)
            Text(
                text = toast.message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onAllowOnce,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("allow_once_action")
            ) {
                Text(
                    text = "Allow once",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primaryTint
                )
            }
            TextButton(
                onClick = onUndo,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("undo_action")
            ) {
                Text(
                    text = "Undo",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.secondaryInk
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutloopModalSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = OutloopTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = colors.sheetSurface,
        contentColor = colors.ink,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .size(width = 40.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(colors.strokeStrong)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.ink
            )
            content()
        }
    }
}
