package com.mixagames666.trackerrntemp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixagames666.trackerrntemp.ui.theme.AppTheme
import com.mixagames666.trackerrntemp.ui.theme.hexColor
import com.mixagames666.trackerrntemp.ui.theme.hexColorWithAlpha
import com.mixagames666.trackerrntemp.ui.theme.onFill
import com.mixagames666.trackerrntemp.ui.theme.readableOn

/** Port de components/Chip.tsx. `color` es un '#rrggbb' (p. ej. el de la categoría). */
@Composable
fun Chip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    icon: String? = null,
    color: String? = null,
) {
    val colors = AppTheme.colors
    val custom: Color? = color?.takeIf { it.isNotEmpty() }?.let { hexColor(it) }
    val shape = RoundedCornerShape(percent = 50)

    // customColor + '40' / '10' son hex RRGGBBAA: alfa 0x40 y 0x10
    val borderColor = if (active) custom ?: colors.primary
    else if (color != null && custom != null) hexColorWithAlpha(color, 0x40) else colors.outline
    val background = if (active) custom ?: colors.primary
    else if (color != null && custom != null) hexColorWithAlpha(color, 0x10) else Color.Transparent
    // Texto legible (≥ 4,5:1): sobre relleno activo, blanco o casi negro según el tono; sin relleno, el tono de la
    // categoría ajustado a la superficie (los amarillos y verdes claros no se leen sobre blanco, ni los rojos sobre oscuro).
    val contentColor = when {
        active -> (custom ?: colors.primary).onFill()
        custom != null -> custom.readableOn(colors.surfaceContainerHigh)
        else -> colors.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .clip(shape)
            .background(background, shape)
            .border(1.5.dp, borderColor, shape)
            .touchableOpacity(role = Role.Checkbox, onClick = onClick)
            .semantics { selected = active } // el lector de pantalla anuncia si la opción está elegida
            .heightIn(min = 36.dp)
            .padding(vertical = 6.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Ionicon(icon, 16, contentColor)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label,
            color = contentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif,
        )
    }
}
