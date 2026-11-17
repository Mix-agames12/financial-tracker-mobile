package com.mixagames666.trackerrntemp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Port de src/theme/colors.ts (mismos tokens y valores).

@Immutable
data class ThemeColors(
    val primary: Color,
    val primaryLight: Color,
    val primaryDark: Color,
    val primaryContainer: Color,
    val onPrimary: Color,
    val onPrimaryContainer: Color,

    val secondary: Color,
    val secondaryLight: Color,
    val secondaryDark: Color,
    val secondaryContainer: Color,
    val onSecondary: Color,
    val onSecondaryContainer: Color,

    val tertiary: Color,
    val tertiaryLight: Color,
    val tertiaryContainer: Color,
    val onTertiary: Color,

    val error: Color,
    val errorLight: Color,
    val errorContainer: Color,
    val onError: Color,

    val background: Color,
    val surface: Color,
    val surfaceDim: Color,
    val surfaceBright: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,

    val inverseSurface: Color?,
    val inverseOnSurface: Color?,

    // Extra semantic
    val incomeCard: Color,
    val savingsCard: Color,
)

/** '#rrggbb' o '#rrggbbaa' (como en React Native: el alfa va al final) → Color. */
fun hexColor(hex: String): Color {
    val clean = hex.removePrefix("#")
    val value = clean.toLongOrNull(16) ?: return Color.Unspecified
    return when (clean.length) {
        6 -> Color(((value shr 16) and 0xFF).toInt(), ((value shr 8) and 0xFF).toInt(), (value and 0xFF).toInt())
        8 -> Color(
            ((value shr 24) and 0xFF).toInt(),
            ((value shr 16) and 0xFF).toInt(),
            ((value shr 8) and 0xFF).toInt(),
            (value and 0xFF).toInt(),
        )
        else -> Color.Unspecified
    }
}

/**
 * Los hex de 8 dígitos de React Native son RRGGBBAA: `color + '20'` aplica un alfa de 0x20.
 * Devuelve el color con ese alfa (0-255) sobre un '#rrggbb'.
 */
fun hexColorWithAlpha(hex: String, alphaByte: Int): Color = hexColor(hex).copy(alpha = alphaByte / 255f)

val DarkColors = ThemeColors(
    primary = Color(0xFF6366F1),
    primaryLight = Color(0xFF818CF8),
    primaryDark = Color(0xFF4F46E5),
    primaryContainer = Color(0xFFE0E7FF),
    onPrimary = Color(0xFFFFFFFF),
    onPrimaryContainer = Color(0xFF312E81), // '#312e81ff' (RRGGBBAA opaco)

    secondary = Color(0xFF10B981),
    secondaryLight = Color(0xFF34D399),
    secondaryDark = Color(0xFF059669),
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondary = Color(0xFFFFFFFF),
    onSecondaryContainer = Color(0xFF064E3B),

    tertiary = Color(0xFFF59E0B),
    tertiaryLight = Color(0xFFFBBF24),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiary = Color(0xFFFFFFFF),

    error = Color(0xFFF43F5E),
    errorLight = Color(0xFFFB7185),
    errorContainer = Color(0xFFFFE4E6),
    onError = Color(0xFFFFFFFF),

    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    surfaceDim = Color(0xFF0F172A),
    surfaceBright = Color(0xFF334155),
    surfaceContainerLowest = Color(0xFF0C1322),
    surfaceContainerLow = Color(0xFF1A2538),
    surfaceContainer = Color(0xFF1E293B),
    surfaceContainerHigh = Color(0xFF273548),
    surfaceContainerHighest = Color(0xFF334155),
    onSurface = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155),

    inverseSurface = null,
    inverseOnSurface = null,

    incomeCard = Color(0xFF10B981),
    savingsCard = Color(0xFFF59E0B),
)

val LightColors = DarkColors.copy(
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFF1F5F9),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1F5F9),
    surfaceContainerHighest = Color(0xFFE2E8F0),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    inverseSurface = Color(0xFF1E293B),
    inverseOnSurface = Color(0xFFF1F5F9),
)
