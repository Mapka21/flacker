package com.marco.audiopro.ui.screens

import com.marco.audiopro.ui.components.AppMenu
import com.marco.audiopro.ui.components.AppMenuDivider
import com.marco.audiopro.ui.components.AppMenuItem
import androidx.compose.material.icons.filled.Check
import com.marco.audiopro.ui.components.textInput
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.marco.audiopro.core.LocalApp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.marco.audiopro.data.ReplayGainMode
import com.marco.audiopro.data.StreamQuality
import com.marco.audiopro.ui.components.GlassChip
import com.marco.audiopro.ui.components.SectionHeader
import com.marco.audiopro.ui.components.glassCard
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.viz.VisualPreset
import com.marco.audiopro.ui.theme.AccentTheme
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(nav: NavController) {
    val c = LocalApp.current
    val prefs by c.settings.prefs.collectAsState()
    val account by c.settings.account.collectAsState()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            com.marco.audiopro.ui.components.ScreenBackButton(nav)
            Text("Ajustes", style = Display.copy(fontSize = 30.sp), modifier = Modifier.padding(start = if (c.isDesktop) 20.dp else 0.dp))
        }

        val lib = c.localLibrary
        if (lib != null && com.marco.audiopro.data.local.LocalMode.isOn(c.settings)) {
            SectionHeader("Tu música (sin servidor)")
            Card { LocalLibraryCard(lib) }
        } else {
        SectionHeader("Servidor")
        Card {
            Text(account?.url ?: "", style = Mono.copy(color = Hifi.Cream, fontSize = 12.sp))
            Text("Usuario: ${account?.user}", color = Hifi.TextDim, fontSize = 13.sp)
            // Scrobbles que se escucharon sin red y esperan para mandarse.
            c.api.offlineQueue?.let { q ->
                val pending by q.pending.collectAsState()
                Text(
                    if (pending == 0) "Scrobbles al día" else "$pending scrobble${if (pending == 1) "" else "s"} esperando conexión (se mandan solos con su hora original)",
                    color = if (pending == 0) Hifi.TextFaint else Hifi.Cream, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { c.settings.saveAccount(null) }) { Text("Cerrar sesión", color = Hifi.LedRed) }
        }
        }

        SectionHeader("Apariencia")
        Card {
            Text("Color de acento", fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AccentTheme.entries.forEach { t ->
                    val sel = prefs.accent == t
                    Column(
                        Modifier.clip(RoundedCornerShape(10.dp)).clickable { c.settings.updatePrefs { it.copy(accent = t) } }.padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(t.color?.let { SolidColor(it) } ?: Brush.sweepGradient(listOf(Color(0xFFFF4D8D), Color(0xFFFFB000), Color(0xFF3CF28E), Color(0xFF3DD9EB), Color(0xFFA48BFF), Color(0xFFFF4D8D))))
                                .border(if (sel) 3.dp else 1.dp, if (sel) Color.White else Hifi.Line, CircleShape),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(t.label, fontSize = 9.sp, color = if (sel) Hifi.Text else Hifi.TextFaint, maxLines = 1)
                    }
                }
            }
            Hint("\"Portada\" toma el color del disco que está sonando.")
        }

        SectionHeader("Calidad de streaming")
        Card {
            QualityPicker(if (c.isDesktop) "Calidad" else "Wi-Fi", prefs.wifiQuality) { q -> c.settings.updatePrefs { it.copy(wifiQuality = q) } }
            if (!c.isDesktop) {
                QualityPicker("Datos móviles", prefs.mobileQuality) { q -> c.settings.updatePrefs { it.copy(mobileQuality = q) } }
            }
            Hint("Las opciones con pérdida las transcodifica Navidrome (necesita ffmpeg). Aplica a las canciones que pongas a partir de ahora.")
        }

        if (c.downloads != null) SectionHeader("Descargas")
        if (c.downloads != null) Card {
            QualityPicker("Calidad de descarga", prefs.downloadQuality) { q -> c.settings.updatePrefs { it.copy(downloadQuality = q) } }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Descargar sólo con Wi-Fi", fontSize = 15.sp, modifier = Modifier.weight(1f))
                Switch(
                    prefs.downloadWifiOnly, { on -> c.settings.updatePrefs { it.copy(downloadWifiOnly = on) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = Hifi.Accent, checkedThumbColor = Color.Black),
                )
            }
            Hint("Las descargas se guardan en la memoria del teléfono y suenan sin conexión, aunque tu servidor esté apagado.")
        }

        if (!c.isDesktop) {
            SectionHeader("Reproductor")
            Card {
                var gestures by remember { mutableStateOf(c.settings.getString("gesture_player") != "0") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Controles por gestos", fontSize = 15.sp)
                        Text("Sin botones: toca la portada para pausar, desliza para cambiar, mantén y arrastra para adelantar.", fontSize = 12.sp, color = Hifi.TextDim)
                    }
                    androidx.compose.material3.Switch(
                        gestures, { on -> gestures = on; c.settings.putString("gesture_player", if (on) "1" else "0") },
                        colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Hifi.Accent, checkedThumbColor = Color.Black),
                    )
                }
            }
        }

        SectionHeader("Volumen parejo")
        com.marco.audiopro.ui.components.LevelerCard(Modifier.padding(horizontal = 16.dp))

        SectionHeader("ReplayGain")
        Card {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReplayGainMode.entries.forEach { m ->
                    GlassChip(m.label, prefs.replayGain == m) { c.settings.updatePrefs { it.copy(replayGain = m) } }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pre-amp", fontSize = 14.sp, modifier = Modifier.width(80.dp))
                Slider(
                    prefs.replayGainPreampDb, { v -> c.settings.updatePrefs { it.copy(replayGainPreampDb = Math.round(v * 2) / 2f) } },
                    valueRange = -12f..6f, modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = Hifi.Cream, activeTrackColor = Hifi.Accent, inactiveTrackColor = Hifi.Line),
                )
                Text("%+.1f dB".format(prefs.replayGainPreampDb), style = Mono.copy(color = Hifi.Cream), modifier = Modifier.width(70.dp).padding(start = 8.dp))
            }
            Hint("Usa los tags de ReplayGain de tus archivos para nivelar el volumen. Nunca sube por encima del pico real (sin clipping).")
        }

        SectionHeader("Modo visual")
        Card {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Fondo que baila con la música", fontSize = 15.sp)
                    Text(if (prefs.visualMode) "Preset: ${prefs.visualPreset.label}" else "Apagado", fontSize = 12.sp, color = Hifi.TextDim)
                }
                Switch(
                    prefs.visualMode, { on -> c.settings.updatePrefs { it.copy(visualMode = on) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = Hifi.Accent, checkedThumbColor = Color.Black),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VisualPreset.entries.forEach { p ->
                    GlassChip(p.label, prefs.visualMode && prefs.visualPreset == p) { c.settings.updatePrefs { it.copy(visualMode = true, visualPreset = p) } }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sync", fontSize = 14.sp, modifier = Modifier.width(80.dp))
                Slider(
                    prefs.visualOffsetMs.toFloat(), { v -> c.settings.updatePrefs { it.copy(visualOffsetMs = (v / 10).toInt() * 10) } },
                    valueRange = -100f..400f, modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = Hifi.Cream, activeTrackColor = Hifi.Accent, inactiveTrackColor = Hifi.Line),
                )
                Text("${prefs.visualOffsetMs} ms", style = Mono.copy(color = Hifi.Cream), modifier = Modifier.width(70.dp).padding(start = 8.dp))
            }
            Hint(if (c.isDesktop) "En PC el sync se calcula solo con la posición real de la salida; ajústalo sólo si lo notas desfasado." else "Si el fondo va adelantado al sonido (pasa con audífonos Bluetooth), súbelo a ~200 ms.")
        }

        SectionHeader("Reseñas de álbum")
        Card {
            KeyField("API key de Last.fm", prefs.lastFmKey) { k -> c.settings.updatePrefs { it.copy(lastFmKey = k) } }
            Hint("Gratis en last.fm/api/account/create. Sin ella se usan las notas que Navidrome ya trae de Last.fm.")
            Spacer(Modifier.height(10.dp))
            KeyField("API key de Anthropic (Claude)", prefs.anthropicKey) { k -> c.settings.updatePrefs { it.copy(anthropicKey = k) } }
            Hint("Opcional, en console.anthropic.com. Si Last.fm no tiene nada del álbum, Claude escribe una reseña (una vez por álbum, queda guardada).")
        }

        c.cache?.let { cache ->
            SectionHeader("Caché")
            Card {
                var used by remember { mutableLongStateOf(-1L) }
                LaunchedEffect(Unit) { used = cache.usedBytes() }
                Text(
                    if (used < 0) "Calculando…" else "Música en caché: %.1f MB de ${cache.capacityLabel}".format(used / 1_048_576.0),
                    style = Mono.copy(color = Hifi.Cream, fontSize = 12.sp),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    scope.launch {
                        cache.clear()
                        used = cache.usedBytes()
                    }
                }) { Text("Vaciar caché") }
                Hint("Lo que ya escuchaste se guarda para no volver a gastar datos.")
            }
        }

        Column(Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            com.marco.audiopro.ui.components.AsciiArt(com.marco.audiopro.ui.components.AsciiSkull.SMALL, fontSize = 4.4.sp)
            Spacer(Modifier.height(10.dp))
            Text("Flacker", style = com.marco.audiopro.ui.theme.Display.copy(fontSize = 17.sp, color = Hifi.Text))
            Text("Versión ${c.versionName}", style = Mono.copy(color = Hifi.TextFaint))
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().glassCard().padding(16.dp),
        content = content,
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 12.sp, color = Hifi.TextFaint, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun QualityPicker(label: String, value: StreamQuality, onPick: (StreamQuality) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value.label, color = Hifi.Accent, fontSize = 14.sp)
                Icon(Icons.Default.ArrowDropDown, null, tint = Hifi.Accent)
            }
            AppMenu(open, { open = false }) {
                StreamQuality.entries.forEach { q ->
                    AppMenuItem(q.label, if (q == value) Icons.Default.Check else null, iconTint = Hifi.Accent) { onPick(q); open = false }
                }
            }
        }
    }
}

@Composable
private fun KeyField(label: String, value: String, onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        text, { text = it; onSave(it) }, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().textInput(),
    )
}

/** Biblioteca local: cuántas canciones, carpetas (PC), volver a escanear y salir del modo. */
@Composable
private fun LocalLibraryCard(lib: com.marco.audiopro.data.local.LocalLibrary) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    val count by lib.count.collectAsState()
    val scanning by lib.scanning.collectAsState()
    var folders by remember { mutableStateOf(com.marco.audiopro.data.local.LocalMode.folders(c.settings)) }
    val isFolders = com.marco.audiopro.platform.localMusicIsFolders
    val add = com.marco.audiopro.platform.rememberLocalMusicPicker { picked ->
        if (picked != null && isFolders) {
            com.marco.audiopro.data.local.LocalMode.addFolder(c.settings, picked)
            folders = com.marco.audiopro.data.local.LocalMode.folders(c.settings)
            scope.launch { lib.rescan() }
        }
    }
    Text(if (scanning != null) "Leyendo… ${scanning} canciones" else "$count canciones", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    Text(if (isFolders) "Carpetas de tu PC" else lib.source.label, color = Hifi.TextDim, fontSize = 13.sp)
    if (isFolders) folders.forEach { path ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(path, style = Mono.copy(color = Hifi.Cream, fontSize = 12.sp), modifier = Modifier.weight(1f))
            TextButton(onClick = {
                com.marco.audiopro.data.local.LocalMode.removeFolder(c.settings, path)
                folders = com.marco.audiopro.data.local.LocalMode.folders(c.settings)
                scope.launch { lib.rescan() }
            }) { Text("Quitar", color = Hifi.TextDim) }
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isFolders) OutlinedButton(onClick = add, enabled = scanning == null) { Text("Agregar carpeta") }
        OutlinedButton(onClick = { scope.launch { lib.rescan() } }, enabled = scanning == null) { Text("Volver a leer") }
    }
    Hint("Lo nuevo o cambiado se lee solo al volver a leer. Me gusta, playlists y cuántas veces pones cada canción se guardan en este dispositivo.")
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = { com.marco.audiopro.data.local.LocalMode.disable(c.settings) }) { Text("Usar un servidor Navidrome", color = Hifi.LedRed) }
}
