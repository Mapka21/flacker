package com.marco.audiopro.ui.screens

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.marco.audiopro.container
import com.marco.audiopro.data.subsonic.SearchResult
import com.marco.audiopro.ui.components.SectionHeader
import com.marco.audiopro.ui.components.TrackRow
import com.marco.audiopro.ui.openAlbum
import com.marco.audiopro.ui.openArtist
import com.marco.audiopro.ui.theme.Hifi
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(nav: NavController) {
    val c = LocalContext.current.container
    var query by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<SearchResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val playback by c.player.state.collectAsState()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(query) {
        if (query.isBlank()) { result = null; return@LaunchedEffect }
        delay(300)
        runCatching { c.api.search(query.trim()) }
            .onSuccess { result = it; error = null }
            .onFailure { error = it.message }
    }
    LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            query, { query = it },
            placeholder = { Text("Artistas, álbumes, canciones") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, null) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus),
        )
        val r = result
        when {
            error != null -> Text(error!!, color = Hifi.LedRed, modifier = Modifier.padding(20.dp))
            r == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Busca en tu biblioteca", color = Hifi.TextFaint)
            }
            r.artist.isEmpty() && r.album.isEmpty() && r.song.isEmpty() ->
                Text("Sin resultados para “$query”", color = Hifi.TextDim, modifier = Modifier.padding(20.dp))
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                if (r.artist.isNotEmpty()) {
                    item { SectionHeader("Artistas") }
                    items(r.artist, key = { "ar" + it.id }) { a -> ArtistRow(a) { nav.openArtist(a.id) } }
                }
                if (r.album.isNotEmpty()) {
                    item {
                        SectionHeader("Álbumes")
                        AlbumRow(r.album) { nav.openAlbum(it.id) }
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
            }
        }
    }
}
