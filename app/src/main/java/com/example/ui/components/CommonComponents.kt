package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.service.InstalledAppDetector
import com.example.ui.theme.LocalHapticsEnabled
import com.example.ui.theme.OutloopMetrics
import com.example.ui.theme.OutloopTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

fun Modifier.outloopPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "outloopPressScale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun OutloopStaggeredEntrance(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val delayMs = (index.coerceIn(0, 6) * 28L)
        if (delayMs > 0L) delay(delayMs)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.86f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
    Box(
        modifier = modifier.graphicsLayer {
            val v = progress.value
            alpha = v.coerceIn(0.05f, 1f)
            translationY = (1f - v) * 14f
        }
    ) {
        content()
    }
}

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
 * Light Tonal Bento primary brand bar: Outloop mark + bold wordmark on left,
 * status pill on right, followed by bold 31sp screen title.
 */
@Composable
fun OutloopBrandTopBar(
    title: String,
    statusText: String,
    statusTone: PillTone,
    onStatusClick: (() -> Unit)? = null,
    statusTestTag: String = "status_pill",
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutloopMark(
                    size = 22.dp,
                    color = colors.ink,
                    strokeWidth = 3.2.dp
                )
                Text(
                    text = "Outloop",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.ink
                )
            }
            OutloopStatusPill(
                text = statusText,
                tone = statusTone,
                onClick = onStatusClick,
                modifier = Modifier.testTag(statusTestTag)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.displayLarge,
            color = colors.ink
        )
    }
}

@Composable
fun OutloopTabHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = OutloopTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (!eyebrow.isNullOrBlank()) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelMedium,
                color = colors.secondaryInk
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displayLarge,
                color = colors.ink
            )
            if (trailing != null) {
                trailing()
            }
        }
    }
}

@Composable
fun OutloopBentoStatTile(
    value: String,
    label: String,
    containerColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = OutloopTheme.colors
    val shape = RoundedCornerShape(22.dp)
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.outloopPressScale(interactionSource, 0.98f) else Modifier)
            .clip(shape)
            .background(containerColor)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 25.sp,
                lineHeight = 30.sp,
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

@Composable
fun OutloopRoundedCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    val shape = RoundedCornerShape(8.dp)
    val bg by animateColorAsState(
        targetValue = if (checked) colors.heroDark else Color.Transparent,
        label = "roundedCheckboxBg"
    )
    val borderCol by animateColorAsState(
        targetValue = if (checked) colors.heroDark else colors.strokeStrong,
        label = "roundedCheckboxBorder"
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                this.stateDescription = if (checked) "Selected" else "Not selected"
            }
            .clickable(
                enabled = enabled,
                role = Role.Checkbox,
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
                .size(26.dp)
                .clip(shape)
                .background(bg)
                .border(1.75.dp, borderCol, shape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color(0xFFFAF8F2),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun OutloopSubScreenScaffold(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = OutloopTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = OutloopMetrics.MaxContentWidth)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = OutloopMetrics.TopBarStartPadding,
                        end = OutloopMetrics.ScreenHorizontalPadding,
                        top = OutloopMetrics.TopBarTopPadding
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutloopBackButton(onClick = onBack)
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.ink
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = OutloopMetrics.ScreenHorizontalPadding,
                        vertical = OutloopMetrics.SectionSpacing
                    ),
                verticalArrangement = Arrangement.spacedBy(OutloopMetrics.SectionSpacing),
                content = content
            )

            if (bottomBar != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = OutloopMetrics.ScreenHorizontalPadding,
                            vertical = 12.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(OutloopMetrics.ItemSpacing),
                    content = bottomBar
                )
            }
        }
    }
}

/**
 * Exact Outloop "One open loop" vector geometry sweeping counter-clockwise
 * with an open gap at the upper right (-27 deg to -63 deg).
 */
@Composable
fun OutloopMark(
    size: Dp = 22.dp,
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
    val infiniteTransition = rememberInfiniteTransition(label = "welcomeHeroMotion")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.965f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "welcomePulseScale"
    )
    val swayDegrees by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "welcomeSwayDegrees"
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height * 0.52f
        val maxRadius = (minOf(size.width, size.height) * 0.40f * pulseScale).coerceAtLeast(32f)

        val outerR1 = maxRadius
        val outerR2 = maxRadius * 0.76f
        val mainR = maxRadius * 0.51f
        val mainStrokePx = (mainR * 0.25f).coerceIn(8.dp.toPx(), 15.dp.toPx())

        rotate(degrees = swayDegrees, pivot = Offset(cx, cy)) {
            drawArc(
                color = colors.sageAccent.copy(alpha = 0.20f),
                startAngle = -27f,
                sweepAngle = -324f,
                useCenter = false,
                topLeft = Offset(cx - outerR1, cy - outerR1),
                size = Size(outerR1 * 2f, outerR1 * 2f),
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        rotate(degrees = -swayDegrees * 0.7f, pivot = Offset(cx, cy)) {
            drawArc(
                color = colors.sageAccent.copy(alpha = 0.34f),
                startAngle = -27f,
                sweepAngle = -324f,
                useCenter = false,
                topLeft = Offset(cx - outerR2, cy - outerR2),
                size = Size(outerR2 * 2f, outerR2 * 2f),
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        drawArc(
            color = colors.sageAccent,
            startAngle = -27f,
            sweepAngle = -324f,
            useCenter = false,
            topLeft = Offset(cx - mainR, cy - mainR),
            size = Size(mainR * 2f, mainR * 2f),
            style = Stroke(width = mainStrokePx, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun OutloopTonalCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = OutloopMetrics.CardCornerRadius,
    elevated: Boolean = false,
    contentPadding: PaddingValues = OutloopMetrics.CardPadding,
    containerColor: Color? = null,
    borderColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = OutloopTheme.colors
    val shape = RoundedCornerShape(cornerRadius)
    val fill = containerColor ?: if (elevated) colors.surfaceElevated else colors.surfaceCard
    val stroke = borderColor ?: colors.hairlineBorder
    val interactionSource = remember { MutableInteractionSource() }

    val cardModifier = modifier
        .then(if (onClick != null) Modifier.outloopPressScale(interactionSource, 0.985f) else Modifier)
        .clip(shape)
        .background(fill)
        .border(1.dp, stroke, shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick
                )
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
        StatusBannerTone.SUCCESS -> colors.mintTile to colors.ink
        StatusBannerTone.WARNING -> colors.peachTile to colors.ink
        StatusBannerTone.INFO -> colors.infoBg to colors.ink
    }
    val shape = RoundedCornerShape(OutloopMetrics.SubCardCornerRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .padding(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
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
                    color = colors.secondaryInk
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
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = {
            haptic()
            onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor ?: colors.heroDark,
            contentColor = contentColor ?: Color(0xFFFAF8F2),
            disabledContainerColor = (containerColor ?: colors.heroDark).copy(alpha = 0.45f),
            disabledContentColor = (contentColor ?: Color(0xFFFAF8F2)).copy(alpha = 0.72f)
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = modifier
            .outloopPressScale(interactionSource)
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
fun OutloopSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color? = null
) {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = {
            haptic()
            onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        shape = CircleShape,
        border = BorderStroke(1.dp, colors.hairlineBorder),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor ?: colors.surfaceCard,
            contentColor = colors.ink
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = modifier
            .outloopPressScale(interactionSource)
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
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier.heightIn(min = OutloopMetrics.ButtonMinHeight)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = color ?: colors.secondaryInk
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
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .outloopPressScale(interactionSource, 0.94f)
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) {
                haptic()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(colors.surfaceCard)
                .border(1.dp, colors.hairlineBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = contentDescription,
                tint = colors.ink,
                modifier = Modifier.size(22.dp)
            )
        }
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
        targetValue = if (checked) 24.dp else 4.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "switchThumb"
    )

    Box(
        modifier = modifier
            .size(width = 54.dp, height = 48.dp)
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
                .size(width = 52.dp, height = 31.dp)
                .clip(CircleShape)
                .background(if (enabled) trackColor else colors.switchOff.copy(alpha = 0.55f))
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset, y = 3.5.dp)
                    .size(24.dp)
                    .shadow(1.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

/**
 * Light Tonal Bento pill bar (used on Insights "Day | Week | Month", Pause Guard durations, etc.)
 * featuring Mint #D2E4CE filled active segment inside a bordered ivory pill container.
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
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(colors.surfaceCard)
            .border(1.dp, colors.strokeStrong.copy(alpha = 0.45f), CircleShape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val pillBg by animateColorAsState(
                targetValue = if (isSelected) colors.mintTile else Color.Transparent,
                animationSpec = tween(durationMillis = 180),
                label = "segmentPillBg"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .clip(CircleShape)
                    .background(pillBg)
                    .clickable {
                        if (!isSelected) {
                            haptic()
                            onSelect(value)
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = colors.ink,
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
    val interactionSource = remember { MutableInteractionSource() }
    val (bg, fg) = when (tone) {
        PillTone.NEUTRAL -> colors.surfaceSubtle to colors.secondaryInk
        PillTone.SUCCESS, PillTone.ACTIVE_DOT -> colors.mintTile to colors.ink
        PillTone.WARNING -> colors.peachTile to colors.ink
    }
    Row(
        modifier = modifier
            .then(if (onClick != null) Modifier.outloopPressScale(interactionSource, 0.96f) else Modifier)
            .heightIn(min = if (onClick != null) 34.dp else 30.dp)
            .clip(CircleShape)
            .background(bg)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
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
    containerColor: Color = OutloopTheme.colors.mintTile,
    tint: Color? = null
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
            tint = tint ?: colors.ink,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun OutloopAppMonogramBadge(
    appName: String,
    modifier: Modifier = Modifier,
    packageName: String? = null,
    size: Dp = OutloopMetrics.AppBadgeSize,
    cornerRadius: Dp = OutloopMetrics.AppBadgeCornerRadius,
    preferDarkMonogram: Boolean = false
) {
    val colors = OutloopTheme.colors
    val context = LocalContext.current
    val appIcon: ImageBitmap? by produceState<ImageBitmap?>(
        initialValue = null,
        packageName,
        preferDarkMonogram
    ) {
        value = if (!preferDarkMonogram && !packageName.isNullOrBlank()) {
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
            .background(if (preferDarkMonogram) colors.heroDark else colors.heroDark),
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
                color = Color(0xFFFAF8F2)
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
        StatusBannerTone.SUCCESS -> colors.mintTile to colors.ink
        StatusBannerTone.WARNING -> colors.peachTile to colors.ink
        StatusBannerTone.INFO -> colors.infoBg to colors.ink
        null -> colors.mintTile to colors.ink
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
                modifier = Modifier.size(15.dp)
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
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(OutloopMetrics.SubCardCornerRadius)
    val bgColor by animateColorAsState(
        targetValue = if (selected) colors.surfaceElevated else colors.surfaceCard,
        animationSpec = tween(180),
        label = "radioCardBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) colors.heroDark else colors.hairlineBorder,
        animationSpec = tween(180),
        label = "radioCardBorder"
    )

    Row(
        modifier = modifier
            .outloopPressScale(interactionSource, 0.985f)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(bgColor)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = shape
            )
            .clickable(
                role = Role.RadioButton,
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) {
                haptic()
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (selected) colors.heroDark else colors.strokeStrong,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            val dotScale by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "radioDotScale"
            )
            if (dotScale > 0.05f) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .graphicsLayer {
                            scaleX = dotScale
                            scaleY = dotScale
                        }
                        .clip(CircleShape)
                        .background(colors.heroDark)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                            .background(colors.mintTile)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.ink
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
            .heightIn(min = OutloopMetrics.NavRowMinHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = OutloopMetrics.ListRowPaddingHorizontal, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            color = titleColor ?: colors.ink,
            modifier = Modifier.weight(1f)
        )
        if (trailingBadge != null) {
            trailingBadge()
        } else if (valueText != null) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
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
        shape = CircleShape,
        color = colors.heroDark,
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OutloopMetrics.ScreenHorizontalPadding)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = Color(0xFFFAF8F2),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
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
        shape = RoundedCornerShape(OutloopMetrics.SubCardCornerRadius),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.hairlineBorder),
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OutloopMetrics.ScreenHorizontalPadding, vertical = 6.dp)
            .testTag("intervention_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutloopMark(size = 16.dp, color = colors.heroDark)
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
                    color = colors.heroDark
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
        shape = RoundedCornerShape(topStart = OutloopMetrics.SheetCornerRadius, topEnd = OutloopMetrics.SheetCornerRadius),
        containerColor = colors.sheetSurface,
        contentColor = colors.ink,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(colors.strokeStrong)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = OutloopMetrics.ScreenHorizontalPadding, end = OutloopMetrics.ScreenHorizontalPadding, bottom = 24.dp),
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

/**
 * In-app Pause Guard dialog matching C · Overlay Pause Guard.png:
 * when strictModeEnabled is true, enforces a 10-second circular reflection countdown
 * before unlocking the "Pause for X min" action; otherwise allows immediate duration selection.
 */
@Composable
fun OutloopPauseGuardDialog(
    strictModeEnabled: Boolean,
    initialMinutes: Int,
    onConfirmPause: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = OutloopTheme.colors
    val haptic = rememberOutloopHaptic()
    var selectedMinutes by remember {
        mutableIntStateOf(if (initialMinutes == 5 || initialMinutes == 15 || initialMinutes == 30) initialMinutes else 15)
    }
    var secondsLeft by remember(strictModeEnabled) {
        mutableIntStateOf(if (strictModeEnabled) 10 else 0)
    }

    LaunchedEffect(strictModeEnabled) {
        if (strictModeEnabled) {
            while (secondsLeft > 0) {
                delay(1000L)
                secondsLeft -= 1
            }
        }
    }

    val ringProgress by animateFloatAsState(
        targetValue = if (strictModeEnabled) (secondsLeft / 10f) else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "pauseGuardRingProgress"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC141C16))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .testTag("pause_guard_dialog")
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.surfaceCard)
                    .border(1.dp, colors.hairlineBorder, RoundedCornerShape(28.dp))
                    .verticalScroll(rememberScrollState())
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Dark hero countdown box matching OutloopSimulationOverlay hero proportions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.heroDark)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.heroChipBg)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (strictModeEnabled) "Strict mode" else "Pause Guard",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFFFAF8F2)
                            )
                        }
                        Text(
                            text = "Pause Guard?",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 24.sp,
                                lineHeight = 28.sp
                            ),
                            color = Color(0xFFFAF8F2)
                        )
                    }

                    Box(
                        modifier = Modifier.size(84.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokePx = 8.dp.toPx()
                            val inset = strokePx / 2f + 2.dp.toPx()
                            val d = (size.minDimension - inset * 2f).coerceAtLeast(1f)
                            drawArc(
                                color = colors.heroTrack,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = Size(d, d),
                                style = Stroke(width = strokePx, cap = StrokeCap.Round)
                            )
                            if (ringProgress > 0f) {
                                drawArc(
                                    color = colors.sageAccent,
                                    startAngle = -90f,
                                    sweepAngle = 360f * ringProgress,
                                    useCenter = false,
                                    topLeft = Offset(inset, inset),
                                    size = Size(d, d),
                                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (strictModeEnabled) secondsLeft.toString() else "$selectedMinutes",
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = 26.sp,
                                    lineHeight = 28.sp,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = Color(0xFFFAF8F2)
                            )
                            Text(
                                text = if (strictModeEnabled) {
                                    if (secondsLeft == 1) "sec" else "secs"
                                } else {
                                    "min"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFAF8F2).copy(alpha = 0.80f)
                            )
                        }
                    }
                }

                Text(
                    text = if (strictModeEnabled) {
                        "Strict mode asks you to wait 10 seconds first. Still want a break?"
                    } else {
                        "Choose how long to pause feed blocking across your guarded apps."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryInk
                )

                OutloopSegmentedControl(
                    options = listOf(5 to "5 min", 15 to "15 min", 30 to "30 min"),
                    selected = selectedMinutes,
                    onSelect = { selectedMinutes = it },
                    modifier = Modifier.testTag("pause_guard_duration_selector")
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutloopPrimaryButton(
                        text = "Keep guarding",
                        onClick = onDismiss,
                        modifier = Modifier.testTag("pause_guard_keep_button")
                    )

                    val canPause = secondsLeft <= 0
                    val pauseInteraction = remember { MutableInteractionSource() }
                    Button(
                        onClick = {
                            if (canPause) {
                                haptic()
                                onConfirmPause(selectedMinutes)
                            }
                        },
                        enabled = canPause,
                        interactionSource = pauseInteraction,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, colors.strokeStrong.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canPause) colors.mintTile else colors.surfaceCard,
                            contentColor = colors.ink,
                            disabledContainerColor = colors.surfaceCard,
                            disabledContentColor = colors.secondaryInk
                        ),
                        modifier = Modifier
                            .outloopPressScale(pauseInteraction)
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("pause_guard_confirm_button")
                    ) {
                        Text(
                            text = if (secondsLeft > 0) "Pause in $secondsLeft s" else "Pause for $selectedMinutes min",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}
