package com.mixagames666.trackerrntemp.platform

import com.mixagames666.trackerrntemp.data.Aggregates
import com.mixagames666.trackerrntemp.data.Backup
import com.mixagames666.trackerrntemp.data.Repos
import com.mixagames666.trackerrntemp.reports.PdfModel
import com.mixagames666.trackerrntemp.reports.ReportData
import com.mixagames666.trackerrntemp.reports.ReportPeriod
import com.mixagames666.trackerrntemp.reports.ReportType
import com.mixagames666.trackerrntemp.reports.buildCsv
import com.mixagames666.trackerrntemp.reports.buildPdfModel
import com.mixagames666.trackerrntemp.reports.loadReportData
import com.mixagames666.trackerrntemp.reports.selectRecords
import com.mixagames666.trackerrntemp.util.Time
import com.mixagames666.trackerrntemp.util.getToday
import com.mixagames666.trackerrntemp.util.ioDispatcher
import com.mixagames666.trackerrntemp.util.platformFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory

/** Ruta en la carpeta temporal de la app (el sistema la limpia sola) para un archivo que se va a compartir. */
private fun tempPath(fileName: String): String = NSTemporaryDirectory().trimEnd('/') + "/" + fileName

private suspend fun writeTempText(fileName: String, text: String): String = withContext(ioDispatcher) {
    val path = tempPath(fileName)
    platformFileSystem.write(path.toPath()) { writeUtf8(text) }
    path
}

/** Backup JSON: se escribe en la carpeta temporal y se comparte con la hoja del sistema (Guardar en Archivos, AirDrop…). */
class IosBackupFiles(private val backup: Backup) : BackupService {
    override suspend fun exportAndShare(): Boolean {
        val json = backup.exportJsonString()
        // Sin ':' en el nombre (en Archivos se vería como '/').
        val fileName = "expense-tracker-backup-${Time.isoNow().replace(':', '-')}.json"
        return shareFile(writeTempText(fileName, json))
    }
}

/** Reportes CSV y PDF: mismos datos que Android (código común); el PDF se dibuja con UIKit ([IosPdfWriter]). */
class IosReportService(private val repos: Repos, private val aggregates: Aggregates) : ReportService {
    override suspend fun loadData(type: ReportType, period: ReportPeriod): ReportData =
        loadReportData(repos, aggregates, type, period)

    override suspend fun generateCsv(data: ReportData): Boolean {
        val (content, fileName) = buildCsv(data)
        return shareFile(writeTempText(fileName, content))
    }

    override suspend fun generatePdf(data: ReportData): Boolean {
        val selected = selectRecords(data)
        val path = tempPath("reporte_${selected.range.fileTag}_${getToday()}.pdf")
        val model: PdfModel = buildPdfModel(data)
        // Dibujar con UIKit fuera del hilo principal es válido para contextos PDF (UIGraphics* es seguro por hilo).
        withContext(Dispatchers.Default) { IosPdfWriter.write(path, model) }
        return shareFile(path)
    }
}
