package com.marco.audiopro.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap

/** Lo que la ventana de PC le presta a la UI para dibujar su propia barra de título. */
class WindowControls(
    val minimize: () -> Unit,
    val toggleMaximize: () -> Unit,
    val close: () -> Unit,
    val maximized: State<Boolean>,
)

/**
 * Barra de título propia (como Discord o Claude): Windows deja de pintar la suya y la app pone
 * sus botones. Las zonas marcadas con [windowDragArea] mueven la ventana con el comportamiento
 * nativo (arrastrar, doble clic para maximizar, acoplar a los lados); las lee el hit-test de Windows.
 */
object WindowChrome {
    /** null = la ventana conserva la barra de Windows (otras plataformas, pruebas). */
    var controls by mutableStateOf<WindowControls?>(null)

    /** Zonas para arrastrar, en pixeles de la ventana. */
    private val dragRects = ConcurrentHashMap<Any, Rect>()

    fun isDragArea(x: Float, y: Float): Boolean = dragRects.values.any { it.contains(Offset(x, y)) }

    internal fun put(key: Any, r: Rect) { dragRects[key] = r }
    internal fun remove(key: Any) { dragRects.remove(key) }
}

/** Esta parte de la UI (vacía, sin botones) sirve para arrastrar la ventana. */
fun Modifier.windowDragArea(): Modifier = composed {
    val key = remember { Any() }
    DisposableEffect(Unit) { onDispose { WindowChrome.remove(key) } }
    onGloballyPositioned { WindowChrome.put(key, it.boundsInWindow()) }
}

/**
 * Tira superior de la ventana: todo es arrastrable menos los botones de minimizar, maximizar y
 * cerrar (estilo Windows 11; cerrar se pone rojo).
 */
@Composable
fun WindowTitleStrip(modifier: Modifier = Modifier) {
    val ctl = WindowChrome.controls ?: return
    Row(modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f).fillMaxHeight().windowDragArea())
        CaptionButton(onClick = ctl.minimize) { c ->
            drawLine(c, Offset(size.width / 2 - 5.dp.toPx(), size.height / 2), Offset(size.width / 2 + 5.dp.toPx(), size.height / 2), 1.dp.toPx())
        }
        CaptionButton(onClick = ctl.toggleMaximize) { c ->
            val s = 9.dp.toPx(); val st = Stroke(1.dp.toPx())
            val o = Offset(size.width / 2 - s / 2, size.height / 2 - s / 2)
            if (ctl.maximized.value) {
                // Dos cuadritos encimados = restaurar.
                val d = 2.dp.toPx()
                drawRect(c, o + Offset(0f, d), Size(s - d, s - d), style = st)
                drawLine(c, o + Offset(d, d), o + Offset(d, 0f), st.width)
                drawLine(c, o + Offset(d, 0f), o + Offset(s, 0f), st.width)
                drawLine(c, o + Offset(s, 0f), o + Offset(s, s - d), st.width)
                drawLine(c, o + Offset(s, s - d), o + Offset(s - d, s - d), st.width)
            } else drawRect(c, o, Size(s, s), style = st)
        }
        CaptionButton(danger = true, onClick = ctl.close) { c ->
            val s = 5.dp.toPx(); val w = 1.dp.toPx()
            val m = Offset(size.width / 2, size.height / 2)
            drawLine(c, m + Offset(-s, -s), m + Offset(s, s), w)
            drawLine(c, m + Offset(-s, s), m + Offset(s, -s), w)
        }
    }
}

@Composable
private fun CaptionButton(danger: Boolean = false, onClick: () -> Unit, glyph: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val bg by animateColorAsState(
        when { hovered && danger -> Color(0xFFC42B1C); hovered -> Color.White.copy(alpha = 0.08f); else -> Color.Transparent }, tween(90), label = "cap",
    )
    Box(
        Modifier.width(46.dp).fillMaxHeight().background(bg).hoverable(src).clickable(src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val fg = if (hovered && danger) Color.White else Color.White.copy(alpha = 0.82f)
        Canvas(Modifier.size(46.dp, 34.dp)) { glyph(fg) }
    }
}
