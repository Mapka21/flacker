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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.marco.audiopro.core.AppGraph
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.data.meta.GenreEntry
import com.marco.audiopro.data.meta.GenreMap
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.ui.GenreRoute
import com.marco.audiopro.ui.RadioRoute
import com.marco.audiopro.ui.components.AlbumCard
import com.marco.audiopro.ui.components.ArtTile
import com.marco.audiopro.ui.components.CoverArt
import com.marco.audiopro.ui.components.CoverCollage
import com.marco.audiopro.ui.components.ScreenBackButton
import com.marco.audiopro.ui.components.SectionTitle
import com.marco.audiopro.ui.components.bouncyClick
import com.marco.audiopro.ui.openAlbum
import com.marco.audiopro.ui.openArtist
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import com.marco.audiopro.platform.supportsBlur
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Un mix de un género: canciones al azar de sus álbumes (hasta [size]). */
suspend fun genreMix(c: AppGraph, g: GenreEntry, size: Int = 60): List<Song> = coroutineScope {
    g.albums.shuffled().take(18).map { a -> async { runCatching { c.api.album(a.id).song }.getOrDefault(emptyList()) } }
        .awaitAll().flatten().shuffled().take(size)
}

/** Línea de "afinando": cuántos álbumes ya se revisaron en MusicBrainz. */
@Composable
private fun TuningLine(modifier: Modifier = Modifier) {
    val st by LocalApp.current.genres.state.collectAsState()
    if (st.toTune == 0 || st.tuned >= st.toTune) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Hifi.TextDim)
        Spacer(Modifier.width(8.dp))
        Text("Afinando géneros con MusicBrainz · ${st.tuned} de ${st.toTune} álbumes", style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp))
    }
}

/**
 * Géneros: arriba tus géneros más fuertes en grande; abajo, cada familia (Rock, Electrónica,
 * Regional mexicano…) con sus géneros en una fila. Todo ya corregido con MusicBrainz.
 */
@Composable
fun GenresScreen(nav: NavController) {
    val c = LocalApp.current
    val st by c.genres.state.collectAsState()
    LaunchedEffect(Unit) { runCatching { c.genres.load() } }
    val families = remember(st.genres) {
        st.genres.groupBy { it.family }.entries.sortedByDescending { e -> e.value.sumOf { it.albums.size } }.map { it.key to it.value }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 700.dp
        val tile = if (wide) 176.dp else 148.dp
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
            item {
                Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
                    Text("Géneros", style = Display.copy(fontSize = 34.sp))
                    Text(
                        if (!st.loaded) "Leyendo tu biblioteca…" else "${st.genres.size} géneros en tu música. Cada disco revisado con MusicBrainz para que esté donde va.",
                        fontSize = 13.sp, color = Hifi.TextDim,
                    )
                    TuningLine(Modifier.padding(top = 6.dp))
                }
            }
            // Los más grandes, en grande.
            val top = st.genres.take(if (wide) 4 else 2)
            if (top.isNotEmpty()) item {
                Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    top.forEach { g ->
                        ArtTile(g.name, "${g.albums.size} álbumes", g.covers, Modifier.weight(1f), kicker = g.family) { nav.navigate(GenreRoute(g.name)) }
                    }
                }
            }
            families.forEach { (fam, gs) ->
                item(key = "f$fam") {
                    Column(Modifier.padding(top = 22.dp)) {
                        SectionTitle(fam, Modifier.padding(horizontal = 20.dp))
                        Text("${gs.size} géneros · ${gs.sumOf { it.albums.size }} álbumes", style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp), modifier = Modifier.padding(start = 20.dp, bottom = 10.dp))
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(gs, key = { it.name }) { g ->
                                ArtTile(g.name, "${g.albums.size} álbumes", g.covers, Modifier.width(tile)) { nav.navigate(GenreRoute(g.name)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** La página de un género: portada de mosaico, mix, radio del género, artistas, lo que más pones y álbumes. */
@Composable
fun GenreScreen(name: String, nav: NavController) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    val st by c.genres.state.collectAsState()
    LaunchedEffect(Unit) { runCatching { c.genres.load() } }
    val g = st.get(name)
    var top by remember(name) { mutableStateOf<List<Song>>(emptyList()) }
    LaunchedEffect(g?.albums?.size) {
        val gg = g ?: return@LaunchedEffect
        top = coroutineScope {
            gg.albums.take(10).map { a -> async { runCatching { c.api.album(a.id).song }.getOrDefault(emptyList()) } }.awaitAll()
        }.flatten().filter { (it.playCount ?: 0) > 0 }.sortedByDescending { it.playCount }.take(8)
    }

    LazyVerticalGrid(
        GridCells.Adaptive(160.dp), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 30.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                // Portada: mosaico difuminado de fondo y el nombre encima.
                Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))) {
                    CoverCollage(g?.covers.orEmpty(), Modifier.fillMaxSize().then(if (supportsBlur) Modifier.blur(28.dp) else Modifier))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.25f), 1f to Hifi.Bg)))
                    Column(Modifier.statusBarsPadding()) { ScreenBackButton(nav) }
                    Row(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalAlignment = Alignment.Bottom) {
                        CoverCollage(g?.covers.orEmpty(), Modifier.size(132.dp).clip(RoundedCornerShape(14.dp)))
                        Spacer(Modifier.width(18.dp))
                        Column {
                            Text("GÉNERO · ${GenreMap.family(name).uppercase()}", style = Mono.copy(color = Color.White.copy(alpha = 0.7f), letterSpacing = 1.sp))
                            Text(name, style = Display.copy(fontSize = 46.sp, color = Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                g?.let { "${it.albums.size} álbumes · ${it.albums.mapNotNull { a -> a.artist }.distinct().size} artistas" } ?: "Cargando…",
                                fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
                Row(Modifier.padding(horizontal = 10.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    BigAction(Icons.Default.PlayArrow, "Mix", filled = true) { g?.let { scope.launch { val s = genreMix(c, it); if (s.isNotEmpty()) c.player.play(s) } } }
                    BigAction(Icons.Default.Shuffle, "Todo al azar") { g?.let { scope.launch { val s = genreMix(c, it, 200); if (s.isNotEmpty()) c.player.play(s, shuffle = true) } } }
                    BigAction(Icons.Default.Radio, "Radio") { nav.navigate(RadioRoute(query = name)) }
                }
                if (g != null) {
                    val artists = g.albums.filter { it.artistId != null }.groupBy { it.artistId!! }.entries.sortedByDescending { e -> e.value.sumOf { it.playCount ?: 0 } }.take(16)
                    if (artists.isNotEmpty()) {
                        SectionTitle("Artistas", Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp))
                        LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(artists, key = { it.key }) { (id, al) ->
                                Column(Modifier.width(100.dp).bouncyClick { nav.openArtist(id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                    CoverArt(al.first().coverArt, Modifier.size(92.dp).clip(CircleShape), size = 200, corner = 46.dp)
                                    Text(al.first().artist ?: "", fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                                    Text("${al.size} álbum${if (al.size == 1) "" else "es"}", fontSize = 11.sp, color = Hifi.TextFaint)
                                }
                            }
                        }
                    }
                }
                if (top.isNotEmpty()) {
                    SectionTitle("Lo que más pones", Modifier.padding(start = 10.dp, end = 10.dp, top = 22.dp, bottom = 6.dp))
                    top.forEachIndexed { i, s ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).bouncyClick { c.player.play(top, i) }.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${i + 1}", style = Display.copy(fontSize = 16.sp, color = Hifi.TextFaint), modifier = Modifier.width(28.dp))
                            CoverArt(s.coverArt, Modifier.size(44.dp), size = 120, corner = 8.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(s.artist ?: "", fontSize = 12.sp, color = Hifi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("${s.playCount}×", style = Mono.copy(color = Hifi.TextFaint, fontSize = 11.sp))
                        }
                    }
                }
                SectionTitle("Álbumes", Modifier.padding(start = 10.dp, end = 10.dp, top = 22.dp, bottom = 6.dp))
            }
        }
        items(g?.albums.orEmpty(), key = { it.id }) { a -> AlbumCard(a, { nav.openAlbum(a.id) }, nav = nav) }
    }
}

@Composable
private fun BigAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, filled: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (filled) Color.White else Color.White.copy(alpha = 0.10f))
            .bouncyClick(onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (filled) Color.Black else Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (filled) Color.Black else Color.White, maxLines = 1)
    }
}

/** Fila de Inicio: un mix por cada uno de tus géneros más grandes. */
@Composable
fun GenreMixesRow(nav: NavController) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    val st by c.genres.state.collectAsState()
    LaunchedEffect(Unit) { runCatching { c.genres.load() } }
    val list = st.genres.take(10)
    if (list.isEmpty()) return
    Column {
        SectionTitle("Mixes por género", Modifier.padding(start = 22.dp, end = 14.dp, top = 22.dp, bottom = 10.dp), "Ver géneros") { nav.navigate(com.marco.audiopro.ui.GenresRoute) }
        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.name }) { g ->
                ArtTile("Mix ${g.name}", "${g.albums.size} álbumes", g.covers, Modifier.width(156.dp), kicker = g.family) {
                    scope.launch { val s = genreMix(c, g); if (s.isNotEmpty()) c.player.play(s) }
                }
            }
        }
        TuningLine(Modifier.padding(start = 22.dp, top = 8.dp))
    }
}
