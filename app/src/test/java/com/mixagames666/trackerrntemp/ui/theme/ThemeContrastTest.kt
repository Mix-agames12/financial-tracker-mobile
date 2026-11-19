package com.mixagames666.trackerrntemp.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Accesibilidad de la paleta (WCAG 2.x): el texto normal necesita 4,5:1, los íconos y bordes 3:1. Cada tema se
 * comprueba por separado (no se infiere uno del otro) y sobre todas las superficies donde se dibuja texto.
 */
class ThemeContrastTest {
    private val themes = mapOf("oscuro" to DarkColors, "claro" to LightColors)

    private fun assertRatio(min: Double, a: Color, b: Color, what: String) {
        val ratio = contrastRatio(a, b)
        assertTrue("$what: $ratio < $min", ratio >= min)
    }

    @Test
    fun textTokensReadOnEverySurface() {
        for ((theme, c) in themes) {
            val surfaces = mapOf(
                "background" to c.background,
                "surface" to c.surface,
                "surfaceContainer" to c.surfaceContainer,
                "surfaceContainerHigh" to c.surfaceContainerHigh,
                "surfaceContainerHighest" to c.surfaceContainerHighest,
            )
            val tokens = mapOf(
                "primaryText" to c.primaryText,
                "successText" to c.successText,
                "warningText" to c.warningText,
                "errorText" to c.errorText,
                "onSurface" to c.onSurface,
                "onSurfaceVariant" to c.onSurfaceVariant,
            )
            for ((surfaceName, surface) in surfaces) {
                for ((tokenName, token) in tokens) assertRatio(4.5, token, surface, "$theme: $tokenName sobre $surfaceName")
            }
        }
    }

    @Test
    fun whiteTextOnFilledControlsIsReadable() {
        for ((theme, c) in themes) {
            assertRatio(4.5, c.onPrimary, c.primary, "$theme: texto sobre botón principal")
            assertRatio(4.5, c.onError, c.errorStrong, "$theme: texto sobre botón de peligro")
        }
    }

    @Test
    fun iconsOnFloatingButtonsReach3to1() {
        for ((theme, c) in themes) {
            assertRatio(3.0, Color.White, c.secondaryDark, "$theme: ícono sobre FAB de ingresos")
            assertRatio(3.0, Color.White, c.tertiaryDark, "$theme: ícono sobre FAB de préstamos")
            assertRatio(3.0, Color.White, c.error, "$theme: ícono sobre FAB de gastos")
        }
    }

    @Test
    fun textOnPastelContainersIsReadable() {
        for ((theme, c) in themes) {
            assertRatio(4.5, c.onErrorContainer, c.errorContainer, "$theme: aviso de error")
            assertRatio(4.5, c.onTertiaryContainer, c.tertiaryContainer, "$theme: aviso de precaución")
            assertRatio(4.5, c.onPrimaryContainer, c.primaryContainer, "$theme: aviso informativo")
            assertRatio(4.5, c.onSecondaryContainer, c.secondaryContainer, "$theme: aviso de éxito")
        }
    }

    @Test
    fun whiteTextOnGradientCardsIsReadable() {
        for ((theme, c) in themes) {
            assertRatio(7.0, Color.White, c.primaryDeep, "$theme: inicio del degradado índigo")
            assertRatio(5.0, Color.White, lerp(c.primaryDeep, c.primaryDark, 0.5f), "$theme: centro del degradado índigo")
            assertRatio(4.5, Color.White, lerp(c.secondaryDeep, c.secondaryDark, 0.5f), "$theme: centro del degradado verde")
        }
    }

    @Test
    fun readableOnKeepsGoodColorsAndFixesBadOnes() {
        val ink = Color(0xFF0F172A)
        assertEquals(Color.White, Color.White.readableOn(ink))

        val amber = Color(0xFFF59E0B) // 2,1:1 sobre blanco
        val fixed = amber.readableOn(Color.White)
        assertRatio(4.5, fixed, Color.White, "ámbar sobre blanco")
        assertTrue("conserva el tono cálido", fixed.red > fixed.blue)

        val indigo = Color(0xFF6063EA) // 2,3:1 sobre #334155
        assertRatio(4.5, indigo.readableOn(Color(0xFF334155)), Color(0xFF334155), "índigo sobre pizarra")
    }

    @Test
    fun onFillPicksTheReadableInk() {
        assertEquals(Color.White, Color(0xFF3730A3).onFill())
        assertEquals(Color(0xFF0F172A), Color(0xFFF59E0B).onFill())
        assertEquals(Color(0xFF0F172A), Color(0xFFEAB308).onFill())
    }
}
