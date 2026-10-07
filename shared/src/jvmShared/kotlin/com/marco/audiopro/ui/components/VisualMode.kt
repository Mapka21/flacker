package com.marco.audiopro.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.viz.VisualPreset

/** Botón de "modo visual" (destellos): se ilumina con el acento cuando está encendido. */
@Composable
fun VisualModeButton(modifier: Modifier = Modifier, size: Int = 22) {
    val c = LocalApp.current
    val on = c.settings.prefs.collectAsState().value.visualMode
    var open by remember { mutableStateOf(false) }
    Box(modifier.size((size + 22).dp).bouncyClick { open = true }, contentAlignment = Alignment.Center) {
        Icon(Icons.Default.AutoAwesome, "Modo visual", tint = if (on) Hifi.Accent else Hifi.OnArt, modifier = Modifier.size(size.dp))
    }
    if (open) VisualModeDialog { open = false }
}

/** Selector del modo visual: encender/apagar y elegir preset viendo cada uno en vivo. */
@Composable
fun VisualModeDialog(onDismiss: () -> Unit) {
    val c = LocalApp.current
    val prefs by c.settings.prefs.collectAsState()
    val cover = c.player.state.collectAsState().value.current?.coverArt
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(20.dp).widthIn(max = 720.dp).fillMaxWidth()
                .shadow(30.dp, RoundedCornerShape(28.dp))
                .glass(RoundedCornerShape(28.dp), Color(0xFF15161C).copy(alpha = 0.96f))
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Modo visual", style = Display.copy(fontSize = 24.sp))
                    Text("El fondo se vuelve el visualizador y baila con tu música.", fontSize = 13.sp, color = Hifi.TextDim)
                }
                Switch(
                    prefs.visualMode, { on -> c.settings.updatePrefs { it.copy(visualMode = on) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = Hifi.Accent, checkedThumbColor = Color.Black),
                )
            }
            Spacer(Modifier.height(18.dp))
            VisualPreset.entries.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { p ->
                        PresetTile(p, cover, selected = prefs.visualMode && prefs.visualPreset == p, Modifier.weight(1f)) {
                            c.settings.updatePrefs { it.copy(visualMode = true, visualPreset = p) }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Text(
                if (prefs.visualMode) "${prefs.visualPreset.label}: ${prefs.visualPreset.blurb}." else "Apagado: el fondo sólo respira con los colores de la portada.",
                fontSize = 12.sp, color = Hifi.TextFaint,
            )
        }
    }
}

@Composable
private fun PresetTile(p: VisualPreset, cover: String?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.bouncyClick(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(16.dp))
                .border(if (selected) 2.5.dp else 1.dp, if (selected) Hifi.Accent else Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp)),
        ) {
            AmbientBackground(cover, preset = p)
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(10.dp).clip(CircleShape)
                        .glass(CircleShape, Hifi.Accent),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(p.label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) Hifi.Text else Hifi.TextDim)
    }
}
