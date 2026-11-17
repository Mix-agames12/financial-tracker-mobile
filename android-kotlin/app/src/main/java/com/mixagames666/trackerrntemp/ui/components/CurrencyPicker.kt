package com.mixagames666.trackerrntemp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixagames666.trackerrntemp.ui.theme.AppTheme
import com.mixagames666.trackerrntemp.util.CURRENCY_OPTIONS
import com.mixagames666.trackerrntemp.util.CurrencyOption
import com.mixagames666.trackerrntemp.util.formatCurrencyExample

/** Port de components/CurrencyPicker.tsx: lista de monedas con un ejemplo de su formato. */
@Composable
fun CurrencyPicker(selectedCode: String?, onSelect: (CurrencyOption) -> Unit) {
    val colors = AppTheme.colors
    Column {
        CURRENCY_OPTIONS.forEach { option ->
            val selected = option.code == selectedCode
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .touchableOpacity(role = null) { onSelect(option) }
                        .semantics { this.selected = selected }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) colors.primary else colors.surfaceContainerHighest, RoundedCornerShape(8.dp))
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = option.code,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) colors.onPrimary else colors.onSurface,
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    ) {
                        Text(text = option.name, fontSize = 16.sp, color = if (selected) colors.primary else colors.onSurface)
                        Text(
                            text = formatCurrencyExample(option),
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (selected) Ionicon("checkmark", 20, colors.primary)
                }
                HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
            }
        }
    }
}
