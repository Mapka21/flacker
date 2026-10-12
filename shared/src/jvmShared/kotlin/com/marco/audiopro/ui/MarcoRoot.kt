package com.marco.audiopro.ui

import androidx.compose.animation.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.marco.audiopro.ui.components.ConnectToast
import com.marco.audiopro.ui.components.DevicesButton
import com.marco.audiopro.ui.components.RemotePlaybackBanner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import com.marco.audiopro.ui.components.AmbientBackground
import com.marco.audiopro.ui.components.bouncyClick
import com.marco.audiopro.ui.components.glass
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import coil3.compose.LocalPlatformContext
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.ui.components.CoverArt
import com.marco.audiopro.ui.components.LikeButton
import com.marco.audiopro.ui.components.coverColors
import com.marco.audiopro.ui.screens.AlbumScreen
import com.marco.audiopro.ui.screens.ArtistScreen
import com.marco.audiopro.ui.screens.BrowseScreen
import com.marco.audiopro.ui.screens.DiscoverScreen
import com.marco.audiopro.ui.screens.LastFmScreen
import com.marco.audiopro.ui.screens.DownloadsScreen
import com.marco.audiopro.ui.screens.ExternalAlbumScreen
import com.marco.audiopro.ui.screens.ExternalArtistScreen
import com.marco.audiopro.ui.screens.EqScreen
import com.marco.audiopro.ui.screens.HomeScreen
import com.marco.audiopro.ui.screens.LibraryScreen
import com.marco.audiopro.ui.screens.LikedScreen
import com.marco.audiopro.ui.screens.LoginScreen
import com.marco.audiopro.ui.screens.NowPlayingScreen
import com.marco.audiopro.ui.screens.PlaylistScreen
import com.marco.audiopro.ui.screens.SearchScreen
import com.marco.audiopro.ui.screens.SettingsScreen
import com.marco.audiopro.ui.theme.AccentTheme
import com.marco.audiopro.ui.theme.Hifi
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

// ------------------------------------------------------------------ rutas

@Serializable data object HomeRoute
/** @param tab 0 = álbumes, 1 = artistas, 2 = canciones, 3 = playlists. */
@Serializable data class LibraryRoute(val tab: Int = 0)
@Serializable data object SearchRoute
@Serializable data object DownloadsRoute
@Serializable data object DspRoute
@Serializable data object LikedRoute
@Serializable data object SettingsRoute
@Serializable data object PlayerRoute
@Serializable data class AlbumRoute(val id: String)
@Serializable data class ArtistRoute(val id: String)
@Serializable data class PlaylistRoute(val id: String)
@Serializable data object DiscoverRoute
@Serializable data object LastFmRoute
/** Álbumes de tu biblioteca por género o por década. */
@Serializable data class BrowseRoute(val title: String, val genre: String? = null, val fromYear: Int? = null, val toYear: Int? = null)
/** Artista / álbum del catálogo (Deezer), aunque no esté en tu biblioteca. */
@Serializable data class ExternalArtistRoute(val id: Long)
@Serializable data class ExternalAlbumRoute(val id: Long)

fun NavController.openAlbum(id: String) = navigate(AlbumRoute(id))
fun NavController.openArtist(id: String) = navigate(ArtistRoute(id))
fun NavController.openPlaylist(id: String) = navigate(PlaylistRoute(id))
fun NavController.openExternalArtist(id: Long) = navigate(ExternalArtistRoute(id))
fun NavController.openExternalAlbum(id: Long) = navigate(ExternalAlbumRoute(id))
fun NavController.openPlayer() = navigate(PlayerRoute) { launchSingleTop = true }

/**
 * Cambia de sección principal y la abre desde su raíz (como Spotify): "Inicio" siempre lleva
 * a Inicio aunque antes hayas entrado a un álbum desde ahí.
 */
fun NavController.openTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id)
    launchSingleTop = true
}

/**
 * Texto de la barra de búsqueda de arriba (PC). Si existe, la pantalla de búsqueda no
 * muestra su propio campo: sólo hay una barra.
 */
val LocalSearchQuery = androidx.compose.runtime.compositionLocalOf<androidx.compose.runtime.MutableState<String>?> { null }

/** La ruta tipada de una entrada del historial (para poder volver a navegar a ella). */
fun androidx.navigation.NavBackStackEntry.routeObject(): Any? {
    val d = destination
    return runCatching {
        when {
            d.hasRoute(AlbumRoute::class) -> toRoute<AlbumRoute>()
            d.hasRoute(ArtistRoute::class) -> toRoute<ArtistRoute>()
            d.hasRoute(PlaylistRoute::class) -> toRoute<PlaylistRoute>()
            d.hasRoute(ExternalArtistRoute::class) -> toRoute<ExternalArtistRoute>()
            d.hasRoute(ExternalAlbumRoute::class) -> toRoute<ExternalAlbumRoute>()
            d.hasRoute(DiscoverRoute::class) -> DiscoverRoute
            d.hasRoute(LastFmRoute::class) -> LastFmRoute
            d.hasRoute(BrowseRoute::class) -> toRoute<BrowseRoute>()
            d.hasRoute(LibraryRoute::class) -> toRoute<LibraryRoute>()
            d.hasRoute(HomeRoute::class) -> HomeRoute
            d.hasRoute(SearchRoute::class) -> SearchRoute
            d.hasRoute(DownloadsRoute::class) -> DownloadsRoute
            d.hasRoute(DspRoute::class) -> DspRoute
            d.hasRoute(LikedRoute::class) -> LikedRoute
            d.hasRoute(SettingsRoute::class) -> SettingsRoute
            d.hasRoute(PlayerRoute::class) -> PlayerRoute
            else -> null
        }
    }.getOrNull()
}

fun NavDestination?.isIn(route: KClass<*>): Boolean =
    this != null && generateSequence(this) { it.parent }.any { it.hasRoute(route) }

data class NavItem(val route: Any, val label: String, val icon: ImageVector)

// ------------------------------------------------------------------ raíz común

/** Login, tema de acento y "me gusta": lo que comparten teléfono y PC antes de la cáscara. */
@Composable
fun MarcoGate(shell: @Composable () -> Unit) {
    val c = LocalApp.current
    AccentController()
    val account by c.settings.account.collectAsState()
    if (account == null) {
        LoginScreen()
        return
    }
    LaunchedEffect(account) { runCatching { c.likes.refresh() } }
    LaunchedEffect(Unit) { com.marco.audiopro.ui.components.LibraryShuffle.keepAlive(c) }
    shell()
}

@Composable
fun AppNavHost(nav: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        nav, startDestination = HomeRoute, modifier = modifier,
        enterTransition = { fadeIn(tween(220)) + androidx.compose.animation.slideInHorizontally(tween(320)) { it / 14 } },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(220)) + androidx.compose.animation.slideInHorizontally(tween(320)) { -it / 14 } },
        popExitTransition = { fadeOut(tween(160)) + androidx.compose.animation.slideOutHorizontally(tween(260)) { it / 14 } },
    ) {
        composable<HomeRoute> { HomeScreen(nav) }
        composable<LibraryRoute> { LibraryScreen(nav, it.toRoute<LibraryRoute>().tab) }
        composable<SearchRoute> { SearchScreen(nav) }
        composable<DownloadsRoute> { DownloadsScreen() }
        composable<DspRoute> { EqScreen() }
        composable<LikedRoute> { LikedScreen(nav) }
        composable<SettingsRoute> { SettingsScreen(nav) }
        composable<AlbumRoute> { AlbumScreen(it.toRoute<AlbumRoute>().id, nav) }
        composable<ArtistRoute> { ArtistScreen(it.toRoute<ArtistRoute>().id, nav) }
        composable<PlaylistRoute> { PlaylistScreen(it.toRoute<PlaylistRoute>().id, nav) }
        composable<DiscoverRoute> { DiscoverScreen(nav) }
        composable<LastFmRoute> { LastFmScreen(nav) }
        composable<BrowseRoute> { BrowseScreen(it.toRoute<BrowseRoute>(), nav) }
        composable<ExternalArtistRoute> { ExternalArtistScreen(it.toRoute<ExternalArtistRoute>().id, nav) }
        composable<ExternalAlbumRoute> { ExternalAlbumScreen(it.toRoute<ExternalAlbumRoute>().id, nav) }
        // El fondo es compartido: el reproductor entra creciendo y desvaneciéndose, sin tapar nada.
        composable<PlayerRoute>(
            enterTransition = { fadeIn(tween(280)) + scaleIn(tween(320), initialScale = 0.94f) + slideInVertically(tween(320)) { it / 10 } },
            exitTransition = { fadeOut(tween(160)) },
            popExitTransition = { fadeOut(tween(220)) + scaleOut(tween(260), targetScale = 0.94f) + slideOutVertically(tween(260)) { it / 10 } },
        ) { NowPlayingScreen(nav) }
    }
}

/** Aplica el tema de acento elegido; en modo "Portada" sigue el color de lo que suena. */
@Composable
private fun AccentController() {
    val ctx = LocalPlatformContext.current
    val c = LocalApp.current
    val theme = c.settings.prefs.collectAsState().value.accent
    val cover = c.player.state.collectAsState().value.current?.coverArt
    val anim = remember { Animatable(Hifi.Accent) }
    LaunchedEffect(theme, cover) {
        val target = theme.color
            ?: coverColors(ctx, c.api, cover)?.accent
            ?: AccentTheme.AMBER.color!!
        anim.animateTo(target, tween(700)) { Hifi.Accent = value }
    }
}

/** Posición de reproducción, refrescada mientras el composable está visible. */
@Composable
fun rememberPlaybackPosition(intervalMs: Long = 250): State<Long> {
    val player = LocalApp.current.player
    val pos = remember { mutableLongStateOf(player.positionMs()) }
    LaunchedEffect(Unit) {
        while (true) {
            pos.longValue = player.positionMs()
            delay(intervalMs)
        }
    }
    return pos
}

/** Color de fondo tomado de la portada, mezclado con un panel (mini-player, barra de PC). */
@Composable
fun rememberCoverTint(coverArt: String?, base: Color = Hifi.PanelHi, amount: Float = 0.55f): State<Color> {
    val ctx = LocalPlatformContext.current
    val api = LocalApp.current.api
    val target = remember { mutableStateOf(base) }
    LaunchedEffect(coverArt) { coverColors(ctx, api, coverArt)?.let { target.value = lerp(base, it.background, amount) } }
    return animateColorAsState(target.value, tween(600), label = "tint")
}

// ------------------------------------------------------------------ cáscara de teléfono

@Composable
fun MarcoRoot(nav: NavHostController = rememberNavController()) = MarcoGate { PhoneShell(nav) }

@Composable
private fun PhoneShell(nav: NavHostController) {
    val c = LocalApp.current
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val playback by c.player.state.collectAsState()
    val onPlayer = dest.isIn(PlayerRoute::class)
    val intensity by animateFloatAsState(if (onPlayer) 1f else 0.45f, tween(500), label = "bg")
    val tabs = buildList {
        add(NavItem(HomeRoute, "Inicio", Icons.Default.Home))
        add(NavItem(LibraryRoute(), "Biblioteca", Icons.Default.LibraryMusic))
        add(NavItem(SearchRoute, "Buscar", Icons.Default.Search))
        add(NavItem(DiscoverRoute, "Descubrir", Icons.Default.Explore))
        // Descargas se abre desde Biblioteca e Inicio; aquí va tu Last.fm, siempre a la mano.
        add(NavItem(LastFmRoute, "Last.fm", Icons.Default.Insights))
    }

    Scaffold(
        containerColor = Hifi.Bg,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (!onPlayer) {
                Column(
                    Modifier
                        .background(Brush.verticalGradient(0f to Color.Transparent, 0.3f to Color.Black.copy(alpha = 0.78f)))
                        .drawBehind { drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, 0f), Offset(size.width, 0f), 1f) },
                ) {
                    RemotePlaybackBanner(Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    if (playback.current != null) MiniPlayer { nav.openPlayer() }
                    BottomBar(tabs, dest) { nav.openTab(it) }
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize()) {
            AmbientBackground(playback.current?.coverArt, intensity = intensity)
            AppNavHost(nav, Modifier.padding(pad))
            ConnectToast(Modifier.align(Alignment.BottomCenter).padding(bottom = pad.calculateBottomPadding() + 12.dp))
        }
    }
}

@Composable
private fun BottomBar(tabs: List<NavItem>, dest: NavDestination?, onSelect: (Any) -> Unit) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().height(62.dp).padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { t ->
            val sel = dest.isIn(t.route::class)
            val color by animateColorAsState(if (sel) Hifi.Accent else Hifi.TextFaint, label = "tab")
            Column(
                Modifier.weight(1f).bouncyClick { onSelect(t.route) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.width(18.dp).height(2.dp).clip(CircleShape).background(if (sel) Hifi.Accent else Color.Transparent))
                Spacer(Modifier.height(5.dp))
                Icon(t.icon, t.label, tint = color, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(3.dp))
                Text(t.label, fontSize = 10.sp, color = color, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun MiniPlayer(onOpen: () -> Unit) {
    val player = LocalApp.current.player
    val st by player.state.collectAsState()
    val song = st.current ?: return
    val pos by rememberPlaybackPosition(500)
    val tint by rememberCoverTint(song.coverArt, Color.Black, 0.55f)
    val frac = if (st.durationMs > 0) (pos.toFloat() / st.durationMs).coerceIn(0f, 1f) else 0f

    Column(
        Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(18.dp))
            .glass(RoundedCornerShape(18.dp), tint.copy(alpha = 0.78f))
            .bouncyClick(onClick = onOpen),
    ) {
        Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverArt(song.coverArt, Modifier.size(44.dp), size = 120, corner = 8.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                // Como Spotify: en verde si suena en otro dispositivo que controlas.
                Text(
                    st.remoteName?.let { "▶ Reproduciendo en $it" } ?: song.artist ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp,
                    color = if (st.remoteName != null) com.marco.audiopro.ui.components.ConnectGreen else Hifi.TextDim,
                )
            }
            DevicesButton(size = 20)
            LikeButton(song, size = 20.dp)
            IconButton(onClick = { player.toggle() }) {
                Icon(if (st.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Hifi.Text)
            }
            IconButton(onClick = { player.next() }) { Icon(Icons.Default.SkipNext, null, tint = Hifi.Text) }
        }
        Box(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp).fillMaxWidth().height(2.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Hifi.Accent))
        }
    }
}
