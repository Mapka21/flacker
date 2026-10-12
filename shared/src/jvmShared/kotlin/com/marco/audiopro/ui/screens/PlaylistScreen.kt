package com.marco.audiopro.ui.screens

import com.marco.audiopro.ui.components.AppMenu
import com.marco.audiopro.ui.components.AppMenuDivider
import com.marco.audiopro.ui.components.AppMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.data.CustomCovers
import com.marco.audiopro.ui.components.CoverArt
import com.marco.audiopro.ui.components.CoverEditButton
import com.marco.audiopro.ui.components.DownloadButton
import com.marco.audiopro.ui.components.LoadBox
import com.marco.audiopro.ui.components.PlayActions
import com.marco.audiopro.ui.components.PlaylistNameDialog
import com.marco.audiopro.ui.components.ScreenBackButton
import com.marco.audiopro.ui.components.TrackRow
import com.marco.audiopro.ui.components.formatDuration
import com.marco.audiopro.ui.components.rememberLoad
import com.marco.audiopro.ui.components.songsLabel
import com.marco.audiopro.ui.openAlbum
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import kotlinx.coroutines.launch

@Composable
fun PlaylistScreen(id: String, nav: NavController) {
    val c = LocalApp.current
    // Se recarga cuando cambia cualquier playlist (agregar/quitar/renombrar).
    val version by c.playlists.version.collectAsState()
    val loader = rememberLoad(id to version) { c.api.playlist(id) }
    val playback by c.player.state.collectAsState()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    LoadBox(loader, Modifier.statusBarsPadding()) { pl ->
        val songs = pl.entry
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                ScreenBackButton(nav)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        CoverArt(
                            pl.coverArt, Modifier.size(160.dp).shadow(24.dp, RoundedCornerShape(14.dp)),
                            size = 500, corner = 14.dp, customKey = CustomCovers.playlist(pl.id),
                        )
                        CoverEditButton(CustomCovers.playlist(pl.id), Modifier.align(Alignment.BottomEnd).padding(8.dp))
                    }
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text("PLAYLIST", style = com.marco.audiopro.ui.theme.Mono.copy(color = Hifi.TextDim))
                        Text(pl.name, style = Display.copy(fontSize = 30.sp))
                        Text("${songsLabel(songs.size)} · ${formatDuration(pl.duration)}", color = Hifi.TextDim, fontSize = 13.sp)
                        pl.comment?.takeIf { it.isNotBlank() }?.let { Text(it, color = Hifi.TextFaint, fontSize = 12.sp) }
                    }
                }
                PlayActions(
                    onPlay = { if (songs.isNotEmpty()) c.player.play(songs) },
                    onShuffle = { if (songs.isNotEmpty()) c.player.play(songs, shuffle = true) },
                    modifier = Modifier.padding(20.dp),
                ) {
                    DownloadButton(songs)
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Más", tint = Hifi.TextDim) }
                        AppMenu(menu, { menu = false }) {
                            AppMenuItem("Cambiar nombre", Icons.Default.Edit) { menu = false; renaming = true }
                            AppMenuDivider()
                            AppMenuItem("Borrar playlist", Icons.Default.Delete, destructive = true) { menu = false; deleting = true }
                        }
                    }
                }
                if (songs.isEmpty()) {
                    Text(
                        "Está vacía. Agrega canciones desde el menú ⋮ (o clic derecho) de cualquier canción o álbum → \"Agregar a playlist\".",
                        color = Hifi.TextDim, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            itemsIndexed(songs, key = { i, s -> "$i-${s.id}" }) { i, s ->
                TrackRow(
                    s, null, playback.current?.id == s.id, { c.player.play(songs, i) },
                    showCover = true, showFormat = true, onGoToAlbum = { s.albumId?.let(nav::openAlbum) },
                    onRemove = { scope.launch { runCatching { c.playlists.removeAt(pl.id, i) } } },
                )
            }
        }
        if (renaming) {
            PlaylistNameDialog("Cambiar nombre", initial = pl.name, confirm = "Guardar", onDismiss = { renaming = false }) { name ->
                renaming = false
                scope.launch { runCatching { c.playlists.rename(pl.id, name) } }
            }
        }
        if (deleting) {
            AlertDialog(
                onDismissRequest = { deleting = false },
                title = { Text("¿Borrar “${pl.name}”?") },
                text = { Text("Se borra del servidor. Las canciones siguen en tu biblioteca.") },
                confirmButton = {
                    TextButton(onClick = {
                        deleting = false
                        scope.launch { runCatching { c.playlists.delete(pl.id) }.onSuccess { nav.popBackStack() } }
                    }) { Text("Borrar", color = Hifi.LedRed) }
                },
                dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancelar") } },
                containerColor = Hifi.PanelHi,
            )
        }
    }
}
