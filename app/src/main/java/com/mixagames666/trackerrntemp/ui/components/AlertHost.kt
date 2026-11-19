package com.mixagames666.trackerrntemp.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.mixagames666.trackerrntemp.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AlertButtonStyle { Default, Cancel, Destructive }

data class AlertButton(
    val text: String,
    val style: AlertButtonStyle = AlertButtonStyle.Default,
    val onPress: (() -> Unit)? = null,
)

data class AlertRequest(val title: String, val message: String?, val buttons: List<AlertButton>)

/**
 * Equivalente a `Alert.alert(título, mensaje, botones)`. Como en Android, el último botón es el positivo,
 * el anterior el negativo y el primero el neutro; el aviso no se cierra tocando fuera ni con Atrás.
 */
object Alerts {
    private val _current = MutableStateFlow<AlertRequest?>(null)
    val current: StateFlow<AlertRequest?> = _current.asStateFlow()

    fun alert(title: String, message: String? = null, buttons: List<AlertButton> = listOf(AlertButton("OK"))) {
        _current.value = AlertRequest(title, message, buttons.ifEmpty { listOf(AlertButton("OK")) }.takeLast(3))
    }

    internal fun dismiss() {
        _current.value = null
    }
}

@Composable
fun AlertHost() {
    val request by Alerts.current.collectAsState()
    val colors = AppTheme.colors
    val req = request ?: return

    val buttons = req.buttons
    val positive = buttons.last()
    val negative = buttons.dropLast(1).lastOrNull()
    val neutral = buttons.dropLast(2).lastOrNull()

    @Composable
    fun ButtonView(button: AlertButton) {
        // Heurística 5 (prevención de errores): la acción destructiva se distingue también por el color.
        val tint = if (button.style == AlertButtonStyle.Destructive) colors.errorText else colors.primaryText
        TextButton(
            onClick = {
                Alerts.dismiss()
                button.onPress?.invoke()
            },
        ) { Text(button.text, color = tint, fontSize = 14.sp, fontWeight = if (button.style == AlertButtonStyle.Destructive) FontWeight.SemiBold else null) }
    }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        containerColor = colors.surfaceContainerHigh,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
        title = { Text(req.title) },
        text = req.message?.takeIf { it.isNotEmpty() }?.let { message -> { Text(message) } },
        confirmButton = { ButtonView(positive) },
        dismissButton = if (negative != null) {
            {
                Row {
                    if (neutral != null) ButtonView(neutral)
                    ButtonView(negative)
                }
            }
        } else {
            null
        },
    )
}
