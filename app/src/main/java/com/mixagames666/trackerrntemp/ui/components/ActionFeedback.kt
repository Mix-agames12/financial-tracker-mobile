package com.mixagames666.trackerrntemp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.mixagames666.trackerrntemp.ui.theme.AppTheme
import com.mixagames666.trackerrntemp.ui.theme.topInset
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Equivalente a `ToastManager` de ActionFeedback.tsx: cualquier parte de la app puede pedir un aviso. */
object ToastManager {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    fun show(message: String = "Guardado con éxito") {
        _events.tryEmit(message)
    }
}

/**
 * Aviso de confirmación animado (resorte al aparecer, 2 s visible, 250 ms para ocultarse), anclado arriba.
 * Origami(tension 80, fricción 5) de React Native = rigidez 375 y amortiguación 0.41 en Compose.
 */
@Composable
fun ActionFeedback() {
    val colors = AppTheme.colors
    var message by remember { mutableStateOf("") }
    val scale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        ToastManager.events.collectLatest { text ->
            message = text
            scale.snapTo(0f)
            coroutineScope {
                launch { scale.animateTo(1f, spring(dampingRatio = 0.41f, stiffness = 375f)) }
                delay(2000)
            }
            scale.animateTo(0f, tween(250))
        }
    }

    val shape = RoundedCornerShape(30.dp)
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = max(topInset() + 20.dp, 40.dp))
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    alpha = minOf(1f, scale.value * 2f)
                }
                .shadow(8.dp, shape)
                .background(colors.primaryContainer, shape)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ionicon("checkmark-circle", 24, colors.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                color = colors.onPrimaryContainer,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
