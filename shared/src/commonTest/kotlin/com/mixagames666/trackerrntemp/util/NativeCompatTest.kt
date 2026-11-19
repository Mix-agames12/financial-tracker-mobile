package com.mixagames666.trackerrntemp.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pruebas mínimas que corren en cada destino de :shared (en CI también en el simulador de iOS, es decir, sobre
 * Kotlin/Native): fechas ISO y reglas de JavaScript que dependen de la implementación de cada plataforma.
 */
class NativeCompatTest {
    @Test
    fun isoStringUsesJavaScriptFormat() {
        assertEquals("1970-01-01T00:00:00.000Z", Time.isoString(0L))
        assertEquals("2024-02-29T12:34:56.789Z", Time.isoString(1709210096789L))
    }

    @Test
    fun isoStringRoundTripsThroughParse() {
        val millis = 1709210096789L
        assertEquals(millis.toDouble(), Time.parseIsoMillis(Time.isoString(millis)))
        assertTrue(Time.parseIsoMillis("no es una fecha").isNaN())
    }

    @Test
    fun toFixedFollowsJavaScript() {
        // (1.005).toFixed(2) === "1.00" porque 1.005 es en realidad 1.00499999999999989…
        assertEquals("1.00", JsCompat.toFixed(1.005, 2))
        assertEquals("0.50", JsCompat.toFixed(0.5, 2))
        assertEquals("-3", JsCompat.toFixed(-2.5, 0))
    }

    @Test
    fun roundTiesGoTowardPositiveInfinity() {
        assertEquals(3.0, JsCompat.round(2.5))
        assertEquals(-2.0, JsCompat.round(-2.5))
    }
}
