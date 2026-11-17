package com.mixagames666.trackerrntemp.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.mixagames666.trackerrntemp.domain.PieSlice
import com.mixagames666.trackerrntemp.ui.theme.hexColor
import com.mixagames666.trackerrntemp.util.JsCompat

// Réplica de react-native-chart-kit <PieChart absolute> (paddingLeft 15, radio = alto / 2.5, leyenda a la
// derecha con "valor nombre", tamaño de leyenda 11).

@Composable
fun SimplePieChart(
    slices: List<PieSlice>,
    legendColor: Color,
    width: Dp,
    height: Dp = Dp(200f),
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = remember(legendColor) { TextStyle(color = legendColor, fontSize = 11.sp) }
    val colors = remember(slices) { slices.map { hexColor(it.color) } }

    Canvas(modifier.width(width).height(height)) {
        val w = size.width
        val h = size.height
        val d = density
        val paddingLeft = 15f * d
        val cx = w / 4 + paddingLeft
        val cy = h / 2
        val radius = h / 2.5f

        val total = slices.sumOf { it.population }
        var start = -90f
        slices.forEachIndexed { i, slice ->
            val sweep = if (total > 0) (slice.population / total * 360).toFloat() else 0f
            if (sweep > 0f) {
                drawArc(
                    color = colors[i],
                    startAngle = start,
                    sweepAngle = if (slices.size == 1) 360f else sweep,
                    useCenter = true,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2, radius * 2),
                )
            }
            start += sweep
        }

        // Leyenda: un círculo de 16 y el texto, repartidos en el 80 % de la altura
        val legendX = cx + w / 2.5f - 24f * d
        slices.forEachIndexed { i, slice ->
            val y = cy - h / 2.5f + ((h * 0.8f) / slices.size) * i + 12f * d
            drawCircle(colors[i], radius = 8f * d, center = Offset(legendX + 8f * d, y + 8f * d))
            val text = "${JsCompat.numberToString(slice.population)} ${slice.name}"
            val maxWidth = (w - (cx + w / 2.5f)).coerceAtLeast(0f).toInt()
            val layout = measurer.measure(
                text,
                labelStyle,
                overflow = TextOverflow.Clip,
                softWrap = false,
                maxLines = 1,
                constraints = Constraints(maxWidth = maxWidth.coerceAtLeast(1)),
            )
            drawText(layout, topLeft = Offset(cx + w / 2.5f, y + 12f * d - layout.firstBaseline))
        }
    }
}
