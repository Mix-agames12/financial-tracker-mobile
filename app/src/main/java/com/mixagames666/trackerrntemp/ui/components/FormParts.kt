package com.mixagames666.trackerrntemp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixagames666.trackerrntemp.data.TaxesConfig
import com.mixagames666.trackerrntemp.domain.TaxBreakdown
import com.mixagames666.trackerrntemp.ui.theme.AppTheme
import com.mixagames666.trackerrntemp.util.JsCompat
import com.mixagames666.trackerrntemp.util.formatCurrency

// Piezas de formulario que se repiten en las pantallas (mismos estilos que los `style` inline del original).

/** Etiqueta de grupo de opciones: 12 sp, onSurfaceVariant, margen inferior 8, 'sans-serif-medium'. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier, bold: Boolean = false, fontSize: Int = 12, bottom: Dp = 8.dp) {
    Text(
        text = text,
        fontSize = fontSize.sp,
        fontFamily = FontFamily.SansSerif,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        color = AppTheme.colors.onSurfaceVariant,
        modifier = modifier.padding(bottom = bottom),
    )
}

/** Fila de chips en una línea (gap 8). */
@Composable
fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** Chips con salto de línea (flexWrap: 'wrap', gap: 8). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** ScrollView horizontal con chips (showsHorizontalScrollIndicator={false}, gap: 8). */
@Composable
fun ChipScrollRow(bottomPadding: Dp = 0.dp, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Desglose del motor de impuestos que se muestra antes de guardar un gasto, pago o depósito. */
@Composable
fun TaxBreakdownBox(
    config: TaxesConfig,
    breakdown: TaxBreakdown,
    totalLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainerHighest, shape)
            .padding(12.dp),
    ) {
        Text("Desglose de Impuestos/Comisión", fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        TaxLine("Comisión (${JsCompat.numberToString(config.comisionRate)}%)", breakdown.comision)
        TaxLine("IVA sobre Comisión (${JsCompat.numberToString(config.ivaRate)}%)", breakdown.iva)
        TaxLine("ISD sobre Principal (${JsCompat.numberToString(config.isdRate)}%)", breakdown.isd)
        Column(Modifier.padding(top = 8.dp)) {
            HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(totalLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                Text(formatCurrency(breakdown.totalCharge), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            }
        }
    }
}

@Composable
private fun TaxLine(label: String, amount: Double) {
    val colors = AppTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = colors.onSurface)
        Text("+${formatCurrency(amount)}", fontSize = 13.sp, color = colors.onSurface)
    }
}

/** Par etiqueta/valor de las hojas de detalle (detailItem: 45 % del ancho). */
@Composable
fun DetailItem(label: String, value: String, modifier: Modifier = Modifier, valueColor: androidx.compose.ui.graphics.Color? = null, bold: Boolean = false) {
    val colors = AppTheme.colors
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = colors.onSurfaceVariant)
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = if (bold) FontWeight.Bold else null,
            color = valueColor ?: colors.onSurface,
        )
    }
}

/** Rejilla de detalles (detailGrid: flexWrap, gap 16, rowGap 24) con borde superior. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailGrid(content: @Composable () -> Unit) {
    val colors = AppTheme.colors
    Column {
        HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) { content() }
    }
}
