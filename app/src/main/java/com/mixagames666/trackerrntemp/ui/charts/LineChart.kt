package com.mixagames666.trackerrntemp.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixagames666.trackerrntemp.util.JsCompat
import kotlin.math.hypot
import kotlin.math.max

// Réplica de react-native-chart-kit <LineChart bezier> con el aspecto que tiene en la app:
// líneas de fondo discontinuas del mismo color de la serie, etiquetas del eje Y con el símbolo de moneda
// y 2 decimales, puntos de radio 3 y curva suavizada. El eje Y queda fijo mientras el gráfico se desplaza.

private const val CHART_HEIGHT = 220f
private const val PADDING_TOP = 16f
private const val PADDING_RIGHT = 64f
private const val Y_LABEL_OFFSET = 12f
private const val FIXED_AXIS_WIDTH = 55f
private const val TOUCH_RADIUS = 20f

private class Geometry(val data: List<Double>, val width: Float) {
    private val rawMin = data.minOrNull() ?: 0.0
    private val rawMax = data.maxOrNull() ?: 0.0
    val min = rawMin
    // Con datos planos el rango se abre a 1 para que haya escala (el original lo forzaba con un punto extra).
    val max = if (rawMax == rawMin) rawMin + 1 else rawMax
    private val scaler = max - min

    fun x(i: Int) = (i * (width - PADDING_RIGHT)) / data.size + PADDING_RIGHT
    fun y(value: Double) = (((CHART_HEIGHT - CHART_HEIGHT * ((value - min) / scaler)) / 4f) * 3f + PADDING_TOP).toFloat()
    fun yLabel(i: Int) = scaler / 4 * i + min
    fun yLabelBaseline(i: Int) = CHART_HEIGHT * 3 / 4 - ((CHART_HEIGHT - CHART_HEIGHT / 4) / 4) * i + PADDING_TOP
    fun gridY(i: Int) = ((CHART_HEIGHT - CHART_HEIGHT / 4) / 4) * i + PADDING_TOP
}

@Composable
fun SpendingLineChart(
    labels: List<String>,
    data: List<Double>,
    lineColor: Color,
    labelColor: Color,
    yPrefix: String,
    backgroundColor: Color,
    visibleWidth: Dp,
    onPointClick: (index: Int, value: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (data.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val contentWidth = max(visibleWidth.value, labels.size * 45f)
    val labelStyle = remember(labelColor) { TextStyle(color = labelColor, fontSize = 12.sp) }

    Box(modifier.height(CHART_HEIGHT.dp)) {
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            val geometry = remember(data, contentWidth) { Geometry(data, contentWidth) }
            Canvas(
                Modifier
                    .width(contentWidth.dp)
                    .height(CHART_HEIGHT.dp)
                    .pointerInput(data, contentWidth) {
                        detectTapGestures { tap ->
                            val density = this.density
                            var best = -1
                            var bestDistance = Float.MAX_VALUE
                            data.forEachIndexed { i, v ->
                                val d = hypot(tap.x - geometry.x(i) * density, tap.y - geometry.y(v) * density)
                                if (d < bestDistance) {
                                    bestDistance = d
                                    best = i
                                }
                            }
                            if (best >= 0 && bestDistance <= TOUCH_RADIUS * density) onPointClick(best, data[best])
                        }
                    },
            ) {
                drawChart(geometry, labels, lineColor, labelStyle, yPrefix, measurer, drawYLabels = true, drawXLabels = true)
            }
        }
        // Eje Y fijo (en el original, un segundo gráfico recortado a 55 dp encima del que se desplaza)
        val geometryFixed = remember(data, contentWidth) { Geometry(data, contentWidth) }
        Canvas(
            Modifier
                .width(FIXED_AXIS_WIDTH.dp)
                .height(CHART_HEIGHT.dp)
                .background(backgroundColor),
        ) {
            drawChart(geometryFixed, labels, lineColor, labelStyle, yPrefix, measurer, drawYLabels = true, drawXLabels = false, onlyAxis = true)
        }
    }
}

private fun DrawScope.drawChart(
    g: Geometry,
    labels: List<String>,
    lineColor: Color,
    labelStyle: TextStyle,
    yPrefix: String,
    measurer: TextMeasurer,
    drawYLabels: Boolean,
    drawXLabels: Boolean,
    onlyAxis: Boolean = false,
) {
    val d = density
    fun px(v: Float) = v * d

    if (!onlyAxis) {
        // Líneas de fondo (stroke = color(0.2) del original, que devuelve el mismo color)
        for (i in 0..4) {
            val y = px(g.gridY(i))
            drawLine(
                color = lineColor,
                start = Offset(px(PADDING_RIGHT), y),
                end = Offset(px(g.width), y),
                strokeWidth = px(1f),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(px(5f), px(10f))),
            )
        }

        // Curva suavizada (segmentos cuadráticos como en chart-kit)
        val points = g.data.mapIndexed { i, v -> Offset(px(g.x(i)), px(g.y(v))) }
        val line = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 0 until points.size - 1) {
                val xMid = (points[i].x + points[i + 1].x) / 2
                val yMid = (points[i].y + points[i + 1].y) / 2
                val cp1x = (xMid + points[i].x) / 2
                val cp2x = (xMid + points[i + 1].x) / 2
                quadraticTo(cp1x, points[i].y, xMid, yMid)
                quadraticTo(cp2x, points[i + 1].y, points[i + 1].x, points[i + 1].y)
            }
        }
        // Relleno bajo la curva (degradado del color de la serie, 10 % → 0 %)
        val baseline = px(CHART_HEIGHT * 3 / 4 + PADDING_TOP)
        val fill = Path().apply {
            addPath(line)
            lineTo(points.last().x, baseline)
            lineTo(px(PADDING_RIGHT), baseline)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.1f), lineColor.copy(alpha = 0f)), startY = 0f, endY = px(CHART_HEIGHT)))
        drawPath(line, lineColor, style = Stroke(width = px(2f)))
        points.forEach { drawCircle(lineColor, radius = px(3f), center = it) }
    }

    if (drawYLabels) {
        for (i in 0..4) {
            val text = yPrefix + JsCompat.toFixed(g.yLabel(i), 2)
            val layout = measurer.measure(text, labelStyle)
            val x = px(PADDING_RIGHT - Y_LABEL_OFFSET) - layout.size.width
            val baseline = px(g.yLabelBaseline(i))
            drawText(layout, topLeft = Offset(x, baseline - layout.firstBaseline))
        }
    }

    if (drawXLabels) {
        labels.forEachIndexed { i, label ->
            val layout = measurer.measure(label, labelStyle)
            val x = px(((g.width - PADDING_RIGHT) / labels.size) * i + PADDING_RIGHT) - layout.size.width / 2f
            val baseline = px(CHART_HEIGHT * 3 / 4 + PADDING_TOP + 12f * 2)
            drawText(layout, topLeft = Offset(x, baseline - layout.firstBaseline))
        }
    }
}
