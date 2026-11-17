package com.mixagames666.trackerrntemp.reports

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Equivalente a expo-sharing: comparte un archivo con el selector del sistema mediante un `content://`
 * del FileProvider de la app. Los archivos se escriben en `files/documents` (Paths.document) o en
 * `cache/reports` (Paths.cache).
 */
object ShareFile {
    fun documentsDir(context: Context): File = File(context.filesDir, "documents").apply { mkdirs() }

    fun cacheDir(context: Context): File = File(context.cacheDir, "reports").apply { mkdirs() }

    /** `Sharing.isAvailableAsync()`: en Android siempre se puede abrir el selector del sistema. */
    fun isAvailable(): Boolean = true

    fun share(context: Context, file: File, mimeType: String, dialogTitle: String? = null) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, dialogTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }
}
