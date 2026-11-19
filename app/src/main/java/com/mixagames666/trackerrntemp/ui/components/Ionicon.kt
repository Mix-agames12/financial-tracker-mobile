package com.mixagames666.trackerrntemp.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixagames666.trackerrntemp.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Iconos Ionicons (la misma fuente que usa @expo/vector-icons) para que los nombres guardados en las
 * categorías ('restaurant', 'car'…) y todos los iconos de la app sean idénticos a los del original.
 */
object Ionicons {
    val fontFamily = FontFamily(Font(R.font.ionicons))

    @Volatile
    private var glyphs: Map<String, Int>? = null

    private fun glyphMap(context: Context): Map<String, Int> {
        glyphs?.let { return it }
        val text = context.assets.open("ionicons_glyphmap.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val parsed: JsonObject = Json.parseToJsonElement(text).jsonObject
        return parsed.mapValues { it.value.jsonPrimitive.int }.also { glyphs = it }
    }

    fun glyph(context: Context, name: String): String? =
        glyphMap(context)[name]?.let { String(Character.toChars(it)) }
}

/** `<Ionicons name=… size=… color=… />` */
@Composable
fun Ionicon(name: String, size: Int, color: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val glyph = remember(name) { Ionicons.glyph(context, name) } ?: return
    IconGlyph(glyph, Ionicons.fontFamily, size, color, modifier)
}

private val MdiPiggyFont = FontFamily(Font(R.font.mdi_piggy_bank))

/** `<MaterialCommunityIcons name="piggy-bank" />` (la fuente sólo contiene ese glifo). */
@Composable
fun MdiPiggyBank(size: Int, color: Color, modifier: Modifier = Modifier) {
    IconGlyph(String(Character.toChars(0xF1007)), MdiPiggyFont, size, color, modifier)
}

@Composable
private fun IconGlyph(glyph: String, family: FontFamily, size: Int, color: Color, modifier: Modifier) {
    // Decorativo: el glifo es un carácter de la fuente de íconos y el lector de pantalla lo leería como un símbolo
    // sin sentido. Quien necesite un nombre accesible lo pone en el contenedor (contentDescription / onClickLabel).
    Box(modifier.size(size.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Text(
            text = glyph,
            color = color,
            softWrap = false,
            style = TextStyle(
                fontFamily = family,
                fontSize = size.sp,
                lineHeight = size.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None,
                ),
            ),
        )
    }
}
