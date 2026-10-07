package com.marco.audiopro.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import com.marco.audiopro.ui.components.AmbientBackground
import com.marco.audiopro.ui.components.bouncyClick
import com.marco.audiopro.ui.components.glass
import androidx.compose.animation.core.tween
import coil3.compose.AsyncImage
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.marco.audiopro.core.LocalApp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.audiopro.player.RepeatMode
import androidx.navigation.NavController
import com.marco.audiopro.audio.AudioEngine
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.StructuredLyrics
import com.marco.audiopro.data.subsonic.khz
import com.marco.audiopro.platform.supportsBlur
import com.marco.audiopro.ui.DspRoute
import com.marco.audiopro.ui.components.CoverArt
import com.marco.audiopro.ui.components.DownloadButton
import com.marco.audiopro.ui.components.LikeButton
import com.marco.audiopro.ui.components.formatMs
import com.marco.audiopro.ui.components.rememberCoverColor
import com.marco.audiopro.ui.openAlbum
import com.marco.audiopro.ui.openArtist
import com.marco.audiopro.ui.rememberPlaybackPosition
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import com.marco.audiopro.ui.viz.Visualizer
import com.marco.audiopro.ui.viz.VizMode
import kotlinx.coroutines.launch

private sealed interface Panel { val label: String }
private data object CoverPanel : Panel { override val label = "PORTADA" }
private data class VizPanel(val mode: VizMode) : Panel { override val label = mode.label }
private data object LyricsPanel : Panel { override val label = "LETRA" }

private val panels: List<Panel> = listOf(CoverPanel) + VizMode.entries.map(::VizPanel) + LyricsPanel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(nav: NavController) {
    val c = LocalApp.current
    val st by c.player.state.collectAsState()
    val song = st.current
    if (song == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val tint by rememberCoverColor(song.coverArt)
    var showQueue by remember { mutableStateOf(false) }
    var showPath by remember { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(0) }
    val pager = rememberPagerState(initialPage = page) { panels.size }
    LaunchedEffect(pager.currentPage) { page = pager.currentPage }

    // Fondo vivo estilo Apple Music: colores de la portada en movimiento + la portada difuminada.
    Box(Modifier.fillMaxSize()) {
        AmbientBackground(song.coverArt)
        if (supportsBlur) {
            AsyncImage(
                c.api.coverUrl(song.coverArt, 400), null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(120.dp).graphicsLayer { alpha = 0.28f },
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to tint.copy(alpha = 0.25f), 1f to Color.Black.copy(alpha = 0.35f))))
    }
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        // En PC/tablet (ventana ancha): panel a la izquierda y controles a la derecha.
        val wide = maxWidth > 900.dp

        val topBar = @Composable {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.KeyboardArrowDown, "Cerrar") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("REPRODUCIENDO", style = Mono.copy(color = Hifi.TextDim, fontSize = 9.sp))
                    Text(song.album ?: "", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = { showQueue = true }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Cola") }
            }
        }

        // Portada / visualizadores / letra
        val pagerBlock = @Composable { mod: Modifier ->
            Column(mod) {
                HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 24.dp), pageSpacing = 16.dp) { i ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when (val p = panels[i]) {
                            CoverPanel -> {
                                // La portada "respira": se encoge un poco en pausa.
                                val scale by animateFloatAsState(if (st.isPlaying) 1f else 0.82f, spring(dampingRatio = 0.62f, stiffness = 180f), label = "cover")
                                CoverArt(
                                    song.coverArt,
                                    Modifier.aspectRatio(1f).graphicsLayer { scaleX = scale; scaleY = scale }
                                        .shadow(40.dp, RoundedCornerShape(18.dp)),
                                    size = 800, corner = 18.dp,
                                )
                            }
                            is VizPanel -> DisplayPanel { Visualizer(p.mode, Modifier.fillMaxSize().padding(12.dp)) }
                            LyricsPanel -> DisplayPanel { LyricsView(song) }
                        }
                    }
                }
                // Indicador de panel
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    panels.indices.forEach { i ->
                        val sel = pager.currentPage == i
                        Box(
                            Modifier.padding(horizontal = 3.dp).size(if (sel) 7.dp else 5.dp).clip(CircleShape)
                                .background(if (sel) Hifi.Accent else Hifi.TextFaint)
                                .clickable { pager.requestScrollToPage(i) },
                        )
                    }
                }
                Text(
                    panels[pager.currentPage].label, style = Mono.copy(color = Hifi.TextFaint, fontSize = 9.sp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center,
                )
            }
        }

        val info = @Composable {
            LcdStrip(song, c.engine) { showPath = true }
            Spacer(Modifier.height(14.dp))
            TitleBlock(song, nav)
            SeekBar(st.durationMs)
            Controls(st.isPlaying, st.isBuffering, st.shuffle, st.repeat)
            VolumeRow()
            st.error?.let { Text(it, color = Hifi.LedRed, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BottomAction(Icons.Default.Equalizer, "DSP") { nav.navigate(DspRoute) }
                BottomAction(Icons.Default.Route, "SEÑAL") { showPath = true }
                DownloadButton(listOf(song))
                BottomAction(Icons.AutoMirrored.Filled.QueueMusic, "COLA") { showQueue = true }
            }
        }

        if (wide) {
            Column(Modifier.fillMaxSize()) {
                topBar()
                Row(Modifier.weight(1f).padding(horizontal = 32.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    pagerBlock(Modifier.weight(1f).fillMaxHeight())
                    Spacer(Modifier.width(40.dp))
                    Column(Modifier.width(460.dp)) { info() }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                topBar()
                pagerBlock(Modifier.fillMaxWidth().weight(1f))
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Spacer(Modifier.height(10.dp))
                    info()
                }
            }
        }
    }

    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }, containerColor = Hifi.PanelHi.copy(alpha = 0.97f), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) { QueueSheet() }
    }
    if (showPath) {
        ModalBottomSheet(onDismissRequest = { showPath = false }, containerColor = Hifi.PanelHi.copy(alpha = 0.97f), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) { SignalPathSheet(song, c.engine) }
    }
}

@Composable
private fun DisplayPanel(content: @Composable () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .shadow(30.dp, RoundedCornerShape(18.dp))
            .glass(RoundedCornerShape(18.dp), Color.Black.copy(alpha = 0.55f)),
    ) { content() }
}

/** Tira tipo display de un DAC: formato de origen, ReplayGain y estado del EQ. */
@Composable
private fun LcdStrip(song: Song, engine: AudioEngine, onClick: () -> Unit) {
    val eq by engine.config.collectAsState()
    val signal by engine.signal.collectAsState()
    Row(
        Modifier
            .fillMaxWidth()
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
        LikeButton(song, size = 28.dp)
    }
}

@Composable
private fun SeekBar(durationMs: Long) {
    val player = LocalApp.current.player
    val pos by rememberPlaybackPosition(200)
    var dragging by remember { mutableStateOf<Float?>(null) }
    val dur = durationMs.coerceAtLeast(1)
    val value = dragging ?: (pos.toFloat() / dur).coerceIn(0f, 1f)
    Spacer(Modifier.height(10.dp))
    AppleBar(value, onChange = { dragging = it }, onCommit = { v -> player.seekTo((v * dur).toLong()); dragging = null })
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(formatMs((value * dur).toLong()), style = Mono.copy(color = Hifi.OnArt.copy(alpha = 0.6f), fontSize = 10.sp), modifier = Modifier.weight(1f))
        Text("-" + formatMs(dur - (value * dur).toLong()), style = Mono.copy(color = Hifi.OnArt.copy(alpha = 0.6f), fontSize = 10.sp))
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
    val thick by animateDpAsState(if (active) 10.dp else 5.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "bar")
    Box(
        modifier.fillMaxWidth().height(22.dp)
            .pointerInput(Unit) {
                detectTapGestures(onPress = { o ->
                    active = true
                    last = (o.x / size.width).coerceIn(0f, 1f)
                    onChange(last)
                    val released = tryAwaitRelease()
                    active = false
                    if (released) onCommit(last)
                })
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> active = true; last = (o.x / size.width).coerceIn(0f, 1f); onChange(last) },
                    onDragEnd = { active = false; onCommit(last) },
                    onDragCancel = { active = false },
                ) { change, _ ->
                    last = (change.position.x / size.width).coerceIn(0f, 1f)
                    onChange(last)
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
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        ToggleGlyph(Icons.Default.Shuffle, shuffle, "Aleatorio") { player.toggleShuffle() }
        Glyph(Icons.Default.SkipPrevious, 46.dp, "Anterior") { player.previous() }
        Box(Modifier.size(78.dp).bouncyClick { player.toggle() }, contentAlignment = Alignment.Center) {
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
        Glyph(Icons.Default.SkipNext, 46.dp, "Siguiente") { player.next() }
        ToggleGlyph(
            if (repeat == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
            repeat != RepeatMode.OFF, "Repetir",
        ) { player.cycleRepeat() }
    }
}

@Composable
private fun Glyph(icon: ImageVector, size: Dp, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(size + 16.dp).bouncyClick(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, Modifier.size(size), tint = Color.White)
    }
}

/** Shuffle/repetir: glifo blanco; activo = pastilla de vidrio detrás, como en iOS. */
@Composable
private fun ToggleGlyph(icon: ImageVector, on: Boolean, desc: String, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) Color.White.copy(alpha = 0.22f) else Color.Transparent, label = "tg")
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(bg).bouncyClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, Modifier.size(22.dp), tint = if (on) Color.White else Hifi.OnArt.copy(alpha = 0.6f)) }
}

/** Volumen (sólo PC): bocinas a los lados de una barra igual a la de avance. */
@Composable
private fun VolumeRow() {
    val player = LocalApp.current.player
    val flow = player.volume ?: return
    val vol by flow.collectAsState()
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.VolumeDown, null, tint = Hifi.OnArt, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        AppleBar(vol, onChange = { player.setVolume(it) }, onCommit = { player.setVolume(it) }, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Hifi.OnArt, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun BottomAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.bouncyClick(onClick = onClick).padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = Hifi.OnArt, modifier = Modifier.size(22.dp))
        Text(label, style = Mono.copy(color = Hifi.OnArt.copy(alpha = 0.55f), fontSize = 9.sp))
    }
}

// ------------------------------------------------------------------ Letra sincronizada

@Composable
private fun LyricsView(song: Song) {
    val c = LocalApp.current
    var lyrics by remember(song.id) { mutableStateOf<StructuredLyrics?>(null) }
    var loaded by remember(song.id) { mutableStateOf(false) }
    LaunchedEffect(song.id) { lyrics = c.api.lyrics(song.id); loaded = true }
    val pos by rememberPlaybackPosition(150)
    val l = lyrics
    when {
        !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("…", color = Hifi.TextFaint) }
        l == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Sin letra para esta canción", color = Hifi.TextFaint, fontSize = 13.sp)
        }
        else -> {
            val current = if (l.synced) l.line.indexOfLast { (it.start ?: 0) <= pos } else -1
            val list = rememberLazyListState()
            LaunchedEffect(current) { if (current >= 0) list.animateScrollToItem(current, -260) }
            LazyColumn(state = list, contentPadding = PaddingValues(vertical = 140.dp, horizontal = 22.dp)) {
                itemsIndexed(l.line) { i, line ->
                    // Estilo Apple Music: la línea actual grande y nítida; las demás tenues y difuminadas.
                    val active = !l.synced || i == current
                    val distance = if (current < 0) 0 else kotlin.math.abs(i - current)
                    val alpha by animateFloatAsState(
                        if (active) 1f else (0.42f - distance * 0.05f).coerceAtLeast(0.18f), tween(450), label = "la",
                    )
                    val scale by animateFloatAsState(
                        if (active && l.synced) 1f else 0.94f, spring(dampingRatio = 0.7f, stiffness = 300f), label = "ls",
                    )
                    val blurR by animateDpAsState(
                        if (active || !supportsBlur) 0.dp else (1.5f + distance * 0.6f).coerceAtMost(4f).dp, tween(450), label = "lb",
                    )
                    Text(
                        line.value.ifBlank { "♪" },
                        style = Display.copy(
                            fontSize = if (l.synced) 28.sp else 18.sp,
                            lineHeight = if (l.synced) 34.sp else 26.sp,
                            color = Color.White,
                        ),
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .graphicsLayer {
                                this.alpha = alpha; scaleX = scale; scaleY = scale
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }
                            .blur(blurR)
                            .clickable(enabled = l.synced && line.start != null) { line.start?.let { c.player.seekTo(it) } },
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Cola

@Composable
private fun QueueSheet() {
    val player = LocalApp.current.player
    val st by player.state.collectAsState()
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (st.currentIndex - 1).coerceAtLeast(0))
    Column(Modifier.fillMaxHeight(0.85f)) {
        Text(
            "COLA · ${st.queue.size} pistas", style = Mono.copy(color = Hifi.Text, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(state = list) {
            itemsIndexed(st.queue, key = { i, s -> "$i-${s.id}" }) { i, s ->
                val cur = i == st.currentIndex
                Row(
                    Modifier.fillMaxWidth().clickable { player.skipToIndex(i) }.background(if (cur) Hifi.PanelHi else Color.Transparent)
                        .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverArt(s.coverArt, Modifier.size(40.dp), size = 120, corner = 4.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (cur) Hifi.Accent else Hifi.Text, fontSize = 14.sp)
                        Text(s.artist ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, color = Hifi.TextDim, fontSize = 12.sp)
                    }
                    if (cur) Icon(Icons.Default.GraphicEq, null, tint = Hifi.Accent, modifier = Modifier.padding(horizontal = 12.dp))
                    else IconButton(onClick = { player.removeAt(i) }) { Icon(Icons.Default.Close, null, tint = Hifi.TextFaint) }
                }
            }
        }
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

    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
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
    }
}
