package com.marco.audiopro.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.marco.audiopro.container
import com.marco.audiopro.data.ReplayGainMode
import com.marco.audiopro.data.StreamQuality
import com.marco.audiopro.ui.components.SectionHeader
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(nav: NavController) {
    val c = LocalContext.current.container
    val prefs by c.settings.prefs.collectAsState()
    val account by c.settings.account.collectAsState()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text("Ajustes", fontSize = 22.sp)
        }

        SectionHeader("Servidor")
        Card {
            Text(account?.url ?: "", style = Mono.copy(color = Hifi.Cream, fontSize = 12.sp))
            Text("Usuario: ${account?.user}", color = Hifi.TextDim, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { c.settings.saveAccount(null) }) { Text("Cerrar sesión", color = Hifi.LedRed) }
        }

        SectionHeader("Calidad de streaming")
        Card {
            QualityPicker("Wi-Fi", prefs.wifiQuality) { q -> c.settings.updatePrefs { it.copy(wifiQuality = q) } }
            QualityPicker("Datos móviles", prefs.mobileQuality) { q -> c.settings.updatePrefs { it.copy(mobileQuality = q) } }
            Hint("Las opciones con pérdida las transcodifica Navidrome (necesita ffmpeg). Aplica a las canciones que pongas a partir de ahora.")
        }

        SectionHeader("ReplayGain")
        Card {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReplayGainMode.entries.forEach { m ->
                    FilterChip(
                        selected = prefs.replayGain == m, onClick = { c.settings.updatePrefs { it.copy(replayGain = m) } },
                        label = { Text(m.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Hifi.AmberDim, selectedLabelColor = Hifi.Cream),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pre-amp", fontSize = 14.sp, modifier = Modifier.width(80.dp))
                Slider(
                    prefs.replayGainPreampDb, { v -> c.settings.updatePrefs { it.copy(replayGainPreampDb = Math.round(v * 2) / 2f) } },
                    valueRange = -12f..6f, modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = Hifi.Cream, activeTrackColor = Hifi.Amber, inactiveTrackColor = Hifi.Line),
                )
                Text("%+.1f dB".format(prefs.replayGainPreampDb), style = Mono.copy(color = Hifi.Cream), modifier = Modifier.width(70.dp).padding(start = 8.dp))
            }
            Hint("Usa los tags de ReplayGain de tus archivos para nivelar el volumen. Nunca sube por encima del pico real (sin clipping).")
        }

        SectionHeader("Visualizadores")
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sync", fontSize = 14.sp, modifier = Modifier.width(80.dp))
                Slider(
                    prefs.visualOffsetMs.toFloat(), { v -> c.settings.updatePrefs { it.copy(visualOffsetMs = (v / 10).toInt() * 10) } },
                    valueRange = -100f..400f, modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = Hifi.Cream, activeTrackColor = Hifi.Amber, inactiveTrackColor = Hifi.Line),
                )
                Text("${prefs.visualOffsetMs} ms", style = Mono.copy(color = Hifi.Cream), modifier = Modifier.width(70.dp).padding(start = 8.dp))
            }
            Hint("Si los visualizadores van adelantados al sonido (pasa con audífonos Bluetooth), súbelo a ~200 ms.")
        }

        SectionHeader("Reseñas de álbum")
        Card {
            KeyField("API key de Last.fm", prefs.lastFmKey) { k -> c.settings.updatePrefs { it.copy(lastFmKey = k) } }
            Hint("Gratis en last.fm/api/account/create. Sin ella se usan las notas que Navidrome ya trae de Last.fm.")
            Spacer(Modifier.height(10.dp))
            KeyField("API key de Anthropic (Claude)", prefs.anthropicKey) { k -> c.settings.updatePrefs { it.copy(anthropicKey = k) } }
            Hint("Opcional, en console.anthropic.com. Si Last.fm no tiene nada del álbum, Claude escribe una reseña (una vez por álbum, queda guardada).")
        }

        SectionHeader("Caché")
        Card {
            var used by remember { mutableLongStateOf(-1L) }
            LaunchedEffect(Unit) { used = withContext(Dispatchers.IO) { c.mediaCache.cacheSpace } }
            Text(
                if (used < 0) "Calculando…" else "Música en caché: %.1f MB de 3 GB".format(used / 1_048_576.0),
                style = Mono.copy(color = Hifi.Cream, fontSize = 12.sp),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { c.mediaCache.keys.toList().forEach { c.mediaCache.removeResource(it) } }
                    used = withContext(Dispatchers.IO) { c.mediaCache.cacheSpace }
                }
            }) { Text("Vaciar caché") }
            Hint("Lo que ya escuchaste se guarda para no volver a gastar datos.")
        }

        Text(
            "MARCO AUDIO PRO · v1.0", style = Mono.copy(color = Hifi.TextFaint),
            modifier = Modifier.fillMaxWidth().padding(28.dp),
        )
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Hifi.Panel).padding(16.dp),
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
                Text(value.label, color = Hifi.Amber, fontSize = 14.sp)
                Icon(Icons.Default.ArrowDropDown, null, tint = Hifi.Amber)
            }
            DropdownMenu(open, { open = false }) {
                StreamQuality.entries.forEach { q ->
                    DropdownMenuItem(text = { Text(q.label) }, onClick = { onPick(q); open = false })
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
        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
    )
}
