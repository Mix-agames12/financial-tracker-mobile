package com.mixagames666.trackerrntemp.data

import android.content.Context
import android.net.Uri
import com.mixagames666.trackerrntemp.reports.ShareFile
import com.mixagames666.trackerrntemp.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File

/** Archivos del backup JSON: exportar y compartir, e importar desde un documento elegido. */
class BackupFiles(private val context: Context, private val backup: Backup) {

    /** `handleExport`: escribe el JSON con sangría de 2 espacios y lo comparte. Devuelve false si no se puede compartir. */
    suspend fun exportAndShare(): Boolean {
        val json = backup.exportJsonString()
        val fileName = "expense-tracker-backup-${Time.isoNow()}-tmp.json"
        val file = withContext(Dispatchers.IO) {
            File(ShareFile.documentsDir(context), fileName).also { it.writeText(json, Charsets.UTF_8) }
        }
        if (!ShareFile.isAvailable()) return false
        ShareFile.share(context, file, "application/json")
        return true
    }

    /** Lee el documento elegido; `null` si no es un backup válido (sin `_exportDate`). Lanza si no se puede leer. */
    suspend fun readBackup(uri: Uri): JsonObject? = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: throw IllegalStateException("No se pudo abrir el archivo")
        Backup.parseBackup(text)
    }
}
