package com.mixagames666.trackerrntemp.ui.theme

import androidx.compose.ui.text.TextStyle

/**
 * Cifras tabulares (`tnum`): todos los dígitos miden lo mismo, de modo que los montos de una lista quedan alineados
 * por decenas, centenas… y no "bailan" al cambiar. Se aplica con `style = MoneyStyle` a los textos de dinero
 * (los parámetros explícitos de `Text`, como fontSize o color, siguen mandando).
 */
val MoneyStyle = TextStyle(fontFeatureSettings = "tnum")
