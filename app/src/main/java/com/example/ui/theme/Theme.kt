package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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

object OutloopTheme {
    val colors: OutloopColors
        @Composable
        @ReadOnlyComposable
        get() = LocalOutloopColors.current
}

private val OutloopShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
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
            surfaceVariant = outloopColors.glassElevated,
            onSurfaceVariant = outloopColors.secondaryInk,
            outline = outloopColors.strokeStrong,
            outlineVariant = outloopColors.divider,
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
            surfaceVariant = outloopColors.glassElevated,
            onSurfaceVariant = outloopColors.secondaryInk,
            outline = outloopColors.strokeStrong,
            outlineVariant = outloopColors.divider,
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
