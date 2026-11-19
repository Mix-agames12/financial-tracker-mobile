package com.mixagames666.trackerrntemp.util

import android.annotation.SuppressLint
import android.icu.text.DateTimePatternGenerator
import android.icu.util.ULocale
import android.os.Build
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import java.util.Date
import java.util.Locale

/** Hermes formatea con android.icu; en las pruebas JVM (SDK_INT = 0) se usa java.text. */
private val useIcu: Boolean get() = Build.VERSION.SDK_INT > 0

internal actual fun createCurrencyFormatter(currency: String, locale: String): (Double) -> String {
    if (useIcu) {
        val nf = android.icu.text.NumberFormat.getCurrencyInstance(ULocale.forLanguageTag(locale))
        nf.currency = android.icu.util.Currency.getInstance(currency)
        nf.minimumFractionDigits = 2
        nf.maximumFractionDigits = 2
        return { value -> synchronized(nf) { nf.format(value) } }
    }
    val nf = java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag(locale))
    nf.currency = java.util.Currency.getInstance(currency)
    nf.minimumFractionDigits = 2
    nf.maximumFractionDigits = 2
    return { value -> synchronized(nf) { nf.format(value) } }
}

internal actual fun formatLocalizedNumber(value: Double, locale: String, maxDecimals: Int): String =
    if (useIcu) {
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

private val PLAIN_LOCALE = Locale("es", "EC")

/** `toLocaleDateString('es-EC', …)`: patrón ICU generado a partir del esqueleto, como hace Intl. */
@SuppressLint("SimpleDateFormat") // el formato ICU lleva su ULocale explícito
internal actual fun formatLocaleDate(date: LocalDate, zone: TimeZone, skeleton: String, fallbackPattern: String): String {
    val instant = Date(date.atStartOfDayIn(zone).toEpochMilliseconds())
    return if (useIcu) {
        val uloc = ULocale("es_EC")
        val pattern = DateTimePatternGenerator.getInstance(uloc).getBestPattern(skeleton)
        val format = android.icu.text.SimpleDateFormat(pattern, uloc)
        format.timeZone = android.icu.util.TimeZone.getTimeZone(zone.id)
        format.format(instant)
    } else {
        val format = java.text.SimpleDateFormat(fallbackPattern, PLAIN_LOCALE)
        format.timeZone = java.util.TimeZone.getTimeZone(zone.id)
        // Sólo pruebas JVM: el JDK abrevia "nov." (con punto) donde ICU escribe "nov".
        format.format(instant).replace(".", "")
    }
}
