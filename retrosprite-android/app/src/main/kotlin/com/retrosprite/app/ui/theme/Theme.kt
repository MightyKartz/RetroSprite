package com.retrosprite.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val RetroDarkColors = darkColorScheme(
    primary = RetroPrimary,
    onPrimary = RetroOnPrimary,
    primaryContainer = RetroPrimaryContainer,
    onPrimaryContainer = RetroOnPrimaryContainer,
    secondary = RetroSecondary,
    onSecondary = RetroOnSecondary,
    secondaryContainer = RetroSecondaryContainer,
    onSecondaryContainer = RetroOnSecondaryContainer,
    tertiary = RetroTertiary,
    onTertiary = RetroOnTertiary,
    tertiaryContainer = RetroTertiaryContainer,
    onTertiaryContainer = RetroOnTertiaryContainer,
    error = RetroError,
    onError = RetroOnError,
    errorContainer = RetroErrorContainer,
    onErrorContainer = RetroOnErrorContainer,
    background = RetroInk,
    onBackground = RetroOnSurface,
    surface = RetroSurface,
    onSurface = RetroOnSurface,
    surfaceVariant = RetroSurfaceGlass,
    onSurfaceVariant = RetroOnSurfaceVariant,
    surfaceTint = RetroPrimary,
    inverseSurface = RetroOnSurface,
    inverseOnSurface = RetroInk,
    outline = RetroOutline,
    outlineVariant = RetroOutlineVariant
)

/**
 * RetroSprite is a dark-first handheld-style app. We deliberately ignore the system
 * light/dark setting and dynamic color so the brand identity stays intact. If the user
 * really insists on light mode in the future, we can add a switch in Settings.
 */
@Composable
fun RetroSpriteTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = RetroDarkColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RetroTypography,
        shapes = RetroShapes,
        content = content
    )
}
