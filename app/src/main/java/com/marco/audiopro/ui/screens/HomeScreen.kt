package com.marco.audiopro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.marco.audiopro.container
import com.marco.audiopro.data.subsonic.Album
import com.marco.audiopro.ui.components.AlbumCard
import com.marco.audiopro.ui.components.CoverArt
import com.marco.audiopro.ui.components.LoadBox
import com.marco.audiopro.ui.components.SectionHeader
import com.marco.audiopro.ui.components.rememberCoverColor
import com.marco.audiopro.ui.components.rememberLoad
import com.marco.audiopro.ui.openAlbum
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import com.marco.audiopro.ui.viz.Visualizer
import com.marco.audiopro.ui.viz.VizMode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Calendar

private val homeSections = listOf(
    "newest" to "Agregados recientemente",
    "recent" to "Escuchados recientemente",
    "frequent" to "Los que más suenan",
    "starred" to "Álbumes favoritos",
    "random" to "Redescubre",
)

@Composable
fun HomeScreen(nav: NavController) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val liked by c.likes.songs.collectAsState()
    val offline by c.downloads.items.collectAsState()
    val loader = rememberLoad(Unit) {
        coroutineScope { homeSections.map { (type, _) -> async { c.api.albumList(type, 20) } }.awaitAll() }
    }
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Buenos días"
            in 12..18 -> "Buenas tardes"
            else -> "Buenas noches"
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("MARCO AUDIO PRO", style = Mono.copy(color = Hifi.Accent, fontSize = 11.sp, letterSpacing = 4.sp))
                    Text(greeting, style = Display.copy(fontSize = 32.sp))
                }
                IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, "Ajustes", tint = Hifi.TextDim) }
            }
            Visualizer(VizMode.SPECTRUM, Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 20.dp))
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Me gusta", "${liked.size} canciones", Icons.Default.Favorite, Modifier.weight(1f), highlight = true) {
                        nav.navigate("liked")
                    }
                    Tile("Descargas", "${offline.values.count { it.done }} sin conexión", Icons.Outlined.DownloadForOffline, Modifier.weight(1f)) {
                        nav.navigate("downloads") { launchSingleTop = true }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("Mezcla", "Toda tu biblioteca", Icons.Default.Shuffle, Modifier.weight(1f)) {
                        scope.launch { runCatching { c.api.randomSongs(80) }.onSuccess { c.player.play(it) } }
                    }
                    Tile("Radio Me gusta", "Tus favoritas al azar", Icons.Default.Radio, Modifier.weight(1f)) {
                        if (liked.isNotEmpty()) c.player.play(liked, shuffle = true)
                    }
                }
            }
        }
        item {
            LoadBox(loader, Modifier.height(240.dp)) { lists ->
                Column {
                    lists.first().firstOrNull()?.let { Hero(it) { nav.openAlbum(it.id) } }
                    homeSections.forEachIndexed { i, (_, title) ->
                        val albums = lists[i]
                        if (albums.isNotEmpty()) {
                            SectionHeader(title)
                            AlbumRow(albums) { nav.openAlbum(it.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(title: String, subtitle: String, icon: ImageVector, modifier: Modifier, highlight: Boolean = false, onClick: () -> Unit) {
    val bg = if (highlight) Brush.linearGradient(listOf(Hifi.AccentDim, Hifi.Panel)) else Brush.linearGradient(listOf(Hifi.PanelHi, Hifi.Panel))
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, Hifi.Line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Hifi.Accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Hifi.Accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, fontSize = 11.sp, color = Hifi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Destacado: lo más nuevo de la biblioteca, con el color de su portada. */
@Composable
private fun Hero(album: Album, onClick: () -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val tint by rememberCoverColor(album.coverArt, Hifi.PanelHi)
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(tint, Hifi.Panel)))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(album.coverArt, Modifier.size(110.dp), size = 400, corner = 10.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("NUEVO EN TU BIBLIOTECA", style = Mono.copy(color = Hifi.Cream, fontSize = 9.sp))
            Spacer(Modifier.height(4.dp))
            Text(album.name, style = Display.copy(fontSize = 20.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(album.artist ?: "", color = Hifi.TextDim, fontSize = 13.sp, maxLines = 1)
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Hifi.Accent).clickable {
                    scope.launch { runCatching { c.api.album(album.id).song }.onSuccess { c.player.play(it) } }
                },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.PlayArrow, "Reproducir", tint = Color.Black) }
        }
    }
}

@Composable
fun AlbumRow(albums: List<Album>, onClick: (Album) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp)) {
        items(albums, key = { it.id }) { a -> AlbumCard(a, { onClick(a) }, Modifier.width(150.dp)) }
    }
}
