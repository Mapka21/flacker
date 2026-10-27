package com.marco.audiopro.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalWindowInfo
import com.marco.audiopro.ui.Immersive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Lo pone la PC preguntándole a Windows si la ventana está al frente: Java no siempre se entera
 * cuando la dejas atrás (minimizada sigue "enfocada"). null = la plataforma no avisa (celular).
 */
object WindowForeground { var on by mutableStateOf<Boolean?>(null) }

/**
 * Si vale la pena animar. Con la ventana atrás (jugando, en otra app, minimizada) lo que se mueve
 * sin fin se congela: si no, Flacker redibuja la ventana entera (con todo y difuminados) sin que
 * nadie la vea y le roba CPU y GPU al juego. La música sigue sonando igual.
 * En pantalla completa (inmersivo) no se frena: ahí lo visual es lo que se quiere ver.
 */
@Composable
fun rememberAnimating(): State<Boolean> =
    rememberUpdatedState((WindowForeground.on ?: LocalWindowInfo.current.isWindowFocused) || Immersive.on)

/** Espera, sin gastar nada, a que vuelva a valer la pena animar. */
suspend fun State<Boolean>.awaitAnimating() { if (!value) snapshotFlow { value }.first { it } }

/**
 * Cuadros para lo que se mueve solo (fondos, visualizadores): se pausa con la ventana atrás y no
 * pasa de ~60-80 por segundo. En un monitor de 165 Hz no se nota en algo que fluye, y es la mitad del trabajo.
 */
suspend fun animationFrames(animating: State<Boolean>, onFrame: (Long) -> Unit): Nothing {
    while (true) {
        animating.awaitAnimating()
        val t = withFrameNanos { it.also(onFrame) }
        // 12 ms y luego al siguiente refresco: 60 Hz y 75 Hz quedan completos, 144/165 Hz se saltan cuadros.
        val wait = ((t + MIN_FRAME_NS - System.nanoTime()) / 1_000_000).coerceAtMost(MIN_FRAME_NS / 1_000_000)
        if (wait > 0) delay(wait)
    }
}

private const val MIN_FRAME_NS = 12_000_000L
