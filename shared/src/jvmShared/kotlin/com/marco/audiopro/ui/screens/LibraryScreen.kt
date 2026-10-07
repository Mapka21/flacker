package com.marco.audiopro.ui.screens

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
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
import com.marco.audiopro.ui.components.LoadBox
import com.marco.audiopro.ui.components.formatDuration
import com.marco.audiopro.ui.components.rememberLoad
import com.marco.audiopro.ui.openAlbum
import com.marco.audiopro.ui.openArtist
import com.marco.audiopro.ui.openPlaylist
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.components.songsLabel
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(nav: NavController, initialTab: Int = 0) {
    var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Biblioteca", style = Display.copy(fontSize = 30.sp), modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp))
        SecondaryTabRow(
            selectedTabIndex = tab, containerColor = Hifi.Bg, contentColor = Hifi.Accent,
            indicator = { TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tab), color = Hifi.Accent) },
            divider = {},
        ) {
            listOf("Álbumes", "Artistas", "Playlists").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }, unselectedContentColor = Hifi.TextDim)
            }
        }
        when (tab) {
            0 -> AlbumsGrid(nav)
            1 -> ArtistsList(nav)
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

@Composable
private fun AlbumsGrid(nav: NavController) {
    val c = LocalApp.current
    var sort by rememberSaveable { mutableStateOf("alphabeticalByName") }
    val albums = remember(sort) { mutableStateListOf<Album>() }
    var end by remember(sort) { mutableStateOf(false) }
    var error by remember(sort) { mutableStateOf<String?>(null) }
    var page by remember(sort) { mutableStateOf(0) }
    val grid = rememberLazyGridState()

    LaunchedEffect(sort, page) {
        if (end) return@LaunchedEffect
        runCatching { c.api.albumList(sort, 60, albums.size) }
            .onSuccess { albums += it.filter { a -> albums.none { it.id == a.id } }; if (it.size < 60 || sort == "random") end = true }
            .onFailure { error = it.message }
    }
    val nearEnd by remember {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= grid.layoutInfo.totalItemsCount - 12
        }
    }
    LaunchedEffect(nearEnd, albums.size) { if (nearEnd && albums.isNotEmpty() && !end) page++ }

    Column {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sorts) { (key, label) ->
                FilterChip(
                    selected = sort == key, onClick = { sort = key }, label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Hifi.AccentDim, selectedLabelColor = Hifi.Cream),
                )
            }
        }
        if (error != null && albums.isEmpty()) {
            ErrorBox(error!!, { error = null; page++ })
        } else {
            LazyVerticalGrid(GridCells.Adaptive(150.dp), state = grid, contentPadding = PaddingValues(10.dp)) {
                itemsIndexed(albums, key = { _, a -> a.id }) { _, a -> AlbumCard(a, { nav.openAlbum(a.id) }) }
            }
        }
    }
}

@Composable
private fun ArtistsList(nav: NavController) {
    val c = LocalApp.current
    val loader = rememberLoad(Unit) { c.api.artists() }
    LoadBox(loader) { artists ->
        val grouped = remember(artists) { artists.groupBy { it.name.firstOrNull()?.uppercaseChar()?.takeIf { ch -> ch.isLetter() } ?: '#' }.toSortedMap() }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            grouped.forEach { (letter, list) ->
                item(key = "h$letter") {
                    Text(letter.toString(), style = Mono.copy(color = Hifi.Accent, fontSize = 13.sp), modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp))
                }
                items(list, key = { it.id }) { ArtistRow(it) { nav.openArtist(it.id) } }
            }
        }
    }
}

@Composable
fun ArtistRow(a: Artist, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CoverArt(a.coverArt, Modifier.size(48.dp), size = 150, corner = 24.dp)
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
    val loader = rememberLoad(Unit) { c.api.playlists() }
    val liked by c.likes.songs.collectAsState()
    LoadBox(loader) { lists ->
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            item {
                Row(Modifier.fillMaxWidth().clickable { nav.navigate(LikedRoute) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(Hifi.Accent, Hifi.AccentDim))),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Favorite, null, tint = Color.White) }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Me gusta", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(songsLabel(liked.size), fontSize = 12.sp, color = Hifi.TextDim)
                    }
                }
            }
            if (lists.isEmpty()) {
                item { Text("No hay playlists en el servidor", color = Hifi.TextDim, modifier = Modifier.padding(20.dp)) }
            }
            items(lists, key = { it.id }) { p ->
                Row(Modifier.fillMaxWidth().clickable { nav.openPlaylist(p.id) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp)) {
                        CoverArt(p.coverArt, Modifier.fillMaxSize(), size = 150)
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = Hifi.Accent,
                            modifier = Modifier.align(Alignment.BottomEnd).background(Hifi.Bg.copy(alpha = 0.7f), CircleShape).padding(2.dp).size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(p.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${songsLabel(p.songCount ?: 0)} · ${formatDuration(p.duration)}", fontSize = 12.sp, color = Hifi.TextDim)
                    }
                }
            }
        }
    }
}
