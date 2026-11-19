package com.mixagames666.trackerrntemp.reports

import android.content.Context
import com.mixagames666.trackerrntemp.data.Aggregates
import com.mixagames666.trackerrntemp.data.Repos
import com.mixagames666.trackerrntemp.platform.ReportService
import com.mixagames666.trackerrntemp.util.getToday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Genera y comparte los reportes (CSV y PDF) del selector de Configuración. */
class ReportGenerator(
    private val context: Context,
    private val repos: Repos,
    private val aggregates: Aggregates,
) : ReportService {
    override suspend fun loadData(type: ReportType, period: ReportPeriod): ReportData = loadReportData(repos, aggregates, type, period)

    /** Genera y comparte el CSV. Devuelve false si el dispositivo no permite compartir archivos. */
    override suspend fun generateCsv(data: ReportData): Boolean {
        val (content, fileName) = buildCsv(data)
        val file = withContext(Dispatchers.IO) {
            File(ShareFile.documentsDir(context), fileName).also { it.writeText(content, Charsets.UTF_8) }
        }
        if (!ShareFile.isAvailable()) return false
        ShareFile.share(context, file, "text/csv", "Compartir reporte CSV")
        return true
    }

    /** Genera y comparte el PDF. Devuelve false si el dispositivo no permite compartir archivos. */
    override suspend fun generatePdf(data: ReportData): Boolean {
        val selected = selectRecords(data)
        val fileName = "reporte_${selected.range.fileTag}_${getToday()}.pdf"
        val file = withContext(Dispatchers.IO) {
            File(ShareFile.cacheDir(context), fileName).also { PdfReport.write(it, data) }
        }
        if (!ShareFile.isAvailable()) return false
        ShareFile.share(context, file, "application/pdf", "Compartir reporte PDF")
        return true
    }
}
