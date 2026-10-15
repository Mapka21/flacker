package com.marco.audiopro.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.audiopro.audio.LevelMode
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import kotlinx.coroutines.delay

/**
 * "Volumen parejo": Apagado / Parejo / Fuerte, con lo que está midiendo y cuánto sube o baja en vivo.
 * Funciona aunque el ecualizador esté apagado.
 */
@Composable
fun LevelerCard(modifier: Modifier = Modifier) {
    val engine = LocalApp.current.engine
    val cfg by engine.config.collectAsState()
    val playing = LocalApp.current.player.state.collectAsState().value.isPlaying
    var readout by remember { mutableStateOf("") }
    LaunchedEffect(cfg.leveler, playing) {
        while (cfg.leveler != LevelMode.OFF) {
            val l = engine.eq.leveler
            readout = if (!playing || l.measuredLufs < -60) "Esperando música…"
            else "Midiendo %.0f LUFS · %+.1f dB".format(l.measuredLufs, l.currentGainDb)
            delay(500)
        }
    }
    Column(modifier.fillMaxWidth().glassCard().padding(16.dp)) {
        Text("Volumen parejo", style = Display.copy(fontSize = 18.sp))
        Text("Que todas las rolas se oigan al mismo nivel (y si quieres, fuerte).", fontSize = 12.sp, color = Hifi.TextDim)
        Spacer(Modifier.height(12.dp))
        SegmentedControl(LevelMode.entries.map { it.label }, cfg.leveler.ordinal, { engine.setLeveler(LevelMode.entries[it]) }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(cfg.leveler.blurb, fontSize = 12.sp, color = Hifi.TextFaint)
        if (cfg.leveler != LevelMode.OFF && readout.isNotEmpty()) Text(readout, style = Mono.copy(color = Hifi.Cream), modifier = Modifier.padding(top = 4.dp))
    }
}
