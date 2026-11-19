package com.mixagames666.trackerrntemp.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * [formatPercentLocalized] debe usar el mismo formato regional que [formatCurrency]: así "$1.430,99" y "204,4%"
 * no mezclan separadores decimales en el resumen mensual del Dashboard.
 */
class PercentFormatTest {
    @Before
    fun setUp() = setCurrencyConfig("USD", "es-EC")

    @After
    fun tearDown() = setCurrencyConfig("USD", "es-EC")

    @Test
    fun usesTheRegionalDecimalSeparator() {
        assertEquals("204,4%", formatPercentLocalized(2.044))
        assertEquals("43,8%", formatPercentLocalized(0.438))
    }

    @Test
    fun dropsUselessDecimals() {
        assertEquals("90%", formatPercentLocalized(0.9))
        assertEquals("0%", formatPercentLocalized(0.0))
        assertEquals("100%", formatPercentLocalized(1.0))
    }

    @Test
    fun keepsAtMostOneDecimalByDefault() {
        assertEquals("33,3%", formatPercentLocalized(1.0 / 3))
        assertEquals("33,33%", formatPercentLocalized(1.0 / 3, maxDecimals = 2))
    }

    @Test
    fun invalidValuesCountAsZero() {
        assertEquals("0%", formatPercentLocalized(Double.NaN))
        assertEquals("0%", formatPercentLocalized(Double.POSITIVE_INFINITY))
    }

    @Test
    fun followsTheActiveLocale() {
        setCurrencyConfig("USD", "en-US")
        assertEquals("43.8%", formatPercentLocalized(0.438))
    }
}
