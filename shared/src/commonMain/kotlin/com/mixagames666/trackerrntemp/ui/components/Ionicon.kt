package com.mixagames666.trackerrntemp.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixagames666.trackerrntemp.shared.resources.Res
import com.mixagames666.trackerrntemp.shared.resources.ionicons
import com.mixagames666.trackerrntemp.shared.resources.mdi_piggy_bank
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.Font
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
    // Mapa nombre -> punto de código, cargado una sola vez desde composeResources/files (lectura suspend).
    // Se precarga en el splash (AppRoot) y sólo se toca desde el hilo principal de Compose.
    private var glyphs: Map<String, Int>? = null

    @OptIn(ExperimentalResourceApi::class)
    suspend fun preload() {
        if (glyphs != null) return
        val text = Res.readBytes("files/ionicons_glyphmap.json").decodeToString()
        val parsed: JsonObject = Json.parseToJsonElement(text).jsonObject
        glyphs = parsed.mapValues { it.value.jsonPrimitive.int }
    }

    fun glyph(name: String): String? = glyphs?.get(name)?.let { codePointToString(it) }
}

private fun codePointToString(cp: Int): String =
    if (cp < 0x10000) {
        cp.toChar().toString()
    } else {
        val c = cp - 0x10000
        charArrayOf((0xD800 + (c shr 10)).toChar(), (0xDC00 + (c and 0x3FF)).toChar()).concatToString()
    }

/** `<Ionicons name=… size=… color=… />` */
@Composable
fun Ionicon(name: String, size: Int, color: Color, modifier: Modifier = Modifier) {
    // Hasta que el mapa de glifos esté cargado (ya lo está al salir del splash) no se pinta nada.
    val ready by produceState(initialValue = Ionicons.glyph(name) != null, name) {
        Ionicons.preload()
        value = true
    }
    val glyph = if (ready) Ionicons.glyph(name) else null
    if (glyph == null) return
    IconGlyph(glyph, FontFamily(Font(Res.font.ionicons)), size, color, modifier)
}

/** `<MaterialCommunityIcons name="piggy-bank" />` (la fuente sólo contiene ese glifo). */
@Composable
fun MdiPiggyBank(size: Int, color: Color, modifier: Modifier = Modifier) {
    IconGlyph(codePointToString(0xF1007), FontFamily(Font(Res.font.mdi_piggy_bank)), size, color, modifier)
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
                platformStyle = iconPlatformTextStyle(),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None,
                ),
            ),
        )
    }
}

/** Android: sin el relleno extra de la fuente (`includeFontPadding = false`) para centrar el glifo; iOS no lo tiene. */
internal expect fun iconPlatformTextStyle(): androidx.compose.ui.text.PlatformTextStyle?
