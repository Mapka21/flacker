package com.marco.audiopro.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.resources.Res
import com.marco.audiopro.resources.app_icon
import com.marco.audiopro.ui.components.CoverArt
import com.marco.audiopro.ui.components.formatMs
import com.marco.audiopro.ui.rememberPlaybackPosition
import com.marco.audiopro.ui.theme.MarcoTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

/** Miniplayer de PC: un iPod classic negro flotante, siempre encima, con click wheel funcional. */
@Composable
fun IpodWindow(graph: DesktopGraph, onExpand: () -> Unit, onKey: (KeyEvent) -> Boolean) {
    val state = rememberWindowState(
        size = DpSize(250.dp, 420.dp),
        position = WindowPosition(Alignment.BottomEnd),
    )
    Window(
        onCloseRequest = onExpand,
        state = state,
        title = "Marco Audio Pro · mini",
        icon = painterResource(Res.drawable.app_icon),
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        onPreviewKeyEvent = onKey,
    ) {
        CompositionLocalProvider(LocalApp provides graph) {
            MarcoTheme(background = false) { IpodBody(graph.player, onExpand) { content -> WindowDraggableArea { content() } } }
        }
    }
}

// Colores del iPod classic negro.
private val BodyTop = Color(0xFF3A3C42)
private val BodyBottom = Color(0xFF16171A)
private val WheelEdge = Color(0xFF1F2024)
private val WheelMid = Color(0xFF2C2E33)
private val WheelLabel = Color(0xFFD5D7DC)
private val ScreenText = Color(0xFF111111)
private val ScreenDim = Color(0xFF5A5F68)

/** @param dragArea envuelve la pantalla para poder arrastrar la ventana (en pruebas, nada). */
@Composable
internal fun IpodBody(player: DesktopPlayer, onExpand: () -> Unit, dragArea: @Composable (@Composable () -> Unit) -> Unit) {
    // Al girar la rueda se muestra el volumen en pantalla un momento, como en el iPod.
    var volumeShownAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(100) } }
    val showVolume = now - volumeShownAt < 1500

    Box(Modifier.fillMaxSize().padding(6.dp)) {
        Column(
            Modifier.fillMaxSize()
                .shadow(14.dp, RoundedCornerShape(30.dp))
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.verticalGradient(listOf(BodyTop, BodyBottom)))
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.03f))), RoundedCornerShape(30.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // La pantalla también sirve para arrastrar la ventana; doble clic = app completa.
            dragArea { Screen(player, showVolume, onExpand) }
            Spacer(Modifier.weight(1f))
            ClickWheel(
                player,
                onMenu = onExpand,
                onRotate = { steps ->
                    player.setVolume(player.volume.value + steps * 0.02f)
                    volumeShownAt = System.currentTimeMillis()
                },
            )
            Spacer(Modifier.weight(1f))
        }
        // Cerrar = volver a la app completa (no apaga la música).
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp).size(18.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .pointerInput(Unit) { detectTapGestures { onExpand() } },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Close, "Volver a la app", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(11.dp)) }
    }
}

@Composable
private fun Screen(player: DesktopPlayer, showVolume: Boolean, onExpand: () -> Unit) {
    val st by player.state.collectAsState()
    val song = st.current
    // Bisel negro + pantalla clara clásica.
    Box(
        Modifier.width(212.dp).height(166.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black).padding(4.dp)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onExpand() }) },
    ) {
        Column(
            Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFFDFDFE), Color(0xFFE3E7EE)))),
        ) {
            // Barra de título plateada.
            Row(
                Modifier.fillMaxWidth().height(17.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFFF9F9FA), Color(0xFFC4CAD3))))
                    .padding(horizontal = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (st.isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause, null, tint = Color(0xFF2F6FD6), modifier = Modifier.size(11.dp))
                Text(
                    "Reproduciendo", color = ScreenText, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Battery()
            }
            if (song == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Sin música", color = ScreenDim, fontSize = 11.sp) }
                return@Column
            }
            Row(Modifier.padding(start = 6.dp, end = 6.dp, top = 7.dp), verticalAlignment = Alignment.Top) {
                CoverArt(song.coverArt, Modifier.size(68.dp), size = 200, corner = 3.dp)
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    if (st.queue.isNotEmpty()) {
                        Text("${st.currentIndex + 1} de ${st.queue.size}", color = ScreenDim, fontSize = 8.sp)
                    }
                    Text(song.title, color = ScreenText, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee())
                    Text(song.artist ?: "", color = ScreenText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.album ?: "", color = ScreenDim, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.weight(1f))
            AnimatedContent(showVolume, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "vol") { vol ->
                if (vol) VolumeBar(player) else ProgressBar(st.durationMs)
            }
        }
    }
}

@Composable
private fun ProgressBar(durationMs: Long) {
    val pos by rememberPlaybackPosition(250)
    val dur = durationMs.coerceAtLeast(1)
    val frac = (pos.toFloat() / dur).coerceIn(0f, 1f)
    Column(Modifier.padding(horizontal = 8.dp).padding(bottom = 6.dp)) {
        GlossyBar(frac)
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(formatMs(pos), color = ScreenText, fontSize = 8.sp, modifier = Modifier.weight(1f))
            Text("-" + formatMs(dur - pos), color = ScreenText, fontSize = 8.sp)
        }
    }
}

@Composable
private fun VolumeBar(player: DesktopPlayer) {
    val vol by player.volume.collectAsState()
    Row(Modifier.padding(horizontal = 8.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.VolumeDown, null, tint = ScreenText, modifier = Modifier.size(12.dp))
        Box(Modifier.weight(1f).padding(horizontal = 4.dp)) { GlossyBar(vol) }
        Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = ScreenText, modifier = Modifier.size(12.dp))
    }
}

/** Barra azul brillante del iPod. */
@Composable
private fun GlossyBar(frac: Float) {
    val anim by animateFloatAsState(frac, label = "gloss")
    Box(
        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(2.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFDADFE6), Color(0xFFF7F8FA))))
            .border(0.5.dp, Color(0xFF8E96A3), RoundedCornerShape(2.dp)),
    ) {
        Box(
            Modifier.fillMaxWidth(anim).fillMaxHeight()
                .background(Brush.verticalGradient(listOf(Color(0xFF8CC4FF), Color(0xFF2F7BE0), Color(0xFF1D5FBE)))),
        )
    }
}

@Composable
private fun Battery() {
    Canvas(Modifier.size(width = 17.dp, height = 8.dp)) {
        val stroke = 1.dp.toPx()
        drawRoundRect(Color(0xFF3A3F47), Offset.Zero, Size(size.width - 2.dp.toPx(), size.height), CornerRadius(1.5f.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        drawRect(Color(0xFF3A3F47), Offset(size.width - 2.dp.toPx(), size.height * 0.3f), Size(2.dp.toPx(), size.height * 0.4f))
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFF9BE37A), Color(0xFF3FA52B))),
            Offset(stroke * 1.5f, stroke * 1.5f), Size((size.width - 2.dp.toPx() - stroke * 3f) * 0.85f, size.height - stroke * 3f),
        )
    }
}

/**
 * Click wheel: girar el dedo/mouse sobre el anillo cambia el volumen (sentido horario = más);
 * los cuatro puntos cardinales son MENU / ⏭ / ⏯ / ⏮ y el centro es play/pausa.
 */
@Composable
private fun ClickWheel(player: DesktopPlayer, onMenu: () -> Unit, onRotate: (Int) -> Unit) {
    var pressedCenter by remember { mutableStateOf(false) }
    val centerScale by animateFloatAsState(if (pressedCenter) 0.94f else 1f, label = "center")
    Box(
        Modifier.size(170.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(WheelMid, WheelEdge)))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.4f))), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { o ->
                        val c = Offset(size.width / 2f, size.height / 2f)
                        if (hypot(o.x - c.x, o.y - c.y) < size.width * 0.21f) {
                            pressedCenter = true
                            tryAwaitRelease()
                            pressedCenter = false
                        }
                    },
                    onTap = { o ->
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val dx = o.x - c.x; val dy = o.y - c.y
                        if (hypot(dx, dy) < size.width * 0.21f) { player.toggle(); return@detectTapGestures }
                        val deg = Math.toDegrees(atan2(dy, dx).toDouble())
                        when {
                            deg in -135.0..-45.0 -> onMenu()
                            deg in -45.0..45.0 -> player.next()
                            deg in 45.0..135.0 -> player.toggle()
                            else -> player.previous()
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                var lastAngle = 0.0
                var acc = 0.0
                detectDragGestures(
                    onDragStart = { o ->
                        lastAngle = atan2((o.y - size.height / 2f).toDouble(), (o.x - size.width / 2f).toDouble())
                        acc = 0.0
                    },
                ) { change, _ ->
                    val p = change.position
                    val a = atan2((p.y - size.height / 2f).toDouble(), (p.x - size.width / 2f).toDouble())
                    var d = a - lastAngle
                    if (d > PI) d -= 2 * PI
                    if (d < -PI) d += 2 * PI
                    lastAngle = a
                    acc += d
                    // Un "clic" de volumen cada ~12°, como los detents del iPod.
                    val step = Math.toRadians(12.0)
                    val n = (acc / step).toInt()
                    if (n != 0) { acc -= n * step; onRotate(n) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("MENU", color = WheelLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp))
        Icon(Icons.Default.SkipNext, "Siguiente", tint = WheelLabel, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(18.dp))
        Icon(Icons.Default.SkipPrevious, "Anterior", tint = WheelLabel, modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp).size(18.dp))
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 13.dp), horizontalArrangement = Arrangement.spacedBy((-3).dp)) {
            Icon(Icons.Default.PlayArrow, null, tint = WheelLabel, modifier = Modifier.size(13.dp))
            Icon(Icons.Default.Pause, "Play/pausa", tint = WheelLabel, modifier = Modifier.size(13.dp))
        }
        // Botón central.
        Box(
            Modifier.size(64.dp)
                .graphicsLayerScale(centerScale)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF34363C), Color(0xFF1C1D21))))
                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
        )
    }
}

private fun Modifier.graphicsLayerScale(s: Float) = this.then(
    Modifier.graphicsLayer(scaleX = s, scaleY = s),
)
