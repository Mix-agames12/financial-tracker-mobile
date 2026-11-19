package com.mixagames666.trackerrntemp.reports

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import kotlin.math.max

// Port del PDF de reportGenerator.ts. El original lo arma como HTML y lo imprime con expo-print; aquí el
// mismo contenido se calcula como modelo (PdfModel, probado contra el HTML original) y se dibuja con
// PdfDocument en hoja carta (612×792), con los mismos colores, secciones y orden.

object PdfReport {
    const val PAGE_WIDTH = 612
    const val PAGE_HEIGHT = 792
    private const val MARGIN = 20f

    fun buildModel(data: ReportData): PdfModel = buildPdfModel(data)

    fun write(file: File, data: ReportData) {
        val model = buildModel(data)
        val doc = PdfDocument()
        try {
            PdfPainter(doc).draw(model)
            file.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Dibujo
    // ---------------------------------------------------------------------------------------------

    private class PdfPainter(private val doc: PdfDocument) {
        private var pageNumber = 0
        private lateinit var page: PdfDocument.Page
        private lateinit var canvas: Canvas
        private var y = MARGIN
        private val contentWidth = PAGE_WIDTH - 2 * MARGIN
        private val bottomLimit = PAGE_HEIGHT - MARGIN

        private fun text(size: Float, color: String, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = Color.parseColor(color)
            typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
        }

        private fun fill(color: String) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = Color.parseColor(color) }

        private fun stroke(color: String, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; this.color = Color.parseColor(color); strokeWidth = width
        }

        private fun newPage() {
            if (pageNumber > 0) doc.finishPage(page)
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN
        }

        private fun ensure(height: Float, onNewPage: (() -> Unit)? = null) {
            if (y + height > bottomLimit) {
                newPage()
                onNewPage?.invoke()
            }
        }

        private fun layout(text: String, paint: TextPaint, width: Float, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, spacing: Float = 1f): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, max(1, width.toInt()))
                .setAlignment(align).setLineSpacing(0f, spacing).setIncludePad(false).build()

        private fun drawLayout(l: StaticLayout, x: Float, top: Float) {
            canvas.save()
            canvas.translate(x, top)
            l.draw(canvas)
            canvas.restore()
        }

        fun draw(m: PdfModel) {
            newPage()
            header(m)
            sectionTitle("Resumen del período", keepWith = 70f)
            stats(m)
            if (m.includeIncomes && m.includeExpenses) donuts(m)
            if (m.includeExpenses) {
                sectionTitle("Gastos por categoría", keepWith = 30f)
                barCard(m.categoryBars)
            }
            if (m.includeIncomes) {
                sectionTitle("Ingresos por fuente", keepWith = 30f)
                barCard(m.sourceBars)
            }
            sectionTitle("Saldos actuales en cuentas", keepWith = 36f)
            balances(m)
            sectionTitle("Detalle de movimientos", keepWith = 70f)
            movements(m)
            doc.finishPage(page)
        }

        private fun header(m: PdfModel) {
            val title = layout("Reporte financiero", text(26f, "#1C3E49", bold = true), contentWidth)
            drawLayout(title, MARGIN, y)
            y += title.height + 8f

            val regular = text(13f, "#666666")
            val line = android.text.SpannableStringBuilder().apply {
                append("Período:", android.text.style.StyleSpan(Typeface.BOLD), 0)
                append(" ${m.periodLabel} · ")
                append("Contenido:", android.text.style.StyleSpan(Typeface.BOLD), 0)
                append(" ${m.typeLabel}")
            }
            val l1 = StaticLayout.Builder.obtain(line, 0, line.length, regular, contentWidth.toInt()).setLineSpacing(0f, 1.5f).setIncludePad(false).build()
            drawLayout(l1, MARGIN, y)
            y += l1.height
            val l2 = layout("Generado el ${m.generatedOn}", regular, contentWidth, spacing = 1.5f)
            drawLayout(l2, MARGIN, y)
            y += l2.height + 16f
            canvas.drawRect(MARGIN, y, MARGIN + contentWidth, y + 2f, fill("#2B9396"))
            y += 2f + 24f
        }

        /** [keepWith]: alto mínimo del primer contenido, para que el título no quede solo al final de una página. */
        private fun sectionTitle(title: String, keepWith: Float) {
            val paint = text(16f, "#1C3E49", bold = true)
            val height = 8f + paint.fontSpacing + 8f
            ensure(24f + height + 12f + keepWith)
            y += 24f
            canvas.drawRoundRect(RectF(MARGIN, y, MARGIN + contentWidth, y + height), 4f, 4f, fill("#EEF2F6"))
            canvas.drawText(title, MARGIN + 12f, y + 8f - paint.fontMetrics.ascent, paint)
            y += height + 12f
        }

        private fun stats(m: PdfModel) {
            val gap = 12f
            val minWidth = 120f
            val perRow = max(1, ((contentWidth + gap) / (minWidth + gap)).toInt())
            m.stats.chunked(perRow).forEach { rowStats ->
                val boxWidth = (contentWidth - gap * (rowStats.size - 1)) / rowStats.size
                val labelPaint = text(12f, "#888888")
                val boxHeight = 12f + labelPaint.fontSpacing + 4f + text(18f, "#333333", bold = true).fontSpacing + 12f
                ensure(boxHeight)
                rowStats.forEachIndexed { i, s ->
                    val x = MARGIN + i * (boxWidth + gap)
                    val rect = RectF(x, y, x + boxWidth, y + boxHeight)
                    canvas.drawRoundRect(rect, 6f, 6f, fill("#FAFAFA"))
                    canvas.drawRoundRect(rect, 6f, 6f, stroke("#DDDDDD", 1f))
                    canvas.drawText(s.label, x + 12f, y + 12f - labelPaint.fontMetrics.ascent, labelPaint)
                    val valuePaint = text(18f, s.color ?: "#333333", bold = true)
                    canvas.drawText(s.value, x + 12f, y + 12f + labelPaint.fontSpacing + 4f - valuePaint.fontMetrics.ascent, valuePaint)
                }
                y += boxHeight + gap
            }
        }

        private fun donuts(m: PdfModel) {
            val size = 110f
            val label = text(13f, "#444444", bold = true)
            val total = 24f + size + 8f + label.fontSpacing + 8f
            ensure(total)
            y += 24f
            val centers = listOf(MARGIN + contentWidth * 0.25f, MARGIN + contentWidth * 0.75f)
            val items = listOf(
                Triple("Gastos sobre ingresos", m.expensePercentage, "#FF5722"),
                Triple("Margen de ahorro", m.balancePercentage, "#2196F3"),
            )
            items.forEachIndexed { i, (name, percent, color) ->
                val cx = centers[i]
                val cy = y + size / 2
                val ringRadius = (size + 74f) / 4f // radio medio del anillo (exterior 110, interior 74)
                val oval = RectF(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)
                val track = stroke("#E0E0E0", (size - 74f) / 2f)
                canvas.drawArc(oval, 0f, 360f, false, track)
                if (percent > 0) canvas.drawArc(oval, -90f, percent * 3.6f, false, stroke(color, (size - 74f) / 2f).apply { strokeCap = Paint.Cap.BUTT })
                val value = text(22f, "#333333", bold = true)
                val textWidth = value.measureText("$percent%")
                canvas.drawText("$percent%", cx - textWidth / 2, cy - (value.fontMetrics.ascent + value.fontMetrics.descent) / 2, value)
                val labelWidth = label.measureText(name)
                canvas.drawText(name, cx - labelWidth / 2, y + size + 8f - label.fontMetrics.ascent, label)
            }
            y += size + 8f + label.fontSpacing + 8f
        }

        private fun emptyText(message: String) {
            val paint = text(13f, "#999999")
            val width = paint.measureText(message)
            ensure(paint.fontSpacing + 8f)
            canvas.drawText(message, MARGIN + (contentWidth - width) / 2, y - paint.fontMetrics.ascent, paint)
            y += paint.fontSpacing + 8f
        }

        private fun barCard(bars: List<PdfBar>) {
            if (bars.isEmpty()) {
                emptyText("Sin movimientos en el período")
                return
            }
            val labelPaint = text(12f, "#555555")
            val valuePaint = text(11f, "#333333")
            val rowHeight = 14f + 10f
            val labelWidth = 130f
            val valueWidth = 80f + 8f
            bars.forEach { bar ->
                ensure(rowHeight)
                val barX = MARGIN + labelWidth + 10f
                val barWidth = contentWidth - labelWidth - 10f - valueWidth
                val labelText = android.text.TextUtils.ellipsize(bar.label, labelPaint, labelWidth - 10f, android.text.TextUtils.TruncateAt.END).toString()
                canvas.drawText(labelText, MARGIN + labelWidth - 10f - labelPaint.measureText(labelText), y + 11f, labelPaint)
                canvas.drawRoundRect(RectF(barX, y, barX + barWidth, y + 14f), 7f, 7f, fill("#EDEDED"))
                val fillWidth = barWidth * (bar.widthPercent.toDoubleOrNull() ?: 0.0).toFloat() / 100f
                if (fillWidth > 0f) canvas.drawRoundRect(RectF(barX, y, barX + max(fillWidth, 0f), y + 14f), 7f, 7f, fill(bar.color))
                canvas.drawText(bar.value, barX + barWidth + 8f, y + 11f, valuePaint)
                y += rowHeight
            }
        }

        private fun balances(m: PdfModel) {
            if (m.noAccounts) {
                emptyText("Sin cuentas registradas")
                return
            }
            val left = text(14f, "#333333")
            m.balances.forEach { b ->
                ensure(left.fontSpacing + 12f)
                val right = text(14f, b.color)
                canvas.drawText(b.label, MARGIN + 14f, y + 6f - left.fontMetrics.ascent, left)
                canvas.drawText(b.value, MARGIN + contentWidth - 14f - right.measureText(b.value), y + 6f - right.fontMetrics.ascent, right)
                val lineY = y + left.fontSpacing + 12f
                canvas.drawLine(MARGIN, lineY, MARGIN + contentWidth, lineY, stroke("#DDDDDD", 1f).apply {
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f, 3f), 0f)
                })
                y = lineY
            }
            y += 4f
        }

        private fun movements(m: PdfModel) {
            if (m.movements.isEmpty()) {
                emptyText("Sin movimientos en el período")
                return
            }
            val widths = listOf(0.15f, 0.30f, 0.18f, 0.22f, 0.15f).map { it * contentWidth }
            val xs = widths.runningFold(MARGIN) { acc, w -> acc + w }
            val headerPaint = text(11f, "#FFFFFF", bold = true)
            val cellPaint = text(11f, "#333333")

            fun drawHeader() {
                val h = 6f + headerPaint.fontSpacing + 6f
                canvas.drawRect(MARGIN, y, MARGIN + contentWidth, y + h, fill("#1C3E49"))
                listOf("Fecha", "Detalle", "Categoría", "Medio de pago", "Monto").forEachIndexed { i, title ->
                    val x = if (i == 4) xs[i] + widths[i] - 8f - headerPaint.measureText(title) else xs[i] + 8f
                    canvas.drawText(title, x, y + 6f - headerPaint.fontMetrics.ascent, headerPaint)
                }
                y += h
            }

            ensure(40f)
            drawHeader()
            m.movements.forEach { mv ->
                val cells = listOf(mv.date, mv.detail, mv.category, mv.method)
                val layouts = cells.mapIndexed { i, t -> layout(t, cellPaint, widths[i] - 16f) }
                val rowHeight = 6f + (layouts.maxOf { it.height }) + 6f
                ensure(rowHeight) { drawHeader() }
                layouts.forEachIndexed { i, l -> drawLayout(l, xs[i] + 8f, y + 6f) }
                val amountPaint = text(11f, if (mv.isIncome) "#2E7D32" else "#E74C3C")
                canvas.drawText(mv.amount, xs[4] + widths[4] - 8f - amountPaint.measureText(mv.amount), y + 6f - amountPaint.fontMetrics.ascent, amountPaint)
                y += rowHeight
                canvas.drawLine(MARGIN, y, MARGIN + contentWidth, y, stroke("#E5E5E5", 1f))
            }
        }
    }
}
