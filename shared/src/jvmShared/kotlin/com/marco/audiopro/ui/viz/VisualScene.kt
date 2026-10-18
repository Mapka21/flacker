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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.unit.IntOffset
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
enum class VisualPreset(val label: String, val blurb: String, val gpu: Boolean = false) {
    PORTADA("Portada viva", "Como Apple Music: tu portada derretida fluyendo con la música"),
    AURORA("Aurora", "Los colores de la portada respiran con el bajo"),
    PULSO("Pulso", "Ondas de choque en cada golpe"),
    HALO("Halo", "Un anillo líquido que baila con el espectro"),
    OLAS("Olas", "Una ola por rango de frecuencias"),
    HIPER("Hiperespacio", "Estrellas que aceleran con los agudos"),
    SYNTH("Synthwave", "Sol neón y rejilla retro al ritmo"),
    NEON("Neón 360", "Como el \"Neon\" de Jeff Minter en el Xbox 360: arcos parabólicos y chorros de partículas en espejo que explotan con el bajo"),
    N360("Xbox 360", "Los del visualizador del Xbox 360 (Neon de Jeff Minter): túneles que se doblan, la flor elástica, pirámides y retroalimentación; cambian solos con la música o con la cruceta"),
    XBOX("Xbox 2001", "El de la primera Xbox (tipo Geiss): ondas verde neón y azul flotando sobre negro, con una estela que crece y gira"),
    ASCII("ASCII 333", "Tu portada y la música dibujadas con caracteres, como arte ASCII: ondas en cada golpe y el espectro subiendo"),
    NEXO("Nexo 2029", "El túnel caleidoscopio del Xbox 360 llevado al futuro: vidrios que salen volando con cada golpe y un disco iridiscente al centro"),
    CALEIDO("Caleidoscopio", "Ocho espejos que doblan líneas de luz y se abren con el bajo", gpu = true),
    PLASMA("Plasma", "El plasma de los demos de los 90 con curvas de neón que corren con la música", gpu = true),
    TUNEL("Túnel 360", "Una rejilla de neón que te jala hacia adentro; los anillos destellan en cada golpe", gpu = true),
    FRACTAL("Fractal", "Un fractal que se dobla sobre sí mismo como vidrio de colores", gpu = true),
    ESTALLIDO("Estallido", "Rayos de luz desde el centro que crecen con el bajo y ondas que salen", gpu = true),
    ;

    companion object {
        /** Los que este dispositivo puede mostrar (los de shader piden Android 13 en el celular). */
        val available: List<VisualPreset> get() = entries.filter { !it.gpu || com.marco.audiopro.platform.supportsFxShaders }
    }
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
    private val neon = MinterNeon()
    private val geiss = GeissFeedback()
    private val n360 = Neon360()
    private val nexo = NexoTunnel()
    private val ascii = AsciiVision()
    /** Para el modo ASCII: la portada nítida y quien mide el texto (los pone el fondo cada frame). */
    var art: ImageBitmap? = null
    var measurer: androidx.compose.ui.text.TextMeasurer? = null
    private var dtLast = 0.016f
    /** Tiempo "musical": corre más rápido con la música y da un jalón en cada golpe (para los shaders). */
    private var flow = 0f
    private val shaders = HashMap<VisualPreset, com.marco.audiopro.platform.FxShader>()
    private val audio = FloatArray(4)

    private fun respawn(s: Star) {
        s.x = rnd.nextFloat() * 2 - 1; s.y = rnd.nextFloat() * 2 - 1; s.z = 1f
    }

    fun step(rawDt: Float, f: AudioFeatures) {
        // Con el control se puede acelerar o frenar todo.
        val dt = rawDt * VizControl.speed
        dtLast = dt
        time += dt
        flow += dt * (0.35f + 1.1f * f.level + 2.2f * f.beat)
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

    /** El pincel del shader de [preset] para este frame, o null si no es de shader o no se puede. */
    fun shaderBrush(preset: VisualPreset, w: Float, h: Float, cols: List<Color>, f: AudioFeatures): Brush? {
        if (!preset.gpu) return null
        val fx = shaders.getOrPut(preset) { com.marco.audiopro.platform.FxShader(ShaderViz.source(preset)!!) }
        audio[0] = f.bass; audio[1] = f.mid; audio[2] = f.treble; audio[3] = f.beat
        return fx.brush(w, h, time, flow, audio, List(3) { cols[it].vivid(DuskPalette[it]) })
    }

    /** Un fondo de shader a pantalla completa; false si no se pudo (y hay que dibujar otro). */
    fun drawShader(scope: DrawScope, preset: VisualPreset, cols: List<Color>, f: AudioFeatures): Boolean = with(scope) {
        val b = shaderBrush(preset, size.width, size.height, cols, f) ?: return false
        drawRect(b)
        true
    }

    /** Presets que llevan figuras encima del fondo (las demás son sólo el fondo fluido). */
    fun hasFx(preset: VisualPreset) = preset != VisualPreset.PORTADA && preset != VisualPreset.AURORA && !preset.gpu

    /**
     * Capa de fondo, pensada para difuminarse muchísimo: la portada girando y fluyendo
     * (Apple Music) o, sin portada, manchas de sus colores.
     */
    fun drawBase(scope: DrawScope, preset: VisualPreset, cols: List<Color>, art: ImageBitmap?, f: AudioFeatures) = scope.clipRect {
        dpScale = density
        drawRect(Hifi.Bg)
        // ASCII: fondo casi negro, los caracteres son toda la imagen.
        if (preset == VisualPreset.ASCII || preset == VisualPreset.N360) return@clipRect
        val v = List(4) { cols[it].vivid(DuskPalette[it]) }
        val k = if (hasFx(preset)) 0.6f else 1f
        if (art != null && preset != VisualPreset.AURORA) artwork(art, f, k) else blobs(cols, v, f, k)
    }

    /** Capa de figuras: se dibuja dos veces, difuminada (brillo) y nítida encima. */
    fun drawFx(scope: DrawScope, preset: VisualPreset, cols: List<Color>, f: AudioFeatures, glow: Boolean) = scope.clipRect {
        dpScale = density
        val v = List(4) { cols[it].vivid(DuskPalette[it]) }
        val punch = 1f + 0.08f * f.beat + 0.07f * f.bass
        drawContext.transform.scale(punch, punch, center)
        when (preset) {
            VisualPreset.PULSO -> pulse(v, f)
            VisualPreset.HALO -> halo(v, f)
            VisualPreset.OLAS -> waves(v, f)
            VisualPreset.HIPER -> hyperspace(v, f)
            VisualPreset.SYNTH -> synthwave(v, f)
            VisualPreset.NEON -> if (glow) neon.drawLast(this) else neon.frame(this, f, time, dtLast)
            VisualPreset.XBOX -> if (glow) geiss.drawLast(this) else geiss.frame(this, f, time, dtLast)
            VisualPreset.N360 -> if (glow) n360.drawLast(this) else n360.frame(this, f, time, dtLast)
            VisualPreset.NEXO -> if (glow) nexo.drawLast(this) else nexo.frame(this, v, f, time, dtLast)
            VisualPreset.ASCII -> measurer?.let { ascii.draw(this, it, art, v, f, time, dpScale, glow) }
            else -> {}
        }
        drawContext.transform.scale(1f / punch, 1f / punch, center)
        if (preset == VisualPreset.NEXO && !glow) nexo.finish(this, f, dpScale)
    }

    /** Velo para que se lea el texto: más oscuro abajo y según la intensidad pedida. */
    fun drawVeil(scope: DrawScope, intensity: Float) = with(scope) {
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.06f), Color.Black.copy(alpha = 0.45f))))
        if (intensity < 1f) drawRect(Hifi.Bg.copy(alpha = ((1f - intensity) * 0.8f).coerceIn(0f, 1f)))
    }

    /** Todo en un solo lienzo, sin difuminar (pantallas chicas como la del iPod). */
    fun draw(scope: DrawScope, preset: VisualPreset, cols: List<Color>, f: AudioFeatures, intensity: Float, art: ImageBitmap? = null) {
        if (preset.gpu && drawShader(scope, preset, cols, f)) { drawVeil(scope, intensity); return }
        drawBase(scope, if (art == null && preset == VisualPreset.PORTADA) VisualPreset.AURORA else preset, cols, art, f)
        if (hasFx(preset)) drawFx(scope, preset, cols, f, glow = false)
        drawVeil(scope, intensity)
    }

    // ---------------------------------------------------------------- Portada viva (Apple Music)

    private val saturate = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.6f) })

    /** Cuatro copias enormes de la portada girando lento; difuminadas parecen pintura líquida. */
    private fun DrawScope.artwork(img: ImageBitmap, f: AudioFeatures, k: Float) {
        val w = size.width; val h = size.height
        val s = max(w, h)
        val src = IntSize(img.width, img.height)
        for (i in 0..3) {
            val a = phase * ART_SPEED[i] + BLOB_OFFSET[i]
            val cx = w * (0.5f + 0.30f * cos(a))
            val cy = h * (0.5f + 0.28f * sin(a * 1.2f))
            val band = when (i) { 0 -> f.bass; 1 -> f.mid; 2 -> f.treble; else -> f.level }
            val d = s * (0.85f + 0.12f * i) * (1f + 0.25f * band)
            rotate(a * 40f + i * 90f, Offset(cx, cy)) {
                drawImage(
                    img, srcOffset = IntOffset.Zero, srcSize = src,
                    dstOffset = IntOffset((cx - d / 2).toInt(), (cy - d / 2).toInt()), dstSize = IntSize(d.toInt(), d.toInt()),
                    alpha = (0.9f * k).coerceAtMost(1f), colorFilter = saturate,
                )
            }
        }
        if (f.beat > 0f) drawRect(Color.White.copy(alpha = 0.07f * f.beat * k))
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
        val ART_SPEED = floatArrayOf(0.6f, -0.45f, 0.35f, -0.8f)
    }
}

/**
 * "Nexo 2029": el túnel caleidoscopio del visualizador del Xbox 360, más fino. Cada frame vuelve a
 * pintar el anterior más grande, girado y en espejo (todo parece salir disparado hacia ti), y encima
 * van vidrios en simetría de 6 que salen del centro con cada golpe, un disco iridiscente que late
 * con el bajo y el espectro alrededor. Al final, un acabado de tele: líneas de barrido, viñeta y
 * una separación de color que se abre en los golpes.
 */
class NexoTunnel(private val maxPx: Int = 560) {
    private var a: ImageBitmap? = null
    private var b: ImageBitmap? = null
    private var ca: Canvas? = null
    private var cb: Canvas? = null
    private val cds = CanvasDrawScope()
    private val path = Path()
    private val rnd = Random(2029)
    private var hue = 0f
    private var spin = 0f
    private var lastBeats = 0
    private var spawnAcc = 0f

    private class Shard {
        var alive = false
        var th = 0f; var d = 0f; var v = 0f
        var rot = 0f; var vr = 0f
        var size = 0f; var tone = 0
        var life = 0f
        val pts = FloatArray(8)
    }
    private val shards = Array(110) { Shard() }

    private fun spawn(power: Float) {
        val s = shards.firstOrNull { !it.alive } ?: return
        s.alive = true
        s.th = rnd.nextFloat() * TAU
        s.d = 0.02f + rnd.nextFloat() * 0.05f
        s.v = 0.10f + rnd.nextFloat() * 0.25f + 0.5f * power
        s.rot = rnd.nextFloat() * TAU
        s.vr = (rnd.nextFloat() - 0.5f) * 5f
        s.size = 0.012f + rnd.nextFloat() * 0.03f + 0.025f * power
        s.tone = rnd.nextInt(4)
        s.life = 1f
        // Vidrio roto: 4 puntas irregulares alrededor del centro.
        for (k in 0 until 4) {
            val ang = k * (TAU / 4) + (rnd.nextFloat() - 0.5f) * 0.9f
            val r = 0.55f + rnd.nextFloat() * 0.9f * if (k == 0) 1.8f else 1f
            s.pts[k * 2] = cos(ang) * r
            s.pts[k * 2 + 1] = sin(ang) * r
        }
    }

    fun frame(scope: DrawScope, v: List<Color>, f: AudioFeatures, time: Float, dt: Float) {
        val w = scope.size.width; val h = scope.size.height
        if (w < 1f || h < 1f) return
        val sc = min(1f, maxPx / max(w, h))
        val bw = max(8, (w * sc).toInt()); val bh = max(8, (h * sc).toInt())
        if (a?.width != bw || a?.height != bh) {
            a = ImageBitmap(bw, bh).also { ca = Canvas(it) }
            b = ImageBitmap(bw, bh).also { cb = Canvas(it) }
        }
        step(f, dt)
        val src = a!!; val dst = b!!
        // Paleta: la portada empujada hacia magenta y violeta, como en el video del 360.
        val magenta = Color(0xFFFF2BD6); val violet = Color(0xFF7A5CFF); val ice = Color(0xFF7FF6FF)
        val drift = Color.hsv(hue, 0.75f, 1f)
        val tones = arrayOf(lerp(v[0], magenta, 0.55f), lerp(v[1], violet, 0.55f), lerp(v[2], ice, 0.4f), lerp(drift, magenta, 0.35f))
        cds.draw(Density(1f), LayoutDirection.Ltr, cb!!, Size(bw.toFloat(), bh.toFloat())) {
            drawRect(Color.Black)
            val z = 1.035f + 0.05f * f.bass + 0.07f * f.beat
            val turn = 0.5f + 2.2f * f.mid + 3f * f.beat
            withTransform({ rotate(turn, center); scale(z, z, center) }) {
                drawImage(src, alpha = 0.74f + 0.08f * f.level)
            }
            // Espejo: la estela se vuelve simétrica y aparece el caleidoscopio. (La suma de las dos
            // copias queda abajo de 1 para que la estela se apague a negro y no se vuelva neblina.)
            withTransform({ rotate(-turn * 0.5f, center); scale(-z, z, center) }) {
                drawImage(src, alpha = 0.16f, blendMode = BlendMode.Screen)
            }
            disc(tones, f, time)
            glass(tones)
        }
        a = dst; b = src
        val t = ca; ca = cb; cb = t
        drawLast(scope)
    }

    private fun step(f: AudioFeatures, dt: Float) {
        hue = (hue + dt * (12f + 60f * f.level)) % 360f
        spin += dt * (0.6f + 2.5f * f.mid + 4f * f.beat)
        if (f.beats != lastBeats) {
            lastBeats = f.beats
            repeat(5 + (6 * f.bass).toInt()) { spawn(1f) }
        }
        spawnAcc += dt * (2.5f + 14f * f.level)
        while (spawnAcc >= 1f) { spawn(0.2f * f.level); spawnAcc -= 1f }
        for (s in shards) if (s.alive) {
            s.v += dt * (0.25f + 0.8f * f.treble)
            s.d += s.v * dt
            s.rot += s.vr * dt
            s.life -= dt * 0.35f
            if (s.d > 0.9f || s.life <= 0f) s.alive = false
        }
    }

    /** Disco al centro (el CD del video): arcoíris girando, surcos, hoyo y aro de espectro. */
    private fun DrawScope.disc(tones: Array<Color>, f: AudioFeatures, time: Float) {
        val s = min(size.width, size.height)
        val r = s * (0.085f + 0.05f * f.bass + 0.03f * f.beat)
        rotate(spin * 57.3f, center) {
            drawCircle(
                Brush.sweepGradient(listOf(tones[0], tones[2], tones[1], Color.White, tones[3], tones[0]), center),
                r, center, alpha = 0.85f,
            )
        }
        for (i in 1..4) drawCircle(Color.Black.copy(alpha = 0.18f), r * (0.45f + 0.13f * i), center, style = Stroke(1f))
        drawCircle(Color.Black, r * 0.22f, center)
        drawCircle(Color.White.copy(alpha = 0.7f), r * 0.22f, center, style = Stroke(1.2f))
        // Espectro alrededor, en espejo para que cierre.
        val n = 48
        for (j in 0 until n) {
            val u = j.toFloat() / n
            val m = if (u < 0.5f) u * 2 else (1 - u) * 2
            val e = f.band(m)
            if (e < 0.04f) continue
            val th = u * TAU + spin * 0.4f
            val r0 = r * 1.25f
            val r1 = r0 + e * s * 0.16f
            drawLine(
                lerp(tones[j % 4], Color.White, 0.25f).copy(alpha = 0.35f + 0.65f * e),
                Offset(center.x + cos(th) * r0, center.y + sin(th) * r0),
                Offset(center.x + cos(th) * r1, center.y + sin(th) * r1),
                strokeWidth = 1.2f + 2.5f * e, cap = StrokeCap.Round,
            )
        }
        if (f.beat > 0.4f) drawCircle(Color.White.copy(alpha = 0.6f * f.beat), r * (1.3f + 2.2f * (1f - f.beat)), center, style = Stroke(2f))
        // Destellos que giran solos (así se mueve aunque esté en pausa).
        for (k in 0 until 3) {
            val th = time * (0.7f + 0.3f * k) + k * 2.1f
            val d = r * (1.9f + 0.6f * sin(time * 0.8f + k))
            drawCircle(lerp(tones[k], Color.White, 0.4f), 1.6f + 2f * f.level, Offset(center.x + cos(th) * d, center.y + sin(th) * d))
        }
    }

    /** Vidrios en simetría de 6 (con espejo en los brazos impares). */
    private fun DrawScope.glass(tones: Array<Color>) {
        val s = min(size.width, size.height)
        val reach = max(size.width, size.height) * 0.62f
        for (sh in shards) {
            if (!sh.alive) continue
            val dist = sh.d * reach
            // Perspectiva: más grandes conforme se acercan a la orilla.
            val sz = s * sh.size * (0.6f + sh.d * 2.4f)
            val cx = center.x + cos(sh.th) * dist
            val cy = center.y + sin(sh.th) * dist
            path.reset()
            for (k in 0 until 4) {
                val px = sh.pts[k * 2]; val py = sh.pts[k * 2 + 1]
                val x = cx + (px * cos(sh.rot) - py * sin(sh.rot)) * sz
                val y = cy + (px * sin(sh.rot) + py * cos(sh.rot)) * sz
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            val alpha = (sh.life * 1.4f).coerceIn(0f, 1f)
            val col = tones[sh.tone]
            val fill = Brush.linearGradient(
                listOf(lerp(col, Color.White, 0.22f).copy(alpha = 0.95f * alpha), col.copy(alpha = 0.6f * alpha)),
                Offset(cx - sz, cy - sz), Offset(cx + sz, cy + sz),
            )
            for (arm in 0 until 6) {
                withTransform({
                    rotate(arm * 60f, center)
                    if (arm % 2 == 1) scale(1f, -1f, center)
                }) {
                    drawPath(path, fill)
                    drawPath(path, Color.White.copy(alpha = 0.55f * alpha), style = Stroke(0.8f))
                }
            }
        }
    }

    /** Pinta el último frame sin avanzar (la capa de brillo). */
    fun drawLast(scope: DrawScope, alpha: Float = 1f, offsetX: Float = 0f, filter: ColorFilter? = null) {
        val img = a ?: return
        scope.drawImage(
            img, srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
            dstOffset = IntOffset(offsetX.toInt(), 0), dstSize = IntSize(scope.size.width.toInt(), scope.size.height.toInt()),
            alpha = alpha, filterQuality = FilterQuality.Low, blendMode = BlendMode.Screen, colorFilter = filter,
        )
    }

    private val redOnly = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
    private val blueOnly = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))

    /** Acabado de tele (encima de todo, nítido): separación de color, barrido y viñeta. */
    fun finish(scope: DrawScope, f: AudioFeatures, dp: Float) = with(scope) {
        val split = (1.5f + 6f * f.beat + 2f * f.bass) * dp
        drawLast(this, 0.45f, -split, redOnly)
        drawLast(this, 0.45f, split, blueOnly)
        val gap = 3f * dp
        var y = 0f
        val line = Color.Black.copy(alpha = 0.10f)
        while (y < size.height) { drawRect(line, Offset(0f, y), Size(size.width, dp)); y += gap }
        val r = max(size.width, size.height) * 0.75f
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), center, r))
    }
}

/**
 * "ASCII 333": la portada convertida en caracteres (" .,:;?iSk$", oscuro → claro) con ondas que salen
 * del centro en cada golpe y el espectro subiendo desde abajo, también en caracteres.
 *
 * Rápido: cada carácter se rasteriza una vez (máscara de alfa) y el cuadro entero se arma en un
 * arreglo de pixeles en Kotlin, que se manda a la pantalla como UNA imagen, a lo mucho 30 veces por
 * segundo. (Dibujar miles de glifos sueltos por frame dejaba la app a ~1 fps.)
 */
class AsciiVision {
    private val ramp = " .,:;?iSk$"
    private var masks: Array<IntArray> = emptyArray()
    private var cw = 0
    private var ch = 0
    private var cols = 0
    private var rows = 0
    private var lum = FloatArray(0)
    private var lumFor: ImageBitmap? = null
    private var buffer: com.marco.audiopro.platform.PixelBuffer? = null
    private var frame: ImageBitmap? = null
    private var lastBuild = -1f

    /** Máscara de alfa (0..255) de cada carácter, del tamaño de una celda. */
    private fun buildMasks(m: androidx.compose.ui.text.TextMeasurer, cellH: Int) {
        val h = cellH.coerceAtLeast(6)
        if (masks.isNotEmpty() && ch == h) return
        val w = (h * 0.6f).toInt().coerceAtLeast(4)
        ch = h; cw = w
        val atlas = ImageBitmap(w * ramp.length, h)
        val style = androidx.compose.ui.text.TextStyle(
            fontFamily = com.marco.audiopro.ui.theme.Fonts.mono, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            fontSize = androidx.compose.ui.unit.TextUnit(h * 0.86f, androidx.compose.ui.unit.TextUnitType.Sp), color = Color.White,
        )
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(atlas), Size(atlas.width.toFloat(), h.toFloat())) {
            for (i in 1 until ramp.length) {
                val l = m.measure(ramp[i].toString(), style, density = this, layoutDirection = LayoutDirection.Ltr)
                drawText(l, topLeft = Offset(i * w + (w - l.size.width) / 2f, (h - l.size.height) / 2f))
            }
        }
        val px = IntArray(atlas.width * h)
        atlas.readPixels(px, 0, 0, atlas.width, h)
        masks = Array(ramp.length) { c ->
            IntArray(w * h) { k -> (px[(k / w) * atlas.width + c * w + k % w] ushr 24) and 255 }
        }
        buffer = null
    }

    /** Brillo de la portada por celda (se recalcula si cambia la portada o la cuadrícula). */
    private fun sample(art: ImageBitmap?) {
        if (lum.size == cols * rows && lumFor === art) return
        lumFor = art
        lum = FloatArray(cols * rows)
        if (art == null) return
        val small = ImageBitmap(cols, rows)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(small), Size(cols.toFloat(), rows.toFloat())) {
            // La portada cubre todo (recortada al centro, como "crop").
            val s = max(cols.toFloat() / art.width, rows.toFloat() / art.height)
            val dw = (art.width * s).toInt(); val dh = (art.height * s).toInt()
            drawImage(art, IntOffset.Zero, IntSize(art.width, art.height), IntOffset((cols - dw) / 2, (rows - dh) / 2), IntSize(dw, dh), filterQuality = FilterQuality.Medium)
        }
        val px = IntArray(cols * rows)
        small.readPixels(px, 0, 0, cols, rows)
        var lo = 1f; var hi = 0f
        for (i in px.indices) {
            val p = px[i]
            val v = (0.299f * ((p shr 16) and 255) + 0.587f * ((p shr 8) and 255) + 0.114f * (p and 255)) / 255f
            lum[i] = v; lo = min(lo, v); hi = max(hi, v)
        }
        // Contraste estirado: hasta una portada oscura usa toda la rampa.
        val span = (hi - lo).coerceAtLeast(0.15f)
        for (i in lum.indices) lum[i] = ((lum[i] - lo) / span).coerceIn(0f, 1f)
    }

    // ---- vida: lluvia tipo Matrix, renglones con "glitch" y caracteres que titilan
    private var dropY = FloatArray(0)
    private var dropSpeed = FloatArray(0)
    private var glitchFrames = 0
    private var glitchShift = IntArray(0)
    private var lastBeats = 0
    private var lastTime = 0f
    private var frameNo = 0
    private val rnd = java.util.Random(333)
    private val rowColor = IntArray(512)

    private fun ensureRain() {
        if (dropY.size == cols) return
        dropY = FloatArray(cols) { -rnd.nextFloat() * rows * 2 }
        dropSpeed = FloatArray(cols) { 6f + rnd.nextFloat() * 14f }
        glitchShift = IntArray(rows)
    }

    fun draw(
        scope: DrawScope, m: androidx.compose.ui.text.TextMeasurer, art: ImageBitmap?, tones: List<Color>,
        f: AudioFeatures, time: Float, dp: Float, glow: Boolean,
    ) = with(scope) {
        if (glow) return@with
        // Se arma a lo mucho a 1.25x (no a la densidad completa) y se escala: menos pixeles por cuadro.
        val k = min(dp, 1.25f)
        buildMasks(m, (13f * k).toInt())
        if (masks.isEmpty()) return@with
        val scale = dp / k
        cols = (size.width / scale / cw).toInt().coerceAtLeast(1)
        rows = (size.height / scale / ch).toInt().coerceAtLeast(1)
        sample(art)
        ensureRain()
        val bw = cols * cw; val bh = rows * ch
        val buf = buffer?.takeIf { it.width == bw && it.height == bh } ?: com.marco.audiopro.platform.PixelBuffer(bw, bh).also { buffer = it; frame = null }
        // Cada cuadro (60 fps): el armado en Kotlin es barato; lo caro era dibujar glifo por glifo.
        if (frame == null || time != lastBuild) {
            val dt = if (lastTime == 0f || time < lastTime) 0.016f else (time - lastTime).coerceIn(0f, 0.1f)
            lastTime = time
            lastBuild = time
            render(buf, art != null, tones, f, time, dt)
            frame = buf.image()
        }
        val img = frame ?: return@with
        // Golpe = "zoom" de cámara.
        val z = 1f + 0.06f * f.beat
        val dw = bw * scale * z; val dh = bh * scale * z
        drawImage(
            img, srcOffset = IntOffset.Zero, srcSize = IntSize(bw, bh),
            dstOffset = IntOffset(((size.width - dw) / 2).toInt(), ((size.height - dh) / 2).toInt()),
            dstSize = IntSize(dw.toInt(), dh.toInt()), filterQuality = FilterQuality.Low,
        )
    }

    private fun mix(a: Color, b: Color, t: Float): Int {
        val r = (a.red + (b.red - a.red) * t) * 255; val g = (a.green + (b.green - a.green) * t) * 255; val bl = (a.blue + (b.blue - a.blue) * t) * 255
        return (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or bl.toInt().coerceIn(0, 255)
    }

    /** Hash barato por celda para que titilen sin pedir números al azar en cada una. */
    private fun hash(x: Int, y: Int, t: Int): Int {
        var h = x * 374761393 + y * 668265263 + t * 2147483647
        h = (h xor (h ushr 13)) * 1274126177
        return h xor (h ushr 16)
    }

    /** El cuadro en el arreglo de pixeles, ya con color (sin premultiplicar). */
    private fun render(buf: com.marco.audiopro.platform.PixelBuffer, hasArt: Boolean, tones: List<Color>, f: AudioFeatures, time: Float, dt: Float) {
        val px = buf.pixels
        java.util.Arrays.fill(px, 0)
        val w = buf.width
        frameNo++
        // Color por renglón: degradado entre los tonos de la portada que se va desplazando.
        for (y in 0 until min(rows, rowColor.size)) {
            val t = ((y.toFloat() / rows + time * 0.05f) % 1f) * 3f
            val i0 = t.toInt().coerceIn(0, 2)
            rowColor[y] = mix(lerp(tones[i0], Color.White, 0.35f), lerp(tones[i0 + 1], Color.White, 0.35f), t - i0)
        }
        // Lluvia: cada columna tiene una gota; el bajo y los agudos la aceleran, un golpe suelta más.
        val hit = f.beats != lastBeats
        lastBeats = f.beats
        val accel = 1f + 2.5f * f.treble + 3f * f.beat
        for (x in 0 until cols) {
            dropY[x] += dropSpeed[x] * dt * accel
            if (dropY[x] > rows + 14) { dropY[x] = -rnd.nextFloat() * rows * (if (hit) 0.2f else 1.2f); dropSpeed[x] = 6f + rnd.nextFloat() * 14f }
        }
        if (hit) for (n in 0 until cols / 6) { val x = rnd.nextInt(cols); if (dropY[x] < 0) dropY[x] = rnd.nextFloat() * 4f }
        // Glitch: en golpes fuertes unos renglones se corren unos cuadros.
        if (hit && f.beat > 0.6f) {
            glitchFrames = 7
            for (y in glitchShift.indices) glitchShift[y] = if (rnd.nextFloat() < 0.12f) rnd.nextInt(9) - 4 else 0
        }
        if (glitchFrames > 0) glitchFrames--
        val specRows = rows * 0.32f
        val cx = cols / 2f; val cy = rows / 2f
        val diag = kotlin.math.sqrt(cx * cx + cy * cy * 4f)
        val last = ramp.length - 1
        val wave = 0.07f + 0.2f * f.bass
        val flicker = 0.03f + 0.3f * f.treble
        val tick = frameNo / 3
        for (y in 0 until rows) {
            val fromBottom = rows - 1 - y
            val shift = if (glitchFrames > 0) glitchShift[y] else 0
            val color = rowColor[min(y, rowColor.size - 1)]
            for (x in 0 until cols) {
                val sx = (x + shift).coerceIn(0, cols - 1)
                var v = if (hasArt) lum[y * cols + sx] * 0.8f else 0.06f + 0.05f * sin(x * 0.3f + time * 2f)
                val dx = x - cx; val dy = (y - cy) * 2f
                val d = kotlin.math.sqrt(dx * dx + dy * dy) / diag
                // Ondas desde el centro (más rápidas que antes), más fuertes con el bajo y en cada golpe.
                v += sin(d * 26f - time * 7f) * wave + f.beat * 0.35f * (1f - d).coerceAtLeast(0f)
                // Gota de lluvia: cabeza brillante y cola que se apaga.
                val dist = dropY[x] - y
                var head = false
                if (dist >= 0 && dist < 14) { v = max(v, 1f - dist / 14f); head = dist < 1f }
                // Espectro: columnas que suben desde abajo.
                val band = f.band(x / cols.toFloat())
                if (fromBottom < specRows * band) v = max(v, 0.7f + 0.3f * (1f - fromBottom / (specRows * band + 0.01f)))
                v = v.coerceIn(0f, 1f)
                var c = (v * last + 0.25f).toInt().coerceIn(0, last)
                if (c == 0) continue
                // Los caracteres titilan (más con los agudos).
                val hsh = hash(x, y, tick)
                if ((hsh and 1023) < flicker * 1024) c = (c + (hsh shr 10 and 3) - 1).coerceIn(1, last)
                val mask = masks[c]
                val a = (0.3f + 0.7f * v) * 255f
                val rgb = if (head) 0xFFFFFF else color
                val base = y * ch * w + x * cw
                for (py in 0 until ch) {
                    val row = base + py * w
                    val mr = py * cw
                    for (pxi in 0 until cw) {
                        val mv = mask[mr + pxi]
                        if (mv != 0) px[row + pxi] = ((mv * a / 255f).toInt() shl 24) or rgb
                    }
                }
            }
        }
    }
}
