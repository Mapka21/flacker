package com.marco.audiopro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.marco.audiopro.container
import com.marco.audiopro.data.subsonic.Album
import com.marco.audiopro.ui.components.AlbumCard
import com.marco.audiopro.ui.components.LoadBox
import com.marco.audiopro.ui.components.SectionHeader
import com.marco.audiopro.ui.components.rememberLoad
import com.marco.audiopro.ui.openAlbum
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
    "starred" to "Favoritos",
    "random" to "Redescubre",
)

@Composable
fun HomeScreen(nav: NavController) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
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

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("MARCO AUDIO PRO", style = Mono.copy(color = Hifi.Amber, fontSize = 11.sp, letterSpacing = 4.sp))
                Text(greeting, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, null, tint = Hifi.TextDim) }
        }
        Visualizer(VizMode.SPECTRUM, Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp))

        LoadBox(loader) { lists ->
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickAction("Mezcla aleatoria", Icons.Default.Shuffle, Modifier.weight(1f)) {
                            scope.launch { runCatching { c.api.randomSongs(80) }.onSuccess { c.player.play(it) } }
                        }
                        QuickAction("Favoritos al azar", Icons.Default.Star, Modifier.weight(1f)) {
                            scope.launch {
                                runCatching {
                                    val albums = c.api.albumList("starred", 30).shuffled().take(6)
                                    albums.flatMap { c.api.album(it.id).song }.shuffled()
                                }.onSuccess { c.player.play(it) }
                            }
                        }
                    }
                }
                homeSections.forEachIndexed { i, (_, title) ->
                    val albums = lists[i]
                    if (albums.isNotEmpty()) {
                        item(key = title) {
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
private fun QuickAction(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Hifi.Panel)
            .border(1.dp, Hifi.Line, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Hifi.Amber)
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AlbumRow(albums: List<Album>, onClick: (Album) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp)) {
        items(albums, key = { it.id }) { a -> AlbumCard(a, { onClick(a) }, Modifier.width(150.dp)) }
    }
}
