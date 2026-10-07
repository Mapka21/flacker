package com.marco.audiopro.ui

import androidx.compose.animation.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.marco.audiopro.ui.screens.DownloadsScreen
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
@Serializable data object LibraryRoute
@Serializable data object SearchRoute
@Serializable data object DownloadsRoute
@Serializable data object DspRoute
@Serializable data object LikedRoute
@Serializable data object SettingsRoute
@Serializable data object PlayerRoute
@Serializable data class AlbumRoute(val id: String)
@Serializable data class ArtistRoute(val id: String)
@Serializable data class PlaylistRoute(val id: String)

fun NavController.openAlbum(id: String) = navigate(AlbumRoute(id))
fun NavController.openArtist(id: String) = navigate(ArtistRoute(id))
fun NavController.openPlaylist(id: String) = navigate(PlaylistRoute(id))
fun NavController.openPlayer() = navigate(PlayerRoute) { launchSingleTop = true }

/** Cambia de sección principal conservando el estado de cada una. */
fun NavController.openTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
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
    shell()
}

@Composable
fun AppNavHost(nav: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        nav, startDestination = HomeRoute, modifier = modifier,
        enterTransition = { fadeIn(tween(180)) }, exitTransition = { fadeOut(tween(180)) },
    ) {
        composable<HomeRoute> { HomeScreen(nav) }
        composable<LibraryRoute> { LibraryScreen(nav) }
        composable<SearchRoute> { SearchScreen(nav) }
        composable<DownloadsRoute> { DownloadsScreen() }
        composable<DspRoute> { EqScreen() }
        composable<LikedRoute> { LikedScreen(nav) }
        composable<SettingsRoute> { SettingsScreen(nav) }
        composable<AlbumRoute> { AlbumScreen(it.toRoute<AlbumRoute>().id, nav) }
        composable<ArtistRoute> { ArtistScreen(it.toRoute<ArtistRoute>().id, nav) }
        composable<PlaylistRoute> { PlaylistScreen(it.toRoute<PlaylistRoute>().id, nav) }
        composable<PlayerRoute>(
            enterTransition = { slideInVertically(tween(320)) { it } },
            exitTransition = { fadeOut(tween(1)) },
            popExitTransition = { slideOutVertically(tween(320)) { it } },
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
    val tabs = buildList {
        add(NavItem(HomeRoute, "Inicio", Icons.Default.Home))
        add(NavItem(LibraryRoute, "Biblioteca", Icons.Default.LibraryMusic))
        add(NavItem(SearchRoute, "Buscar", Icons.Default.Search))
        if (c.downloads != null) add(NavItem(DownloadsRoute, "Descargas", Icons.Outlined.DownloadForOffline))
        add(NavItem(DspRoute, "DSP", Icons.Default.Equalizer))
    }

    Scaffold(
        containerColor = Hifi.Bg,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (!dest.isIn(PlayerRoute::class)) {
                Column(Modifier.background(Brush.verticalGradient(0f to Color.Transparent, 0.25f to Hifi.Bg))) {
                    if (playback.current != null) MiniPlayer { nav.openPlayer() }
                    BottomBar(tabs, dest) { nav.openTab(it) }
                }
            }
        },
    ) { pad -> AppNavHost(nav, Modifier.padding(pad)) }
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
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .clickable(remember { MutableInteractionSource() }, null) { onSelect(t.route) }
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
    val tint by rememberCoverTint(song.coverArt)
    val frac = if (st.durationMs > 0) (pos.toFloat() / st.durationMs).coerceIn(0f, 1f) else 0f

    Column(
        Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tint)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverArt(song.coverArt, Modifier.size(44.dp), size = 120, corner = 8.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(song.artist ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = Hifi.TextDim)
            }
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
