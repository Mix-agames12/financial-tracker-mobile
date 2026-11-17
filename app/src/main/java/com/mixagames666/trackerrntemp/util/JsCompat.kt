package com.mixagames666.trackerrntemp.util

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.max

/**
 * Reproduce la semántica de JavaScript que usa la app React Native (números como `double`, `toFixed`,
 * `String(n)`, `parseFloat`, `parseInt`, `Math.round`, `Number(x) || 0`). Se usa en lugar de las
 * funciones equivalentes de Kotlin/Java porque difieren en casos límite (p. ej. 1.005.toFixed(2) = "1.00").
 */
object JsCompat {

    /** `Number.prototype.toFixed`: expansión decimal exacta del double y empate hacia el valor mayor. */
    fun toFixed(value: Double, digits: Int): String {
        if (value.isNaN()) return "NaN"
        if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"
        if (abs(value) >= 1e21) return numberToString(value)
        val magnitude = BigDecimal(abs(value)).setScale(digits, RoundingMode.HALF_UP).toPlainString()
        return if (value < 0) "-$magnitude" else magnitude
    }

    /** `String(n)` / `n.toString()`: dígitos más cortos que identifican el double, sin ".0" ni notación E en el rango habitual. */
    fun numberToString(value: Double): String {
        if (value.isNaN()) return "NaN"
        if (value == 0.0) return "0"
        if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"
        val sign = if (value < 0) "-" else ""
        val decimal = BigDecimal(java.lang.Double.toString(abs(value))).stripTrailingZeros()
        val digits = decimal.unscaledValue().toString()
        val k = digits.length
        val n = k - decimal.scale() // el punto decimal queda tras n dígitos
        val body = when {
            n in k..21 -> digits + "0".repeat(n - k)
            n in 1..21 -> digits.substring(0, n) + "." + digits.substring(n)
            n in -5..0 -> "0." + "0".repeat(-n) + digits
            else -> {
                val exponent = n - 1
                val mantissa = if (k == 1) digits else digits[0] + "." + digits.substring(1)
                mantissa + "e" + (if (exponent < 0) "-" else "+") + abs(exponent)
            }
        }
        return sign + body
    }

    private val FLOAT_PREFIX = Regex("^[\\s\\uFEFF]*([+-]?(?:Infinity|(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?))")

    /** `parseFloat(text)`: lee el prefijo numérico y descarta el resto; NaN si no hay ninguno. */
    fun parseFloat(text: String?): Double {
        if (text == null) return Double.NaN
        val match = FLOAT_PREFIX.find(text) ?: return Double.NaN
        val token = match.groupValues[1]
        return when {
            token.endsWith("Infinity") -> if (token.startsWith("-")) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
            else -> token.toDoubleOrNull() ?: Double.NaN
        }
    }

    private val INT_PREFIX = Regex("^[\\s\\uFEFF]*([+-]?)(0[xX][0-9a-fA-F]+|\\d+)")

    /** `parseInt(text)` (radix 10, o 16 con prefijo 0x): lee el prefijo entero; NaN si no hay ninguno. */
    fun parseInt(text: String?): Double {
        if (text == null) return Double.NaN
        val match = INT_PREFIX.find(text) ?: return Double.NaN
        val sign = if (match.groupValues[1] == "-") -1.0 else 1.0
        val body = match.groupValues[2]
        val magnitude = if (body.startsWith("0x") || body.startsWith("0X")) {
            body.substring(2).toBigInteger(16).toDouble()
        } else {
            BigDecimal(body).toDouble()
        }
        return sign * magnitude
    }

    /** `parseInt(text) || 0` acotado al rango de Int. */
    fun parseIntOrZero(text: String?): Int = toIntClamped(parseInt(text).orZero())

    /** `Number(text)`: cadena vacía = 0; NaN si no es un número. */
    fun number(text: String?): Double {
        val trimmed = text?.trim() ?: return 0.0
        if (trimmed.isEmpty()) return 0.0
        return trimmed.toDoubleOrNull()?.takeIf { !trimmed.endsWith("d", true) && !trimmed.endsWith("f", true) } ?: Double.NaN
    }

    /** `Math.round`: el entero más cercano; los empates van hacia +∞ (distinto de `kotlin.math.round`). */
    fun round(value: Double): Double = if (value.isNaN() || value.isInfinite()) value else Math.round(value).toDouble()

    fun toIntClamped(value: Double): Int = when {
        value.isNaN() -> 0
        value >= Int.MAX_VALUE -> Int.MAX_VALUE
        value <= Int.MIN_VALUE -> Int.MIN_VALUE
        else -> value.toInt()
    }

    /** `Math.min(Math.max(Math.trunc(n) || 1, 1), 31)` */
    fun clampDay(day: Int): Int = Math.min(max(if (day == 0) 1 else day, 1), 31)
}

/** Atajo para `Number(x) || 0` sobre un `Double`. */
fun Double.orZero(): Double = if (this.isNaN() || this == 0.0) 0.0 else this
