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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.data.CustomCovers
import com.marco.audiopro.data.subsonic.Album
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.platform.supportsBlur
import com.marco.audiopro.ui.BrowseRoute
import com.marco.audiopro.ui.DownloadsRoute
import com.marco.audiopro.ui.LikedRoute
import com.marco.audiopro.ui.SettingsRoute
import com.marco.audiopro.ui.components.AlbumCard
import com.marco.audiopro.ui.components.AlbumMenu
import com.marco.audiopro.ui.components.ArrowRow
import com.marco.audiopro.ui.components.CoverArt
import com.marco.audiopro.ui.components.LikedCover
import com.marco.audiopro.ui.components.LoadBox
import com.marco.audiopro.ui.components.RemotePlaybackBanner
import com.marco.audiopro.ui.components.SectionHeader
import com.marco.audiopro.ui.components.bouncyClick
import com.marco.audiopro.ui.components.formatDuration
import com.marco.audiopro.ui.components.glass
import com.marco.audiopro.ui.components.onRightClick
import com.marco.audiopro.ui.components.rememberLoad
import com.marco.audiopro.ui.components.songsLabel
import com.marco.audiopro.ui.openAlbum
import com.marco.audiopro.ui.openArtist
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Un artista de tu biblioteca que escuchas mucho. */
private data class TopArtist(val id: String, val name: String, val cover: String?)

/** Inicio = sólo TU biblioteca (lo de fuera vive en Descubrir). */
private data class HomeData(
    val recent: List<Album>,
    val frequent: List<Album>,
    val newest: List<Album>,
    val starred: List<Album>,
    val rediscover: List<Album>,
    val random: List<Album>,
    val topArtists: List<TopArtist>,
    /** Tus géneros con más álbumes, con una portada de cada uno. */
    val genres: List<Pair<String, String?>>,
    /** Décadas que hay en tu biblioteca (1990, 2000…). */
    val decades: List<Int>,
)

private suspend fun loadHome(api: SubsonicApi): HomeData = coroutineScope {
    val frequent = async { api.albumList("frequent", 40) }
    val recent = async { api.albumList("recent", 30) }
    val newest = async { api.albumList("newest", 20) }
    val starred = async { api.albumList("starred", 20) }
    val random = async { api.albumList("random", 20) }
    val f = frequent.await()
    val r = recent.await()
    val recentIds = r.mapTo(HashSet()) { it.id }
    val top = f.filter { it.artistId != null }.groupBy { it.artistId!! }
        .entries.sortedByDescending { (_, al) -> al.sumOf { it.playCount ?: 0 } }
        .take(12).map { (id, al) -> TopArtist(id, al.first().artist ?: "", al.first().coverArt) }
    val g = runCatching { api.genres() }.getOrDefault(emptyList()).filter { it.albumCount > 0 }.sortedByDescending { it.albumCount }.take(8)
    val genres = g.map { genre ->
        async { genre.value to runCatching { api.albumList("byGenre", 1, 0, mapOf("genre" to genre.value)).firstOrNull()?.coverArt }.getOrNull() }
    }.map { it.await() }
    val all = f + r + newest.await() + starred.await() + random.await()
    val decades = all.mapNotNull { it.year?.takeIf { y -> y > 1900 } }.map { it / 10 * 10 }.distinct().sortedDescending()
    HomeData(r, f, newest.await(), starred.await(), f.filter { it.id !in recentIds }.take(20), random.await(), top, genres, decades)
}

@Composable
fun HomeScreen(nav: NavController) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    val liked by c.likes.songs.collectAsState()
    val store = c.downloads
    val offlineCount = store?.items?.collectAsState()?.value?.values?.count { it.done } ?: 0
    val loader = rememberLoad(Unit) { loadHome(c.api) }
    val (greeting, date) = remember {
        val now = Calendar.getInstance()
        val g = when (now.get(Calendar.HOUR_OF_DAY)) { in 5..11 -> "Buenos días"; in 12..18 -> "Buenas tardes"; else -> "Buenas noches" }
        g to SimpleDateFormat("EEEE d 'de' MMMM", Locale.forLanguageTag("es-MX")).format(now.time).uppercase()
    }
    fun playAlbum(id: String) = scope.launch { runCatching { c.api.album(id).song }.onSuccess { if (it.isNotEmpty()) c.player.play(it) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 700.dp
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(start = 22.dp, end = 10.dp, top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(date, style = Mono.copy(color = Hifi.TextDim, fontSize = 11.sp, letterSpacing = 1.5.sp))
                        Text(greeting, style = Display.copy(fontSize = 36.sp))
                    }
                    if (!c.isDesktop) {
                        IconButton(onClick = { nav.navigate(com.marco.audiopro.ui.LastFmRoute) }) { Icon(Icons.Default.Insights, "Mi Last.fm", tint = Hifi.TextDim) }
                        IconButton(onClick = { nav.navigate(SettingsRoute) }) { Icon(Icons.Default.Settings, "Ajustes", tint = Hifi.TextDim) }
                    }
                }
                if (!c.isDesktop) RemotePlaybackBanner(Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
            item {
                LoadBox(loader, Modifier.height(300.dp)) { home ->
                    Column {
                        // Destacados grandes, como "Top Picks" de Apple Music.
                        Spacer(Modifier.height(14.dp))
                        ArrowRow(contentPadding = PaddingValues(horizontal = 16.dp), spacing = 14.dp, arrowsOffset = 0.dp) {
                            item(key = "liked") {
                                FeatureCard(
                                    kicker = "TU MÚSICA", title = "Tus Me gusta",
                                    subtitle = "${songsLabel(liked.size)} · ${formatDuration(liked.sumOf { it.duration ?: 0 })}",
                                    wide = wide, cover = liked.firstOrNull()?.coverArt, art = { LikedCover(Modifier.fillMaxSize(), corner = 0.dp) },
                                    onClick = { nav.navigate(LikedRoute) },
                                    onPlay = { if (liked.isNotEmpty()) c.player.play(liked, shuffle = true) },
                                )
                            }
                            home.recent.firstOrNull()?.let { a ->
                                item(key = "last") {
                                    FeatureCard("LO ÚLTIMO QUE PUSISTE", a.name, a.artist ?: "", wide, a.coverArt, onClick = { nav.openAlbum(a.id) }, onPlay = { playAlbum(a.id) })
                                }
                            }
                            home.newest.firstOrNull()?.let { a ->
                                item(key = "new") {
                                    FeatureCard("NUEVO EN TU BIBLIOTECA", a.name, a.artist ?: "", wide, a.coverArt, onClick = { nav.openAlbum(a.id) }, onPlay = { playAlbum(a.id) })
                                }
                            }
                            home.frequent.firstOrNull()?.let { a ->
                                item(key = "top") {
                                    FeatureCard("TU MÁS ESCUCHADO", a.name, a.artist ?: "", wide, a.coverArt, onClick = { nav.openAlbum(a.id) }, onPlay = { playAlbum(a.id) })
                                }
                            }
                        }
                        if (store != null) {
                            Row(Modifier.padding(start = 16.dp, top = 14.dp)) {
                                Row(
                                    Modifier.bouncyClick { nav.navigate(DownloadsRoute) { launchSingleTop = true } }.glass(RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.DownloadForOffline, null, tint = Hifi.Accent, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Descargas · $offlineCount", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        if (home.recent.size > 1) {
                            SectionHeader("Volver a escuchar")
                            ShortcutGrid(home.recent.drop(1).take(if (wide) 8 else 6), columns = if (wide) 4 else 2, nav)
                        }
                        if (home.topArtists.isNotEmpty()) {
                            SectionHeader("Tus artistas")
                            ArrowRow(contentPadding = PaddingValues(horizontal = 16.dp), spacing = 16.dp) {
                                items(home.topArtists, key = { it.id }) { a -> ArtistCircle(a) { nav.openArtist(a.id) } }
                            }
                        }
                        Shelf("Agregados recientemente", home.newest, nav, big = true)
                        if (home.genres.isNotEmpty()) {
                            SectionHeader("Explora por género")
                            val cols = if (wide) 4 else 2
                            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                home.genres.chunked(cols).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        row.forEach { (name, cover) ->
                                            CategoryTile(name, null, cover, Modifier.weight(1f)) { nav.navigate(BrowseRoute(name, genre = name)) }
                                        }
                                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                        Shelf("Lo que más pones", home.frequent, nav)
                        Shelf("Tus favoritos", home.starred, nav)
                        if (home.decades.size > 1) {
                            SectionHeader("Por década")
                            ArrowRow(contentPadding = PaddingValues(horizontal = 16.dp), spacing = 12.dp, arrowsOffset = 0.dp) {
                                items(home.decades, key = { it }) { d ->
                                    val label = if (d >= 2000) "Los $d" else "Los ${d % 100}"
                                    CategoryTile(label, null, null, Modifier.width(170.dp)) { nav.navigate(BrowseRoute(label, fromYear = d, toYear = d + 9)) }
                                }
                            }
                        }
                        Shelf("Redescubre", home.rediscover, nav)
                        Shelf("Al azar de tu biblioteca", home.random, nav)
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta grande de destacados: la portada borrosa de fondo, la portada nítida encima y el
 * título abajo con un botón de play.
 */
@Composable
private fun FeatureCard(
    kicker: String, title: String, subtitle: String, wide: Boolean, cover: String?,
    art: (@Composable () -> Unit)? = null, onClick: () -> Unit, onPlay: () -> Unit,
) {
    val w = if (wide) 290.dp else 250.dp
    Column(Modifier.width(w)) {
        Text(kicker, style = Mono.copy(color = Hifi.TextDim, fontSize = 10.sp), modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Box(
            Modifier.width(w).height(w * 1.12f).shadow(24.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp))
                .background(Hifi.PanelHi).bouncyClick(onClick = onClick),
        ) {
            // Fondo: la misma portada, enorme y difuminada (el look de Apple Music).
            Box(Modifier.fillMaxSize().graphicsLayer { scaleX = 1.6f; scaleY = 1.6f }.then(if (supportsBlur) Modifier.blur(40.dp) else Modifier)) {
                if (art != null) art() else CoverArt(cover, Modifier.fillMaxSize(), size = 300, corner = 0.dp)
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))
            Box(Modifier.align(Alignment.TopCenter).padding(top = 22.dp).size(w * 0.6f).shadow(20.dp, RoundedCornerShape(12.dp))) {
                if (art != null) Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) { art() }
                else CoverArt(cover, Modifier.fillMaxSize(), size = 500, corner = 12.dp)
            }
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = Display.copy(fontSize = 19.sp, color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    Modifier.size(42.dp).shadow(10.dp, CircleShape).clip(CircleShape).background(Color.White).bouncyClick(onClick = onPlay),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.PlayArrow, "Reproducir", tint = Color.Black, modifier = Modifier.size(24.dp)) }
            }
        }
    }
}

@Composable
private fun ArtistCircle(a: TopArtist, onClick: () -> Unit) {
    Column(Modifier.width(118.dp).bouncyClick(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        CoverArt(a.cover, Modifier.size(110.dp).shadow(14.dp, CircleShape), size = 300, corner = 55.dp, customKey = CustomCovers.artist(a.id))
        Spacer(Modifier.height(8.dp))
        Text(a.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Shelf(title: String, albums: List<Album>, nav: NavController, big: Boolean = false) {
    if (albums.isEmpty()) return
    SectionHeader(title)
    AlbumRow(albums, big = big, nav = nav) { nav.openAlbum(it.id) }
}

/** Atajos compactos (portada + título), como los de arriba en Spotify. */
@Composable
private fun ShortcutGrid(albums: List<Album>, columns: Int, nav: NavController) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        albums.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { a ->
                    var menu by remember { mutableStateOf<DpOffset?>(null) }
                    Box(Modifier.weight(1f)) {
                        Row(
                            Modifier.fillMaxWidth().height(60.dp).onRightClick { menu = it }.bouncyClick { nav.openAlbum(a.id) }
                                .glass(RoundedCornerShape(12.dp), Color.White.copy(alpha = 0.07f)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CoverArt(a.coverArt, Modifier.size(60.dp), size = 200, corner = 0.dp)
                            Column(Modifier.padding(horizontal = 12.dp)) {
                                Text(a.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(a.artist ?: "", fontSize = 11.sp, color = Hifi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        AlbumMenu(a, menu != null, { menu = null }, nav, offset = menu ?: DpOffset.Zero)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun AlbumRow(albums: List<Album>, big: Boolean = false, nav: NavController? = null, onClick: (Album) -> Unit) {
    ArrowRow {
        items(albums, key = { it.id }) { a -> AlbumCard(a, { onClick(a) }, Modifier.width(if (big) 190.dp else 160.dp), nav = nav) }
    }
}
