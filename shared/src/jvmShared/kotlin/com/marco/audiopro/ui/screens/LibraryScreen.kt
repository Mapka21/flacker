package com.marco.audiopro.ui.screens

import com.marco.audiopro.ui.components.ArtistImage
import com.marco.audiopro.ui.components.textInput
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.marco.audiopro.core.LocalApp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.marco.audiopro.data.subsonic.Album
import com.marco.audiopro.data.subsonic.Artist
import com.marco.audiopro.ui.LikedRoute
import com.marco.audiopro.ui.components.AlbumCard
import com.marco.audiopro.ui.components.CoverArt
import com.marco.audiopro.ui.components.ErrorBox
import com.marco.audiopro.ui.components.GlassChip
import com.marco.audiopro.ui.components.LikedCover
import com.marco.audiopro.ui.components.NewPlaylistFlow
import com.marco.audiopro.ui.components.TrackRow
import com.marco.audiopro.data.CustomCovers
import com.marco.audiopro.data.subsonic.Song
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Add
import com.marco.audiopro.ui.components.SegmentedControl
import com.marco.audiopro.ui.components.LoadBox
import com.marco.audiopro.ui.components.bouncyClick
import com.marco.audiopro.ui.components.formatDuration
import com.marco.audiopro.ui.components.rememberLoad
import com.marco.audiopro.ui.openAlbum
import com.marco.audiopro.ui.openArtist
import com.marco.audiopro.ui.openPlaylist
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.components.songsLabel
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.marco.audiopro.ui.components.AlphabetIndex
import com.marco.audiopro.ui.components.LibraryShuffle
import com.marco.audiopro.ui.components.glass
import com.marco.audiopro.ui.components.rememberRetained
import com.marco.audiopro.ui.theme.Fonts
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(nav: NavController, initialTab: Int = 0) {
    var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    val c = LocalApp.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Biblioteca", style = Display.copy(fontSize = 32.sp), modifier = Modifier.weight(1f))
            GlassChip("Aleatorio · toda tu música", false) { LibraryShuffle.start(c) }
            // En el celular, las descargas viven aquí.
            c.downloads?.let { store ->
                Spacer(Modifier.width(8.dp))
                val done = store.items.collectAsState().value.values.count { it.done }
                GlassChip("Descargas · $done", false) { nav.navigate(com.marco.audiopro.ui.DownloadsRoute) { launchSingleTop = true } }
            }
        }
        // Control segmentado de vidrio (en lugar de la barra de pestañas de Material).
        SegmentedControl(
            listOf("Álbumes", "Artistas", "Canciones", "Playlists"), tab, { tab = it },
            Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp).fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        when (tab) {
            0 -> AlbumsGrid(nav)
            1 -> ArtistsGrid(nav)
            2 -> SongsTable(nav)
            else -> PlaylistsList(nav)
        }
    }
}

private val sorts = listOf(
    "alphabeticalByName" to "A-Z",
    "alphabeticalByArtist" to "Artista",
    "newest" to "Recientes",
    "frequent" to "Más oídos",
    "recent" to "Últimos oídos",
    "starred" to "Favoritos",
    "random" to "Al azar",
)

/** Estado de una lista por páginas que se conserva al salir y regresar. */
private class Pager<T> {
    val items: SnapshotStateList<T> = mutableStateListOf()
    var end by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var page by mutableStateOf(0)
}

@Composable
private fun AlbumsGrid(nav: NavController) {
    val c = LocalApp.current
    var sort by rememberSaveable { mutableStateOf("alphabeticalByName") }
    val pager = rememberRetained(sort) { Pager<Album>() }
    val albums = pager.items
    val grid = rememberLazyGridState()

    LaunchedEffect(sort, pager.page) {
        if (pager.end || (pager.page == 0 && albums.isNotEmpty())) return@LaunchedEffect
        runCatching { c.api.albumList(sort, 60, albums.size) }
            .onSuccess { albums += it.filter { a -> albums.none { it.id == a.id } }; if (it.size < 60 || sort == "random") pager.end = true }
            .onFailure { pager.error = it.message }
    }
    val nearEnd by remember {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= grid.layoutInfo.totalItemsCount - 12
        }
    }
    LaunchedEffect(nearEnd, albums.size) { if (nearEnd && albums.isNotEmpty() && !pager.end) pager.page++ }

    Column {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sorts) { (key, label) ->
                GlassChip(label, sort == key) { sort = key }
            }
        }
        if (pager.error != null && albums.isEmpty()) {
            ErrorBox(pager.error!!, { pager.error = null; pager.page++ })
        } else {
            LazyVerticalGrid(GridCells.Adaptive(150.dp), state = grid, contentPadding = PaddingValues(10.dp)) {
                itemsIndexed(albums, key = { _, a -> a.id }) { _, a -> AlbumCard(a, { nav.openAlbum(a.id) }, nav = nav) }
            }
        }
    }
}

private fun initialOf(name: String): Char =
    java.text.Normalizer.normalize(name.trim(), java.text.Normalizer.Form.NFD).firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'

/** Artistas en cuadrícula con su foto (como Apple Music), por letra y con índice A-Z al lado. */
@Composable
private fun ArtistsGrid(nav: NavController) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoad(Unit) { c.api.artists() }
    LoadBox(loader) { artists ->
        val sections = remember(artists) { artists.groupBy { initialOf(it.name) }.toSortedMap(compareBy { if (it == '#') ' ' else it }) }
        // Índice de la cabecera de cada letra dentro de la cuadrícula.
        val headerAt = remember(sections) {
            var i = 0
            sections.mapValues { (_, list) -> i.also { i += 1 + list.size } }
        }
        val grid = rememberLazyGridState()
        Row(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                GridCells.Adaptive(140.dp), state = grid, modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 4.dp, bottom = 24.dp),
            ) {
                sections.forEach { (letter, list) ->
                    item(key = "h$letter", span = { GridItemSpan(maxLineSpan) }) {
                        Text(letter.toString(), style = Display.copy(fontSize = 24.sp, color = Hifi.Accent), modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 6.dp))
                    }
                    items(list, key = { it.id }) { a -> ArtistTile(a) { nav.openArtist(a.id) } }
                }
            }
            AlphabetIndex(sections.keys.toList()) { l -> headerAt[l]?.let { scope.launch { grid.scrollToItem(it) } } }
        }
    }
}

@Composable
private fun ArtistTile(a: Artist, onClick: () -> Unit) {
    Column(Modifier.padding(8.dp).bouncyClick(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        ArtistImage(a.id, a.name, a.coverArt, Modifier.fillMaxWidth().aspectRatio(1f).shadow(14.dp, CircleShape), size = 300, corner = 200.dp)
        Spacer(Modifier.height(8.dp))
        Text(a.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        a.albumCount?.let { Text("$it álbum${if (it == 1) "" else "es"}", fontSize = 12.sp, color = Hifi.TextDim) }
    }
}

@Composable
fun ArtistRow(a: Artist, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().bouncyClick(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ArtistImage(a.id, a.name, a.coverArt, Modifier.size(48.dp), size = 150, corner = 24.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(a.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            a.albumCount?.let { Text("$it álbum${if (it == 1) "" else "es"}", fontSize = 12.sp, color = Hifi.TextDim) }
        }
    }
}

@Composable
private fun PlaylistsList(nav: NavController) {
    val c = LocalApp.current
    val lists by c.playlists.lists.collectAsState()
    val liked by c.likes.songs.collectAsState()
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { c.playlists.refresh() }.onFailure { error = it.message }; loaded = true }
    if (creating) NewPlaylistFlow(nav) { creating = false }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Row(Modifier.fillMaxWidth().bouncyClick { creating = true }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, null, tint = Hifi.Text)
                }
                Spacer(Modifier.width(14.dp))
                Text("Nueva playlist", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().bouncyClick { nav.navigate(LikedRoute) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                LikedCover(Modifier.size(52.dp), corner = 8.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Me gusta", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(songsLabel(liked.size), fontSize = 12.sp, color = Hifi.TextDim)
                }
            }
        }
        if (error != null && lists.isEmpty()) item { Text("No se pudieron cargar: $error", color = Hifi.LedRed, modifier = Modifier.padding(20.dp)) }
        else if (loaded && lists.isEmpty()) item { Text("Todavía no tienes playlists.", color = Hifi.TextDim, modifier = Modifier.padding(20.dp)) }
        items(lists, key = { it.id }) { p ->
            Row(Modifier.fillMaxWidth().bouncyClick { nav.openPlaylist(p.id) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverArt(p.coverArt, Modifier.size(52.dp), size = 150, customKey = CustomCovers.playlist(p.id))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(p.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${songsLabel(p.songCount ?: 0)} · ${formatDuration(p.duration)}", fontSize = 12.sp, color = Hifi.TextDim)
                }
            }
        }
    }
}

private val songSorts = listOf("title" to "Título", "artist" to "Artista", "album" to "Álbum", "plays" to "Más oídas")

/**
 * Todas tus canciones como la vista "Canciones" de Apple Music: tabla con título, artista,
 * álbum y duración en la PC; filtro instantáneo, orden e índice A-Z para no bajar a ciegas.
 * Se cargan todas en segundo plano (por páginas) y se recuerdan al salir y regresar.
 */
@Composable
private fun SongsTable(nav: NavController) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    val pager = rememberRetained("songs") { Pager<Song>() }
    val songs = pager.items
    var sort by rememberSaveable { mutableStateOf("title") }
    var filter by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    val playback by c.player.state.collectAsState()
    LaunchedEffect(Unit) {
        while (!pager.end) {
            runCatching { c.api.allSongs(songs.size, 500) }
                .onSuccess { page -> songs += page.filter { s -> songs.none { it.id == s.id } }; if (page.size < 500) pager.end = true }
                .onFailure { pager.error = it.message; return@LaunchedEffect }
        }
    }
    val shown = remember(songs.size, sort, filter) {
        val f = filter.trim().lowercase()
        val base = if (f.isEmpty()) songs.toList() else songs.filter { s ->
            s.title.lowercase().contains(f) || (s.artist ?: "").lowercase().contains(f) || (s.album ?: "").lowercase().contains(f)
        }
        when (sort) {
            "artist" -> base.sortedWith(compareBy({ (it.artist ?: "").lowercase() }, { (it.album ?: "").lowercase() }, { it.discNumber ?: 1 }, { it.track ?: 0 }))
            "album" -> base.sortedWith(compareBy({ (it.album ?: "").lowercase() }, { it.discNumber ?: 1 }, { it.track ?: 0 }))
            "plays" -> base.sortedByDescending { it.playCount ?: 0 }
            else -> base.sortedBy { it.title.lowercase() }
        }
    }
    val keyOf: (Song) -> String = { s -> when (sort) { "artist" -> s.artist ?: ""; "album" -> s.album ?: ""; else -> s.title } }

    if (pager.error != null && songs.isEmpty()) { ErrorBox(pager.error!!, { pager.error = null; pager.end = false }); return }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 760.dp
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Filtro instantáneo dentro de tus canciones.
                Row(
                    Modifier.weight(1f).height(38.dp).glass(RoundedCornerShape(50), Color.Black.copy(alpha = 0.25f)).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, null, tint = Hifi.TextDim, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (filter.isEmpty()) Text("Filtrar ${fmtCount(songs.size)} canciones…", color = Hifi.TextFaint, fontSize = 13.sp)
                        BasicTextField(
                            filter, { filter = it }, singleLine = true, cursorBrush = SolidColor(Hifi.Accent),
                            textStyle = TextStyle(color = Hifi.Text, fontSize = 13.sp, fontFamily = Fonts.inter), modifier = Modifier.fillMaxWidth().textInput(),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Hifi.Accent).bouncyClick { if (shown.isNotEmpty()) c.player.play(shown, shuffle = true) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Shuffle, "Aleatorio", tint = Color.Black, modifier = Modifier.size(18.dp)) }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                items(songSorts) { (k, label) -> GlassChip(label, sort == k) { sort = k } }
                item {
                    Text(
                        if (pager.end) "${fmtCount(shown.size)} canciones" else "cargando… ${fmtCount(songs.size)}",
                        style = Mono.copy(color = Hifi.TextFaint), modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            if (wide) {
                // Encabezado de la tabla.
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 52.dp, top = 10.dp, bottom = 4.dp)) {
                    Spacer(Modifier.width(58.dp))
                    Text("TÍTULO", style = Mono.copy(color = Hifi.TextFaint, fontSize = 9.sp), modifier = Modifier.weight(1f))
                    Text("ÁLBUM", style = Mono.copy(color = Hifi.TextFaint, fontSize = 9.sp), modifier = Modifier.weight(0.8f))
                    Text("⏱", style = Mono.copy(color = Hifi.TextFaint, fontSize = 9.sp))
                }
            }
            Row(Modifier.weight(1f)) {
                LazyColumn(state = list, modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                    itemsIndexed(shown, key = { _, s -> s.id }) { i, s ->
                        TrackRow(
                            s, null, playback.current?.id == s.id, { c.player.play(shown, i) },
                            showCover = true, showFormat = !wide, showAlbum = wide, onGoToAlbum = { s.albumId?.let(nav::openAlbum) },
                        )
                    }
                }
                if (sort != "plays" && filter.isBlank()) {
                    val letters = remember(shown) { shown.map { initialOf(keyOf(it)) }.distinct() }
                    AlphabetIndex(letters) { l ->
                        val idx = shown.indexOfFirst { initialOf(keyOf(it)) == l }
                        if (idx >= 0) scope.launch { list.scrollToItem(idx) }
                    }
                }
            }
        }
    }
}

private fun fmtCount(n: Int) = java.text.NumberFormat.getIntegerInstance().format(n)
