package com.mixagames666.trackerrntemp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mixagames666.trackerrntemp.ui.theme.AppTheme

/**
 * Port de components/Card.tsx. Los parámetros de estilo equivalen a los `style` que pasan las pantallas
 * (padding, marginBottom, borderRadius, backgroundColor, borderColor/borderWidth).
 *
 * Aspecto: las tarjetas normales llevan un borde de 1 dp (`cardBorder`) que las separa del fondo en los dos temas
 * (en oscuro la sombra no se ve); las destacadas ([gradient] / [gradientGreen]) usan un degradado diagonal del tono
 * profundo al base, con dos círculos translúcidos que le dan profundidad. El texto blanco se lee ≥ 4,5:1 en todo
 * el degradado (comprobado en ThemeContrastTest).
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    gradient: Boolean = false,
    gradientGreen: Boolean = false,
    backgroundColor: Color? = null,
    contentPadding: Dp = 16.dp,
    bottomMargin: Dp = 16.dp,
    cornerRadius: Dp = 16.dp,
    borderColor: Color? = null,
    borderWidth: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(cornerRadius)
    val featured = backgroundColor == null && (gradient || gradientGreen)

    var bg = colors.surfaceContainer
    if (elevated) bg = colors.surfaceContainerHigh
    if (backgroundColor != null) bg = backgroundColor

    val brush = when {
        !featured -> null
        gradient -> Brush.linearGradient(listOf(colors.primaryDeep, colors.primaryDark))
        else -> Brush.linearGradient(listOf(colors.secondaryDeep, colors.secondaryDark))
    }

    Column(
        modifier = modifier
            .padding(bottom = bottomMargin)
            .shadow(elevation = if (elevated) 4.dp else 2.dp, shape = shape, clip = false)
            .clip(shape)
            .then(if (brush != null) Modifier.background(brush, shape) else Modifier.background(bg, shape))
            .then(
                if (featured) {
                    Modifier.drawBehind {
                        drawCircle(Color.White.copy(alpha = 0.08f), radius = size.height * 0.9f, center = Offset(size.width * 1.02f, -size.height * 0.1f))
                        drawCircle(Color.White.copy(alpha = 0.06f), radius = size.height * 0.55f, center = Offset(size.width * 0.78f, size.height * 1.05f))
                    }
                } else {
                    Modifier
                },
            )
            .then(
                when {
                    borderColor != null && borderWidth > 0.dp -> Modifier.border(borderWidth, borderColor, shape)
                    !featured -> Modifier.border(1.dp, colors.cardBorder, shape)
                    else -> Modifier
                },
            )
            .padding(contentPadding),
        content = content,
    )
}
