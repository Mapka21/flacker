package com.marco.audiopro.ui.screens

import com.marco.audiopro.ui.components.textInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.marco.audiopro.core.LocalApp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.marco.audiopro.data.subsonic.SearchResult
import androidx.compose.ui.unit.sp
import com.marco.audiopro.ui.LocalSearchQuery
import com.marco.audiopro.ui.components.SectionHeader
import com.marco.audiopro.ui.components.TrackRow
import com.marco.audiopro.ui.openAlbum
import com.marco.audiopro.ui.openArtist
import com.marco.audiopro.ui.theme.Hifi
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(nav: NavController) {
    val c = LocalApp.current
    // En la PC el texto viene de la barra de arriba; en el celular, del campo de esta pantalla.
    val shared = LocalSearchQuery.current
    var localQuery by rememberSaveable { mutableStateOf("") }
    val query = shared?.value ?: localQuery
    var result by remember { mutableStateOf<SearchResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val playback by c.player.state.collectAsState()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(query) {
        // Un error viejo no se queda pegado: cada búsqueda nueva empieza limpia.
        error = null
        if (query.isBlank()) { result = null; return@LaunchedEffect }
        delay(300)
        runCatching { c.api.search(query.trim()) }
            .onSuccess { result = it; error = null }
            .onFailure { error = it.message }
    }
    // (En el celular no se abre el teclado solo: primero se ven tus géneros para explorar.)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (shared != null) {
            Text(
                if (query.isBlank()) "Buscar" else "Resultados para “$query”",
                style = com.marco.audiopro.ui.theme.Display.copy(fontSize = 30.sp),
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
            )
        } else {
            // Celular: título grande y una pastilla de búsqueda (como Spotify / Apple Music).
            Text("Buscar", style = com.marco.audiopro.ui.theme.Display.copy(fontSize = 32.sp), modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 10.dp))
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.10f)).padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, null, tint = Hifi.TextDim, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Artistas, álbumes, canciones", color = Hifi.TextFaint, fontSize = 15.sp)
                    BasicTextField(
                        query, { localQuery = it }, singleLine = true,
                        textStyle = TextStyle(color = Hifi.Text, fontSize = 15.sp, fontFamily = com.marco.audiopro.ui.theme.Fonts.inter),
                        cursorBrush = SolidColor(Hifi.Text),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).textInput(),
                    )
                }
                if (query.isNotEmpty()) IconButton(onClick = { localQuery = "" }) { Icon(Icons.Default.Close, "Borrar", tint = Hifi.TextDim) }
            }
            Spacer(Modifier.height(8.dp))
        }
        val r = result
        when {
            error != null -> Text(error!!, color = Hifi.LedRed, modifier = Modifier.padding(20.dp))
            r == null && shared == null -> ExploreGenres(nav)
            r == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Escribe arriba para buscar en tu biblioteca", color = Hifi.TextFaint)
            }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                if (r.artist.isEmpty() && r.album.isEmpty() && r.song.isEmpty()) {
                    item { Text("No está en tu biblioteca: “$query”", color = Hifi.TextDim, modifier = Modifier.padding(20.dp)) }
                }
                if (r.artist.isNotEmpty()) {
                    item { SectionHeader("Artistas") }
                    items(r.artist, key = { "ar" + it.id }) { a -> ArtistRow(a) { nav.openArtist(a.id) } }
                }
                if (r.album.isNotEmpty()) {
                    item {
                        SectionHeader("Álbumes")
                        AlbumRow(r.album, nav = nav) { nav.openAlbum(it.id) }
                    }
                }
                if (r.song.isNotEmpty()) {
                    item { SectionHeader("Canciones") }
                    itemsIndexed(r.song, key = { _, s -> "s" + s.id }) { i, s ->
                        TrackRow(
                            s, null, playback.current?.id == s.id, { c.player.play(r.song, i) },
                            showCover = true, showFormat = true, onGoToAlbum = { s.albumId?.let(nav::openAlbum) },
                        )
                    }
                }
                // Lo que no tienes: catálogo para descubrir y conseguir en hi-res.
                item(key = "outside") { Column { OutsideLibraryResults(query.trim(), nav) } }
            }
        }
    }
}

/** Con la búsqueda vacía en el celular: tus géneros (los de verdad, de MusicBrainz) en mosaicos para explorar. */
@Composable
private fun ExploreGenres(nav: NavController) {
    val c = LocalApp.current
    val lib by c.genres.state.collectAsState()
    LaunchedEffect(Unit) { runCatching { c.genres.load() } }
    val genres = lib.genres.take(16).map { it.name to it.covers.firstOrNull() }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        if (genres.isNotEmpty()) item {
            Text("Explora tus géneros", style = com.marco.audiopro.ui.theme.Display.copy(fontSize = 20.sp), modifier = Modifier.padding(top = 14.dp, bottom = 12.dp))
        }
        items(genres.chunked(2)) { row ->
            Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (name, cover) ->
                    CategoryTile(name, null, cover, Modifier.weight(1f)) { nav.navigate(com.marco.audiopro.ui.GenreRoute(name)) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
