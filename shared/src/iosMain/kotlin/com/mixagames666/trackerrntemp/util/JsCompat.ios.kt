package com.mixagames666.trackerrntemp.util

// Implementación iOS (sin java.math): reglas de JavaScript reproducidas con aritmética propia.

/** `toFixed`: expansión decimal exacta del double y empate hacia arriba (ver [ExactDecimal]). */
internal actual fun exactToFixedMagnitude(magnitude: Double, digits: Int): String =
    ExactDecimal.toFixedMagnitude(magnitude, digits)

/**
 * `String(n)`: `Double.toString` de Kotlin/Native ya entrega los dígitos más cortos que identifican el double
 * (misma regla que JavaScript), sólo hay que separar dígitos y exponente.
 */
internal actual fun shortestDigits(magnitude: Double): ShortestDigits =
    ExactDecimal.shortestFromString(magnitude.toString())

/** `parseInt` decimal: el redondeo a double de una cadena de dígitos es el correcto (como `Number("123…")`). */
internal actual fun decimalMagnitude(digits: String): Double = digits.toDouble()

/** `parseInt(…, 16)`: acumula en double (exacto hasta 2^53; más allá JavaScript también pierde precisión). */
internal actual fun hexMagnitude(digits: String): Double {
    var acc = 0.0
    for (c in digits) acc = acc * 16 + c.digitToInt(16)
    return acc
}
