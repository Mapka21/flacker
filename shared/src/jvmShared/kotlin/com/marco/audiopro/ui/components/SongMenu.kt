package com.marco.audiopro.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SendToMobile
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.marco.audiopro.core.AppGraph
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.ui.openAlbum
import com.marco.audiopro.ui.openArtist
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Menú de una canción (lo mismo con clic derecho en cualquier lado donde aparezca: la tarjeta de
 * "Reproduciendo", el mini reproductor, el reproductor completo…).
 */
@Composable
fun SongMenu(song: Song, expanded: Boolean, onDismiss: () -> Unit, nav: NavController?, offset: DpOffset = DpOffset.Zero) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    val liked = song.id in c.likes.ids.collectAsState().value
    var addTo by remember { mutableStateOf(false) }
    var story by remember { mutableStateOf(false) }
    var sendTo by remember { mutableStateOf(false) }
    if (addTo) AddToPlaylistDialog(listOf(song)) { addTo = false }
    if (sendTo) SendToDialog(listOf(song)) { sendTo = false }
    if (story) ShareStoryDialog(song) { story = false }
    AppMenu(expanded, onDismiss, offset, header = { MenuHeader(song.coverArt, song.title, song.artist) }) {
        if (!song.isPreview) {
            AppMenuItem(
                if (liked) "Quitar de Me gusta" else "Me gusta", if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                iconTint = if (liked) Hifi.Accent else null,
            ) { c.likes.toggle(song); onDismiss() }
            AppMenuItem("Agregar a playlist…", Icons.AutoMirrored.Filled.PlaylistAdd) { onDismiss(); addTo = true }
            AppMenuDivider()
        }
        AppMenuItem("Reproducir siguiente", Icons.Default.SkipNext) { c.player.playNext(listOf(song)); onDismiss() }
        AppMenuItem("Agregar a la cola", Icons.AutoMirrored.Filled.QueueMusic) { c.player.enqueue(listOf(song)); onDismiss() }
        if (!song.isPreview) {
            AppMenuItem("Iniciar radio", Icons.Default.Radio) {
                onDismiss()
                scope.launch { c.player.play(listOf(song) + c.api.similarSongs(song.id).filter { it.id != song.id }) }
            }
        }
        c.downloads?.takeIf { !song.isPreview }?.let { store ->
            AppMenuItem("Descargar", Icons.Outlined.Download) { store.download(listOf(song)); onDismiss() }
        }
        AppMenuItem("Compartir en historia", Icons.Default.IosShare) { onDismiss(); story = true }
        if (!song.isPreview && !song.isRadio) AppMenuItem("Mandar a…", Icons.Default.SendToMobile) { onDismiss(); sendTo = true }
        if (nav != null && (song.albumId != null || song.artistId != null)) {
            AppMenuDivider()
            song.albumId?.let { id -> AppMenuItem("Ir al álbum", Icons.Default.Album) { onDismiss(); nav.openAlbum(id) } }
            song.artistId?.let { id -> AppMenuItem("Ir al artista", Icons.Default.Person) { onDismiss(); nav.openArtist(id) } }
        }
    }
}

/**
 * "Aleatorio de toda tu música", modo chill: pone canciones al azar de toda la biblioteca y,
 * cuando quedan pocas en la cola, agrega más solas. Se apaga en cuanto pones otra cosa.
 */
object LibraryShuffle {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var anchor: String? = null
    private var refilling = false

    fun start(c: AppGraph) {
        scope.launch {
            runCatching { c.api.randomSongs(150) }.onSuccess { songs ->
                if (songs.isEmpty()) return@onSuccess
                anchor = songs.first().id
                c.player.play(songs)
                c.connect.notice.value = "Aleatorio de toda tu música"
            }.onFailure { c.connect.notice.value = "No se pudo: ${it.message}" }
        }
    }

    /** Vigila la cola (se arranca una vez desde la raíz de la app). */
    suspend fun keepAlive(c: AppGraph) {
        c.player.state.collect { st ->
            val a = anchor ?: return@collect
            if (st.queue.firstOrNull()?.id != a) { if (st.queue.isNotEmpty()) anchor = null; return@collect }
            if (!refilling && st.currentIndex >= 0 && st.queue.size - st.currentIndex <= 5) {
                refilling = true
                scope.launch {
                    val have = st.queue.mapTo(HashSet()) { it.id }
                    runCatching { c.api.randomSongs(100) }.onSuccess { more -> c.player.enqueue(more.filter { it.id !in have }) }
                    refilling = false
                }
            }
        }
    }
}

/**
 * Índice A-Z a un lado (como en Contactos o Apple Music): tocar o arrastrar sobre una letra
 * salta a esa sección.
 */
@Composable
fun AlphabetIndex(letters: List<Char>, modifier: Modifier = Modifier, onPick: (Char) -> Unit) {
    if (letters.size < 2) return
    var active by remember { mutableStateOf<Char?>(null) }
    fun pick(y: Float, h: Int) {
        val i = ((y / h) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        if (letters[i] != active) { active = letters[i]; onPick(letters[i]) }
    }
    Column(
        modifier.width(22.dp).fillMaxHeight().padding(vertical = 8.dp)
            .pointerInput(letters) { detectTapGestures { o -> pick(o.y, size.height); active = null } }
            .pointerInput(letters) { detectVerticalDragGestures(onDragEnd = { active = null }) { ch, _ -> pick(ch.position.y, size.height) } },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { l ->
            Text(
                l.toString(), style = Mono.copy(color = if (l == active) Hifi.Accent else Hifi.TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}
