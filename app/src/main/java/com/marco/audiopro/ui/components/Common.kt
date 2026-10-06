package com.marco.audiopro.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.marco.audiopro.container
import com.marco.audiopro.data.subsonic.Album
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono

// ------------------------------------------------------------------ carga asíncrona

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Err(val message: String) : Load<Nothing>
}

class Loader<T>(val state: State<Load<T>>, val retry: () -> Unit)

/** Carga `block` al entrar (y al cambiar `key`); `retry()` la repite. */
@Composable
fun <T> rememberLoad(key: Any?, block: suspend () -> T): Loader<T> {
    var attempt by remember { mutableIntStateOf(0) }
    val state = remember(key) { mutableStateOf<Load<T>>(Load.Loading) }
    LaunchedEffect(key, attempt) {
        if (state.value !is Load.Ok) state.value = Load.Loading
        state.value = runCatching { block() }.fold({ Load.Ok(it) }, { Load.Err(it.message ?: it.javaClass.simpleName) })
    }
    return Loader(state) { attempt++ }
}

@Composable
fun <T> LoadBox(loader: Loader<T>, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    when (val s = loader.state.value) {
        is Load.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Hifi.Amber, strokeWidth = 2.dp)
        }
        is Load.Err -> ErrorBox(s.message, loader.retry, modifier)
        is Load.Ok -> content(s.value)
    }
}

@Composable
fun ErrorBox(message: String, retry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SIN SEÑAL", style = Mono.copy(color = Hifi.LedRed, fontSize = 14.sp))
        Spacer(Modifier.height(8.dp))
        Text(message, color = Hifi.TextDim, fontSize = 13.sp)
        TextButton(onClick = retry) { Text("Reintentar") }
    }
}

// ------------------------------------------------------------------ portadas

@Composable
fun CoverArt(coverArt: String?, modifier: Modifier = Modifier, size: Int = 400, corner: Dp = 6.dp) {
    val api = LocalContext.current.container.api
    val url = remember(coverArt, size) { api.coverUrl(coverArt, size) }
    Box(
        modifier.clip(RoundedCornerShape(corner)).background(Hifi.PanelHi),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Album, null, tint = Hifi.TextFaint, modifier = Modifier.fillMaxSize(0.4f))
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Color dominante de la portada (para fondos degradados). */
@Composable
fun rememberCoverColor(coverArt: String?, fallback: Color = Hifi.Panel): State<Color> {
    val ctx = LocalContext.current
    val target = remember { mutableStateOf(fallback) }
    LaunchedEffect(coverArt) {
        val url = ctx.container.api.coverUrl(coverArt, 120) ?: return@LaunchedEffect
        val req = ImageRequest.Builder(ctx).data(url).allowHardware(false).build()
        val res = SingletonImageLoader.get(ctx).execute(req) as? SuccessResult ?: return@LaunchedEffect
        val p = Palette.from(res.image.toBitmap()).generate()
        val sw = p.darkVibrantSwatch ?: p.vibrantSwatch ?: p.darkMutedSwatch ?: p.dominantSwatch
        if (sw != null) target.value = Color(sw.rgb)
    }
    return animateColorAsState(target.value, tween(600), label = "cover")
}

// ------------------------------------------------------------------ badges y formato

@Composable
fun FormatBadge(song: Song?, modifier: Modifier = Modifier, large: Boolean = false) {
    if (song == null) return
    val color = when {
        song.isHiRes -> Hifi.Amber
        song.isLossless -> Hifi.Cream
        else -> Hifi.TextDim
    }
    Row(
        modifier
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
            .padding(horizontal = if (large) 8.dp else 5.dp, vertical = if (large) 3.dp else 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (song.isHiRes) {
            Text("HI-RES ", style = Mono.copy(color = color, fontSize = if (large) 11.sp else 9.sp, fontWeight = FontWeight.Bold))
        }
        Text(song.formatLabel, style = Mono.copy(color = color, fontSize = if (large) 11.sp else 9.sp))
    }
}

fun formatDuration(seconds: Int?): String {
    val s = seconds ?: 0
    val h = s / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

fun formatMs(ms: Long): String = formatDuration((ms / 1000).toInt())

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 3.dp, height = 14.dp).background(Hifi.Amber))
        Spacer(Modifier.width(8.dp))
        Text(title.uppercase(), style = Mono.copy(color = Hifi.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

// ------------------------------------------------------------------ tarjetas y filas

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(6.dp)) {
        CoverArt(album.coverArt, Modifier.fillMaxWidth().aspectRatio(1f), size = 400)
        Spacer(Modifier.height(6.dp))
        Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(
            listOfNotNull(album.artist, album.year?.toString()).joinToString(" · "),
            maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = Hifi.TextDim,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    song: Song,
    leading: String?,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showCover: Boolean = false,
    showArtist: Boolean = true,
    showFormat: Boolean = false,
    onGoToAlbum: (() -> Unit)? = null,
) {
    val player = LocalContext.current.container.player
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menu = true })
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            showCover -> CoverArt(song.coverArt, Modifier.size(44.dp), size = 120, corner = 4.dp)
            isCurrent -> Icon(Icons.Default.GraphicEq, null, tint = Hifi.Amber, modifier = Modifier.width(28.dp).size(18.dp))
            else -> Text(leading ?: "", style = Mono.copy(color = Hifi.TextFaint), modifier = Modifier.width(28.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp,
                color = if (isCurrent) Hifi.Amber else Hifi.Text,
            )
            val sub = listOfNotNull(if (showArtist) song.artist else null, formatDuration(song.duration)).joinToString(" · ")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = Hifi.TextDim, modifier = Modifier.weight(1f, fill = false))
                if (showFormat) { Spacer(Modifier.width(6.dp)); FormatBadge(song) }
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null, tint = Hifi.TextFaint) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Reproducir siguiente") },
                    leadingIcon = { Icon(Icons.Default.SkipNext, null) },
                    onClick = { player.playNext(listOf(song)); menu = false },
                )
                DropdownMenuItem(
                    text = { Text("Agregar a la cola") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                    onClick = { player.enqueue(listOf(song)); menu = false },
                )
                if (onGoToAlbum != null && song.albumId != null) {
                    DropdownMenuItem(
                        text = { Text("Ir al álbum") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                        onClick = { onGoToAlbum(); menu = false },
                    )
                }
            }
        }
    }
}
