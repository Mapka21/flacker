package com.marco.audiopro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.marco.audiopro.core.AppGraph
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.data.discover.Discoveries
import com.marco.audiopro.data.discover.DzAlbum
import com.marco.audiopro.data.discover.DzArtist
import com.marco.audiopro.data.discover.DzTrack
import com.marco.audiopro.data.discover.normName
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.ui.components.ArrowRow
import com.marco.audiopro.ui.components.Load
import com.marco.audiopro.ui.components.LoadBox
import com.marco.audiopro.ui.components.ScreenBackButton
import com.marco.audiopro.ui.components.SectionHeader
import com.marco.audiopro.ui.components.bouncyClick
import com.marco.audiopro.ui.components.formatDuration
import com.marco.audiopro.ui.components.glass
import com.marco.audiopro.ui.components.rememberLoad
import com.marco.audiopro.ui.openAlbum
import com.marco.audiopro.ui.openArtist
import com.marco.audiopro.ui.openExternalAlbum
import com.marco.audiopro.ui.openExternalArtist
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ muestras de 30 s

/** Pone muestras de 30 s en el reproductor normal (las URLs se piden frescas al tocar). */
private class PreviewPlayer(private val c: AppGraph, private val scope: CoroutineScope) {
    var loading by mutableStateOf<Any?>(null)

    fun play(key: Any, startId: String? = null, build: suspend () -> List<Song>) {
        if (loading != null) return
        loading = key
        scope.launch {
            runCatching { build() }
                .onSuccess { songs ->
                    if (songs.isEmpty()) c.connect.notice.value = "No hay muestra disponible"
                    else c.player.play(songs, songs.indexOfFirst { it.id == startId }.coerceAtLeast(0))
                }
                .onFailure { c.connect.notice.value = "No se pudo cargar la muestra" }
            loading = null
        }
    }
}

@Composable
private fun rememberPreviewPlayer(): PreviewPlayer {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    return remember { PreviewPlayer(c, scope) }
}

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

/** Canción del catálogo: tocarla pone su muestra; el disco/artista se abre desde los textos. */
@Composable
private fun ExtTrackRow(t: DzTrack, nav: NavController, playing: Boolean, loading: Boolean, index: Int? = null, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (playing) Hifi.Accent.copy(alpha = 0.10f) else Color.Transparent)
            .bouncyClick(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) Text("$index", style = Mono.copy(color = Hifi.TextFaint), modifier = Modifier.width(26.dp))
        Box(contentAlignment = Alignment.Center) {
            NetImage(t.album?.cover_medium, Modifier.size(48.dp), corner = 8.dp)
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                when {
                    loading -> CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    playing -> Icon(Icons.Default.GraphicEq, null, tint = Hifi.Accent, modifier = Modifier.size(20.dp))
                    else -> Icon(Icons.Default.PlayArrow, "Escuchar muestra", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (playing) Hifi.Accent else Hifi.Text)
            Row {
                Text(
                    t.artist?.name ?: "", fontSize = 12.sp, color = Hifi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).bouncyClick { t.artist?.id?.let(nav::openExternalArtist) },
                )
                t.album?.title?.let {
                    Text(" · $it", fontSize = 12.sp, color = Hifi.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).bouncyClick { t.album.id.takeIf { id -> id > 0 }?.let(nav::openExternalAlbum) })
                }
            }
        }
        Text("MUESTRA", style = Mono.copy(color = Hifi.TextFaint, fontSize = 8.sp))
    }
}

@Composable
private fun TrackList(tracks: List<DzTrack>, nav: NavController, preview: PreviewPlayer, key: String, wide: Boolean, numbered: Boolean = false, build: suspend () -> List<Song>) {
    val current = LocalApp.current.player.state.collectAsState().value.current?.id
    val rows = tracks.mapIndexed { i, t ->
        @Composable {
            ExtTrackRow(t, nav, current == "dz:${t.id}", preview.loading == "$key:${t.id}", if (numbered) i + 1 else null) {
                preview.play("$key:${t.id}", "dz:${t.id}", build)
            }
        }
    }
    if (wide && tracks.size > 4) {
        Row(Modifier.padding(horizontal = 4.dp)) {
            rows.chunked((rows.size + 1) / 2).forEach { col -> Column(Modifier.weight(1f)) { col.forEach { it() } } }
        }
    } else Column { rows.forEach { it() } }
}

/** Botón "Escuchar muestras" (pone todas las del bloque, una tras otra). */
@Composable
private fun PlayAllButton(label: String, loading: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Hifi.Accent).bouncyClick(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
        else Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
    val preview = rememberPreviewPlayer()
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
                        Text("Rolas y artistas que no tienes, parecidos a lo que escuchas. Toca para oír 30 s.", fontSize = 13.sp, color = Hifi.TextDim)
                    }
                    Box(Modifier.size(40.dp).glass(CircleShape, Color.Black.copy(alpha = 0.25f)).bouncyClick { if (!loading) load(true) }, contentAlignment = Alignment.Center) {
                        if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = Hifi.Text, strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, "Nuevas recomendaciones", tint = Hifi.Text, modifier = Modifier.size(20.dp))
                    }
                }
            }
            val d = data
            when {
                d != null -> item { DiscoverySections(d, nav, wide, preview) }
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

@Composable
private fun DiscoverySections(d: Discoveries, nav: NavController, wide: Boolean, preview: PreviewPlayer) {
    val c = LocalApp.current
    // Lo primero: canciones para escuchar (lo que más importa para conocer música).
    if (d.tracks.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionHeader("Canciones para descubrir") }
            PlayAllButton("Escuchar todas", preview.loading == "all") { preview.play("all") { c.discover.previewSongs(d.tracks) } }
        }
        TrackList(d.tracks, nav, preview, "rec", wide) { c.discover.previewSongs(d.tracks) }
    }
    if (d.artists.isNotEmpty()) {
        SectionHeader("Artistas que te podrían gustar")
        ArrowRow(contentPadding = PaddingValues(horizontal = 16.dp), spacing = 14.dp) {
            items(d.artists, key = { it.artist.id }) { r -> ExtArtistBubble(r.artist, nav, "Porque escuchas ${r.because}") }
        }
    }
    if (d.albums.isNotEmpty()) {
        SectionHeader("Álbumes para descubrir")
        ArrowRow {
            items(d.albums, key = { it.id }) { a -> ExtAlbumCard(a, nav, Modifier.width(170.dp)) }
        }
    }
    for (g in d.missing) {
        SectionHeader("Lo que no tienes de ${g.artist}")
        ArrowRow {
            items(g.albums, key = { it.id }) { a -> ExtAlbumCard(a, nav, Modifier.width(160.dp), subtitle = a.year ?: "") }
        }
    }
}

// ------------------------------------------------------------------ artista del catálogo

@Composable
fun ExternalArtistScreen(id: Long, nav: NavController) {
    val c = LocalApp.current
    val preview = rememberPreviewPlayer()
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
                            Text(if (d.libraryId != null) "EN TU BIBLIOTECA" else "PARA DESCUBRIR", style = Mono.copy(color = if (d.libraryId != null) Hifi.LedGreen else Hifi.Accent))
                            Text(d.artist.name, style = Display.copy(fontSize = 38.sp))
                            Text(
                                listOfNotNull(d.artist.nb_fan?.let { "%,d fans".format(it) }, d.artist.nb_album?.let { "$it lanzamientos" }).joinToString(" · "),
                                fontSize = 13.sp, color = Hifi.TextDim,
                            )
                        }
                    }
                }
                item {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlayAllButton("Escuchar populares", preview.loading == "top") { preview.play("top") { c.discover.topPreviews(id) } }
                        if (d.libraryId != null) {
                            Spacer(Modifier.width(10.dp))
                            Row(
                                Modifier.clip(RoundedCornerShape(50)).background(Hifi.LedGreen.copy(alpha = 0.18f))
                                    .bouncyClick { nav.openArtist(d.libraryId) }.padding(horizontal = 16.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.LibraryMusic, null, tint = Hifi.LedGreen, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("En tu biblioteca (${d.owned.size})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
                if (d.top.isNotEmpty()) item {
                    SectionHeader("Populares")
                    TrackList(d.top.map { it.copy(artist = it.artist ?: d.artist) }, nav, preview, "top", wide, numbered = true) { c.discover.topPreviews(id) }
                }
                val groups = listOf("album" to "Álbumes", "ep" to "EPs", "single" to "Sencillos")
                for ((type, title) in groups) {
                    val list = d.albums.filter { it.record_type == type }
                    if (list.isEmpty()) continue
                    item(key = "h$type") {
                        SectionHeader(title)
                        ArrowRow {
                            items(list, key = { it.id }) { a ->
                                val have = normName(a.title) in d.owned
                                ExtAlbumCard(a.copy(artist = d.artist), nav, Modifier.width(160.dp), subtitle = listOfNotNull(a.year, if (have) "✓ Lo tienes" else null).joinToString(" · "))
                            }
                        }
                    }
                }
                if (d.related.isNotEmpty()) item {
                    SectionHeader("Artistas parecidos")
                    ArrowRow(contentPadding = PaddingValues(horizontal = 16.dp), spacing = 14.dp) {
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
    val preview = rememberPreviewPlayer()
    val loader = rememberLoad(id) {
        val a = c.discover.album(id)
        a to c.discover.libraryAlbumId(a.artist?.name ?: "", a.title)
    }
    LoadBox(loader, Modifier.statusBarsPadding()) { (a, libId) ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > 700.dp
            val tracks = a.tracks?.data.orEmpty().map { it.copy(album = it.album ?: a, artist = it.artist ?: a.artist) }
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
                                if (libId != null) "$kind · YA LO TIENES" else "$kind · PARA DESCUBRIR",
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
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PlayAllButton("Escuchar muestras", preview.loading == "album") { preview.play("album") { c.discover.albumPreviews(id) } }
                                if (libId != null) {
                                    Spacer(Modifier.width(10.dp))
                                    Row(
                                        Modifier.clip(RoundedCornerShape(50)).background(Hifi.LedGreen.copy(alpha = 0.18f))
                                            .bouncyClick { nav.openAlbum(libId) }.padding(horizontal = 16.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Default.CheckCircle, null, tint = Hifi.LedGreen, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Abrir en tu biblioteca", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
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
                if (tracks.isNotEmpty()) item {
                    SectionHeader("Canciones")
                    TrackList(tracks, nav, preview, "alb", wide = false, numbered = true) { c.discover.albumPreviews(id) }
                }
                item {
                    Text(
                        "Muestras de 30 s del catálogo de Deezer: para conocer el disco antes de tenerlo.",
                        fontSize = 11.sp, color = Hifi.TextFaint, modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ búsqueda: fuera de tu biblioteca

/** Resultados del catálogo para la búsqueda: lo que NO tienes, para conocerlo. */
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
    Text("Del catálogo · tócalo para conocerlo y oír muestras", fontSize = 12.sp, color = Hifi.TextDim, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
    if (artists.isNotEmpty()) {
        ArrowRow(contentPadding = PaddingValues(horizontal = 16.dp), spacing = 14.dp) {
            items(artists, key = { "a${it.id}" }) { a -> ExtArtistBubble(a, nav) }
        }
    }
    if (albums.isNotEmpty()) {
        ArrowRow {
            items(albums, key = { "b${it.id}" }) { a -> ExtAlbumCard(a, nav, Modifier.width(160.dp)) }
        }
    }
}
