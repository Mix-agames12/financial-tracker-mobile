package com.mixagames666.trackerrntemp.platform

import com.mixagames666.trackerrntemp.reports.PdfBar
import com.mixagames666.trackerrntemp.reports.PdfModel
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSAttributedString
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.NSLineBreakByTruncatingTail
import platform.UIKit.NSLineBreakByWordWrapping
import platform.UIKit.NSMutableParagraphStyle
import platform.UIKit.NSParagraphStyleAttributeName
import platform.UIKit.NSStringDrawingTruncatesLastVisibleLine
import platform.UIKit.NSStringDrawingUsesLineFragmentOrigin
import platform.UIKit.NSTextAlignment
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.NSTextAlignmentLeft
import platform.UIKit.NSTextAlignmentRight
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsBeginPDFContextToFile
import platform.UIKit.UIGraphicsBeginPDFPageWithInfo
import platform.UIKit.UIGraphicsEndPDFContext
import platform.UIKit.boundingRectWithSize
import platform.UIKit.drawWithRect
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max

/**
 * PDF del reporte en iOS: dibuja el [PdfModel] (el mismo de Android) con UIKit en hoja carta (612×792 pt), con las
 * mismas secciones, colores y orden que `PdfReport` de Android: título y período, resumen, anillos de porcentaje,
 * gastos por categoría, ingresos por fuente, saldos de cuentas y detalle de movimientos con paginación.
 *
 * Diferencias respecto a Android (simplificaciones): el período y el contenido van en una línea sin saltos; las
 * líneas punteadas de la sección de saldos son sólidas; el texto usa la fuente del sistema (San Francisco).
 *
 * Usa las funciones `UIGraphicsBeginPDFContextToFile`/`UIGraphicsBeginPDFPageWithInfo` (en lugar de
 * `UIGraphicsPDFRenderer`, cuya API con bloques es más delicada desde Kotlin/Native); el sistema de coordenadas es
 * el de UIKit (origen arriba a la izquierda, como el `Canvas` de Android).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal object IosPdfWriter {
    private const val PAGE_WIDTH = 612.0
    private const val PAGE_HEIGHT = 792.0
    private const val MARGIN = 20.0

    fun write(path: String, model: PdfModel) {
        UIGraphicsBeginPDFContextToFile(path, CGRectMake(0.0, 0.0, PAGE_WIDTH, PAGE_HEIGHT), null)
        try {
            Painter(model).draw()
        } finally {
            UIGraphicsEndPDFContext()
        }
    }

    private class Painter(private val m: PdfModel) {
        private val contentWidth = PAGE_WIDTH - 2 * MARGIN
        private val bottomLimit = PAGE_HEIGHT - MARGIN
        private var y = MARGIN

        // --- Primitivas ---------------------------------------------------------------------------

        private fun color(hex: String): UIColor {
            val v = hex.removePrefix("#").toInt(16)
            return UIColor.colorWithRed(
                red = ((v shr 16) and 0xFF) / 255.0,
                green = ((v shr 8) and 0xFF) / 255.0,
                blue = (v and 0xFF) / 255.0,
                alpha = 1.0,
            )
        }

        private fun font(size: Double, bold: Boolean): UIFont =
            if (bold) UIFont.boldSystemFontOfSize(size) else UIFont.systemFontOfSize(size)

        private fun lineHeight(size: Double, bold: Boolean = false): Double = font(size, bold).lineHeight

        private fun attributed(
            text: String,
            size: Double,
            hex: String,
            bold: Boolean,
            align: NSTextAlignment,
            truncate: Boolean,
        ): NSAttributedString {
            val paragraph = NSMutableParagraphStyle()
            paragraph.alignment = align
            paragraph.lineBreakMode = if (truncate) NSLineBreakByTruncatingTail else NSLineBreakByWordWrapping
            return NSAttributedString.create(
                string = text,
                attributes = mapOf<Any?, Any?>(
                    NSFontAttributeName to font(size, bold),
                    NSForegroundColorAttributeName to color(hex),
                    NSParagraphStyleAttributeName to paragraph,
                ),
            )
        }

        /** Alto que ocupa [text] ajustado al ancho [width]. */
        private fun textHeight(text: String, width: Double, fontSize: Double, bold: Boolean = false): Double =
            attributed(text, fontSize, "#000000", bold, NSTextAlignmentLeft, false)
                .boundingRectWithSize(CGSizeMake(width, 100000.0), NSStringDrawingUsesLineFragmentOrigin, null)
                .useContents { ceil(size.height) }

        /** Ancho de [text] en una sola línea. */
        private fun textWidth(text: String, fontSize: Double, bold: Boolean = false): Double =
            attributed(text, fontSize, "#000000", bold, NSTextAlignmentLeft, false)
                .boundingRectWithSize(CGSizeMake(100000.0, 100000.0), NSStringDrawingUsesLineFragmentOrigin, null)
                .useContents { ceil(size.width) }

        /** Dibuja [text] con su borde superior en [top]; devuelve el alto usado (una sola línea si [truncate]). */
        private fun drawText(
            text: String,
            x: Double,
            top: Double,
            width: Double,
            size: Double,
            hex: String,
            bold: Boolean = false,
            align: NSTextAlignment = NSTextAlignmentLeft,
            truncate: Boolean = false,
        ): Double {
            val height = if (truncate) lineHeight(size, bold) else textHeight(text, width, size, bold)
            attributed(text, size, hex, bold, align, truncate).drawWithRect(
                CGRectMake(x, top, width, height),
                if (truncate) NSStringDrawingUsesLineFragmentOrigin or NSStringDrawingTruncatesLastVisibleLine else NSStringDrawingUsesLineFragmentOrigin,
                null,
            )
            return height
        }

        private fun fillRect(x: Double, top: Double, w: Double, h: Double, hex: String, radius: Double = 0.0) {
            val rect = CGRectMake(x, top, w, h)
            val path = if (radius > 0) UIBezierPath.bezierPathWithRoundedRect(rect, cornerRadius = radius) else UIBezierPath.bezierPathWithRect(rect)
            color(hex).setFill()
            path.fill()
        }

        private fun strokeRect(x: Double, top: Double, w: Double, h: Double, hex: String, radius: Double, lineWidth: Double) {
            val path = UIBezierPath.bezierPathWithRoundedRect(CGRectMake(x, top, w, h), cornerRadius = radius)
            path.lineWidth = lineWidth
            color(hex).setStroke()
            path.stroke()
        }

        private fun line(x1: Double, y1: Double, x2: Double, y2: Double, hex: String, width: Double) {
            val path = UIBezierPath.bezierPath()
            path.moveToPoint(CGPointMake(x1, y1))
            path.addLineToPoint(CGPointMake(x2, y2))
            path.lineWidth = width
            color(hex).setStroke()
            path.stroke()
        }

        private fun newPage() {
            UIGraphicsBeginPDFPageWithInfo(CGRectMake(0.0, 0.0, PAGE_WIDTH, PAGE_HEIGHT), null)
            y = MARGIN
        }

        private fun ensure(height: Double, onNewPage: (() -> Unit)? = null) {
            if (y + height > bottomLimit) {
                newPage()
                onNewPage?.invoke()
            }
        }

        // --- Secciones ----------------------------------------------------------------------------

        fun draw() {
            newPage()
            header()
            sectionTitle("Resumen del período", keepWith = 70.0)
            stats()
            if (m.includeIncomes && m.includeExpenses) donuts()
            if (m.includeExpenses) {
                sectionTitle("Gastos por categoría", keepWith = 30.0)
                barCard(m.categoryBars)
            }
            if (m.includeIncomes) {
                sectionTitle("Ingresos por fuente", keepWith = 30.0)
                barCard(m.sourceBars)
            }
            sectionTitle("Saldos actuales en cuentas", keepWith = 36.0)
            balances()
            sectionTitle("Detalle de movimientos", keepWith = 70.0)
            movements()
        }

        private fun header() {
            y += drawText("Reporte financiero", MARGIN, y, contentWidth, 26.0, "#1C3E49", bold = true) + 8.0

            val size = 13.0
            val rowHeight = lineHeight(size) * 1.5
            var x = MARGIN
            fun segment(text: String, bold: Boolean) {
                val w = textWidth(text, size, bold)
                drawText(text, x, y, w + 2.0, size, "#666666", bold = bold)
                x += w
            }
            segment("Período:", true)
            segment(" ${m.periodLabel} · ", false)
            segment("Contenido:", true)
            segment(" ${m.typeLabel}", false)
            y += rowHeight
            drawText("Generado el ${m.generatedOn}", MARGIN, y, contentWidth, size, "#666666")
            y += rowHeight + 16.0
            fillRect(MARGIN, y, contentWidth, 2.0, "#2B9396")
            y += 2.0 + 24.0
        }

        /** [keepWith]: alto mínimo del primer contenido, para que el título no quede solo al final de una página. */
        private fun sectionTitle(title: String, keepWith: Double) {
            val height = 8.0 + lineHeight(16.0, true) + 8.0
            ensure(24.0 + height + 12.0 + keepWith)
            y += 24.0
            fillRect(MARGIN, y, contentWidth, height, "#EEF2F6", radius = 4.0)
            drawText(title, MARGIN + 12.0, y + 8.0, contentWidth - 24.0, 16.0, "#1C3E49", bold = true)
            y += height + 12.0
        }

        private fun stats() {
            val gap = 12.0
            val minWidth = 120.0
            val perRow = max(1, ((contentWidth + gap) / (minWidth + gap)).toInt())
            m.stats.chunked(perRow).forEach { rowStats ->
                val boxWidth = (contentWidth - gap * (rowStats.size - 1)) / rowStats.size
                val boxHeight = 12.0 + lineHeight(12.0) + 4.0 + lineHeight(18.0, true) + 12.0
                ensure(boxHeight)
                rowStats.forEachIndexed { i, s ->
                    val x = MARGIN + i * (boxWidth + gap)
                    fillRect(x, y, boxWidth, boxHeight, "#FAFAFA", radius = 6.0)
                    strokeRect(x, y, boxWidth, boxHeight, "#DDDDDD", radius = 6.0, lineWidth = 1.0)
                    drawText(s.label, x + 12.0, y + 12.0, boxWidth - 24.0, 12.0, "#888888", truncate = true)
                    drawText(s.value, x + 12.0, y + 12.0 + lineHeight(12.0) + 4.0, boxWidth - 24.0, 18.0, s.color ?: "#333333", bold = true, truncate = true)
                }
                y += boxHeight + gap
            }
        }

        private fun donuts() {
            val size = 110.0
            val labelHeight = lineHeight(13.0, true)
            ensure(24.0 + size + 8.0 + labelHeight + 8.0)
            y += 24.0
            val centers = listOf(MARGIN + contentWidth * 0.25, MARGIN + contentWidth * 0.75)
            val items = listOf(
                Triple("Gastos sobre ingresos", m.expensePercentage, "#FF5722"),
                Triple("Margen de ahorro", m.balancePercentage, "#2196F3"),
            )
            val ringWidth = (size - 74.0) / 2.0
            val ringRadius = (size + 74.0) / 4.0 // radio medio del anillo (exterior 110, interior 74)
            items.forEachIndexed { i, (name, percent, hex) ->
                val cx = centers[i]
                val cy = y + size / 2
                val center = CGPointMake(cx, cy)
                val track = UIBezierPath.bezierPathWithArcCenter(center, radius = ringRadius, startAngle = 0.0, endAngle = 2 * PI, clockwise = true)
                track.lineWidth = ringWidth
                color("#E0E0E0").setStroke()
                track.stroke()
                if (percent > 0) {
                    val start = -PI / 2
                    val arc = UIBezierPath.bezierPathWithArcCenter(center, radius = ringRadius, startAngle = start, endAngle = start + 2 * PI * percent / 100.0, clockwise = true)
                    arc.lineWidth = ringWidth
                    color(hex).setStroke()
                    arc.stroke()
                }
                val valueHeight = lineHeight(22.0, true)
                drawText("$percent%", cx - 40.0, cy - valueHeight / 2, 80.0, 22.0, "#333333", bold = true, align = NSTextAlignmentCenter)
                drawText(name, cx - contentWidth / 4, y + size + 8.0, contentWidth / 2, 13.0, "#444444", bold = true, align = NSTextAlignmentCenter, truncate = true)
            }
            y += size + 8.0 + labelHeight + 8.0
        }

        private fun emptyText(message: String) {
            val height = lineHeight(13.0)
            ensure(height + 8.0)
            drawText(message, MARGIN, y, contentWidth, 13.0, "#999999", align = NSTextAlignmentCenter)
            y += height + 8.0
        }

        private fun barCard(bars: List<PdfBar>) {
            if (bars.isEmpty()) {
                emptyText("Sin movimientos en el período")
                return
            }
            val rowHeight = 14.0 + 10.0
            val labelWidth = 130.0
            val valueWidth = 80.0 + 8.0
            bars.forEach { bar ->
                ensure(rowHeight)
                val barX = MARGIN + labelWidth + 10.0
                val barWidth = contentWidth - labelWidth - 10.0 - valueWidth
                drawText(bar.label, MARGIN, y, labelWidth - 10.0, 12.0, "#555555", align = NSTextAlignmentRight, truncate = true)
                fillRect(barX, y, barWidth, 14.0, "#EDEDED", radius = 7.0)
                val fillWidth = barWidth * (bar.widthPercent.toDoubleOrNull() ?: 0.0) / 100.0
                if (fillWidth > 0.0) fillRect(barX, y, fillWidth, 14.0, bar.color, radius = 7.0)
                drawText(bar.value, barX + barWidth + 8.0, y, 80.0, 11.0, "#333333", truncate = true)
                y += rowHeight
            }
        }

        private fun balances() {
            if (m.noAccounts) {
                emptyText("Sin cuentas registradas")
                return
            }
            val rowHeight = lineHeight(14.0) + 12.0
            m.balances.forEach { b ->
                ensure(rowHeight)
                drawText(b.label, MARGIN + 14.0, y + 6.0, contentWidth * 0.62, 14.0, "#333333", truncate = true)
                drawText(b.value, MARGIN + contentWidth * 0.62, y + 6.0, contentWidth * 0.38 - 14.0, 14.0, b.color, align = NSTextAlignmentRight, truncate = true)
                y += rowHeight
                line(MARGIN, y, MARGIN + contentWidth, y, "#DDDDDD", 1.0) // en Android es punteada
            }
            y += 4.0
        }

        private fun movements() {
            if (m.movements.isEmpty()) {
                emptyText("Sin movimientos en el período")
                return
            }
            val widths = listOf(0.15, 0.30, 0.18, 0.22, 0.15).map { it * contentWidth }
            val xs = widths.runningFold(MARGIN) { acc, w -> acc + w }

            fun drawHeader() {
                val h = 6.0 + lineHeight(11.0, true) + 6.0
                fillRect(MARGIN, y, contentWidth, h, "#1C3E49")
                listOf("Fecha", "Detalle", "Categoría", "Medio de pago", "Monto").forEachIndexed { i, title ->
                    drawText(
                        title, xs[i] + 8.0, y + 6.0, widths[i] - 16.0, 11.0, "#FFFFFF", bold = true,
                        align = if (i == 4) NSTextAlignmentRight else NSTextAlignmentLeft, truncate = true,
                    )
                }
                y += h
            }

            ensure(40.0)
            drawHeader()
            m.movements.forEach { mv ->
                val cells = listOf(mv.date, mv.detail, mv.category, mv.method)
                val cellHeights = cells.mapIndexed { i, t -> textHeight(t, widths[i] - 16.0, 11.0) }
                val rowHeight = 6.0 + cellHeights.max() + 6.0
                ensure(rowHeight) { drawHeader() }
                cells.forEachIndexed { i, t -> drawText(t, xs[i] + 8.0, y + 6.0, widths[i] - 16.0, 11.0, "#333333") }
                drawText(
                    mv.amount, xs[4] + 8.0, y + 6.0, widths[4] - 16.0, 11.0, if (mv.isIncome) "#2E7D32" else "#E74C3C",
                    align = NSTextAlignmentRight, truncate = true,
                )
                y += rowHeight
                line(MARGIN, y, MARGIN + contentWidth, y, "#E5E5E5", 1.0)
            }
        }
    }
}
