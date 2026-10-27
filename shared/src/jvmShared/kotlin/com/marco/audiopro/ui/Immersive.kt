package com.marco.audiopro.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.BiasAlignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
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
import com.marco.audiopro.ui.theme.Fonts
import com.marco.audiopro.ui.viz.LiveAudio
import com.marco.audiopro.ui.viz.VisualPreset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Modo inmersivo (pantalla completa): lo prende cualquier pantalla; la ventana / el celular reaccionan. */
object Immersive {
    var on by mutableStateOf(false)
    /** Qué se ve encima de los efectos: la letra, nada (sólo visual) o la portada en grande. */
    var mode by mutableStateOf(ImmersiveMode.LETRA)
    /** Fondo fijo elegido (null = se van mezclando solos). */
    var pinned by mutableStateOf<VisualPreset?>(null)
    fun cycleMode() { mode = ImmersiveMode.entries[(mode.ordinal + 1) % ImmersiveMode.entries.size] }
    /** Pedir un cambio de efecto ya (A en el control, el coro). */
    var swapNow by mutableStateOf(false)
    /** Aviso corto arriba ("Letra +0.2 s", "Velocidad ×2"). */
    var toast by mutableStateOf<String?>(null)
    /** Lo pone la pantalla completa: mueve la letra de la canción actual [deltaMs]. */
    var nudgeLyrics: (Long) -> Unit = {}
}

enum class ImmersiveMode(val label: String) { LETRA("Letra"), VISUAL("Solo visual"), PORTADA("Portada") }

/** Fondos completos (abajo): los de shader y los que llenan la pantalla. */
private val BasePool = listOf(
    VisualPreset.N360, VisualPreset.N360, VisualPreset.N360,
    VisualPreset.CALEIDO, VisualPreset.PLASMA, VisualPreset.TUNEL, VisualPreset.FRACTAL, VisualPreset.ESTALLIDO,
    VisualPreset.NEXO, VisualPreset.NEON, VisualPreset.XBOX, VisualPreset.SYNTH, VisualPreset.ASCII, VisualPreset.HIPER,
).filter { it in VisualPreset.available }.distinct().let { l -> l + List(2) { VisualPreset.N360 } }

/** Figuras que se suman encima con luz (null = el fondo solo, para que respire). */
private val TopPool: List<VisualPreset?> = listOf(
    VisualPreset.PULSO, VisualPreset.HALO, VisualPreset.OLAS, VisualPreset.HIPER, VisualPreset.NEON, VisualPreset.NEXO, null,
)

/** Cambios de efecto: atravesar, salir disparado hacia la cámara o disolverse lento. */
private fun swapSpec(n: Int): ContentTransform = when (n % 3) {
    0 -> (fadeIn(tween(1100)) + scaleIn(tween(1400), initialScale = 1.4f)) togetherWith (fadeOut(tween(1100)) + scaleOut(tween(1400), targetScale = 0.7f))
    1 -> (fadeIn(tween(250)) + scaleIn(tween(700), initialScale = 0.6f)) togetherWith (fadeOut(tween(650)) + scaleOut(tween(650), targetScale = 2.2f))
    else -> fadeIn(tween(2200)) togetherWith fadeOut(tween(2200))
}

/**
 * Pantalla completa como el visualizador del Xbox 360: un fondo completo (muchos calculados en la
 * tarjeta de video) y figuras encima mezcladas con luz; cada 9–16 s, en un golpe fuerte o cuando
 * entra el coro, uno se cambia por otro con un destello. Encima, la letra como video musical.
 */
@Composable
fun ImmersiveScreen(onExit: () -> Unit) {
    val c = LocalApp.current
    val st by c.player.state.collectAsState()
    val song = st.current
    ImmersiveSystem(true, onExit)

    // El modo y el fondo fijo se recuerdan entre sesiones.
    LaunchedEffect(Unit) {
        c.settings.getString("immersive_mode")?.let { m -> ImmersiveMode.entries.firstOrNull { it.name == m }?.let { Immersive.mode = it } }
        c.settings.getString("immersive_fx")?.let { p -> Immersive.pinned = VisualPreset.available.firstOrNull { it.name == p } }
    }
    LaunchedEffect(Immersive.mode, Immersive.pinned) {
        c.settings.putString("immersive_mode", Immersive.mode.name)
        c.settings.putString("immersive_fx", Immersive.pinned?.name ?: "")
    }
    // Arranca con los del Xbox 360 (la mezcla completa ya trae sus propias escenas).
    var base by remember { mutableStateOf(VisualPreset.N360) }
    LaunchedEffect(Immersive.pinned) { Immersive.pinned?.let { base = it } }
    var top by remember { mutableStateOf<VisualPreset?>(null) }
    var swaps by remember { mutableIntStateOf(0) }
    val flash = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        var since = 0L
        var every = 12_000L
        var changeTop = false
        while (true) {
            delay(100); since += 100
            val strongBeat = LiveAudio.features.beat > 0.9f && since > 6_000
            val swapNow = Immersive.swapNow
            if (since > every || strongBeat || (swapNow && since > 1_500)) {
                swaps++
                if (changeTop || Immersive.pinned != null) {
                    // Los del 360 ya son una escena completa: encima no va nada.
                    top = if (base == VisualPreset.N360) null else TopPool.filter { it != top && it != base }.random()
                } else {
                    base = BasePool.filter { it != base && it != top }.random()
                    if (base == VisualPreset.N360) top = null
                    if (base == VisualPreset.ASCII && top == VisualPreset.ASCII) top = null
                }
                changeTop = !changeTop
                launch { flash.snapTo(if (swapNow || strongBeat) 0.55f else 0.25f); flash.animateTo(0f, tween(700)) }
                Immersive.swapNow = false
                since = 0
                every = if (base == VisualPreset.N360) Random.nextLong(40_000, 70_000) else Random.nextLong(9_000, 16_000)
            }
        }
    }

    var controls by remember { mutableStateOf(true) }
    var lastMove by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastMove) { delay(3000); controls = false }

    // Reloj y golpe por frame: se leen sólo al dibujar (nada se vuelve a componer por esto).
    val beat = remember { mutableFloatStateOf(0f) }
    val bass = remember { mutableFloatStateOf(0f) }
    val clock = remember { mutableFloatStateOf(0f) }
    val animating = com.marco.audiopro.ui.components.rememberAnimating()
    LaunchedEffect(Unit) {
        val t0 = System.nanoTime()
        var last = 0L
        com.marco.audiopro.ui.components.animationFrames(animating) {
            beat.floatValue = LiveAudio.features.beat; bass.floatValue = LiveAudio.features.bass; clock.floatValue = (it - t0) / 1e9f
            if (last != 0L) com.marco.audiopro.ui.viz.VizControl.step(((it - last) / 1e9f).coerceAtMost(0.1f))
            last = it
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type == PointerEventType.Move || e.type == PointerEventType.Press) { controls = true; lastMove = System.currentTimeMillis() }
                        if (e.type == PointerEventType.Scroll) {
                            val vc = com.marco.audiopro.ui.viz.VizControl
                            vc.zoom = (vc.zoom - (e.changes.firstOrNull()?.scrollDelta?.y ?: 0f) * 0.06f).coerceIn(-0.4f, 0.8f)
                        }
                    }
                }
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onExit() }) },
    ) {
        // "Cámara": respira con el bajo, brinca en cada golpe y tiembla tantito cuando pega fuerte.
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val b = beat.floatValue; val lo = bass.floatValue; val t = clock.floatValue
                val vc = com.marco.audiopro.ui.viz.VizControl
                val z = (1.03f + 0.07f * lo + 0.10f * b) * (1f + 0.5f * vc.zoom)
                scaleX = z; scaleY = z
                rotationZ = vc.angle + sin(t * 0.35f) * 1.2f + (if (b > 0.4f) sin(t * 40f) * 1.1f * b else 0f)
                translationX = if (b > 0.4f) sin(t * 63f) * 9f * b * density else 0f
                translationY = if (b > 0.4f) sin(t * 71f + 1f) * 7f * b * density else 0f
            },
        ) {
            val n = swaps
            AnimatedContent(base, transitionSpec = { swapSpec(n) }, label = "base") { p -> AmbientBackground(song?.coverArt, preset = p) }
            // Encima, mezclado con luz: lo negro no tapa, lo brillante se suma.
            Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; blendMode = BlendMode.Screen; alpha = if (base.gpu) 0.75f else 0.9f }) {
                AnimatedContent(top, transitionSpec = { swapSpec(n + 1) }, label = "top") { p ->
                    if (p != null) AmbientBackground(song?.coverArt, preset = p, figuresOnly = true) else Box(Modifier.fillMaxSize())
                }
            }
        }
        // Destello en los golpes y en cada cambio de efecto, y un resplandor al centro que late con el bajo.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = (beat.floatValue * 0.10f + flash.value).coerceIn(0f, 1f) }.background(Color.White))
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = (0.10f * bass.floatValue + 0.22f * beat.floatValue).coerceIn(0f, 1f); scaleX = 0.8f + 0.5f * bass.floatValue; scaleY = scaleX }
                .background(Brush.radialGradient(listOf(Color.White, Color.Transparent))),
        )
        // Viñeta para que se lea la letra.
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))

        if (song != null) AnimatedContent(Immersive.mode, transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) }, label = "mode") { m ->
            when (m) {
                ImmersiveMode.LETRA -> ImmersiveLyrics(song, beat, bass, clock, onChorus = { Immersive.swapNow = true }, Modifier.fillMaxSize())
                ImmersiveMode.PORTADA -> CoverStage(song, st.durationMs, beat, bass, clock)
                ImmersiveMode.VISUAL -> NowPlayingToast(song)
            }
        }

        val toast = Immersive.toast
        LaunchedEffect(toast) { if (toast != null) { delay(1400); Immersive.toast = null } }
        AnimatedVisibility(toast != null, Modifier.align(Alignment.TopCenter).padding(top = 28.dp), enter = fadeIn(), exit = fadeOut()) {
            Text(
                toast ?: "", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 18.dp, vertical = 9.dp),
            )
        }
        AnimatedVisibility(controls && com.marco.audiopro.ui.viz.VizControl.pad, Modifier.align(Alignment.BottomEnd), enter = fadeIn(), exit = fadeOut()) {
            Text(
                "CONTROL  ·  cruceta: escenas del 360  ·  stick izq: girar y acercar  ·  stick der: mover y doblar el túnel  ·  gatillos: velocidad  ·  X: forma  ·  A: otro efecto  ·  Y: modo  ·  B: salir",
                fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.padding(24.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        AnimatedVisibility(controls, Modifier.align(Alignment.BottomStart), enter = fadeIn(), exit = fadeOut()) {
            if (song != null) ImmersiveBar(song, st.isPlaying)
        }
        AnimatedVisibility(controls, Modifier.align(Alignment.TopEnd), enter = fadeIn(), exit = fadeOut()) {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                ImmersiveMode.entries.forEach { m ->
                    val sel = Immersive.mode == m
                    Text(
                        m.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (sel) Color.Black else Color.White,
                        modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(50)).background(if (sel) Color.White else Color.Black.copy(alpha = 0.45f))
                            .bouncyClick { Immersive.mode = m }.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                FxPicker(base)
                Spacer(Modifier.width(6.dp))
                Box(Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))) { com.marco.audiopro.ui.components.DevicesButton(tint = Color.White) }
                Spacer(Modifier.width(12.dp))
                Text(if (c.isDesktop) "L cambia el modo · Esc sale" else "Doble toque para salir", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
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
 * Posición de la canción para cada frame. El reproductor la reporta a brincos (por bloques de
 * audio), así que aquí se avanza con el reloj de la pantalla y se corrige poquito a poco hacia la
 * real; si brinca mucho (adelantar, otra canción) se iguala de golpe.
 */
@Composable
private fun rememberSmoothPosition(): State<Long> {
    val player = LocalApp.current.player
    val pos = remember { mutableLongStateOf(player.positionMs()) }
    val animating = com.marco.audiopro.ui.components.rememberAnimating()
    LaunchedEffect(player) {
        var est = player.positionMs().toDouble()
        var last = 0L
        com.marco.audiopro.ui.components.animationFrames(animating) { t ->
            val dt = if (last == 0L) 0.0 else (t - last) / 1e6
            last = t
            val raw = player.positionMs()
            if (player.state.value.isPlaying) {
                est += dt
                val err = raw - est
                if (abs(err) > 350) est = raw.toDouble() else est += err * 0.06
            } else est = raw.toDouble()
            pos.longValue = est.toLong()
        }
    }
    return pos
}

/** Cómo entra cada verso. */
private enum class Kin { POP, LEAN_L, LEAN_R, STACK, DROP, ZOOM, TYPE, OUTLINE, SCATTER, SLAM, WAVE, SPOT }

/** Un verso ya preparado: sus palabras, cuándo entra cada una y su estilo. */
private class Verse(val text: String, val start: Long, end: Long, val kin: Kin, val chorus: Boolean, wordStarts: List<Long>? = null) {
    val words = text.split(" ").filter { it.isNotEmpty() }
    private val len = text.length
    /** Cuánto tarda en salir todo el verso (se canta al principio; luego suele haber pausa). */
    val reveal: Long = min((end - start).coerceAtLeast(400) * 0.92, 450.0 + len * 70.0).toLong()
    val holdUntil: Long = (end - start)
    private val t0 = LongArray(words.size)
    private val t1 = LongArray(words.size)

    init {
        if (wordStarts != null && wordStarts.size == words.size) {
            // La letra trae el tiempo exacto de cada palabra: se usa tal cual.
            for (w in words.indices) {
                t0[w] = wordStarts[w] - start
                t1[w] = (wordStarts.getOrNull(w + 1)?.minus(start) ?: (t0[w] + 450)).coerceAtLeast(t0[w] + 120) + 60
            }
        } else initGuess()
    }

    private fun initGuess() {
        // Las palabras largas tardan más en cantarse: el tiempo se reparte por letras.
        val weights = words.map { it.length + 1.5 }
        val total = weights.sum().coerceAtLeast(1.0)
        var acc = 0.0
        for (w in words.indices) {
            t0[w] = (reveal * acc / total).toLong()
            acc += weights[w]
            t1[w] = (reveal * acc / total).toLong() + 140
        }
    }

    /** 0..1 de cada palabra a [elapsed] ms del inicio del verso. */
    fun word(w: Int, elapsed: Long): Float = ((elapsed - t0[w]).toFloat() / (t1[w] - t0[w]).coerceAtLeast(1)).coerceIn(0f, 1f)
    fun live(w: Int, elapsed: Long) = elapsed in t0[w]..t1[w]
    /** La palabra que se está cantando (la última que ya empezó). */
    fun current(elapsed: Long): Int = (words.indices.lastOrNull { t0[it] <= elapsed } ?: 0)
}

private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

/** Estilo de cada verso: nunca el mismo dos veces seguidas; el coro (versos que se repiten) pega más. */
private fun planVerses(song: Song, lines: List<com.marco.audiopro.data.subsonic.Lyric>): List<Verse> {
    val rnd = Random(song.id.hashCode())
    val counts = lines.groupingBy { norm(it.value) }.eachCount()
    val recent = ArrayDeque<Kin>()
    return lines.mapIndexed { k, l ->
        val text = l.value.trim()
        val n = text.split(" ").count { it.isNotEmpty() }
        val chorus = text.length > 8 && (counts[norm(text)] ?: 0) >= 2
        val options = (if (chorus) listOf(Kin.OUTLINE, Kin.ZOOM, Kin.SLAM, Kin.STACK, Kin.SPOT, Kin.POP) else Kin.entries).filter { kin ->
            kin !in recent && when (kin) {
                Kin.STACK -> n in 1..5
                Kin.ZOOM -> n <= 7
                Kin.SCATTER -> n in 2..8
                Kin.SLAM -> n <= 8
                Kin.SPOT -> n in 1..10
                else -> true
            }
        }.ifEmpty { listOf(Kin.POP) }
        val kin = options[rnd.nextInt(options.size)]
        recent.addLast(kin); if (recent.size > 2) recent.removeFirst()
        val start = l.start ?: 0L
        Verse(text, start, lines.getOrNull(k + 1)?.start ?: (start + 5000), kin, chorus, l.words)
    }
}

/**
 * La letra como video musical (tipografía cinética). Cada verso trae su manera de entrar: palabra
 * por palabra desde grande, inclinado desde un lado, una palabra por renglón, cayendo, desde la
 * cámara, a máquina, delineado que se rellena (karaoke), regado o con glitch. La palabra que se está
 * cantando late con el bajo. Todo se mueve en la fase de dibujo con una posición suavizada, así que
 * va a los fps de la pantalla. Sin letra sincronizada: el título y el artista.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImmersiveLyrics(song: Song, beat: State<Float>, bass: State<Float>, clock: State<Float>, onChorus: () -> Unit, modifier: Modifier) {
    val c = LocalApp.current
    val lyrics by produceState<StructuredLyrics?>(null, song.id) { value = runCatching { c.lyrics.get(song) }.getOrNull() }
    val pos = rememberSmoothPosition()
    val offsets by c.lyrics.offsets.collectAsState()
    val prefs by c.settings.prefs.collectAsState()
    // El mismo retraso que los visuales (bocinas Bluetooth, etc.) y el ajuste de esta canción.
    val offset = (offsets[song.id] ?: 0L) - prefs.visualOffsetMs
    DisposableEffect(song.id) {
        Immersive.nudgeLyrics = { d ->
            val now = (c.lyrics.offsets.value[song.id] ?: 0L) + d
            c.lyrics.setOffset(song.id, now)
            Immersive.toast = "Letra " + (if (now >= 0) "+" else "") + "%.1f s".format(now / 1000f)
        }
        onDispose { Immersive.nudgeLyrics = {} }
    }

    BoxWithConstraints(modifier.padding(horizontal = 48.dp, vertical = 40.dp), contentAlignment = Alignment.Center) {
        val wide = maxWidth > 700.dp
        val big = if (wide) 74.sp else 36.sp
        val small = if (wide) 22.sp else 15.sp
        val l = lyrics
        val lines = l?.line.orEmpty()
        if (l == null || !l.synced || lines.isEmpty()) {
            TitleCard(song, big, small, beat, clock)
            return@BoxWithConstraints
        }
        val verses = remember(lines) { planVerses(song, lines) }
        val index by remember(verses, offset) { derivedStateOf { val p = pos.value + offset; verses.indexOfLast { it.start <= p } } }
        LaunchedEffect(index) { if (verses.getOrNull(index)?.chorus == true && verses.getOrNull(index - 1)?.chorus != true) onChorus() }

        AnimatedContent(
            index,
            transitionSpec = {
                val leaving = verses.getOrNull(initialState)?.kin
                fadeIn(tween(160)) togetherWith when (leaving) {
                    Kin.ZOOM, Kin.STACK -> fadeOut(tween(260)) + scaleOut(tween(320), targetScale = 2.4f)
                    Kin.DROP -> fadeOut(tween(260)) + slideOutVertically(tween(320)) { it / 3 }
                    Kin.LEAN_L -> fadeOut(tween(260)) + slideOutHorizontally(tween(320)) { -it / 3 }
                    Kin.LEAN_R -> fadeOut(tween(260)) + slideOutHorizontally(tween(320)) { it / 3 }
                    Kin.SLAM -> fadeOut(tween(200)) + scaleOut(tween(260), targetScale = 1.7f)
                    Kin.SPOT -> fadeOut(tween(140))
                    else -> fadeOut(tween(240)) + slideOutVertically(tween(300)) { -it / 4 }
                }
            },
            label = "verse",
        ) { k ->
            val v = verses.getOrNull(k)
            when {
                k < 0 || v == null -> TitleCard(song, big, small, beat, clock)
                v.words.isEmpty() || v.text.all { !it.isLetterOrDigit() } -> Dots(beat, clock)
                else -> {
                    // Mientras es el verso actual avanza con la canción; al irse queda completo.
                    val elapsed = { if (index == k) pos.value + offset - v.start else Long.MAX_VALUE / 4 }
                    if (v.kin == Kin.SPOT) SpotView(v, elapsed, beat, bass, big, small)
                    else VerseView(v, elapsed, beat, bass, clock, big, small, prev = verses.getOrNull(k - 1)?.text, next = verses.getOrNull(k + 1)?.text)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VerseView(v: Verse, elapsed: () -> Long, beat: State<Float>, bass: State<Float>, clock: State<Float>, big: TextUnit, small: TextUnit, prev: String?, next: String?) {
    val kin = v.kin
    val fit = when { v.text.length > 75 -> 0.62f; v.text.length > 45 -> 0.8f; else -> 1f }
    val mult = when (kin) { Kin.STACK -> 1.3f; Kin.ZOOM -> 1.12f; Kin.OUTLINE -> 1.15f; Kin.SLAM -> 1.18f; Kin.TYPE -> 0.78f; else -> 1f } * (if (v.chorus) 1.1f else 1f)
    val size = big * (fit * mult)
    val upper = kin == Kin.STACK || kin == Kin.ZOOM || kin == Kin.SLAM
    val style = when (kin) {
        Kin.TYPE -> TextStyle(fontFamily = Fonts.mono, fontWeight = FontWeight.Bold, fontSize = size, color = Color.White, lineHeight = size * 1.15f)
        Kin.ZOOM -> Display.copy(fontSize = size, color = Color.White, letterSpacing = 3.sp, lineHeight = size * 1.05f)
        else -> Display.copy(fontSize = size, color = Color.White, lineHeight = size * 1.06f)
    }
    val (h, bias, tilt) = when (kin) {
        Kin.LEAN_L -> Triple(Alignment.Start, -0.35f, -3.5f)
        Kin.LEAN_R -> Triple(Alignment.End, 0.3f, 3f)
        Kin.TYPE -> Triple(Alignment.Start, 0f, 0f)
        Kin.DROP -> Triple(Alignment.CenterHorizontally, -0.15f, 0f)
        else -> Triple(Alignment.CenterHorizontally, 0f, 0f)
    }
    val showContext = kin == Kin.POP || kin == Kin.LEAN_L || kin == Kin.LEAN_R || kin == Kin.TYPE || kin == Kin.DROP
    // El verso largo que ya se cantó se desvanece si falta mucho para el siguiente (que luzcan los efectos).
    val gapFade = { val e = elapsed(); if (v.holdUntil - e < 2500) 1f else (1f - (e - v.reveal - 4500) / 900f).coerceIn(0f, 1f) }
    // La palabra más larga del verso regado va más grande.
    val longest = v.words.indices.maxByOrNull { v.words[it].length } ?: 0

    Box(Modifier.fillMaxSize(), contentAlignment = BiasAlignment(0f, bias)) {
        Column(
            Modifier.fillMaxWidth().graphicsLayer {
                rotationZ = tilt; alpha = gapFade(); compositingStrategy = CompositingStrategy.ModulateAlpha
                val e = elapsed()
                val life = (e.toFloat() / v.holdUntil.coerceAtLeast(1)).coerceIn(0f, 1f)
                val sc = (1f + 0.05f * life) * (1f + 0.035f * bass.value)
                scaleX = sc; scaleY = sc
                if (kin == Kin.SLAM) {
                    // Impacto: justo cuando cae una palabra, todo el verso se sacude.
                    var imp = 0f
                    for (w in v.words.indices) { val a = v.word(w, e); if (a in 0.6f..0.999f) imp = maxOf(imp, 1f - (a - 0.6f) / 0.4f) }
                    translationX = sin(clock.value * 90f) * 12f * imp * density
                    translationY = sin(clock.value * 77f + 2f) * 9f * imp * density
                }
            },
            horizontalAlignment = h,
        ) {
            if (showContext && !prev.isNullOrBlank()) Text(
                prev, fontSize = small, color = Color.White.copy(alpha = 0.3f),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 12.dp),
            )
            FlowRow(
                // Espacio entre palabras proporcional a la letra (con 16 dp fijos, a 70 sp se veían pegadas).
                horizontalArrangement = Arrangement.spacedBy((size.value * when (kin) { Kin.STACK -> 0f; Kin.TYPE -> 0.5f; Kin.SCATTER -> 0.45f; Kin.ZOOM, Kin.SLAM -> 0.38f; else -> 0.3f }).dp, h),
                verticalArrangement = Arrangement.spacedBy(if (kin == Kin.SCATTER) 10.dp else 2.dp),
                maxItemsInEachRow = if (kin == Kin.STACK) 1 else Int.MAX_VALUE,
            ) {
                v.words.forEachIndexed { w, word ->
                    Word(
                        if (upper) word.uppercase() else word, kin,
                        // La más larga del verso regado va más grande (de tamaño real, para que no se encime).
                        if (kin == Kin.SCATTER && w == longest) style.copy(fontSize = size * 1.35f, lineHeight = size * 1.4f) else style,
                        seed = v.start.toInt() * 31 + w,
                        a = { v.word(w, elapsed()) }, live = { v.live(w, elapsed()) }, beat = beat, bass = bass, clock = clock,
                    )
                }
            }
            if (showContext && !next.isNullOrBlank()) Text(
                next, fontSize = small, color = Color.White.copy(alpha = 0.4f),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

private val Glow = Shadow(Color.White.copy(alpha = 0.55f), Offset.Zero, 28f)

private fun outCubic(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)
private fun outBack(t: Float): Float { val c = 1.9f; val x = t - 1f; return 1f + (c + 1f) * x * x * x + c * x * x }
private fun outBounce(t: Float): Float = when {
    t < 1 / 2.75f -> 7.5625f * t * t
    t < 2 / 2.75f -> { val x = t - 1.5f / 2.75f; 7.5625f * x * x + 0.75f }
    t < 2.5f / 2.75f -> { val x = t - 2.25f / 2.75f; 7.5625f * x * x + 0.9375f }
    else -> { val x = t - 2.625f / 2.75f; 7.5625f * x * x + 0.984375f }
}

/** Una palabra: capa apagada (o delineada) y capa encendida, animadas sólo al dibujar. */
@Composable
private fun Word(
    text: String, kin: Kin, style: TextStyle, seed: Int,
    a: () -> Float, live: () -> Boolean, beat: State<Float>, bass: State<Float>, clock: State<Float>,
) {
    val r = remember(seed) { Random(seed) }
    val jx = remember(seed) { r.nextFloat() * 2 - 1 }
    val jy = remember(seed) { r.nextFloat() * 2 - 1 }
    val jr = remember(seed) { r.nextFloat() * 2 - 1 }
    val lit = style.copy(shadow = Glow)
    Box(Modifier.graphicsLayer { move(kin, a(), live(), beat.value, bass.value, clock.value, jx, jy, jr) }) {
        when (kin) {
            Kin.OUTLINE -> Text(text, style = style.copy(drawStyle = Stroke(width = 2.2f)), modifier = Modifier.graphicsLayer { alpha = 0.8f; compositingStrategy = CompositingStrategy.ModulateAlpha })
            Kin.POP, Kin.LEAN_L, Kin.LEAN_R, Kin.WAVE -> Text(text, style = style, modifier = Modifier.graphicsLayer { alpha = 0.16f; compositingStrategy = CompositingStrategy.ModulateAlpha })
            else -> {}
        }
        val wipe = kin == Kin.OUTLINE || kin == Kin.TYPE
        Text(
            text, style = lit,
            modifier = Modifier
                .graphicsLayer { alpha = if (wipe) 1f else litAlpha(kin, a()); compositingStrategy = CompositingStrategy.ModulateAlpha }
                .then(if (!wipe) Modifier else Modifier.drawWithContent {
                    var f = a()
                    if (kin == Kin.TYPE) f = floor(f * text.length) / text.length.coerceAtLeast(1)
                    val pad = size.height
                    // Completa: sin recorte (si no, se corta el brillo de la orilla).
                    if (f >= 1f) this@drawWithContent.drawContent()
                    else if (f > 0f) clipRect(-pad, -pad, size.width * f, size.height + pad) { this@drawWithContent.drawContent() }
                    // Cursor de máquina de escribir mientras se "teclea" la palabra.
                    if (kin == Kin.TYPE && f < 1f && a() > 0f) drawRect(
                        Color.White.copy(alpha = if ((clock.value * 4f).toInt() % 2 == 0) 0.9f else 0.25f),
                        topLeft = Offset(size.width * f + 2f, size.height * 0.12f), size = Size(size.height * 0.42f, size.height * 0.76f),
                    )
                }),
        )
    }
}

private fun litAlpha(kin: Kin, a: Float) = when (kin) {
    Kin.ZOOM, Kin.DROP, Kin.SCATTER, Kin.STACK, Kin.SLAM -> (a * 1.6f).coerceAtMost(1f)
    else -> a
}

/** Movimiento de entrada de cada estilo; [a] = 0 todavía no se canta, 1 ya está en su lugar. */
private fun GraphicsLayerScope.move(kin: Kin, a: Float, live: Boolean, beat: Float, bass: Float, t: Float, jx: Float, jy: Float, jr: Float) {
    val pulse = if (live) 1f + 0.12f * beat + 0.05f * bass else 1f
    val o = outCubic(a)
    val d = density
    var s = pulse
    when (kin) {
        Kin.POP -> { s *= 1.3f - 0.3f * o; translationY = (1f - o) * 22f * d }
        Kin.LEAN_L -> { translationX = (1f - o) * 46f * d; rotationZ = (1f - o) * 6f }
        Kin.LEAN_R -> { translationY = (1f - o) * 40f * d; rotationZ = (1f - o) * -6f }
        Kin.STACK -> { s *= 0.35f + 0.65f * outBack(a) }
        Kin.DROP -> { translationY = (1f - outBounce(a)) * -110f * d; rotationZ = (1f - o) * jr * 28f }
        Kin.ZOOM -> { s *= 3.4f - 2.4f * o }
        Kin.TYPE -> {}
        Kin.OUTLINE -> { s *= 1.06f - 0.06f * o }
        Kin.SCATTER -> {
            s *= 0.6f + 0.4f * outBack(a)
            translationX = jx * 120f * (1f - o) * d
            translationY = (jy * 90f * (1f - o) + jy * 10f) * d
            rotationZ = jr * 7f + (1f - o) * jr * 40f
        }
        Kin.SLAM -> { s *= 2.8f - 1.8f * outBack(a); rotationZ = (1f - o) * jr * 12f }
        Kin.WAVE -> {
            // Las palabras flotan en una ola que crece con el bajo.
            translationY = (sin(t * 2.6f + jx * 6f) * (5f + 22f * bass) + (1f - o) * 30f) * d
            rotationZ = sin(t * 2.6f + jx * 6f + 1.2f) * (1.5f + 4f * bass)
        }
        Kin.SPOT -> {}
    }
    scaleX = s; scaleY = s
}

/** Sin letra todavía (intro) o sin letra sincronizada: la canción como título de video. */
@Composable
private fun TitleCard(song: Song, big: TextUnit, small: TextUnit, beat: State<Float>, clock: State<Float>) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer { val s = 1f + 0.03f * sin(clock.value * 0.4f) + 0.04f * beat.value; scaleX = s; scaleY = s },
        ) {
            Text(song.title, style = Display.copy(fontSize = big, color = Color.White, shadow = Glow), textAlign = TextAlign.Center)
            Spacer(Modifier.size(10.dp))
            Text((song.artist ?: "").uppercase(), fontSize = small, letterSpacing = 6.sp, color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center)
        }
    }
}

/** Parte instrumental: tres puntos que laten con el bajo, uno tras otro. */
@Composable
private fun Dots(beat: State<Float>, clock: State<Float>) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            repeat(3) { i ->
                Box(
                    Modifier.size(18.dp).graphicsLayer {
                        val w = 0.5f + 0.5f * sin(clock.value * 3f - i * 0.9f)
                        val s = 0.7f + 0.4f * w + 0.35f * beat.value
                        scaleX = s; scaleY = s; alpha = 0.35f + 0.65f * w
                    }.clip(CircleShape).background(Color.White),
                )
            }
        }
    }
}

/** Para revisar los estilos (capturas): un verso de cada uno, a medio salir ([progress] 0..1). */
@Composable
fun LyricStylesPreview(progress: Float) {
    val beat = remember { mutableFloatStateOf(0.6f) }
    val clock = remember { mutableFloatStateOf(1f) }
    Column(Modifier.fillMaxSize().background(Color(0xFF1A1030)).padding(16.dp)) {
        Kin.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                row.forEach { kin ->
                    val v = remember(kin) { Verse("de veinte hercios ${kin.name.lowercase()}", 0, 3000, kin, chorus = false) }
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        if (kin == Kin.SPOT) SpotView(v, { (v.reveal * progress).toLong() }, beat, beat, 30.sp, 12.sp)
                        else VerseView(v, { (v.reveal * progress).toLong() }, beat, beat, clock, 30.sp, 12.sp, prev = "verso anterior", next = "verso siguiente")
                    }
                }
            }
        }
    }
}

/**
 * Una palabra a la vez, gigante al centro (como los subtítulos de los videos): cada palabra entra de
 * golpe y late con el bajo; abajo, el verso completo chiquito para no perderse.
 */
@Composable
private fun SpotView(v: Verse, elapsed: () -> Long, beat: State<Float>, bass: State<Float>, big: TextUnit, small: TextUnit) {
    val cur by remember(v) { derivedStateOf { v.current(elapsed()) } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedContent(
            cur,
            transitionSpec = { (scaleIn(tween(230), initialScale = 2.3f) + fadeIn(tween(110))) togetherWith (scaleOut(tween(170), targetScale = 0.55f) + fadeOut(tween(150))) },
            label = "spot",
        ) { w ->
            val word = v.words.getOrNull(w).orEmpty().uppercase()
            val size = big * (if (word.length > 9) 1.45f else 1.95f)
            Text(
                word, style = Display.copy(fontSize = size, color = Color.White, shadow = Glow, letterSpacing = 2.sp, lineHeight = size),
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { val sc = 1f + 0.10f * beat.value + 0.06f * bass.value; scaleX = sc; scaleY = sc },
            )
        }
        Text(
            v.text, fontSize = small, color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp),
        )
    }
}

/** Modo "Portada": la portada en grande flotando y latiendo con el bajo, con la canción y su avance. */
@Composable
private fun CoverStage(song: Song, durationMs: Long, beat: State<Float>, bass: State<Float>, clock: State<Float>) {
    val pos = rememberSmoothPosition()
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth * 0.5f, maxHeight * 0.52f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CoverArt(
                song.coverArt,
                Modifier.size(side).graphicsLayer {
                    val sc = 1f + 0.035f * bass.value + 0.05f * beat.value
                    scaleX = sc; scaleY = sc
                    rotationZ = sin(clock.value * 0.5f) * 1.6f
                    translationY = sin(clock.value * 0.8f) * 8f * density
                    shadowElevation = 40f * density; shape = RoundedCornerShape(18.dp); clip = true
                },
                size = 1000, corner = 18.dp,
            )
            Spacer(Modifier.size(28.dp))
            Text(song.title, style = Display.copy(fontSize = 40.sp, color = Color.White, shadow = Glow), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.size(6.dp))
            Text(
                listOfNotNull(song.artist, song.album).joinToString("  ·  ").uppercase(), fontSize = 15.sp, letterSpacing = 4.sp,
                color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(22.dp))
            Box(
                Modifier.width(side).padding(horizontal = 4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f))
                    .drawWithContent {
                        val f = if (durationMs > 0) (pos.value.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                        drawRect(Color.White, size = Size(size.width * f, size.height))
                    }.size(width = side, height = 4.dp),
            )
        }
    }
}

/** Modo "Solo visual": sólo los efectos; al cambiar de canción aparece su nombre unos segundos. */
@Composable
private fun NowPlayingToast(song: Song) {
    var show by remember(song.id) { mutableStateOf(true) }
    LaunchedEffect(song.id) { delay(5000); show = false }
    Box(Modifier.fillMaxSize().padding(36.dp), contentAlignment = Alignment.BottomEnd) {
        AnimatedVisibility(show, enter = fadeIn(tween(600)), exit = fadeOut(tween(1200))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverArt(song.coverArt, Modifier.size(64.dp), size = 200, corner = 10.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(song.title, style = Display.copy(fontSize = 24.sp, color = Color.White, shadow = Glow))
                    Text((song.artist ?: "").uppercase(), fontSize = 12.sp, letterSpacing = 3.sp, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

/** Elegir el fondo: "Mezcla" (cambian solos) o uno fijo. */
@Composable
private fun FxPicker(current: VisualPreset) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            "Fondo: " + (Immersive.pinned?.label ?: "Mezcla") + " ▾", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f)).bouncyClick { open = true }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
        androidx.compose.material3.DropdownMenu(open, { open = false }) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Mezcla automática" + if (Immersive.pinned == null) "  ✓" else "") },
                onClick = { Immersive.pinned = null; open = false },
            )
            BasePool.forEach { p ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(p.label + if (Immersive.pinned == p) "  ✓" else if (Immersive.pinned == null && current == p) "  (ahora)" else "") },
                    onClick = { Immersive.pinned = p; open = false },
                )
            }
        }
    }
}
