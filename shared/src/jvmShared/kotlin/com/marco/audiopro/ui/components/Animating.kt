package com.marco.audiopro.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalWindowInfo
import com.marco.audiopro.ui.Immersive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Lo pone la PC preguntándole a Windows, porque Java no siempre se entera (minimizada sigue
 * "enfocada"). null = la plataforma no avisa (celular).
 */
object DesktopWindow {
    /** Es la ventana activa: se usa el mouse o el control en ella. */
    var foreground by mutableStateOf<Boolean?>(null)
    /** Se alcanza a ver algo de ella: no está minimizada, oculta ni tapada por completo. */
    var visible by mutableStateOf<Boolean?>(null)
}

/**
 * Si vale la pena animar: con la ventana a la vista sí, aunque estés en otra cosa (en otro monitor,
 * por ejemplo). Si nadie la puede ver (minimizada o con un juego encima) lo que se mueve sin fin se
 * congela para no robarle CPU y GPU a lo demás. La música sigue sonando igual.
 */
@Composable
fun rememberAnimating(): State<Boolean> =
    rememberUpdatedState(DesktopWindow.visible ?: (LocalWindowInfo.current.isWindowFocused || Immersive.on))

/** Espera, sin gastar nada, a que vuelva a valer la pena animar. */
suspend fun State<Boolean>.awaitAnimating() { if (!value) snapshotFlow { value }.first { it } }

/**
 * Cuadros para lo que se mueve solo (fondos, visualizadores): se pausa si la ventana no se ve, no
 * pasa de ~60 por segundo y, si estás en otra ventana, va a ~20 ([lively]: ~30, pantalla completa y visualizadores grandes).
 */
suspend fun animationFrames(animating: State<Boolean>, lively: () -> Boolean = { false }, onFrame: (Long) -> Unit): Nothing {
    while (true) {
        animating.awaitAnimating()
        val t = withFrameNanos { it.also(onFrame) }
        val minFrame = when {
            DesktopWindow.foreground != false -> FRAME_NS
            lively() -> LIVELY_BACKGROUND_FRAME_NS
            else -> CALM_BACKGROUND_FRAME_NS
        }
        // Hasta el tiempo mínimo y luego al siguiente refresco del monitor.
        val wait = ((t + minFrame - System.nanoTime()) / 1_000_000).coerceAtMost(minFrame / 1_000_000)
        if (wait > 0) delay(wait)
    }
}

/** Fase 0..1 que da la vuelta cada [periodMs] por [animationFrames] (una animación infinita de Compose iría a la velocidad del monitor). */
@Composable
fun rememberLoopPhase(periodMs: Int): State<Float> {
    val phase = remember { mutableFloatStateOf(0f) }
    val animating = rememberAnimating()
    LaunchedEffect(periodMs) {
        animationFrames(animating) { t -> phase.floatValue = (t / 1_000_000 % periodMs) / periodMs.toFloat() }
    }
    return phase
}

private const val FRAME_NS = 12_000_000L
private const val LIVELY_BACKGROUND_FRAME_NS = 28_000_000L
private const val CALM_BACKGROUND_FRAME_NS = 45_000_000L
