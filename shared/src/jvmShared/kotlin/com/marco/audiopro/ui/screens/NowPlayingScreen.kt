package com.marco.audiopro.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.marco.audiopro.ui.components.GestureCover
import com.marco.audiopro.ui.components.rememberTick
import com.marco.audiopro.ui.components.SwipeToSkip
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.marco.audiopro.audio.AudioEngine
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.khz
import com.marco.audiopro.player.RepeatMode
import com.marco.audiopro.ui.DspRoute
import com.marco.audiopro.ui.components.CoverArt
import com.marco.audiopro.ui.components.DevicesButton
import com.marco.audiopro.ui.components.RemoteBadge
import com.marco.audiopro.ui.components.DownloadButton
import com.marco.audiopro.ui.components.GlassChip
import com.marco.audiopro.ui.components.LikeButton
import com.marco.audiopro.ui.components.LyricsPane
import com.marco.audiopro.ui.components.VisualModeButton
import com.marco.audiopro.ui.components.ShareStoryButton
import com.marco.audiopro.ui.components.bouncyClick
import com.marco.audiopro.ui.components.formatDuration
import com.marco.audiopro.ui.components.formatMs
import com.marco.audiopro.ui.components.glass
import com.marco.audiopro.ui.components.volumeWheel
import com.marco.audiopro.ui.components.onRightClick
import com.marco.audiopro.ui.openAlbum
import com.marco.audiopro.ui.openArtist
import com.marco.audiopro.ui.rememberPlaybackPosition
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import com.marco.audiopro.ui.viz.Visualizer
import com.marco.audiopro.ui.viz.VizMode
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Qué ocupa el área grande: la portada, la letra o la cola (como los botones de Apple Music). */
private enum class Stage { COVER, LYRICS, QUEUE }

/**
 * Reproductor a pantalla completa. El fondo (portada viva / visualizador) lo pinta la cáscara,
 * así que aquí sólo va el contenido. En ventana ancha: reproductor a la izquierda y la letra o
 * la cola a la derecha; en el teléfono los botones de abajo cambian el área grande.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(nav: NavController) {
    val c = LocalApp.current
    val st by c.player.state.collectAsState()
    val song = st.current
    if (song == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    var showPath by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        if (maxWidth > 900.dp) WidePlayer(song, nav, maxWidth) { showPath = true }
        else PhonePlayer(song, nav) { showPath = true }
    }

    if (showPath) {
        ModalBottomSheet(onDismissRequest = { showPath = false }, containerColor = Hifi.PanelHi.copy(alpha = 0.97f), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) { SignalPathSheet(song, c.engine) }
    }
}

// ------------------------------------------------------------------ PC / tablet

@Composable
private fun WidePlayer(song: Song, nav: NavController, width: Dp, onPath: () -> Unit) {
    val c = LocalApp.current
    val st by c.player.state.collectAsState()
    var side by rememberSaveable { mutableStateOf(Stage.LYRICS) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Icons.Default.KeyboardArrowDown, "Cerrar") { nav.popBackStack() }
            Spacer(Modifier.width(14.dp))
            RemoteBadge(st.remoteName)
            Spacer(Modifier.weight(1f))
            Row(Modifier.glass(RoundedCornerShape(50), Color.Black.copy(alpha = 0.25f)).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                VisualModeButton(size = 20)
                StageToggle(Icons.Default.Fullscreen, "Pantalla completa (F11)", false) { com.marco.audiopro.ui.Immersive.on = true }
                ShareStoryButton(song, size = 19)
                StageToggle(Icons.Default.Lyrics, "Letra", side == Stage.LYRICS) { side = if (side == Stage.LYRICS) Stage.COVER else Stage.LYRICS }
                StageToggle(Icons.AutoMirrored.Filled.QueueMusic, "Cola", side == Stage.QUEUE) { side = if (side == Stage.QUEUE) Stage.COVER else Stage.QUEUE }
                DevicesButton(size = 20)
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth().padding(start = 48.dp, end = 32.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth().volumeWheel().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    var menu by remember { mutableStateOf<androidx.compose.ui.unit.DpOffset?>(null) }
                    Box(Modifier.onRightClick { menu = it }) {
                        BreathingCover(song, st.isPlaying, Modifier.widthIn(max = 560.dp))
                        com.marco.audiopro.ui.components.SongMenu(song, menu != null, { menu = null }, nav, offset = menu ?: androidx.compose.ui.unit.DpOffset.Zero)
                    }
                }
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                    TitleBlock(song, nav)
                    SeekBar(st.durationMs)
                    Controls(st.isPlaying, st.isBuffering, st.shuffle, st.repeat)
                    VolumeRow()
                    st.error?.let { Text(it, color = Hifi.LedRed, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        LcdStrip(song, c.engine, Modifier.weight(1f), onPath)
                        Spacer(Modifier.width(10.dp))
                        StageToggle(Icons.Default.Equalizer, "Ecualizador", false) { nav.navigate(DspRoute) }
                    }
                }
            }
            AnimatedVisibility(
                side != Stage.COVER,
                enter = expandHorizontally(tween(280)) + fadeIn(),
                exit = shrinkHorizontally() + fadeOut(),
            ) {
                Box(Modifier.padding(start = 40.dp).width(width * 0.44f).fillMaxHeight()) {
                    AnimatedContent(side, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "side") { s ->
                        when (s) {
                            Stage.QUEUE -> QueuePane(Modifier.fillMaxSize())
                            else -> LyricsPane(song, Modifier.fillMaxSize(), fontSize = 34.sp, horizontalPadding = 8)
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ teléfono

@Composable
private fun PhonePlayer(song: Song, nav: NavController, onPath: () -> Unit) {
    val c = LocalApp.current
    val st by c.player.state.collectAsState()
    var stage by rememberSaveable { mutableStateOf(Stage.COVER) }
    // Controles por gestos (Ajustes → Reproductor); de fábrica, encendidos.
    val gestures = remember { c.settings.getString("gesture_player") != "0" }
    // Jalar hacia abajo (desde arriba o desde la portada) cierra el reproductor, como en Spotify.
    val drop = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissDrag = Modifier.draggable(
        orientation = Orientation.Vertical,
        state = rememberDraggableState { d -> scope.launch { drop.snapTo((drop.value + d).coerceAtLeast(0f)) } },
        onDragStopped = { v ->
            if (drop.value > with(density) { 130.dp.toPx() } || v > 2000f) nav.popBackStack()
            else drop.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 420f))
        },
    )
    Column(
        Modifier.fillMaxSize().graphicsLayer {
            translationY = drop.value
            val k = (drop.value / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
            alpha = 1f - k * 0.6f
            scaleX = 1f - k * 0.08f; scaleY = 1f - k * 0.08f
        },
    ) {
        Row(Modifier.fillMaxWidth().then(dismissDrag).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).bouncyClick { nav.popBackStack() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.KeyboardArrowDown, "Cerrar", tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (st.remoteName != null) RemoteBadge(st.remoteName)
                else Text("REPRODUCIENDO", style = Mono.copy(color = Hifi.OnArt.copy(alpha = 0.6f), fontSize = 9.sp))
                Text(song.album ?: "", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.size(44.dp).bouncyClick { com.marco.audiopro.ui.Immersive.on = true }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Fullscreen, "Pantalla completa", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            ShareStoryButton(song, size = 21, tint = Color.White)
            VisualModeButton()
        }
        AnimatedContent(stage, Modifier.weight(1f), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "stage") { s ->
            when (s) {
                Stage.COVER -> Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth().then(dismissDrag).padding(horizontal = 24.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                        if (gestures) {
                            // Sin botones: la portada es el control (toque, deslizar, mantener y arrastrar).
                            GestureCover(
                                playing = st.isPlaying, durationMs = st.durationMs, position = { c.player.positionMs() },
                                onToggle = { c.player.toggle() }, onNext = { c.player.next() }, onPrevious = { c.player.previous() },
                                onSeek = { c.player.seekTo(it) },
                            ) { BreathingCover(song, st.isPlaying, Modifier) }
                        } else {
                            // La portada se desliza a los lados para cambiar de canción.
                            SwipeToSkip(onNext = { c.player.next() }, onPrevious = { c.player.previous() }) {
                                BreathingCover(song, st.isPlaying, Modifier)
                            }
                        }
                    }
                    Column(Modifier.padding(horizontal = 24.dp)) {
                        LcdStrip(song, c.engine, Modifier.fillMaxWidth(), onPath)
                        Spacer(Modifier.height(14.dp))
                        TitleBlock(song, nav)
                    }
                }
                Stage.LYRICS -> Column(Modifier.fillMaxSize()) {
                    CompactHeader(song) { stage = Stage.COVER }
                    LyricsPane(song, Modifier.weight(1f), fontSize = 28.sp)
                }
                Stage.QUEUE -> Column(Modifier.fillMaxSize()) {
                    CompactHeader(song) { stage = Stage.COVER }
                    QueuePane(Modifier.weight(1f).padding(horizontal = 8.dp))
                }
            }
        }
        Column(Modifier.padding(horizontal = 24.dp)) {
            SeekBar(st.durationMs)
            // Con gestos, la portada es el control; en la letra o la cola no hay portada, así que van los botones.
            if (gestures && stage == Stage.COVER) GestureHint(st.isPlaying) else Controls(st.isPlaying, st.isBuffering, st.shuffle, st.repeat)
            VolumeRow()
            st.error?.let { Text(it, color = Hifi.LedRed, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                StageToggle(Icons.Default.Lyrics, "Letra", stage == Stage.LYRICS) { stage = if (stage == Stage.LYRICS) Stage.COVER else Stage.LYRICS }
                StageToggle(Icons.Default.Equalizer, "DSP", false) { nav.navigate(DspRoute) }
                if (gestures && stage == Stage.COVER) {
                    // Sin la fila de botones, aleatorio y repetir viven aquí abajo.
                    StageToggle(Icons.Default.Shuffle, "Aleatorio", st.shuffle) { c.player.toggleShuffle() }
                    StageToggle(if (st.repeat == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, "Repetir", st.repeat != RepeatMode.OFF) { c.player.cycleRepeat() }
                }
                DevicesButton(size = 22)
                if (!gestures) StageToggle(Icons.Default.Route, "Señal", false, onPath)
                StageToggle(Icons.AutoMirrored.Filled.QueueMusic, "Cola", stage == Stage.QUEUE) { stage = if (stage == Stage.QUEUE) Stage.COVER else Stage.QUEUE }
            }
        }
    }
}

/** Portada chica + título: arriba de la letra/cola en el teléfono (tocarla regresa a la portada). */
@Composable
private fun CompactHeader(song: Song, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CoverArt(song.coverArt, Modifier.size(58.dp).shadow(12.dp, RoundedCornerShape(8.dp)).bouncyClick(onClick = onClick), size = 200, corner = 8.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = Display.copy(fontSize = 17.sp, color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist ?: "", fontSize = 14.sp, color = Hifi.OnArt, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LikeButton(song, size = 22.dp)
    }
}

/** La portada "respira": se encoge un poco en pausa. */
@Composable
private fun BreathingCover(song: Song, playing: Boolean, modifier: Modifier) {
    val scale by animateFloatAsState(if (playing) 1f else 0.84f, spring(dampingRatio = 0.62f, stiffness = 180f), label = "cover")
    CoverArt(
        song.coverArt,
        modifier.aspectRatio(1f, matchHeightConstraintsFirst = true)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(40.dp, RoundedCornerShape(18.dp)),
        size = 800, corner = 18.dp,
    )
}

@Composable
private fun RoundButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).glass(CircleShape, Color.Black.copy(alpha = 0.25f)).bouncyClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(24.dp)) }
}

/** Botón de glifo que, encendido, lleva una pastilla de vidrio detrás (como en iOS). */
@Composable
private fun StageToggle(icon: ImageVector, desc: String, on: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) Color.White.copy(alpha = 0.22f) else Color.Transparent, label = "stage")
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(bg).bouncyClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, Modifier.size(22.dp), tint = if (on) Color.White else Hifi.OnArt) }
}

/** Tira tipo display de un DAC: formato de origen, ReplayGain y estado del EQ. */
@Composable
private fun LcdStrip(song: Song, engine: AudioEngine, modifier: Modifier, onClick: () -> Unit) {
    val eq by engine.config.collectAsState()
    val signal by engine.signal.collectAsState()
    Row(
        modifier
            .glass(RoundedCornerShape(12.dp))
            .bouncyClick(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val lcd = Mono.copy(color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        val parts = buildList {
            add((song.suffix ?: "?").uppercase())
            song.bitDepth?.takeIf { it > 0 }?.let { add("$it BIT") }
            song.samplingRate?.takeIf { it > 0 }?.let { add("${khz(it)} kHz") }
            // En lossless los kbps no dicen nada; en formatos con pérdida sí importan.
            if (!song.isLossless) song.bitRate?.takeIf { it > 0 }?.let { add("$it kbps") }
        }
        Text(parts.joinToString("  "), style = lcd, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (song.isPreview) {
            Text("MUESTRA 30 s", style = lcd.copy(color = Hifi.Accent, fontSize = 10.sp))
            return@Row
        }
        if (signal.replayGainDb != null) {
            Text("RG %+.1f".format(signal.replayGainDb), style = lcd.copy(color = Hifi.OnArt, fontSize = 10.sp))
            Spacer(Modifier.width(8.dp))
        }
        if (song.isHiRes) {
            Text("HI-RES", style = lcd.copy(color = Hifi.HiResGold, fontSize = 10.sp))
            Spacer(Modifier.width(8.dp))
        }
        Text("EQ", style = lcd.copy(color = if (eq.enabled) Hifi.LedGreen else Hifi.OnArt.copy(alpha = 0.4f), fontSize = 10.sp))
    }
}

@Composable
private fun TitleBlock(song: Song, nav: NavController) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(song.title, style = Display.copy(fontSize = 26.sp, color = Color.White), maxLines = 1, modifier = Modifier.basicMarquee())
            Text(
                song.artist ?: "", fontSize = 18.sp, color = Hifi.OnArt, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { song.artistId?.let(nav::openArtist) },
            )
            Text(
                listOfNotNull(song.album, song.year?.toString()).joinToString(" · "), fontSize = 13.sp, color = Hifi.OnArt.copy(alpha = 0.5f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { song.albumId?.let(nav::openAlbum) },
            )
        }
        DownloadButton(listOf(song))
        LikeButton(song, size = 28.dp)
    }
}

/**
 * Barra de avance. Al soltar, se queda en el punto elegido hasta que el reproductor confirma
 * la nueva posición (la PC tarda un momento en buscar dentro del archivo), así no "rebota".
 */
@Composable
fun SeekBar(durationMs: Long, modifier: Modifier = Modifier, compact: Boolean = false) {
    val player = LocalApp.current.player
    val pos by rememberPlaybackPosition(200)
    var dragging by remember { mutableStateOf<Float?>(null) }
    var pending by remember { mutableStateOf<Long?>(null) }
    val dur = durationMs.coerceAtLeast(1)
    LaunchedEffect(pending) { if (pending != null) { delay(2500); pending = null } }
    val p = pending
    if (p != null && abs(pos - p) < 1500) pending = null
    val value = dragging ?: ((p ?: pos).toFloat() / dur).coerceIn(0f, 1f)
    Column(modifier) {
        if (!compact) Spacer(Modifier.height(10.dp))
        AppleBar(value, onChange = { dragging = it }, onCommit = { v ->
            val ms = (v * dur).toLong()
            pending = ms
            dragging = null
            player.seekTo(ms)
        })
        Row(Modifier.fillMaxWidth().padding(top = if (compact) 0.dp else 6.dp)) {
            val shown = (value * dur).toLong()
            Text(formatMs(shown), style = Mono.copy(color = Hifi.OnArt.copy(alpha = 0.6f), fontSize = 10.sp), modifier = Modifier.weight(1f))
            Text("-" + formatMs(dur - shown), style = Mono.copy(color = Hifi.OnArt.copy(alpha = 0.6f), fontSize = 10.sp))
        }
    }
}

/**
 * Barra fina estilo Apple: sin "thumb"; al tocarla o arrastrarla se engrosa con resorte.
 * Se arrastra desde cualquier punto y salta al tocar.
 */
@Composable
fun AppleBar(value: Float, onChange: (Float) -> Unit, onCommit: (Float) -> Unit, modifier: Modifier = Modifier) {
    var active by remember { mutableStateOf(false) }
    var last by remember { mutableStateOf(value) }
    // Los gestos viven más que una recomposición: siempre llaman a la versión actual de los
    // callbacks (antes se quedaban con la duración de la primera canción y saltaban mal).
    val change by rememberUpdatedState(onChange)
    val commit by rememberUpdatedState(onCommit)
    val thick by animateDpAsState(if (active) 10.dp else 5.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "bar")
    Box(
        modifier.fillMaxWidth().height(22.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = { o ->
                    active = true
                    last = (o.x / size.width).coerceIn(0f, 1f)
                    change(last)
                    val released = tryAwaitRelease()
                    active = false
                    if (released) commit(last)
                })
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> active = true; last = (o.x / size.width).coerceIn(0f, 1f); change(last) },
                    onDragEnd = { active = false; commit(last) },
                    onDragCancel = { active = false; commit(last) },
                ) { ch, _ ->
                    ch.consume()
                    last = (ch.position.x / size.width).coerceIn(0f, 1f)
                    change(last)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(thick).clip(CircleShape).background(Color.White.copy(alpha = 0.22f))) {
            Box(Modifier.fillMaxWidth(value).fillMaxHeight().background(Color.White.copy(alpha = if (active) 1f else 0.85f)))
        }
    }
}

@Composable
private fun Controls(playing: Boolean, buffering: Boolean, shuffle: Boolean, repeat: RepeatMode) {
    val player = LocalApp.current.player
    val tick = rememberTick()
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        StageToggle(Icons.Default.Shuffle, "Aleatorio", shuffle) { tick(); player.toggleShuffle() }
        Glyph(Icons.Default.SkipPrevious, 46.dp, "Anterior") { tick(); player.previous() }
        Box(Modifier.size(78.dp).bouncyClick { tick(); player.toggle() }, contentAlignment = Alignment.Center) {
            if (buffering) {
                CircularProgressIndicator(Modifier.size(40.dp), color = Color.White, strokeWidth = 3.dp)
            } else {
                AnimatedContent(
                    playing,
                    transitionSpec = {
                        (scaleIn(spring(dampingRatio = 0.5f), 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut())
                    },
                    label = "playpause",
                ) { p -> Icon(if (p) Icons.Default.Pause else Icons.Default.PlayArrow, "Reproducir", Modifier.size(64.dp), tint = Color.White) }
            }
        }
        Glyph(Icons.Default.SkipNext, 46.dp, "Siguiente") { tick(); player.next() }
        StageToggle(
            if (repeat == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
            "Repetir", repeat != RepeatMode.OFF,
        ) { player.cycleRepeat() }
    }
}

@Composable
private fun Glyph(icon: ImageVector, size: Dp, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(size + 16.dp).bouncyClick(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, Modifier.size(size), tint = Color.White)
    }
}

/** Volumen (sólo PC): bocinas a los lados de una barra igual a la de avance. */
@Composable
private fun VolumeRow() {
    val player = LocalApp.current.player
    val flow = player.volume ?: return
    val vol by flow.collectAsState()
    Row(Modifier.fillMaxWidth().volumeWheel().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.VolumeDown, null, tint = Hifi.OnArt, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        AppleBar(vol, onChange = { player.setVolume(it) }, onCommit = { player.setVolume(it) }, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Hifi.OnArt, modifier = Modifier.size(18.dp))
    }
}

// ------------------------------------------------------------------ Cola

/** La cola en su lugar (no en una hoja aparte): la actual resaltada, toca para saltar. */
@Composable
fun QueuePane(modifier: Modifier = Modifier) {
    val player = LocalApp.current.player
    val st by player.state.collectAsState()
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (st.currentIndex - 1).coerceAtLeast(0))
    Column(modifier) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("A continuación", style = Display.copy(fontSize = 20.sp), modifier = Modifier.weight(1f))
            Text("${(st.queue.size - st.currentIndex - 1).coerceAtLeast(0)} pistas", style = Mono.copy(color = Hifi.OnArt.copy(alpha = 0.5f)))
        }
        LazyColumn(state = list, contentPadding = PaddingValues(bottom = 16.dp)) {
            itemsIndexed(st.queue, key = { i, s -> "$i-${s.id}" }) { i, s ->
                val cur = i == st.currentIndex
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (cur) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                        .bouncyClick { player.skipToIndex(i) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .graphicsLayer { alpha = if (i < st.currentIndex) 0.45f else 1f },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverArt(s.coverArt, Modifier.size(44.dp), size = 120, corner = 8.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White, fontSize = 14.sp, fontWeight = if (cur) FontWeight.Bold else FontWeight.Medium)
                        Text(s.artist ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, color = Hifi.OnArt, fontSize = 12.sp)
                    }
                    if (cur) Icon(Icons.Default.GraphicEq, null, tint = Hifi.Accent, modifier = Modifier.padding(horizontal = 10.dp))
                    else {
                        Text(formatDuration(s.duration), style = Mono.copy(color = Hifi.OnArt.copy(alpha = 0.5f), fontSize = 10.sp))
                        Box(Modifier.size(36.dp).bouncyClick { player.removeAt(i) }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Close, "Quitar", tint = Hifi.OnArt.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Medidores (en la ruta de señal)

/** Los medidores de estudio (VU, espectro, goniómetro…) quedan como herramienta, no como adorno. */
@Composable
private fun Meters() {
    var mode by rememberSaveable { mutableStateOf(VizMode.VU) }
    Text("MEDIDORES", style = Mono.copy(color = Hifi.TextDim, fontSize = 10.sp), modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(VizMode.entries) { m -> GlassChip(m.label.lowercase().replaceFirstChar { it.uppercase() }, m == mode) { mode = m } }
    }
    Spacer(Modifier.height(10.dp))
    Box(Modifier.fillMaxWidth().height(200.dp).glass(RoundedCornerShape(16.dp), Color.Black.copy(alpha = 0.55f)).padding(10.dp)) {
        Visualizer(mode, Modifier.fillMaxSize())
    }
}

// ------------------------------------------------------------------ Ruta de señal

private enum class Quality(val color: Color) { LOSSLESS(Hifi.LedGreen), PROCESSED(Hifi.Accent), LOSSY(Color(0xFFFF7A45)) }

@Composable
private fun SignalPathSheet(song: Song, engine: AudioEngine) {
    val eq by engine.config.collectAsState()
    val signal by engine.signal.collectAsState()
    val fmt = engine.eq.inputFormat
    val transcoded = signal.transport.contains("Opus") || signal.transport.contains("MP3")

    val steps = buildList {
        add(
            Triple(
                "FUENTE",
                listOfNotNull(
                    (song.suffix ?: "?").uppercase(),
                    song.bitDepth?.takeIf { it > 0 }?.let { "$it-bit" },
                    song.samplingRate?.takeIf { it > 0 }?.let { "${khz(it)} kHz" },
                    song.channelCount?.let { if (it == 2) "estéreo" else "$it canales" },
                    song.bitRate?.let { "$it kbps" },
                ).joinToString(" · "),
                if (song.isLossless) Quality.LOSSLESS else Quality.LOSSY,
            ),
        )
        add(
            Triple(
                "TRANSPORTE",
                when { transcoded -> "${signal.transport} (transcodificado en el servidor)"; signal.transport.startsWith("Descargado") -> "${signal.transport} · desde el teléfono"; else -> "Archivo original sin transcodificar" },
                if (transcoded) Quality.LOSSY else Quality.LOSSLESS,
            ),
        )
        if (fmt != null) {
            val enc = fmt.encoding
            add(Triple("DECODIFICADOR", "PCM $enc · ${khz(fmt.sampleRate)} kHz · ${fmt.channels} ch", Quality.LOSSLESS))
        }
        add(
            Triple(
                "REPLAYGAIN",
                signal.replayGainDb?.let { "%+.1f dB (por %s)".format(it, signal.replayGainSource) } ?: "Apagado",
                if (signal.replayGainDb != null && signal.replayGainDb != 0f) Quality.PROCESSED else Quality.LOSSLESS,
            ),
        )
        add(
            Triple(
                "EQ PARAMÉTRICO",
                if (eq.enabled) "${eq.presetName} · ${eq.bands.count { it.enabled }} bandas · preamp %+.1f dB%s"
                    .format(eq.effectivePreampDb(), if (eq.dither) " · dither TPDF" else "") else "Bypass (sin procesar)",
                if (eq.enabled) Quality.PROCESSED else Quality.LOSSLESS,
            ),
        )
        add(Triple("SALIDA", signal.output.ifBlank { "Salida de audio del sistema" }, Quality.LOSSLESS))
    }
    val overall = steps.maxOf { it.third.ordinal }.let { Quality.entries[it] }

    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("RUTA DE SEÑAL", style = Mono.copy(color = Hifi.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp), modifier = Modifier.weight(1f))
            Box(Modifier.size(10.dp).clip(CircleShape).background(overall.color))
            Spacer(Modifier.width(6.dp))
            Text(
                when (overall) { Quality.LOSSLESS -> "LOSSLESS"; Quality.PROCESSED -> "ALTA CALIDAD · DSP"; Quality.LOSSY -> "CON PÉRDIDA" },
                style = Mono.copy(color = overall.color),
            )
        }
        Spacer(Modifier.height(16.dp))
        steps.forEachIndexed { i, (title, detail, q) ->
            Row {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(18.dp)) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(q.color))
                    if (i < steps.lastIndex) Box(Modifier.width(2.dp).height(42.dp).background(Hifi.Line))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, style = Mono.copy(color = Hifi.TextDim, fontSize = 10.sp))
                    Text(detail, fontSize = 14.sp, color = Hifi.Text)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Nota: el sistema mezcla todo el audio; según el dispositivo, la salida puede re-muestrearse (p. ej. a 48 kHz).",
            fontSize = 11.sp, color = Hifi.TextFaint,
        )
        Meters()
    }
}

/**
 * En lugar de los botones: el estado y cómo se usa la portada (lo de abajo se ve sobre todo en pausa,
 * para que se aprenda sin estorbar).
 */
@Composable
private fun GestureHint(playing: Boolean) {
    val alpha by animateFloatAsState(if (playing) 0.45f else 0.85f, tween(400), label = "hint")
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp).graphicsLayer { this.alpha = alpha }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Toca la portada para ${if (playing) "pausar" else "reproducir"}",
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
        )
        Text("Desliza ← → para cambiar · mantén y arrastra para adelantar · ↓ para cerrar", fontSize = 11.sp, color = Hifi.OnArt, textAlign = TextAlign.Center)
    }
}
