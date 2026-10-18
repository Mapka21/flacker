package com.marco.audiopro.ui.screens

import androidx.compose.foundation.background
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
import com.marco.audiopro.ui.components.CoverArt
import com.marco.audiopro.ui.components.GlassChip
import com.marco.audiopro.ui.components.ScreenBackButton
import com.marco.audiopro.ui.components.bouncyClick
import com.marco.audiopro.ui.openAlbum
import com.marco.audiopro.ui.openArtist
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
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
        Text("Afinando géneros con MusicBrainz… ${st.tuned} de ${st.toTune} álbumes", style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp))
    }
}

/**
 * Géneros: todos los de tu biblioteca, ya corregidos (un disco de shoegaze ya no sale como "Rock"),
 * agrupados por familia. Cada uno abre su página.
 */
@Composable
fun GenresScreen(nav: NavController) {
    val c = LocalApp.current
    val st by c.genres.state.collectAsState()
    LaunchedEffect(Unit) { runCatching { c.genres.load() } }
    var family by remember { mutableStateOf<String?>(null) }
    val families = remember(st.genres) { st.genres.groupBy { it.family }.entries.sortedByDescending { e -> e.value.sumOf { it.albums.size } }.map { it.key } }
    val shown = st.genres.filter { family == null || it.family == family }

    LazyVerticalGrid(
        GridCells.Adaptive(180.dp), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding().padding(top = 18.dp)) {
                Text("Géneros", style = Display.copy(fontSize = 34.sp))
                Text(
                    if (!st.loaded) "Leyendo tu biblioteca…" else "${st.genres.size} géneros en tu música, revisados con MusicBrainz para que cada disco esté donde va.",
                    fontSize = 13.sp, color = Hifi.TextDim,
                )
                TuningLine(Modifier.padding(top = 6.dp))
                LazyRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { GlassChip("Todos", family == null) { family = null } }
                    items(families) { f -> GlassChip(f, family == f) { family = if (family == f) null else f } }
                }
            }
        }
        items(shown, key = { it.name }) { g ->
            CategoryTile(g.name, "${g.albums.size} álbumes · ${g.family}", g.covers.firstOrNull(), Modifier.fillMaxWidth()) { nav.navigate(GenreRoute(g.name)) }
        }
    }
}

/** La página de un género: mix, radio del género, artistas, álbumes y lo más escuchado. */
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
        // Lo más escuchado: de los discos que más pones de este género.
        top = coroutineScope {
            gg.albums.take(10).map { a -> async { runCatching { c.api.album(a.id).song }.getOrDefault(emptyList()) } }.awaitAll()
        }.flatten().filter { (it.playCount ?: 0) > 0 }.sortedByDescending { it.playCount }.take(10)
    }
    val color = tileColor(name)

    LazyVerticalGrid(
        GridCells.Adaptive(160.dp),
        Modifier.fillMaxSize().background(Brush.verticalGradient(0f to color.copy(alpha = 0.6f), 0.4f to Color.Transparent)),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 28.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding()) {
                ScreenBackButton(nav)
                Text("GÉNERO · ${GenreMap.family(name).uppercase()}", style = Mono.copy(color = Color.White.copy(alpha = 0.7f)), modifier = Modifier.padding(start = 10.dp, top = 10.dp))
                Text(name, style = Display.copy(fontSize = 44.sp), modifier = Modifier.padding(start = 10.dp))
                Text(
                    g?.let { "${it.albums.size} álbumes · ${it.albums.mapNotNull { a -> a.artist }.distinct().size} artistas" } ?: "Cargando…",
                    fontSize = 13.sp, color = Hifi.TextDim, modifier = Modifier.padding(start = 10.dp),
                )
                Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    BigAction(Icons.Default.PlayArrow, "Mix $name", filled = true) {
                        g?.let { scope.launch { val s = genreMix(c, it); if (s.isNotEmpty()) c.player.play(s) } }
                    }
                    BigAction(Icons.Default.Shuffle, "Todo al azar") {
                        g?.let { scope.launch { val s = genreMix(c, it, 200); if (s.isNotEmpty()) c.player.play(s, shuffle = true) } }
                    }
                    BigAction(Icons.Default.Radio, "Radio $name") { nav.navigate(RadioRoute(query = name)) }
                }
                if (g != null) {
                    val artists = g.albums.filter { it.artistId != null }.groupBy { it.artistId!! }.entries.sortedByDescending { e -> e.value.sumOf { it.playCount ?: 0 } }.take(16)
                    if (artists.isNotEmpty()) {
                        Text("Artistas", style = Display.copy(fontSize = 20.sp), modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp))
                        LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(artists, key = { it.key }) { (id, al) ->
                                Column(Modifier.width(96.dp).bouncyClick { nav.openArtist(id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                    CoverArt(al.first().coverArt, Modifier.size(88.dp).clip(CircleShape), size = 200, corner = 44.dp)
                                    Text(al.first().artist ?: "", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                }
                            }
                        }
                    }
                }
                if (top.isNotEmpty()) {
                    Text("Lo que más pones", style = Display.copy(fontSize = 20.sp), modifier = Modifier.padding(start = 10.dp, top = 18.dp, bottom = 4.dp))
                    top.forEachIndexed { i, s ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).bouncyClick { c.player.play(top, i) }.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${i + 1}", style = Mono.copy(color = Hifi.TextFaint), modifier = Modifier.width(24.dp))
                            CoverArt(s.coverArt, Modifier.size(40.dp), size = 120, corner = 6.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(s.artist ?: "", fontSize = 12.sp, color = Hifi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("${s.playCount} veces", style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp))
                        }
                    }
                }
                Text("Álbumes", style = Display.copy(fontSize = 20.sp), modifier = Modifier.padding(start = 10.dp, top = 18.dp, bottom = 4.dp))
            }
        }
        items(g?.albums.orEmpty(), key = { it.id }) { a -> AlbumCard(a, { nav.openAlbum(a.id) }, nav = nav) }
    }
}

@Composable
private fun BigAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, filled: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (filled) Color.White else Color.White.copy(alpha = 0.12f))
            .bouncyClick(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (filled) Color.Black else Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (filled) Color.Black else Color.White, maxLines = 1)
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
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Mixes por género", style = Display.copy(fontSize = 22.sp), modifier = Modifier.weight(1f))
            Text("Ver géneros", fontSize = 13.sp, color = Hifi.TextDim, modifier = Modifier.clip(RoundedCornerShape(8.dp)).bouncyClick { nav.navigate(com.marco.audiopro.ui.GenresRoute) }.padding(6.dp))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.name }) { g ->
                Column(Modifier.width(150.dp).bouncyClick { scope.launch { val s = genreMix(c, g); if (s.isNotEmpty()) c.player.play(s) } }) {
                    Box(Modifier.size(150.dp).clip(RoundedCornerShape(14.dp)).background(tileColor(g.name))) {
                        // Collage de 4 portadas con el nombre encima.
                        val cv = g.covers
                        Column {
                            Row { cv.getOrNull(0)?.let { CoverArt(it, Modifier.size(75.dp), size = 200, corner = 0.dp) }; cv.getOrNull(1)?.let { CoverArt(it, Modifier.size(75.dp), size = 200, corner = 0.dp) } }
                            Row { cv.getOrNull(2)?.let { CoverArt(it, Modifier.size(75.dp), size = 200, corner = 0.dp) }; cv.getOrNull(3)?.let { CoverArt(it, Modifier.size(75.dp), size = 200, corner = 0.dp) } }
                        }
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))))
                        Text("Mix\n${g.name}", style = Display.copy(fontSize = 18.sp, color = Color.White), modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
                    }
                    Text("${g.albums.size} álbumes", fontSize = 12.sp, color = Hifi.TextDim, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        TuningLine(Modifier.padding(start = 22.dp, top = 6.dp))
        Spacer(Modifier.height(4.dp))
    }
}
