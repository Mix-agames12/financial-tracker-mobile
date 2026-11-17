package com.mixagames666.trackerrntemp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role

/**
 * Equivalente a `TouchableOpacity`: al pulsar baja la opacidad (0.2 por defecto) en lugar de mostrar
 * la onda de Material. Es la interacción de todos los botones, chips y filas tocables del original.
 */
fun Modifier.touchableOpacity(
    enabled: Boolean = true,
    activeOpacity: Float = 0.2f,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        targetValue = if (pressed && enabled) activeOpacity else 1f,
        animationSpec = tween(durationMillis = if (pressed) 0 else 250),
        label = "touchableOpacity",
    )
    this
        .alpha(alpha)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
}
