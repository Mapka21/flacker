package com.marco.audiopro.ui.viz

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.marco.audiopro.audio.SampleSource
import com.marco.audiopro.ui.theme.Hifi
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Los "modos visuales": el fondo de la app se vuelve el visualizador. */
enum class VisualPreset(val label: String, val blurb: String) {
    AURORA("Aurora", "Los colores de la portada respiran con el bajo"),
    PULSO("Pulso", "Ondas de choque en cada golpe"),
    HALO("Halo", "Un anillo líquido que baila con el espectro"),
    OLAS("Olas", "Una ola por rango de frecuencias"),
    HIPER("Hiperespacio", "Estrellas que aceleran con los agudos"),
    SYNTH("Synthwave", "Sol neón y rejilla retro al ritmo"),
    NEON("Neón 360", "Psicodelia con estela, como el del Xbox 360"),
}

/** Atardecer neón (carmesí, índigo, azul noche, ciruela): colores cuando no hay portada. */
val DuskPalette = listOf(Color(0xFF8E1B3A), Color(0xFF2A2370), Color(0xFF0F1B45), Color(0xFF5A1A5E))

private const val TAU = (2 * PI).toFloat()

/**
 * Rasgos de la música, ya suavizados, para animar los fondos: 24 bandas, graves/medios/agudos
 * normalizados (con control automático de ganancia) y detección de golpes en el bajo.
 */
class AudioFeatures {
    val bands = FloatArray(24)
    val wave = FloatArray(128)
    var bass = 0f; private set
    var mid = 0f; private set
    var treble = 0f; private set
    var level = 0f; private set
    /** 1 justo en el golpe y decae a 0. */
    var beat = 0f; private set
    var beats = 0; private set

    private val raw = FloatArray(24)
    private val peaks = floatArrayOf(0.3f, 0.3f, 0.3f)
    private var bassAvg = 0f
    private var sinceBeat = 1f

    fun update(a: Analyzer?, dt: Float) {
        val live = a != null && a.hasData
        if (live) {
            a!!.computeSpectrum()
            a.logBands(raw, 30f, 16000f)
            for (i in raw.indices) {
                // Compensa la caída natural de energía hacia los agudos (~1.2 dB por banda).
                val v = ((raw[i] + 72f + i * 1.3f) / 54f).coerceIn(0f, 1f)
                bands[i] += (v - bands[i]) * if (v > bands[i]) 0.6f else min(1f, dt * 6f)
            }
            val span = min(a.n, 1024)
            val step = span / wave.size
            for (j in wave.indices) {
                val i = a.n - span + j * step
                wave[j] = (a.left[i] + a.right[i]) * 0.5f
            }
        } else {
            val k = max(0f, 1f - dt * 3f)
            for (i in bands.indices) bands[i] *= k
            for (j in wave.indices) wave[j] *= k
        }
        val rawBass = avg(0, 5)
        bass = follow(bass, agc(0, rawBass, dt), dt)
        mid = follow(mid, agc(1, avg(5, 15), dt), dt)
        treble = follow(treble, agc(2, avg(15, 24), dt), dt)
        level = bass * 0.5f + mid * 0.35f + treble * 0.15f
        // Golpe: el bajo brinca por encima de su promedio reciente.
        bassAvg += (rawBass - bassAvg) * min(1f, dt * 2f)
        sinceBeat += dt
        if (live && rawBass > bassAvg * 1.10f + 0.02f && rawBass > 0.18f && sinceBeat > 0.22f) {
            beat = 1f; beats++; sinceBeat = 0f
        }
        beat = max(0f, beat - dt * 2.4f)
    }

    fun band(u: Float): Float {
        val p = u.coerceIn(0f, 1f) * (bands.size - 1)
        val i = p.toInt(); val t = p - i
        return bands[i] * (1 - t) + bands[min(i + 1, bands.size - 1)] * t
    }

    private fun avg(from: Int, to: Int): Float {
        var s = 0f
        for (i in from until to) s += bands[i]
        return s / (to - from)
    }

    /** Ganancia automática: una rola bajita también mueve el fondo. */
    private fun agc(i: Int, v: Float, dt: Float): Float {
        peaks[i] = max(v, peaks[i] - dt * 0.08f).coerceAtLeast(0.2f)
        return (v / peaks[i]).coerceIn(0f, 1f).pow(1.3f)
    }

    private fun follow(cur: Float, target: Float, dt: Float) =
        cur + (target - cur) * if (target > cur) min(1f, dt * 25f) else min(1f, dt * 6f)
}

/** Un solo análisis por frame, compartido por el fondo, las vistas previas y el iPod. */
object LiveAudio {
    val features = AudioFeatures()
    /** Rasgos en cero: para el fondo cuando el modo visual está apagado. */
    val quiet = AudioFeatures()
    private var analyzer: Analyzer? = null
    private var source: SampleSource? = null
    private var lastFrame = 0L

    fun update(tap: SampleSource, frameNanos: Long, playing: Boolean, offsetMs: Int) {
        if (frameNanos == lastFrame) return
        val dt = if (lastFrame == 0L) 0.016f else ((frameNanos - lastFrame) / 1e9f).coerceIn(0f, 0.1f)
        lastFrame = frameNanos
        val a = analyzer?.takeIf { source === tap } ?: Analyzer(tap, 2048).also { analyzer = it; source = tap }
        a.capture(offsetMs, playing)
        features.update(a, dt)
    }
}

/** Saturado y brillante; las portadas casi grises usan el color de respaldo. */
internal fun Color.vivid(fallback: Color): Color {
    val mx = max(red, max(green, blue)); val mn = min(red, min(green, blue))
    val sat = if (mx <= 0f) 0f else (mx - mn) / mx
    val base = if (sat < 0.18f) fallback else this
    val m = max(base.red, max(base.green, base.blue)).coerceAtLeast(0.01f)
    return Color(base.red / m, base.green / m, base.blue / m)
}

/**
 * Estado y dibujo de los modos visuales. [step] avanza la animación (una vez por frame) y
 * [draw] pinta el preset; todo sin asignar memoria por frame.
 */
class VisualScene {
    private var time = 0f
    private var phase = 0f
    private var rot = 0f
    private var grid = 0f
    private var lastBeats = 0
    private var sinceRing = 0f

    private class Ring(var r: Float, var a: Float, val color: Int, val strength: Float)
    private val rings = ArrayList<Ring>()

    private class Star { var x = 0f; var y = 0f; var z = 1f }
    private val rnd = Random(7)
    private val stars = Array(220) { Star().also { s -> respawn(s); s.z = rnd.nextFloat() * 0.95f + 0.05f } }
    private val sky = FloatArray(120) { rnd.nextFloat() }

    private val path = Path()
    private val xs = FloatArray(120)
    private val ys = FloatArray(120)
    private val neon = NeonFeedback()
    private var dtLast = 0.016f

    private fun respawn(s: Star) {
        s.x = rnd.nextFloat() * 2 - 1; s.y = rnd.nextFloat() * 2 - 1; s.z = 1f
    }

    fun step(dt: Float, f: AudioFeatures) {
        dtLast = dt
        time += dt
        phase += dt * (TAU / 28f) * (1f + 5f * f.level + 4f * f.beat)
        rot += dt * (0.15f + 1.4f * f.mid + 1.5f * f.beat)
        grid = (grid + dt * (0.10f + 0.45f * f.level + 0.6f * f.beat)) % 1f

        sinceRing += dt
        val hit = f.beats != lastBeats
        lastBeats = f.beats
        if (hit || sinceRing > 2.8f) {
            if (rings.size < 12) rings.add(Ring(0.05f, 1f, f.beats % 4, if (hit) 1f else 0.45f))
            sinceRing = 0f
        }
        for (r in rings) { r.r += dt * (0.55f + 0.9f * f.level); r.a -= dt * 0.5f }
        rings.removeAll { it.a <= 0f }

        val speed = 0.06f + 0.5f * f.treble + 0.9f * f.beat + 0.25f * f.level
        for (s in stars) { s.z -= dt * speed; if (s.z <= 0.03f) respawn(s) }
    }

    /**
     * @param cols 4 tonos de la portada (animados).
     * @param intensity 1 = a todo color (reproductor); menos = más oscuro, para fondos con texto.
     */
    fun draw(scope: DrawScope, preset: VisualPreset, cols: List<Color>, f: AudioFeatures, intensity: Float) =
        // Estrellas y rejilla se salen del lienzo: se recorta para las vistas previas chicas.
        scope.clipRect { drawClipped(this, preset, cols, f, intensity) }

    private fun drawClipped(scope: DrawScope, preset: VisualPreset, cols: List<Color>, f: AudioFeatures, intensity: Float) = with(scope) {
        dpScale = density
        drawRect(Hifi.Bg)
        val v = List(4) { cols[it].vivid(DuskPalette[it]) }
        val punch = 1f + 0.06f * f.beat + 0.03f * f.bass
        drawContext.transform.scale(punch, punch, center)
        when (preset) {
            VisualPreset.AURORA -> blobs(cols, v, f, 1f)
            VisualPreset.PULSO -> { blobs(cols, v, f, 0.6f); pulse(v, f) }
            VisualPreset.HALO -> { blobs(cols, v, f, 0.5f); halo(v, f) }
            VisualPreset.OLAS -> { blobs(cols, v, f, 0.55f); waves(v, f) }
            VisualPreset.HIPER -> { blobs(cols, v, f, 0.45f); hyperspace(v, f) }
            VisualPreset.SYNTH -> synthwave(v, f)
            VisualPreset.NEON -> neon.frame(this, f, time, dtLast)
        }
        drawContext.transform.scale(1f / punch, 1f / punch, center)
        // Velo: más oscuro abajo, donde van los controles y el texto.
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.50f))))
        if (intensity < 1f) drawRect(Hifi.Bg.copy(alpha = ((1f - intensity) * 0.8f).coerceIn(0f, 1f)))
    }

    // ---------------------------------------------------------------- Aurora (base de casi todos)

    private fun DrawScope.blobs(cols: List<Color>, v: List<Color>, f: AudioFeatures, k: Float) {
        val w = size.width; val h = size.height
        val r = max(w, h) * 0.72f * (1f + 0.45f * f.bass)
        val alpha = (0.8f * k * (0.75f + 0.6f * f.level)).coerceAtMost(1f)
        val reach = 0.38f + 0.12f * f.mid
        for (i in 0..3) {
            val a = phase * BLOB_SPEED[i] + BLOB_OFFSET[i]
            // Cada mancha late con su propio rango: graves, medios y agudos.
            val band = when (i) { 0 -> f.bass; 1 -> f.mid; 2 -> f.treble; else -> f.level }
            val ri = r * (0.85f + 0.35f * band)
            val c = Offset(w * (0.5f + reach * cos(a)), h * (0.5f + reach * 0.9f * sin(a * 1.3f)))
            drawCircle(Brush.radialGradient(listOf(lerp(cols[i], v[i], 0.35f * band).copy(alpha = alpha), Color.Transparent), c, ri), ri, c)
        }
        if (f.beat > 0f) {
            val rr = max(w, h) * (0.5f + 0.3f * f.beat)
            drawCircle(Brush.radialGradient(listOf(v[0].copy(alpha = 0.35f * f.beat * k), Color.Transparent), center, rr), rr, center)
        }
    }

    // ---------------------------------------------------------------- Pulso

    private fun DrawScope.pulse(v: List<Color>, f: AudioFeatures) {
        val s = min(size.width, size.height)
        val core = s * (0.10f + 0.22f * f.bass + 0.1f * f.beat) * 2.2f
        drawCircle(Brush.radialGradient(listOf(v[1].copy(alpha = 0.55f), Color.Transparent), center, core), core, center)
        for (ring in rings) {
            val col = v[ring.color]
            val a = ring.a.coerceIn(0f, 1f) * ring.strength
            val rad = ring.r * s
            drawCircle(col.copy(alpha = 0.18f * a), rad, center, style = Stroke(s * 0.06f))
            drawCircle(lerp(col, Color.White, 0.3f).copy(alpha = 0.85f * a), rad, center, style = Stroke(2.dp()))
        }
    }

    // ---------------------------------------------------------------- Halo

    private fun DrawScope.halo(v: List<Color>, f: AudioFeatures) {
        val s = min(size.width, size.height)
        val r0 = s * 0.2f * (1f + 0.3f * f.bass + 0.15f * f.beat)
        haloPath(r0, rot, f, 1.7f)
        drawPath(path, Brush.radialGradient(listOf(v[1].copy(alpha = 0.5f), v[0].copy(alpha = 0.12f)), center, r0 * 1.7f))
        drawPath(path, v[2].copy(alpha = 0.12f), style = Stroke(s * 0.035f))
        drawPath(path, v[2].copy(alpha = 0.35f), style = Stroke(s * 0.012f))
        drawPath(path, Color.White.copy(alpha = 0.85f), style = Stroke(1.5f.dp()))
        haloPath(r0 * 0.62f, -rot * 1.4f, f, 1.1f)
        drawPath(path, v[3].copy(alpha = 0.7f), style = Stroke(1.5f.dp()))
    }

    /** Anillo deformado por el espectro (espejeado para que cierre suave). */
    private fun DrawScope.haloPath(r0: Float, rot: Float, f: AudioFeatures, amount: Float) {
        val n = xs.size
        for (j in 0 until n) {
            val u = j.toFloat() / n
            val m = if (u < 0.5f) u * 2 else (1 - u) * 2
            val th = u * TAU + rot
            val r = r0 + f.band(m) * r0 * 0.55f * amount + sin(th * 3 + time * 1.7f) * r0 * 0.04f
            xs[j] = center.x + cos(th) * r
            ys[j] = center.y + sin(th) * r
        }
        path.reset()
        path.moveTo((xs[n - 1] + xs[0]) / 2, (ys[n - 1] + ys[0]) / 2)
        for (j in 0 until n) {
            val k = (j + 1) % n
            path.quadraticTo(xs[j], ys[j], (xs[j] + xs[k]) / 2, (ys[j] + ys[k]) / 2)
        }
        path.close()
    }

    // ---------------------------------------------------------------- Olas

    private fun DrawScope.waves(v: List<Color>, f: AudioFeatures) {
        val w = size.width; val h = size.height
        val step = max(6f, w / 160f)
        for (l in 0..3) {
            val e = when (l) { 0 -> f.bass; 1 -> (f.bass + f.mid) / 2; 2 -> f.mid; else -> f.treble }
            val base = h * (0.50f + l * 0.11f)
            val amp = h * (0.03f + 0.22f * e + 0.05f * f.beat)
            path.reset()
            path.moveTo(0f, h)
            var x = 0f
            while (x <= w + step) {
                val u = x / w
                val y = base + sin(u * TAU * (1.1f + l * 0.55f) + time * (0.7f + l * 0.3f) + l) * amp +
                    sin(u * TAU * (3.3f + l) - time * 1.25f) * amp * 0.35f
                path.lineTo(x, y)
                x += step
            }
            path.lineTo(w, h)
            path.close()
            drawPath(path, Brush.verticalGradient(listOf(v[l].copy(alpha = 0.42f), v[l].copy(alpha = 0.04f)), startY = base - amp, endY = h))
        }
    }

    // ---------------------------------------------------------------- Hiperespacio

    private fun DrawScope.hyperspace(v: List<Color>, f: AudioFeatures) {
        val s = max(size.width, size.height) * 0.3f
        val tail = 0.035f + 0.08f * f.treble + 0.05f * f.beat
        for ((i, st) in stars.withIndex()) {
            val z = st.z
            val zt = z + tail
            val p1 = Offset(center.x + st.x / z * s, center.y + st.y / z * s)
            val p0 = Offset(center.x + st.x / zt * s, center.y + st.y / zt * s)
            val near = 1f - z
            drawLine(
                lerp(v[i % 4], Color.White, 0.45f).copy(alpha = near.pow(1.5f).coerceIn(0f, 1f)),
                p0, p1, strokeWidth = near * 3f.dp() + 0.6f.dp(), cap = StrokeCap.Round,
            )
        }
    }

    // ---------------------------------------------------------------- Synthwave

    private fun DrawScope.synthwave(v: List<Color>, f: AudioFeatures) {
        val w = size.width; val h = size.height
        val hz = h * 0.6f
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFF07021A), lerp(v[1], Color(0xFF2A0845), 0.55f)), startY = 0f, endY = hz),
            size = Size(w, hz),
        )
        for (i in 0 until sky.size / 2) {
            val tw = 0.35f + 0.3f * sin(time * 2f + i)
            drawCircle(Color.White.copy(alpha = tw), 1f.dp(), Offset(sky[i * 2] * w, sky[i * 2 + 1] * hz * 0.85f))
        }
        val sunR = min(w, h) * 0.24f * (1f + 0.15f * f.bass + 0.12f * f.beat)
        val sc = Offset(w / 2, hz - sunR * 0.38f)
        drawCircle(Brush.radialGradient(listOf(v[3].copy(alpha = 0.45f), Color.Transparent), sc, sunR * 2.1f), sunR * 2.1f, sc)
        // Sol con rayas recortadas (capa aparte para que el recorte deje ver el cielo).
        drawContext.canvas.saveLayer(Rect(0f, 0f, w, h), Paint())
        drawCircle(
            Brush.verticalGradient(listOf(Color(0xFFFFE27A), lerp(Color(0xFFFF4FA3), v[0], 0.3f)), startY = sc.y - sunR, endY = sc.y + sunR),
            sunR, sc,
        )
        val gap = sunR * 0.14f
        val scroll = (time * sunR * 0.05f) % gap
        for (i in 0 until 7) {
            val y = sc.y + sunR * 0.05f + i * gap + scroll
            drawRect(Color.Black, Offset(sc.x - sunR, y), Size(2 * sunR, sunR * 0.018f * (i + 1)), blendMode = BlendMode.DstOut)
        }
        drawContext.canvas.restore()
        // Piso y rejilla en perspectiva.
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0A0418), Color(0xFF020006)), startY = hz, endY = h), Offset(0f, hz), Size(w, h - hz))
        val gridCol = lerp(v[2], Color(0xFFFF2BD6), 0.35f)
        val glow = 0.45f + 0.55f * f.bass + 0.3f * f.beat
        for (i in 0 until 16) {
            val t = (i + grid) / 16f
            val y = hz + (h - hz) * t * t
            drawLine(gridCol.copy(alpha = ((0.15f + 0.85f * t) * glow).coerceAtMost(1f)), Offset(0f, y), Offset(w, y), (1f + 1.5f * t).dp())
        }
        for (j in -12..12) {
            drawLine(gridCol.copy(alpha = 0.55f * glow), Offset(w / 2 + j * w * 0.012f, hz), Offset(w / 2 + j * w * 0.14f, h), 1.5f.dp())
        }
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, v[3].copy(alpha = 0.6f), Color.Transparent), startY = hz - 8f.dp(), endY = hz + 8f.dp()),
            Offset(0f, hz - 8f.dp()), Size(w, 16f.dp()),
        )
        drawLine(lerp(v[3], Color.White, 0.4f), Offset(0f, hz), Offset(w, hz), 2f.dp())
    }

    /** Medidas en dp con la densidad del lienzo actual (se fija al empezar [draw]). */
    private var dpScale = 1f
    private fun Float.dp() = this * dpScale
    private fun Int.dp() = this * dpScale

    private companion object {
        val BLOB_SPEED = floatArrayOf(1.0f, -0.7f, 0.5f, -1.3f)
        val BLOB_OFFSET = floatArrayOf(0.0f, 1.9f, 3.6f, 5.1f)
    }
}

/**
 * "Neón 360": visual con retroalimentación como el del Xbox 360. Cada frame se vuelve a pintar
 * el anterior un poco más grande y girado (la estela) y encima figuras nuevas en caleidoscopio.
 * Se dibuja en baja resolución y se escala: el desenfoque es parte del look.
 */
class NeonFeedback(private val maxPx: Int = 420) {
    private var a: ImageBitmap? = null
    private var b: ImageBitmap? = null
    private var ca: Canvas? = null
    private var cb: Canvas? = null
    private val cds = CanvasDrawScope()
    private val path = Path()
    private var hue = 0f

    fun frame(scope: DrawScope, f: AudioFeatures, time: Float, dt: Float) {
        val w = scope.size.width; val h = scope.size.height
        if (w < 1f || h < 1f) return
        val sc = min(1f, maxPx / max(w, h))
        val bw = max(8, (w * sc).toInt()); val bh = max(8, (h * sc).toInt())
        if (a?.width != bw || a?.height != bh) {
            a = ImageBitmap(bw, bh).also { ca = Canvas(it) }
            b = ImageBitmap(bw, bh).also { cb = Canvas(it) }
        }
        val src = a!!; val dst = b!!
        hue = (hue + dt * (30f + 140f * f.level)) % 360f
        cds.draw(Density(1f), LayoutDirection.Ltr, cb!!, Size(bw.toFloat(), bh.toFloat())) {
            drawRect(Color.Black)
            val z = 1.03f + 0.08f * f.bass + 0.05f * f.beat
            withTransform({ rotate(0.9f + 3f * f.mid, center); scale(z, z, center) }) {
                drawImage(src, alpha = 0.86f + 0.08f * f.level)
            }
            shapes(f, time)
        }
        // Intercambia: lo recién pintado es la base del siguiente frame.
        a = dst; b = src
        val t = ca; ca = cb; cb = t
        scope.drawImage(
            dst, srcOffset = androidx.compose.ui.unit.IntOffset.Zero, srcSize = IntSize(bw, bh),
            dstOffset = androidx.compose.ui.unit.IntOffset.Zero, dstSize = IntSize(w.toInt(), h.toInt()),
            filterQuality = FilterQuality.Low,
        )
    }

    private fun DrawScope.shapes(f: AudioFeatures, time: Float) {
        val s = min(size.width, size.height)
        val c1 = Color.hsv(hue, 1f, 1f)
        val c2 = Color.hsv((hue + 120f) % 360f, 0.9f, 1f)
        val c3 = Color.hsv((hue + 240f) % 360f, 0.9f, 1f)
        // Roseta con la forma de onda.
        val n = f.wave.size
        path.reset()
        for (j in 0..n) {
            val k = j % n
            val th = j.toFloat() / n * TAU + time * 0.4f
            val r = s * (0.16f + 0.16f * f.bass) * (1f + f.wave[k] * 2.4f)
            val p = Offset(center.x + cos(th) * r, center.y + sin(th) * r)
            if (j == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, c1, style = Stroke(1.6f + 3f * f.level))
        // Caleidoscopio de 6 brazos con las bandas.
        for (arm in 0 until 6) rotate(arm * 60f + time * 20f, center) {
            for (i in 0 until f.bands.size step 3) {
                val v = f.bands[i]
                if (v < 0.1f) continue
                val d = s * (0.05f + i / 24f * 0.42f)
                drawCircle(c2.copy(alpha = v), 1f + v * s * 0.02f, Offset(center.x + d, center.y))
            }
            if (f.treble > 0.05f) drawLine(c3.copy(alpha = 0.6f * f.treble), center, Offset(center.x + s * 0.45f * f.treble, center.y), 1.5f)
            // Un punto que orbita en cada brazo: con la estela dibuja espirales aunque no haya bajo.
            val d = s * (0.22f + 0.12f * sin(time * 0.9f) + 0.1f * f.mid)
            drawCircle(c1, 1.5f + 4f * f.level, Offset(center.x + d, center.y + sin(time * 2.3f) * s * 0.05f))
        }
        // Lissajous que sigue vivo aunque esté en pausa.
        drawCircle(c3, 2.5f, Offset(center.x + sin(time * 1.3f) * s * 0.3f, center.y + cos(time * 1.7f) * s * 0.2f))
        if (f.beat > 0.5f) drawCircle(Color.White.copy(alpha = f.beat * 0.5f), s * 0.3f * (1.2f - f.beat), center, style = Stroke(3f))
    }
}
