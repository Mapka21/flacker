package com.marco.audiopro.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.marco.audiopro.core.LocalApp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.marco.audiopro.data.info.LastFm
import com.marco.audiopro.data.CustomCovers
import com.marco.audiopro.ui.components.AlbumCard
import com.marco.audiopro.ui.components.CoverEditButton
import com.marco.audiopro.ui.components.ScreenBackButton
import com.marco.audiopro.ui.components.CoverArt
import com.marco.audiopro.ui.components.LoadBox
import com.marco.audiopro.ui.components.PlayActions
import com.marco.audiopro.ui.components.SectionHeader
import com.marco.audiopro.ui.components.rememberLoad
import com.marco.audiopro.ui.openAlbum
import com.marco.audiopro.ui.openArtist
import com.marco.audiopro.ui.theme.Hifi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun ArtistScreen(id: String, nav: NavController) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoad(id) {
        coroutineScope {
            val a = async { c.api.artist(id) }
            val i = async { c.api.artistInfo(id) }
            a.await() to i.await()
        }
    }
    LoadBox(loader, Modifier.statusBarsPadding()) { (artist, info) ->
        val albums = remember(artist) { artist.album.sortedByDescending { it.year ?: 0 } }
        var bioOpen by remember { mutableStateOf(false) }
        fun playAll(shuffle: Boolean) = scope.launch {
            val songs = coroutineScope { albums.map { async { c.api.album(it.id).song } }.awaitAll().flatten() }
            c.player.play(songs, shuffle = shuffle)
        }

        LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().aspectRatio(1.25f)) {
                    val img = info.largeImageUrl?.takeIf { it.isNotBlank() }
                    c.covers.map.collectAsState().value
                    val custom = c.covers.get(CustomCovers.artist(artist.id))
                    if (custom != null) {
                        AsyncImage(custom, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else if (img != null) {
                        AsyncImage(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        CoverArt(artist.coverArt, Modifier.fillMaxSize(), size = 800, corner = 0.dp)
                    }
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Hifi.Bg)))
                    ScreenBackButton(nav, Modifier.statusBarsPadding())
                    CoverEditButton(CustomCovers.artist(artist.id), Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(14.dp))
                    Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                        Text(artist.name, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp)
                        Text(if (albums.size == 1) "1 álbum" else "${albums.size} álbumes", color = Hifi.TextDim, fontSize = 13.sp)
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                PlayActions(
                    onPlay = { playAll(false) }, onShuffle = { playAll(true) },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) { PopularSongs(artist.name, nav) }
            val bio = info.biography?.let(LastFm::cleanLastFmText)?.takeIf { it.length > 20 }
            if (bio != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).animateContentSize()) {
                        Text(
                            bio, fontSize = 14.sp, lineHeight = 21.sp, color = Hifi.Text.copy(alpha = 0.85f),
                            maxLines = if (bioOpen) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { bioOpen = !bioOpen },
                        )
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Discografía") }
            items(albums, key = { it.id }) { a -> AlbumCard(a, { nav.openAlbum(a.id) }, Modifier.padding(horizontal = 4.dp), nav = nav) }
            val similar = info.similarArtist.filter { it.id.isNotBlank() }
            if (similar.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        SectionHeader("Similares en tu biblioteca")
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowItems(similar, key = { it.id }) { s ->
                                Column(Modifier.width(96.dp).clickable { nav.openArtist(s.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                    CoverArt(s.coverArt, Modifier.size(88.dp), size = 200, corner = 44.dp, customKey = CustomCovers.artist(s.id))
                                    Spacer(Modifier.height(6.dp))
                                    Text(s.name, fontSize = 12.sp, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "Populares" del artista (Last.fm vía Navidrome), como en Spotify. */
@Composable
private fun PopularSongs(artist: String, nav: NavController) {
    val c = LocalApp.current
    val loader = rememberLoad(artist) { c.api.topSongs(artist, 10) }
    val songs = (loader.state.value as? com.marco.audiopro.ui.components.Load.Ok)?.value.orEmpty()
    val playback by c.player.state.collectAsState()
    var expanded by remember(artist) { mutableStateOf(false) }
    if (songs.isEmpty()) return
    Column {
        SectionHeader("Populares")
        val shown = if (expanded) songs else songs.take(5)
        shown.forEachIndexed { i, s ->
            com.marco.audiopro.ui.components.TrackRow(
                s, (i + 1).toString(), playback.current?.id == s.id, { c.player.play(songs, i) },
                showCover = true, showArtist = false, showFormat = true, onGoToAlbum = { s.albumId?.let(nav::openAlbum) },
            )
        }
        if (songs.size > 5) {
            Text(
                if (expanded) "Ver menos" else "Ver más", color = Hifi.OnArt, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp).clickable { expanded = !expanded },
            )
        }
    }
}
