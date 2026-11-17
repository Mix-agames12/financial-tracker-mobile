package com.mixagames666.trackerrntemp.util

import android.util.Log
import androidx.core.os.LocaleListCompat
import com.mixagames666.trackerrntemp.data.Repos

// Port de src/utils/currency.ts

data class CurrencyOption(
    val code: String,
    val name: String,
    val locale: String, // formato por defecto al elegirla manualmente
)

val CURRENCY_OPTIONS: List<CurrencyOption> = listOf(
    CurrencyOption("USD", "Dólar estadounidense", "es-EC"),
    CurrencyOption("EUR", "Euro", "es-ES"),
    CurrencyOption("MXN", "Peso mexicano", "es-MX"),
    CurrencyOption("COP", "Peso colombiano", "es-CO"),
    CurrencyOption("PEN", "Sol peruano", "es-PE"),
    CurrencyOption("CLP", "Peso chileno", "es-CL"),
    CurrencyOption("ARS", "Peso argentino", "es-AR"),
    CurrencyOption("BOB", "Boliviano", "es-BO"),
    CurrencyOption("PYG", "Guaraní paraguayo", "es-PY"),
    CurrencyOption("UYU", "Peso uruguayo", "es-UY"),
    CurrencyOption("GTQ", "Quetzal guatemalteco", "es-GT"),
    CurrencyOption("HNL", "Lempira hondureño", "es-HN"),
    CurrencyOption("NIO", "Córdoba nicaragüense", "es-NI"),
    CurrencyOption("CRC", "Colón costarricense", "es-CR"),
    CurrencyOption("DOP", "Peso dominicano", "es-DO"),
    CurrencyOption("BRL", "Real brasileño", "pt-BR"),
    CurrencyOption("CAD", "Dólar canadiense", "en-CA"),
    CurrencyOption("GBP", "Libra esterlina", "en-GB"),
)

fun getCurrencyOption(code: String?): CurrencyOption? = CURRENCY_OPTIONS.firstOrNull { it.code == code }

/** Moneda sugerida por la región configurada en el dispositivo (no requiere permisos de ubicación). */
fun detectDeviceCurrency(): CurrencyConfig {
    try {
        val locale = LocaleListCompat.getAdjustedDefault().get(0)
        if (locale != null) {
            val code = try {
                java.util.Currency.getInstance(locale)?.currencyCode?.uppercase()
            } catch (_: Exception) {
                null
            }
            val region = locale.country.takeIf { it.isNotEmpty() }?.uppercase()
            if (!code.isNullOrEmpty()) {
                val candidates = listOfNotNull(
                    region?.let { "es-$it" },
                    getCurrencyOption(code)?.locale,
                    locale.toLanguageTag(),
                    "es-EC",
                )
                for (candidate in candidates) {
                    val config = CurrencyConfig(code, candidate)
                    if (isValidCurrencyConfig(config)) return config
                }
            }
        }
    } catch (e: Exception) {
        Log.w("Currency", "No se pudo detectar la moneda del dispositivo", e)
    }
    return CurrencyConfig("USD", "es-EC")
}

/**
 * Aplica la moneda guardada. Sólo en el primer inicio (sin moneda guardada) la detecta desde
 * la región del dispositivo y la persiste; la hoja de bienvenida pide confirmarla.
 */
suspend fun initCurrency(repos: Repos) {
    val settings = repos.settings.get()
    if (!settings.currency.isNullOrEmpty()) {
        setCurrencyConfig(settings.currency, settings.currencyLocale)
        return
    }
    val detected = detectDeviceCurrency()
    setCurrencyConfig(detected.currency, detected.locale)
    repos.settings.save { it.copy(currency = detected.currency, currencyLocale = detected.locale) }
}

/** Aplica la moneda guardada sin detectar ni persistir (contextos sin UI como el widget). */
suspend fun applySavedCurrency(repos: Repos) {
    val settings = repos.settings.get()
    if (!settings.currency.isNullOrEmpty()) {
        setCurrencyConfig(settings.currency, settings.currencyLocale)
    }
}

suspend fun saveCurrency(repos: Repos, config: CurrencyConfig) {
    setCurrencyConfig(config.currency, config.locale)
    repos.settings.save { it.copy(currency = config.currency, currencyLocale = config.locale) }
}

/** Ejemplo "1.234,50" de cada moneda para el selector (CurrencyPicker.formatExample). */
fun formatCurrencyExample(option: CurrencyOption): String = try {
    // Se formatea con una configuración propia sin alterar la moneda activa.
    formatCurrencyWith(CurrencyConfig(option.code, option.locale), 1234.5)
} catch (_: Exception) {
    option.code
}
