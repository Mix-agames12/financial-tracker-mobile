package com.mixagames666.trackerrntemp.data

import com.mixagames666.trackerrntemp.util.AppLog
import com.mixagames666.trackerrntemp.util.SynchronizedLock
import com.mixagames666.trackerrntemp.util.platformFileSystem
import com.mixagames666.trackerrntemp.util.ioDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use

/**
 * Almacén de documentos JSON: una colección = un archivo `<nombre>.json` con un arreglo, igual que cada
 * clave `@tracker_gastos_<nombre>` de AsyncStorage. Las escrituras son atómicas (archivo temporal + rename)
 * y cada colección tiene su propio candado para que leer-modificar-escribir no se intercale.
 */
class JsonStore(
    private val directory: Path,
    private val fileSystem: FileSystem = platformFileSystem,
) {
    constructor(directory: String) : this(directory.toPath())

    // Mapa de candados guardado por un candado corto (ConcurrentHashMap no existe en código común).
    private val locks = HashMap<String, Mutex>()
    private val locksGuard = SynchronizedLock()

    fun lockFor(store: String): Mutex = locksGuard.withLock { locks.getOrPut(store) { Mutex() } }

    private fun file(store: String): Path = directory / "$store.json"

    /** Texto crudo de la colección; `null` si no existe (AsyncStorage.getItem). */
    suspend fun readText(store: String): String? = withContext(ioDispatcher) {
        val f = file(store)
        if (fileSystem.exists(f)) fileSystem.source(f).buffer().use { it.readUtf8() } else null
    }

    suspend fun writeText(store: String, text: String) = withContext(ioDispatcher) {
        fileSystem.createDirectories(directory)
        val target = file(store)
        val temp = directory / "$store.json.tmp"
        fileSystem.write(temp) { writeUtf8(text) }
        try {
            fileSystem.atomicMove(temp, target)
        } catch (_: Exception) {
            // Algunos sistemas de archivos no reemplazan el destino: se borra y se reintenta.
            try {
                fileSystem.delete(target)
                fileSystem.atomicMove(temp, target)
            } catch (_: Exception) {
                fileSystem.write(target) { writeUtf8(text) }
                fileSystem.delete(temp)
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
            AppLog.e(TAG, "Error loading $store", e)
            JsonArray(emptyList())
        }
    }

    suspend fun writeRaw(store: String, array: JsonArray) = writeText(store, AppJson.encodeToString(JsonArray.serializer(), array))

    companion object {
        private const val TAG = "JsonStore"
    }
}
