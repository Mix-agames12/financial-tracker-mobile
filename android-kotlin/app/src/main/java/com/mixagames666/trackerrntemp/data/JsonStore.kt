package com.mixagames666.trackerrntemp.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Almacén de documentos JSON: una colección = un archivo `<nombre>.json` con un arreglo, igual que cada
 * clave `@tracker_gastos_<nombre>` de AsyncStorage. Las escrituras son atómicas (archivo temporal + rename)
 * y cada colección tiene su propio candado para que leer-modificar-escribir no se intercale.
 */
class JsonStore(private val directory: File) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    fun lockFor(store: String): Mutex = locks.getOrPut(store) { Mutex() }

    private fun file(store: String) = File(directory, "$store.json")

    /** Texto crudo de la colección; `null` si no existe (AsyncStorage.getItem). */
    suspend fun readText(store: String): String? = withContext(Dispatchers.IO) {
        val f = file(store)
        if (f.exists()) f.readText(Charsets.UTF_8) else null
    }

    suspend fun writeText(store: String, text: String) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val target = file(store)
        val temp = File(directory, "$store.json.tmp")
        temp.writeText(text, Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            target.delete()
            if (!temp.renameTo(target)) {
                target.writeText(text, Charsets.UTF_8)
                temp.delete()
            }
        }
    }

    /** Arreglo JSON crudo de la colección (vacío si falta o está corrupto). */
    suspend fun readRaw(store: String): JsonArray {
        val text = readText(store) ?: return JsonArray(emptyList())
        return try {
            val element: JsonElement = AppJson.parseToJsonElement(text)
            element as? JsonArray ?: JsonArray(emptyList())
        } catch (e: Exception) {
            Log.e(TAG, "Error loading $store", e)
            JsonArray(emptyList())
        }
    }

    suspend fun writeRaw(store: String, array: JsonArray) = writeText(store, AppJson.encodeToString(JsonArray.serializer(), array))

    companion object {
        private const val TAG = "JsonStore"
    }
}
