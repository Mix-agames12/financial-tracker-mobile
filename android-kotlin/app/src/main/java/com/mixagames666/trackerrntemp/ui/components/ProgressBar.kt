package com.mixagames666.trackerrntemp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mixagames666.trackerrntemp.domain.AvailabilityLevel
import com.mixagames666.trackerrntemp.ui.theme.AppTheme

enum class ProgressVariant { Primary, Success, Warning, Danger }

fun AvailabilityLevel.toProgressVariant(): ProgressVariant = when (this) {
    AvailabilityLevel.SUCCESS -> ProgressVariant.Success
    AvailabilityLevel.WARNING -> ProgressVariant.Warning
    AvailabilityLevel.DANGER -> ProgressVariant.Danger
}

/** Port de components/ProgressBar.tsx: `progress` va de 0 a 1 y se acota. */
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

    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(shape)
            .background(colors.surfaceContainerHighest, shape),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(safeProgress.toFloat())
                .background(fill, shape),
        )
    }
}
