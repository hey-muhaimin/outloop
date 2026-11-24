package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
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
    val ScreenHorizontalPadding = 16.dp
    val ScreenTopPadding = 12.dp
    val TopBarTopPadding = 10.dp
    val TopBarStartPadding = 10.dp
    val SectionSpacing = 12.dp
    val ItemSpacing = 8.dp
    val CardCornerRadius = 24.dp
    val HeroCornerRadius = 26.dp
    val SubCardCornerRadius = 18.dp
    val SheetCornerRadius = 28.dp
    val CardPadding = PaddingValues(18.dp)
    val CompactCardPadding = PaddingValues(14.dp)
    val ListRowPaddingHorizontal = 16.dp
    val ListRowPaddingVertical = 10.dp
    val RowMinHeight = 56.dp
    val NavRowMinHeight = 52.dp
    val ButtonMinHeight = 50.dp
    val AppBadgeSize = 42.dp
    val AppBadgeCornerRadius = 14.dp
    val AppDetailBadgeSize = 48.dp
    val AppDetailBadgeCornerRadius = 14.dp
    val IconBadgeSize = 36.dp
    val IconBadgeCornerRadius = 12.dp
    val IconBadgeGlyphSize = 18.dp
    val HeroRingSize = 86.dp
    val RhythmCircleSize = 30.dp
    val DockHeight = 68.dp
    val DockBottomPadding = 0.dp
    val BottomNavContentPadding = 96.dp
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
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun isDarkThemeForAppearance(appearanceMode: AppearanceMode): Boolean = when (appearanceMode) {
    AppearanceMode.LIGHT -> false
    AppearanceMode.DARK -> true
    AppearanceMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun FeedSurgeonTheme(
    appearanceMode: AppearanceMode = AppearanceMode.LIGHT,
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    // Light Tonal Bento is the primary design system unless user explicitly selects Dark
    val darkTheme = isDarkThemeForAppearance(appearanceMode)
    val outloopColors = if (darkTheme) DarkOutloopColors else LightOutloopColors

    val materialScheme = if (darkTheme) {
        darkColorScheme(
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
    } else {
        lightColorScheme(
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
    }

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
