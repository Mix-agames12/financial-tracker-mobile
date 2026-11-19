package com.mixagames666.trackerrntemp.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp

val LocalColors = staticCompositionLocalOf { DarkColors }
val LocalIsDark = staticCompositionLocalOf { true }

/** Atajos para leer los tokens de color vigentes (equivale a `useTheme().colors`). */
object AppTheme {
    val colors: ThemeColors
        @Composable @ReadOnlyComposable get() = LocalColors.current

    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalIsDark.current
}

/**
 * Tema de la app: los tokens de colors.ts se exponen tal cual (LocalColors) y además se proyectan sobre
 * el ColorScheme de Material 3 para que los componentes del sistema (diálogos, selector de fecha) coincidan.
 */
@Composable
fun AppTheme(isDark: Boolean, content: @Composable () -> Unit) {
    val c = if (isDark) DarkColors else LightColors
    val scheme = if (isDark) {
        darkColorScheme(
            primary = c.primary, onPrimary = c.onPrimary, primaryContainer = c.primaryContainer, onPrimaryContainer = c.onPrimaryContainer,
            secondary = c.secondary, onSecondary = c.onSecondary, secondaryContainer = c.secondaryContainer, onSecondaryContainer = c.onSecondaryContainer,
            tertiary = c.tertiary, onTertiary = c.onTertiary, tertiaryContainer = c.tertiaryContainer, onTertiaryContainer = c.onSurface,
            error = c.error, onError = c.onError, errorContainer = c.errorContainer, onErrorContainer = c.error,
            background = c.background, onBackground = c.onSurface,
            surface = c.surface, onSurface = c.onSurface, surfaceVariant = c.surfaceContainerHighest, onSurfaceVariant = c.onSurfaceVariant,
            outline = c.outline, outlineVariant = c.outlineVariant,
            surfaceDim = c.surfaceDim, surfaceBright = c.surfaceBright,
            surfaceContainerLowest = c.surfaceContainerLowest, surfaceContainerLow = c.surfaceContainerLow,
            surfaceContainer = c.surfaceContainer, surfaceContainerHigh = c.surfaceContainerHigh,
            surfaceContainerHighest = c.surfaceContainerHighest,
        )
    } else {
        lightColorScheme(
            primary = c.primary, onPrimary = c.onPrimary, primaryContainer = c.primaryContainer, onPrimaryContainer = c.onPrimaryContainer,
            secondary = c.secondary, onSecondary = c.onSecondary, secondaryContainer = c.secondaryContainer, onSecondaryContainer = c.onSecondaryContainer,
            tertiary = c.tertiary, onTertiary = c.onTertiary, tertiaryContainer = c.tertiaryContainer, onTertiaryContainer = c.onSurface,
            error = c.error, onError = c.onError, errorContainer = c.errorContainer, onErrorContainer = c.error,
            background = c.background, onBackground = c.onSurface,
            surface = c.surface, onSurface = c.onSurface, surfaceVariant = c.surfaceContainerHighest, onSurfaceVariant = c.onSurfaceVariant,
            outline = c.outline, outlineVariant = c.outlineVariant,
            surfaceDim = c.surfaceDim, surfaceBright = c.surfaceBright,
            surfaceContainerLowest = c.surfaceContainerLowest, surfaceContainerLow = c.surfaceContainerLow,
            surfaceContainer = c.surfaceContainer, surfaceContainerHigh = c.surfaceContainerHigh,
            surfaceContainerHighest = c.surfaceContainerHighest,
            inverseSurface = c.inverseSurface ?: c.surface,
            inverseOnSurface = c.inverseOnSurface ?: c.onSurface,
        )
    }
    CompositionLocalProvider(LocalColors provides c, LocalIsDark provides isDark) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Equivalente a `useSafeAreaInsets().top`: barra de estado + recorte de pantalla. */
@Composable
fun topInset(): Dp = WindowInsets.statusBars.union(WindowInsets.displayCutout).asPaddingValues().calculateTopPadding()

/** Equivalente a `useSafeAreaInsets().bottom`: barra de navegación. */
@Composable
fun bottomInset(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

@Composable
fun systemInsets(): PaddingValues = WindowInsets.statusBars.union(WindowInsets.displayCutout).union(WindowInsets.navigationBars).asPaddingValues()
