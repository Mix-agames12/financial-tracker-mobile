package com.mixagames666.trackerrntemp.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UIScreen

@Composable
actual fun rememberImeVisible(): Boolean = WindowInsets.ime.getBottom(LocalDensity.current) > 0

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun screenWidthDp(): Int {
    // TODO iOS: no se recompone sola al rotar; verificar en Mac (los puntos de UIKit equivalen a dp).
    return UIScreen.mainScreen.bounds.useContents { size.width }.toInt()
}
