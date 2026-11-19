package com.mixagames666.trackerrntemp.data

import com.mixagames666.trackerrntemp.util.Time
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Export/import/borrado de todos los datos (exportAllData, importAllData, clearAllData y hasAnyData de
 * storage.ts). Trabaja con el JSON crudo de cada colección, así el backup conserva todo lo guardado.
 */
class Backup(private val store: JsonStore) {

    /** Documento del backup: una clave por colección + `_exportDate`. */
    suspend fun exportAllData(): JsonObject {
        val arrays = ALL_STORES.associateWith { store.readRaw(it) }
        return buildJsonObject {
            ALL_STORES.forEach { put(it, arrays.getValue(it)) }
            put("_exportDate", JsonPrimitive(Time.isoNow()))
        }
    }

    /** `JSON.stringify(data, null, 2)` */
    suspend fun exportJsonString(): String = PRETTY.encodeToString(JsonObject.serializer(), exportAllData())

    /** Sólo se sustituyen las colecciones presentes en el archivo que son arreglos. */
    suspend fun importAllData(data: JsonObject) {
        for (name in ALL_STORES) {
            val value: JsonElement? = data[name]
            if (value is JsonArray) {
                store.lockFor(name).withLock { store.writeRaw(name, value) }
            }
        }
    }

    /** Vacía todas las colecciones menos los ajustes. */
    suspend fun clearAllData() {
        for (name in ALL_STORES) {
            if (name == "settings") continue // keep settings
            store.lockFor(name).withLock { store.writeText(name, "[]") }
        }
    }

    suspend fun hasAnyData(): Boolean {
        for (name in ALL_STORES) {
            if (name == "settings" || name == "categories") continue
            if (store.readRaw(name).isNotEmpty()) return true
        }
        return false
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        private val PRETTY = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
            encodeDefaults = true
            explicitNulls = false
        }

        /** Lee el texto de un backup; `null` si no es un objeto con `_exportDate` (archivo inválido). */
        fun parseBackup(text: String): JsonObject? {
            val element = AppJson.parseToJsonElement(text)
            val obj = element as? JsonObject ?: return null
            val exportDate = obj["_exportDate"]
            if (exportDate == null || (exportDate is JsonPrimitive && !exportDate.isString && exportDate.content == "null")) return null
            if (exportDate is JsonPrimitive && exportDate.isString && exportDate.content.isEmpty()) return null
            return obj
        }
    }
}
