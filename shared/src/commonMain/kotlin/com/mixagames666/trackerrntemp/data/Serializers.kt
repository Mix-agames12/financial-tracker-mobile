package com.mixagames666.trackerrntemp.data

import com.mixagames666.trackerrntemp.util.JsCompat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.math.round

/**
 * Configuración JSON compartida: los mismos documentos que escribe AsyncStorage (`JSON.stringify`).
 * - `ignoreUnknownKeys`: los registros pueden traer campos que este modelo no declara.
 * - `explicitNulls = false`: un campo `undefined` del RN se omite, igual que `JSON.stringify`.
 * - `encodeDefaults = true`: se escribe siempre lo que el RN habría escrito (p. ej. `amount: 0`).
 */
@OptIn(ExperimentalSerializationApi::class)
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
    isLenient = true
}

/** Lee un número tolerando textos ("12.5"), booleanos y nulos, como `Number(x) || 0` en el RN. */
private fun JsonDecoder.lenientNumber(): Double {
    val element = decodeJsonElement()
    if (element !is JsonPrimitive || element is JsonNull) return 0.0
    if (element.isString) {
        val parsed = JsCompat.number(element.content)
        return if (parsed.isNaN()) 0.0 else parsed
    }
    element.content.toDoubleOrNull()?.let { return it }
    return when (element.content) {
        "true" -> 1.0
        else -> 0.0
    }
}

object LenientDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double =
        (decoder as? JsonDecoder)?.lenientNumber() ?: decoder.decodeDouble()

    /** Los enteros se escriben sin ".0" (`1500`), como hace `JSON.stringify`. */
    override fun serialize(encoder: Encoder, value: Double) {
        if (value.isFinite() && value == round(value) && abs(value) < 1e15) {
            encoder.encodeLong(value.toLong())
        } else if (value.isFinite()) {
            encoder.encodeDouble(value)
        } else {
            encoder.encodeLong(0) // JSON.stringify(NaN/Infinity) escribe null; se normaliza a 0
        }
    }
}

object LenientIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int =
        (decoder as? JsonDecoder)?.let { JsCompat.toIntClamped(it.lenientNumber()) } ?: decoder.decodeInt()

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

object LenientLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientLong", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long =
        (decoder as? JsonDecoder)?.lenientNumber()?.toLong() ?: decoder.decodeLong()

    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}

/** Acepta números y booleanos donde se espera texto (un `detail` numérico importado, p. ej.). */
object LenientStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val json = decoder as? JsonDecoder ?: return decoder.decodeString()
        val element = json.decodeJsonElement()
        if (element !is JsonPrimitive || element is JsonNull) return ""
        if (element.isString) return element.content
        val asNumber = element.content.toDoubleOrNull()
        return if (asNumber != null) JsCompat.numberToString(asNumber) else element.content
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

object LenientBooleanSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientBoolean", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean {
        val json = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        val element = json.decodeJsonElement()
        if (element !is JsonPrimitive || element is JsonNull) return false
        if (!element.isString) {
            element.content.toBooleanStrictOrNull()?.let { return it }
            return (element.content.toDoubleOrNull() ?: 0.0) != 0.0
        }
        return element.content.isNotEmpty() && element.content != "false" && element.content != "0"
    }

    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
}
