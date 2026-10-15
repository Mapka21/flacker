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
import kotlin.math.max
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
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

/**
 * Portada que es el control (celular, sin botones):
 * - toque: play / pausa (con un ícono grande que aparece y se va);
 * - deslizar a los lados: siguiente / anterior;
 * - mantener y arrastrar: "rayar el disco" para adelantar o regresar (vibra cada 5 s y muestra el tiempo);
 * - deslizar hacia abajo no se toca aquí: lo toma el reproductor para cerrarse.
 */
@Composable
fun GestureCover(
    playing: Boolean,
    durationMs: Long,
    position: () -> Long,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val toggle by rememberUpdatedState(onToggle)
    val next by rememberUpdatedState(onNext)
    val prev by rememberUpdatedState(onPrevious)
    val seek by rememberUpdatedState(onSeek)
    val dur by rememberUpdatedState(durationMs)
    val pos by rememberUpdatedState(position)
    val isPlaying by rememberUpdatedState(playing)
    val width = remember { floatArrayOf(1f) }
    // Ícono de play/pausa que aparece al tocar.
    val flash = remember { Animatable(0f) }
    var flashPlaying by remember { mutableStateOf(playing) }
    // Modo "rayar": a dónde iría la canción.
    var scrubTo by remember { mutableStateOf<Long?>(null) }
    val scrubScale by androidx.compose.animation.core.animateFloatAsState(if (scrubTo != null) 0.92f else 1f, spring(dampingRatio = 0.7f), label = "scrub")

    Box(
        modifier
            .onSizeChanged { width[0] = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = viewConfiguration.touchSlop
                    val longMs = viewConfiguration.longPressTimeoutMillis
                    val t0 = System.currentTimeMillis()
                    val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                    var mode = 0 // 0 = aún no, 1 = deslizar, 2 = rayar, 3 = vertical (lo toma el padre)
                    var total = Offset.Zero
                    var scrubStart = 0L
                    var scrubDx = 0f
                    var lastTick = 0L
                    while (true) {
                        val ev = if (mode == 0) {
                            val left = longMs - (System.currentTimeMillis() - t0)
                            if (left <= 0) null else withTimeoutOrNull(left) { awaitPointerEvent() }
                        } else awaitPointerEvent()
                        if (ev == null) {
                            // Se mantuvo sin moverse: modo rayar.
                            mode = 2
                            scrubStart = pos()
                            scrubTo = scrubStart
                            lastTick = scrubStart / 5000
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            continue
                        }
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) {
                            when (mode) {
                                0 -> {
                                    flashPlaying = !isPlaying
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    toggle()
                                    scope.launch { flash.snapTo(1f); flash.animateTo(0f, tween(650)) }
                                }
                                1 -> {
                                    val v = tracker.calculateVelocity().x
                                    val w = width[0]
                                    scope.launch {
                                        if (abs(offset.value) > w / 3 || abs(v) > 1500f) {
                                            val dir = if (offset.value + v * 0.05f < 0) -1f else 1f
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            offset.animateTo(dir * w, tween(140))
                                            if (dir < 0) next() else prev()
                                            offset.snapTo(-dir * w * 0.6f)
                                            offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 420f))
                                        } else offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f))
                                    }
                                }
                                2 -> { scrubTo?.let(seek); scrubTo = null }
                            }
                            break
                        }
                        tracker.addPosition(ch.uptimeMillis, ch.position)
                        val d = ch.positionChange()
                        total += d
                        when (mode) {
                            0 -> if (abs(total.x) > slop && abs(total.x) > abs(total.y)) { mode = 1; ch.consume() }
                                 else if (abs(total.y) > slop) mode = 3
                            1 -> { ch.consume(); scope.launch { offset.snapTo(offset.value + d.x) } }
                            2 -> {
                                ch.consume()
                                scrubDx += d.x
                                // Todo el ancho de la portada = medio minuto (o 1/4 de la canción si es larga).
                                val span = max(30_000L, dur / 4)
                                val t = (scrubStart + (scrubDx / width[0] * span).toLong()).coerceIn(0L, max(0L, dur - 1000))
                                scrubTo = t
                                if (t / 5000 != lastTick) { lastTick = t / 5000; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                            }
                            else -> {}
                        }
                    }
                }
            }
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (abs(offset.value) / (width[0] * 1.4f)).coerceIn(0f, 0.7f)
                scaleX = scrubScale; scaleY = scrubScale
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
        // Play/pausa grande que aparece al tocar.
        if (flash.value > 0.01f) {
            Box(
                Modifier.size(96.dp).graphicsLayer { alpha = flash.value; scaleX = 1.4f - 0.4f * flash.value; scaleY = scaleX }
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (flashPlaying) Icons.Default.PlayArrow else Icons.Default.Pause, null, tint = Color.White, modifier = Modifier.size(56.dp))
            }
        }
        // Rayando el disco: el tiempo a donde vas.
        scrubTo?.let { t ->
            Box(Modifier.matchParentSize().clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatMs(t), style = com.marco.audiopro.ui.theme.Display.copy(fontSize = 44.sp, color = Color.White))
                    Text("de ${formatMs(dur)}", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.width(180.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
                        Box(Modifier.fillMaxWidth(if (dur > 0) (t.toFloat() / dur).coerceIn(0f, 1f) else 0f).fillMaxHeight().background(Color.White))
                    }
                }
            }
        }
    }
}
