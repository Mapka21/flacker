package com.marco.audiopro.ui.screens

import com.marco.audiopro.ui.components.textInput
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.marco.audiopro.core.LocalApp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.audiopro.audio.AudioEngine
import com.marco.audiopro.audio.EqBand
import com.marco.audiopro.audio.EqConfig
import com.marco.audiopro.audio.EqMath
import com.marco.audiopro.audio.EqPresets
import com.marco.audiopro.audio.FilterType
import com.marco.audiopro.ui.components.GlassChip
import com.marco.audiopro.ui.components.SectionHeader
import com.marco.audiopro.ui.components.bouncyClick
import com.marco.audiopro.ui.components.glassCard
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import com.marco.audiopro.ui.viz.Analyzer
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow

private const val DB_RANGE = 15f

fun freqLabel(f: Float): String = if (f >= 1000) {
    val k = f / 1000f
    if (k >= 10 || k == k.toInt().toFloat()) "${k.toInt()}k" else "%.1fk".format(k)
} else "${f.toInt()}"

@Composable
fun EqScreen() {
    val engine = LocalApp.current.engine
    val cfg by engine.config.collectAsState()
    var selected by remember { mutableIntStateOf(0) }
    var saveDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("DSP", style = Mono.copy(color = Hifi.Accent, fontSize = 11.sp, letterSpacing = 1.sp))
                Text("Ecualizador", style = Display.copy(fontSize = 30.sp))
            }
            Switch(
                cfg.enabled, { on -> engine.update { it.copy(enabled = on) } },
                colors = SwitchDefaults.colors(checkedTrackColor = Hifi.Accent, checkedThumbColor = Color.Black),
            )
        }
        Spacer(Modifier.height(12.dp))
        com.marco.audiopro.ui.components.LevelerCard(Modifier.padding(horizontal = 12.dp))
        Spacer(Modifier.height(12.dp))
        EqGraph(cfg, engine, selected, onSelect = { selected = it }, Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(250.dp))

        // Selector de banda
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(cfg.bands.size) { i ->
                val b = cfg.bands[i]
                val sel = i == selected
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(if (sel) Hifi.Accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
                        .border(1.dp, if (sel) Hifi.Accent else Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .bouncyClick { selected = i }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("${i + 1} ${b.type.short}", style = Mono.copy(color = if (b.enabled) Hifi.Cream else Hifi.TextFaint, fontSize = 9.sp))
                    Text(freqLabel(b.freq), style = Mono.copy(color = Hifi.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    Text(if (b.type.hasGain) "%+.1f".format(b.gainDb) else "Q%.1f".format(b.q), style = Mono.copy(color = Hifi.TextDim, fontSize = 9.sp))
                }
            }
        }

        BandEditor(cfg.bands.getOrNull(selected) ?: return@Column, onChange = { f -> engine.updateBand(selected, f) })

        SectionHeader("Ganancia")
        PreampEditor(cfg, engine)

        SectionHeader("Presets") {
            TextButton(onClick = { saveDialog = true }) { Text("Guardar", color = Hifi.Accent) }
        }
        PresetRow(cfg, engine)

        SectionHeader("Comparar")
        AbButton(cfg, engine)
        Spacer(Modifier.height(28.dp))
    }

    if (saveDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saveDialog = false },
            title = { Text("Guardar preset") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nombre") }, modifier = Modifier.textInput()) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { engine.savePreset(name.trim()); saveDialog = false }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { saveDialog = false }) { Text("Cancelar") } },
            containerColor = Hifi.Panel,
        )
    }
}

// ------------------------------------------------------------------ Gráfica interactiva

@Composable
private fun EqGraph(cfg: EqConfig, engine: AudioEngine, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val c = LocalApp.current
    val analyzer = remember { Analyzer(c.engine.tap) }
    val playing by rememberUpdatedState(c.player.state.collectAsState().value.isPlaying)
    val offset by rememberUpdatedState(c.settings.prefs.collectAsState().value.visualOffsetMs)
    val tick = remember { mutableLongStateOf(0L) }
    val spectrum = remember { FloatArray(96) }
    val smooth = remember { FloatArray(96) { -120f } }
    var lastClip by remember { mutableLongStateOf(0L) }
    var clipSeenAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { t ->
            analyzer.capture(offset, playing)
            analyzer.computeSpectrum()
            analyzer.logBands(spectrum, 20f, 20000f)
            for (i in spectrum.indices) smooth[i] = if (spectrum[i] > smooth[i]) spectrum[i] else smooth[i] * 0.9f + spectrum[i] * 0.1f
            val clips = engine.eq.clipCount
            if (clips != lastClip) { lastClip = clips; clipSeenAt = t }
            tick.longValue = t
        }
    }
    val response = remember(cfg.bands) { EqMath.response(cfg.bands, 200) }
    val currentCfg by rememberUpdatedState(cfg)
    val tm = rememberTextMeasurer()
    val label = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = Hifi.TextFaint)
    val curve = remember { Path() }
    val fill = remember { Path() }

    Canvas(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
            .pointerInput(Unit) {
                detectTapGestures { onSelect(nearestBand(currentCfg.bands, it, size.width.toFloat(), size.height.toFloat())) }
            }
            .pointerInput(Unit) {
                var idx = 0
                detectDragGestures(
                    onDragStart = { p ->
                        idx = nearestBand(currentCfg.bands, p, size.width.toFloat(), size.height.toFloat())
                        onSelect(idx)
                    },
                    onDrag = { change, _ ->
                        val p = change.position
                        val f = EqMath.freqAt((p.x / size.width).coerceIn(0f, 1f))
                        val g = ((1 - p.y / (size.height / 2f)) * DB_RANGE).coerceIn(-DB_RANGE, DB_RANGE)
                        engine.updateBand(idx) { b ->
                            b.copy(freq = f.roundFreq(), gainDb = if (b.type.hasGain) (g * 10).toInt() / 10f else b.gainDb)
                        }
                    },
                )
            },
    ) {
        tick.longValue
        val w = size.width; val h = size.height
        val mid = h / 2
        // Rejilla
        for (f in listOf(20f, 50f, 100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f, 20000f)) {
            val x = EqMath.xOf(f) * w
            drawLine(Hifi.Line.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, h), 1f)
            if (f > 20f && f < 20000f) drawText(tm.measure(freqLabel(f), label), topLeft = Offset(x + 3, h - 14.dp.toPx()))
        }
        for (db in listOf(-12, -6, 0, 6, 12)) {
            val y = mid - db / DB_RANGE * mid
            drawLine(if (db == 0) Hifi.TextFaint else Hifi.Line.copy(alpha = 0.6f), Offset(0f, y), Offset(w, y), 1f)
            drawText(tm.measure(if (db > 0) "+$db" else "$db", label), topLeft = Offset(4f, y - 12.dp.toPx()))
        }
        // Espectro en vivo (post-EQ) de fondo
        if (analyzer.hasData) {
            val sp = Path()
            sp.moveTo(0f, h)
            for (i in smooth.indices) {
                val x = w * i / (smooth.size - 1)
                val v = ((smooth[i] + 80f) / 80f).coerceIn(0f, 1f)
                sp.lineTo(x, h - v * h * 0.9f)
            }
            sp.lineTo(w, h); sp.close()
            drawPath(sp, Brush.verticalGradient(listOf(Hifi.Cyan.copy(alpha = 0.22f), Hifi.Cyan.copy(alpha = 0.03f))))
        }
        // Curva de respuesta
        val active = cfg.enabled
        val curveColor = if (active) Hifi.Accent else Hifi.TextFaint
        curve.reset(); fill.reset()
        for (i in response.indices) {
            val x = w * i / (response.size - 1)
            val y = (mid - response[i].coerceIn(-DB_RANGE * 1.2f, DB_RANGE * 1.2f) / DB_RANGE * mid)
            if (i == 0) { curve.moveTo(x, y); fill.moveTo(x, mid); fill.lineTo(x, y) } else { curve.lineTo(x, y); fill.lineTo(x, y) }
        }
        fill.lineTo(w, mid); fill.close()
        drawPath(fill, curveColor.copy(alpha = 0.12f))
        drawPath(curve, curveColor, style = Stroke(2.5.dp.toPx()))
        // Nodos de banda
        cfg.bands.forEachIndexed { i, b ->
            val x = EqMath.xOf(b.freq) * w
            val y = if (b.type.hasGain) mid - b.gainDb / DB_RANGE * mid else mid
            val sel = i == selected
            val col = if (!b.enabled) Hifi.TextFaint else if (sel) Hifi.Cream else Hifi.Accent
            if (sel) drawCircle(col.copy(alpha = 0.18f), 20.dp.toPx(), Offset(x, y))
            drawCircle(Color.Black, 10.dp.toPx(), Offset(x, y))
            drawCircle(col, 10.dp.toPx(), Offset(x, y), style = Stroke(2.dp.toPx()))
            val t = tm.measure("${i + 1}", TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = col, fontWeight = FontWeight.Bold))
            drawText(t, topLeft = Offset(x - t.size.width / 2f, y - t.size.height / 2f))
        }
        // LED de clip
        val clipOn = tick.longValue - clipSeenAt < 600_000_000L && clipSeenAt != 0L
        val ledPos = Offset(w - 46.dp.toPx(), 12.dp.toPx())
        drawCircle(if (clipOn) Hifi.LedRed else Color(0xFF3A1512), 4.dp.toPx(), ledPos)
        drawText(tm.measure("CLIP", label.copy(color = if (clipOn) Hifi.LedRed else Hifi.TextFaint)), topLeft = ledPos + Offset(8.dp.toPx(), -6.dp.toPx()))
    }
}

private fun nearestBand(bands: List<EqBand>, p: Offset, w: Float, h: Float): Int =
    bands.indices.minByOrNull { i ->
        val b = bands[i]
        val y = if (b.type.hasGain) h / 2f * (1 - b.gainDb / DB_RANGE) else h / 2f
        hypot(EqMath.xOf(b.freq) * w - p.x, y - p.y)
    } ?: 0

private fun Float.roundFreq(): Float = when {
    this < 100f -> Math.round(this).toFloat()
    this < 1000f -> Math.round(this / 5f) * 5f
    else -> Math.round(this / 50f) * 50f
}

// ------------------------------------------------------------------ Editor de banda

@Composable
private fun BandEditor(b: EqBand, onChange: ((EqBand) -> EqBand) -> Unit) {
    Column(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().glassCard().padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(FilterType.entries) { t ->
                    GlassChip(t.label, b.type == t) { onChange { it.copy(type = t) } }
                }
            }
            Switch(
                b.enabled, { on -> onChange { it.copy(enabled = on) } },
                colors = SwitchDefaults.colors(checkedTrackColor = Hifi.Accent, checkedThumbColor = Color.Black),
            )
        }
        // Frecuencia en escala log
        LabeledSlider("FREQ", "${freqLabel(b.freq)} Hz", EqMath.xOf(b.freq), 0f..1f) { x ->
            onChange { it.copy(freq = EqMath.freqAt(x).roundFreq()) }
        }
        if (b.type.hasGain) {
            LabeledSlider("GAIN", "%+.1f dB".format(b.gainDb), b.gainDb, -DB_RANGE..DB_RANGE) { g ->
                onChange { it.copy(gainDb = (g * 10).toInt() / 10f) }
            }
        }
        // Q en escala log 0.1 .. 10
        val qx = (ln(b.q / 0.1f) / ln(100f)).coerceIn(0f, 1f)
        LabeledSlider("Q", "%.2f".format(b.q), qx, 0f..1f) { x ->
            onChange { it.copy(q = (0.1f * 100f.pow(x) * 100).toInt() / 100f) }
        }
    }
}

@Composable
private fun LabeledSlider(name: String, value: String, v: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = Mono.copy(color = Hifi.TextDim), modifier = Modifier.width(44.dp))
        Slider(
            v, onChange, valueRange = range, modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = Hifi.Cream, activeTrackColor = Hifi.Accent, inactiveTrackColor = Hifi.Line),
        )
        Text(value, style = Mono.copy(color = Hifi.Cream), modifier = Modifier.width(82.dp).padding(start = 8.dp))
    }
}

// ------------------------------------------------------------------ Preamp / dither

@Composable
private fun PreampEditor(cfg: EqConfig, engine: AudioEngine) {
    Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth().glassCard().padding(14.dp)) {
        SwitchRow("Pre-amp automático (anti-clipping)", cfg.autoPreamp) { on -> engine.update { it.copy(autoPreamp = on) } }
        if (!cfg.autoPreamp) {
            LabeledSlider("PRE", "%+.1f dB".format(cfg.preampDb), cfg.preampDb, -20f..6f) { v ->
                engine.update { it.copy(preampDb = (v * 10).toInt() / 10f) }
            }
        } else {
            Text("Pre-amp efectivo: %+.1f dB".format(cfg.effectivePreampDb()), style = Mono.copy(color = Hifi.Cream), modifier = Modifier.padding(vertical = 6.dp))
        }
        SwitchRow("Dither TPDF al re-cuantizar a 16 bits", cfg.dither) { on -> engine.update { it.copy(dither = on) } }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Hifi.Accent, checkedThumbColor = Color.Black))
    }
}

// ------------------------------------------------------------------ Presets

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetRow(cfg: EqConfig, engine: AudioEngine) {
    val user by engine.userPresets.collectAsState()
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    val genre = com.marco.audiopro.core.LocalApp.current.player.state.collectAsState().value.current?.genre
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Automático: cada canción se oye con el preset de su género.
        item(key = "auto") {
            val on = cfg.autoGenre
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (on) Color.White else Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = if (on) 0f else 0.25f), RoundedCornerShape(50))
                    .clickable { engine.setAutoGenre(!on, genre) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("✦ Auto por género", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) Color.Black else Hifi.Text)
            }
        }
        items(EqPresets.builtIn + user, key = { it.name }) { p ->
            val sel = cfg.presetName == p.name && !cfg.autoGenre
            val isUser = user.any { it.name == p.name }
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (sel) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = if (sel) 0f else 0.10f), RoundedCornerShape(50))
                    .combinedClickable(onClick = { engine.applyPreset(p) }, onLongClick = { if (isUser) confirmDelete = p.name })
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text((if (isUser) "★ " else "") + p.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (sel) Color.Black else Hifi.TextDim)
            }
        }
    }
    Text(
        if (cfg.autoGenre) "Auto: \"${cfg.presetName}\"${genre?.let { " para $it" } ?: ""}. Cambia solo con cada canción; elige un preset para fijarlo."
        else "Mantén presionado un preset tuyo (★) para borrarlo.",
        fontSize = 11.sp, color = Hifi.TextFaint, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
    confirmDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("¿Borrar \"$name\"?") },
            confirmButton = { TextButton(onClick = { engine.deletePreset(name); confirmDelete = null }) { Text("Borrar", color = Hifi.LedRed) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } },
            containerColor = Hifi.Panel,
        )
    }
}

/** Mantener presionado = escuchar sin EQ (bypass momentáneo, no se guarda). */
@Composable
private fun AbButton(cfg: EqConfig, engine: AudioEngine) {
    var pressed by remember { mutableStateOf(false) }
    val currentCfg by rememberUpdatedState(cfg)
    Box(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (pressed) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.22f))
            .border(1.dp, if (pressed) Hifi.Cream else Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    engine.eq.setConfig(currentCfg.copy(enabled = false))
                    try { tryAwaitRelease() } finally {
                        engine.eq.setConfig(currentCfg)
                        pressed = false
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (pressed) Hifi.TextFaint else if (cfg.enabled) Hifi.LedGreen else Hifi.TextFaint))
            Spacer(Modifier.width(10.dp))
            Text(
                if (pressed) "B · SIN EQ (suelta para volver)" else "A · CON EQ — mantén presionado para escuchar sin EQ",
                style = Mono.copy(color = if (pressed) Hifi.Cream else Hifi.TextDim, fontSize = 11.sp),
            )
        }
    }
}
