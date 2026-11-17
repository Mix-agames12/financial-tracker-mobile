package com.mixagames666.trackerrntemp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixagames666.trackerrntemp.ui.theme.AppTheme
import com.mixagames666.trackerrntemp.util.Time
import com.mixagames666.trackerrntemp.util.parseLocalDateOrNull
import com.mixagames666.trackerrntemp.util.sanitizeDecimalInput
import com.mixagames666.trackerrntemp.util.sanitizeIntegerInput
import com.mixagames666.trackerrntemp.util.toLocalDateStr
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

enum class FieldKeyboard { Default, Decimal, Number }

/**
 * Port de components/TextField.tsx: etiqueta sobre el campo, ayuda con el ícono ⓘ, error, saneado de
 * números y modo fecha (selector nativo). Con [onPress] el campo es de sólo lectura y actúa como botón
 * (los selectores que en el original envuelven un TextField en un TouchableOpacity).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    isDate: Boolean = false,
    help: String? = null,
    keyboard: FieldKeyboard = FieldKeyboard.Default,
    maxLength: Int? = null,
    autoFocus: Boolean = false,
    onPress: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    var isFocused by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(8.dp)

    val borderColor = when {
        error != null -> colors.error
        isFocused -> colors.primary
        else -> colors.outline
    }
    val inputStyle = TextStyle(color = colors.onSurface, fontSize = 16.sp, fontFamily = FontFamily.SansSerif)
    val inputModifier = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(colors.surfaceContainerLow, shape)
        .border(1.5.dp, borderColor, shape)
        .padding(horizontal = 16.dp, vertical = 12.dp)

    Column(modifier.padding(bottom = 16.dp)) {
        Row(
            Modifier.padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
                color = when {
                    error != null -> colors.error
                    isFocused -> colors.primaryLight
                    else -> colors.onSurfaceVariant
                },
                modifier = Modifier.weight(1f, fill = false),
            )
            if (!help.isNullOrEmpty()) {
                Box(
                    Modifier
                        .touchableOpacity(onClick = { showHelp = !showHelp })
                        .semantics {
                            contentDescription = "Ayuda: $label"
                            role = Role.Button
                            stateDescription = if (showHelp) "expandido" else "contraído"
                        },
                ) {
                    Ionicon(if (showHelp) "information-circle" else "information-circle-outline", 16, colors.primary)
                }
            }
        }

        if (!help.isNullOrEmpty() && showHelp) {
            Text(
                text = help,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceContainerHighest, RoundedCornerShape(8.dp))
                    .padding(8.dp),
            )
        }

        when {
            isDate -> {
                Box(Modifier.touchableOpacity(activeOpacity = 0.8f, role = null, onClick = { showPicker = true })) {
                    Box(inputModifier) { Text(value, style = inputStyle) }
                }
            }

            onPress != null -> {
                Box(Modifier.touchableOpacity(role = null, onClick = onPress)) {
                    Box(inputModifier) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = inputStyle.copy(color = colors.onSurfaceVariant))
                        } else {
                            Text(value, style = inputStyle)
                        }
                    }
                }
            }

            else -> {
                LaunchedEffect(autoFocus) { if (autoFocus) focusRequester.requestFocus() }
                BasicTextField(
                    value = value,
                    onValueChange = { text ->
                        val sanitized = when (keyboard) {
                            FieldKeyboard.Decimal -> sanitizeDecimalInput(text)
                            FieldKeyboard.Number -> sanitizeIntegerInput(text)
                            FieldKeyboard.Default -> text
                        }
                        onValueChange(if (maxLength != null) sanitized.take(maxLength) else sanitized)
                    },
                    singleLine = true,
                    textStyle = inputStyle,
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (keyboard) {
                            FieldKeyboard.Decimal -> KeyboardType.Decimal
                            FieldKeyboard.Number -> KeyboardType.Number
                            FieldKeyboard.Default -> KeyboardType.Text
                        },
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { isFocused = it.isFocused },
                    decorationBox = { inner ->
                        Box(inputModifier) {
                            if (value.isEmpty() && placeholder != null) {
                                Text(placeholder, style = inputStyle.copy(color = colors.onSurfaceVariant))
                            }
                            inner()
                        }
                    },
                )
            }
        }

        if (showPicker && isDate) {
            val initial: LocalDate = parseLocalDateOrNull(value) ?: Time.today()
            val state = rememberDatePickerState(
                initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = { showPicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            state.selectedDateMillis?.let { millis ->
                                val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                                onValueChange(toLocalDateStr(picked))
                            }
                            showPicker = false
                        },
                    ) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancelar") } },
            ) {
                DatePicker(state = state)
            }
        }

        if (error != null) {
            Text(
                text = error,
                fontSize = 12.sp,
                color = colors.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
