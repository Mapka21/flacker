package com.marco.audiopro.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Deslizar a los lados para cambiar de canción (como el mini reproductor de Spotify): el contenido
 * sigue al dedo; si pasa de un tercio (o se avienta rápido), sale por ese lado, vibra, cambia la
 * canción y entra la nueva del otro lado. Si no, regresa con resorte.
 */
@Composable
fun SwipeToSkip(
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val next by rememberUpdatedState(onNext)
    val prev by rememberUpdatedState(onPrevious)
    val width = remember { floatArrayOf(1f) }
    Box(
        modifier
            .onSizeChanged { width[0] = it.width.toFloat().coerceAtLeast(1f) }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { d -> scope.launch { offset.snapTo(offset.value + d) } },
                onDragStopped = { v ->
                    val w = width[0]
                    val go = abs(offset.value) > w / 3 || abs(v) > 2200f
                    if (!go) { offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f)); return@draggable }
                    val dir = if (offset.value + v * 0.05f < 0) -1f else 1f
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    offset.animateTo(dir * w, tween(140))
                    if (dir < 0) next() else prev()
                    offset.snapTo(-dir * w * 0.6f)
                    offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 420f))
                },
            )
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (abs(offset.value) / (width[0] * 1.4f)).coerceIn(0f, 0.7f)
            },
        content = content,
    )
}

/** Vibración cortita para confirmar acciones (play, me gusta, cambiar de pestaña). */
@Composable
fun rememberTick(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
}
