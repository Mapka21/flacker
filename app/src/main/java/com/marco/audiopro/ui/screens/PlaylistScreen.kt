package com.marco.audiopro.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.marco.audiopro.container
import com.marco.audiopro.ui.components.CoverArt
import com.marco.audiopro.ui.components.LoadBox
import com.marco.audiopro.ui.components.TrackRow
import com.marco.audiopro.ui.components.formatDuration
import com.marco.audiopro.ui.components.rememberLoad
import com.marco.audiopro.ui.openAlbum
import com.marco.audiopro.ui.theme.Hifi

@Composable
fun PlaylistScreen(id: String, nav: NavController) {
    val c = LocalContext.current.container
    val loader = rememberLoad(id) { c.api.playlist(id) }
    val playback by c.player.state.collectAsState()
    LoadBox(loader, Modifier.statusBarsPadding()) { pl ->
        val songs = pl.entry
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverArt(pl.coverArt, Modifier.size(120.dp), size = 400)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(pl.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("${songs.size} canciones · ${formatDuration(pl.duration)}", color = Hifi.TextDim, fontSize = 13.sp)
                        pl.comment?.takeIf { it.isNotBlank() }?.let { Text(it, color = Hifi.TextFaint, fontSize = 12.sp) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { c.player.play(songs) },
                        colors = ButtonDefaults.buttonColors(containerColor = Hifi.Amber, contentColor = Color.Black),
                        modifier = Modifier.height(46.dp),
                    ) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("REPRODUCIR", fontWeight = FontWeight.Bold) }
                    OutlinedButton(onClick = { c.player.play(songs, shuffle = true) }, modifier = Modifier.height(46.dp)) {
                        Icon(Icons.Default.Shuffle, null, tint = Hifi.Cream)
                    }
                }
            }
            itemsIndexed(songs, key = { i, s -> "$i-${s.id}" }) { i, s ->
                TrackRow(
                    s, null, playback.current?.id == s.id, { c.player.play(songs, i) },
                    showCover = true, showFormat = true, onGoToAlbum = { s.albumId?.let(nav::openAlbum) },
                )
            }
        }
    }
}
