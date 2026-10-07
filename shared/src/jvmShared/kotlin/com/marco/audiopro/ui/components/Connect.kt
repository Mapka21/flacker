package com.marco.audiopro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Verde "en vivo" de Spotify Connect. */
val ConnectGreen = Color(0xFF1ED760)

private fun platformIcon(p: String): ImageVector = if (p == "android") Icons.Default.PhoneAndroid else Icons.Default.Computer

/** Botón de dispositivos (Marco Connect). Se pinta de verde si algo suena en otro dispositivo. */
@Composable
fun DevicesButton(modifier: Modifier = Modifier, tint: Color = Hifi.OnArt, size: Int = 22) {
    val c = LocalApp.current
    val devices by c.connect.devices.collectAsState()
    var open by remember { mutableStateOf(false) }
    val remotePlaying = devices.any { it.nowPlaying?.playing == true }
    IconButton(onClick = { open = true }, modifier) {
        Box {
            Icon(Icons.Default.Devices, "Dispositivos", tint = if (remotePlaying) ConnectGreen else tint, modifier = Modifier.size(size.dp))
            if (devices.isNotEmpty()) {
                Box(Modifier.align(Alignment.TopEnd).size(7.dp).clip(CircleShape).background(ConnectGreen))
            }
        }
    }
    if (open) DevicesDialog { open = false }
}

/** "Conectar a un dispositivo", como el de Spotify: este dispositivo + los que hay en la red. */
@Composable
fun DevicesDialog(onDismiss: () -> Unit) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    val devices by c.connect.devices.collectAsState()
    val st by c.player.state.collectAsState()
    var busy by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth()
                .shadow(30.dp, RoundedCornerShape(26.dp))
                .glass(RoundedCornerShape(26.dp), Hifi.PanelHi.copy(alpha = 0.97f))
                .padding(20.dp),
        ) {
            Text("Conectar a un dispositivo", style = Display.copy(fontSize = 22.sp))
            Spacer(Modifier.height(4.dp))
            Text("Marco Connect · misma red Wi-Fi", style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp))
            Spacer(Modifier.height(16.dp))

            // Este dispositivo
            DeviceRow(
                icon = if (c.isDesktop) Icons.Default.Computer else Icons.Default.PhoneAndroid,
                name = "Este dispositivo",
                subtitle = st.current?.let { "${it.title} · ${it.artist ?: ""}" } ?: "Nada sonando",
                active = st.isPlaying,
                trailing = { if (st.isPlaying) Icon(Icons.Default.Check, null, tint = ConnectGreen) },
                onClick = null,
            )

            if (devices.isEmpty()) {
                Text(
                    "No veo otros dispositivos. Abre Flacker en tu celular o PC conectados a la misma red." +
                        if (c.isDesktop) " Si Windows preguntó por el firewall, permite el acceso en redes privadas." else "",
                    color = Hifi.TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp),
                )
            }
            devices.forEach { d ->
                val np = d.nowPlaying
                DeviceRow(
                    icon = platformIcon(d.platform),
                    name = d.name,
                    subtitle = when {
                        busy == d.id -> "Conectando…"
                        np?.playing == true -> "▶ ${np.title} · ${np.artist ?: ""}"
                        np != null -> "En pausa · ${np.title}"
                        else -> "Disponible"
                    },
                    active = np?.playing == true,
                    trailing = {
                        if (np != null && !st.isPlaying) {
                            Text(
                                "Traer aquí", color = ConnectGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).bouncyClick {
                                    busy = d.id
                                    scope.launch { c.connect.pullFrom(d); busy = null; onDismiss() }
                                }.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    },
                    // Tocar un dispositivo = mandar ahí lo que suena aquí.
                    onClick = if (st.current != null) ({
                        busy = d.id
                        scope.launch { c.connect.transferTo(d); busy = null; onDismiss() }
                    }) else null,
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(
    icon: ImageVector,
    name: String,
    subtitle: String,
    active: Boolean,
    trailing: @Composable () -> Unit,
    onClick: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
            .background(if (active) ConnectGreen.copy(alpha = 0.10f) else Color.Transparent)
            .then(if (onClick != null) Modifier.bouncyClick(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Hifi.Glass), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (active) ConnectGreen else Hifi.Text)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (active) ConnectGreen else Hifi.Text)
            Text(subtitle, fontSize = 12.sp, color = Hifi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/**
 * Barra "Reproduciendo en …" (como la verde de Spotify): aparece cuando otro dispositivo
 * suena y éste no. Control remoto + "Traer aquí".
 */
@Composable
fun RemotePlaybackBanner(modifier: Modifier = Modifier) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    val devices by c.connect.devices.collectAsState()
    val local by c.player.state.collectAsState()
    val remote = devices.firstOrNull { it.nowPlaying?.playing == true }
    AnimatedVisibility(
        remote != null && !local.isPlaying,
        enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        val d = remote ?: return@AnimatedVisibility
        val np = d.nowPlaying ?: return@AnimatedVisibility
        Row(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp), ConnectGreen.copy(alpha = 0.16f)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverArt(np.coverArt, Modifier.size(40.dp), size = 120, corner = 8.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(platformIcon(d.platform), null, tint = ConnectGreen, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reproduciendo en ${d.name}", style = Mono.copy(color = ConnectGreen, fontSize = 10.sp))
                }
                Text("${np.title} · ${np.artist ?: ""}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { scope.launch { c.connect.remote(d, "toggle") } }) {
                Icon(if (np.playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/pausa remoto", tint = Hifi.Text)
            }
            IconButton(onClick = { scope.launch { c.connect.remote(d, "next") } }) { Icon(Icons.Default.SkipNext, "Siguiente remoto", tint = Hifi.Text) }
            Text(
                "Traer", color = ConnectGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).bouncyClick { scope.launch { c.connect.pullFrom(d) } }.padding(8.dp),
            )
        }
    }
}

/** Avisos cortos de Connect ("Ahora suena en PC-MARCO"). */
@Composable
fun ConnectToast(modifier: Modifier = Modifier) {
    val c = LocalApp.current
    val notice by c.connect.notice.collectAsState()
    LaunchedEffect(notice) { if (notice != null) { delay(3000); c.connect.notice.value = null } }
    AnimatedVisibility(notice != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut(), modifier = modifier) {
        Row(
            Modifier.shadow(16.dp, RoundedCornerShape(50)).glass(RoundedCornerShape(50), Hifi.PanelHi.copy(alpha = 0.95f))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Devices, null, tint = ConnectGreen, modifier = Modifier.size(16.dp))
            Text(notice ?: "", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
