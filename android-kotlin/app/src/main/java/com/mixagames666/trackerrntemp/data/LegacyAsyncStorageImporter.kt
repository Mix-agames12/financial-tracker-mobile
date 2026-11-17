package com.mixagames666.trackerrntemp.data

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import java.io.File

/**
 * Migración desde la app React Native. AsyncStorage guardaba cada colección como una fila
 * `@tracker_gastos_<colección>` → texto JSON en la base SQLite `RKStorage`; el almacén de esta app usa el
 * mismo documento, así que al instalar esta versión sobre la de React Native (mismo paquete y firma) los
 * datos del usuario se copian tal cual, una sola vez.
 *
 * Sólo actúa si el almacén propio todavía no tiene ninguna colección (primer arranque): nunca pisa datos
 * ya guardados por esta app. Debe ejecutarse antes de cualquier lectura o escritura del almacén.
 */
class LegacyAsyncStorageImporter(
    private val databaseFile: File,
    private val storeDirectory: File,
    private val store: JsonStore,
) {

    /** Devuelve cuántas colecciones se importaron (0 si no había nada que migrar). Nunca lanza. */
    suspend fun importIfNeeded(): Int = withContext(Dispatchers.IO) {
        if (storeDirectory.listFiles { f -> f.extension == "json" }?.isNotEmpty() == true) return@withContext 0
        if (!databaseFile.exists()) return@withContext 0
        try {
            importRows(readRows())
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron importar los datos de la versión React Native", e)
            0
        }
    }

    private fun readRows(): Map<String, String> {
        val keys = ALL_STORES.map { "$PREFIX$it" }
        val rows = LinkedHashMap<String, String>()
        SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val placeholders = keys.joinToString(",") { "?" }
            db.rawQuery("SELECT key, value FROM catalystLocalStorage WHERE key IN ($placeholders)", keys.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) rows[cursor.getString(0)] = cursor.getString(1)
            }
        }
        return rows
    }

    /** Copia cada colección conocida cuyo valor sea un arreglo JSON; devuelve cuántas se importaron. */
    suspend fun importRows(rows: Map<String, String>): Int {
        var imported = 0
        for (name in ALL_STORES) {
            val text = rows["$PREFIX$name"] ?: continue
            val isArray = try {
                AppJson.parseToJsonElement(text) is JsonArray
            } catch (_: Exception) {
                false
            }
            if (!isArray) continue
            store.lockFor(name).withLock { store.writeText(name, text) }
            imported++
        }
        return imported
    }

    companion object {
        private const val TAG = "LegacyImporter"
        private const val PREFIX = "@tracker_gastos_" // STORAGE_PREFIX de storage.ts
    }
}
