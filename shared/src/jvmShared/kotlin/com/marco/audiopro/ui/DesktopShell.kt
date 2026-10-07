package com.marco.audiopro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.player.RepeatMode
import com.marco.audiopro.ui.components.AmbientBackground
import com.marco.audiopro.ui.components.ConnectToast
import com.marco.audiopro.ui.components.CoverArt
import com.marco.audiopro.ui.components.DevicesButton
import com.marco.audiopro.ui.components.LikeButton
import com.marco.audiopro.ui.components.Load
import com.marco.audiopro.ui.components.RemotePlaybackBanner
import com.marco.audiopro.ui.components.bouncyClick
import com.marco.audiopro.ui.components.formatDuration
import com.marco.audiopro.ui.components.formatMs
import com.marco.audiopro.ui.components.glass
import com.marco.audiopro.ui.components.rememberLoad
import com.marco.audiopro.ui.screens.AppleBar
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono

/** Paneles flotantes de la PC: vidrio oscuro con esquinas grandes. */
private val PanelShape = RoundedCornerShape(26.dp)
private fun Modifier.panel() = this.glass(PanelShape, Color.Black.copy(alpha = 0.30f))

/**
 * Cáscara de PC: barra lateral | contenido | Reproduciendo + A continuación, todo en paneles
 * de vidrio flotando sobre el fondo vivo. En ventanas angostas la columna derecha se vuelve
 * una píldora flotante.
 * @param onMiniPlayer si no es null, aparece el botón para pasar al miniplayer (iPod).
 */
@Composable
fun DesktopRoot(nav: NavHostController = rememberNavController(), onMiniPlayer: (() -> Unit)? = null) =
    MarcoGate { DesktopShell(nav, onMiniPlayer) }

@Composable
private fun DesktopShell(nav: NavHostController, onMiniPlayer: (() -> Unit)?) {
    val c = LocalApp.current
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val playback by c.player.state.collectAsState()
    val onPlayer = dest.isIn(PlayerRoute::class)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1240.dp
        AmbientBackground(playback.current?.coverArt, intensity = 0.75f)
        Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Sidebar(nav, entry, dest)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                TopBar(nav, onMiniPlayer)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.weight(1f).fillMaxWidth().panel()) { AppNavHost(nav, Modifier.fillMaxSize()) }
            }
            AnimatedVisibility(
                wide && playback.current != null,
                enter = expandHorizontally(spring(dampingRatio = 0.85f, stiffness = 260f)) + fadeIn(),
                exit = shrinkHorizontally() + fadeOut(),
            ) { RightColumn(nav) }
        }
        AnimatedVisibility(
            !wide && playback.current != null && !onPlayer,
            enter = slideInVertically(spring(dampingRatio = 0.75f, stiffness = 300f)) { it * 2 } + fadeIn(),
            exit = slideOutVertically { it * 2 } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 30.dp, bottom = 30.dp),
        ) { PillPlayer(nav) }
        ConnectToast(Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp))
    }
}

// ------------------------------------------------------------------ barra lateral

private fun NavHostController.openLibrary(tab: Int) = navigate(LibraryRoute(tab)) {
    popUpTo(graph.findStartDestination().id)
    launchSingleTop = true
}

@Composable
private fun Sidebar(nav: NavHostController, entry: NavBackStackEntry?, dest: NavDestination?) {
    val c = LocalApp.current
    val libTab = if (dest.isIn(LibraryRoute::class)) runCatching { entry?.toRoute<LibraryRoute>()?.tab }.getOrNull() else null
    val playlists = rememberLoad(Unit) { c.api.playlists() }

    Column(Modifier.width(236.dp).fillMaxHeight().panel().padding(vertical = 22.dp)) {
        Column(Modifier.padding(horizontal = 22.dp)) {
            Text("MARCO", style = Mono.copy(color = Hifi.Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp))
            Text("AUDIO PRO", style = Mono.copy(color = Hifi.Cream, fontSize = 10.sp, letterSpacing = 5.sp))
        }
        SectionLabel("MENÚ")
        SidebarItem(Icons.Default.Home, "Inicio", dest.isIn(HomeRoute::class)) { nav.openTab(HomeRoute) }
        SidebarItem(Icons.Default.Search, "Buscar", dest.isIn(SearchRoute::class)) { nav.openTab(SearchRoute) }
        SidebarItem(Icons.Default.Equalizer, "Ecualizador", dest.isIn(DspRoute::class)) { nav.openTab(DspRoute) }
        SectionLabel("MI BIBLIOTECA")
        SidebarItem(Icons.Default.Album, "Álbumes", libTab == 0) { nav.openLibrary(0) }
        SidebarItem(Icons.Default.Person, "Artistas", libTab == 1) { nav.openLibrary(1) }
        SidebarItem(Icons.Default.Favorite, "Me gusta", dest.isIn(LikedRoute::class)) { nav.openTab(LikedRoute) }
        SidebarItem(Icons.Default.LibraryMusic, "Playlists", libTab == 2) { nav.openLibrary(2) }
        SectionLabel("TUS PLAYLISTS")
        val lists = (playlists.state.value as? Load.Ok)?.value.orEmpty()
        LazyColumn(Modifier.weight(1f)) {
            items(lists, key = { it.id }) { p ->
                val sel = dest.isIn(PlaylistRoute::class) && runCatching { entry?.toRoute<PlaylistRoute>()?.id }.getOrNull() == p.id
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 1.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (sel) Color.White.copy(alpha = 0.10f) else Color.Transparent)
                        .bouncyClick { nav.openPlaylist(p.id) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverArt(p.coverArt, Modifier.size(28.dp), size = 120, corner = 6.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(p.name, fontSize = 13.sp, color = if (sel) Hifi.Text else Hifi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        SidebarItem(Icons.Default.Settings, "Ajustes", dest.isIn(SettingsRoute::class)) { nav.openTab(SettingsRoute) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp, letterSpacing = 2.sp), modifier = Modifier.padding(start = 24.dp, top = 22.dp, bottom = 6.dp))
}

@Composable
private fun SidebarItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(if (selected) Hifi.Accent else Hifi.TextDim, label = "side")
    Row(
        Modifier.padding(horizontal = 10.dp, vertical = 1.dp).fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Hifi.Accent.copy(alpha = 0.14f) else Color.Transparent)
            .bouncyClick(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Rayita de color a la izquierda del seleccionado (como Resso).
        Box(Modifier.width(3.dp).height(16.dp).clip(CircleShape).background(if (selected) Hifi.Accent else Color.Transparent))
        Spacer(Modifier.width(9.dp))
        Icon(icon, null, tint = color, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = if (selected) Hifi.Text else Hifi.TextDim, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ------------------------------------------------------------------ barra superior

@Composable
private fun TopBar(nav: NavHostController, onMiniPlayer: (() -> Unit)?) {
    val c = LocalApp.current
    val playing = c.player.state.collectAsState().value.current != null
    Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundGlass(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") { nav.popBackStack() }
        Spacer(Modifier.width(12.dp))
        Row(
            Modifier.width(420.dp).height(44.dp).glass(RoundedCornerShape(50), Color.Black.copy(alpha = 0.28f))
                .bouncyClick { nav.openTab(SearchRoute) }.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, null, tint = Hifi.TextDim, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("Busca artistas, álbumes o canciones", color = Hifi.TextFaint, fontSize = 13.sp)
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.glass(CircleShape, Color.Black.copy(alpha = 0.28f))) { DevicesButton() }
        if (onMiniPlayer != null && playing) {
            Spacer(Modifier.width(10.dp))
            RoundGlass(Icons.Default.PictureInPictureAlt, "Miniplayer iPod", onClick = onMiniPlayer)
        }
        Spacer(Modifier.width(10.dp))
        RoundGlass(Icons.Default.Settings, "Ajustes") { nav.openTab(SettingsRoute) }
    }
}

@Composable
private fun RoundGlass(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).glass(CircleShape, Color.Black.copy(alpha = 0.28f)).bouncyClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = Hifi.OnArt, modifier = Modifier.size(20.dp)) }
}

// ------------------------------------------------------------------ columna derecha

@Composable
private fun RightColumn(nav: NavHostController) {
    Column(Modifier.width(340.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RemotePlaybackBanner()
        NowPlayingCard(nav)
        UpNextCard(Modifier.weight(1f))
    }
}

@Composable
private fun NowPlayingCard(nav: NavHostController) {
    val c = LocalApp.current
    val player = c.player
    val st by player.state.collectAsState()
    val song = st.current ?: return
    val pos by rememberPlaybackPosition(200)
    var dragging by remember { mutableStateOf<Float?>(null) }
    val dur = st.durationMs.coerceAtLeast(1)
    val value = dragging ?: (pos.toFloat() / dur).coerceIn(0f, 1f)
    val tint by rememberCoverTint(song.coverArt, Color.Black, 0.55f)

    Column(Modifier.fillMaxWidth().glass(PanelShape, tint.copy(alpha = 0.55f)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("REPRODUCIENDO", style = Mono.copy(color = Hifi.OnArt.copy(alpha = 0.6f), fontSize = 10.sp, letterSpacing = 2.sp), modifier = Modifier.weight(1f))
            Icon(Icons.Default.OpenInFull, "Vista completa", tint = Hifi.OnArt, modifier = Modifier.size(16.dp).bouncyClick { nav.openPlayer() })
        }
        Spacer(Modifier.height(12.dp))
        CoverArt(
            song.coverArt,
            Modifier.fillMaxWidth().aspectRatio(1f).shadow(26.dp, RoundedCornerShape(18.dp)).bouncyClick { nav.openPlayer() },
            size = 800, corner = 18.dp,
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(song.title, style = Display.copy(fontSize = 19.sp, color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    song.artist ?: "", fontSize = 14.sp, color = Hifi.OnArt, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.bouncyClick { song.artistId?.let(nav::openArtist) },
                )
            }
            LikeButton(song, size = 22.dp)
        }
        Spacer(Modifier.height(8.dp))
        AppleBar(value, onChange = { dragging = it }, onCommit = { v -> player.seekTo((v * dur).toLong()); dragging = null })
        Row(Modifier.fillMaxWidth()) {
            Text(formatMs((value * dur).toLong()), style = Mono.copy(color = Hifi.OnArt.copy(alpha = 0.6f), fontSize = 10.sp), modifier = Modifier.weight(1f))
            Text("-" + formatMs(dur - (value * dur).toLong()), style = Mono.copy(color = Hifi.OnArt.copy(alpha = 0.6f), fontSize = 10.sp))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SmallToggle(Icons.Default.Shuffle, st.shuffle) { player.toggleShuffle() }
            Icon(Icons.Default.SkipPrevious, "Anterior", tint = Color.White, modifier = Modifier.size(34.dp).bouncyClick { player.previous() })
            Box(Modifier.size(56.dp).shadow(14.dp, CircleShape).clip(CircleShape).background(Color.White).bouncyClick { player.toggle() }, contentAlignment = Alignment.Center) {
                if (st.isBuffering) CircularProgressIndicator(Modifier.size(24.dp), color = Color.Black, strokeWidth = 2.dp)
                else Icon(if (st.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Reproducir", tint = Color.Black, modifier = Modifier.size(30.dp))
            }
            Icon(Icons.Default.SkipNext, "Siguiente", tint = Color.White, modifier = Modifier.size(34.dp).bouncyClick { player.next() })
            SmallToggle(if (st.repeat == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, st.repeat != RepeatMode.OFF) { player.cycleRepeat() }
        }
        player.volume?.let { flow ->
            val vol by flow.collectAsState()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.VolumeDown, null, tint = Hifi.OnArt, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                AppleBar(vol, onChange = { player.setVolume(it) }, onCommit = { player.setVolume(it) }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Hifi.OnArt, modifier = Modifier.size(16.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.openPlayer() }) { Icon(Icons.Default.Lyrics, "Letra y visualizadores", tint = Hifi.OnArt, modifier = Modifier.size(20.dp)) }
            IconButton(onClick = { nav.openTab(DspRoute) }) { Icon(Icons.Default.Equalizer, "Ecualizador", tint = Hifi.OnArt, modifier = Modifier.size(20.dp)) }
            DevicesButton(size = 20)
        }
    }
}

@Composable
private fun SmallToggle(icon: ImageVector, on: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) Color.White.copy(alpha = 0.22f) else Color.Transparent, label = "st")
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(bg).bouncyClick(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (on) Color.White else Hifi.OnArt.copy(alpha = 0.55f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun UpNextCard(modifier: Modifier) {
    val player = LocalApp.current.player
    val st by player.state.collectAsState()
    val next = st.queue.withIndex().drop(st.currentIndex + 1).take(40)
    Column(modifier.fillMaxWidth().panel().padding(top = 16.dp)) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = Hifi.Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("A continuación", style = Display.copy(fontSize = 17.sp), modifier = Modifier.weight(1f))
            Text("${next.size}", style = Mono.copy(color = Hifi.TextFaint))
        }
        if (next.isEmpty()) {
            Text("Es la última de la cola. Prueba \"Iniciar radio\" en cualquier canción.", color = Hifi.TextFaint, fontSize = 12.sp, modifier = Modifier.padding(18.dp))
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(next, key = { (i, s) -> "$i-${s.id}" }) { (i, s) ->
                Row(
                    Modifier.fillMaxWidth().bouncyClick { player.skipToIndex(i) }.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverArt(s.coverArt, Modifier.size(40.dp), size = 120, corner = 8.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.artist ?: "", fontSize = 11.sp, color = Hifi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(formatDuration(s.duration), style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ píldora flotante (ventana angosta)

@Composable
private fun PillPlayer(nav: NavHostController) {
    val player = LocalApp.current.player
    val st by player.state.collectAsState()
    val song = st.current ?: return
    val pos by rememberPlaybackPosition(500)
    val frac = if (st.durationMs > 0) (pos.toFloat() / st.durationMs).coerceIn(0f, 1f) else 0f
    val tint by rememberCoverTint(song.coverArt, Color.Black, 0.55f)
    // La portada gira como disco mientras suena.
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(12_000, easing = LinearEasing)), label = "a")

    Column(
        Modifier.width(400.dp).shadow(26.dp, RoundedCornerShape(50)).glass(RoundedCornerShape(50), tint.copy(alpha = 0.75f))
            .bouncyClick { nav.openPlayer() },
    ) {
        Row(Modifier.padding(start = 10.dp, end = 12.dp, top = 9.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverArt(song.coverArt, Modifier.size(46.dp).rotate(if (st.isPlaying) angle else 0f), size = 200, corner = 23.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist ?: "", fontSize = 12.sp, color = Hifi.OnArt, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.SkipPrevious, "Anterior", tint = Color.White, modifier = Modifier.size(26.dp).bouncyClick { player.previous() })
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White).bouncyClick { player.toggle() }, contentAlignment = Alignment.Center) {
                Icon(if (st.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Reproducir", tint = Color.Black)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.SkipNext, "Siguiente", tint = Color.White, modifier = Modifier.size(26.dp).bouncyClick { player.next() })
        }
        Box(Modifier.padding(horizontal = 30.dp).padding(bottom = 8.dp).fillMaxWidth().height(2.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Hifi.Accent))
        }
    }
}
