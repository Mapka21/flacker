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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.data.subsonic.Album
import com.marco.audiopro.ui.BrowseRoute
import com.marco.audiopro.ui.components.AlbumCard
import com.marco.audiopro.ui.components.CoverArt
import com.marco.audiopro.ui.components.GlassChip
import com.marco.audiopro.ui.components.ScreenBackButton
import com.marco.audiopro.ui.components.bouncyClick
import com.marco.audiopro.ui.openAlbum
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/** Colores vivos para las tarjetas de género (cada género siempre el mismo). */
private val tileColors = listOf(
    Color(0xFFE13300), Color(0xFF8D67AB), Color(0xFF1E3264), Color(0xFFE8115B), Color(0xFF148A08),
    Color(0xFFBA5D07), Color(0xFF503750), Color(0xFF0D73EC), Color(0xFFAF2896), Color(0xFF477D95),
    Color(0xFF27856A), Color(0xFFD84000),
)

fun tileColor(key: String): Color = tileColors[key.hashCode().absoluteValue % tileColors.size]

/**
 * Tarjeta de categoría como las de "Explorar" de Spotify / Apple Music: color sólido, nombre
 * grande y una portada inclinada asomándose en la esquina.
 */
@Composable
fun CategoryTile(title: String, subtitle: String?, cover: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val base = tileColor(title)
    Box(
        modifier.height(104.dp).shadow(12.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(base, lerp(base, Color.Black, 0.35f))))
            .bouncyClick(onClick = onClick),
    ) {
        if (cover != null) {
            CoverArt(
                cover, Modifier.align(Alignment.BottomEnd).padding(end = 0.dp).size(78.dp).rotate(22f).shadow(10.dp, RoundedCornerShape(6.dp)),
                size = 200, corner = 6.dp,
            )
        }
        Column(Modifier.padding(14.dp)) {
            Text(title, style = Display.copy(fontSize = 18.sp, color = Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

/** Álbumes de tu biblioteca de un género o una década, con orden elegible. */
@Composable
fun BrowseScreen(route: BrowseRoute, nav: NavController) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    val albums = remember(route) { mutableStateListOf<Album>() }
    var end by remember(route) { mutableStateOf(false) }
    var error by remember(route) { mutableStateOf<String?>(null) }
    var page by remember(route) { mutableStateOf(0) }
    val grid = rememberLazyGridState()
    val extra = buildMap<String, Any?> {
        route.genre?.let { put("genre", it) }
        route.fromYear?.let { put("fromYear", it) }
        route.toYear?.let { put("toYear", it) }
    }
    val type = if (route.genre != null) "byGenre" else "byYear"
    LaunchedEffect(route, page) {
        if (end) return@LaunchedEffect
        runCatching { c.api.albumList(type, 60, albums.size, extra) }
            .onSuccess { albums += it.filter { a -> albums.none { it.id == a.id } }; if (it.size < 60) end = true }
            .onFailure { error = it.message }
    }
    val nearEnd by remember { derivedStateOf { (grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= grid.layoutInfo.totalItemsCount - 12 } }
    LaunchedEffect(nearEnd, albums.size) { if (nearEnd && albums.isNotEmpty() && !end) page++ }

    val color = tileColor(route.title)
    LazyVerticalGrid(
        GridCells.Adaptive(160.dp), state = grid,
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(0f to color.copy(alpha = 0.55f), 0.35f to Color.Transparent)),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 24.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding()) {
                ScreenBackButton(nav)
                Text(if (route.genre != null) "GÉNERO" else "DÉCADA", style = Mono.copy(color = Color.White.copy(alpha = 0.7f)), modifier = Modifier.padding(start = 10.dp, top = 12.dp))
                Text(route.title, style = Display.copy(fontSize = 40.sp), modifier = Modifier.padding(start = 10.dp))
                Text(
                    if (end) "${albums.size} álbumes en tu biblioteca" else "${albums.size} álbumes…",
                    fontSize = 13.sp, color = Hifi.TextDim, modifier = Modifier.padding(start = 10.dp, bottom = 10.dp),
                )
                Row(Modifier.padding(start = 10.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("Aleatorio", false) {
                        scope.launch {
                            val songs = albums.shuffled().take(12).mapNotNull { runCatching { c.api.album(it.id).song }.getOrNull() }.flatten()
                            if (songs.isNotEmpty()) c.player.play(songs, shuffle = true)
                        }
                    }
                }
                error?.let { Text(it, color = Hifi.LedRed, modifier = Modifier.padding(10.dp)) }
            }
        }
        items(albums, key = { it.id }) { a -> AlbumCard(a, { nav.openAlbum(a.id) }, nav = nav) }
    }
}
