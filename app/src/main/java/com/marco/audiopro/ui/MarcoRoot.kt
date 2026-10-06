package com.marco.audiopro.ui

import android.net.Uri
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.marco.audiopro.container
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

fun NavController.openAlbum(id: String) = navigate("album/${Uri.encode(id)}")
fun NavController.openArtist(id: String) = navigate("artist/${Uri.encode(id)}")
fun NavController.openPlaylist(id: String) = navigate("playlist/${Uri.encode(id)}")
fun NavController.openPlayer() = navigate("player") { launchSingleTop = true }

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Inicio", Icons.Default.Home),
    Tab("library", "Biblioteca", Icons.Default.LibraryMusic),
    Tab("search", "Buscar", Icons.Default.Search),
    Tab("downloads", "Descargas", Icons.Outlined.DownloadForOffline),
    Tab("dsp", "DSP", Icons.Default.Equalizer),
)

@Composable
fun MarcoRoot() {
    val c = LocalContext.current.container
    AccentController()
    val account by c.settings.account.collectAsState()
    if (account == null) {
        LoginScreen()
        return
    }
    LaunchedEffect(account) { runCatching { c.likes.refresh() } }

    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val playback by c.player.state.collectAsState()

    Scaffold(
        containerColor = Hifi.Bg,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (route != "player") {
                Column(Modifier.background(Brush.verticalGradient(0f to Color.Transparent, 0.25f to Hifi.Bg))) {
                    if (playback.current != null) MiniPlayer { nav.openPlayer() }
                    BottomBar(entry?.destination) { t ->
                        nav.navigate(t) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            }
        },
    ) { pad ->
        NavHost(
            nav, startDestination = "home", modifier = Modifier.padding(pad),
            enterTransition = { fadeIn(tween(180)) }, exitTransition = { fadeOut(tween(180)) },
        ) {
            composable("home") { HomeScreen(nav) }
            composable("library") { LibraryScreen(nav) }
            composable("search") { SearchScreen(nav) }
            composable("downloads") { DownloadsScreen() }
            composable("dsp") { EqScreen() }
            composable("liked") { LikedScreen(nav) }
            composable("settings") { SettingsScreen(nav) }
            composable("album/{id}") { AlbumScreen(it.arguments?.getString("id")!!, nav) }
            composable("artist/{id}") { ArtistScreen(it.arguments?.getString("id")!!, nav) }
            composable("playlist/{id}") { PlaylistScreen(it.arguments?.getString("id")!!, nav) }
            composable(
                "player",
                enterTransition = { slideInVertically(tween(320)) { it } },
                exitTransition = { fadeOut(tween(1)) },
                popExitTransition = { slideOutVertically(tween(320)) { it } },
            ) { NowPlayingScreen(nav) }
        }
    }
}

/** Aplica el tema de acento elegido; en modo "Portada" sigue el color de lo que suena. */
@Composable
private fun AccentController() {
    val ctx = LocalContext.current
    val c = ctx.container
    val theme = c.settings.prefs.collectAsState().value.accent
    val cover = c.player.state.collectAsState().value.current?.coverArt
    val anim = remember { Animatable(Hifi.Accent) }
    LaunchedEffect(theme, cover) {
        val target = theme.color
            ?: coverColors(ctx, cover)?.accent
            ?: AccentTheme.AMBER.color!!
        anim.animateTo(target, tween(700)) { Hifi.Accent = value }
    }
}

private fun NavDestination.inHierarchy(route: String): Boolean =
    generateSequence(this) { it.parent }.any { it.route == route }

@Composable
private fun BottomBar(dest: NavDestination?, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().height(62.dp).padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { t ->
            val sel = dest?.inHierarchy(t.route) == true
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

/** Posición de reproducción, refrescada mientras el composable está visible. */
@Composable
fun rememberPlaybackPosition(intervalMs: Long = 250): State<Long> {
    val player = LocalContext.current.container.player
    val pos = remember { mutableLongStateOf(player.positionMs()) }
    LaunchedEffect(Unit) {
        while (true) {
            pos.longValue = player.positionMs()
            delay(intervalMs)
        }
    }
    return pos
}

@Composable
private fun MiniPlayer(onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val player = ctx.container.player
    val st by player.state.collectAsState()
    val song = st.current ?: return
    val pos by rememberPlaybackPosition(500)
    val tintTarget = remember { androidx.compose.runtime.mutableStateOf(Hifi.PanelHi) }
    LaunchedEffect(song.coverArt) { coverColors(ctx, song.coverArt)?.let { tintTarget.value = lerp(Hifi.PanelHi, it.background, 0.55f) } }
    val tint by animateColorAsState(tintTarget.value, tween(600), label = "mini")
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
