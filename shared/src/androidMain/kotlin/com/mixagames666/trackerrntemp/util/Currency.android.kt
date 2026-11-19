package com.mixagames666.trackerrntemp.util

import androidx.core.os.LocaleListCompat

actual fun deviceLocaleInfo(): DeviceLocaleInfo? {
    val locale = LocaleListCompat.getAdjustedDefault().get(0) ?: return null
    val currencyCode = try {
        java.util.Currency.getInstance(locale)?.currencyCode
    } catch (_: Exception) {
        null
    }
    return DeviceLocaleInfo(
        region = locale.country.takeIf { it.isNotEmpty() },
        currencyCode = currencyCode,
        languageTag = locale.toLanguageTag(),
    )
}
