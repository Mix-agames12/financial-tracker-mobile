package com.mixagames666.trackerrntemp.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

@OptIn(ExperimentalLayoutApi::class)
@Composable
actual fun rememberImeVisible(): Boolean = WindowInsets.isImeVisible

@Composable
actual fun screenWidthDp(): Int = LocalConfiguration.current.screenWidthDp
