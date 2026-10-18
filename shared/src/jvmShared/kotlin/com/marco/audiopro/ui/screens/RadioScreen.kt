package com.marco.audiopro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
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
import com.marco.audiopro.ui.components.CoverArt
import com.marco.audiopro.ui.components.ScreenBackButton
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
 * Radio: estaciones en vivo de todo el mundo (radio-browser.info), con nichos armados a mano
 * (groove ruso, techno francés, italo disco, city pop…), buscador por nombre o género y favoritas.
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
    val playing = c.player.state.collectAsState().value.current?.id

    // Busca al escribir (con una pausita) o al elegir un nicho.
    LaunchedEffect(q, niche) {
        val n = niche
        if (n == null && q.isBlank()) { stations = emptyList(); return@LaunchedEffect }
        if (n == null) delay(450)
        loading = true
        stations = runCatching { if (n != null) c.radio.niche(n) else c.radio.find(q.trim()) }.getOrDefault(emptyList())
        loading = false
    }
    fun play(s: Station, list: List<Station>) {
        val songs = list.map { it.toSong() }
        c.player.play(songs, list.indexOf(s).coerceAtLeast(0))
        scope.launch { c.radio.click(s.stationuuid) }
    }
    fun toggleFav(s: Station) {
        favs = if (favs.any { it.stationuuid == s.stationuuid }) favs.filterNot { it.stationuuid == s.stationuuid } else favs + s
        saveFavorites(c, favs)
    }

    LazyVerticalGrid(
        GridCells.Adaptive(190.dp), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding().padding(top = 10.dp)) {
                if (query != null) ScreenBackButton(nav)
                Text("Radio", style = Display.copy(fontSize = 34.sp))
                Text("Estaciones en vivo de todo el mundo, de nicho y con madre.", fontSize = 13.sp, color = Hifi.TextDim)
                OutlinedTextField(
                    q, { q = it; niche = null },
                    Modifier.fillMaxWidth().padding(top = 12.dp).textInput(),
                    placeholder = { Text("Busca un género o una estación: techno, cumbia, KEXP…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.White.copy(alpha = 0.4f), unfocusedBorderColor = Color.White.copy(alpha = 0.12f)),
                )
            }
        }
        if (niche != null || q.isNotBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text(niche?.title ?: "“$q”", style = Display.copy(fontSize = 22.sp), modifier = Modifier.weight(1f))
                    if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Hifi.TextDim)
                    else Text("Ver nichos", fontSize = 13.sp, color = Hifi.TextDim, modifier = Modifier.clip(RoundedCornerShape(8.dp)).bouncyClick { niche = null; q = "" }.padding(6.dp))
                }
            }
            if (!loading && stations.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text("No encontré estaciones. Prueba otra palabra (en inglés suele haber más: \"french house\", \"jazz\").", color = Hifi.TextDim, fontSize = 13.sp)
            }
            items(stations, key = { "s" + it.stationuuid }, span = { GridItemSpan(maxLineSpan) }) { s ->
                StationRow(s, playing == "radio:${s.stationuuid}", favs.any { it.stationuuid == s.stationuuid }, { play(s, stations) }, { toggleFav(s) })
            }
        } else {
            if (favs.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { Text("Tus estaciones", style = Display.copy(fontSize = 22.sp), modifier = Modifier.padding(top = 8.dp)) }
                items(favs, key = { "f" + it.stationuuid }, span = { GridItemSpan(maxLineSpan) }) { s ->
                    StationRow(s, playing == "radio:${s.stationuuid}", true, { play(s, favs) }, { toggleFav(s) })
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Text("Nichos", style = Display.copy(fontSize = 22.sp), modifier = Modifier.padding(top = 8.dp)) }
            items(RadioNiches, key = { it.title }) { n -> CategoryTile(n.title, n.blurb, null, Modifier.fillMaxWidth()) { niche = n } }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Directorio abierto de radio-browser.info · sólo estaciones que funcionan", style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp), modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

@Composable
private fun StationRow(s: Station, playing: Boolean, fav: Boolean, onPlay: () -> Unit, onFav: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (playing) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .bouncyClick(onClick = onPlay).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(s.favicon.takeIf { it.startsWith("http") }, Modifier.size(48.dp), size = 120, corner = 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playing) { Icon(Icons.Default.GraphicEq, null, tint = Hifi.Accent, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
                Text(s.name.trim(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                listOf(s.country, s.tagList.take(3).joinToString(", "), listOf(if (s.bitrate > 0) "${s.bitrate} kbps" else "", s.codec).filter { it.isNotBlank() }.joinToString(" "))
                    .filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 12.sp, color = Hifi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (fav) "Quitar de tus estaciones" else "Guardar estación",
            tint = if (fav) Hifi.Accent else Hifi.TextDim, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(50)).bouncyClick(onClick = onFav).padding(8.dp),
        )
    }
}
