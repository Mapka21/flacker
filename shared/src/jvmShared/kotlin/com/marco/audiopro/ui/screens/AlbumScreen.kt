package com.marco.audiopro.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.marco.audiopro.ui.components.glass
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.marco.audiopro.core.LocalApp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.marco.audiopro.data.info.AlbumSummary
import com.marco.audiopro.data.info.InfoSource
import com.marco.audiopro.data.subsonic.Album
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import com.marco.audiopro.ui.components.AddToPlaylistDialog
import com.marco.audiopro.ui.components.CoverArt
import com.marco.audiopro.ui.components.ScreenBackButton
import com.marco.audiopro.ui.components.DownloadButton
import com.marco.audiopro.ui.components.PlayActions
import com.marco.audiopro.ui.components.FormatBadge
import com.marco.audiopro.ui.components.LoadBox
import com.marco.audiopro.ui.components.SectionHeader
import com.marco.audiopro.ui.components.TrackRow
import com.marco.audiopro.ui.components.formatDuration
import com.marco.audiopro.ui.components.rememberCoverColor
import com.marco.audiopro.ui.components.rememberLoad
import com.marco.audiopro.ui.openAlbum
import com.marco.audiopro.ui.openArtist
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import kotlinx.coroutines.launch
import java.text.NumberFormat

@Composable
fun AlbumScreen(id: String, nav: NavController) {
    val c = LocalApp.current
    val loader = rememberLoad(id) { c.api.album(id) }
    val playback by c.player.state.collectAsState()
    LoadBox(loader, Modifier.statusBarsPadding()) { album ->
        val tint by rememberCoverColor(album.coverArt)
        val songs = album.song
        val multiDisc = songs.mapNotNull { it.discNumber }.distinct().size > 1
        val more = rememberLoad(album.artistId) { album.artistId?.let { c.api.artist(it).album.filter { it.id != album.id } } ?: emptyList() }

        LazyColumn(
            Modifier.fillMaxSize().background(Brush.verticalGradient(0f to tint.copy(alpha = 0.75f), 0.5f to Color.Transparent)),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { AlbumHeader(album, nav) }
            item { AlbumSummaryCard(album) }
            itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                if (multiDisc && (i == 0 || songs[i - 1].discNumber != s.discNumber)) {
                    Text(
                        "DISCO ${s.discNumber ?: 1}", style = Mono.copy(color = Hifi.Accent),
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                TrackRow(
                    s, leading = s.track?.toString(), isCurrent = playback.current?.id == s.id,
                    onClick = { c.player.play(songs, i) },
                    showArtist = s.artist != album.artist,
                    onGoToAlbum = null,
                    showFormat = s.formatLabel != songs.first().formatLabel,
                )
            }
            item {
                val total = songs.sumOf { it.size ?: 0L }
                Text(
                    "${songs.size} pistas · ${formatDuration(album.duration)}" +
                        (if (total > 0) " · %.1f MB".format(total / 1_048_576.0) else ""),
                    style = Mono.copy(color = Hifi.TextFaint), modifier = Modifier.padding(20.dp),
                )
            }
            val others = (more.state.value as? com.marco.audiopro.ui.components.Load.Ok)?.value.orEmpty()
            if (others.isNotEmpty()) {
                item {
                    SectionHeader("Más de ${album.artist}")
                    AlbumRow(others, nav = nav) { nav.openAlbum(it.id) }
                }
            }
        }
    }
}

@Composable
private fun AlbumHeader(album: Album, nav: NavController) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    var starred by remember(album.id) { mutableStateOf(album.starred != null) }
    var addTo by remember { mutableStateOf(false) }
    val songs = album.song
    if (addTo) AddToPlaylistDialog(songs) { addTo = false }
    Column(Modifier.fillMaxWidth().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth()) { ScreenBackButton(nav) }
        Spacer(Modifier.height(16.dp))
        CoverArt(
            album.coverArt,
            Modifier.size(256.dp).shadow(32.dp, RoundedCornerShape(12.dp)),
            size = 800, corner = 12.dp,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            album.name, style = Display.copy(fontSize = 26.sp), textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Text(
            album.artist ?: "", fontSize = 16.sp, color = Hifi.Accent,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { album.artistId?.let(nav::openArtist) }.padding(4.dp),
        )
        val genres = album.genres?.map { it.name }?.filter { it.isNotBlank() }.orEmpty().ifEmpty { listOfNotNull(album.genre) }
        val label = album.recordLabels?.firstOrNull()?.name?.takeIf { it.isNotBlank() }
        Text(
            listOfNotNull(album.year?.toString(), genres.take(2).joinToString(" / ").ifBlank { null }, label).joinToString(" · "),
            fontSize = 13.sp, color = Hifi.TextDim,
        )
        Spacer(Modifier.height(8.dp))
        FormatBadge(songs.firstOrNull(), large = true)
        Spacer(Modifier.height(16.dp))
        PlayActions(
            onPlay = { c.player.play(songs) },
            onShuffle = { c.player.play(songs, shuffle = true) },
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
            IconButton(onClick = {
                starred = !starred
                scope.launch { runCatching { c.api.star(album.id, starred) } }
            }) {
                Icon(if (starred) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Álbum favorito", tint = if (starred) Hifi.Accent else Hifi.TextDim)
            }
            DownloadButton(songs)
            IconButton(onClick = { addTo = true }) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Agregar a playlist", tint = Hifi.TextDim) }
            IconButton(onClick = { c.player.enqueue(songs) }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Agregar a la cola", tint = Hifi.TextDim) }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlbumSummaryCard(album: Album) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    var summary by remember(album.id) { mutableStateOf<AlbumSummary?>(null) }
    var loading by remember(album.id) { mutableStateOf(true) }
    var expanded by remember(album.id) { mutableStateOf(false) }
    LaunchedEffect(album.id) {
        summary = runCatching { c.albumInfo.summary(album) }.getOrElse { AlbumSummary(error = it.message) }
        loading = false
    }
    val s = summary
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(16.dp))
            .padding(16.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ACERCA DEL ÁLBUM", style = Mono.copy(color = Hifi.Text, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            if (s != null && s.source != InfoSource.NONE) {
                Box(Modifier.border(1.dp, Hifi.AccentDim, RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(s.source.label.uppercase(), style = Mono.copy(color = Hifi.Accent, fontSize = 9.sp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            loading -> Text("Buscando reseña…", color = Hifi.TextFaint, fontSize = 14.sp)
            s?.text != null -> {
                Text(
                    s.text, fontSize = 14.sp, lineHeight = 21.sp, color = Hifi.Text.copy(alpha = 0.9f),
                    maxLines = if (expanded) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
                if (!expanded && s.text.length > 320) {
                    Text("Leer más", color = Hifi.Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp).clickable { expanded = true })
                }
            }
            else -> Text(s?.error ?: "Sin información.", color = Hifi.TextDim, fontSize = 13.sp)
        }
        if (s != null && s.tags.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.tags.forEach { t ->
                    Text(
                        t.lowercase(), fontSize = 11.sp, color = Hifi.Cream,
                        modifier = Modifier.background(Hifi.PanelHi, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
        if (s?.listeners != null) {
            Spacer(Modifier.height(10.dp))
            val nf = NumberFormat.getIntegerInstance()
            Text(
                "${nf.format(s.listeners)} oyentes · ${nf.format(s.playcount ?: 0)} reproducciones en Last.fm",
                style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp),
            )
        }
        val hasKey = c.settings.prefs.collectAsState().value.anthropicKey.isNotBlank()
        if (!loading && hasKey && (s?.text == null || s.source == InfoSource.CLAUDE)) {
            TextButton(onClick = {
                loading = true
                scope.launch {
                    summary = c.albumInfo.generateWithClaude(album, s ?: AlbumSummary())
                    loading = false
                }
            }) {
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = Hifi.Accent)
                Spacer(Modifier.width(6.dp))
                Text(if (s?.source == InfoSource.CLAUDE) "Regenerar con IA" else "Generar reseña con IA", color = Hifi.Accent)
            }
        }
    }
}
