package com.marco.audiopro.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.data.discover.normName
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.download.OfflineSong
import com.marco.audiopro.ui.components.CoverArt
import com.marco.audiopro.ui.components.DownloadButton
import com.marco.audiopro.ui.components.PlayActions
import com.marco.audiopro.ui.components.SectionHeader
import com.marco.audiopro.ui.components.SegmentedControl
import com.marco.audiopro.ui.components.TrackRow
import com.marco.audiopro.ui.components.bouncyClick
import com.marco.audiopro.ui.components.formatBytes
import com.marco.audiopro.ui.components.songsLabel
import com.marco.audiopro.ui.components.textInput
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Fonts
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import kotlinx.coroutines.delay

private data class OfflineAlbum(val key: String, val name: String, val artist: String?, val coverArt: String?, val year: Int?, val songs: List<OfflineSong>)

/**
 * "Sin conexión": tu otra biblioteca, la que vive en el teléfono. Buscador, pestañas de Álbumes
 * (cuadrícula), Artistas y Canciones, cada álbum abre su lista, y arriba lo que se está bajando.
 * Todo sale de lo descargado: funciona sin internet.
 */
@Composable
fun DownloadsScreen() {
    val c = LocalApp.current
    val store = c.downloads ?: return
    val items by store.items.collectAsState()
    val playback by c.player.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(0) }
    var openAlbum by rememberSaveable { mutableStateOf<String?>(null) }
    var openArtist by rememberSaveable { mutableStateOf<String?>(null) }

    val done = remember(items) { items.values.filter { it.done } }
    val active = remember(items) { items.values.filter { it.active || it.failed } }
    // Búsqueda sin acentos ni mayúsculas, en título, artista y álbum.
    val q = normName(query)
    val matching = remember(done, q) {
        if (q.isBlank()) done else done.filter { o ->
            listOfNotNull(o.song.title, o.song.artist, o.song.album).any { normName(it).contains(q) }
        }
    }
    val albums = remember(matching) {
        matching.groupBy { it.song.albumId ?: it.song.album ?: "?" }
            .map { (k, list) ->
                val sorted = list.sortedWith(compareBy({ it.song.discNumber ?: 1 }, { it.song.track ?: 0 }))
                val s = sorted.first().song
                OfflineAlbum(k, s.album ?: "Sin álbum", s.artist, s.coverArt, s.year, sorted)
            }
            .sortedBy { it.name.lowercase() }
    }
    val artists = remember(matching) {
        matching.groupBy { it.song.artist ?: "Desconocido" }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }
    val allSongs = remember(albums) { albums.flatMap { a -> a.songs.map { it.song } } }
    val totalBytes = done.sumOf { it.bytes }
    val free = remember { store.freeBytes() }

    LaunchedEffect(active.any { it.active }) {
        while (active.any { it.active }) { store.pollProgress(); delay(1000) }
    }

    // Un álbum o artista abierto se ve como su propia pantalla dentro de "Sin conexión".
    val album = openAlbum?.let { k -> albums.firstOrNull { it.key == k } }
    if (album != null) { OfflineAlbumPage(album, playback.current?.id) { openAlbum = null }; return }
    val artistSongs = openArtist?.let { artists[it] }
    if (openArtist != null && artistSongs != null) {
        OfflineSongsPage(openArtist!!, artistSongs.map { it.song }, playback.current?.id) { openArtist = null }
        return
    }

    LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 24.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding().padding(horizontal = 10.dp, vertical = 12.dp)) {
                Text("SIN CONEXIÓN", style = Mono.copy(color = Hifi.Accent, fontSize = 11.sp, letterSpacing = 1.sp))
                Text("Tu música en el teléfono", style = Display.copy(fontSize = 28.sp))
                Spacer(Modifier.height(4.dp))
                Text(
                    "${songsLabel(done.size)} · ${albums.size} álbum${if (albums.size == 1) "" else "es"} · ${formatBytes(totalBytes)}",
                    color = Hifi.TextDim, fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                StorageBar(totalBytes, free)
                if (done.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    SearchPill(query, { query = it })
                    Spacer(Modifier.height(12.dp))
                    PlayActions(onPlay = { c.player.play(allSongs) }, onShuffle = { c.player.play(allSongs, shuffle = true) })
                    Spacer(Modifier.height(12.dp))
                    SegmentedControl(listOf("Álbumes", "Artistas", "Canciones"), tab, { tab = it }, Modifier.fillMaxWidth())
                }
            }
        }

        if (active.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ActiveDownloads(active) { store.remove(it) }
            }
        }

        if (done.isEmpty() && active.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.DownloadForOffline, null, tint = Hifi.TextFaint, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Nada descargado todavía.\nToca ⬇ en un álbum, playlist o en Me gusta para escucharlo sin conexión.",
                        color = Hifi.TextDim, fontSize = 14.sp, textAlign = TextAlign.Center,
                    )
                }
            }
        } else if (done.isNotEmpty() && matching.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Nada descargado coincide con “$query”.", color = Hifi.TextDim, modifier = Modifier.padding(20.dp))
            }
        } else when (tab) {
            0 -> items(albums, key = { "al" + it.key }) { a -> OfflineAlbumCard(a) { openAlbum = a.key } }
            1 -> items(artists.keys.toList(), key = { "ar$it" }, span = { GridItemSpan(maxLineSpan) }) { name ->
                val list = artists.getValue(name)
                OfflineArtistRow(name, list) { openArtist = name }
            }
            else -> items(allSongs.sortedBy { it.title.lowercase() }, key = { "s" + it.id }, span = { GridItemSpan(maxLineSpan) }) { s ->
                val sorted = allSongs.sortedBy { it.title.lowercase() }
                TrackRow(s, null, playback.current?.id == s.id, { c.player.play(sorted, sorted.indexOf(s)) }, showCover = true)
            }
        }
    }
}

@Composable
private fun SearchPill(query: String, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.10f)).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, null, tint = Hifi.TextDim, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Buscar en tus descargas", color = Hifi.TextFaint, fontSize = 14.sp)
            BasicTextField(
                query, onChange, singleLine = true,
                textStyle = TextStyle(color = Hifi.Text, fontSize = 14.sp, fontFamily = Fonts.inter), cursorBrush = SolidColor(Hifi.Text),
                modifier = Modifier.fillMaxWidth().textInput(),
            )
        }
        if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, "Borrar", tint = Hifi.TextDim) }
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

/** Lo que se está bajando, plegable para que no estorbe. */
@Composable
private fun ActiveDownloads(active: List<OfflineSong>, onCancel: (List<Song>) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val rot by animateFloatAsState(if (open) 180f else 0f, label = "chev")
    val running = active.filter { it.active }
    val avg = if (running.isEmpty()) 1f else running.sumOf { it.percent.toDouble() }.toFloat() / running.size / 100f
    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f))) {
        Row(Modifier.fillMaxWidth().bouncyClick { open = !open }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (running.isNotEmpty()) "Descargando ${running.size}" else "${active.size} con error",
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { avg.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                    color = Hifi.Accent, trackColor = Hifi.Line,
                )
            }
            Icon(Icons.Default.ExpandMore, null, tint = Hifi.TextFaint, modifier = Modifier.padding(start = 10.dp).rotate(rot))
        }
        AnimatedVisibility(open) {
            Column {
                active.forEach { o -> ActiveRow(o) { onCancel(listOf(o.song)) } }
                TextButton(onClick = { onCancel(active.map { it.song }) }, modifier = Modifier.padding(start = 6.dp)) { Text("Cancelar todo", color = Hifi.TextDim) }
            }
        }
    }
}

@Composable
private fun ActiveRow(o: OfflineSong, onCancel: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        CoverArt(o.song.coverArt, Modifier.size(36.dp), size = 120, corner = 6.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(o.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
            if (o.failed) Text("Falló la descarga", color = Hifi.LedRed, fontSize = 11.sp)
            else Text("%.0f %%".format(o.percent), style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp))
        }
        IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancelar", tint = Hifi.TextFaint) }
    }
}

@Composable
private fun OfflineAlbumCard(a: OfflineAlbum, onClick: () -> Unit) {
    val c = LocalApp.current
    Column(Modifier.padding(6.dp).bouncyClick(onClick = onClick)) {
        Box {
            CoverArt(a.coverArt, Modifier.fillMaxWidth().aspectRatio(1f).shadow(12.dp, RoundedCornerShape(10.dp)), size = 400, corner = 10.dp)
            Box(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).size(38.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(Color.White)
                    .bouncyClick { c.player.play(a.songs.map { it.song }) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.PlayArrow, "Reproducir", tint = Color.Black, modifier = Modifier.size(22.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(
            listOfNotNull(a.artist, songsLabel(a.songs.size)).joinToString(" · "),
            maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = Hifi.TextDim,
        )
    }
}

@Composable
private fun OfflineArtistRow(name: String, songs: List<OfflineSong>, onClick: () -> Unit) {
    val albums = songs.mapNotNull { it.song.album }.distinct().size
    Row(Modifier.fillMaxWidth().bouncyClick(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CoverArt(songs.first().song.coverArt, Modifier.size(52.dp), size = 150, corner = 26.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${songsLabel(songs.size)} · $albums álbum${if (albums == 1) "" else "es"}", fontSize = 12.sp, color = Hifi.TextDim)
        }
    }
}

/** Un álbum descargado: portada, botones y sus canciones; quitarlo de descargas abajo. */
@Composable
private fun OfflineAlbumPage(a: OfflineAlbum, currentId: String?, onBack: () -> Unit) {
    val c = LocalApp.current
    val songs = a.songs.map { it.song }
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.statusBarsPadding().fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth()) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } }
                CoverArt(a.coverArt, Modifier.size(220.dp).shadow(24.dp, RoundedCornerShape(14.dp)), size = 600, corner = 14.dp)
                Spacer(Modifier.height(14.dp))
                Text(a.name, style = Display.copy(fontSize = 24.sp), textAlign = TextAlign.Center)
                Text(listOfNotNull(a.artist, a.year?.toString()).joinToString(" · "), color = Hifi.TextDim, fontSize = 14.sp)
                Text("${songsLabel(songs.size)} · ${formatBytes(a.songs.sumOf { it.bytes })} · sin conexión", style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp))
                Spacer(Modifier.height(14.dp))
                PlayActions(onPlay = { c.player.play(songs) }, onShuffle = { c.player.play(songs, shuffle = true) })
            }
        }
        items(songs.size) { i ->
            val s = songs[i]
            TrackRow(s, s.track?.toString(), currentId == s.id, { c.player.play(songs, i) }, showArtist = s.artist != a.artist)
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Quitar el álbum de descargas", color = Hifi.TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 16.dp))
                DownloadButton(songs)
            }
        }
    }
}

/** Canciones descargadas de un artista. */
@Composable
private fun OfflineSongsPage(title: String, songs: List<Song>, currentId: String?, onBack: () -> Unit) {
    val c = LocalApp.current
    val sorted = remember(songs) { songs.sortedWith(compareBy({ it.album ?: "" }, { it.discNumber ?: 1 }, { it.track ?: 0 })) }
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.statusBarsPadding().padding(16.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
                Text(title, style = Display.copy(fontSize = 28.sp), modifier = Modifier.padding(start = 4.dp))
                Text("${songsLabel(sorted.size)} descargadas", color = Hifi.TextDim, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(12.dp))
                PlayActions(onPlay = { c.player.play(sorted) }, onShuffle = { c.player.play(sorted, shuffle = true) })
            }
        }
        items(sorted.size) { i ->
            val s = sorted[i]
            TrackRow(s, null, currentId == s.id, { c.player.play(sorted, i) }, showCover = true, showArtist = false)
        }
    }
}
