package com.marco.audiopro.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.marco.audiopro.container
import com.marco.audiopro.ui.components.CoverArt
import com.marco.audiopro.ui.screens.AlbumScreen
import com.marco.audiopro.ui.screens.ArtistScreen
import com.marco.audiopro.ui.screens.EqScreen
import com.marco.audiopro.ui.screens.HomeScreen
import com.marco.audiopro.ui.screens.LibraryScreen
import com.marco.audiopro.ui.screens.LoginScreen
import com.marco.audiopro.ui.screens.NowPlayingScreen
import com.marco.audiopro.ui.screens.PlaylistScreen
import com.marco.audiopro.ui.screens.SearchScreen
import com.marco.audiopro.ui.screens.SettingsScreen
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
    Tab("dsp", "DSP", Icons.Default.Equalizer),
)

@Composable
fun MarcoRoot() {
    val c = LocalContext.current.container
    val account by c.settings.account.collectAsState()
    if (account == null) {
        LoginScreen()
        return
    }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val playback by c.player.state.collectAsState()

    Scaffold(
        containerColor = Hifi.Bg,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (route != "player") {
                Column {
                    if (playback.current != null) MiniPlayer { nav.openPlayer() }
                    NavigationBar(containerColor = Hifi.Panel, tonalElevation = 0.dp) {
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = entry?.destination?.hierarchy()?.contains(t.route) == true,
                                onClick = {
                                    nav.navigate(t.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(t.icon, null) },
                                label = { Text(t.label, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Hifi.Amber,
                                    selectedTextColor = Hifi.Amber,
                                    indicatorColor = Hifi.PanelHi,
                                    unselectedIconColor = Hifi.TextDim,
                                    unselectedTextColor = Hifi.TextDim,
                                ),
                            )
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
            composable("dsp") { EqScreen() }
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

private fun androidx.navigation.NavDestination.hierarchy(): List<String> =
    generateSequence(this) { it.parent }.mapNotNull { it.route }.toList()

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
    val player = LocalContext.current.container.player
    val st by player.state.collectAsState()
    val song = st.current ?: return
    val pos by rememberPlaybackPosition(500)
    Column(Modifier.background(Hifi.PanelHi).clickable(onClick = onOpen)) {
        val frac = if (st.durationMs > 0) (pos.toFloat() / st.durationMs).coerceIn(0f, 1f) else 0f
        Box(Modifier.fillMaxWidth().height(2.dp).background(Hifi.Line)) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Hifi.Amber))
        }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverArt(song.coverArt, Modifier.size(42.dp), size = 120, corner = 4.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                Text(song.artist ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = Hifi.TextDim)
            }
            IconButton(onClick = { player.toggle() }) {
                Icon(if (st.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Hifi.Text)
            }
            IconButton(onClick = { player.next() }) { Icon(Icons.Default.SkipNext, null, tint = Hifi.Text) }
        }
    }
}
