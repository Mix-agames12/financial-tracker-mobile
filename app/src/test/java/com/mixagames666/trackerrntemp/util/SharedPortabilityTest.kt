package com.mixagames666.trackerrntemp.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime
import kotlin.random.Random
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Comprueba en la JVM el código "sin BigDecimal / java.time" que usa iOS contra las implementaciones de
 * referencia de Java (el código de iosMain no se puede compilar en Windows).
 */
class SharedPortabilityTest {

    @After
    fun resetClock() {
        Time.zoneProvider = { TimeZone.currentSystemDefault() }
    }

    @Test
    fun exactDecimalCoincideConBigDecimal() {
        val random = Random(42)
        val samples = mutableListOf(0.0, 1.005, 2.5, 0.5, 1.45, 8.345, 1e-7, 123456789.125, 9.999999, 0.1 + 0.2, 1e20, Double.MIN_VALUE, 4.9e-324)
        repeat(3000) {
            samples.add(random.nextDouble() * Math.pow(10.0, random.nextInt(-8, 21).toDouble()))
        }
        for (v in samples) for (digits in listOf(0, 1, 2, 3, 6)) {
            val expected = BigDecimal(v).setScale(digits, RoundingMode.HALF_UP).toPlainString()
            assertEquals("toFixed($v, $digits)", expected, ExactDecimal.toFixedMagnitude(v, digits))
        }
    }

    @Test
    fun digitosMasCortosCoincidenConBigDecimal() {
        val random = Random(7)
        val samples = mutableListOf(1.0, 0.001, 1e10, 123.456, 1.5e-7, 1e21, 12345678901234567890.0, 0.5, Double.MIN_VALUE)
        repeat(3000) { samples.add(random.nextDouble() * Math.pow(10.0, random.nextInt(-10, 25).toDouble())) }
        for (v in samples) {
            val decimal = BigDecimal(java.lang.Double.toString(v)).stripTrailingZeros()
            val actual = ExactDecimal.shortestFromString(v.toString())
            assertEquals("digits $v", decimal.unscaledValue().toString(), actual.digits)
            assertEquals("punto $v", decimal.precision() - decimal.scale(), actual.pointPosition)
        }
    }

    @Test
    fun isoStringEsIgualAlFormatoDeToISOString() {
        assertEquals("1970-01-01T00:00:00.000Z", Time.isoString(0))
        assertEquals("2026-03-05T14:07:09.050Z", Time.isoString(1772719629050L))
        assertEquals("1969-12-31T23:59:59.999Z", Time.isoString(-1))
        val random = Random(1)
        repeat(500) {
            val ms = random.nextLong(-2_000_000_000_000L, 4_000_000_000_000L)
            val expected = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.ofEpochMilli(ms))
            assertEquals(expected, Time.isoString(ms))
        }
    }

    @Test
    fun parseaFechasIsoComoJavaTime() {
        Time.zoneProvider = { TimeZone.of("America/Guayaquil") }
        assertEquals(java.time.Instant.parse("2026-01-15T10:00:00.123Z").toEpochMilli().toDouble(), Time.parseIsoMillis("2026-01-15T10:00:00.123Z"), 0.0)
        assertEquals(OffsetDateTime.parse("2026-01-15T10:00:00+02:00").toInstant().toEpochMilli().toDouble(), Time.parseIsoMillis("2026-01-15T10:00:00+02:00"), 0.0)
        assertEquals(java.time.LocalDateTime.parse("2026-01-15T10:00:00").atZone(java.time.ZoneId.of("America/Guayaquil")).toInstant().toEpochMilli().toDouble(), Time.parseIsoMillis("2026-01-15T10:00:00"), 0.0)
        assertEquals(java.time.LocalDate.parse("2026-01-15").atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli().toDouble(), Time.parseIsoMillis("2026-01-15"), 0.0)
        assertEquals(true, Time.parseIsoMillis("basura").isNaN())
    }

    @Test
    fun jsDateDesbordaComoJavaScript() {
        for (year in listOf(2023, 2024, 2026)) for (monthIndex in -14..26) for (day in listOf(-31, -1, 0, 1, 15, 31, 45)) {
            val expected = java.time.LocalDate.of(year, 1, 1).plusMonths(monthIndex.toLong()).plusDays((day - 1).toLong())
            assertEquals(expected.toString(), jsDate(year, monthIndex, day).toString())
            assertEquals(expected.dayOfWeek.value % 7, jsDate(year, monthIndex, day).jsDayOfWeek())
        }
    }
}
