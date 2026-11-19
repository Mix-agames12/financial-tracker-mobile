package com.mixagames666.trackerrntemp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixagames666.trackerrntemp.ui.theme.AppTheme

enum class ButtonVariant { Filled, Outlined, Tonal, Danger }

/** Port de components/Button.tsx */
@Composable
fun AppButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Filled,
    icon: String? = null,
    loading: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    val disabled = !enabled

    val background = if (disabled) {
        colors.surfaceContainerHighest
    } else {
        when (variant) {
            ButtonVariant.Filled -> colors.primary
            ButtonVariant.Outlined -> Color.Transparent
            ButtonVariant.Tonal -> colors.primaryContainer
            ButtonVariant.Danger -> colors.errorStrong // texto blanco 4,7:1 (el rojo base sólo 3,7:1)
        }
    }
    val textColor = if (disabled) {
        colors.onSurfaceVariant
    } else {
        when (variant) {
            ButtonVariant.Filled -> colors.onPrimary
            ButtonVariant.Outlined -> colors.primaryText
            ButtonVariant.Tonal -> colors.onPrimaryContainer
            ButtonVariant.Danger -> colors.onError
        }
    }
    val borderColor = if (variant == ButtonVariant.Outlined) {
        if (disabled) colors.outlineVariant else colors.outline
    } else {
        Color.Transparent
    }
    val shape = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp) // objetivo táctil mínimo recomendado (48 dp)
            .clip(shape)
            .background(background, shape)
            .then(if (variant == ButtonVariant.Outlined) Modifier.border(1.5.dp, borderColor, shape) else Modifier)
            .touchableOpacity(enabled = enabled && !loading, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = textColor, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (icon != null) {
                    Ionicon(icon, 20, textColor)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = title,
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                )
            }
        }
    }
}
