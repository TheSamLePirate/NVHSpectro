package com.example.nvhspectro.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * The single, fixed dark colour scheme [U5, plan 4.3, decision D4].
 *
 * There is deliberately no light scheme and no dynamic (wallpaper) colour:
 *  - the instrument's main surface is a black, colour-mapped canvas — a light chrome around
 *    it changes the perceived contrast of the measurement;
 *  - wallpaper-derived colours would make the status LED, criticality badges and order
 *    traces device-dependent, which a measurement UI cannot accept;
 *  - the previous template scheme mixed a dark-designed layout (hard-coded white text) with
 *    a light Material surface, producing white-on-light text in light mode.
 *
 * Tokens and their contrast ratios are documented in `Color.kt`.
 */
private val NvhDarkColorScheme =
    darkColorScheme(
        primary = NvhPrimary,
        onPrimary = NvhOnPrimary,
        primaryContainer = NvhPrimaryContainer,
        onPrimaryContainer = NvhOnPrimaryContainer,
        secondary = NvhSecondary,
        onSecondary = NvhOnSecondary,
        secondaryContainer = NvhSecondaryContainer,
        onSecondaryContainer = NvhOnSecondaryContainer,
        tertiary = NvhTertiary,
        onTertiary = NvhOnTertiary,
        background = NvhBackground,
        onBackground = NvhOnSurface,
        surface = NvhSurface,
        onSurface = NvhOnSurface,
        surfaceVariant = NvhSurfaceVariant,
        onSurfaceVariant = NvhOnSurfaceVariant,
        surfaceContainerHigh = NvhSurfaceContainerHigh,
        outline = NvhOutline,
        outlineVariant = NvhOutline,
        error = NvhError,
        onError = NvhOnError,
        errorContainer = NvhErrorContainer,
        onErrorContainer = NvhOnErrorContainer,
        scrim = NvhCanvas,
    )

@Composable
fun NVHSpectroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NvhDarkColorScheme,
        typography = Typography,
        shapes = NvhShapes,
        content = content,
    )
}
