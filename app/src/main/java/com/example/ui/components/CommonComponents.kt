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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.ui.draw.shadow
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
 * Calm, uniform Outloop canvas without radial gradients or blur overlays.
 */
@Composable
fun OutloopCanvas(
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
 * Exact Outloop "One open loop" vector geometry sweeping counter-clockwise
 * with an open 36-degree gap at the upper right (-27 deg to -63 deg).
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

@Composable
fun OutloopWelcomeArtwork(
    modifier: Modifier = Modifier
) {
    val colors = OutloopTheme.colors
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height * 0.52f
        val maxRadius = (minOf(size.width, size.height) * 0.42f).coerceAtLeast(36f)

        val outerR1 = maxRadius
        val outerR2 = maxRadius * 0.76f
        val mainR = maxRadius * 0.51f
        val mainStrokePx = (mainR * 0.25f).coerceIn(10.dp.toPx(), 18.dp.toPx())

        drawArc(
            color = colors.welcomeOuterRing.copy(alpha = 0.16f),
            startAngle = -27f,
            sweepAngle = -324f,
            useCenter = false,
            topLeft = Offset(cx - outerR1, cy - outerR1),
            size = Size(outerR1 * 2f, outerR1 * 2f),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = colors.welcomeOuterRing.copy(alpha = 0.28f),
            startAngle = -27f,
            sweepAngle = -324f,
            useCenter = false,
            topLeft = Offset(cx - outerR2, cy - outerR2),
            size = Size(outerR2 * 2f, outerR2 * 2f),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = Color(0xFFFAF9F5),
            startAngle = -27f,
            sweepAngle = -324f,
            useCenter = false,
            topLeft = Offset(cx - mainR, cy - mainR),
            size = Size(mainR * 2f, mainR * 2f),
            style = Stroke(width = mainStrokePx, cap = StrokeCap.Round)
        )
    }
}

/**
 * Crisp tonal card surface with a 1dp hairline border and zero glass/blur effects.
 */
@Composable
fun OutloopTonalCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    elevated: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    containerColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = OutloopTheme.colors
    val shape = RoundedCornerShape(cornerRadius)
    val fill = containerColor ?: if (elevated) colors.surfaceElevated else colors.surfaceCard

    val cardModifier = modifier
        .clip(shape)
        .background(fill)
        .border(1.dp, colors.hairlineBorder, shape)
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
        StatusBannerTone.SUCCESS -> colors.okBg to colors.okText
        StatusBannerTone.WARNING -> colors.warnBg to colors.warnText
        StatusBannerTone.INFO -> colors.infoBg to colors.infoText
    }
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.14f), shape)
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
                    color = fg.copy(alpha = 0.92f)
                )
                if (action != null) {
                    Spacer(modifier = Modifier.height(8.dp))
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
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor ?: colors.primaryTint,
            contentColor = contentColor ?: colors.onPrimaryTint,
            disabledContainerColor = (containerColor ?: colors.primaryTint).copy(alpha = 0.45f),
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
        border = BorderStroke(1.dp, colors.hairlineBorder),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.surfaceElevated,
            contentColor = colors.ink
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
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
            .background(colors.surfaceCard)
            .border(1.dp, colors.hairlineBorder, CircleShape)
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
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "switchThumb"
    )

    Box(
        modifier = modifier
            .size(width = 51.dp, height = 48.dp)
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
                .size(width = 51.dp, height = 31.dp)
                .clip(CircleShape)
                .background(if (enabled) trackColor else colors.switchOff.copy(alpha = 0.55f))
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset, y = 2.dp)
                    .size(27.dp)
                    .shadow(1.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

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
            .clip(CircleShape)
            .background(colors.surfaceSubtle)
            .border(1.dp, colors.hairlineBorder, CircleShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .then(
                        if (isSelected) {
                            Modifier
                                .clip(CircleShape)
                                .background(colors.segmentThumb)
                                .border(1.dp, colors.hairlineBorder, CircleShape)
                        } else {
                            Modifier.clip(CircleShape)
                        }
                    )
                    .clickable {
                        if (!isSelected) {
                            haptic()
                            onSelect(value)
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) colors.ink else colors.secondaryInk,
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
        PillTone.NEUTRAL, PillTone.ACTIVE_DOT -> colors.surfaceCard to colors.ink
        PillTone.SUCCESS -> colors.okBg to colors.okText
        PillTone.WARNING -> colors.warnBg to colors.warnText
    }
    Row(
        modifier = modifier
            .heightIn(min = 32.dp)
            .clip(CircleShape)
            .background(bg)
            .then(
                if (tone == PillTone.NEUTRAL || tone == PillTone.ACTIVE_DOT) {
                    Modifier.border(1.dp, colors.hairlineBorder, CircleShape)
                } else {
                    Modifier.border(1.dp, fg.copy(alpha = 0.14f), CircleShape)
                }
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (tone == PillTone.ACTIVE_DOT) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.switchOn)
            )
        } else if (leadingIcon != null) {
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
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
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
    size: Dp = 40.dp,
    cornerRadius: Dp = 12.dp,
    tint: Color? = null
) {
    val colors = OutloopTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(colors.surfaceSubtle)
            .border(1.dp, colors.hairlineBorder, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint ?: colors.primaryTint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun OutloopAppMonogramBadge(
    appName: String,
    modifier: Modifier = Modifier,
    packageName: String? = null,
    size: Dp = 48.dp,
    cornerRadius: Dp = 15.dp
) {
    val colors = OutloopTheme.colors
    val context = LocalContext.current
    val appIcon: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = if (!packageName.isNullOrBlank()) {
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
            .background(colors.surfaceSubtle)
            .border(1.dp, colors.hairlineBorder, shape),
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
                fontSize = (size.value * 0.37f).sp,
                fontWeight = FontWeight.Bold,
                color = colors.primaryTint
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
        StatusBannerTone.SUCCESS -> colors.okBg to colors.okText
        StatusBannerTone.WARNING -> colors.warnBg to colors.warnText
        StatusBannerTone.INFO -> colors.infoBg to colors.infoText
        null -> colors.surfaceSubtle to colors.primaryTint
    }
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (tone == null) Modifier.border(1.dp, colors.hairlineBorder, CircleShape) else Modifier),
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
                style = MaterialTheme.typography.labelLarge,
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
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.surfaceElevated else colors.surfaceCard)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.primaryTint else colors.hairlineBorder,
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
                            .background(colors.recommendedChipBg)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primaryTint
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
    valueText: String? = null,
    titleColor: Color? = null,
    trailingBadge: (@Composable RowScope.() -> Unit)? = null
) {
    val colors = OutloopTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
            color = titleColor ?: colors.ink,
            modifier = Modifier.weight(1f)
        )
        if (trailingBadge != null) {
            trailingBadge()
        } else if (valueText != null) {
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

@Composable
fun OutloopToastPill(
    message: String,
    modifier: Modifier = Modifier
) {
    val colors = OutloopTheme.colors
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.hairlineBorder),
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.ink,
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
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.hairlineBorder),
        shadowElevation = 4.dp,
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
            OutloopMark(size = 18.dp, color = colors.switchOn)
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
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
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
