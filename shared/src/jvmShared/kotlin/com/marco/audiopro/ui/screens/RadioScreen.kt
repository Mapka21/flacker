package com.marco.audiopro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.marco.audiopro.core.AppGraph
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.data.radio.RadioNiche
import com.marco.audiopro.data.radio.RadioNiches
import com.marco.audiopro.data.radio.Station
import com.marco.audiopro.data.subsonic.SubsonicJson
import com.marco.audiopro.ui.components.ArtTile
import com.marco.audiopro.ui.components.EqBars
import com.marco.audiopro.ui.components.GlyphArt
import com.marco.audiopro.ui.components.LiveBadge
import com.marco.audiopro.ui.components.PlayPauseIcon
import com.marco.audiopro.ui.components.ScreenBackButton
import com.marco.audiopro.ui.components.SectionTitle
import com.marco.audiopro.ui.components.StationLogo
import com.marco.audiopro.ui.components.bouncyClick
import com.marco.audiopro.ui.components.textInput
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

private fun favorites(c: AppGraph): List<Station> =
    runCatching { SubsonicJson.decodeFromString<List<Station>>(c.settings.getString("radio_favs") ?: "[]") }.getOrDefault(emptyList())

private fun saveFavorites(c: AppGraph, list: List<Station>) = c.settings.putString("radio_favs", SubsonicJson.encodeToString(list))

/**
 * Radio: estaciones en vivo de todo el mundo (radio-browser.info). Arriba lo que suena (si es
 * radio), el buscador y tus estaciones; abajo los nichos armados a mano, cada uno con su arte.
 * @param query búsqueda inicial (desde la página de un género: "Radio Shoegaze").
 */
@Composable
fun RadioScreen(nav: NavController, query: String? = null) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf(query.orEmpty()) }
    var niche by remember { mutableStateOf<RadioNiche?>(null) }
    var stations by remember { mutableStateOf<List<Station>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var favs by remember { mutableStateOf(favorites(c)) }
    val st = c.player.state.collectAsState().value
    val playing = st.current?.takeIf { it.isRadio }

    LaunchedEffect(q, niche) {
        val n = niche
        if (n == null && q.isBlank()) { stations = emptyList(); return@LaunchedEffect }
        if (n == null) delay(450)
        loading = true
        stations = runCatching { if (n != null) c.radio.niche(n) else c.radio.find(q.trim()) }.getOrDefault(emptyList())
        loading = false
    }
    fun play(s: Station, list: List<Station>) {
        c.player.play(list.map { it.toSong() }, list.indexOf(s).coerceAtLeast(0))
        scope.launch { c.radio.click(s.stationuuid) }
    }
    fun toggleFav(s: Station) {
        favs = if (favs.any { it.stationuuid == s.stationuuid }) favs.filterNot { it.stationuuid == s.stationuuid } else favs + s
        saveFavorites(c, favs)
    }
    val browsing = niche != null || q.isNotBlank()

    LazyVerticalGrid(
        GridCells.Adaptive(170.dp), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 30.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding().padding(top = 10.dp)) {
                if (query != null) ScreenBackButton(nav)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Radio", style = Display.copy(fontSize = 34.sp), modifier = Modifier.weight(1f))
                    if (playing != null) LiveBadge()
                }
                Text("En vivo desde todo el mundo, de nicho y con madre.", fontSize = 13.sp, color = Hifi.TextDim)
                // Lo que suena ahorita (si es radio).
                if (playing != null) {
                    Row(
                        Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.04f))))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StationLogo(playing.title, playing.coverArt, Modifier.size(64.dp), corner = 14.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (st.isPlaying) { EqBars(color = Color.White); Spacer(Modifier.width(8.dp)) }
                                Text("SONANDO", style = Mono.copy(color = Hifi.TextFaint, fontSize = 9.sp, letterSpacing = 1.sp))
                            }
                            Text(playing.title, style = Display.copy(fontSize = 19.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(playing.artist ?: "", fontSize = 12.sp, color = Hifi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White).bouncyClick { c.player.toggle() }, contentAlignment = Alignment.Center) {
                            PlayPauseIcon(st.isPlaying, Color.Black, 26.dp)
                        }
                    }
                }
                // Buscador.
                Row(
                    Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.07f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, null, tint = Hifi.TextDim, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (q.isEmpty()) Text("Busca un género o una estación: techno, cumbia, KEXP…", color = Hifi.TextFaint, fontSize = 14.sp, maxLines = 1)
                        BasicTextField(
                            q, { q = it; niche = null }, Modifier.fillMaxWidth().textInput(), singleLine = true,
                            textStyle = TextStyle(color = Hifi.Text, fontSize = 14.sp), cursorBrush = SolidColor(Color.White),
                        )
                    }
                    if (q.isNotEmpty() || niche != null) Icon(
                        Icons.Default.Close, "Borrar", tint = Hifi.TextDim,
                        modifier = Modifier.size(22.dp).clip(CircleShape).bouncyClick { q = ""; niche = null },
                    )
                }
                if (!browsing && favs.isNotEmpty()) {
                    SectionTitle("Tus estaciones", Modifier.padding(top = 20.dp, bottom = 10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(favs, key = { it.stationuuid }) { s ->
                            Column(Modifier.width(110.dp).bouncyClick { play(s, favs) }) {
                                Box {
                                    StationLogo(s.name, s.favicon.takeIf { it.startsWith("http") }, Modifier.size(110.dp), corner = 16.dp)
                                    if (playing?.id == "radio:${s.stationuuid}") EqBars(Modifier.align(Alignment.BottomEnd).padding(8.dp), color = Color.White)
                                }
                                Text(s.name.trim(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                Text(s.country, fontSize = 11.sp, color = Hifi.TextFaint, maxLines = 1)
                            }
                        }
                    }
                }
                if (browsing) Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(niche?.title ?: "“$q”", style = Display.copy(fontSize = 24.sp))
                        Text(niche?.blurb ?: "Estaciones y géneros", fontSize = 12.sp, color = Hifi.TextDim)
                    }
                    if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Hifi.TextDim)
                } else SectionTitle("Nichos", Modifier.padding(top = 20.dp))
            }
        }
        if (browsing) {
            if (!loading && stations.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text("No encontré estaciones. Prueba otra palabra (en inglés suele haber más: \"french house\", \"jazz\").", color = Hifi.TextDim, fontSize = 13.sp)
            }
            items(stations, key = { "s" + it.stationuuid }, span = { GridItemSpan(maxLineSpan) }) { s ->
                StationRow(s, playing?.id == "radio:${s.stationuuid}", favs.any { it.stationuuid == s.stationuuid }, { play(s, stations) }, { toggleFav(s) })
            }
        } else {
            items(RadioNiches, key = { it.title }) { n -> ArtTile(n.title, n.blurb, emptyList(), Modifier.fillMaxWidth(), kicker = "Nicho") { niche = n } }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Directorio abierto radio-browser.info · sólo estaciones que funcionan", style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp), modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun StationRow(s: Station, playing: Boolean, fav: Boolean, onPlay: () -> Unit, onFav: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (playing) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.03f))
            .bouncyClick(onClick = onPlay).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            StationLogo(s.name, s.favicon.takeIf { it.startsWith("http") }, Modifier.size(52.dp))
            if (playing) Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) { EqBars(color = Color.White) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.name.trim(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(s.country, s.tagList.take(3).joinToString(", ")).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 12.sp, color = Hifi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val q = listOf(if (s.bitrate > 0) "${s.bitrate}" else "", s.codec.uppercase()).filter { it.isNotBlank() }.joinToString(" ")
        if (q.isNotBlank()) Text(q, style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp), modifier = Modifier.padding(horizontal = 8.dp))
        Icon(
            if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (fav) "Quitar de tus estaciones" else "Guardar estación",
            tint = if (fav) Color.White else Hifi.TextDim, modifier = Modifier.size(38.dp).clip(CircleShape).bouncyClick(onClick = onFav).padding(9.dp),
        )
    }
}
