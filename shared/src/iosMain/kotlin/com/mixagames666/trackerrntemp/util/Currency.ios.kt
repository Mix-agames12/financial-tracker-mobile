package com.mixagames666.trackerrntemp.util

import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.NSLocaleCurrencyCode
import platform.Foundation.NSLocaleLanguageCode

actual fun deviceLocaleInfo(): DeviceLocaleInfo? {
    val locale = NSLocale.currentLocale
    val region = (locale.objectForKey(NSLocaleCountryCode) as? String)?.takeIf { it.isNotEmpty() }
    val language = (locale.objectForKey(NSLocaleLanguageCode) as? String)?.takeIf { it.isNotEmpty() } ?: return null
    return DeviceLocaleInfo(
        region = region,
        currencyCode = locale.objectForKey(NSLocaleCurrencyCode) as? String,
        languageTag = if (region != null) "$language-$region" else language,
    )
}
