package com.mixagames666.trackerrntemp.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixagames666.trackerrntemp.ui.theme.AppTheme
import com.mixagames666.trackerrntemp.util.Time
import com.mixagames666.trackerrntemp.util.jsDate
import com.mixagames666.trackerrntemp.util.toLocalDateStr
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

data class DateOption(val label: String, val value: String, val subLabel: String? = null)

/**
 * Opciones rápidas de fecha de DatePickerModal: hoy, ayer, los 5 días anteriores y "hace 1 mes".
 * "Hace 1 mes" resta un mes con el desborde de `Date.setMonth` (el 31/03 cae el 03/03).
 */
fun buildDateOptions(today: LocalDate = Time.today()): List<DateOption> {
    val list = mutableListOf<DateOption>()
    list.add(DateOption("Hoy", toLocalDateStr(today)))
    list.add(DateOption("Ayer", toLocalDateStr(today.minus(1, DateTimeUnit.DAY))))
    for (i in 2..6) {
        val d = today.minus(i, DateTimeUnit.DAY)
        list.add(DateOption("Hace $i días", toLocalDateStr(d), "${d.dayOfMonth}/${d.monthNumber}"))
    }
    val mes = jsDate(today.year, today.monthNumber - 2, today.dayOfMonth)
    list.add(DateOption("Hace 1 mes", toLocalDateStr(mes)))
    return list
}

/** Port de components/DatePickerModal.tsx */
@Composable
fun DatePickerSheet(
    visible: Boolean,
    onClose: () -> Unit,
    value: String,
    onSelect: (String) -> Unit,
    title: String = "Seleccionar fecha",
) {
    val colors = AppTheme.colors
    // Se recalcula al abrir para que "Hoy" siga siendo correcto si la app pasa la medianoche abierta.
    val options = remember(visible) { buildDateOptions() }

    BottomSheet(visible = visible, onClose = onClose, title = title) {
        Column(
            Modifier
                .heightIn(max = 350.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            options.forEach { opt ->
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .touchableOpacity(role = null) {
                                onSelect(opt.value)
                                onClose()
                            }
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = opt.label,
                                fontSize = 16.sp,
                                color = if (value == opt.value) colors.primary else colors.onSurface,
                            )
                            if (opt.subLabel != null) {
                                Text(text = opt.subLabel, fontSize = 12.sp, color = colors.onSurfaceVariant)
                            }
                        }
                        if (value == opt.value) Ionicon("checkmark", 20, colors.primary)
                    }
                    HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
                }
            }
        }
    }
}
