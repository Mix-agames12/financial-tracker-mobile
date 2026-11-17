package com.mixagames666.trackerrntemp.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/**
 * Equivalente a `numberOfLines={1} adjustsFontSizeToFit minimumFontScale={…}`: una sola línea que reduce
 * la fuente hasta que quepa, sin bajar de `minimumScale` veces el tamaño original.
 */
@Composable
fun AutoSizeText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    minimumScale: Float = 0.5f,
    textAlign: TextAlign? = null,
) {
    var scale by remember(text, fontSize) { mutableFloatStateOf(1f) }
    Text(
        text = text,
        color = color,
        fontSize = fontSize * scale,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier,
        onTextLayout = { layout ->
            if (layout.didOverflowWidth && scale > minimumScale) {
                scale = (scale * 0.92f).coerceAtLeast(minimumScale)
            }
        },
    )
}
