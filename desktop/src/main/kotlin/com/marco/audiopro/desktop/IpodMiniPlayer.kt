package com.marco.audiopro.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.marco.audiopro.ui.viz.DuskPalette
import com.marco.audiopro.ui.viz.LiveAudio
import com.marco.audiopro.ui.viz.VisualPreset
import com.marco.audiopro.ui.viz.VisualScene
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import coil3.compose.LocalPlatformContext
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.player.PlayerController
import com.marco.audiopro.resources.Res
import com.marco.audiopro.resources.app_icon
import com.marco.audiopro.ui.components.CoverArt
import com.marco.audiopro.ui.components.coverColors
import com.marco.audiopro.ui.components.formatMs
import com.marco.audiopro.ui.rememberPlaybackPosition
import com.marco.audiopro.ui.theme.MarcoTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

/** Tamaños del iPod: todo el cuerpo se escala junto (texto, rueda y pantalla). */
enum class IpodSize(val label: String, val scale: Float) { S("S", 0.8f), M("M", 1f), L("L", 1.45f) }

/** Lo que muestra la pantalla: la canción o un visualizador (tocar la pantalla cambia). */
enum class IpodScreen(val preset: VisualPreset?) { INFO(null), NEON(VisualPreset.NEON), NEXO(VisualPreset.NEXO), HIPER(VisualPreset.HIPER), HALO(VisualPreset.HALO) }

private val BaseSize = DpSize(178.dp, 266.dp)

/**
 * Miniplayer de PC: un iPod nano (3ª gen, el "gordito") del color de la portada, en tres
 * tamaños, siempre encima y arrastrable desde cualquier parte menos la rueda.
 */
@Composable
fun IpodWindow(graph: DesktopGraph, onExpand: () -> Unit, onKey: (KeyEvent) -> Boolean) {
    var ipodSize by remember { mutableStateOf(enumValueOrNull<IpodSize>(graph.settings.getString("ipod_size")) ?: IpodSize.M) }
    val state = rememberWindowState(size = BaseSize * ipodSize.scale, position = WindowPosition(Alignment.BottomEnd))
    Window(
        onCloseRequest = onExpand,
        state = state,
        title = "Flacker · nano",
        icon = painterResource(Res.drawable.app_icon),
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        onPreviewKeyEvent = onKey,
    ) {
        CompositionLocalProvider(LocalApp provides graph) {
            MarcoTheme(background = false) {
                IpodBody(
                    graph.player, onExpand, ipodSize,
                    onResize = {
                        val next = IpodSize.entries[(ipodSize.ordinal + 1) % IpodSize.entries.size]
                        // Crece hacia arriba y a la izquierda: la esquina de abajo a la derecha no se mueve.
                        val old = state.size
                        val new = BaseSize * next.scale
                        (state.position as? WindowPosition.Absolute)?.let { p ->
                            state.position = WindowPosition(p.x + old.width - new.width, p.y + old.height - new.height)
                        }
                        state.size = new
                        ipodSize = next
                        graph.settings.putString("ipod_size", next.name)
                    },
                ) { content -> WindowDraggableArea { content() } }
            }
        }
    }
}

private inline fun <reified E : Enum<E>> enumValueOrNull(name: String?): E? =
    name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() }

private val ScreenText = Color(0xFF111111)
private val ScreenDim = Color(0xFF5A5F68)
private val Silver = Color(0xFFB9BEC6)

/**
 * @param dragArea envuelve el cuerpo para poder arrastrar la ventana (en pruebas, nada).
 *   La rueda consume sus propios toques, así que arrastrar sobre ella no mueve la ventana.
 */
@Composable
internal fun IpodBody(
    player: PlayerController,
    onExpand: () -> Unit,
    size: IpodSize = IpodSize.M,
    onResize: () -> Unit = {},
    dragArea: @Composable (@Composable () -> Unit) -> Unit,
) {
    // Se dibuja siempre en las medidas del modelo M y se escala con la densidad.
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density * size.scale, d.fontScale)) {
        IpodBodyContent(player, onExpand, size, onResize, dragArea)
    }
}

@Composable
private fun IpodBodyContent(
    player: PlayerController,
    onExpand: () -> Unit,
    size: IpodSize,
    onResize: () -> Unit,
    dragArea: @Composable (@Composable () -> Unit) -> Unit,
) {
    val ctx = LocalPlatformContext.current
    val app = LocalApp.current
    val api = app.api
    val st by player.state.collectAsState()
    var screen by remember { mutableStateOf(enumValueOrNull<IpodScreen>(app.settings.getString("ipod_screen")) ?: IpodScreen.INFO) }

    // Aluminio anodizado del color de la portada (como los nano de colores); plata si no hay.
    var bodyTarget by remember { mutableStateOf(Silver) }
    LaunchedEffect(st.current?.coverArt) {
        bodyTarget = coverColors(ctx, api, st.current?.coverArt)?.accent?.let { lerp(it, Color.Black, 0.12f) } ?: Silver
    }
    val body by animateColorAsState(bodyTarget, tween(900), label = "body")
    val onBodyDark = body.luminance() < 0.45f

    var volumeShownAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(100) } }
    val showVolume = now - volumeShownAt < 1500

    // Textos de 7–10 sp con interlineado compacto (el del tema es de 24 sp).
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(lineHeight = 1.18.em)) {
    // La rueda del mouse en cualquier parte del iPod = volumen (como girar la click wheel).
    Box(Modifier.fillMaxSize().padding(5.dp).pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent()
                if (e.type == PointerEventType.Scroll) {
                    val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (dy != 0f) {
                        player.setVolume((player.volume?.value ?: 0f) - dy * 0.04f)
                        volumeShownAt = System.currentTimeMillis()
                        e.changes.forEach { it.consume() }
                    }
                }
            }
        }
    }) {
        dragArea {
            Column(
                Modifier.fillMaxSize()
                    .shadow(12.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(lerp(body, Color.White, 0.30f), body, lerp(body, Color.Black, 0.25f))))
                    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.05f))), RoundedCornerShape(24.dp))
                    .padding(horizontal = 11.dp, vertical = 11.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Screen(player, showVolume, screen, onExpand) {
                    screen = IpodScreen.entries[(screen.ordinal + 1) % IpodScreen.entries.size]
                    app.settings.putString("ipod_screen", screen.name)
                }
                Spacer(Modifier.weight(1f))
                ClickWheel(
                    player,
                    onMenu = onExpand,
                    onRotate = { steps ->
                        player.setVolume((player.volume?.value ?: 0f) + steps * 0.02f)
                        volumeShownAt = System.currentTimeMillis()
                    },
                )
                Spacer(Modifier.weight(1f))
            }
        }
        // Cerrar = volver a la app completa (la música sigue).
        Box(
            Modifier.align(Alignment.TopEnd).size(16.dp).clip(CircleShape)
                .background(if (onBodyDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.45f))
                .pointerInput(Unit) { detectTapGestures { onExpand() } },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Close, "Volver a la app", tint = Color.White, modifier = Modifier.size(10.dp)) }
        // Tamaño: S → M → L.
        Box(
            Modifier.align(Alignment.TopStart).size(16.dp).clip(CircleShape)
                .background(if (onBodyDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.45f))
                .pointerInput(Unit) { detectTapGestures { onResize() } },
            contentAlignment = Alignment.Center,
        ) { Text(size.label, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
    }
    }
}

@Composable
private fun Screen(player: PlayerController, showVolume: Boolean, screen: IpodScreen, onExpand: () -> Unit, onCycle: () -> Unit) {
    val st by player.state.collectAsState()
    val song = st.current
    Box(
        Modifier.fillMaxWidth().height(116.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black).padding(3.dp)
            .pointerInput(Unit) { detectTapGestures(onTap = { onCycle() }, onDoubleTap = { onExpand() }) },
    ) {
        val preset = screen.preset
        if (preset != null) {
            VizScreen(player, preset, showVolume)
            return@Box
        }
        Column(
            Modifier.fillMaxSize().clip(RoundedCornerShape(3.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFFDFDFE), Color(0xFFE3E7EE)))),
        ) {
            Row(
                Modifier.fillMaxWidth().height(13.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFFF9F9FA), Color(0xFFC4CAD3))))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (st.isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause, null, tint = Color(0xFF2F6FD6), modifier = Modifier.size(9.dp))
                Text("Reproduciendo", color = ScreenText, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Battery()
            }
            if (song == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Sin música", color = ScreenDim, fontSize = 9.sp) }
                return@Column
            }
            Row(Modifier.padding(start = 5.dp, end = 5.dp, top = 5.dp), verticalAlignment = Alignment.Top) {
                CoverArt(song.coverArt, Modifier.size(46.dp), size = 200, corner = 2.dp)
                Spacer(Modifier.width(5.dp))
                Column(Modifier.weight(1f)) {
                    // El título siempre se lee completo: corre como marquesina.
                    Text(song.title, color = ScreenText, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee())
                    Text(song.artist ?: "", color = ScreenText, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.album ?: "", color = ScreenDim, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (st.queue.isNotEmpty()) Text("${st.currentIndex + 1} de ${st.queue.size}", color = ScreenDim, fontSize = 7.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            AnimatedContent(showVolume, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "vol") { vol ->
                if (vol) VolumeBar(player) else ProgressBar(st.durationMs)
            }
        }
    }
}

/**
 * Pantalla en modo visualizador (como los del Xbox 360): el visual a pantalla completa y,
 * abajo, el título corriendo para saber qué suena.
 */
@Composable
private fun VizScreen(player: PlayerController, preset: VisualPreset, showVolume: Boolean) {
    val app = LocalApp.current
    val st by player.state.collectAsState()
    val playing by rememberUpdatedState(st.isPlaying)
    val scene = remember { VisualScene() }
    val tick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { t ->
            val dt = if (last == 0L) 0.016f else ((t - last) / 1e9f).coerceIn(0f, 0.1f)
            last = t
            LiveAudio.update(app.engine.tap, t, playing, 0)
            scene.step(dt, LiveAudio.features)
            tick.longValue = t
        }
    }
    var cols by remember { mutableStateOf(DuskPalette) }
    val ctx = LocalPlatformContext.current
    LaunchedEffect(st.current?.coverArt) { coverColors(ctx, app.api, st.current?.coverArt)?.let { cols = it.swatches } }
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(3.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            tick.longValue
            scene.draw(this, preset, cols, LiveAudio.features, 1f)
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 5.dp, vertical = 3.dp)) {
            if (showVolume) {
                VolumeBar(player)
            } else {
                Text(
                    listOfNotNull(st.current?.title, st.current?.artist).joinToString("  ·  "),
                    color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
                val pos by rememberPlaybackPosition(250)
                val frac = if (st.durationMs > 0) (pos.toFloat() / st.durationMs).coerceIn(0f, 1f) else 0f
                Box(Modifier.padding(top = 2.dp).fillMaxWidth().height(2.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Color.White.copy(alpha = 0.85f)))
                }
            }
        }
        Text(
            preset.label.uppercase(), color = Color.White.copy(alpha = 0.55f), fontSize = 6.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).padding(3.dp),
        )
    }
}

@Composable
private fun ProgressBar(durationMs: Long) {
    val pos by rememberPlaybackPosition(250)
    val dur = durationMs.coerceAtLeast(1)
    Column(Modifier.padding(horizontal = 6.dp).padding(bottom = 4.dp)) {
        GlossyBar((pos.toFloat() / dur).coerceIn(0f, 1f))
        Row(Modifier.fillMaxWidth().padding(top = 1.dp)) {
            Text(formatMs(pos), color = ScreenText, fontSize = 7.sp, modifier = Modifier.weight(1f))
            Text("-" + formatMs(dur - pos), color = ScreenText, fontSize = 7.sp)
        }
    }
}

@Composable
private fun VolumeBar(player: PlayerController) {
    val vol by (player.volume ?: kotlinx.coroutines.flow.MutableStateFlow(0f)).collectAsState()
    Row(Modifier.padding(horizontal = 6.dp).padding(bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.VolumeDown, null, tint = ScreenText, modifier = Modifier.size(10.dp))
        Box(Modifier.weight(1f).padding(horizontal = 3.dp)) { GlossyBar(vol) }
        Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = ScreenText, modifier = Modifier.size(10.dp))
    }
}

@Composable
private fun GlossyBar(frac: Float) {
    val anim by animateFloatAsState(frac, label = "gloss")
    Box(
        Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(2.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFDADFE6), Color(0xFFF7F8FA))))
            .border(0.5.dp, Color(0xFF8E96A3), RoundedCornerShape(2.dp)),
    ) {
        Box(Modifier.fillMaxWidth(anim).fillMaxHeight().background(Brush.verticalGradient(listOf(Color(0xFF8CC4FF), Color(0xFF2F7BE0), Color(0xFF1D5FBE)))))
    }
}

@Composable
private fun Battery() {
    Canvas(Modifier.size(width = 13.dp, height = 6.dp)) {
        val stroke = 0.8.dp.toPx()
        val tip = 1.5.dp.toPx()
        drawRoundRect(Color(0xFF3A3F47), Offset.Zero, Size(size.width - tip, size.height), CornerRadius(1.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        drawRect(Color(0xFF3A3F47), Offset(size.width - tip, size.height * 0.3f), Size(tip, size.height * 0.4f))
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFF9BE37A), Color(0xFF3FA52B))),
            Offset(stroke * 1.5f, stroke * 1.5f), Size((size.width - tip - stroke * 3f) * 0.85f, size.height - stroke * 3f),
        )
    }
}

/**
 * Click wheel blanca del nano: girar = volumen (horario sube), MENU/⏭/⏯/⏮ en los cuatro
 * puntos y el centro es play/pausa.
 */
@Composable
private fun ClickWheel(player: PlayerController, onMenu: () -> Unit, onRotate: (Int) -> Unit) {
    var pressedCenter by remember { mutableStateOf(false) }
    val centerScale by animateFloatAsState(if (pressedCenter) 0.92f else 1f, label = "center")
    val label = Color(0xFF8A8F98)
    Box(
        Modifier.size(108.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFFFAFAFB), Color(0xFFDDE0E5))))
            .border(0.8.dp, Color.Black.copy(alpha = 0.12f), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { o ->
                        val c = Offset(size.width / 2f, size.height / 2f)
                        if (hypot(o.x - c.x, o.y - c.y) < size.width * 0.19f) {
                            pressedCenter = true
                            tryAwaitRelease()
                            pressedCenter = false
                        }
                    },
                    onTap = { o ->
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val dx = o.x - c.x; val dy = o.y - c.y
                        if (hypot(dx, dy) < size.width * 0.19f) { player.toggle(); return@detectTapGestures }
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
                    change.consume()
                    val p = change.position
                    val a = atan2((p.y - size.height / 2f).toDouble(), (p.x - size.width / 2f).toDouble())
                    var d = a - lastAngle
                    if (d > PI) d -= 2 * PI
                    if (d < -PI) d += 2 * PI
                    lastAngle = a
                    acc += d
                    val step = Math.toRadians(14.0)
                    val n = (acc / step).toInt()
                    if (n != 0) { acc -= n * step; onRotate(n) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("MENU", color = label, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
        Icon(Icons.Default.SkipNext, "Siguiente", tint = label, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(13.dp))
        Icon(Icons.Default.SkipPrevious, "Anterior", tint = label, modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp).size(13.dp))
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp)) {
            Icon(Icons.Default.PlayArrow, null, tint = label, modifier = Modifier.size(9.dp))
            Icon(Icons.Default.Pause, "Play/pausa", tint = label, modifier = Modifier.size(9.dp))
        }
        Box(
            Modifier.size(40.dp)
                .graphicsLayer(scaleX = centerScale, scaleY = centerScale)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFFF2F3F5), Color(0xFFE2E5E9))))
                .border(0.8.dp, Color.Black.copy(alpha = 0.10f), CircleShape),
        )
    }
}
