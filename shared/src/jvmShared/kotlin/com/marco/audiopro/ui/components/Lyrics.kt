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
    LaunchedEffect(song.id) { lyrics = runCatching { c.lyrics.get(song) }.getOrNull(); loaded = true }
    val l = lyrics
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
            Text("No la tiene Navidrome ni LRCLIB.", color = Hifi.TextFaint, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        else -> SyncedLyrics(l, modifier, fontSize, horizontalPadding)
    }
}

@Composable
private fun SyncedLyrics(l: StructuredLyrics, modifier: Modifier, fontSize: TextUnit, hPad: Int) {
    val c = LocalApp.current
    val pos by rememberPlaybackPosition(120)
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
