package com.mixagames666.trackerrntemp.util

/**
 * Aritmética decimal exacta sin BigDecimal, para plataformas sin java.math (iOS). Reproduce el cálculo de
 * `Number.prototype.toFixed` de JavaScript: se toma la expansión decimal EXACTA del double (no su cadena corta)
 * y se redondea con empate hacia el valor mayor (1.005.toFixed(2) = "1.00" porque 1.005 es en realidad
 * 1.00499999999999989…, y 2.5.toFixed(0) = "3"). Las pruebas JVM comparan este código con BigDecimal.
 */
object ExactDecimal {
    private const val BASE = 1_000_000_000L

    /** `value.toFixed(digits)` para `0 <= value < 1e21` finito, sin signo. */
    fun toFixedMagnitude(value: Double, digits: Int): String {
        val bits = value.toRawBits()
        val rawExp = ((bits ushr 52) and 0x7FF).toInt()
        val fraction = bits and 0xFFFFFFFFFFFFFL
        val mantissa = if (rawExp == 0) fraction else fraction or (1L shl 52)
        val exponent = if (rawExp == 0) -1074 else rawExp - 1075 // valor = mantissa * 2^exponent

        // valor = D / 10^scale con D entero: mantissa * 2^e (scale 0) o mantissa * 5^k / 10^k (e = -k).
        val limbs = mutableListOf(mantissa % BASE, mantissa / BASE)
        trim(limbs)
        val scale: Int
        if (exponent >= 0) {
            multiplyRepeated(limbs, 2L, exponent, 29)
            scale = 0
        } else {
            multiplyRepeated(limbs, 5L, -exponent, 13)
            scale = -exponent
        }
        val all = toDecimalString(limbs)

        // Pasa a exactamente `digits` decimales.
        val padded: String = if (scale <= digits) {
            all + "0".repeat(digits - scale)
        } else {
            val drop = scale - digits
            val full = all.padStart(drop + 1, '0')
            val kept = full.substring(0, full.length - drop)
            val roundUp = full[full.length - drop] >= '5' // la expansión es exacta: basta el primer dígito descartado
            if (roundUp) increment(kept) else kept
        }
        val withInt = padded.padStart(digits + 1, '0')
        val intPart = withInt.substring(0, withInt.length - digits)
        return if (digits == 0) intPart else intPart + "." + withInt.substring(withInt.length - digits)
    }

    /**
     * Dígitos y posición del punto a partir de `Double.toString` (formato "123.45", "1.0E10", "1.5E-7"),
     * equivalentes a BigDecimal(...).stripTrailingZeros(): valor = 0.DIGITOS × 10^pointPosition.
     */
    fun shortestFromString(text: String): ShortestDigits {
        val e = text.indexOfFirst { it == 'E' || it == 'e' }
        val mantissa = if (e >= 0) text.substring(0, e) else text
        val exp10 = if (e >= 0) text.substring(e + 1).toInt() else 0
        val dot = mantissa.indexOf('.')
        val intPart = if (dot >= 0) mantissa.substring(0, dot) else mantissa
        val fracPart = if (dot >= 0) mantissa.substring(dot + 1) else ""
        var digits = intPart + fracPart
        var point = intPart.length + exp10
        val leading = digits.length - digits.trimStart('0').length
        digits = digits.substring(leading)
        point -= leading
        digits = digits.trimEnd('0')
        return ShortestDigits(digits.ifEmpty { "0" }, point)
    }

    private fun trim(limbs: MutableList<Long>) {
        while (limbs.size > 1 && limbs.last() == 0L) limbs.removeAt(limbs.size - 1)
    }

    /** limbs (base 1e9, menos significativo primero) *= factor^times, en tandas de [chunk] factores por pasada. */
    private fun multiplyRepeated(limbs: MutableList<Long>, factor: Long, times: Int, chunk: Int) {
        var remaining = times
        while (remaining > 0) {
            val n = if (remaining < chunk) remaining else chunk
            var multiplier = 1L
            repeat(n) { multiplier *= factor }
            multiply(limbs, multiplier)
            remaining -= n
        }
    }

    private fun multiply(limbs: MutableList<Long>, multiplier: Long) {
        var carry = 0L
        for (i in limbs.indices) {
            val product = limbs[i] * multiplier + carry
            limbs[i] = product % BASE
            carry = product / BASE
        }
        while (carry > 0) {
            limbs.add(carry % BASE)
            carry /= BASE
        }
    }

    private fun toDecimalString(limbs: List<Long>): String {
        val sb = StringBuilder(limbs.last().toString())
        for (i in limbs.size - 2 downTo 0) sb.append(limbs[i].toString().padStart(9, '0'))
        return sb.toString()
    }

    private fun increment(digits: String): String {
        val chars = digits.toCharArray()
        var i = chars.size - 1
        while (i >= 0) {
            if (chars[i] == '9') {
                chars[i] = '0'
                i--
            } else {
                chars[i] = chars[i] + 1
                return chars.concatToString()
            }
        }
        return "1" + chars.concatToString()
    }
}
