package com.marco.audiopro.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.data.info.AlbumSummary
import com.marco.audiopro.data.info.InfoSource
import com.marco.audiopro.data.subsonic.Album
import com.marco.audiopro.data.subsonic.ItemDate
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.khz
import com.marco.audiopro.platform.supportsBlur
import com.marco.audiopro.ui.components.AddToPlaylistDialog
import com.marco.audiopro.ui.components.CoverArt
import com.marco.audiopro.ui.components.DownloadButton
import com.marco.audiopro.ui.components.FormatBadge
import com.marco.audiopro.ui.components.LoadBox
import com.marco.audiopro.ui.components.PlayActions
import com.marco.audiopro.ui.components.ScreenBackButton
import com.marco.audiopro.ui.components.SectionHeader
import com.marco.audiopro.ui.components.TrackRow
import com.marco.audiopro.ui.components.bouncyClick
import com.marco.audiopro.ui.components.formatBytes
import com.marco.audiopro.ui.components.formatDuration
import com.marco.audiopro.ui.components.glass
import com.marco.audiopro.ui.components.rememberCoverColor
import com.marco.audiopro.ui.components.rememberLoad
import com.marco.audiopro.ui.openAlbum
import com.marco.audiopro.ui.openArtist
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Álbum: encabezado compacto y el tracklist. Toda la información (reseña, formato, créditos,
 * sello…) vive "dentro de la portada": al tocarla se abre en grande con su tarjeta.
 */
@Composable
fun AlbumScreen(id: String, nav: NavController) {
    val c = LocalApp.current
    val loader = rememberLoad(id) { c.api.album(id) }
    val playback by c.player.state.collectAsState()
    var details by remember { mutableStateOf(false) }
    LoadBox(loader, Modifier.statusBarsPadding()) { album ->
        val tint by rememberCoverColor(album.coverArt)
        val songs = album.song
        val multiDisc = songs.mapNotNull { it.discNumber }.distinct().size > 1
        val more = rememberLoad(album.artistId) { album.artistId?.let { c.api.artist(it).album.filter { it.id != album.id } } ?: emptyList() }

        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize().background(Brush.verticalGradient(0f to tint.copy(alpha = 0.7f), 0.45f to Color.Transparent)),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item { AlbumHeader(album, nav) { details = true } }
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
                        showFormat = s.formatLabel != songs.first().formatLabel,
                    )
                }
                item {
                    Text(
                        listOfNotNull(
                            album.releaseDate?.let(::longDate) ?: album.year?.toString(),
                            "${songs.size} canciones",
                            formatDuration(album.duration),
                            album.recordLabels?.firstOrNull()?.name?.takeIf { it.isNotBlank() }?.let { "℗ $it" },
                        ).joinToString(" · "),
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
            AnimatedVisibility(
                details,
                enter = fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.96f),
                exit = fadeOut(tween(180)) + scaleOut(tween(200), targetScale = 0.96f),
            ) { AlbumDetails(album, nav) { details = false } }
        }
    }
}

/** Encabezado chico: portada (se toca para ver todo), título, artista y acciones. */
@Composable
private fun AlbumHeader(album: Album, nav: NavController, onDetails: () -> Unit) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    var starred by remember(album.id) { mutableStateOf(album.starred != null) }
    var addTo by remember { mutableStateOf(false) }
    val songs = album.song
    if (addTo) AddToPlaylistDialog(songs) { addTo = false }
    BoxWithConstraints(Modifier.fillMaxWidth().statusBarsPadding()) {
        val wide = maxWidth > 640.dp
        Column {
            ScreenBackButton(nav)
            val cover = @Composable {
                Box {
                    CoverArt(
                        album.coverArt,
                        Modifier.size(if (wide) 220.dp else 210.dp).shadow(28.dp, RoundedCornerShape(12.dp)).bouncyClick(onClick = onDetails),
                        size = 800, corner = 12.dp,
                    )
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(8.dp).size(30.dp).shadow(6.dp, CircleShape)
                            .glass(CircleShape, Color.Black.copy(alpha = 0.55f)).bouncyClick(onClick = onDetails),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Info, "Ver toda la información", tint = Color.White, modifier = Modifier.size(16.dp)) }
                }
            }
            val info = @Composable { centered: Boolean ->
                Column(horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
                    Text(releaseKind(album).uppercase(), style = Mono.copy(color = Hifi.TextDim))
                    Text(album.name, style = Display.copy(fontSize = if (wide) 34.sp else 26.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        album.artist ?: "", fontSize = 17.sp, color = Hifi.Accent, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { album.artistId?.let(nav::openArtist) }.padding(vertical = 2.dp),
                    )
                    Text(
                        listOfNotNull(album.year?.toString(), genresOf(album).firstOrNull(), "${songs.size} canciones", formatDuration(album.duration)).joinToString(" · "),
                        fontSize = 13.sp, color = Hifi.TextDim,
                    )
                    Spacer(Modifier.height(8.dp))
                    FormatBadge(songs.firstOrNull(), large = true)
                }
            }
            if (wide) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                    cover(); Spacer(Modifier.width(24.dp)); info(false)
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    cover(); Spacer(Modifier.height(16.dp)); info(true)
                }
            }
            PlayActions(
                onPlay = { c.player.play(songs) },
                onShuffle = { c.player.play(songs, shuffle = true) },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
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
                IconButton(onClick = onDetails) { Icon(Icons.Default.Info, "Información del álbum", tint = Hifi.TextDim) }
            }
        }
    }
}

// ------------------------------------------------------------------ "dentro de la portada"

/**
 * La portada en grande sobre su propio color difuminado, con una tarjeta de vidrio que
 * tiene todo: reseña, fechas, formato, sello, créditos y tus números. Tocar fuera la cierra.
 */
@Composable
fun AlbumDetails(album: Album, nav: NavController, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onClose() } }) {
        CoverArt(
            album.coverArt,
            Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f }.then(if (supportsBlur) Modifier.blur(70.dp) else Modifier),
            size = 300, corner = 0.dp,
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (supportsBlur) 0.45f else 0.8f)))
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
            val wide = maxWidth > 760.dp
            if (wide) {
                val coverSize = minOf(maxHeight - 8.dp, maxWidth * 0.46f)
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    CoverArt(album.coverArt, Modifier.size(coverSize).shadow(40.dp, RoundedCornerShape(16.dp)).noTapThrough(), size = 1200, corner = 16.dp)
                    Spacer(Modifier.width(28.dp))
                    InfoCard(album, nav, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    CoverArt(album.coverArt, Modifier.fillMaxWidth().aspectRatio(1f).shadow(40.dp, RoundedCornerShape(16.dp)).noTapThrough(), size = 1200, corner = 16.dp)
                    Spacer(Modifier.height(20.dp))
                    InfoCard(album, nav, Modifier.fillMaxWidth(), scroll = false)
                }
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(14.dp).size(40.dp)
                .glass(CircleShape, Color.Black.copy(alpha = 0.5f)).bouncyClick(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Close, "Cerrar", tint = Color.White) }
    }
}

/** Para que tocar la portada o la tarjeta no cierre la vista. */
private fun Modifier.noTapThrough(): Modifier = this.clickable(MutableInteractionSource(), indication = null) {}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoCard(album: Album, nav: NavController, modifier: Modifier, scroll: Boolean = true) {
    val songs = album.song
    Column(
        modifier.widthIn(max = 720.dp).noTapThrough()
            .glass(RoundedCornerShape(24.dp), Color.Black.copy(alpha = 0.42f))
            .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(24.dp),
    ) {
        Text(listOfNotNull(releaseKind(album), album.year?.toString()).joinToString(" · ").uppercase(), style = Mono.copy(color = Hifi.Accent))
        Text(album.name, style = Display.copy(fontSize = 30.sp, color = Color.White))
        album.version?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 14.sp, color = Hifi.OnArt) }
        Text(
            album.artist ?: "", fontSize = 18.sp, color = Hifi.Accent, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { album.artistId?.let(nav::openArtist) }.padding(vertical = 2.dp),
        )

        // Ficha técnica.
        Spacer(Modifier.height(18.dp))
        val facts = buildList {
            album.releaseDate?.let(::longDate)?.let { add("Lanzamiento" to it) }
            album.originalReleaseDate?.takeIf { it != album.releaseDate }?.let(::longDate)?.let { add("Lanzamiento original" to it) }
            genresOf(album).takeIf { it.isNotEmpty() }?.let { add((if (it.size > 1) "Géneros" else "Género") to it.joinToString(", ")) }
            album.recordLabels?.mapNotNull { it.name.takeIf { n -> n.isNotBlank() } }?.takeIf { it.isNotEmpty() }?.let { add("Sello" to it.joinToString(", ")) }
            add("Formato" to formatOf(songs))
            add("Calidad" to qualityOf(songs))
            add("Canciones" to buildString {
                append(songs.size)
                val discs = songs.mapNotNull { it.discNumber }.distinct().size
                if (discs > 1) append(" · $discs discos")
            })
            add("Duración" to formatDuration(album.duration))
            songs.sumOf { it.size ?: 0L }.takeIf { it > 0 }?.let { add("Tamaño" to formatBytes(it)) }
            songs.firstNotNullOfOrNull { it.replayGain?.albumGain }?.let { add("ReplayGain" to "%+.1f dB (álbum)".format(it)) }
            album.moods?.takeIf { it.isNotEmpty() }?.let { add("Ambiente" to it.joinToString(", ")) }
            album.playCount?.takeIf { it > 0 }?.let { add("Lo has puesto" to "$it ${if (it == 1L) "vez" else "veces"}") }
            album.played?.let(::isoDate)?.let { add("Última vez" to it) }
            album.created?.let(::isoDate)?.let { add("En tu biblioteca desde" to it) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            facts.forEach { (k, v) -> Fact(k, v) }
        }

        // Créditos (quién lo produjo, mezcló, compuso…), si los archivos los traen.
        val credits = creditsOf(songs)
        if (credits.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Text("CRÉDITOS", style = Mono.copy(color = Hifi.Text, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            credits.forEach { (role, names) ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text(role, fontSize = 13.sp, color = Hifi.TextDim, modifier = Modifier.width(130.dp))
                    Text(names.joinToString(", "), fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        AboutAlbum(album)

        Spacer(Modifier.height(10.dp))
        Text("Datos de tu servidor (etiquetas de los archivos) y de Last.fm.", style = Mono.copy(color = Hifi.TextFaint, fontSize = 9.sp))
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column(
        Modifier.widthIn(min = 150.dp, max = 320.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label.uppercase(), style = Mono.copy(color = Hifi.TextDim, fontSize = 9.sp))
        Text(value, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

/** La reseña completa (Last.fm → Navidrome → IA) con etiquetas y oyentes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutAlbum(album: Album) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    var summary by remember(album.id) { mutableStateOf<AlbumSummary?>(null) }
    var loading by remember(album.id) { mutableStateOf(true) }
    LaunchedEffect(album.id) {
        summary = runCatching { c.albumInfo.summary(album) }.getOrElse { AlbumSummary(error = it.message) }
        loading = false
    }
    val s = summary
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
        s?.text != null -> Text(s.text, fontSize = 15.sp, lineHeight = 23.sp, color = Color.White.copy(alpha = 0.9f))
        else -> Text(s?.error ?: "No hay reseña de este álbum.", color = Hifi.TextDim, fontSize = 13.sp)
    }
    if (s != null && s.tags.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            s.tags.forEach { t ->
                Text(
                    t.lowercase(), fontSize = 11.sp, color = Hifi.Cream,
                    modifier = Modifier.background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
    if (s?.listeners != null) {
        Spacer(Modifier.height(10.dp))
        val nf = NumberFormat.getIntegerInstance()
        Text("${nf.format(s.listeners)} oyentes · ${nf.format(s.playcount ?: 0)} reproducciones en Last.fm", style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp))
    }
    val hasKey = c.settings.prefs.collectAsState().value.anthropicKey.isNotBlank()
    if (!loading && hasKey && (s?.text == null || s.source == InfoSource.CLAUDE)) {
        TextButton(onClick = {
            loading = true
            scope.launch { summary = c.albumInfo.generateWithClaude(album, s ?: AlbumSummary()); loading = false }
        }) {
            Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = Hifi.Accent)
            Spacer(Modifier.width(6.dp))
            Text(if (s?.source == InfoSource.CLAUDE) "Regenerar con IA" else "Generar reseña con IA", color = Hifi.Accent)
        }
    }
}

// ------------------------------------------------------------------ cómo se cuenta cada dato

private val es = Locale.forLanguageTag("es-MX")

private fun releaseKind(a: Album): String = when {
    a.isCompilation == true -> "Recopilación"
    a.releaseTypes.orEmpty().any { it.equals("ep", true) } -> "EP"
    a.releaseTypes.orEmpty().any { it.equals("single", true) } -> "Sencillo"
    a.releaseTypes.orEmpty().any { it.equals("live", true) } -> "En vivo"
    else -> "Álbum"
}

private fun genresOf(a: Album) = a.genres?.map { it.name }?.filter { it.isNotBlank() }.orEmpty().ifEmpty { listOfNotNull(a.genre) }

private val months = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

private fun longDate(d: ItemDate): String? {
    val y = d.year?.takeIf { it > 0 } ?: return null
    val m = d.month?.let { months.getOrNull(it - 1) } ?: return "$y"
    return if (d.day != null && d.day > 0) "${d.day} de $m de $y" else "$m de $y"
}

/** "2024-03-01T12:00:00Z" → "1 de marzo de 2024". */
private fun isoDate(s: String): String? = runCatching {
    val p = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s.take(10)) ?: return null
    SimpleDateFormat("d 'de' MMMM 'de' yyyy", es).format(p)
}.getOrNull()

private fun formatOf(songs: List<Song>): String {
    val kinds = songs.map { s ->
        buildList {
            add((s.suffix ?: "?").uppercase())
            val bd = s.bitDepth ?: 0
            val sr = s.samplingRate ?: 0
            if (bd > 0 && sr > 0) add("$bd-bit / ${khz(sr)} kHz")
            else if ((s.bitRate ?: 0) > 0) add("${s.bitRate} kbps")
        }.joinToString(" · ")
    }.distinct()
    val ch = songs.mapNotNull { it.channelCount }.distinct().singleOrNull()?.let { if (it == 2) "estéreo" else if (it == 1) "mono" else "$it canales" }
    val base = if (kinds.size <= 1) kinds.firstOrNull() ?: "—" else "Mixto (${kinds.size} formatos)"
    return base + (ch?.let { " · $it" } ?: "")
}

private fun qualityOf(songs: List<Song>): String = when {
    songs.isEmpty() -> "—"
    songs.all { it.isHiRes } -> "Hi-Res lossless"
    songs.all { it.isLossless } -> "Lossless (calidad CD)"
    songs.none { it.isLossless } -> "Con pérdida · ~${songs.mapNotNull { it.bitRate }.average().toInt()} kbps"
    else -> "Mixta"
}

private val roleNames = mapOf(
    "composer" to "Composición", "lyricist" to "Letra", "producer" to "Producción", "engineer" to "Ingeniería",
    "mixer" to "Mezcla", "remixer" to "Remix", "arranger" to "Arreglos", "conductor" to "Dirección",
    "performer" to "Intérpretes", "djmixer" to "DJ", "director" to "Dirección", "masteringengineer" to "Masterización",
)

/** Créditos de todas las canciones, agrupados por rol (sin el artista principal). */
private fun creditsOf(songs: List<Song>): List<Pair<String, List<String>>> {
    val byRole = LinkedHashMap<String, LinkedHashSet<String>>()
    for (s in songs) {
        for (c in s.contributors.orEmpty()) {
            val role = c.role.lowercase()
            if (role == "artist" || role == "albumartist" || c.artist.name.isBlank()) continue
            val label = roleNames[role] ?: role.replaceFirstChar { it.uppercase() }
            val name = c.artist.name + (c.subRole?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
            byRole.getOrPut(label) { LinkedHashSet() } += name
        }
        if (s.contributors.isNullOrEmpty()) s.displayComposer?.takeIf { it.isNotBlank() }?.let { byRole.getOrPut("Composición") { LinkedHashSet() } += it }
    }
    return byRole.map { (k, v) -> k to v.take(12) }
}
