package com.marco.audiopro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.data.CustomCovers
import com.marco.audiopro.data.info.LfUser
import com.marco.audiopro.data.local.LocalMode
import com.marco.audiopro.ui.DownloadsRoute
import com.marco.audiopro.ui.DspRoute
import com.marco.audiopro.ui.FixRoute
import com.marco.audiopro.ui.LastFmRoute
import com.marco.audiopro.ui.ProfileRoute
import com.marco.audiopro.ui.SettingsRoute
import com.marco.audiopro.ui.components.AppMenu
import com.marco.audiopro.ui.components.AppMenuDivider
import com.marco.audiopro.ui.components.AppMenuItem
import com.marco.audiopro.ui.components.ConnectGreen
import com.marco.audiopro.ui.components.CoverEditButton
import com.marco.audiopro.ui.components.DevicesDialog
import com.marco.audiopro.ui.components.GlassDialog
import com.marco.audiopro.ui.components.Load
import com.marco.audiopro.ui.components.SectionHeader
import com.marco.audiopro.ui.components.StatTile
import com.marco.audiopro.ui.components.bouncyClick
import com.marco.audiopro.ui.components.fmt
import com.marco.audiopro.ui.components.glassCard
import com.marco.audiopro.ui.components.hoverHighlight
import com.marco.audiopro.ui.components.rememberLoad
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import java.util.Calendar

// ------------------------------------------------------------------ quién eres

/** Tu perfil de Last.fm si lo conectaste (foto, nombre y números); null si no. */
@Composable
fun rememberLastFmUser(): LfUser? {
    val c = LocalApp.current
    val prefs by c.settings.prefs.collectAsState()
    val key = prefs.lastFmUser to prefs.lastFmKey
    val load = rememberLoad(key) { if (key.first.isBlank() || key.second.isBlank()) null else c.lastFm.user(key.second, key.first) }
    return (load.state.value as? Load.Ok)?.value
}

/** Tu nombre: el de Last.fm si tiene uno; si no, tu usuario de Navidrome. */
@Composable
fun profileName(lf: LfUser?): String {
    val c = LocalApp.current
    val account by c.settings.account.collectAsState()
    return lf?.realName ?: lf?.name ?: account?.user?.takeIf { !LocalMode.isOn(c.settings) } ?: "Tú"
}

/** Dónde vive tu música, en una línea. */
@Composable
private fun profileWhere(): String {
    val c = LocalApp.current
    val account by c.settings.account.collectAsState()
    val lib = c.localLibrary
    if (lib != null && LocalMode.isOn(c.settings)) {
        val n by lib.count.collectAsState()
        return "Sin servidor · $n canciones en este dispositivo"
    }
    val host = account?.url?.substringAfter("://")?.substringBefore("/") ?: ""
    return "@${account?.user ?: ""} · $host"
}

/**
 * Tu foto: la que elegiste (viaja a tus otros dispositivos), si no la de Last.fm, y si no tu
 * inicial sobre gris.
 */
@Composable
fun UserAvatar(size: Dp, lf: LfUser? = rememberLastFmUser(), modifier: Modifier = Modifier) {
    val c = LocalApp.current
    c.covers.map.collectAsState().value
    val custom = c.covers.get(CustomCovers.PROFILE)
    val name = profileName(lf)
    Box(
        modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF3A3B42), Color(0xFF131418))))
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), style = Display.copy(fontSize = (size.value * 0.42f).sp, color = Hifi.Text))
        val img: Any? = custom ?: lf?.image?.takeIf { it.isNotBlank() }
        if (img != null) AsyncImage(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

// ------------------------------------------------------------------ menú del avatar (PC)

/**
 * Tu avatar arriba a la derecha (como Spotify): abre un menú con tu perfil, las herramientas,
 * Ajustes y cerrar sesión.
 */
@Composable
fun ProfileMenuButton(nav: NavController, modifier: Modifier = Modifier) {
    val c = LocalApp.current
    val lf = rememberLastFmUser()
    var open by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }
    val name = profileName(lf)
    val where = profileWhere()
    fun go(route: Any) { open = false; nav.navigate(route) { launchSingleTop = true } }
    Box(modifier) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.36f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape).bouncyClick { open = true },
            contentAlignment = Alignment.Center,
        ) { UserAvatar(34.dp, lf) }
        AppMenu(open, { open = false }, offset = androidx.compose.ui.unit.DpOffset((-196).dp, 6.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).hoverHighlight().bouncyClick { go(ProfileRoute) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(38.dp, lf)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.width(170.dp)) {
                    Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(where, fontSize = 11.sp, color = Hifi.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            AppMenuDivider()
            AppMenuItem("Perfil", Icons.Default.Person) { go(ProfileRoute) }
            AppMenuItem("Mi Last.fm", Icons.Default.Insights) { go(LastFmRoute) }
            AppMenuDivider()
            AppMenuItem("Ecualizador", Icons.Default.Equalizer) { go(DspRoute) }
            if (c.fixer != null) AppMenuItem("Arreglar metadatos", Icons.Default.AutoFixHigh) { go(FixRoute) }
            AppMenuItem("Ajustes", Icons.Default.Settings) { go(SettingsRoute) }
            AppMenuDivider()
            AppMenuItem(logoutLabel(), Icons.AutoMirrored.Filled.Logout, destructive = true) { open = false; logout = true }
        }
    }
    if (logout) LogoutDialog { logout = false }
}

@Composable
private fun logoutLabel(): String {
    val c = LocalApp.current
    return if (c.localLibrary != null && LocalMode.isOn(c.settings)) "Usar un servidor Navidrome" else "Cerrar sesión"
}

/** Confirmación antes de salir (desde un menú es fácil darle sin querer). */
@Composable
private fun LogoutDialog(onDismiss: () -> Unit) {
    val c = LocalApp.current
    val local = c.localLibrary != null && LocalMode.isOn(c.settings)
    GlassDialog(onDismiss) {
        Text(if (local) "¿Dejar el modo sin servidor?" else "¿Cerrar sesión?", style = Display.copy(fontSize = 22.sp))
        Spacer(Modifier.height(8.dp))
        Text(
            if (local) "Vas a poder entrar a tu Navidrome. Tus me gusta y playlists de este dispositivo se quedan guardados."
            else "Tus descargas, ecualizador y ajustes se quedan. Para volver sólo pon otra vez tu servidor y contraseña.",
            color = Hifi.TextDim, fontSize = 14.sp,
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Hifi.TextDim) }
            TextButton(onClick = {
                onDismiss()
                if (local) LocalMode.disable(c.settings, c.localLibrary) else c.settings.saveAccount(null)
            }) { Text(if (local) "Salir" else "Cerrar sesión", color = Hifi.LedRed, fontWeight = FontWeight.Bold) }
        }
    }
}

// ------------------------------------------------------------------ pantalla de perfil

/**
 * Tu perfil: quién eres, tus números, tu Last.fm, las herramientas (ecualizador, arreglar
 * metadatos, descargas, ajustes) y tus otros dispositivos con Flacker.
 */
@Composable
fun ProfileScreen(nav: NavController) {
    val c = LocalApp.current
    val lf = rememberLastFmUser()
    val liked by c.likes.songs.collectAsState()
    val lists by c.playlists.lists.collectAsState()
    val devices by c.connect.devices.collectAsState()
    var devicesOpen by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }
    fun go(route: Any) = nav.navigate(route) { launchSingleTop = true }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 700.dp
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            // Sin flecha de regresar: en el celular es una pestaña y en la PC está la de arriba.
            Spacer(Modifier.height(if (wide) 0.dp else 24.dp))
            // Cabecera: foto grande (con lápiz para cambiarla), nombre y dónde vive tu música.
            val avatar = if (wide) 148.dp else 112.dp
            if (wide) {
                Row(Modifier.padding(start = 28.dp, end = 28.dp, top = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                    AvatarWithEdit(avatar, lf)
                    Spacer(Modifier.width(26.dp))
                    ProfileTitle(lf, Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AvatarWithEdit(avatar, lf)
                    Spacer(Modifier.height(16.dp))
                    ProfileTitle(lf, center = true)
                }
            }

            // Números.
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Me gusta", fmt(liked.size.toLong()), Modifier.weight(1f))
                StatTile("Playlists", fmt(lists.size.toLong()), Modifier.weight(1f))
                if (lf != null) {
                    StatTile("Scrobbles", fmt(lf.scrobbles), Modifier.weight(1f))
                    if (wide) StatTile("Artistas", fmt(lf.artists), Modifier.weight(1f))
                }
            }

            // Last.fm.
            SectionHeader("Last.fm")
            ActionRow(
                Icons.Default.Insights,
                if (lf != null) "Mi Last.fm" else "Conecta tu Last.fm",
                lf?.let { "${fmt(it.scrobbles)} scrobbles${it.registered?.let { r -> " · desde ${yearOf(r)}" } ?: ""}" }
                    ?: "Tus números, gráficas, tops y scrobbles, aquí adentro",
            ) { go(LastFmRoute) }

            // Herramientas.
            SectionHeader("Herramientas")
            val tools = buildList {
                add(Tool(Icons.Default.Equalizer, "Ecualizador", "EQ paramétrico, presets y volumen parejo", DspRoute))
                if (c.fixer != null) add(Tool(Icons.Default.AutoFixHigh, "Arreglar metadatos", "Géneros, años y portadas con MusicBrainz", FixRoute))
                if (c.downloads != null) add(Tool(Icons.Outlined.DownloadForOffline, "Descargas", "Lo que suena sin conexión", DownloadsRoute))
                add(Tool(Icons.Default.Settings, "Ajustes", "Calidad, apariencia, modo visual, caché", SettingsRoute))
            }
            val cols = if (wide) 2 else 1
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tools.chunked(cols).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { t -> ToolCard(t, Modifier.weight(1f)) { go(t.route) } }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            // Tus dispositivos (Flacker Connect).
            SectionHeader("Tus dispositivos")
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().glassCard().padding(vertical = 6.dp)) {
                DeviceLine(if (c.isDesktop) Icons.Default.Computer else Icons.Default.PhoneAndroid, "Este dispositivo", "Flacker ${c.versionName}", here = true)
                devices.forEach { d ->
                    val np = d.nowPlaying
                    DeviceLine(
                        if (d.platform == "android") Icons.Default.PhoneAndroid else Icons.Default.Computer, d.name,
                        when { np?.playing == true -> "▶ ${np.title ?: "…"}"; np != null -> "En pausa · ${np.title ?: "…"}"; else -> "Disponible" },
                    )
                }
                if (devices.isEmpty()) {
                    Text(
                        "Abre Flacker en tu celular o en otra PC de la misma red y aparecen aquí.",
                        fontSize = 12.sp, color = Hifi.TextFaint, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                TextButton(onClick = { devicesOpen = true }, modifier = Modifier.padding(horizontal = 6.dp)) {
                    Icon(Icons.Default.Devices, null, tint = Hifi.Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Conectar a un dispositivo o a la tele", color = Hifi.Accent)
                }
            }

            // Salir.
            Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).border(1.dp, Hifi.LedRed.copy(alpha = 0.45f), RoundedCornerShape(50))
                        .bouncyClick { logout = true }.padding(horizontal = 20.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Hifi.LedRed, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(logoutLabel(), color = Hifi.LedRed, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }
    if (devicesOpen) DevicesDialog { devicesOpen = false }
    if (logout) LogoutDialog { logout = false }
}

private fun yearOf(epochS: Long): Int = Calendar.getInstance().apply { timeInMillis = epochS * 1000 }.get(Calendar.YEAR)

@Composable
private fun AvatarWithEdit(size: Dp, lf: LfUser?) {
    Box {
        UserAvatar(size, lf, Modifier.shadow(24.dp, CircleShape))
        CoverEditButton(CustomCovers.PROFILE, Modifier.align(Alignment.BottomEnd).padding(4.dp))
    }
}

@Composable
private fun ProfileTitle(lf: LfUser?, modifier: Modifier = Modifier, center: Boolean = false) {
    Column(modifier, horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        Text("PERFIL", style = Mono.copy(color = Hifi.TextDim, letterSpacing = 1.2.sp))
        Text(profileName(lf), style = Display.copy(fontSize = if (center) 34.sp else 52.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(profileWhere(), fontSize = 14.sp, color = Hifi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        lf?.country?.let { Text(it, fontSize = 13.sp, color = Hifi.TextFaint) }
    }
}

private data class Tool(val icon: ImageVector, val title: String, val subtitle: String, val route: Any)

@Composable
private fun ToolCard(t: Tool, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.glassCard().hoverHighlight(shape = RoundedCornerShape(20.dp)).bouncyClick(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = 0.09f)), contentAlignment = Alignment.Center) {
            Icon(t.icon, null, tint = Hifi.Text, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(t.subtitle, fontSize = 12.sp, color = Hifi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Hifi.TextFaint)
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) =
    ToolCard(Tool(icon, title, subtitle, Unit), Modifier.padding(horizontal = 16.dp).fillMaxWidth(), onClick)

@Composable
private fun DeviceLine(icon: ImageVector, name: String, subtitle: String, here: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (here) ConnectGreen else Hifi.TextDim, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (here) ConnectGreen else Hifi.Text)
            Text(subtitle, fontSize = 12.sp, color = Hifi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
