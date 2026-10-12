package com.marco.audiopro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.data.subsonic.Album
import com.marco.audiopro.data.subsonic.Playlist
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.platform.rememberImagePicker
import com.marco.audiopro.ui.openArtist
import com.marco.audiopro.ui.openPlaylist
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Para acciones que siguen después de cerrar el diálogo que las lanzó. */
private val actionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

// ------------------------------------------------------------------ mouse

/**
 * Clic derecho (mouse). Entrega dónde fue el clic como desplazamiento para un DropdownMenu
 * anclado a este mismo elemento, así el menú sale justo donde picaste.
 */
fun Modifier.onRightClick(action: (DpOffset) -> Unit): Modifier = composed {
    val current by rememberUpdatedState(action)
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent()
                if (e.type == PointerEventType.Press && e.buttons.isSecondaryPressed) {
                    val pos = e.changes.firstOrNull()?.position ?: continue
                    e.changes.forEach { it.consume() }
                    // El DropdownMenu se coloca debajo del ancla: se compensa la altura.
                    current(DpOffset(pos.x.toDp(), (pos.y - size.height).toDp()))
                }
            }
        }
    }
}

/**
 * Cuántos campos de texto tienen el foco. En la PC, Espacio pausa como en Spotify salvo mientras
 * escribes en uno de ellos (búsqueda, nombres de playlist, ajustes…).
 */
object TextInputFocus {
    @Volatile var count = 0
    val active: Boolean get() = count > 0
}

/** Marca un campo de texto para [TextInputFocus]. */
fun Modifier.textInput(): Modifier = composed {
    val had = remember { booleanArrayOf(false) }
    DisposableEffect(Unit) { onDispose { if (had[0]) { had[0] = false; TextInputFocus.count-- } } }
    onFocusChanged { f ->
        if (f.isFocused != had[0]) {
            had[0] = f.isFocused
            TextInputFocus.count += if (f.isFocused) 1 else -1
        }
    }
}

/** Rueda del mouse = volumen (sólo donde el reproductor maneja volumen, o sea en la PC). */
fun Modifier.volumeWheel(): Modifier = composed {
    val player = LocalApp.current.player
    val volume = player.volume
    if (volume == null) Modifier else Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent()
                if (e.type == PointerEventType.Scroll) {
                    val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (dy != 0f) {
                        player.setVolume((volume.value - dy * 0.04f).coerceIn(0f, 1f))
                        e.changes.forEach { it.consume() }
                    }
                }
            }
        }
    }
}

/**
 * Flecha de regresar de cada pantalla: sólo en el celular. En la PC ya está la de la barra
 * de arriba (y los botones laterales del mouse), así que no se repite.
 */
@Composable
fun ScreenBackButton(nav: NavController, modifier: Modifier = Modifier) {
    if (LocalApp.current.isDesktop) return
    IconButton(onClick = { nav.popBackStack() }, modifier) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
}

// ------------------------------------------------------------------ diálogos de vidrio

@Composable
fun GlassDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(20.dp).widthIn(max = 460.dp).fillMaxWidth()
                .shadow(30.dp, RoundedCornerShape(26.dp))
                .glass(RoundedCornerShape(26.dp), Color(0xFF16171D).copy(alpha = 0.97f))
                .padding(22.dp),
            content = content,
        )
    }
}

/** Nombre de una playlist nueva (o renombrar). */
@Composable
fun PlaylistNameDialog(title: String, initial: String = "", confirm: String = "Crear", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    GlassDialog(onDismiss) {
        Text(title, style = Display.copy(fontSize = 22.sp))
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            name, { name = it }, singleLine = true, placeholder = { Text("Mi playlist") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name.trim()) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).textInput(),
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Hifi.TextDim) }
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirm, color = Hifi.Accent, fontWeight = FontWeight.Bold) }
        }
    }
}

/** "Agregar a playlist": tus playlists + crear una nueva con estas canciones. */
@Composable
fun AddToPlaylistDialog(songs: List<Song>, onDismiss: () -> Unit) {
    val c = LocalApp.current
    val lists by c.playlists.lists.collectAsState()
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { c.playlists.refresh() } }
    if (creating) {
        PlaylistNameDialog("Nueva playlist", onDismiss = onDismiss) { name ->
            actionScope.launch {
                runCatching { c.playlists.create(name, songs) }
                    .onSuccess { c.connect.notice.value = "Creada “$name”" }
                    .onFailure { c.connect.notice.value = "No se pudo crear: ${it.message}" }
            }
            onDismiss()
        }
        return
    }
    GlassDialog(onDismiss) {
        Text("Agregar a playlist", style = Display.copy(fontSize = 22.sp))
        Text(
            if (songs.size == 1) songs.first().title else songsLabel(songs.size), color = Hifi.TextDim, fontSize = 13.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).bouncyClick { creating = true }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, null, tint = Hifi.Text)
            }
            Spacer(Modifier.width(12.dp))
            Text("Nueva playlist", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(Modifier.heightIn(max = 380.dp)) {
            items(lists, key = { it.id }) { p ->
                PlaylistRowItem(p) {
                    actionScope.launch {
                        runCatching { c.playlists.add(p.id, songs) }
                            .onSuccess { c.connect.notice.value = "Agregada a “${p.name}”" }
                            .onFailure { c.connect.notice.value = "No se pudo agregar: ${it.message}" }
                    }
                    onDismiss()
                }
            }
        }
    }
}

@Composable
fun PlaylistRowItem(p: Playlist, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).bouncyClick(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(p.coverArt, Modifier.size(44.dp), size = 150, corner = 8.dp, customKey = com.marco.audiopro.data.CustomCovers.playlist(p.id))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(songsLabel(p.songCount ?: 0), fontSize = 12.sp, color = Hifi.TextDim)
        }
    }
}

// ------------------------------------------------------------------ portadas propias

/** Portada de "Me gusta": la tuya si pusiste una; si no, el degradado con el corazón. */
@Composable
fun LikedCover(modifier: Modifier = Modifier, corner: Dp = 12.dp) {
    val c = LocalApp.current
    c.covers.map.collectAsState().value
    val file = c.covers.get(com.marco.audiopro.data.CustomCovers.LIKED)
    Box(
        modifier.clip(RoundedCornerShape(corner)).background(Brush.linearGradient(listOf(Hifi.Accent, Hifi.AccentDim, Hifi.Panel))),
        contentAlignment = Alignment.Center,
    ) {
        if (file != null) AsyncImage(file, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Default.Favorite, null, tint = Color.White, modifier = Modifier.fillMaxSize(0.42f))
    }
}

/** Botón ✎ sobre una portada: elegir una imagen propia o volver a la original. */
@Composable
fun CoverEditButton(key: String, modifier: Modifier = Modifier) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    c.covers.map.collectAsState().value
    val has = c.covers.get(key) != null
    var menu by remember { mutableStateOf(false) }
    val pick = rememberImagePicker { bytes -> actionScope.launch { c.covers.set(key, bytes) } }
    Box(modifier) {
        Box(
            Modifier.size(32.dp).shadow(8.dp, CircleShape).glass(CircleShape, Color.Black.copy(alpha = 0.55f))
                .bouncyClick { if (has) menu = true else pick() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Edit, "Cambiar imagen", tint = Color.White, modifier = Modifier.size(16.dp)) }
        AppMenu(menu, { menu = false }) {
            AppMenuItem("Cambiar imagen…", Icons.Default.Image) { menu = false; pick() }
            AppMenuItem("Usar la original", Icons.Default.Delete) { menu = false; scope.launch { c.covers.clear(key) } }
        }
    }
}

// ------------------------------------------------------------------ menú de álbum (clic derecho)

/** Acciones de un álbum sin abrirlo: se cargan sus canciones al elegir la opción. */
@Composable
fun AlbumMenu(album: Album, expanded: Boolean, onDismiss: () -> Unit, nav: NavController? = null, offset: DpOffset = DpOffset.Zero) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    var addTo by remember { mutableStateOf<List<Song>?>(null) }
    fun withSongs(block: (List<Song>) -> Unit) {
        onDismiss()
        scope.launch { runCatching { c.api.album(album.id).song }.onSuccess { if (it.isNotEmpty()) block(it) } }
    }
    AppMenu(expanded, onDismiss, offset) {
        AppMenuItem("Reproducir", Icons.Default.PlayArrow) { withSongs { c.player.play(it) } }
        AppMenuItem("Aleatorio", Icons.Default.Shuffle) { withSongs { c.player.play(it, shuffle = true) } }
        AppMenuDivider()
        AppMenuItem("Reproducir siguiente", Icons.Default.SkipNext) { withSongs { c.player.playNext(it) } }
        AppMenuItem("Agregar a la cola", Icons.AutoMirrored.Filled.QueueMusic) { withSongs { c.player.enqueue(it) } }
        AppMenuItem("Agregar a playlist…", Icons.AutoMirrored.Filled.PlaylistAdd) { withSongs { addTo = it } }
        c.downloads?.let { store ->
            AppMenuItem("Descargar", Icons.Default.Download) { withSongs { store.download(it) } }
        }
        if (nav != null && album.artistId != null) {
            AppMenuDivider()
            AppMenuItem("Ir al artista", Icons.Default.Person) { onDismiss(); nav.openArtist(album.artistId) }
        }
    }
    addTo?.let { AddToPlaylistDialog(it) { addTo = null } }
}

/** Crear playlist vacía y abrirla (desde la barra lateral o la biblioteca). */
@Composable
fun NewPlaylistFlow(nav: NavController, onDone: () -> Unit) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    PlaylistNameDialog("Nueva playlist", onDismiss = onDone) { name ->
        onDone()
        actionScope.launch {
            runCatching { c.playlists.create(name) }
                .onSuccess { p -> if (p.id.isNotBlank()) nav.openPlaylist(p.id) }
                .onFailure { c.connect.notice.value = "No se pudo crear: ${it.message}" }
        }
    }
}
