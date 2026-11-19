package com.mixagames666.trackerrntemp.ui.components

import androidx.compose.runtime.Composable

/** `true` mientras el teclado en pantalla está visible (insets del IME). */
@Composable
expect fun rememberImeVisible(): Boolean

/** Ancho de pantalla en dp; se recalcula con rotaciones, pantallas plegables o multiventana. */
@Composable
expect fun screenWidthDp(): Int
