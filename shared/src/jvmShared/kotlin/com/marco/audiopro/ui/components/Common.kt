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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.filled.Radio
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import com.marco.audiopro.core.LocalApp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.platform.coilImageToImageBitmap
import com.marco.audiopro.data.subsonic.Album
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.ui.theme.Display
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
            CircularProgressIndicator(color = Hifi.Accent, strokeWidth = 2.dp)
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
        Spacer(Modifier.height(4.dp))
        Text("Tus descargas siguen disponibles sin conexión.", color = Hifi.TextFaint, fontSize = 12.sp)
        TextButton(onClick = retry) { Text("Reintentar", color = Hifi.Accent) }
    }
}

// ------------------------------------------------------------------ portadas

/** @param customKey si hay una portada propia guardada con esa clave, se usa en lugar de la del servidor. */
@Composable
fun CoverArt(coverArt: String?, modifier: Modifier = Modifier, size: Int = 400, corner: Dp = 8.dp, customKey: String? = null) {
    val c = LocalApp.current
    val url = remember(coverArt, size) { c.api.coverUrl(coverArt, size) }
    val custom = if (customKey != null) { c.covers.map.collectAsState().value; c.covers.get(customKey) } else null
    Box(
        modifier.clip(RoundedCornerShape(corner)).background(Hifi.PanelHi),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Album, null, tint = Hifi.TextFaint, modifier = Modifier.fillMaxSize(0.4f))
        val model: Any? = custom ?: url
        if (model != null) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * Colores sacados de la portada: un fondo oscuro, un acento legible sobre negro y hasta
 * 4 tonos dominantes para el fondo animado ([AmbientBackground]).
 */
data class CoverColors(val background: Color, val accent: Color, val swatches: List<Color>)

private val paletteCache = object : LinkedHashMap<String, CoverColors>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CoverColors>?) = size > 64
}

suspend fun coverColors(ctx: PlatformContext, api: SubsonicApi, coverArt: String?): CoverColors? {
    coverArt ?: return null
    paletteCache[coverArt]?.let { return it }
    val url = api.coverUrl(coverArt, 120) ?: return null
    val req = ImageRequest.Builder(ctx).data(url).build()
    val res = SingletonImageLoader.get(ctx).execute(req) as? SuccessResult ?: return null
    return extractColors(coilImageToImageBitmap(res.image))?.also { paletteCache[coverArt] = it }
}

private val bitmapCache = object : LinkedHashMap<String, ImageBitmap>(8, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 8
}

/** La portada como bitmap chico (para el fondo "Portada viva"). */
suspend fun coverBitmap(ctx: PlatformContext, api: SubsonicApi, coverArt: String?, size: Int = 300): ImageBitmap? {
    coverArt ?: return null
    bitmapCache[coverArt]?.let { return it }
    val url = api.coverUrl(coverArt, size) ?: return null
    val res = SingletonImageLoader.get(ctx).execute(ImageRequest.Builder(ctx).data(url).build()) as? SuccessResult ?: return null
    return runCatching { coilImageToImageBitmap(res.image) }.getOrNull()?.also { bitmapCache[coverArt] = it }
}

/**
 * Extractor de color tipo "Palette": histograma por tono ponderado por saturación; el tono
 * ganador da el acento (vivo y claro) y una versión oscura para fondos.
 */
private fun extractColors(img: ImageBitmap): CoverColors? {
    val px = img.toPixelMap()
    val buckets = 24
    val weight = FloatArray(buckets)
    val sumR = FloatArray(buckets); val sumG = FloatArray(buckets); val sumB = FloatArray(buckets)
    var grayW = 0f; var gr = 0f; var gg = 0f; var gb = 0f
    val step = maxOf(1, px.width / 48)
    for (y in 0 until px.height step step) for (x in 0 until px.width step step) {
        val c = px[x, y]
        val max = maxOf(c.red, c.green, c.blue); val min = minOf(c.red, c.green, c.blue)
        val sat = if (max == 0f) 0f else (max - min) / max
        if (sat < 0.18f || max < 0.12f) { grayW += 1f; gr += c.red; gg += c.green; gb += c.blue; continue }
        val hue = rgbHue(c.red, c.green, c.blue, max, min)
        val b = ((hue / 360f) * buckets).toInt().coerceIn(0, buckets - 1)
        val w = sat * (0.4f + max)
        weight[b] += w; sumR[b] += c.red * w; sumG[b] += c.green * w; sumB[b] += c.blue * w
    }
    fun bucketColor(b: Int) = Color(sumR[b] / weight[b], sumG[b] / weight[b], sumB[b] / weight[b])
    val ranked = weight.indices.filter { weight[it] > 0.5f }.sortedByDescending { weight[it] }
    val gray = if (grayW > 0) Color(gr / grayW, gg / grayW, gb / grayW) else null
    val base = ranked.firstOrNull()?.let(::bucketColor) ?: gray ?: return null
    var accent = base
    if (accent.luminance() < 0.3f) accent = lerp(accent, Color.White, 0.45f)
    val background = lerp(Color.Black, base, 0.42f)
    // Tonos para el fondo vivo: los más fuertes; si la portada es casi gris, variaciones del gris.
    val tones = (ranked.take(4).map(::bucketColor) + listOfNotNull(gray)).ifEmpty { listOf(base) }
    val swatches = List(4) { i -> tones[i % tones.size].let { if (i >= tones.size) lerp(it, Color.Black, 0.35f) else it } }
    return CoverColors(background, accent, swatches)
}

private fun rgbHue(r: Float, g: Float, b: Float, max: Float, min: Float): Float {
    val d = max - min
    if (d == 0f) return 0f
    val h = when (max) {
        r -> ((g - b) / d) % 6f
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } * 60f
    return if (h < 0) h + 360f else h
}

/** Color de fondo de la portada, animado (para degradados). */
@Composable
fun rememberCoverColor(coverArt: String?, fallback: Color = Hifi.Panel): State<Color> {
    val ctx = LocalPlatformContext.current
    val api = LocalApp.current.api
    val target = remember { mutableStateOf(fallback) }
    LaunchedEffect(coverArt) { coverColors(ctx, api, coverArt)?.let { target.value = it.background } }
    return animateColorAsState(target.value, tween(600), label = "cover")
}

// ------------------------------------------------------------------ badges y formato

@Composable
fun FormatBadge(song: Song?, modifier: Modifier = Modifier, large: Boolean = false) {
    if (song == null) return
    val color = when {
        song.isHiRes -> Hifi.HiResGold
        song.isLossless -> Hifi.Cream
        else -> Hifi.TextDim
    }
    val fs = if (large) 11.sp else 9.sp
    Row(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
            .padding(horizontal = if (large) 8.dp else 5.dp, vertical = if (large) 3.dp else 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (song.isHiRes) Text("HI-RES ", style = Mono.copy(color = color, fontSize = fs, fontWeight = FontWeight.Bold))
        Text(song.formatLabel, style = Mono.copy(color = color, fontSize = fs))
    }
}

fun formatDuration(seconds: Int?): String {
    val s = seconds ?: 0
    val h = s / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

fun songsLabel(n: Int): String = if (n == 1) "1 canción" else "$n canciones"

fun formatMs(ms: Long): String = formatDuration((ms / 1000).toInt())

fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.2f GB".format(b / (1L shl 30).toDouble())
    else -> "%.0f MB".format(b / (1L shl 20).toDouble())
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = Display.copy(fontSize = 19.sp, color = Hifi.Text), modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

// ------------------------------------------------------------------ tarjetas y filas

/** @param nav si se da, el menú (clic derecho) incluye "Ir al artista". */
@Composable
fun AlbumCard(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier, nav: androidx.navigation.NavController? = null) {
    var menu by remember { mutableStateOf<DpOffset?>(null) }
    Box(modifier.onRightClick { menu = it }.bouncyClick(onClick = onClick).padding(6.dp)) {
        Column {
            CoverArt(album.coverArt, Modifier.fillMaxWidth().aspectRatio(1f).shadow(12.dp, RoundedCornerShape(10.dp)), size = 400, corner = 10.dp)
            Spacer(Modifier.height(8.dp))
            Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(album.artist, album.year?.toString()).joinToString(" · "),
                maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = Hifi.TextDim,
            )
        }
        AlbumMenu(album, menu != null, { menu = null }, nav, offset = menu ?: DpOffset.Zero)
    }
}

/** Corazón de "me gusta" sincronizado con Navidrome. */
@Composable
fun LikeButton(song: Song, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    if (song.isPreview) return
    val likes = LocalApp.current.likes
    val liked = song.id in likes.ids.collectAsState().value
    val tint by animateColorAsState(if (liked) Hifi.Accent else Hifi.TextDim, label = "like")
    IconButton(onClick = { likes.toggle(song) }, modifier) {
        Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Me gusta", tint = tint, modifier = Modifier.size(size))
    }
}

/**
 * Botón de descarga para una o varias canciones: muestra progreso, y si ya está todo
 * descargado ofrece quitarlo (con confirmación).
 */
@Composable
fun DownloadButton(songs: List<Song>, modifier: Modifier = Modifier) {
    val downloads = LocalApp.current.downloads ?: return
    val items by downloads.items.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    if (songs.isEmpty() || songs.any { it.isPreview }) return
    val states = songs.map { items[it.id] }
    val done = states.count { it?.done == true }
    val active = states.filterNotNull().filter { it.active }
    val failed = states.any { it?.failed == true }
    LaunchedEffect(active.isNotEmpty()) {
        while (active.isNotEmpty()) { downloads.pollProgress(); kotlinx.coroutines.delay(1000) }
    }
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        when {
            done == songs.size -> IconButton(onClick = { confirm = true }) {
                Icon(Icons.Default.DownloadForOffline, "Descargado", tint = Hifi.Accent)
            }
            active.isNotEmpty() -> {
                val progress = (done + active.sumOf { it.percent.toDouble() / 100 }).toFloat() / songs.size
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0.02f, 1f) }, modifier = Modifier.size(26.dp),
                    color = Hifi.Accent, trackColor = Hifi.Line, strokeWidth = 2.5.dp,
                )
            }
            else -> IconButton(onClick = { downloads.download(songs) }) {
                Icon(if (failed) Icons.Outlined.ErrorOutline else Icons.Outlined.Download, "Descargar", tint = if (failed) Hifi.LedRed else Hifi.TextDim)
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("¿Quitar descarga?") },
            text = { Text(if (songs.size == 1) "Se borra la copia local de esta canción." else "Se borran las ${songs.size} canciones del almacenamiento del teléfono.") },
            confirmButton = { TextButton(onClick = { downloads.remove(songs); confirm = false }) { Text("Quitar", color = Hifi.LedRed) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
            containerColor = Hifi.Panel,
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
    /** En una playlist: quitar esta canción de ella. */
    onRemove: (() -> Unit)? = null,
) {
    val c = LocalApp.current
    val liked = song.id in c.likes.ids.collectAsState().value
    val offline = c.downloads?.items?.collectAsState()?.value?.get(song.id)
    // Dónde abrir el menú (null = cerrado): donde picaste con clic derecho o junto al ⋮.
    var menu by remember { mutableStateOf<DpOffset?>(null) }
    var rowWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val dotsAt = { with(density) { DpOffset((rowWidth.toDp() - 236.dp).coerceAtLeast(0.dp), 0.dp) } }
    var addTo by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (addTo) AddToPlaylistDialog(listOf(song)) { addTo = false }
    Box(modifier.fillMaxWidth().onSizeChanged { rowWidth = it.width }) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isCurrent) Hifi.Accent.copy(alpha = 0.07f) else Color.Transparent)
            .onRightClick { menu = it }
            .combinedClickable(onClick = onClick, onLongClick = { menu = dotsAt() })
            .padding(start = 16.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            showCover -> CoverArt(song.coverArt, Modifier.size(46.dp), size = 120, corner = 6.dp)
            isCurrent -> Icon(Icons.Default.GraphicEq, null, tint = Hifi.Accent, modifier = Modifier.width(28.dp).size(18.dp))
            else -> Text(leading ?: "", style = Mono.copy(color = Hifi.TextFaint), modifier = Modifier.width(28.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) Hifi.Accent else Hifi.Text,
            )
            val sub = listOfNotNull(if (showArtist) song.artist else null, formatDuration(song.duration)).joinToString(" · ")
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (offline?.done == true) {
                    Icon(Icons.Default.DownloadForOffline, "Descargado", tint = Hifi.Accent, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = Hifi.TextDim, modifier = Modifier.weight(1f, fill = false))
                if (showFormat) { Spacer(Modifier.width(6.dp)); FormatBadge(song) }
            }
        }
        if (liked) Icon(Icons.Default.Favorite, "Me gusta", tint = Hifi.Accent, modifier = Modifier.size(15.dp))
        IconButton(onClick = { menu = dotsAt() }) { Icon(Icons.Default.MoreVert, null, tint = Hifi.TextFaint) }
    }
            DropdownMenu(
                expanded = menu != null, onDismissRequest = { menu = null }, offset = menu ?: DpOffset.Zero,
                containerColor = Hifi.PanelHi.copy(alpha = 0.96f), shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            ) {
                DropdownMenuItem(
                    text = { Text(if (liked) "Quitar de Me gusta" else "Me gusta") },
                    leadingIcon = { Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = Hifi.Accent) },
                    onClick = { c.likes.toggle(song); menu = null },
                )
                DropdownMenuItem(
                    text = { Text("Iniciar radio") },
                    leadingIcon = { Icon(Icons.Default.Radio, null) },
                    onClick = {
                        menu = null
                        // Como la radio de Spotify: esta canción y luego parecidas.
                        scope.launch { c.player.play(listOf(song) + c.api.similarSongs(song.id).filter { it.id != song.id }) }
                    },
                )
                DropdownMenuItem(
                    text = { Text("Reproducir siguiente") },
                    leadingIcon = { Icon(Icons.Default.SkipNext, null) },
                    onClick = { c.player.playNext(listOf(song)); menu = null },
                )
                DropdownMenuItem(
                    text = { Text("Agregar a la cola") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                    onClick = { c.player.enqueue(listOf(song)); menu = null },
                )
                DropdownMenuItem(
                    text = { Text("Agregar a playlist…") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                    onClick = { menu = null; addTo = true },
                )
                if (onRemove != null) {
                    DropdownMenuItem(
                        text = { Text("Quitar de esta playlist") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { menu = null; onRemove() },
                    )
                }
                val store = c.downloads
                if (store != null && (offline?.done == true || offline?.active == true)) {
                    DropdownMenuItem(
                        text = { Text("Quitar descarga") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { store.remove(listOf(song)); menu = null },
                    )
                } else if (store != null) {
                    DropdownMenuItem(
                        text = { Text("Descargar") },
                        leadingIcon = { Icon(Icons.Outlined.Download, null) },
                        onClick = { store.download(listOf(song)); menu = null },
                    )
                }
                if (onGoToAlbum != null && song.albumId != null) {
                    DropdownMenuItem(
                        text = { Text("Ir al álbum") },
                        leadingIcon = { Icon(Icons.Default.Album, null) },
                        onClick = { onGoToAlbum(); menu = null },
                    )
                }
            }
    }
}
