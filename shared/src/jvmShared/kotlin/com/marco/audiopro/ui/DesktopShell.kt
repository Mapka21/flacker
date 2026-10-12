package com.marco.audiopro.ui

import com.marco.audiopro.ui.components.textInput
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import com.marco.audiopro.data.CustomCovers
import com.marco.audiopro.ui.components.NewPlaylistFlow
import com.marco.audiopro.ui.components.volumeWheel
import com.marco.audiopro.ui.theme.Fonts
import kotlinx.coroutines.flow.MutableSharedFlow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Insights
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
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.ui.unit.DpOffset
import com.marco.audiopro.ui.components.SongMenu
import com.marco.audiopro.ui.components.onRightClick
import androidx.compose.material.icons.filled.UnfoldMore
import com.marco.audiopro.ui.components.ConnectGreen
import com.marco.audiopro.ui.components.DevicesButton
import com.marco.audiopro.ui.components.RemoteBadge
import com.marco.audiopro.ui.components.FlackerLogo
import com.marco.audiopro.ui.components.LyricsPane
import com.marco.audiopro.ui.components.SegmentedControl
import com.marco.audiopro.ui.components.VisualModeButton
import com.marco.audiopro.ui.screens.SeekBar
import com.marco.audiopro.ui.components.LikeButton
import com.marco.audiopro.ui.components.Load
import com.marco.audiopro.ui.components.RemotePlaybackBanner
import com.marco.audiopro.ui.components.bouncyClick
import com.marco.audiopro.ui.components.formatDuration
import com.marco.audiopro.ui.components.formatMs
import com.marco.audiopro.ui.components.glass
import com.marco.audiopro.ui.components.PlayPauseIcon
import com.marco.audiopro.ui.components.hoverHighlight
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

/**
 * Atrás/adelante que llegan de fuera de Compose: los botones laterales del mouse (los lee la
 * ventana con AWT) y Alt+←/→. `true` = adelante.
 */
object DesktopHistory {
    val events = MutableSharedFlow<Boolean>(extraBufferCapacity = 8)
}

@Composable
private fun DesktopShell(nav: NavHostController, onMiniPlayer: (() -> Unit)?) {
    val c = LocalApp.current
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val playback by c.player.state.collectAsState()
    val onPlayer = dest.isIn(PlayerRoute::class)
    // El reproductor completo ocupa toda la ventana: sin barra lateral ni columna derecha,
    // para no repetir la misma información dos veces.
    // Sin resorte: un resorte se pasa de 0 y un padding negativo tumba la app.
    val pad by animateDpAsState(if (onPlayer) 0.dp else 14.dp, tween(260), label = "pad")
    val intensity by animateFloatAsState(if (onPlayer) 1f else 0.75f, tween(500), label = "bg")
    var rightTab by remember { mutableStateOf(0) }
    val search = remember { mutableStateOf("") }

    // Historial tipo navegador: atrás guarda la página para poder ir "adelante" otra vez.
    val forward = remember { mutableStateListOf<Any>() }
    var historyNav by remember { mutableStateOf(false) }
    LaunchedEffect(entry?.id) { if (!historyNav) forward.clear(); historyNav = false }
    val goBack: () -> Unit = {
        val current = entry
        if (nav.previousBackStackEntry != null && current != null) {
            historyNav = true
            current.routeObject()?.let { forward.add(it) }
            nav.popBackStack()
        }
    }
    val goForward: () -> Unit = {
        forward.removeLastOrNull()?.let { historyNav = true; nav.navigate(it) }
    }
    val back by rememberUpdatedState(goBack)
    val fwd by rememberUpdatedState(goForward)
    LaunchedEffect(Unit) { DesktopHistory.events.collect { if (it) fwd() else back() } }

    // Panel derecho escondido = sólo el mini reproductor flotante (en cualquier ancho; se recuerda).
    var rightHidden by remember { mutableStateOf(c.settings.getString("right_hidden") == "1") }
    fun setRightHidden(v: Boolean) { rightHidden = v; c.settings.putString("right_hidden", if (v) "1" else "0") }

    CompositionLocalProvider(LocalSearchQuery provides search) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1240.dp
        AmbientBackground(playback.current?.coverArt, intensity = intensity)
        Row(Modifier.fillMaxSize().padding(pad.coerceAtLeast(0.dp))) {
            AnimatedVisibility(
                !onPlayer,
                enter = expandHorizontally(tween(280)) + fadeIn(),
                exit = shrinkHorizontally() + fadeOut(),
            ) { Row { Sidebar(nav, entry, dest); Spacer(Modifier.width(14.dp)) } }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                AnimatedVisibility(!onPlayer, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column { TopBar(nav, onMiniPlayer, search, dest.isIn(SearchRoute::class), goBack); Spacer(Modifier.height(12.dp)) }
                }
                Box(Modifier.weight(1f).fillMaxWidth().then(if (onPlayer) Modifier else Modifier.panel())) { AppNavHost(nav, Modifier.fillMaxSize()) }
            }
            AnimatedVisibility(
                wide && !rightHidden && playback.current != null && !onPlayer,
                enter = expandHorizontally(tween(280)) + fadeIn(),
                exit = shrinkHorizontally() + fadeOut(),
            ) { Row { Spacer(Modifier.width(14.dp)); RightColumn(nav, rightTab, { rightTab = it }) { setRightHidden(true) } } }
        }
        AnimatedVisibility(
            (!wide || rightHidden) && playback.current != null && !onPlayer,
            enter = slideInVertically(spring(dampingRatio = 0.75f, stiffness = 300f)) { it * 2 } + fadeIn(),
            exit = slideOutVertically { it * 2 } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 30.dp, bottom = 30.dp),
        ) { PillPlayer(nav, onShowPanel = if (wide) ({ setRightHidden(false) }) else null) }
        ConnectToast(Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp))
    }
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
    val lists by c.playlists.lists.collectAsState()
    LaunchedEffect(Unit) { runCatching { c.playlists.refresh() } }
    var creating by remember { mutableStateOf(false) }
    if (creating) NewPlaylistFlow(nav) { creating = false }

    Column(Modifier.width(236.dp).fillMaxHeight().panel().padding(vertical = 22.dp)) {
        FlackerLogo(Modifier.padding(start = 22.dp, end = 22.dp, bottom = 14.dp))
        SidebarItem(Icons.Default.Home, "Inicio", dest.isIn(HomeRoute::class)) { nav.openTab(HomeRoute) }
        SidebarItem(Icons.Default.Explore, "Descubrir", dest.isIn(DiscoverRoute::class)) { nav.openTab(DiscoverRoute) }
        SidebarItem(Icons.Default.Insights, "Mi Last.fm", dest.isIn(LastFmRoute::class)) { nav.openTab(LastFmRoute) }
        SidebarItem(Icons.Default.Equalizer, "Ecualizador", dest.isIn(DspRoute::class)) { nav.openTab(DspRoute) }
        SectionLabel("Biblioteca")
        SidebarItem(Icons.Default.Album, "Álbumes", libTab == 0) { nav.openLibrary(0) }
        SidebarItem(Icons.Default.Person, "Artistas", libTab == 1) { nav.openLibrary(1) }
        SidebarItem(Icons.Default.MusicNote, "Canciones", libTab == 2) { nav.openLibrary(2) }
        SidebarItem(Icons.Default.Favorite, "Me gusta", dest.isIn(LikedRoute::class)) { nav.openTab(LikedRoute) }
        SidebarItem(Icons.Default.LibraryMusic, "Playlists", libTab == 3) { nav.openLibrary(3) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionLabel("Tus playlists") }
            Box(
                Modifier.padding(end = 18.dp, top = 14.dp).size(26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
                    .bouncyClick { creating = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Add, "Nueva playlist", tint = Hifi.TextDim, modifier = Modifier.size(16.dp)) }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(lists, key = { it.id }) { p ->
                val sel = dest.isIn(PlaylistRoute::class) && runCatching { entry?.toRoute<PlaylistRoute>()?.id }.getOrNull() == p.id
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 1.dp).fillMaxWidth().hoverHighlight(sel)
                        .bouncyClick { nav.openPlaylist(p.id) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverArt(p.coverArt, Modifier.size(28.dp), size = 120, corner = 6.dp, customKey = CustomCovers.playlist(p.id))
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
    Text(text, style = Mono.copy(color = Hifi.TextFaint, fontSize = 11.5.sp, letterSpacing = 0.2.sp), modifier = Modifier.padding(start = 24.dp, top = 22.dp, bottom = 6.dp))
}

@Composable
private fun SidebarItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val color by animateColorAsState(if (selected) Hifi.Accent else if (hovered) Hifi.Text else Hifi.TextDim, tween(160), label = "side")
    val bg by animateColorAsState(
        when { selected -> Color.White.copy(alpha = 0.09f); hovered -> Color.White.copy(alpha = 0.05f); else -> Color.Transparent }, tween(160), label = "sideBg",
    )
    Row(
        Modifier.padding(horizontal = 10.dp, vertical = 1.dp).fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .hoverable(hover)
            .bouncyClick(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(13.dp))
        Text(
            label, color = if (selected || hovered) Hifi.Text else Hifi.TextDim, fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

// ------------------------------------------------------------------ barra superior

@Composable
private fun TopBar(nav: NavHostController, onMiniPlayer: (() -> Unit)?, query: MutableState<String>, onSearch: Boolean, onBack: () -> Unit) {
    val c = LocalApp.current
    val playing = c.player.state.collectAsState().value.current != null
    Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundGlass(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", onClick = onBack)
        Spacer(Modifier.width(12.dp))
        SearchField(nav, query, onSearch)
        Spacer(Modifier.weight(1f))
        RoundGlass(Icons.Default.Shuffle, "Aleatorio de toda tu música") { com.marco.audiopro.ui.components.LibraryShuffle.start(c) }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.glass(CircleShape, Color.Black.copy(alpha = 0.28f))) { VisualModeButton() }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.glass(CircleShape, Color.Black.copy(alpha = 0.28f))) { DevicesButton() }
        if (onMiniPlayer != null && playing) {
            Spacer(Modifier.width(10.dp))
            RoundGlass(Icons.Default.PictureInPictureAlt, "Miniplayer iPod", onClick = onMiniPlayer)
        }
    }
}

/** La única barra de búsqueda (como Spotify): escribir aquí muestra los resultados al centro. */
@Composable
private fun SearchField(nav: NavHostController, query: MutableState<String>, onSearch: Boolean) {
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (focused) Color.White.copy(alpha = 0.35f) else Color.Transparent, label = "sf")
    Row(
        Modifier.width(440.dp).height(44.dp).glass(RoundedCornerShape(50), Color.Black.copy(alpha = if (focused) 0.42f else 0.28f))
            .border(1.dp, border, RoundedCornerShape(50)).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, null, tint = if (focused) Hifi.Text else Hifi.TextDim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.value.isEmpty()) Text("¿Qué quieres escuchar?", color = Hifi.TextFaint, fontSize = 14.sp)
            BasicTextField(
                query.value,
                { q ->
                    query.value = q
                    if (q.isNotBlank() && !onSearch) nav.navigate(SearchRoute) { launchSingleTop = true }
                },
                singleLine = true,
                textStyle = TextStyle(color = Hifi.Text, fontSize = 14.sp, fontFamily = Fonts.inter),
                cursorBrush = SolidColor(Hifi.Accent),
                modifier = Modifier.fillMaxWidth().textInput()
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused && !onSearch) nav.navigate(SearchRoute) { launchSingleTop = true }
                    }
                    .onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { query.value = ""; focus.clearFocus(); true } else false
                    },
            )
        }
        if (query.value.isNotEmpty()) {
            Box(Modifier.size(30.dp).clip(CircleShape).bouncyClick { query.value = "" }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Close, "Borrar", tint = Hifi.TextDim, modifier = Modifier.size(16.dp))
            }
        }
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
private fun RightColumn(nav: NavHostController, tab: Int, onTab: (Int) -> Unit, onHide: () -> Unit) {
    val c = LocalApp.current
    val song = c.player.state.collectAsState().value.current
    // Encogido = mini reproductor arriba y más espacio para la cola o la letra (se recuerda).
    var collapsed by remember { mutableStateOf(c.settings.getString("np_collapsed") == "1") }
    fun setCollapsed(v: Boolean) { collapsed = v; c.settings.putString("np_collapsed", if (v) "1" else "0") }
    Column(Modifier.width(340.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RemotePlaybackBanner()
        AnimatedContent(collapsed, transitionSpec = { (fadeIn() + expandVertically()) togetherWith (fadeOut() + shrinkVertically()) }, label = "np") { small ->
            if (small) CompactNowPlaying(nav, onHide) { setCollapsed(false) }
            else NowPlayingCard(nav, lyricsOn = tab == 1, onCollapse = { setCollapsed(true) }, onHide = onHide) { onTab(if (tab == 1) 0 else 1) }
        }
        // Abajo: la cola o la letra (como la barra lateral de Apple Music en Mac).
        Column(Modifier.weight(1f).fillMaxWidth().panel().padding(top = 12.dp)) {
            SegmentedControl(listOf("A continuación", "Letra"), tab, onTab, Modifier.padding(horizontal = 12.dp).fillMaxWidth())
            AnimatedContent(tab, Modifier.weight(1f), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "right") { t ->
                if (t == 1 && song != null) LyricsPane(song, Modifier.fillMaxSize(), fontSize = 20.sp, horizontalPadding = 18)
                else UpNextList()
            }
        }
    }
}

@Composable
private fun NowPlayingCard(nav: NavHostController, lyricsOn: Boolean, onCollapse: () -> Unit, onHide: () -> Unit, onLyrics: () -> Unit) {
    val c = LocalApp.current
    val player = c.player
    val st by player.state.collectAsState()
    val song = st.current ?: return
    val tint by rememberCoverTint(song.coverArt, Color.Black, 0.55f)
    var menu by remember { mutableStateOf<DpOffset?>(null) }

    Box(Modifier.onRightClick { menu = it }) {
    SongMenu(song, menu != null, { menu = null }, nav, offset = menu ?: DpOffset.Zero)
    Column(Modifier.fillMaxWidth().volumeWheel().glass(PanelShape, tint.copy(alpha = 0.55f)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (st.remoteName != null) RemoteBadge(st.remoteName)
                else Text("REPRODUCIENDO", style = Mono.copy(color = Hifi.OnArt.copy(alpha = 0.6f), fontSize = 10.sp, letterSpacing = 0.9.sp))
            }
            Icon(Icons.Default.UnfoldLess, "Hacer mini", tint = Hifi.OnArt, modifier = Modifier.size(18.dp).bouncyClick(onClick = onCollapse))
            Spacer(Modifier.width(12.dp))
            Icon(Icons.AutoMirrored.Filled.LastPage, "Esconder el panel (mini reproductor)", tint = Hifi.OnArt, modifier = Modifier.size(18.dp).bouncyClick(onClick = onHide))
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Default.OpenInFull, "Vista completa", tint = Hifi.OnArt, modifier = Modifier.size(16.dp).bouncyClick { nav.openPlayer() })
        }
        Spacer(Modifier.height(12.dp))
        // Al cambiar de canción la portada nueva entra creciendo y la vieja se desvanece.
        AnimatedContent(
            song.coverArt,
            transitionSpec = { (fadeIn(tween(420)) + scaleIn(tween(420), initialScale = 0.92f)) togetherWith fadeOut(tween(220)) },
            label = "npCover",
        ) { art ->
            CoverArt(
                art,
                Modifier.fillMaxWidth().aspectRatio(1f).shadow(26.dp, RoundedCornerShape(18.dp)).bouncyClick { nav.openPlayer() },
                size = 800, corner = 18.dp,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(
                song, Modifier.weight(1f),
                transitionSpec = { (fadeIn(tween(320, 120)) + slideInVertically(tween(320, 120)) { it / 3 }) togetherWith fadeOut(tween(160)) },
                contentKey = { it.id }, label = "npTitle",
            ) { s ->
                Column {
                    Text(s.title, style = Display.copy(fontSize = 19.sp, color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        s.artist ?: "", fontSize = 14.sp, color = Hifi.OnArt, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.bouncyClick { s.artistId?.let(nav::openArtist) },
                    )
                }
            }
            LikeButton(song, size = 22.dp)
        }
        Spacer(Modifier.height(8.dp))
        SeekBar(st.durationMs, compact = true)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SmallToggle(Icons.Default.Shuffle, st.shuffle) { player.toggleShuffle() }
            Icon(Icons.Default.SkipPrevious, "Anterior", tint = Color.White, modifier = Modifier.size(34.dp).bouncyClick { player.previous() })
            Box(Modifier.size(56.dp).shadow(14.dp, CircleShape).clip(CircleShape).background(Color.White).bouncyClick { player.toggle() }, contentAlignment = Alignment.Center) {
                if (st.isBuffering) CircularProgressIndicator(Modifier.size(24.dp), color = Color.Black, strokeWidth = 2.dp)
                else PlayPauseIcon(st.isPlaying, Color.Black, 30.dp)
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
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (lyricsOn) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                    .bouncyClick(onClick = onLyrics),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Lyrics, "Letra", tint = if (lyricsOn) Color.White else Hifi.OnArt, modifier = Modifier.size(20.dp)) }
            IconButton(onClick = { nav.openTab(DspRoute) }) { Icon(Icons.Default.Equalizer, "Ecualizador", tint = Hifi.OnArt, modifier = Modifier.size(20.dp)) }
            DevicesButton(size = 20)
        }
    }
}
}

/** "Reproduciendo" encogido: portada, título, controles y la barrita de avance. */
@Composable
private fun CompactNowPlaying(nav: NavHostController, onHide: () -> Unit, onExpand: () -> Unit) {
    val c = LocalApp.current
    val player = c.player
    val st by player.state.collectAsState()
    val song = st.current ?: return
    val pos by rememberPlaybackPosition(400)
    val frac = if (st.durationMs > 0) (pos.toFloat() / st.durationMs).coerceIn(0f, 1f) else 0f
    val tint by rememberCoverTint(song.coverArt, Color.Black, 0.55f)
    var menu by remember { mutableStateOf<DpOffset?>(null) }
    Box(Modifier.onRightClick { menu = it }) {
    SongMenu(song, menu != null, { menu = null }, nav, offset = menu ?: DpOffset.Zero)
    Column(Modifier.fillMaxWidth().volumeWheel().glass(PanelShape, tint.copy(alpha = 0.55f)).padding(12.dp)) {
        if (st.remoteName != null) { RemoteBadge(st.remoteName); Spacer(Modifier.height(8.dp)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverArt(song.coverArt, Modifier.size(54.dp).shadow(10.dp, RoundedCornerShape(10.dp)).bouncyClick { nav.openPlayer() }, size = 200, corner = 10.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist ?: "", fontSize = 12.sp, color = Hifi.OnArt, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.UnfoldMore, "Agrandar", tint = Hifi.OnArt, modifier = Modifier.size(18.dp).bouncyClick(onClick = onExpand))
            Spacer(Modifier.width(10.dp))
            Icon(Icons.AutoMirrored.Filled.LastPage, "Esconder el panel", tint = Hifi.OnArt, modifier = Modifier.size(18.dp).bouncyClick(onClick = onHide))
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Color.White.copy(alpha = 0.9f)))
            }
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Default.SkipPrevious, "Anterior", tint = Color.White, modifier = Modifier.size(26.dp).bouncyClick { player.previous() })
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White).bouncyClick { player.toggle() }, contentAlignment = Alignment.Center) {
                PlayPauseIcon(st.isPlaying, Color.Black, 22.dp)
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.SkipNext, "Siguiente", tint = Color.White, modifier = Modifier.size(26.dp).bouncyClick { player.next() })
        }
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
private fun UpNextList() {
    val player = LocalApp.current.player
    val st by player.state.collectAsState()
    val next = st.queue.withIndex().drop(st.currentIndex + 1).take(40)
    Column(Modifier.fillMaxSize()) {
        if (next.isEmpty()) {
            Text("Es la última de la cola. Prueba \"Iniciar radio\" en cualquier canción.", color = Hifi.TextFaint, fontSize = 12.sp, modifier = Modifier.padding(18.dp))
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(next, key = { (i, s) -> "$i-${s.id}" }) { (i, s) ->
                Row(
                    Modifier.padding(horizontal = 6.dp).fillMaxWidth().hoverHighlight().bouncyClick { player.skipToIndex(i) }.padding(horizontal = 8.dp, vertical = 6.dp),
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
private fun PillPlayer(nav: NavHostController, onShowPanel: (() -> Unit)?) {
    val player = LocalApp.current.player
    val st by player.state.collectAsState()
    val song = st.current ?: return
    val pos by rememberPlaybackPosition(500)
    val frac = if (st.durationMs > 0) (pos.toFloat() / st.durationMs).coerceIn(0f, 1f) else 0f
    val tint by rememberCoverTint(song.coverArt, Color.Black, 0.55f)
    // La portada gira como disco mientras suena.
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(12_000, easing = LinearEasing)), label = "a")

    var menu by remember { mutableStateOf<DpOffset?>(null) }
    Box(Modifier.onRightClick { menu = it }) {
    SongMenu(song, menu != null, { menu = null }, nav, offset = menu ?: DpOffset.Zero)
    Column(
        Modifier.width(if (onShowPanel != null) 440.dp else 400.dp).volumeWheel().shadow(26.dp, RoundedCornerShape(50)).glass(RoundedCornerShape(50), tint.copy(alpha = 0.75f))
            .bouncyClick { nav.openPlayer() },
    ) {
        Row(Modifier.padding(start = 10.dp, end = 12.dp, top = 9.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverArt(song.coverArt, Modifier.size(46.dp).rotate(if (st.isPlaying) angle else 0f), size = 200, corner = 23.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(st.remoteName?.let { "▶ En $it" } ?: song.artist ?: "", fontSize = 12.sp, color = if (st.remoteName != null) ConnectGreen else Hifi.OnArt, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.SkipPrevious, "Anterior", tint = Color.White, modifier = Modifier.size(26.dp).bouncyClick { player.previous() })
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White).bouncyClick { player.toggle() }, contentAlignment = Alignment.Center) {
                PlayPauseIcon(st.isPlaying, Color.Black, 24.dp)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.SkipNext, "Siguiente", tint = Color.White, modifier = Modifier.size(26.dp).bouncyClick { player.next() })
            if (onShowPanel != null) {
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Default.ViewSidebar, "Mostrar el panel", tint = Hifi.OnArt, modifier = Modifier.size(22.dp).bouncyClick(onClick = onShowPanel))
            }
        }
        Box(Modifier.padding(horizontal = 30.dp).padding(bottom = 8.dp).fillMaxWidth().height(2.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Hifi.Accent))
        }
    }
}
}
