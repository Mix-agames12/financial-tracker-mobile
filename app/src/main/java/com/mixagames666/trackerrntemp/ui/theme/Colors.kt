package com.mixagames666.trackerrntemp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// Port de src/theme/colors.ts (mismos tokens y valores), con los añadidos de accesibilidad que se indican abajo.

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

    // ---- Añadidos de UI (no existen en colors.ts) ----
    /** Borde de 1 dp de las tarjetas: las separa del fondo en ambos temas (en oscuro la sombra no se ve). */
    val cardBorder: Color,
    /** Extremo oscuro de los degradados de las tarjetas destacadas (texto blanco ≥ 5:1). */
    val primaryDeep: Color,
    val secondaryDeep: Color,
    /** Ámbar fuerte para rellenos con ícono blanco (≥ 3:1; el ámbar base sólo llega a 2,2:1). */
    val tertiaryDark: Color,
    /** Rojo fuerte para rellenos con texto blanco (botón de peligro: 4,7:1 frente a 3,7:1 del rojo base). */
    val errorStrong: Color,
    /** Texto sobre los contenedores pastel de error y aviso (≥ 4,5:1). */
    val onErrorContainer: Color,
    val onTertiaryContainer: Color,
) {
    // Variantes para TEXTO: mismo tono que el color base, ajustado (si hace falta) hasta alcanzar 4,5:1 sobre la
    // superficie más exigente (`surfaceContainerHighest`: la más clara en oscuro y la más oscura en claro). Los tonos
    // base se conservan para rellenos, barras, bordes e íconos.
    val primaryText: Color by lazy(LazyThreadSafetyMode.NONE) { primary.readableOn(surfaceContainerHighest) }
    val successText: Color by lazy(LazyThreadSafetyMode.NONE) { secondary.readableOn(surfaceContainerHighest) }
    val warningText: Color by lazy(LazyThreadSafetyMode.NONE) { tertiary.readableOn(surfaceContainerHighest) }
    val errorText: Color by lazy(LazyThreadSafetyMode.NONE) { error.readableOn(surfaceContainerHighest) }
}

/** Luminancia relativa WCAG de un color sRGB opaco. */
fun Color.relativeLuminance(): Double {
    fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
}

/** Relación de contraste WCAG (de 1 a 21) entre dos colores opacos. */
fun contrastRatio(a: Color, b: Color): Double {
    val la = a.relativeLuminance()
    val lb = b.relativeLuminance()
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

/**
 * Este color, ajustado si hace falta para que un texto suyo alcance [minRatio] sobre [background]: se acerca a
 * blanco (fondo oscuro) o a negro (fondo claro) en pasos del 4 %, conservando el tono. Si ya cumple, no cambia.
 */
fun Color.readableOn(background: Color, minRatio: Double = 4.5): Color {
    if (contrastRatio(this, background) >= minRatio) return this
    val target = if (background.relativeLuminance() < 0.5) Color.White else Color.Black
    var step = 0.04f
    while (step < 1f) {
        val candidate = lerp(this, target, step)
        if (contrastRatio(candidate, background) >= minRatio) return candidate
        step += 0.04f
    }
    return target
}

private val InkColor = Color(0xFF0F172A)

/** Texto (blanco o casi negro) que mejor se lee sobre un relleno de este color (p. ej. un chip de categoría). */
fun Color.onFill(): Color = if (contrastRatio(Color.White, this) >= contrastRatio(InkColor, this)) Color.White else InkColor

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
    // indigo-500 (#6366F1) oscurecido ~3 %: el texto blanco de los botones pasa de 4,47:1 a 4,70:1 (mínimo AA = 4,5:1).
    primary = Color(0xFF6063EA),
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
    // slate-400 (#94A3B8) aclarado: el texto secundario sólo daba 4,1:1 sobre las superficies más claras del tema oscuro.
    onSurfaceVariant = Color(0xFFA3B3C7),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155),

    inverseSurface = null,
    inverseOnSurface = null,

    incomeCard = Color(0xFF10B981),
    savingsCard = Color(0xFFF59E0B),

    cardBorder = Color(0x14FFFFFF), // blanco 8 %
    primaryDeep = Color(0xFF3730A3), // indigo-800
    secondaryDeep = Color(0xFF065F46), // emerald-800
    tertiaryDark = Color(0xFFD97706), // amber-600
    errorStrong = Color(0xFFE11D48), // rose-600
    onErrorContainer = Color(0xFF9F1239), // rose-800 sobre #FFE4E6
    onTertiaryContainer = Color(0xFF78350F), // amber-900 sobre #FEF3C7
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
    cardBorder = Color(0x120F172A), // slate-900 7 %
)
