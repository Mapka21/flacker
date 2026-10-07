package com.marco.audiopro.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import com.marco.audiopro.core.LocalApp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.audiopro.download.OfflineSong
import com.marco.audiopro.ui.components.CoverArt
import com.marco.audiopro.ui.components.DownloadButton
import com.marco.audiopro.ui.components.PlayActions
import com.marco.audiopro.ui.components.SectionHeader
import com.marco.audiopro.ui.components.TrackRow
import com.marco.audiopro.ui.components.formatBytes
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.components.songsLabel
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import kotlinx.coroutines.delay

private data class OfflineAlbum(val key: String, val name: String, val artist: String?, val coverArt: String?, val songs: List<OfflineSong>)

@Composable
fun DownloadsScreen() {
    val c = LocalApp.current
    val store = c.downloads ?: return
    val items by store.items.collectAsState()
    val playback by c.player.state.collectAsState()

    val done = remember(items) { items.values.filter { it.done } }
    val active = remember(items) { items.values.filter { it.active || it.failed } }
    val albums = remember(done) {
        done.groupBy { it.song.albumId ?: it.song.album ?: "?" }
            .map { (k, list) ->
                val sorted = list.sortedWith(compareBy({ it.song.discNumber ?: 1 }, { it.song.track ?: 0 }))
                val s = sorted.first().song
                OfflineAlbum(k, s.album ?: "Sin álbum", s.artist, s.coverArt, sorted)
            }
            .sortedBy { it.name.lowercase() }
    }
    val allSongs = remember(albums) { albums.flatMap { a -> a.songs.map { it.song } } }
    val totalBytes = done.sumOf { it.bytes }
    val free = remember { store.freeBytes() }

    LaunchedEffect(active.any { it.active }) {
        while (active.any { it.active }) { store.pollProgress(); delay(1000) }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("OFFLINE", style = Mono.copy(color = Hifi.Accent, fontSize = 11.sp, letterSpacing = 4.sp))
                Text("Descargas", style = Display.copy(fontSize = 30.sp))
                Spacer(Modifier.height(4.dp))
                Text("${songsLabel(done.size)} · ${formatBytes(totalBytes)}", color = Hifi.TextDim, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                StorageBar(totalBytes, free)
                Spacer(Modifier.height(16.dp))
                if (allSongs.isNotEmpty()) {
                    PlayActions(onPlay = { c.player.play(allSongs) }, onShuffle = { c.player.play(allSongs, shuffle = true) })
                }
            }
        }

        if (active.isNotEmpty()) {
            item {
                SectionHeader("Descargando · ${active.count { it.active }}") {
                    TextButton(onClick = { store.remove(active.map { it.song }) }) { Text("Cancelar todo", color = Hifi.TextDim) }
                }
            }
            items(active, key = { "a" + it.song.id }) { o -> ActiveRow(o) { store.remove(listOf(o.song)) } }
        }

        if (done.isEmpty() && active.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.DownloadForOffline, null, tint = Hifi.TextFaint, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Nada descargado todavía.\nToca ⬇ en un álbum, playlist o en Me gusta para escucharlo sin conexión.",
                        color = Hifi.TextDim, fontSize = 14.sp, textAlign = TextAlign.Center,
                    )
                }
            }
        } else if (albums.isNotEmpty()) {
            item { SectionHeader("Álbumes · ${albums.size}") }
        }

        albums.forEach { a ->
            item(key = "h" + a.key) { OfflineAlbumRow(a, playback.current?.id) }
        }
    }
}

@Composable
private fun StorageBar(used: Long, free: Long) {
    val frac = if (used + free > 0) used.toFloat() / (used + free) else 0f
    val anim by animateFloatAsState(frac, label = "storage")
    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Hifi.PanelHi)) {
        Box(Modifier.fillMaxWidth(anim.coerceAtLeast(0.01f)).fillMaxHeight().background(Hifi.Accent))
    }
    Spacer(Modifier.height(4.dp))
    Text("${formatBytes(used)} usados · ${formatBytes(free)} libres en el teléfono", style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp))
}

@Composable
private fun ActiveRow(o: OfflineSong, onCancel: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        CoverArt(o.song.coverArt, Modifier.size(40.dp), size = 120, corner = 6.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(o.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            Spacer(Modifier.height(5.dp))
            if (o.failed) {
                Text("Falló la descarga", color = Hifi.LedRed, fontSize = 12.sp)
            } else {
                LinearProgressIndicator(
                    progress = { (o.percent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                    color = Hifi.Accent, trackColor = Hifi.Line,
                )
            }
        }
        IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancelar", tint = Hifi.TextFaint) }
    }
}

@Composable
private fun OfflineAlbumRow(a: OfflineAlbum, currentId: String?) {
    val c = LocalApp.current
    var open by rememberSaveable(a.key) { mutableStateOf(false) }
    val rot by animateFloatAsState(if (open) 180f else 0f, label = "chev")
    val songs = a.songs.map { it.song }
    Column {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverArt(a.coverArt, Modifier.size(58.dp), size = 400, corner = 8.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(a.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(a.artist ?: "", color = Hifi.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${songsLabel(a.songs.size)} · ${formatBytes(a.songs.sumOf { it.bytes })}", style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp))
            }
            Icon(Icons.Default.ExpandMore, null, tint = Hifi.TextFaint, modifier = Modifier.rotate(rot))
            IconButton(onClick = { c.player.play(songs) }) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Hifi.Accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                }
            }
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(start = 8.dp).background(Hifi.Panel.copy(alpha = 0.5f), RoundedCornerShape(8.dp))) {
                songs.forEachIndexed { i, s ->
                    TrackRow(s, s.track?.toString(), currentId == s.id, { c.player.play(songs, i) }, showArtist = s.artist != a.artist)
                }
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Quitar el álbum de descargas", color = Hifi.TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 16.dp))
                    DownloadButton(songs)
                }
            }
        }
    }
}
