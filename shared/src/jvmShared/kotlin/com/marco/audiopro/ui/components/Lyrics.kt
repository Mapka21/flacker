package com.marco.audiopro.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.audiopro.core.LocalApp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.marco.audiopro.data.info.LyricsCandidate
import kotlinx.coroutines.launch
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.StructuredLyrics
import com.marco.audiopro.platform.supportsBlur
import com.marco.audiopro.ui.rememberPlaybackPosition
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import kotlin.math.abs

/**
 * Letra estilo Apple Music: la línea que suena grande y nítida, las demás tenues y
 * difuminadas; se acomoda sola a un tercio de la altura. Si mueves la letra con el dedo o la
 * rueda, deja de seguir la canción unos segundos. Tocar una línea salta a ese momento.
 * @param fontSize tamaño de la letra sincronizada (la no sincronizada usa ~65 %).
 */
@Composable
fun LyricsPane(song: Song, modifier: Modifier = Modifier, fontSize: TextUnit = 30.sp, horizontalPadding: Int = 22) {
    val c = LocalApp.current
    var lyrics by remember(song.id) { mutableStateOf<StructuredLyrics?>(null) }
    var loaded by remember(song.id) { mutableStateOf(false) }
    var reload by remember(song.id) { mutableStateOf(0) }
    var picking by remember(song.id) { mutableStateOf(false) }
    LaunchedEffect(song.id, reload) { lyrics = runCatching { c.lyrics.get(song) }.getOrNull(); loaded = true }
    if (picking) LyricsPicker(song, onDismiss = { picking = false }) { picking = false; reload++ }
    val l = lyrics
    Box(modifier.fillMaxSize()) {
    when {
        !loaded -> Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Buscando la letra…", color = Hifi.TextFaint, fontSize = 13.sp)
        }
        l == null || l.line.isEmpty() -> Column(
            modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Lyrics, null, tint = Hifi.TextFaint, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(10.dp))
            Text("Sin letra para esta canción", color = Hifi.TextDim, fontSize = 15.sp)
            Text("No encontré una letra de esta misma versión.", color = Hifi.TextFaint, fontSize = 12.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            ToolChip("Buscar letra a mano") { picking = true }
        }
        else -> {
            SyncedLyrics(song, l, Modifier, fontSize, horizontalPadding)
            LyricsTools(song, l.synced, Modifier.align(Alignment.TopEnd)) { picking = true }
        }
    }
    }
}

/**
 * Herramientas de la letra (arriba a la derecha): atrasar/adelantar medio segundo si va
 * desfasada, y "Otra letra" si salió la de otra canción. Se recuerda por canción.
 */
@Composable
private fun LyricsTools(song: Song, synced: Boolean, modifier: Modifier, onPick: () -> Unit) {
    val c = LocalApp.current
    val off = c.lyrics.offsets.collectAsState().value[song.id] ?: 0L
    Row(modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (synced) {
            ToolChip("−½ s") { c.lyrics.setOffset(song.id, off - 500) }
            if (off != 0L) {
                Text(
                    "%+.1f s".format(off / 1000f), style = Mono.copy(color = Hifi.Accent, fontSize = 10.sp),
                    modifier = Modifier.bouncyClick { c.lyrics.setOffset(song.id, 0) },
                )
            }
            ToolChip("+½ s") { c.lyrics.setOffset(song.id, off + 500) }
        }
        ToolChip("Otra letra", onPick)
    }
}

@Composable
private fun ToolChip(label: String, onClick: () -> Unit) {
    Text(
        label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Hifi.OnArt,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.35f)).bouncyClick(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** Elegir la letra a mano entre lo que tiene LRCLIB (la duración más parecida primero). */
@Composable
private fun LyricsPicker(song: Song, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var list by remember { mutableStateOf<List<LyricsCandidate>?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { list = runCatching { c.lyrics.candidates(song) }.getOrDefault(emptyList()) }
    GlassDialog(onDismiss) {
        Text("Elegir letra", style = Display.copy(fontSize = 22.sp))
        Text(
            "${song.title} · ${song.artist ?: ""}${song.duration?.let { " · " + formatDuration(it) } ?: ""}",
            color = Hifi.TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            query, { query = it }, singleLine = true, placeholder = { Text("Buscar otra (artista y título)") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                scope.launch { list = null; list = runCatching { c.lyrics.candidates(song, query) }.getOrDefault(emptyList()) }
            }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        val l = list
        when {
            l == null -> Text("Buscando…", color = Hifi.TextFaint)
            l.isEmpty() -> Text("No hay resultados.", color = Hifi.TextDim)
            else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(l, key = { it.id }) { cand ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).bouncyClick {
                            if (busy) return@bouncyClick
                            busy = true
                            scope.launch { c.lyrics.choose(song, cand.id); busy = false; onChanged() }
                        }.padding(vertical = 8.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(cand.track, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(cand.artist, cand.album).joinToString(" · "), fontSize = 12.sp, color = Hifi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatDuration(cand.duration), style = Mono.copy(color = if ((cand.diff ?: 99) <= 3) Hifi.LedGreen else Hifi.TextDim, fontSize = 11.sp))
                            Text(
                                if (cand.synced) "SINCRONIZADA" else "SÓLO TEXTO",
                                style = Mono.copy(color = if (cand.synced) Hifi.Accent else Hifi.TextFaint, fontSize = 9.sp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { scope.launch { c.lyrics.hide(song); onChanged() } }) { Text("Sin letra", color = Hifi.TextDim) }
            TextButton(onClick = { scope.launch { c.lyrics.reset(song); onChanged() } }) { Text("Automática", color = Hifi.Accent) }
        }
        Text("La duración en verde coincide con tu archivo (±3 s): esa es la versión correcta.", fontSize = 11.sp, color = Hifi.TextFaint)
    }
}

@Composable
private fun SyncedLyrics(song: Song, l: StructuredLyrics, modifier: Modifier, fontSize: TextUnit, hPad: Int) {
    val c = LocalApp.current
    val rawPos by rememberPlaybackPosition(120)
    // Ajuste manual de esta canción (positivo = la letra va antes).
    val pos = rawPos + (c.lyrics.offsets.collectAsState().value[song.id] ?: 0L)
    val current = if (l.synced) l.line.indexOfLast { (it.start ?: 0) <= pos } else -1
    val list = rememberLazyListState()
    // Si el usuario mueve la letra, se respeta su scroll 4 s antes de volver a seguir la canción.
    var userScrollAt by remember { mutableLongStateOf(0L) }
    var autoScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { if (it && !autoScrolling) userScrollAt = System.currentTimeMillis() }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val anchor = maxHeight * 0.30f
        LaunchedEffect(current) {
            if (current < 0 || System.currentTimeMillis() - userScrollAt < 4000) return@LaunchedEffect
            autoScrolling = true
            try { list.animateScrollToItem(current, 0) } finally { autoScrolling = false }
        }
        val size = if (l.synced) fontSize else fontSize * 0.65f
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(start = hPad.dp, end = hPad.dp, top = anchor, bottom = maxHeight * 0.6f),
            modifier = Modifier.fillMaxSize()
                // Las orillas se desvanecen, como en Apple Music.
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(0f to Color.Transparent, 0.12f to Color.Black, 0.85f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            itemsIndexed(l.line) { i, line ->
                val active = !l.synced || i == current
                val distance = if (current < 0) 0 else abs(i - current)
                val alpha by animateFloatAsState(
                    if (!l.synced) 0.85f else if (active) 1f else (0.40f - distance * 0.04f).coerceAtLeast(0.16f), tween(450), label = "la",
                )
                val scale by animateFloatAsState(if (active && l.synced) 1f else 0.95f, spring(dampingRatio = 0.7f, stiffness = 300f), label = "ls")
                val blurR by animateDpAsState(
                    if (active || !supportsBlur || !l.synced) 0.dp else (1.2f + distance * 0.5f).coerceAtMost(3.5f).dp, tween(450), label = "lb",
                )
                Text(
                    line.value.ifBlank { "♪" },
                    style = Display.copy(fontSize = size, lineHeight = size * 1.2f, color = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = (size.value * 0.28f).dp)
                        .graphicsLayer {
                            this.alpha = alpha; scaleX = scale; scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .blur(blurR)
                        .clickable(remember { MutableInteractionSource() }, indication = null, enabled = l.synced && line.start != null) {
                            userScrollAt = 0L
                            line.start?.let { c.player.seekTo(it) }
                        },
                )
            }
            item {
                Text(
                    "LETRA · ${(l.source ?: "Navidrome").uppercase()}${if (l.synced) "" else " · SIN SINCRONIZAR"}",
                    style = Mono.copy(color = Hifi.TextFaint, fontSize = 9.sp),
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
    }
}
