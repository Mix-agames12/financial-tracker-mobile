package com.mixagames666.trackerrntemp.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.numberWithDouble
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localeWithLocaleIdentifier
import platform.Foundation.timeZoneWithName

// Equivalentes de Foundation (iOS) de los formatos ICU que usa Android.

/** "es-EC" (etiqueta BCP 47) -> "es_EC" (identificador de NSLocale). */
private fun nsLocale(languageTag: String): NSLocale = NSLocale.localeWithLocaleIdentifier(languageTag.replace('-', '_'))

/** Códigos de moneda ISO 4217: tres letras mayúsculas (NSNumberFormatter no los rechaza por sí mismo). */
private val CURRENCY_CODE = Regex("^[A-Z]{3}$")

internal actual fun createCurrencyFormatter(currency: String, locale: String): (Double) -> String {
    require(CURRENCY_CODE.matches(currency)) { "Moneda inválida: $currency" }
    val formatter = NSNumberFormatter()
    formatter.numberStyle = NSNumberFormatterCurrencyStyle
    formatter.locale = nsLocale(locale)
    formatter.currencyCode = currency
    formatter.minimumFractionDigits = 2u
    formatter.maximumFractionDigits = 2u
    return { value -> formatter.stringFromNumber(NSNumber.numberWithDouble(value))!! }
}

internal actual fun formatLocalizedNumber(value: Double, locale: String, maxDecimals: Int): String {
    val formatter = NSNumberFormatter()
    formatter.numberStyle = NSNumberFormatterDecimalStyle
    formatter.locale = nsLocale(locale)
    formatter.minimumFractionDigits = 0u
    formatter.maximumFractionDigits = maxDecimals.toULong()
    formatter.usesGroupingSeparator = false
    return formatter.stringFromNumber(NSNumber.numberWithDouble(value)) ?: value.toString()
}

/**
 * `toLocaleDateString('es-EC', …)`. Para es-EC el esqueleto ICU "yMMMdd" resuelve a "dd MMM y" y "MMMdd" a
 * "dd MMM" (CLDR), que es justo [fallbackPattern]; se usa ese patrón con el NSLocale es_EC para que los meses
 * abreviados salgan de los datos de Foundation ("nov", sin punto).
 */
internal actual fun formatLocaleDate(date: LocalDate, zone: TimeZone, skeleton: String, fallbackPattern: String): String {
    val formatter = NSDateFormatter()
    formatter.locale = nsLocale("es-EC")
    formatter.timeZone = NSTimeZone.timeZoneWithName(zone.id) ?: NSTimeZone.defaultTimeZone
    formatter.dateFormat = fallbackPattern
    val seconds = date.atStartOfDayIn(zone).toEpochMilliseconds() / 1000.0
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(seconds))
}
