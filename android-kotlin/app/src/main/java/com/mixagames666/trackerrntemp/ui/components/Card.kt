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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mixagames666.trackerrntemp.ui.theme.AppTheme

/**
 * Port de components/Card.tsx. Los parámetros de estilo equivalen a los `style` que pasan las pantallas
 * (padding, marginBottom, borderRadius, backgroundColor, borderColor/borderWidth).
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
    var bg = colors.surfaceContainer
    if (elevated) bg = colors.surfaceContainerHigh
    if (gradient) bg = colors.primaryDark
    if (gradientGreen) bg = colors.secondaryDark
    if (backgroundColor != null) bg = backgroundColor

    Column(
        modifier = modifier
            .padding(bottom = bottomMargin)
            .shadow(elevation = if (elevated) 4.dp else 2.dp, shape = shape, clip = false)
            .clip(shape)
            .background(bg, shape)
            .then(if (borderColor != null && borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, shape) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}
