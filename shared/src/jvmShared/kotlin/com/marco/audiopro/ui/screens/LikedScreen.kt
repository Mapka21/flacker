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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.marco.audiopro.core.LocalApp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.marco.audiopro.ui.components.DownloadButton
import com.marco.audiopro.ui.components.ErrorBox
import com.marco.audiopro.ui.components.PlayActions
import com.marco.audiopro.ui.components.TrackRow
import com.marco.audiopro.ui.components.formatDuration
import com.marco.audiopro.ui.openAlbum
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.components.songsLabel
import com.marco.audiopro.ui.theme.Hifi

@Composable
fun LikedScreen(nav: NavController) {
    val c = LocalApp.current
    val songs by c.likes.songs.collectAsState()
    val playback by c.player.state.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(attempt) { runCatching { c.likes.refresh() }.onFailure { error = it.message } }

    LazyColumn(
        Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Hifi.Accent.copy(alpha = 0.28f), 0.35f to Hifi.Bg)),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding()) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(112.dp).clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(Hifi.Accent, Hifi.AccentDim, Hifi.Panel))),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Favorite, null, tint = Color.White, modifier = Modifier.size(48.dp)) }
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text("Me gusta", style = Display.copy(fontSize = 30.sp))
                        Text(
                            "${songsLabel(songs.size)} · ${formatDuration(songs.sumOf { it.duration ?: 0 })}",
                            color = Hifi.TextDim, fontSize = 13.sp,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                PlayActions(
                    onPlay = { c.player.play(songs) }, onShuffle = { c.player.play(songs, shuffle = true) },
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) { DownloadButton(songs) }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (songs.isEmpty() && error != null) {
            item { Box(Modifier.height(300.dp)) { ErrorBox(error!!, { error = null; attempt++ }) } }
        } else if (songs.isEmpty() && c.likes.loaded) {
            item {
                Text(
                    "Todavía no tienes canciones con me gusta. Toca el ♥ en el reproductor o en el menú de cualquier canción.",
                    color = Hifi.TextDim, fontSize = 14.sp, modifier = Modifier.padding(24.dp),
                )
            }
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
            TrackRow(
                s, null, playback.current?.id == s.id, { c.player.play(songs, i) },
                showCover = true, showFormat = true, onGoToAlbum = { s.albumId?.let(nav::openAlbum) },
            )
        }
    }
}
