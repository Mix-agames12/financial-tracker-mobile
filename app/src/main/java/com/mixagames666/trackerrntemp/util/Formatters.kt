package com.mixagames666.trackerrntemp.util

import android.annotation.SuppressLint
import android.icu.text.DateTimePatternGenerator
import android.icu.util.ULocale
import android.os.Build
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

// Port de src/utils/formatters.ts

// ===== Moneda =====

data class CurrencyConfig(
    val currency: String, // ISO 4217, p. ej. "USD"
    val locale: String, // p. ej. "es-EC"
)

private val DEFAULT_CURRENCY_CONFIG = CurrencyConfig("USD", "es-EC")

/** Hermes formatea con android.icu; en las pruebas JVM (SDK_INT = 0) se usa java.text. */
private val useIcu: Boolean get() = Build.VERSION.SDK_INT > 0

private fun buildFormatter(config: CurrencyConfig): (Double) -> String {
    if (useIcu) {
        val nf = android.icu.text.NumberFormat.getCurrencyInstance(ULocale.forLanguageTag(config.locale))
        nf.currency = android.icu.util.Currency.getInstance(config.currency)
        nf.minimumFractionDigits = 2
        nf.maximumFractionDigits = 2
        return { value -> synchronized(nf) { nf.format(value) } }
    }
    val nf = java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag(config.locale))
    nf.currency = java.util.Currency.getInstance(config.currency)
    nf.minimumFractionDigits = 2
    nf.maximumFractionDigits = 2
    return { value -> synchronized(nf) { nf.format(value) } }
}

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
    val locale = CurrencyState.config.locale
    val text = if (useIcu) {
        val nf = android.icu.text.NumberFormat.getInstance(ULocale.forLanguageTag(locale))
        nf.minimumFractionDigits = 0
        nf.maximumFractionDigits = maxDecimals
        nf.isGroupingUsed = false
        nf.format(value)
    } else {
        val nf = java.text.NumberFormat.getInstance(Locale.forLanguageTag(locale))
        nf.minimumFractionDigits = 0
        nf.maximumFractionDigits = maxDecimals
        nf.isGroupingUsed = false
        nf.format(value)
    }
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

/** Redondea a 2 decimales evitando errores de coma flotante (0.1 + 0.2). */
fun roundMoney(value: Double): Double {
    if (!value.isFinite()) return 0.0
    return JsCompat.round((value + Math.ulp(1.0)) * 100) / 100 // Number.EPSILON = ulp(1.0)
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

private val PLAIN_LOCALE = Locale("es", "EC")

/** `toLocaleDateString('es-EC', …)`: patrón ICU generado a partir del esqueleto, como hace Intl. */
@SuppressLint("SimpleDateFormat") // el formato ICU lleva su ULocale explícito
private fun localeDate(date: LocalDate, skeleton: String, fallbackPattern: String): String {
    val instant = Date.from(date.atStartOfDay(Time.zone()).toInstant())
    return if (useIcu) {
        val uloc = ULocale("es_EC")
        val pattern = DateTimePatternGenerator.getInstance(uloc).getBestPattern(skeleton)
        val format = android.icu.text.SimpleDateFormat(pattern, uloc)
        format.timeZone = android.icu.util.TimeZone.getTimeZone(Time.zone().id)
        format.format(instant)
    } else {
        val format = java.text.SimpleDateFormat(fallbackPattern, PLAIN_LOCALE)
        format.timeZone = java.util.TimeZone.getTimeZone(Time.zone())
        // Sólo pruebas JVM: el JDK abrevia "nov." (con punto) donde ICU escribe "nov".
        format.format(instant).replace(".", "")
    }
}

fun formatDate(dateStr: String?): String {
    if (dateStr.isNullOrEmpty()) return ""
    val date = parseLocalDateOrNull(dateStr) ?: return "Invalid Date"
    return localeDate(date, "yMMMdd", "dd MMM yyyy")
}

fun formatDateShort(dateStr: String?): String {
    if (dateStr.isNullOrEmpty()) return ""
    val date = parseLocalDateOrNull(dateStr) ?: return "Invalid Date"
    return localeDate(date, "MMMdd", "dd MMM")
}

fun formatPercent(value: Double): String = "${JsCompat.toFixed(value * 100, 1)}%"

/** "1 mes" / "3 meses". */
fun formatMonths(n: Int): String = "$n ${if (n == 1) "mes" else "meses"}"

fun getToday(): String = toLocalDateStr(Time.today())

fun getNow(): String {
    val now = Time.now()
    return "%02d:%02d".format(now.hour, now.minute)
}

private val MONTH_NAMES = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre",
)

fun getMonthName(month: Int): String = MONTH_NAMES.getOrElse(month - 1) { "" }

/** Semana ISO (jueves de la semana), igual que getWeekNumber de formatters.ts. */
fun getWeekNumber(date: LocalDate): Int {
    val d = date.plusDays((3 - (date.jsDayOfWeek() + 6) % 7).toLong())
    val week1 = LocalDate.of(d.year, 1, 4)
    val diffDays = java.time.temporal.ChronoUnit.DAYS.between(week1, d).toDouble()
    return 1 + JsCompat.round((diffDays - 3 + (week1.jsDayOfWeek() + 6) % 7) / 7).toInt()
}

fun getDaysInMonth(year: Int, month: Int): Int = daysInMonthOf(year, month)

/** Días hasta esa fecha desde hoy (negativo = pasó). `null` si la fecha no es válida (NaN en JS). */
fun daysUntil(dateStr: String, today: LocalDate = Time.today()): Int? {
    val target = parseLocalDateOrNull(dateStr) ?: return null
    val zone = Time.zone()
    val nowMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val targetMs = target.atStartOfDay(zone).toInstant().toEpochMilli()
    return ceil((targetMs - nowMs) / 86_400_000.0).toInt()
}

/** Ese día del mes (índice de mes base 0); si el mes es más corto, su último día. */
private fun dayInMonth(year: Int, monthIndex: Int, day: Int): LocalDate {
    val lastDay = jsDate(year, monthIndex + 1, 0).dayOfMonth
    return jsDate(year, monthIndex, Math.min(day, lastDay))
}

/** Próxima fecha (hoy o posterior) con ese día del mes; en meses cortos usa el último día. */
fun nextDateForDay(day: Int, from: LocalDate = Time.today()): String {
    val thisMonth = dayInMonth(from.year, from.monthValue - 1, JsCompat.clampDay(day))
    if (!thisMonth.isBefore(from)) return toLocalDateStr(thisMonth)
    return toLocalDateStr(dayInMonth(from.year, from.monthValue, JsCompat.clampDay(day)))
}

/** Última fecha (hoy o anterior) con ese día del mes, p. ej. el último corte de una tarjeta. */
fun lastDateForDay(day: Int, from: LocalDate = Time.today()): String {
    val thisMonth = dayInMonth(from.year, from.monthValue - 1, JsCompat.clampDay(day))
    if (!thisMonth.isAfter(from)) return toLocalDateStr(thisMonth)
    return toLocalDateStr(dayInMonth(from.year, from.monthValue - 2, JsCompat.clampDay(day)))
}

/** Próxima fecha (hoy o posterior) con ese día y mes (1-12); el 29/02 cae el 28/02 en años no bisiestos. */
fun nextDateForMonthDay(month: Int, day: Int, from: LocalDate = Time.today()): String {
    val monthIndex = Math.min(Math.max(if (month == 0) 1 else month, 1), 12) - 1
    val thisYear = dayInMonth(from.year, monthIndex, JsCompat.clampDay(day))
    if (!thisYear.isBefore(from)) return toLocalDateStr(thisYear)
    return toLocalDateStr(dayInMonth(from.year + 1, monthIndex, JsCompat.clampDay(day)))
}
