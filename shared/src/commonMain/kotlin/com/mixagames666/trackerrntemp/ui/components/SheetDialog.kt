package com.mixagames666.trackerrntemp.ui.components

import androidx.compose.runtime.Composable

/**
 * Diálogo a pantalla completa que aloja la hoja inferior. Cada plataforma crea su ventana y la configura
 * (Android: ventana anclada arriba, sin atenuar, con barras transparentes y ADJUST_RESIZE para el teclado).
 * El teclado se gestiona en común con `WindowInsets.ime`.
 */
@Composable
expect fun SheetDialog(onClose: () -> Unit, content: @Composable () -> Unit)
