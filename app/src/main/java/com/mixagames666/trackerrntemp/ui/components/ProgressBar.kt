package com.mixagames666.trackerrntemp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mixagames666.trackerrntemp.domain.AvailabilityLevel
import com.mixagames666.trackerrntemp.ui.theme.AppTheme

enum class ProgressVariant { Primary, Success, Warning, Danger }

fun AvailabilityLevel.toProgressVariant(): ProgressVariant = when (this) {
    AvailabilityLevel.SUCCESS -> ProgressVariant.Success
    AvailabilityLevel.WARNING -> ProgressVariant.Warning
    AvailabilityLevel.DANGER -> ProgressVariant.Danger
}

/**
 * Port de components/ProgressBar.tsx: `progress` va de 0 a 1 y se acota. El relleno se anima al aparecer y al
 * cambiar (700 ms, desaceleración) y se dibuja en la fase de dibujo: no relanza la composición ni el diseño en
 * cada cuadro. Con las animaciones del sistema desactivadas salta directo al valor final.
 */
@Composable
fun AppProgressBar(
    progress: Double,
    modifier: Modifier = Modifier,
    variant: ProgressVariant = ProgressVariant.Primary,
) {
    val colors = AppTheme.colors
    val fill = when (variant) {
        ProgressVariant.Primary -> colors.primary
        ProgressVariant.Success -> colors.secondary
        ProgressVariant.Warning -> colors.tertiary
        ProgressVariant.Danger -> colors.error
    }
    val safeProgress = if (progress.isNaN()) 0.0 else progress.coerceIn(0.0, 1.0)
    val shape = RoundedCornerShape(4.dp)

    val shown = remember { Animatable(0f) }
    LaunchedEffect(safeProgress) { shown.animateTo(safeProgress.toFloat(), tween(700, easing = FastOutSlowInEasing)) }

    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(safeProgress.toFloat(), 0f..1f) }
            .clip(shape)
            .background(colors.surfaceContainerHighest, shape)
            .drawBehind {
                val width = size.width * shown.value
                if (width > 0f) {
                    drawRoundRect(color = fill, size = Size(width, size.height), cornerRadius = CornerRadius(size.height / 2f))
                }
            },
    )
}
