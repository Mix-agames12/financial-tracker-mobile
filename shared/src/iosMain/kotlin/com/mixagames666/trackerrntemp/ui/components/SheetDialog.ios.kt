package com.mixagames666.trackerrntemp.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
actual fun SheetDialog(onClose: () -> Unit, content: @Composable () -> Unit) {
    // TODO iOS: comprobar en Mac la atenuación (la hoja ya pinta su propio fondo oscuro) y el teclado.
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) { content() }
}
