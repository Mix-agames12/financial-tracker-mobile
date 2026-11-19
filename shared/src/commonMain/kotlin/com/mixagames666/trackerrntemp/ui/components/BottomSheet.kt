package com.mixagames666.trackerrntemp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixagames666.trackerrntemp.ui.theme.AppTheme
import com.mixagames666.trackerrntemp.ui.theme.bottomInset
import com.mixagames666.trackerrntemp.ui.theme.topInset
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Port de components/BottomSheet.tsx: hoja inferior arrastrable con dos alturas (media = 60 % del espacio
// útil y completa), cierre arrastrando hacia abajo, ajuste al teclado y cierre al tocar el fondo.
//
// Geometría (sin huecos): la ventana del diálogo se ancla arriba y ocupa toda la pantalla, de modo que el fondo
// oscuro cubre también las barras y el área del teclado. La hoja llega hasta el borde inferior y reserva el
// espacio del teclado DENTRO de sí misma; así nunca queda una franja entre la hoja y el borde inferior o el
// teclado, aunque el IME reserve espacio sin dibujar nada (teclado físico, emulador) o se esté animando.

private const val HALF_RATIO = 0.6f
private const val DISMISS_VELOCITY = 1.5f // dp/ms, como el vy de PanResponder
private const val SNAP_VELOCITY = 0.5f

private enum class Snap { Half, Full }

private class GestureState {
    var half = 0f
    var full = 0f
    var snap = Snap.Half
    var keyboardVisible = false
    var startHeight = 0f
    var onClose: () -> Unit = {}
}

/**
 * [scrollState] permite al llamador desplazar el contenido (p. ej. volver arriba para mostrar un error de validación).
 */
@Composable
fun BottomSheet(
    visible: Boolean,
    onClose: () -> Unit,
    title: String,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    // Se mantiene compuesta durante la animación de salida (RN: Modal animationType="slide").
    var rendered by remember { mutableStateOf(visible) }
    val slide = remember { Animatable(if (visible) 0f else 1f) }
    LaunchedEffect(visible) {
        if (visible) {
            scrollState.scrollTo(0) // el estado de scroll vive fuera del diálogo: cada apertura empieza arriba
            rendered = true
            slide.snapTo(1f)
            slide.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        } else if (rendered) {
            slide.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
            rendered = false
        }
    }
    if (!rendered) return

    SheetDialog(onClose = onClose) {
        SheetLayout(slideFraction = slide.value, title = title, onClose = onClose, scrollState = scrollState, content = content)
    }
}

@Composable
private fun SheetLayout(
    slideFraction: Float,
    title: String,
    onClose: () -> Unit,
    scrollState: ScrollState,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // Fondo oscuro (rgba(0,0,0,0.6)): cubre toda la ventana, también bajo el teclado y la barra de
        // navegación, y al tocarlo se cierra la hoja.
        Box(
            Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f * (1f - slideFraction)))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        )
        SheetBody(slideFraction, title, onClose, scrollState, content)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetBody(
    slideFraction: Float,
    title: String,
    onClose: () -> Unit,
    scrollState: ScrollState,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val keyboardVisible = rememberImeVisible()
    val topInsetPx = with(density) { topInset().toPx() }
    val topGapPx = with(density) { 8.dp.toPx() }
    val dismissDistancePx = with(density) { 120.dp.toPx() }
    val snapDistancePx = with(density) { 60.dp.toPx() }
    val slopPx = with(density) { 5.dp.toPx() }

    var snap by remember { mutableStateOf(Snap.Half) }
    val gesture = remember { GestureState() }
    val dragY = remember { Animatable(0f) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Altura de toda la ventana (no se resta el teclado: su espacio se reserva dentro de la hoja).
        val availablePx = constraints.maxHeight.toFloat()
        val fullPx = max(availablePx - topInsetPx - topGapPx, 0f)
        val halfPx = min(fullPx, availablePx * HALF_RATIO)
        // Con el teclado abierto la hoja ocupa todo el espacio libre sobre él.
        val targetPx = if (snap == Snap.Full || keyboardVisible) fullPx else halfPx

        val maxHeight = remember { Animatable(halfPx) }
        LaunchedEffect(targetPx) { maxHeight.animateTo(targetPx, tween(220)) }

        gesture.half = halfPx
        gesture.full = fullPx
        gesture.snap = snap
        gesture.keyboardVisible = keyboardVisible
        gesture.onClose = onClose

        fun changeSnap(next: Snap) {
            // Bajar a media altura mientras se escribe cierra el teclado.
            if (next == Snap.Half && gesture.keyboardVisible) {
                keyboard?.hide()
                focusManager.clearFocus()
            }
            snap = next
        }

        fun releaseGesture(dy: Float, vy: Float) {
            val half = gesture.half
            val full = gesture.full
            val startHeight = gesture.startHeight
            val belowHalf = half - (startHeight - dy)
            val startedAtHalf = startHeight <= half + 1

            if (belowHalf > dismissDistancePx || (startedAtHalf && vy > DISMISS_VELOCITY)) {
                scope.launch {
                    dragY.animateTo(full + with(density) { 100.dp.toPx() }, tween(200))
                    gesture.onClose()
                }
                return
            }

            scope.launch { dragY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy)) }

            var next: Snap? = null
            if (vy < -SNAP_VELOCITY || dy < -snapDistancePx) next = Snap.Full
            else if (vy > SNAP_VELOCITY || dy > snapDistancePx) next = Snap.Half

            // Un arrastre corto no cambia el snap: la hoja vuelve a la altura que tenía.
            if (next != null) changeSnap(next)
            val resolved = next ?: gesture.snap
            val target = if (resolved == Snap.Full || (gesture.keyboardVisible && next != Snap.Half)) full else half
            scope.launch { maxHeight.animateTo(target, tween(220)) }
        }

        val isFull = snap == Snap.Full || keyboardVisible
        val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        val maxHeightDp = with(density) { maxHeight.value.toDp() }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxHeightDp)
                .offset { IntOffset(0, (dragY.value + slideFraction * availablePx).roundToInt()) }
                .clip(sheetShape)
                .background(colors.surfaceContainerHigh, sheetShape)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        ) {
            // Asa + título: zona de arrastre (PanResponder.panHandlers)
            var headerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
            Column(
                Modifier
                    .onGloballyPositioned { headerCoords = it }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val coords = headerCoords ?: return@awaitEachGesture
                            val inHandle = down.position.y <= with(density) { 42.dp.toPx() } &&
                                abs(down.position.x - size.width / 2f) <= with(density) { 64.dp.toPx() }
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, Offset(0f, coords.localToRoot(down.position).y))
                            var baseline = coords.localToRoot(down.position).y
                            var dragging = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val rootY = coords.localToRoot(change.position).y
                                tracker.addPosition(change.uptimeMillis, Offset(0f, rootY))
                                if (change.changedToUpIgnoreConsumed()) {
                                    if (dragging) {
                                        val vy = tracker.calculateVelocity().y / density.density / 1000f
                                        releaseGesture(rootY - baseline, vy)
                                    } else if (inHandle) {
                                        changeSnap(if (gesture.snap == Snap.Full || gesture.keyboardVisible) Snap.Half else Snap.Full)
                                    }
                                    break
                                }
                                if (!dragging && abs(rootY - baseline) > slopPx) {
                                    dragging = true
                                    baseline = rootY
                                    gesture.startHeight = if (gesture.snap == Snap.Full || gesture.keyboardVisible) gesture.full else gesture.half
                                }
                                if (dragging) {
                                    change.consume()
                                    val dy = rootY - baseline
                                    val next = gesture.startHeight - dy // arrastrar hacia arriba (dy < 0) agranda la hoja
                                    scope.launch {
                                        if (next >= gesture.half) {
                                            maxHeight.snapTo(min(next, gesture.full))
                                            dragY.snapTo(0f)
                                        } else {
                                            // Por debajo de media altura la hoja baja para cerrarse.
                                            maxHeight.snapTo(gesture.half)
                                            dragY.snapTo(gesture.half - next)
                                        }
                                    }
                                }
                            }
                        }
                    },
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 6.dp, bottom = 6.dp)
                        .padding(bottom = 10.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = if (isFull) "Contraer" else "Expandir"
                        },
                ) {
                    Box(
                        Modifier
                            .width(32.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.outline, RoundedCornerShape(2.dp)),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = title,
                        color = colors.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    )
                    Box(
                        Modifier
                            .touchableOpacity(onClickLabel = "Cerrar", onClick = onClose)
                            .padding(4.dp)
                            .semantics { contentDescription = "Cerrar" },
                    ) {
                        Ionicon("close-circle", 28, colors.onSurfaceVariant)
                    }
                }
            }

            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState)
                    .padding(bottom = if (keyboardVisible) 16.dp else 24.dp + bottomInset()),
                content = content,
            )

            // Espacio del teclado: la hoja llega hasta el borde de la pantalla y deja libre la franja que ocupa
            // el IME (vale 0 con el teclado oculto y sigue su animación al abrirse o cerrarse).
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.ime))
        }
    }
}
