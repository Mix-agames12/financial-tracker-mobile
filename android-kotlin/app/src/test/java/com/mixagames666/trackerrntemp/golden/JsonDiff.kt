package com.mixagames666.trackerrntemp.golden

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** Compara dos documentos JSON y devuelve las diferencias (números por valor, no por texto). */
object JsonDiff {
    private fun normalizeText(text: String) = text.replace(' ', ' ').replace(' ', ' ')

    fun diff(expected: JsonElement?, actual: JsonElement?, path: String = "$", out: MutableList<String> = mutableListOf()): List<String> {
        val e = expected ?: JsonNull
        val a = actual ?: JsonNull
        when {
            e is JsonObject && a is JsonObject -> {
                (e.keys + a.keys).forEach { key ->
                    val ev = e[key]
                    val av = a[key]
                    when {
                        ev == null -> out.add("$path.$key: sobra en Kotlin = $av")
                        av == null -> out.add("$path.$key: falta en Kotlin (esperado $ev)")
                        else -> diff(ev, av, "$path.$key", out)
                    }
                }
            }

            e is JsonArray && a is JsonArray -> {
                if (e.size != a.size) out.add("$path: tamaño esperado ${e.size}, Kotlin ${a.size}")
                for (i in 0 until minOf(e.size, a.size)) diff(e[i], a[i], "$path[$i]", out)
            }

            e is JsonPrimitive && a is JsonPrimitive -> {
                if (e is JsonNull || a is JsonNull) {
                    if (e is JsonNull != a is JsonNull) out.add("$path: esperado $e, Kotlin $a")
                } else if (!e.isString && !a.isString) {
                    val ed = e.doubleOrNull
                    val ad = a.doubleOrNull
                    val same = if (ed != null && ad != null) ed == ad else e.content == a.content
                    if (!same) out.add("$path: esperado ${e.content}, Kotlin ${a.content}")
                } else if (normalizeText(e.content) != normalizeText(a.content) || e.isString != a.isString) {
                    out.add("$path: esperado \"${e.content}\", Kotlin \"${a.content}\"")
                }
            }

            else -> out.add("$path: tipo distinto (esperado $e, Kotlin $a)")
        }
        return out
    }
}
