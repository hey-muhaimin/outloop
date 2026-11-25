package com.example.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import com.example.data.local.AppearanceMode

val LocalOutloopColors = staticCompositionLocalOf { LightOutloopColors }
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/**
 * Shared Light Tonal Bento spacing, corner radii, and component tokens.
 */
object OutloopMetrics {
    val ScreenHorizontalPadding = 20.dp
    val ScreenTopPadding = 12.dp
    val TopBarTopPadding = 10.dp
    val TopBarStartPadding = 10.dp
    val SectionSpacing = 14.dp
    val ItemSpacing = 8.dp
    val BentoGap = 10.dp
    val CardCornerRadius = 22.dp
    val HeroCornerRadius = 22.dp
    val SubCardCornerRadius = 16.dp
    val WellCornerRadius = 16.dp
    val SheetCornerRadius = 28.dp
    val ButtonCornerRadius = 16.dp
    val NavCornerRadius = 22.dp
    val CardPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)
    val CompactCardPadding = PaddingValues(16.dp)
    val WellPadding = PaddingValues(14.dp)
    val ListRowPaddingHorizontal = 18.dp
    val ListRowPaddingVertical = 12.dp
    val RowMinHeight = 56.dp
    val NavRowMinHeight = 52.dp
    val ButtonMinHeight = 52.dp
    val AppBadgeSize = 36.dp
    val AppBadgeCornerRadius = 12.dp
    val AppDetailBadgeSize = 48.dp
    val AppDetailBadgeCornerRadius = 14.dp
    val IconBadgeSize = 36.dp
    val IconBadgeCornerRadius = 12.dp
    val IconBadgeGlyphSize = 18.dp
    val HeroRingSize = 86.dp
    val RhythmCircleSize = 36.dp
    val DockHeight = 64.dp
    val DockBottomPadding = 12.dp
    val BottomNavContentPadding = 104.dp
    val SideRailWidth = 80.dp
    val MaxContentWidth = 580.dp
}

object OutloopTheme {
    val colors: OutloopColors
        @Composable
        @ReadOnlyComposable
        get() = LocalOutloopColors.current
}

private val OutloopShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun isDarkThemeForAppearance(@Suppress("UNUSED_PARAMETER") appearanceMode: AppearanceMode): Boolean = false

@Composable
fun FeedSurgeonTheme(
    @Suppress("UNUSED_PARAMETER") appearanceMode: AppearanceMode = AppearanceMode.LIGHT,
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val outloopColors = LightOutloopColors

    val materialScheme = lightColorScheme(
        primary = outloopColors.primaryTint,
        onPrimary = outloopColors.onPrimaryTint,
        secondary = outloopColors.textAction,
        onSecondary = outloopColors.onPrimaryTint,
        background = outloopColors.canvas,
        onBackground = outloopColors.ink,
        surface = outloopColors.sheetSurface,
        onSurface = outloopColors.ink,
        surfaceVariant = outloopColors.surfaceElevated,
        onSurfaceVariant = outloopColors.secondaryInk,
        outline = outloopColors.strokeStrong,
        outlineVariant = outloopColors.hairlineBorder,
        error = outloopColors.destructive
    )

    CompositionLocalProvider(
        LocalOutloopColors provides outloopColors,
        LocalHapticsEnabled provides hapticsEnabled
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = OutloopTypography,
            shapes = OutloopShapes,
            content = content
        )
    }
}
