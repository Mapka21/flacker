package com.marco.audiopro.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.StructuredLyrics
import com.marco.audiopro.platform.supportsBlur
import com.marco.audiopro.ui.rememberPlaybackPosition
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import kotlin.math.abs

/**
 * Letra estilo Apple Music: la línea que suena grande y nítida; las que
 * ya pasaron se apagan y las que vienen esperan un poco más claras; en las partes
 * instrumentales aparecen los tres puntitos que se van llenando. Se acomoda sola a un tercio
 * de la altura; si mueves la letra con el dedo o la rueda, la respeta unos segundos. Tocar una
 * línea salta a ese momento. La mejor letra se elige sola (ver LyricsRepository).
 * @param fontSize tamaño de la letra sincronizada (la no sincronizada usa ~65 %).
 */
@Composable
fun LyricsPane(song: Song, modifier: Modifier = Modifier, fontSize: TextUnit = 30.sp, horizontalPadding: Int = 22) {
    val c = LocalApp.current
    var lyrics by remember(song.id) { mutableStateOf<StructuredLyrics?>(null) }
    var loaded by remember(song.id) { mutableStateOf(false) }
    LaunchedEffect(song.id) { lyrics = runCatching { c.lyrics.get(song) }.getOrNull(); loaded = true }
    val l = lyrics
    when {
        !loaded -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { InstrumentalDots(progress = -1f, size = 12.dp) }
        l == null || l.line.isEmpty() -> Column(
            modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsciiArt(AsciiSkull.SMALL, fontSize = 3.6.sp, top = Hifi.TextDim, bottom = Hifi.TextFaint)
            Spacer(Modifier.height(10.dp))
            Text("Sin letra para esta canción", color = Hifi.TextDim, fontSize = 15.sp)
            Text("No hay una letra que coincida con esta versión.", color = Hifi.TextFaint, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        else -> SyncedLyrics(song, l, modifier, fontSize, horizontalPadding)
    }
}

/**
 * Los tres puntitos de Apple Music en las partes sin letra: se llenan uno a uno según avanza
 * la pausa y el grupo "respira". `progress` < 0 = cargando (sólo respiran).
 */
@Composable
private fun InstrumentalDots(progress: Float, size: Dp, modifier: Modifier = Modifier) {
    val animating by rememberAnimating()
    val breathe = if (!animating) 1f else rememberInfiniteTransition(label = "dots").animateFloat(
        0.88f, 1.08f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "b",
    ).value
    Row(modifier.graphicsLayer { scaleX = breathe; scaleY = breathe; transformOrigin = TransformOrigin(0f, 0.5f) }) {
        for (i in 0..2) {
            val fill = if (progress < 0) 0.55f else ((progress * 3f - i).coerceIn(0f, 1f) * 0.75f + 0.25f)
            Box(Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = fill)))
            if (i < 2) Spacer(Modifier.width(size * 0.6f))
        }
    }
}

@Composable
private fun SyncedLyrics(song: Song, l: StructuredLyrics, modifier: Modifier, fontSize: TextUnit, hPad: Int) {
    val c = LocalApp.current
    val rawPos by rememberPlaybackPosition(80)
    val pos = rawPos + (c.lyrics.offsets.collectAsState().value[song.id] ?: 0L)
    val lines = l.line
    // Intro instrumental: si la primera línea tarda en llegar, puntitos arriba mientras tanto.
    val firstStart = lines.firstOrNull { it.value.isNotBlank() }?.start ?: 0L
    val intro = l.synced && firstStart > 3_000
    val current = if (l.synced) lines.indexOfLast { (it.start ?: 0) <= pos } else -1
    // Índice en la lista (con la intro, todo se recorre uno).
    val focus = if (!l.synced) -1 else if (current < 0) (if (intro) 0 else -1) else current + (if (intro) 1 else 0)
    val list = rememberLazyListState()
    var userScrollAt by remember { mutableLongStateOf(0L) }
    var autoScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { if (it && !autoScrolling) userScrollAt = System.currentTimeMillis() }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val anchor = maxHeight * 0.30f
        LaunchedEffect(focus) {
            if (focus < 0 || System.currentTimeMillis() - userScrollAt < 4000) return@LaunchedEffect
            autoScrolling = true
            try { list.animateScrollToItem(focus, 0) } finally { autoScrolling = false }
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
            if (intro) {
                item(key = "intro") {
                    val p = (pos.toFloat() / firstStart).coerceIn(0f, 1f)
                    val on = current < 0
                    val a by animateFloatAsState(if (on) 1f else 0f, tween(400), label = "ia")
                    Box(Modifier.padding(vertical = (size.value * 0.4f).dp).graphicsLayer { alpha = a }) {
                        InstrumentalDots(p, (size.value * 0.42f).dp)
                    }
                }
            }
            itemsIndexed(lines) { i, line ->
                val active = !l.synced || i == current
                val distance = if (current < 0) i + 1 else abs(i - current)
                val past = l.synced && current >= 0 && i < current
                val alpha by animateFloatAsState(
                    when {
                        !l.synced -> 0.88f
                        active -> 1f
                        past -> (0.34f - distance * 0.03f).coerceAtLeast(0.14f)
                        else -> (0.46f - distance * 0.04f).coerceAtLeast(0.18f)
                    },
                    tween(520), label = "la",
                )
                val scale by animateFloatAsState(if (active && l.synced) 1f else 0.955f, spring(dampingRatio = 0.72f, stiffness = 260f), label = "ls")
                val blurR by animateDpAsState(
                    if (active || !supportsBlur || !l.synced) 0.dp else (1.0f + distance * 0.55f).coerceAtMost(3.6f).dp, tween(520), label = "lb",
                )
                val rowMod = Modifier
                    .fillMaxWidth()
                    .padding(vertical = (size.value * 0.28f).dp)
                    .graphicsLayer {
                        this.alpha = alpha; scaleX = scale; scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .blur(blurR)
                    .clickable(remember { MutableInteractionSource() }, indication = null, enabled = l.synced && line.start != null) {
                        userScrollAt = 0L
                        line.start?.let { c.player.seekTo(it - (c.lyrics.offsets.value[song.id] ?: 0L)) }
                    }
                if (line.value.isBlank() && l.synced) {
                    // Parte instrumental: puntitos que se llenan hasta la siguiente línea.
                    val next = lines.drop(i + 1).firstOrNull { it.start != null }?.start
                    val start = line.start ?: 0
                    val p = if (active && next != null && next > start) ((pos - start).toFloat() / (next - start)).coerceIn(0f, 1f) else 0f
                    Box(rowMod) { InstrumentalDots(p, (size.value * 0.42f).dp) }
                } else {
                    Text(
                        line.value.ifBlank { "♪" },
                        style = Display.copy(fontSize = size, lineHeight = size * 1.2f, color = Color.White),
                        modifier = rowMod,
                    )
                }
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
