package com.mixagames666.trackerrntemp.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Reloj de la app. Equivale al `new Date()` global del RN; se puede sustituir en las pruebas para
 * congelar la fecha (los vectores dorados corren con un reloj fijo).
 */
object Time {
    @Volatile
    var nowProvider: () -> LocalDateTime = { LocalDateTime.now() }

    @Volatile
    var zoneProvider: () -> ZoneId = { ZoneId.systemDefault() }

    fun now(): LocalDateTime = nowProvider()
    fun today(): LocalDate = now().toLocalDate()
    fun zone(): ZoneId = zoneProvider()
    fun nowMillis(): Long = now().atZone(zone()).toInstant().toEpochMilli()

    /** `Date.now() + days` etc. */
    fun epochMillisAt(date: LocalDate): Long = date.atStartOfDay(zone()).toInstant().toEpochMilli()

    private val ISO_MILLIS: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(java.time.ZoneOffset.UTC)

    /** `new Date().toISOString()` */
    fun isoNow(): String = ISO_MILLIS.format(Instant.ofEpochMilli(nowMillis()))

    /** `Date.parse(text)` para las fechas ISO que escribe la app; NaN si no se puede leer. */
    fun parseIsoMillis(text: String?): Double {
        if (text.isNullOrEmpty()) return Double.NaN
        return try {
            Instant.parse(text).toEpochMilli().toDouble()
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(text).toInstant().toEpochMilli().toDouble()
            } catch (_: Exception) {
                try {
                    LocalDateTime.parse(text).atZone(zone()).toInstant().toEpochMilli().toDouble()
                } catch (_: Exception) {
                    try {
                        LocalDate.parse(text).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli().toDouble()
                    } catch (_: Exception) {
                        Double.NaN
                    }
                }
            }
        }
    }
}

/**
 * `new Date(year, monthIndex, day)` con el desborde de JavaScript: el mes puede salirse de 0..11 y el
 * día 0 es el último del mes anterior (p. ej. `new Date(2026, 2, 0)` = 28/02/2026).
 */
fun jsDate(year: Int, monthIndex: Int, day: Int): LocalDate =
    LocalDate.of(year, 1, 1).plusMonths(monthIndex.toLong()).plusDays((day - 1).toLong())

/** `Date.prototype.getDay()`: domingo = 0 … sábado = 6. */
fun LocalDate.jsDayOfWeek(): Int = dayOfWeek.value % 7

/** Último día del mes (1-12) de ese año: `new Date(year, month, 0).getDate()`. */
fun daysInMonthOf(year: Int, month: Int): Int = jsDate(year, month, 0).dayOfMonth
