package com.mixagames666.trackerrntemp.data

import android.content.Context
import com.mixagames666.trackerrntemp.platform.BackupService
import com.mixagames666.trackerrntemp.reports.ShareFile
import com.mixagames666.trackerrntemp.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Archivos del backup JSON: exportar y compartir (importar lo hace el selector de documentos de la UI común). */
class BackupFiles(private val context: Context, private val backup: Backup) : BackupService {

    /** `handleExport`: escribe el JSON con sangría de 2 espacios y lo comparte. Devuelve false si no se puede compartir. */
    override suspend fun exportAndShare(): Boolean {
        val json = backup.exportJsonString()
        val fileName = "expense-tracker-backup-${Time.isoNow()}-tmp.json"
        val file = withContext(Dispatchers.IO) {
            File(ShareFile.documentsDir(context), fileName).also { it.writeText(json, Charsets.UTF_8) }
        }
        if (!ShareFile.isAvailable()) return false
        ShareFile.share(context, file, "application/json")
        return true
    }
}
