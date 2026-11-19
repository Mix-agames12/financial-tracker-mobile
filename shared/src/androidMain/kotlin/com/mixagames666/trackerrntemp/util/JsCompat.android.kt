package com.mixagames666.trackerrntemp.util

import java.math.BigDecimal
import java.math.RoundingMode

// Implementación JVM de los cálculos numéricos de JsCompat con BigDecimal (exactos).

internal actual fun exactToFixedMagnitude(magnitude: Double, digits: Int): String =
    BigDecimal(magnitude).setScale(digits, RoundingMode.HALF_UP).toPlainString()

internal actual fun shortestDigits(magnitude: Double): ShortestDigits {
    val decimal = BigDecimal(java.lang.Double.toString(magnitude)).stripTrailingZeros()
    val digits = decimal.unscaledValue().toString()
    return ShortestDigits(digits, digits.length - decimal.scale())
}

internal actual fun decimalMagnitude(digits: String): Double = BigDecimal(digits).toDouble()

internal actual fun hexMagnitude(digits: String): Double = digits.toBigInteger(16).toDouble()
