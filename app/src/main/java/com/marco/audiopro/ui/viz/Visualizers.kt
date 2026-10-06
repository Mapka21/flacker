package com.marco.audiopro.ui.viz

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.audiopro.container
import com.marco.audiopro.ui.theme.Hifi
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class VizMode(val label: String) {
    SPECTRUM("ESPECTRO"),
    SPECTROGRAM("ESPECTROGRAMA"),
    VU("VU"),
    GONIO("GONIÓMETRO"),
    SCOPE("OSCILOSCOPIO"),
}

private val monoSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = Hifi.TextFaint)

/** Visualizador en tiempo real alimentado por el AnalyzerTap (audio post-EQ). */
@Composable
fun Visualizer(mode: VizMode, modifier: Modifier = Modifier, accent: Color = Hifi.Amber) {
    val ctx = LocalContext.current
    val c = ctx.container
    val analyzer = remember { Analyzer(c.engine.tap) }
    val playing by rememberUpdatedState(c.player.state.collectAsState().value.isPlaying)
    val offset by rememberUpdatedState(c.settings.prefs.collectAsState().value.visualOffsetMs)
    val tick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { t ->
            analyzer.capture(offset, playing)
            tick.longValue = t
        }
    }
    val tm = rememberTextMeasurer()
    when (mode) {
        VizMode.SPECTRUM -> Spectrum(analyzer, tick, tm, accent, modifier)
        VizMode.SPECTROGRAM -> Spectrogram(analyzer, tick, tm, modifier)
        VizMode.VU -> VuPair(analyzer, tick, tm, modifier)
        VizMode.GONIO -> Goniometer(analyzer, tick, tm, accent, modifier)
        VizMode.SCOPE -> Scope(analyzer, tick, tm, accent, modifier)
    }
}

private class Clock {
    private var last = 0L
    fun dt(t: Long): Float {
        val d = if (last == 0L) 0.016f else ((t - last) / 1e9f).coerceIn(0f, 0.1f)
        last = t
        return d
    }
}

// ---------------------------------------------------------------- Espectro (barras LED)

private class SpecState(n: Int) {
    val raw = FloatArray(n)
    val level = FloatArray(n)
    val peak = FloatArray(n)
    val hold = FloatArray(n)
    val clock = Clock()
}

@Composable
private fun Spectrum(a: Analyzer, tick: State<Long>, tm: TextMeasurer, accent: Color, modifier: Modifier) {
    val bars = 48
    val st = remember { SpecState(bars) }
    val floorDb = -78f
    Canvas(modifier) {
        val dt = st.clock.dt(tick.value)
        val labelH = 14.dp.toPx()
        val h = size.height - labelH
        // Rejilla dB
        for (db in intArrayOf(-12, -24, -36, -48, -60, -72)) {
            val y = h * (db / floorDb)
            drawLine(Hifi.Line.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 1f)
        }
        a.computeSpectrum()
        a.logBands(st.raw)
        val segH = 3.dp.toPx(); val segGap = 1.5.dp.toPx()
        val segs = (h / (segH + segGap)).toInt()
        val w = size.width / bars
        val gap = max(1.5f, w * 0.2f)
        for (i in 0 until bars) {
            val target = ((st.raw[i] - floorDb) / -floorDb).coerceIn(0f, 1f)
            st.level[i] = if (target > st.level[i]) target else max(target, st.level[i] - dt * 1.4f)
            if (st.level[i] >= st.peak[i]) { st.peak[i] = st.level[i]; st.hold[i] = 0.6f }
            else if (st.hold[i] > 0) st.hold[i] -= dt
            else st.peak[i] = max(0f, st.peak[i] - dt * 0.5f)

            val x = i * w + gap / 2
            val lit = (st.level[i] * segs).toInt()
            for (s in 0 until lit) {
                val frac = s / segs.toFloat()
                val col = when {
                    frac > 0.88f -> Hifi.LedRed
                    frac > 0.7f -> Color(0xFFFFD34D)
                    else -> accent
                }
                val y = h - (s + 1) * (segH + segGap)
                drawRect(col.copy(alpha = 0.55f + 0.45f * frac), Offset(x, y), Size(w - gap, segH))
            }
            // Segmentos apagados, muy tenues (look de display VFD)
            for (s in lit until segs step 2) {
                val y = h - (s + 1) * (segH + segGap)
                drawRect(accent.copy(alpha = 0.05f), Offset(x, y), Size(w - gap, segH))
            }
            val py = h - st.peak[i] * h
            drawRect(Hifi.Cream, Offset(x, py - 2f), Size(w - gap, 2.dp.toPx()))
        }
        // Etiquetas de frecuencia
        val top = minOf(20000f, a.sampleRate / 2f)
        for ((f, l) in listOf(50f to "50", 100f to "100", 250f to "250", 500f to "500", 1000f to "1k", 2500f to "2.5k", 5000f to "5k", 10000f to "10k", 20000f to "20k")) {
            if (f > top) continue
            val x = size.width * (log10(f / 25f) / log10(top / 25f))
            drawLabel(tm, l, Offset(x, h + 3.dp.toPx()), center = true)
        }
    }
}

// ---------------------------------------------------------------- Espectrograma (cascada)

private class GramState {
    val w = 240; val h = 128
    val pixels = IntArray(w * h)
    val bmp: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val img = bmp.asImageBitmap()
    val col = FloatArray(h)
    val lut = IntArray(256) { inferno(it / 255f) }
}

/** Mapa de color tipo "inferno": negro → púrpura → rojo → ámbar → crema. */
private fun inferno(t: Float): Int {
    val stops = arrayOf(
        0f to Color(0xFF000004), 0.25f to Color(0xFF3B0F70), 0.5f to Color(0xFF8C2981),
        0.7f to Color(0xFFDE4968), 0.85f to Color(0xFFFE9F6D), 1f to Color(0xFFFCFDBF),
    )
    for (i in 1 until stops.size) {
        val (t1, c1) = stops[i]
        val (t0, c0) = stops[i - 1]
        if (t <= t1) {
            val k = (t - t0) / (t1 - t0)
            return Color(
                c0.red + (c1.red - c0.red) * k,
                c0.green + (c1.green - c0.green) * k,
                c0.blue + (c1.blue - c0.blue) * k,
            ).toArgb()
        }
    }
    return stops.last().second.toArgb()
}

@Composable
private fun Spectrogram(a: Analyzer, tick: State<Long>, tm: TextMeasurer, modifier: Modifier) {
    val st = remember { GramState() }
    Canvas(modifier) {
        tick.value
        a.computeSpectrum()
        a.logBands(st.col, 30f, 20000f)
        val w = st.w; val h = st.h
        // Desplaza una columna a la izquierda y pinta la nueva a la derecha (graves abajo).
        for (y in 0 until h) {
            val row = y * w
            System.arraycopy(st.pixels, row + 1, st.pixels, row, w - 1)
            val db = st.col[h - 1 - y]
            val v = ((db + 90f) / 90f).coerceIn(0f, 1f)
            st.pixels[row + w - 1] = st.lut[(v * 255).toInt()]
        }
        st.bmp.setPixels(st.pixels, 0, w, 0, 0, w, h)
        drawImage(
            st.img, srcOffset = IntOffset.Zero, srcSize = IntSize(w, h),
            dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.Low,
        )
        val top = minOf(20000f, a.sampleRate / 2f)
        for ((f, l) in listOf(100f to "100", 1000f to "1k", 10000f to "10k")) {
            if (f > top) continue
            val y = size.height * (1 - log10(f / 30f) / log10(20000f / 30f))
            drawLine(Color.White.copy(alpha = 0.15f), Offset(0f, y), Offset(size.width, y), 1f)
            drawLabel(tm, l, Offset(4.dp.toPx(), y - 11.dp.toPx()), color = Color.White.copy(alpha = 0.5f))
        }
    }
}

// ---------------------------------------------------------------- VU analógicos

private class VuState {
    val pos = FloatArray(2)
    val ledHold = FloatArray(2)
    val clock = Clock()
}

private val vuMarks = listOf(-20, -10, -7, -5, -3, -2, -1, 0, 1, 2, 3)

/** Escala VU auténtica: desviación proporcional a la tensión, +3 VU a fondo de escala. */
private fun vuPos(vu: Float): Float = (10f.pow(vu / 20f) / 10f.pow(3f / 20f)).coerceIn(0f, 1.08f)

@Composable
private fun VuPair(a: Analyzer, tick: State<Long>, tm: TextMeasurer, modifier: Modifier) {
    val st = remember { VuState() }
    Canvas(modifier) {
        val dt = st.clock.dt(tick.value)
        val frames = a.framesFor(60)
        val gap = 10.dp.toPx()
        val mw = (size.width - gap) / 2
        val mh = min(size.height, mw * 0.78f)
        val top = (size.height - mh) / 2
        for (ch in 0..1) {
            val data = if (ch == 0) a.left else a.right
            // 0 VU = -18 dBFS (alineación típica de estudio digital)
            val vu = a.rmsDb(data, frames) + 18f
            val target = if (a.hasData) vuPos(vu) else 0f
            // Balística VU: ~300 ms para llegar al 99 %.
            st.pos[ch] += (target - st.pos[ch]) * (1 - exp(-dt / 0.065f))
            if (a.peak(data, frames) > 0.89f) st.ledHold[ch] = 0.4f else st.ledHold[ch] -= dt
            drawVuMeter(Offset(ch * (mw + gap), top), Size(mw, mh), st.pos[ch], st.ledHold[ch] > 0, if (ch == 0) "L" else "R", tm)
        }
    }
}

private fun DrawScope.drawVuMeter(o: Offset, s: Size, pos: Float, led: Boolean, ch: String, tm: TextMeasurer) {
    val r = CornerRadius(10.dp.toPx())
    // Carcasa
    drawRoundRect(Color(0xFF050505), o, s, r)
    val inset = 5.dp.toPx()
    val fo = o + Offset(inset, inset)
    val fs = Size(s.width - 2 * inset, s.height - 2 * inset)
    // Cara retroiluminada ámbar
    drawRoundRect(
        Brush.radialGradient(
            listOf(Color(0xFFFFE3A3), Color(0xFFF7C46A), Color(0xFFC98A2E)),
            center = fo + Offset(fs.width / 2, fs.height * 0.9f), radius = fs.width * 0.85f,
        ),
        fo, fs, CornerRadius(6.dp.toPx()),
    )
    val pivot = fo + Offset(fs.width / 2, fs.height * 1.18f)
    val radius = fs.height * 0.98f
    val sweep = 50f
    fun angleOf(p: Float) = -sweep + 2 * sweep * p
    fun pointAt(deg: Float, rad: Float): Offset {
        val a = (deg - 90f) * PI.toFloat() / 180f
        return pivot + Offset(cos(a) * rad, sin(a) * rad)
    }
    clipRect(fo.x, fo.y, fo.x + fs.width, fo.y + fs.height) {
        // Arco de escala y zona roja
        val arcR = radius * 0.86f
        val p0 = vuPos(0f)
        var prev = pointAt(angleOf(0f), arcR)
        val steps = 40
        for (i in 1..steps) {
            val p = i / steps.toFloat()
            val pt = pointAt(angleOf(p), arcR)
            drawLine(if (p > p0) Color(0xFFB3261E) else Color(0xFF1A1208), prev, pt, if (p > p0) 3.dp.toPx() else 1.2.dp.toPx())
            prev = pt
        }
        for (m in vuMarks) {
            val ang = angleOf(vuPos(m.toFloat()))
            val red = m > 0
            val col = if (red) Color(0xFFB3261E) else Color(0xFF1A1208)
            drawLine(col, pointAt(ang, arcR), pointAt(ang, arcR + 7.dp.toPx()), 1.5.dp.toPx())
            val lbl = if (m > 0) "+$m" else "$m"
            val lp = pointAt(ang, arcR + 15.dp.toPx())
            val layout = tm.measure(lbl, TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = col, fontWeight = FontWeight.Bold))
            drawText(layout, topLeft = lp - Offset(layout.size.width / 2f, layout.size.height / 2f))
        }
        val vuLayout = tm.measure("VU", TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1208)))
        drawText(vuLayout, topLeft = fo + Offset(fs.width / 2 - vuLayout.size.width / 2f, fs.height * 0.52f))
        val chLayout = tm.measure(ch, TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color(0xFF3A2A10)))
        drawText(chLayout, topLeft = fo + Offset(8.dp.toPx(), fs.height - chLayout.size.height - 6.dp.toPx()))
        // LED de pico
        val ledC = fo + Offset(fs.width - 12.dp.toPx(), fs.height - 12.dp.toPx())
        drawCircle(if (led) Hifi.LedRed else Color(0xFF4A1A14), 4.dp.toPx(), ledC)
        if (led) drawCircle(Hifi.LedRed.copy(alpha = 0.35f), 9.dp.toPx(), ledC)
        // Aguja
        val ang = angleOf(pos.coerceIn(-0.02f, 1.06f))
        drawLine(Color.Black.copy(alpha = 0.25f), pivot + Offset(3f, 3f), pointAt(ang, radius * 0.97f) + Offset(3f, 3f), 2.dp.toPx())
        drawLine(Color(0xFF111111), pivot, pointAt(ang, radius * 0.97f), 1.6.dp.toPx(), StrokeCap.Round)
        // Reflejo del vidrio
        drawRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), fo.y, fo.y + fs.height * 0.45f),
            fo, Size(fs.width, fs.height * 0.45f),
        )
    }
}

// ---------------------------------------------------------------- Goniómetro (fase estéreo)

private class GonioState {
    val pts = FloatArray(2048 * 2)
    val paint = Paint().apply { isAntiAlias = true; strokeWidth = 3f; strokeCap = Paint.Cap.ROUND }
    var gain = 1f
    var corr = 0f
    val clock = Clock()
}

@Composable
private fun Goniometer(a: Analyzer, tick: State<Long>, tm: TextMeasurer, accent: Color, modifier: Modifier) {
    val st = remember { GonioState() }
    Canvas(modifier) {
        val dt = st.clock.dt(tick.value)
        val barH = 26.dp.toPx()
        val side = min(size.width, size.height - barH)
        val c = Offset(size.width / 2, side / 2)
        val rad = side / 2 * 0.92f
        // Guías: diamante + ejes L, R, M, S
        val guide = Hifi.Line
        drawLine(guide, c + Offset(-rad, 0f), c + Offset(rad, 0f), 1f)
        drawLine(guide, c + Offset(0f, -rad), c + Offset(0f, rad), 1f)
        val d = rad * 0.7071f
        drawLine(guide, c + Offset(-d, -d), c + Offset(d, d), 1f)
        drawLine(guide, c + Offset(d, -d), c + Offset(-d, d), 1f)
        drawCircle(guide, rad, c, style = Stroke(1f))
        drawLabel(tm, "M", c + Offset(0f, -rad - 2.dp.toPx()), center = true, above = true)
        drawLabel(tm, "L", c + Offset(-d - 8.dp.toPx(), -d - 8.dp.toPx()), center = true)
        drawLabel(tm, "R", c + Offset(d + 8.dp.toPx(), -d - 8.dp.toPx()), center = true)
        drawLabel(tm, "+S", c + Offset(-rad + 10.dp.toPx(), 2.dp.toPx()), center = true)
        drawLabel(tm, "−S", c + Offset(rad - 10.dp.toPx(), 2.dp.toPx()), center = true)

        val count = 2048
        val n = a.n
        var pk = 0.02f
        for (i in n - count until n) pk = max(pk, max(abs(a.left[i]), abs(a.right[i])))
        // Auto-ganancia suave para que la figura llene el círculo.
        val tg = 1f / pk
        st.gain += (tg - st.gain) * (if (tg < st.gain) 0.5f else 0.04f)
        val k = rad * 0.7071f * st.gain * 0.9f
        for (j in 0 until count) {
            val l = a.left[n - count + j]; val r = a.right[n - count + j]
            st.pts[j * 2] = c.x + (r - l) * k
            st.pts[j * 2 + 1] = c.y - (l + r) * k
        }
        st.paint.color = accent.copy(alpha = 0.55f).toArgb()
        drawIntoCanvas { it.nativeCanvas.drawPoints(st.pts, 0, count * 2, st.paint) }

        // Medidor de correlación
        val corr = if (a.hasData) a.correlation(a.framesFor(120)) else 0f
        st.corr += (corr - st.corr) * (1 - exp(-dt / 0.15f))
        val by = side + 8.dp.toPx()
        val bw = size.width * 0.8f
        val bx = (size.width - bw) / 2
        drawRoundRect(Hifi.Panel, Offset(bx, by), Size(bw, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
        drawLine(Hifi.TextFaint, Offset(bx + bw / 2, by - 2f), Offset(bx + bw / 2, by + 6.dp.toPx() + 2f), 1f)
        val mx = bx + bw * (st.corr + 1) / 2
        val col = when { st.corr < 0f -> Hifi.LedRed; st.corr < 0.3f -> Hifi.Amber; else -> Hifi.LedGreen }
        drawCircle(col, 5.dp.toPx(), Offset(mx, by + 3.dp.toPx()))
        drawLabel(tm, "-1", Offset(bx - 12.dp.toPx(), by - 2.dp.toPx()))
        drawLabel(tm, "+1", Offset(bx + bw + 4.dp.toPx(), by - 2.dp.toPx()))
        drawLabel(tm, "CORR %+.2f".format(st.corr), Offset(size.width / 2, by + 9.dp.toPx()), center = true, color = col)
    }
}

// ---------------------------------------------------------------- Osciloscopio

private class ScopeState {
    val pl = Path(); val pr = Path()
}

@Composable
private fun Scope(a: Analyzer, tick: State<Long>, tm: TextMeasurer, accent: Color, modifier: Modifier) {
    val st = remember { ScopeState() }
    Canvas(modifier) {
        tick.value
        val n = a.n
        val win = min(n / 2, a.framesFor(25))
        // Disparo en cruce por cero ascendente para que la onda se quede quieta.
        var start = n - win
        for (i in n - 2 * win until n - win) {
            val m0 = a.left[i] + a.right[i]; val m1 = a.left[i + 1] + a.right[i + 1]
            if (m0 <= 0f && m1 > 0f) { start = i; break }
        }
        val midL = size.height * 0.5f
        for (gy in 1..3) drawLine(Hifi.Line.copy(alpha = 0.5f), Offset(0f, size.height * gy / 4), Offset(size.width, size.height * gy / 4), 1f)
        for (gx in 1..9) drawLine(Hifi.Line.copy(alpha = 0.3f), Offset(size.width * gx / 10, 0f), Offset(size.width * gx / 10, size.height), 1f)
        val amp = size.height * 0.45f
        st.pl.reset(); st.pr.reset()
        for (j in 0 until win) {
            val x = size.width * j / (win - 1)
            val yl = midL - a.left[start + j] * amp
            val yr = midL - a.right[start + j] * amp
            if (j == 0) { st.pl.moveTo(x, yl); st.pr.moveTo(x, yr) } else { st.pl.lineTo(x, yl); st.pr.lineTo(x, yr) }
        }
        drawPath(st.pr, Hifi.Cyan.copy(alpha = 0.7f), style = Stroke(1.4.dp.toPx()))
        drawPath(st.pl, accent, style = Stroke(1.4.dp.toPx()))
        drawLabel(tm, "L", Offset(6.dp.toPx(), 4.dp.toPx()), color = accent)
        drawLabel(tm, "R", Offset(18.dp.toPx(), 4.dp.toPx()), color = Hifi.Cyan)
        drawLabel(tm, "${win * 1000 / a.sampleRate} ms", Offset(size.width - 40.dp.toPx(), 4.dp.toPx()))
    }
}

// ---------------------------------------------------------------- util

private fun DrawScope.drawLabel(
    tm: TextMeasurer, text: String, at: Offset, center: Boolean = false, above: Boolean = false,
    color: Color = Hifi.TextFaint,
) {
    val layout = tm.measure(text, monoSmall.copy(color = color))
    val x = if (center) at.x - layout.size.width / 2f else at.x
    val y = if (above) at.y - layout.size.height else at.y
    drawText(layout, topLeft = Offset(x, y))
}
