package com.mixagames666.trackerrntemp.util

import kotlin.concurrent.Volatile
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

// Port de src/utils/formatters.ts

// ===== Moneda =====

data class CurrencyConfig(
    val currency: String, // ISO 4217, p. ej. "USD"
    val locale: String, // p. ej. "es-EC"
)

private val DEFAULT_CURRENCY_CONFIG = CurrencyConfig("USD", "es-EC")

/**
 * Formateador de moneda de la plataforma (ICU en Android, NSNumberFormatter en iOS) con siempre 2 decimales.
 * Lanza una excepción si la moneda o el idioma no son válidos.
 */
internal expect fun createCurrencyFormatter(currency: String, locale: String): (Double) -> String

private fun buildFormatter(config: CurrencyConfig): (Double) -> String = createCurrencyFormatter(config.currency, config.locale)

/** Número con los separadores del idioma [locale] (sin agrupar miles, de 0 a [maxDecimals] decimales). */
internal expect fun formatLocalizedNumber(value: Double, locale: String, maxDecimals: Int): String

/**
 * `toLocaleDateString('es-EC', …)` de [date] (a medianoche de [zone]): patrón generado a partir del esqueleto
 * ICU [skeleton] como hace Intl; [fallbackPattern] (el patrón equivalente para es-EC) sólo lo usan las pruebas JVM (sin ICU de Android) e iOS.
 */
internal expect fun formatLocaleDate(date: LocalDate, zone: TimeZone, skeleton: String, fallbackPattern: String): String

/** Formatea con una configuración concreta sin cambiar la moneda activa (selector de monedas). */
fun formatCurrencyWith(config: CurrencyConfig, amount: Double): String = buildFormatter(config)(amount)

fun isValidCurrencyConfig(config: CurrencyConfig): Boolean = try {
    buildFormatter(config)(1.0)
    true
} catch (_: Exception) {
    false
}

private object CurrencyState {
    @Volatile var config: CurrencyConfig = DEFAULT_CURRENCY_CONFIG
    @Volatile var formatter: ((Double) -> String)? = null
}

/** Define la moneda usada por [formatCurrency] en toda la app (se carga desde Settings al iniciar). */
fun setCurrencyConfig(currency: String?, locale: String?) {
    val next = CurrencyConfig(
        currency = currency?.takeIf { it.isNotEmpty() } ?: DEFAULT_CURRENCY_CONFIG.currency,
        locale = locale?.takeIf { it.isNotEmpty() } ?: DEFAULT_CURRENCY_CONFIG.locale,
    )
    CurrencyState.config = if (isValidCurrencyConfig(next)) next else DEFAULT_CURRENCY_CONFIG
    CurrencyState.formatter = null
}

fun getCurrencyConfig(): CurrencyConfig = CurrencyState.config

private fun currentFormatter(): (Double) -> String =
    CurrencyState.formatter ?: buildFormatter(CurrencyState.config).also { CurrencyState.formatter = it }

private fun formatCurrencyValue(num: Double): String = currentFormatter()(if (num.isFinite()) num else 0.0)

fun formatCurrency(amount: Number?): String = formatCurrencyValue(amount?.toDouble() ?: 0.0)

fun formatCurrency(amount: String?): String = formatCurrencyValue(JsCompat.parseFloat(amount))

/**
 * Porcentaje con el separador decimal de la moneda activa ("43,8%"; sin decimales sobrantes: 0.9 → "90%").
 * [ratio] va de 0 a 1 (puede superar 1: 2.044 → "204,4%"). A diferencia de [formatPercent] (puerto de
 * formatters.ts, siempre con punto), éste sigue el mismo formato regional que [formatCurrency], de modo que
 * "$1.430,99" y "204,4%" no mezclen separadores en una misma pantalla (heurística 4: consistencia).
 */
fun formatPercentLocalized(ratio: Double, maxDecimals: Int = 1): String {
    val value = if (ratio.isFinite()) ratio * 100 else 0.0
    val text = formatLocalizedNumber(value, CurrencyState.config.locale, maxDecimals)
    return "$text%"
}

private val SYMBOL_STRIP = Regex("[0-9\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF.,-]")

/** Símbolo de la moneda activa (p. ej. "$", "€", "S/"). */
fun getCurrencySymbol(): String {
    val symbol = currentFormatter()(0.0).replace(SYMBOL_STRIP, "")
    return symbol.ifEmpty { CurrencyState.config.currency }
}

// ===== Montos =====

/** Deja un único separador decimal ('.') y como máximo [maxDecimals] decimales. */
fun sanitizeDecimalInput(text: String, maxDecimals: Int = 2): String {
    val cleaned = text.replace(',', '.').replace(Regex("[^0-9.]"), "")
    val dotIndex = cleaned.indexOf('.')
    if (dotIndex == -1) return cleaned
    val intPart = cleaned.substring(0, dotIndex).ifEmpty { "0" }
    val decimals = cleaned.substring(dotIndex + 1).replace(".", "").take(maxDecimals)
    return "$intPart.$decimals"
}

fun sanitizeIntegerInput(text: String): String = text.replace(Regex("[^0-9]"), "")

/** `Number.EPSILON` (2^-52, el ulp de 1.0). */
private const val NUMBER_EPSILON = 2.220446049250313E-16

/** Redondea a 2 decimales evitando errores de coma flotante (0.1 + 0.2). */
fun roundMoney(value: Double): Double {
    if (!value.isFinite()) return 0.0
    return JsCompat.round((value + NUMBER_EPSILON) * 100) / 100
}

fun roundMoney(value: Int): Double = roundMoney(value.toDouble())

/** Valor para precargar un campo de monto al editar: máximo 2 decimales, vacío si no hay valor. */
fun toAmountInput(value: Number?): String {
    if (value == null) return ""
    val num = value.toDouble()
    return if (num.isFinite()) JsCompat.numberToString(roundMoney(num)) else ""
}

fun toAmountInput(value: String?): String {
    if (value.isNullOrEmpty()) return ""
    val num = JsCompat.number(value)
    return if (num.isFinite()) JsCompat.numberToString(roundMoney(num)) else ""
}

// ===== Fechas =====

/** 'YYYY-MM-DD' en hora local. */
fun toLocalDateStr(d: LocalDate): String = d.toString()

fun parseLocalDateOrNull(dateStr: String?): LocalDate? = try {
    if (dateStr.isNullOrEmpty()) null else LocalDate.parse(dateStr)
} catch (_: Exception) {
    null
}

fun formatDate(dateStr: String?): String {
    if (dateStr.isNullOrEmpty()) return ""
    val date = parseLocalDateOrNull(dateStr) ?: return "Invalid Date"
    return formatLocaleDate(date, Time.zone(), "yMMMdd", "dd MMM yyyy")
}

fun formatDateShort(dateStr: String?): String {
    if (dateStr.isNullOrEmpty()) return ""
    val date = parseLocalDateOrNull(dateStr) ?: return "Invalid Date"
    return formatLocaleDate(date, Time.zone(), "MMMdd", "dd MMM")
}

fun formatPercent(value: Double): String = "${JsCompat.toFixed(value * 100, 1)}%"

/** "1 mes" / "3 meses". */
fun formatMonths(n: Int): String = "$n ${if (n == 1) "mes" else "meses"}"

fun getToday(): String = toLocalDateStr(Time.today())

fun getNow(): String {
    val now = Time.now()
    return "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}"
}

private val MONTH_NAMES = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre",
)

fun getMonthName(month: Int): String = MONTH_NAMES.getOrElse(month - 1) { "" }

/** Semana ISO (jueves de la semana), igual que getWeekNumber de formatters.ts. */
fun getWeekNumber(date: LocalDate): Int {
    val d = date.plus(3 - (date.jsDayOfWeek() + 6) % 7, DateTimeUnit.DAY)
    val week1 = LocalDate(d.year, 1, 4)
    val diffDays = week1.daysUntil(d).toDouble()
    return 1 + JsCompat.round((diffDays - 3 + (week1.jsDayOfWeek() + 6) % 7) / 7).toInt()
}

fun getDaysInMonth(year: Int, month: Int): Int = daysInMonthOf(year, month)

/** Días hasta esa fecha desde hoy (negativo = pasó). `null` si la fecha no es válida (NaN en JS). */
fun daysUntil(dateStr: String, today: LocalDate = Time.today()): Int? {
    val target = parseLocalDateOrNull(dateStr) ?: return null
    val zone = Time.zone()
    val nowMs = today.atStartOfDayIn(zone).toEpochMilliseconds()
    val targetMs = target.atStartOfDayIn(zone).toEpochMilliseconds()
    return ceil((targetMs - nowMs) / 86_400_000.0).toInt()
}

/** Ese día del mes (índice de mes base 0); si el mes es más corto, su último día. */
private fun dayInMonth(year: Int, monthIndex: Int, day: Int): LocalDate {
    val lastDay = jsDate(year, monthIndex + 1, 0).dayOfMonth
    return jsDate(year, monthIndex, min(day, lastDay))
}

/** Próxima fecha (hoy o posterior) con ese día del mes; en meses cortos usa el último día. */
fun nextDateForDay(day: Int, from: LocalDate = Time.today()): String {
    val thisMonth = dayInMonth(from.year, from.monthNumber - 1, JsCompat.clampDay(day))
    if (thisMonth >= from) return toLocalDateStr(thisMonth)
    return toLocalDateStr(dayInMonth(from.year, from.monthNumber, JsCompat.clampDay(day)))
}

/** Última fecha (hoy o anterior) con ese día del mes, p. ej. el último corte de una tarjeta. */
fun lastDateForDay(day: Int, from: LocalDate = Time.today()): String {
    val thisMonth = dayInMonth(from.year, from.monthNumber - 1, JsCompat.clampDay(day))
    if (thisMonth <= from) return toLocalDateStr(thisMonth)
    return toLocalDateStr(dayInMonth(from.year, from.monthNumber - 2, JsCompat.clampDay(day)))
}

/** Próxima fecha (hoy o posterior) con ese día y mes (1-12); el 29/02 cae el 28/02 en años no bisiestos. */
fun nextDateForMonthDay(month: Int, day: Int, from: LocalDate = Time.today()): String {
    val monthIndex = min(max(if (month == 0) 1 else month, 1), 12) - 1
    val thisYear = dayInMonth(from.year, monthIndex, JsCompat.clampDay(day))
    if (thisYear >= from) return toLocalDateStr(thisYear)
    return toLocalDateStr(dayInMonth(from.year + 1, monthIndex, JsCompat.clampDay(day)))
}
