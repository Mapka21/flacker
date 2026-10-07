package com.marco.audiopro.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import com.marco.audiopro.ui.components.AmbientBackground
import com.marco.audiopro.ui.components.bouncyClick
import com.marco.audiopro.ui.components.glass
import com.marco.audiopro.ui.screens.AppleBar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.data.subsonic.khz
import com.marco.audiopro.player.RepeatMode
import com.marco.audiopro.ui.components.CoverArt
import com.marco.audiopro.ui.components.LikeButton
import com.marco.audiopro.ui.components.formatMs
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import com.marco.audiopro.ui.viz.Visualizer
import com.marco.audiopro.ui.viz.VizMode

/**
 * Cáscara de PC: barra lateral, contenido y barra de reproducción abajo.
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

    Box(Modifier.fillMaxSize()) {
        // Fondo vivo sutil con los colores de lo que suena: se transparenta en el vidrio.
        AmbientBackground(playback.current?.coverArt, intensity = 0.45f)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f)) {
                Sidebar(dest) { nav.openTab(it) }
                AppNavHost(nav, Modifier.weight(1f).fillMaxHeight())
            }
            AnimatedVisibility(
                playback.current != null && !onPlayer,
                enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) { PlayerBar(onMiniPlayer) { nav.openPlayer() } }
        }
    }
}

@Composable
private fun Sidebar(dest: NavDestination?, onSelect: (Any) -> Unit) {
    val items = listOf(
        NavItem(HomeRoute, "Inicio", Icons.Default.Home),
        NavItem(LibraryRoute, "Biblioteca", Icons.Default.LibraryMusic),
        NavItem(SearchRoute, "Buscar", Icons.Default.Search),
        NavItem(LikedRoute, "Me gusta", Icons.Default.Favorite),
        NavItem(DspRoute, "DSP / Ecualizador", Icons.Default.Equalizer),
    )
    Column(
        Modifier.width(232.dp).fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.32f))
            .drawBehind { drawLine(Color.White.copy(alpha = 0.07f), Offset(size.width, 0f), Offset(size.width, size.height), 1f) }
            .padding(vertical = 20.dp),
    ) {
        Column(Modifier.padding(horizontal = 22.dp)) {
            Text("MARCO", style = Mono.copy(color = Hifi.Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp))
            Text("AUDIO PRO", style = Mono.copy(color = Hifi.Cream, fontSize = 10.sp, letterSpacing = 5.sp))
        }
        Spacer(Modifier.height(26.dp))
        items.forEach { SidebarItem(it, dest.isIn(it.route::class)) { onSelect(it.route) } }
        Spacer(Modifier.weight(1f))
        Visualizer(VizMode.SPECTRUM, Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 18.dp))
        Spacer(Modifier.height(12.dp))
        SidebarItem(NavItem(SettingsRoute, "Ajustes", Icons.Default.Settings), dest.isIn(SettingsRoute::class)) { onSelect(SettingsRoute) }
    }
}

@Composable
private fun SidebarItem(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(if (selected) Hifi.Accent else Hifi.TextDim, label = "side")
    Row(
        Modifier.padding(horizontal = 10.dp, vertical = 2.dp).fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .bouncyClick(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(item.icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(item.label, color = if (selected) Hifi.Text else Hifi.TextDim, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun PlayerBar(onMiniPlayer: (() -> Unit)?, onOpen: () -> Unit) {
    val c = LocalApp.current
    val player = c.player
    val st by player.state.collectAsState()
    val song = st.current ?: return
    val tint by rememberCoverTint(song.coverArt, Color.Black, 0.5f)
    val pos by rememberPlaybackPosition(200)
    var dragging by remember { mutableStateOf<Float?>(null) }
    val dur = st.durationMs.coerceAtLeast(1)
    val value = dragging ?: (pos.toFloat() / dur).coerceIn(0f, 1f)

    Row(
        Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp).fillMaxWidth().height(92.dp)
            .shadow(24.dp, RoundedCornerShape(22.dp))
            .glass(RoundedCornerShape(22.dp), tint.copy(alpha = 0.62f))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Canción actual
        Row(Modifier.width(320.dp).bouncyClick(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
            CoverArt(song.coverArt, Modifier.size(64.dp).shadow(10.dp, RoundedCornerShape(10.dp)), size = 400, corner = 10.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist ?: "", fontSize = 13.sp, color = Hifi.OnArt, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val fmt = listOfNotNull(
                    song.suffix?.uppercase(),
                    song.bitDepth?.takeIf { it > 0 }?.let { "$it-bit" },
                    song.samplingRate?.takeIf { it > 0 }?.let { "${khz(it)} kHz" },
                ).joinToString(" · ")
                Text(fmt, style = Mono.copy(color = if (song.isHiRes) Hifi.HiResGold else Hifi.OnArt.copy(alpha = 0.6f), fontSize = 10.sp))
            }
            LikeButton(song, size = 20.dp)
        }

        // Controles + barra de avance
        Column(Modifier.weight(1f).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = { player.toggleShuffle() }) {
                    Icon(Icons.Default.Shuffle, "Aleatorio", tint = if (st.shuffle) Color.White else Hifi.OnArt.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { player.previous() }) { Icon(Icons.Default.SkipPrevious, "Anterior", Modifier.size(28.dp)) }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color.White).bouncyClick { player.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (st.isBuffering) CircularProgressIndicator(Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                    else Icon(if (st.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Reproducir", tint = Color.Black)
                }
                IconButton(onClick = { player.next() }) { Icon(Icons.Default.SkipNext, "Siguiente", Modifier.size(28.dp)) }
                IconButton(onClick = { player.cycleRepeat() }) {
                    Icon(
                        if (st.repeat == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, "Repetir",
                        tint = if (st.repeat != RepeatMode.OFF) Color.White else Hifi.OnArt.copy(alpha = 0.5f), modifier = Modifier.size(20.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatMs((value * dur).toLong()), style = Mono.copy(color = Hifi.TextDim, fontSize = 10.sp), modifier = Modifier.width(48.dp))
                AppleBar(
                    value, onChange = { dragging = it },
                    onCommit = { v -> player.seekTo((v * dur).toLong()); dragging = null },
                    modifier = Modifier.weight(1f),
                )
                Text(formatMs(dur), style = Mono.copy(color = Hifi.TextDim, fontSize = 10.sp), modifier = Modifier.width(48.dp).padding(start = 8.dp))
            }
        }

        // Visualizador, volumen y vista completa
        Row(Modifier.width(320.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            Visualizer(VizMode.SPECTRUM, Modifier.width(90.dp).height(40.dp))
            Spacer(Modifier.width(8.dp))
            player.volume?.let { volFlow ->
                val vol by volFlow.collectAsState()
                IconButton(onClick = { player.setVolume(if (vol > 0f) 0f else 0.8f) }) {
                    Icon(if (vol > 0f) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff, "Volumen", tint = Hifi.TextDim, modifier = Modifier.size(20.dp))
                }
                AppleBar(vol, onChange = { player.setVolume(it) }, onCommit = { player.setVolume(it) }, modifier = Modifier.width(110.dp))
            }
            if (onMiniPlayer != null) {
                IconButton(onClick = onMiniPlayer) {
                    Icon(Icons.Default.PictureInPictureAlt, "Miniplayer", tint = Hifi.OnArt, modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = onOpen) { Icon(Icons.Default.OpenInFull, "Vista completa", tint = Hifi.OnArt, modifier = Modifier.size(18.dp)) }
        }
    }
}
