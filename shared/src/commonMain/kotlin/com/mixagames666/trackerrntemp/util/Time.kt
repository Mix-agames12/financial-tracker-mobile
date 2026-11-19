package com.mixagames666.trackerrntemp.util

import kotlin.concurrent.Volatile
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Reloj de la app. Equivale al `new Date()` global del RN; se puede sustituir en las pruebas para
 * congelar la fecha (los vectores dorados corren con un reloj fijo).
 */
object Time {
    @Volatile
    var nowProvider: () -> LocalDateTime = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }

    @Volatile
    var zoneProvider: () -> TimeZone = { TimeZone.currentSystemDefault() }

    fun now(): LocalDateTime = nowProvider()
    fun today(): LocalDate = now().date
    fun zone(): TimeZone = zoneProvider()
    fun nowMillis(): Long = now().toInstant(zone()).toEpochMilliseconds()

    /** `Date.now() + days` etc. */
    fun epochMillisAt(date: LocalDate): Long = date.atStartOfDayIn(zone()).toEpochMilliseconds()

    /** `new Date().toISOString()`: siempre `yyyy-MM-ddTHH:mm:ss.SSSZ` (UTC, tres decimales). */
    fun isoNow(): String = isoString(nowMillis())

    /** Formatea epoch-millis como `Date.prototype.toISOString` (byte a byte, sin depender del formato de Instant.toString). */
    fun isoString(epochMillis: Long): String {
        val utc = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.UTC)
        fun two(n: Int) = n.toString().padStart(2, '0')
        val millis = (((epochMillis % 1000) + 1000) % 1000).toInt().toString().padStart(3, '0')
        val year = utc.year.toString().padStart(4, '0')
        return "$year-${two(utc.monthNumber)}-${two(utc.dayOfMonth)}T${two(utc.hour)}:${two(utc.minute)}:${two(utc.second)}.${millis}Z"
    }

    /** `Date.parse(text)` para las fechas ISO que escribe la app; NaN si no se puede leer. */
    fun parseIsoMillis(text: String?): Double {
        if (text.isNullOrEmpty()) return Double.NaN
        return try {
            // Admite "Z" y desfases (+02:00), igual que Instant/OffsetDateTime de java.time.
            Instant.parse(text).toEpochMilliseconds().toDouble()
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(text).toInstant(zone()).toEpochMilliseconds().toDouble()
            } catch (_: Exception) {
                try {
                    LocalDate.parse(text).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds().toDouble()
                } catch (_: Exception) {
                    Double.NaN
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
    LocalDate(year, 1, 1).plus(monthIndex, DateTimeUnit.MONTH).plus(day - 1, DateTimeUnit.DAY)

/** `Date.prototype.getDay()`: domingo = 0 … sábado = 6. */
fun LocalDate.jsDayOfWeek(): Int = dayOfWeek.isoDayNumber % 7

/** Último día del mes (1-12) de ese año: `new Date(year, month, 0).getDate()`. */
fun daysInMonthOf(year: Int, month: Int): Int = jsDate(year, month, 0).dayOfMonth
