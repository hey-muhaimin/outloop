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
 * Shared 20%-compact spatial & sizing tokens used across every Outloop screen
 * so padding, corners, button heights, badge sizes, and title positioning stay consistent.
 */
object OutloopMetrics {
    val ScreenHorizontalPadding = 16.dp
    val ScreenTopPadding = 16.dp
    val TopBarTopPadding = 10.dp
    val TopBarStartPadding = 10.dp
    val SectionSpacing = 11.dp
    val ItemSpacing = 8.dp
    val CardCornerRadius = 20.dp
    val SubCardCornerRadius = 18.dp
    val SheetCornerRadius = 28.dp
    val CardPadding = PaddingValues(16.dp)
    val CompactCardPadding = PaddingValues(13.dp)
    val ListRowPaddingHorizontal = 14.dp
    val ListRowPaddingVertical = 8.dp
    val RowMinHeight = 56.dp
    val NavRowMinHeight = 48.dp
    val ButtonMinHeight = 48.dp
    val AppBadgeSize = 40.dp
    val AppBadgeCornerRadius = 12.dp
    val AppDetailBadgeSize = 46.dp
    val AppDetailBadgeCornerRadius = 14.dp
    val IconBadgeSize = 34.dp
    val IconBadgeCornerRadius = 10.dp
    val IconBadgeGlyphSize = 17.dp
    val HeroRingSize = 86.dp
    val RhythmCircleSize = 30.dp
    val DockHeight = 56.dp
    val DockBottomPadding = 12.dp
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
    appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
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
