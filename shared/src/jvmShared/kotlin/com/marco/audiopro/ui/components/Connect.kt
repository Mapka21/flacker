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
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
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
    val controlling by c.connect.controlling.collectAsState()
    val tv by c.cast.active.collectAsState()
    val remotePlaying = controlling != null || tv != null || devices.any { it.nowPlaying?.playing == true }
    IconButton(onClick = { open = true }, modifier) {
        Box {
            Icon(if (tv != null) Icons.Default.Cast else Icons.Default.Devices, "Dispositivos", tint = if (remotePlaying) ConnectGreen else tint, modifier = Modifier.size(size.dp))
            if (devices.isNotEmpty()) {
                Box(Modifier.align(Alignment.TopEnd).size(7.dp).clip(CircleShape).background(ConnectGreen))
            }
        }
    }
    if (open) DevicesDialog { open = false }
}

/**
 * "Conectar a un dispositivo", como el de Spotify: elegir dónde suena. Al elegir otro
 * dispositivo la música pasa allá y toda la app lo controla (play, barra, volumen, cola).
 */
@Composable
fun DevicesDialog(onDismiss: () -> Unit) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    val devices by c.connect.devices.collectAsState()
    val controlling by c.connect.controlling.collectAsState()
    val st by c.player.state.collectAsState()
    val vol = c.player.volume?.collectAsState()?.value
    var busy by remember { mutableStateOf<String?>(null) }
    fun go(block: suspend () -> Unit, id: String) {
        busy = id
        scope.launch { block(); busy = null; onDismiss() }
    }
    val tvs by c.cast.devices.collectAsState()
    val tvScanning by c.cast.scanning.collectAsState()
    val tv by c.cast.active.collectAsState()
    var castError by remember { mutableStateOf<String?>(null) }
    val routed = c.player as? com.marco.audiopro.player.RoutedPlayer
    LaunchedEffect(Unit) { if (tv == null) c.cast.scan() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth()
                .shadow(30.dp, RoundedCornerShape(26.dp))
                .glass(RoundedCornerShape(26.dp), Hifi.PanelHi.copy(alpha = 0.97f))
                .padding(20.dp),
        ) {
            Text("Conectar a un dispositivo", style = Display.copy(fontSize = 22.sp))
            Spacer(Modifier.height(4.dp))
            Text("Flacker Connect, Chromecast, Roku y DLNA · misma red", style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp))
            Spacer(Modifier.height(16.dp))

            val here = controlling == null && tv == null
            DeviceRow(
                icon = if (c.isDesktop) Icons.Default.Computer else Icons.Default.PhoneAndroid,
                name = "Este dispositivo",
                subtitle = if (here) st.current?.let { "${it.title} · ${it.artist ?: ""}" } ?: "Nada sonando" else "Toca para traer la música aquí",
                active = here,
                trailing = { if (here) Icon(Icons.Default.Check, null, tint = ConnectGreen) },
                onClick = when {
                    tv != null -> ({ go({ routed?.stopCasting() }, "here") })
                    else -> controlling?.let { d -> { go({ c.connect.pullFrom(d) }, "here") } }
                },
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
                val active = controlling?.id == d.id
                DeviceRow(
                    icon = platformIcon(d.platform),
                    name = d.name,
                    subtitle = when {
                        busy == d.id -> "Conectando…"
                        active -> "Controlando · ${np?.title ?: "…"}"
                        np?.playing == true -> "▶ ${np.title} · ${np.artist ?: ""}"
                        np != null -> "En pausa · ${np.title}"
                        else -> "Disponible"
                    },
                    active = active,
                    trailing = {
                        if (!active && np != null) {
                            Text(
                                "Controlar", color = ConnectGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).bouncyClick { c.connect.control(d); onDismiss() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                        if (active) Icon(Icons.Default.Check, null, tint = ConnectGreen)
                    },
                    // Tocar un dispositivo = que la música suene allá (venga de aquí o de otro).
                    onClick = when {
                        active -> null
                        controlling != null -> ({ go({ c.connect.moveBetween(controlling!!, d) }, d.id) })
                        st.current != null -> ({ go({ c.connect.transferTo(d) }, d.id) })
                        np != null -> ({ c.connect.control(d); onDismiss() })
                        else -> null
                    },
                )
            }
            // Teles y bocinas: Chromecast, Roku y DLNA.
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TRANSMITIR A LA TELE", style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp), modifier = Modifier.weight(1f))
                if (tvScanning) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = ConnectGreen)
                else Icon(
                    Icons.Default.Refresh, "Buscar otra vez", tint = Hifi.TextDim,
                    modifier = Modifier.size(28.dp).clip(CircleShape).bouncyClick { c.cast.scan() }.padding(5.dp),
                )
            }
            if (tvs.isEmpty() && !tvScanning) Text(
                "No encontré teles ni bocinas. Tienen que estar prendidas y en el mismo Wi-Fi (Chromecast, Roku o DLNA).",
                color = Hifi.TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
            )
            tvs.forEach { d ->
                val active = tv?.id == d.id
                DeviceRow(
                    icon = when (d.kind) { com.marco.audiopro.cast.CastDevice.Kind.DLNA -> Icons.Default.Speaker; com.marco.audiopro.cast.CastDevice.Kind.ROKU -> Icons.Default.Tv; else -> Icons.Default.Cast },
                    name = d.name,
                    subtitle = when {
                        busy == d.id -> "Conectando…"
                        active -> "Transmitiendo · ${st.current?.title ?: "…"}"
                        else -> listOfNotNull(d.kind.label, d.model).distinct().joinToString(" · ")
                    },
                    active = active,
                    trailing = { if (active) Icon(Icons.Default.Check, null, tint = ConnectGreen) },
                    onClick = if (active || routed == null) null else ({
                        busy = d.id; castError = null
                        scope.launch {
                            if (tv != null) routed.stopCasting()
                            val err = routed.castTo(d)
                            busy = null
                            if (err == null) onDismiss() else castError = err
                        }
                    }),
                )
            }
            castError?.let { Text(it, color = Hifi.LedRed, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            // Volumen de donde suena (aquí, el dispositivo controlado o la tele).
            val castVol = if (tv != null && c.player.volume == null) c.cast.player.volume.collectAsState().value else null
            if (castVol != null) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.VolumeDown, null, tint = Hifi.TextDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    com.marco.audiopro.ui.screens.AppleBar(castVol, { c.cast.player.setVolume(it) }, { c.cast.player.setVolume(it) }, Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Hifi.TextDim, modifier = Modifier.size(18.dp))
                }
            }
            if (vol != null) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.VolumeDown, null, tint = Hifi.TextDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    com.marco.audiopro.ui.screens.AppleBar(vol, { c.player.setVolume(it) }, { c.player.setVolume(it) }, Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Hifi.TextDim, modifier = Modifier.size(18.dp))
                }
                Text(
                    "Volumen de ${tv?.name ?: controlling?.name ?: "este dispositivo"}", style = Mono.copy(color = Hifi.TextFaint, fontSize = 9.sp),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Pastilla verde "Reproduciendo en PC-MARCO" (como Spotify) cuando controlas otro dispositivo. */
@Composable
fun RemoteBadge(name: String?, modifier: Modifier = Modifier) {
    if (name == null) return
    Row(
        modifier.clip(RoundedCornerShape(50)).background(ConnectGreen.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Devices, null, tint = ConnectGreen, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(6.dp))
        Text("Reproduciendo en $name", color = ConnectGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
 * Aviso "Suena en …" cuando otro dispositivo está reproduciendo y aquí no (y no lo controlas):
 * tocarlo lo controla desde aquí; "Traer" pasa la música a este dispositivo.
 */
@Composable
fun RemotePlaybackBanner(modifier: Modifier = Modifier) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    val devices by c.connect.devices.collectAsState()
    val controlling by c.connect.controlling.collectAsState()
    val local by c.player.state.collectAsState()
    val remote = devices.firstOrNull { it.nowPlaying?.playing == true }
    AnimatedVisibility(
        remote != null && !local.isPlaying && controlling == null,
        enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        val d = remote ?: return@AnimatedVisibility
        val np = d.nowPlaying ?: return@AnimatedVisibility
        Row(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp), ConnectGreen.copy(alpha = 0.16f))
                .bouncyClick { c.connect.control(d) }.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverArt(np.coverArt, Modifier.size(40.dp), size = 120, corner = 8.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(platformIcon(d.platform), null, tint = ConnectGreen, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Suena en ${d.name} · toca para controlar", style = Mono.copy(color = ConnectGreen, fontSize = 10.sp), maxLines = 1)
                }
                Text("${np.title} · ${np.artist ?: ""}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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
    val castNotice by c.cast.notice.collectAsState()
    LaunchedEffect(castNotice) { castNotice?.let { c.connect.notice.value = it; c.cast.notice.value = null } }
    val notice by c.connect.notice.collectAsState()
    LaunchedEffect(notice) { if (notice != null) { delay(if (notice!!.length > 60) 7000 else 3000); c.connect.notice.value = null } }
    AnimatedVisibility(notice != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut(), modifier = modifier) {
        Row(
            Modifier.shadow(16.dp, RoundedCornerShape(50)).glass(RoundedCornerShape(50), Hifi.PanelHi.copy(alpha = 0.95f))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Devices, null, tint = ConnectGreen, modifier = Modifier.size(16.dp))
            Text(notice ?: "", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(max = 520.dp))
        }
    }
}
