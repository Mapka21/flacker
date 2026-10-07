package com.marco.audiopro.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Equalizer
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.data.subsonic.Album
import com.marco.audiopro.ui.DownloadsRoute
import com.marco.audiopro.ui.DspRoute
import com.marco.audiopro.ui.LikedRoute
import com.marco.audiopro.ui.SettingsRoute
import com.marco.audiopro.ui.components.AlbumCard
import com.marco.audiopro.ui.components.CoverArt
import com.marco.audiopro.ui.components.FlackerLogo
import com.marco.audiopro.ui.components.LoadBox
import com.marco.audiopro.ui.components.RemotePlaybackBanner
import com.marco.audiopro.ui.components.SectionHeader
import com.marco.audiopro.ui.components.bouncyClick
import com.marco.audiopro.ui.components.formatDuration
import com.marco.audiopro.ui.components.glass
import com.marco.audiopro.ui.components.rememberLoad
import com.marco.audiopro.ui.components.songsLabel
import com.marco.audiopro.ui.openAlbum
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Calendar

private val homeSections = listOf(
    "newest" to "Agregados recientemente",
    "recent" to "Escuchados recientemente",
    "starred" to "Álbumes favoritos",
    "random" to "Redescubre",
)

@Composable
fun HomeScreen(nav: NavController) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    val liked by c.likes.songs.collectAsState()
    val store = c.downloads
    val offlineCount = store?.items?.collectAsState()?.value?.values?.count { it.done } ?: 0
    val loader = rememberLoad(Unit) {
        coroutineScope {
            val frequent = async { c.api.albumList("frequent", 12) }
            val rest = homeSections.map { (type, _) -> async { c.api.albumList(type, 20) } }
            frequent.await() to rest.awaitAll()
        }
    }
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Buenos días"
            in 12..18 -> "Buenas tardes"
            else -> "Buenas noches"
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 700.dp
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(start = 22.dp, end = 10.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        FlackerLogo(size = 12.sp, tagline = null)
                        Text(greeting, style = Display.copy(fontSize = 34.sp))
                    }
                    if (!c.isDesktop) IconButton(onClick = { nav.navigate(SettingsRoute) }) { Icon(Icons.Default.Settings, "Ajustes", tint = Hifi.TextDim) }
                }
                Spacer(Modifier.height(6.dp))
                if (!c.isDesktop) RemotePlaybackBanner(Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
            item {
                LikedHero(liked.map { it.coverArt }, liked.size, liked.sumOf { it.duration ?: 0 }, wide,
                    onOpen = { nav.navigate(LikedRoute) },
                    onPlay = { if (liked.isNotEmpty()) c.player.play(liked) },
                    onShuffle = { if (liked.isNotEmpty()) c.player.play(liked, shuffle = true) },
                )
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Chip("Mezcla de todo", Icons.Default.Shuffle) {
                            scope.launch { runCatching { c.api.randomSongs(80) }.onSuccess { c.player.play(it) } }
                        }
                    }
                    item { Chip("Radio Me gusta", Icons.Default.Radio) { if (liked.isNotEmpty()) c.player.play(liked, shuffle = true) } }
                    if (store != null) item { Chip("Descargas · $offlineCount", Icons.Outlined.DownloadForOffline) { nav.navigate(DownloadsRoute) { launchSingleTop = true } } }
                    item { Chip("Ecualizador", Icons.Default.Equalizer) { nav.navigate(DspRoute) { launchSingleTop = true } } }
                }
            }
            item {
                LoadBox(loader, Modifier.height(240.dp)) { (frequent, lists) ->
                    Column {
                        if (frequent.isNotEmpty()) {
                            SectionHeader("Más escuchados")
                            MostPlayedGrid(frequent.take(if (wide) 9 else 6), columns = if (wide) 3 else 2) { nav.openAlbum(it.id) }
                        }
                        homeSections.forEachIndexed { i, (_, title) ->
                            val albums = lists[i]
                            if (albums.isNotEmpty()) {
                                SectionHeader(title)
                                AlbumRow(albums, big = i == 0) { nav.openAlbum(it.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Banner "Tus Me gusta": degradado del acento con las portadas en abanico (como Resso / Apple). */
@Composable
private fun LikedHero(
    covers: List<String?>, count: Int, seconds: Int, wide: Boolean,
    onOpen: () -> Unit, onPlay: () -> Unit, onShuffle: () -> Unit,
) {
    val accent = Hifi.Accent
    Box(
        Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth().height(if (wide) 220.dp else 190.dp)
            .shadow(24.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(lerp(accent, Color.White, 0.08f), accent, lerp(accent, Color.Black, 0.55f))))
            .bouncyClick(onClick = onOpen),
    ) {
        // Portadas en abanico a la derecha.
        val fan = covers.filterNotNull().distinct().take(3)
        Box(Modifier.align(Alignment.CenterEnd).padding(end = if (wide) 40.dp else 14.dp)) {
            fan.reversed().forEachIndexed { i, art ->
                val k = fan.size - 1 - i
                CoverArt(
                    art,
                    Modifier.size(if (wide) 150.dp else 104.dp)
                        .offset(x = (-k * (if (wide) 46 else 30)).dp, y = (k * 6).dp)
                        .rotate(-8f + k * 8f)
                        .shadow(18.dp, RoundedCornerShape(16.dp)),
                    size = 400, corner = 16.dp,
                )
            }
        }
        Column(Modifier.align(Alignment.CenterStart).padding(start = 24.dp).fillMaxWidth(if (wide) 0.5f else 0.55f)) {
            Icon(Icons.Default.Favorite, null, tint = Color.White, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(8.dp))
            Text("Tus Me gusta", style = Display.copy(fontSize = if (wide) 34.sp else 26.sp, color = Color.White))
            Text(
                "${songsLabel(count)} · ${formatDuration(seconds)}",
                color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).shadow(12.dp, CircleShape).clip(CircleShape).background(Color.White).bouncyClick(onClick = onPlay), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, "Reproducir Me gusta", tint = accent, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)).bouncyClick(onClick = onShuffle), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Shuffle, "Aleatorio", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.bouncyClick(onClick = onClick).glass(RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Hifi.Accent, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Mosaicos compactos (portada + título), como "Most Played" de Resso o los atajos de Spotify. */
@Composable
private fun MostPlayedGrid(albums: List<Album>, columns: Int, onClick: (Album) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        albums.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { a ->
                    Row(
                        Modifier.weight(1f).bouncyClick { onClick(a) }.glass(RoundedCornerShape(14.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoverArt(a.coverArt, Modifier.size(56.dp), size = 200, corner = 0.dp)
                        Column(Modifier.padding(horizontal = 12.dp)) {
                            Text(a.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(a.artist ?: "", fontSize = 11.sp, color = Hifi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun AlbumRow(albums: List<Album>, big: Boolean = false, onClick: (Album) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp)) {
        items(albums, key = { it.id }) { a -> AlbumCard(a, { onClick(a) }, Modifier.width(if (big) 190.dp else 160.dp)) }
    }
}
