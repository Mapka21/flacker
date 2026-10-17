package com.marco.audiopro.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.StructuredLyrics
import com.marco.audiopro.platform.ImmersiveSystem
import com.marco.audiopro.ui.components.AmbientBackground
import com.marco.audiopro.ui.components.CoverArt
import com.marco.audiopro.ui.components.PlayPauseIcon
import com.marco.audiopro.ui.components.bouncyClick
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.viz.LiveAudio
import com.marco.audiopro.ui.viz.VisualPreset
import kotlinx.coroutines.delay

/** Modo inmersivo (pantalla completa): lo prende cualquier pantalla; la ventana / el celular reaccionan. */
object Immersive {
    var on by mutableStateOf(false)
}

/** Los efectos que se van mezclando (todos los que tienen figuras). */
private val MixPool = listOf(
    VisualPreset.NEXO, VisualPreset.NEON, VisualPreset.HIPER, VisualPreset.HALO,
    VisualPreset.PULSO, VisualPreset.OLAS, VisualPreset.SYNTH, VisualPreset.ASCII,
)

/**
 * Pantalla completa como el visualizador del Xbox 360: dos efectos a la vez, el de encima mezclado
 * "con luz" (screen) sobre el de abajo, y cada ~20 s (o antes, en un golpe fuerte) uno se cambia
 * por otro con un fundido. Encima, la letra en grande (o la canción, si no hay letra sincronizada) y
 * los controles, que aparecen al mover el mouse o tocar y se esconden solos.
 */
@Composable
fun ImmersiveScreen(onExit: () -> Unit) {
    val c = LocalApp.current
    val st by c.player.state.collectAsState()
    val song = st.current
    ImmersiveSystem(true, onExit)

    var base by remember { mutableStateOf(MixPool.random()) }
    var top by remember { mutableStateOf(MixPool.filter { it != base }.random()) }
    LaunchedEffect(Unit) {
        var since = 0L
        var changeTop = true
        while (true) {
            delay(250); since += 250
            val strongBeat = LiveAudio.features.beat > 0.9f && since > 6_000
            if (since > 12_000 || strongBeat) {
                val next = MixPool.filter { it != base && it != top }.random()
                if (changeTop) top = next else base = next
                changeTop = !changeTop
                since = 0
            }
        }
    }

    var controls by remember { mutableStateOf(true) }
    var lastMove by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastMove) { delay(3000); controls = false }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type == PointerEventType.Move || e.type == PointerEventType.Press) { controls = true; lastMove = System.currentTimeMillis() }
                    }
                }
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onExit() }) },
    ) {
        // "Cámara": con cada golpe todo da un brinquito de zoom y un leve giro.
        var beat by remember { mutableFloatStateOf(0f) }
        var clock by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(Unit) { val t0 = System.nanoTime(); while (true) withFrameNanos { beat = LiveAudio.features.beat; clock = (it - t0) / 1e9f } }
        val swap = { (fadeIn(tween(1100)) + scaleIn(tween(1400), initialScale = 1.4f)) togetherWith (fadeOut(tween(1100)) + scaleOut(tween(1400), targetScale = 0.7f)) }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val z = 1.04f + 0.06f * beat
                scaleX = z; scaleY = z
                rotationZ = kotlin.math.sin(clock * 0.35f) * 1.2f + (if (beat > 0.5f) kotlin.math.sin(clock * 40f) * 0.8f * beat else 0f)
            },
        ) {
            // Capa de abajo y capa de encima mezclada con luz (lo negro no tapa, lo brillante se suma).
            AnimatedContent(base, transitionSpec = { swap() }, label = "base") { p -> AmbientBackground(song?.coverArt, preset = p) }
            Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; blendMode = BlendMode.Screen; alpha = 0.9f }) {
                AnimatedContent(top, transitionSpec = { swap() }, label = "top") { p -> AmbientBackground(song?.coverArt, preset = p, figuresOnly = true) }
            }
        }
        // Destello en los golpes.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = (beat * 0.14f).coerceIn(0f, 1f) }.background(Color.White))
        // Viñeta para que se lea la letra.
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))

        if (song != null) ImmersiveLyrics(song, Modifier.align(Alignment.Center))

        AnimatedVisibility(controls, Modifier.align(Alignment.BottomStart), enter = fadeIn(), exit = fadeOut()) {
            if (song != null) ImmersiveBar(song, st.isPlaying)
        }
        AnimatedVisibility(controls, Modifier.align(Alignment.TopEnd), enter = fadeIn(), exit = fadeOut()) {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Esc o doble clic para salir", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).bouncyClick(onClick = onExit),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Close, "Salir", tint = Color.White) }
            }
        }
    }
}

/** Portada, canción y controles abajo a la izquierda. */
@Composable
private fun ImmersiveBar(song: Song, playing: Boolean) {
    val player = LocalApp.current.player
    Row(
        Modifier.padding(24.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(song.coverArt, Modifier.size(56.dp).shadow(10.dp, RoundedCornerShape(10.dp)), size = 200, corner = 10.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.widthIn(max = 280.dp)) {
            Text(song.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
            Text(song.artist ?: "", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = 0.7f))
        }
        Spacer(Modifier.width(16.dp))
        Icon(Icons.Default.SkipPrevious, "Anterior", tint = Color.White, modifier = Modifier.size(32.dp).bouncyClick { player.previous() })
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White).bouncyClick { player.toggle() }, contentAlignment = Alignment.Center) {
            PlayPauseIcon(playing, Color.Black, 28.dp)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.SkipNext, "Siguiente", tint = Color.White, modifier = Modifier.size(32.dp).bouncyClick { player.next() })
    }
}

/**
 * La letra como video musical (tipografía cinética): cada verso entra palabra por palabra al ritmo
 * de la canción (las que faltan se ven apagadas, como karaoke) y cada verso trae su propio estilo:
 * al centro, inclinado a la izquierda o a la derecha, o una palabra por renglón en grande. La palabra
 * que va entrando late con el bajo. Sin letra sincronizada: el título y el artista.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ImmersiveLyrics(song: Song, modifier: Modifier) {
    val c = LocalApp.current
    val lyrics by produceState<StructuredLyrics?>(null, song.id) { value = runCatching { c.lyrics.get(song) }.getOrNull() }
    val raw by rememberPlaybackPosition(40)
    val offsets by c.lyrics.offsets.collectAsState()
    val pos = raw + (offsets[song.id] ?: 0L)
    var beat by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { beat = LiveAudio.features.beat } }

    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 40.dp), contentAlignment = Alignment.Center) {
        val wide = maxWidth > 700.dp
        val big = if (wide) 64.sp else 34.sp
        val small = if (wide) 22.sp else 15.sp
        val glow = Shadow(Color.White.copy(alpha = 0.6f), Offset.Zero, 30f)
        val l = lyrics
        val lines = l?.line.orEmpty()
        if (l == null || !l.synced || lines.isEmpty()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { val s = 1f + 0.04f * beat; scaleX = s; scaleY = s }) {
                Text(song.title, style = Display.copy(fontSize = big, color = Color.White, shadow = glow), textAlign = TextAlign.Center)
                Text(song.artist ?: "", fontSize = small, color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center)
            }
            return@BoxWithConstraints
        }
        val i = lines.indexOfLast { (it.start ?: 0) <= pos }
        val start = lines.getOrNull(i)?.start ?: 0L
        val end = lines.getOrNull(i + 1)?.start ?: (start + 4000)
        val progress = if (i < 0) 0f else ((pos - start).toFloat() / (end - start).coerceAtLeast(400)).coerceIn(0f, 1f)
        AnimatedContent(
            i,
            transitionSpec = {
                (fadeIn(tween(300)) + scaleIn(tween(450), initialScale = 0.85f)) togetherWith (fadeOut(tween(250)) + slideOutVertically(tween(300)) { -it / 4 })
            },
            label = "verse",
        ) { k ->
            val text = lines.getOrNull(k)?.value?.takeIf { it.isNotBlank() } ?: "♪"
            val words = text.split(" ").filter { it.isNotEmpty() }
            // Estilo por verso (fijo para ese verso, cambia de uno a otro).
            val style = Math.floorMod((k * 2654435761L).toInt(), 4)
            val (align, tilt) = when (style) { 1 -> Alignment.Start to -3.5f; 2 -> Alignment.End to 3f; else -> Alignment.CenterHorizontally to 0f }
            val p = if (k == i) progress else 1f
            Column(
                Modifier.fillMaxWidth().graphicsLayer { rotationZ = tilt },
                horizontalAlignment = when (style) { 1 -> Alignment.Start; 2 -> Alignment.End; else -> Alignment.CenterHorizontally },
            ) {
                Text(
                    lines.getOrNull(k - 1)?.value.orEmpty(), fontSize = small, color = Color.White.copy(alpha = 0.3f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 12.dp),
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(if (style == 3) 0.dp else 14.dp, align),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    maxItemsInEachRow = if (style == 3) 1 else Int.MAX_VALUE,
                ) {
                    val n = words.size
                    words.forEachIndexed { w, word ->
                        // Cada palabra entra en su parte del verso; la que va entrando late con el bajo.
                        val a = ((p - w.toFloat() / n * 0.9f) * n * 1.6f).coerceIn(0f, 1f)
                        val live = a in 0.01f..0.99f || (a >= 0.99f && w == (p * n * 0.9f).toInt().coerceAtMost(n - 1))
                        val size = if (style == 3) big * 1.15f else big
                        Text(
                            word,
                            style = Display.copy(fontSize = size, color = Color.White, shadow = if (a > 0.5f) glow else null, lineHeight = size * 1.08f),
                            modifier = Modifier.graphicsLayer {
                                alpha = 0.18f + 0.82f * a
                                val s = (1.25f - 0.25f * a) * (if (live) 1f + 0.06f * beat else 1f)
                                scaleX = s; scaleY = s
                                translationY = (1f - a) * 24f
                            },
                        )
                    }
                }
                Text(
                    lines.getOrNull(k + 1)?.value.orEmpty(), fontSize = small, color = Color.White.copy(alpha = 0.4f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
    }
}
