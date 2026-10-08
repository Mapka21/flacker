package com.marco.audiopro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.data.discover.Discoveries
import com.marco.audiopro.data.discover.DzAlbum
import com.marco.audiopro.data.discover.DzArtist
import com.marco.audiopro.data.discover.DzTrack
import com.marco.audiopro.data.discover.HiResStores
import com.marco.audiopro.data.discover.normName
import com.marco.audiopro.ui.components.Load
import com.marco.audiopro.ui.components.LoadBox
import com.marco.audiopro.ui.components.ScreenBackButton
import com.marco.audiopro.ui.components.SectionHeader
import com.marco.audiopro.ui.components.bouncyClick
import com.marco.audiopro.ui.components.formatDuration
import com.marco.audiopro.ui.components.glass
import com.marco.audiopro.ui.components.glassCard
import com.marco.audiopro.ui.components.rememberLoad
import com.marco.audiopro.ui.openAlbum
import com.marco.audiopro.ui.openArtist
import com.marco.audiopro.ui.openExternalAlbum
import com.marco.audiopro.ui.openExternalArtist
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ piezas

/** Imagen de internet (portadas y fotos del catálogo) con un hueco mientras carga. */
@Composable
fun NetImage(url: String?, modifier: Modifier = Modifier, corner: Dp = 10.dp) {
    Box(modifier.clip(RoundedCornerShape(corner)).background(Hifi.PanelHi), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Album, null, tint = Hifi.TextFaint, modifier = Modifier.fillMaxSize(0.4f))
        if (url != null) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun ExtAlbumCard(a: DzAlbum, nav: NavController, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.bouncyClick { nav.openExternalAlbum(a.id) }.padding(6.dp)) {
        NetImage(a.cover_xl ?: a.cover_medium, Modifier.fillMaxWidth().aspectRatio(1f).shadow(12.dp, RoundedCornerShape(10.dp)))
        Spacer(Modifier.height(8.dp))
        Text(a.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(
            subtitle ?: listOfNotNull(a.artist?.name, a.year).joinToString(" · "),
            maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = Hifi.TextDim,
        )
    }
}

@Composable
fun ExtArtistBubble(a: DzArtist, nav: NavController, caption: String? = null) {
    Column(Modifier.width(128.dp).bouncyClick { nav.openExternalArtist(a.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
        NetImage(a.picture_xl ?: a.picture_medium, Modifier.size(116.dp).shadow(14.dp, CircleShape), corner = 58.dp)
        Spacer(Modifier.height(8.dp))
        Text(a.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
        Text(caption ?: "Artista", fontSize = 11.sp, color = Hifi.TextDim, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ExtTrackRow(t: DzTrack, nav: NavController, index: Int? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .bouncyClick { t.album?.id?.let(nav::openExternalAlbum) ?: t.artist?.id?.let(nav::openExternalArtist) }
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) Text("$index", style = Mono.copy(color = Hifi.TextFaint), modifier = Modifier.width(26.dp))
        NetImage(t.album?.cover_medium, Modifier.size(46.dp), corner = 6.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(t.artist?.name, t.album?.title).joinToString(" · "), fontSize = 12.sp, color = Hifi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatDuration(t.duration), style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp))
    }
}

/** "Consíguelo en alta calidad": tiendas legales con FLAC / Hi-Res para este artista o disco. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HiResLinks(query: String, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    Column(modifier.fillMaxWidth().glassCard().padding(16.dp)) {
        Text("CONSÍGUELO EN ALTA CALIDAD", style = Mono.copy(color = Hifi.Text, fontWeight = FontWeight.Bold))
        Text("Tiendas con FLAC / Hi-Res. Al comprarlo, guárdalo en la carpeta de Navidrome y aparece en tu biblioteca.", fontSize = 12.sp, color = Hifi.TextDim)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HiResStores.all.forEach { s ->
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f))
                        .bouncyClick { runCatching { uri.openUri(s.url(query)) } }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(s.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(s.note, fontSize = 10.sp, color = Hifi.TextDim)
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Hifi.TextDim, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/** Secciones de recomendaciones (las usan Inicio, en corto, y Descubrir, completas). */
@Composable
fun DiscoverySections(d: Discoveries, nav: NavController, wide: Boolean, compact: Boolean) {
    if (d.artists.isNotEmpty()) {
        SectionHeader("Artistas que te podrían gustar")
        Text("No están en tu biblioteca · parecidos a lo que escuchas", fontSize = 12.sp, color = Hifi.TextDim, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(d.artists.take(if (compact) 12 else 24), key = { it.artist.id }) { r -> ExtArtistBubble(r.artist, nav, "Porque escuchas ${r.because}") }
        }
    }
    if (d.albums.isNotEmpty()) {
        SectionHeader("Álbumes para descubrir")
        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp)) {
            items(d.albums, key = { it.id }) { a -> ExtAlbumCard(a, nav, Modifier.width(170.dp)) }
        }
    }
    if (d.tracks.isNotEmpty()) {
        SectionHeader("Canciones para descubrir")
        val shown = if (compact) d.tracks.take(if (wide) 8 else 5) else d.tracks
        if (wide) {
            Row(Modifier.padding(horizontal = 4.dp)) {
                shown.chunked((shown.size + 1) / 2).forEach { col -> Column(Modifier.weight(1f)) { col.forEach { ExtTrackRow(it, nav) } } }
            }
        } else shown.forEach { ExtTrackRow(it, nav) }
    }
    for (g in d.missing.take(if (compact) 2 else 4)) {
        SectionHeader("Te faltan de ${g.artist}")
        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp)) {
            items(g.albums, key = { it.id }) { a -> ExtAlbumCard(a, nav, Modifier.width(160.dp), subtitle = a.year ?: "") }
        }
    }
}

// ------------------------------------------------------------------ Descubrir

@Composable
fun DiscoverScreen(nav: NavController) {
    val c = LocalApp.current
    var data by remember { mutableStateOf(c.discover.cached()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun load(force: Boolean) {
        loading = true; error = null
        scope.launch {
            runCatching { c.discover.recommendations(force) }.onSuccess { data = it }.onFailure { error = it.message }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 700.dp
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Descubrir", style = Display.copy(fontSize = 32.sp))
                        Text("Música que todavía no tienes, según lo que escuchas", fontSize = 13.sp, color = Hifi.TextDim)
                    }
                    Box(Modifier.size(40.dp).glass(CircleShape, Color.Black.copy(alpha = 0.25f)).bouncyClick { if (!loading) load(true) }, contentAlignment = Alignment.Center) {
                        if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = Hifi.Text, strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, "Nuevas recomendaciones", tint = Hifi.Text, modifier = Modifier.size(20.dp))
                    }
                }
                Text(
                    "Busca cualquier artista o disco en la barra de búsqueda: lo que no tengas aparece en \"Fuera de tu biblioteca\".",
                    fontSize = 12.sp, color = Hifi.TextFaint, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            val d = data
            when {
                d != null -> item { DiscoverySections(d, nav, wide, compact = false) }
                error != null -> item { Text("No se pudo armar: $error", color = Hifi.LedRed, modifier = Modifier.padding(20.dp)) }
                else -> item {
                    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Hifi.Accent, strokeWidth = 2.dp)
                        Spacer(Modifier.height(12.dp))
                        Text("Buscando música para ti…", color = Hifi.TextDim)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ artista del catálogo

@Composable
fun ExternalArtistScreen(id: Long, nav: NavController) {
    val c = LocalApp.current
    val loader = rememberLoad(id) {
        val a = c.discover.artist(id)
        val owned = c.discover.ownedAlbums(a.name)
        val lib = c.discover.libraryArtist(a.name)
        ExtArtistData(a, c.discover.top(id), c.discover.discography(id), c.discover.related(id), owned, lib?.id)
    }
    LoadBox(loader, Modifier.statusBarsPadding()) { d ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > 700.dp
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
                item {
                    Box(Modifier.fillMaxWidth().height(if (wide) 320.dp else 280.dp)) {
                        NetImage(d.artist.picture_xl, Modifier.fillMaxSize(), corner = 0.dp)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Hifi.Bg)))
                        ScreenBackButton(nav, Modifier.statusBarsPadding())
                        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                            Text(if (d.libraryId != null) "EN TU BIBLIOTECA" else "NO LO TIENES", style = Mono.copy(color = if (d.libraryId != null) Hifi.LedGreen else Hifi.Accent))
                            Text(d.artist.name, style = Display.copy(fontSize = 38.sp))
                            Text(
                                listOfNotNull(d.artist.nb_fan?.let { "%,d fans".format(it) }, d.artist.nb_album?.let { "$it lanzamientos" }).joinToString(" · "),
                                fontSize = 13.sp, color = Hifi.TextDim,
                            )
                        }
                    }
                }
                if (d.libraryId != null) item {
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 6.dp).clip(RoundedCornerShape(50)).background(Hifi.LedGreen.copy(alpha = 0.18f))
                            .bouncyClick { nav.openArtist(d.libraryId) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.LibraryMusic, null, tint = Hifi.LedGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Abrir en tu biblioteca (${d.owned.size} discos)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
                item { HiResLinks(d.artist.name, Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) }
                if (d.top.isNotEmpty()) {
                    item { SectionHeader("Populares") }
                    itemsIndexed(d.top, key = { _, t -> "t${t.id}" }) { i, t -> ExtTrackRow(t.copy(artist = t.artist ?: d.artist), nav, i + 1) }
                }
                val groups = listOf("album" to "Álbumes", "ep" to "EPs", "single" to "Sencillos")
                for ((type, title) in groups) {
                    val list = d.albums.filter { it.record_type == type }
                    if (list.isEmpty()) continue
                    item(key = "h$type") {
                        SectionHeader(title)
                        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp)) {
                            items(list, key = { it.id }) { a ->
                                val have = normName(a.title) in d.owned
                                ExtAlbumCard(a.copy(artist = d.artist), nav, Modifier.width(160.dp), subtitle = listOfNotNull(a.year, if (have) "✓ Lo tienes" else null).joinToString(" · "))
                            }
                        }
                    }
                }
                if (d.related.isNotEmpty()) item {
                    SectionHeader("Artistas parecidos")
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(d.related, key = { it.id }) { a -> ExtArtistBubble(a, nav) }
                    }
                }
            }
        }
    }
}

private data class ExtArtistData(
    val artist: DzArtist,
    val top: List<DzTrack>,
    val albums: List<DzAlbum>,
    val related: List<DzArtist>,
    val owned: Set<String>,
    val libraryId: String?,
)

// ------------------------------------------------------------------ álbum del catálogo

@Composable
fun ExternalAlbumScreen(id: Long, nav: NavController) {
    val c = LocalApp.current
    val loader = rememberLoad(id) {
        val a = c.discover.album(id)
        a to c.discover.libraryAlbumId(a.artist?.name ?: "", a.title)
    }
    LoadBox(loader, Modifier.statusBarsPadding()) { (a, libId) ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > 700.dp
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
                item {
                    ScreenBackButton(nav)
                    val header = @Composable {
                        NetImage(a.cover_xl, Modifier.size(if (wide) 240.dp else 220.dp).shadow(30.dp, RoundedCornerShape(12.dp)), corner = 12.dp)
                    }
                    val info = @Composable {
                        Column {
                            val kind = when (a.record_type) { "ep" -> "EP"; "single" -> "SENCILLO"; else -> "ÁLBUM" }
                            Text(
                                if (libId != null) "$kind · YA LO TIENES" else "$kind · NO LO TIENES",
                                style = Mono.copy(color = if (libId != null) Hifi.LedGreen else Hifi.Accent),
                            )
                            Text(a.title, style = Display.copy(fontSize = 30.sp))
                            Text(
                                a.artist?.name ?: "", fontSize = 16.sp, color = Hifi.Accent,
                                modifier = Modifier.bouncyClick { a.artist?.id?.let(nav::openExternalArtist) }.padding(vertical = 2.dp),
                            )
                            Text(
                                listOfNotNull(a.release_date, a.genres?.data?.firstOrNull()?.name, a.label, a.nb_tracks?.let { "$it pistas" }, a.duration?.let(::formatDuration))
                                    .joinToString(" · "),
                                fontSize = 13.sp, color = Hifi.TextDim,
                            )
                            if (libId != null) {
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    Modifier.clip(RoundedCornerShape(50)).background(Hifi.LedGreen.copy(alpha = 0.18f))
                                        .bouncyClick { nav.openAlbum(libId) }.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Hifi.LedGreen, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Abrir en tu biblioteca", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    if (wide) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.Bottom) { header(); Spacer(Modifier.width(24.dp)); info() }
                    } else {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) { header(); Spacer(Modifier.height(18.dp)); info() }
                    }
                }
                item { HiResLinks("${a.artist?.name ?: ""} ${a.title}", Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                val tracks = a.tracks?.data.orEmpty()
                if (tracks.isNotEmpty()) {
                    item { SectionHeader("Canciones") }
                    itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${t.track_position ?: (i + 1)}", style = Mono.copy(color = Hifi.TextFaint), modifier = Modifier.width(30.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                t.artist?.name?.takeIf { it != a.artist?.name }?.let { Text(it, fontSize = 12.sp, color = Hifi.TextDim) }
                            }
                            Text(formatDuration(t.duration), style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp))
                        }
                    }
                }
                item {
                    Text(
                        "Datos del catálogo de Deezer. Para escucharlo aquí, consíguelo y ponlo en tu biblioteca de Navidrome.",
                        fontSize = 11.sp, color = Hifi.TextFaint, modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ búsqueda: fuera de tu biblioteca

/** Resultados del catálogo para la búsqueda: lo que NO tienes queda marcado. */
@Composable
fun OutsideLibraryResults(query: String, nav: NavController) {
    val c = LocalApp.current
    val loader = rememberLoad(query) {
        val artists = c.discover.searchArtists(query).take(10)
        val albums = c.discover.searchAlbums(query).take(18)
        artists to albums
    }
    val res = (loader.state.value as? Load.Ok)?.value ?: return
    val (artists, albums) = res
    if (artists.isEmpty() && albums.isEmpty()) return
    SectionHeader("Fuera de tu biblioteca")
    Text("Del catálogo · tócalo para ver dónde conseguirlo en hi-res", fontSize = 12.sp, color = Hifi.TextDim, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
    if (artists.isNotEmpty()) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(artists, key = { "a${it.id}" }) { a -> ExtArtistBubble(a, nav) }
        }
    }
    if (albums.isNotEmpty()) {
        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp)) {
            items(albums, key = { "b${it.id}" }) { a -> ExtAlbumCard(a, nav, Modifier.width(160.dp)) }
        }
    }
}
