package com.marco.audiopro.ui.viz

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.marco.audiopro.platform.drawPointCloud
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Manejar los visuales en vivo, como con el control en los Xbox: velocidad, giro, zoom, hacia dónde
 * se va la imagen y la forma de las ondas. Lo mueven el control de Xbox (PC), las flechas y la rueda
 * del mouse en pantalla completa. Son valores sueltos (no estado): se leen al dibujar cada cuadro.
 */
object VizControl {
    /** Multiplicador de la velocidad de todo (0.25 … 4). */
    var speed = 1f
    /** Giro extra en grados por segundo (stick izquierdo / ← →). */
    var spin = 0f
    /** Zoom extra (−0.4 … 0.8) (stick izquierdo / ↑ ↓ / rueda). */
    var zoom = 0f
    /** Hacia dónde "fluye" la estela (stick derecho), −1 … 1. */
    var driftX = 0f
    var driftY = 0f
    /** Giro acumulado de la cámara (grados). */
    var angle = 0f
    /** Forma de las ondas (X en el control). */
    var shape by mutableIntStateOf(0)
    /** Hay un control conectado (para mostrar la ayuda). */
    var pad by mutableStateOf(false)

    fun step(dt: Float) { angle = (angle + spin * dt) % 360f }
    fun reset() { speed = 1f; spin = 0f; zoom = 0f; driftX = 0f; driftY = 0f; angle = 0f }
}

/** Retroalimentación: cada cuadro se vuelve a pintar el anterior deformado y encima lo nuevo. */
abstract class Feedback(private val maxPx: Int) {
    private var a: ImageBitmap? = null
    private var b: ImageBitmap? = null
    private var ca: Canvas? = null
    private var cb: Canvas? = null
    private val cds = CanvasDrawScope()

    protected abstract fun DrawScope.warp(prev: ImageBitmap, f: AudioFeatures, time: Float, dt: Float)
    protected abstract fun DrawScope.shapes(f: AudioFeatures, time: Float, dt: Float)

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
        cds.draw(Density(1f), LayoutDirection.Ltr, cb!!, Size(bw.toFloat(), bh.toFloat())) {
            drawRect(Color.Black)
            warp(src, f, time, dt)
            shapes(f, time, dt)
        }
        a = dst; b = src
        val t = ca; ca = cb; cb = t
        drawLast(scope)
    }

    fun drawLast(scope: DrawScope) {
        val img = a ?: return
        scope.drawImage(
            img, srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
            dstOffset = IntOffset.Zero, dstSize = IntSize(scope.size.width.toInt(), scope.size.height.toInt()),
            filterQuality = FilterQuality.Low, blendMode = BlendMode.Screen,
        )
    }
}

/**
 * "Xbox 2001": el visualizador del reproductor de CD de la primera Xbox (motor tipo Geiss / Windows
 * Media Player): ondas psicodélicas verde neón y azul flotando sobre negro, con la estela que se
 * agranda, gira y se deforma al ritmo. Tres formas de onda (X en el control): listones, anillo y espiral.
 */
class GeissFeedback : Feedback(400) {
    private val path = Path()
    private val green = Color(0xFF39FF14)
    private val blue = Color(0xFF2E8BFF)
    private val aqua = Color(0xFF00FFC6)
    private val rnd = Random(2001)
    private val sparks = FloatArray(160)
    private var lastBeats = 0
    private var wob = 0f

    override fun DrawScope.warp(prev: ImageBitmap, f: AudioFeatures, time: Float, dt: Float) {
        wob += dt * (0.7f + 2f * f.mid)
        val z = 1.025f + 0.07f * f.bass + 0.04f * f.beat + 0.06f * VizControl.zoom
        val rot = (0.5f + 1.6f * f.mid + 0.05f * VizControl.spin) * sin(wob * 0.37f).let { if (abs(it) < 0.3f) 0.3f * (if (it < 0) -1 else 1) else it }
        val drift = Offset(size.width * (0.006f * sin(wob) + 0.02f * VizControl.driftX), size.height * (0.006f * cos(wob * 0.8f) + 0.02f * VizControl.driftY))
        withTransform({ translate(drift.x, drift.y); rotate(rot, center); scale(z, z * (1f + 0.012f * sin(wob * 1.7f)), center) }) {
            drawImage(prev, alpha = 0.80f + 0.06f * f.level)
        }
        // Un segundo paso más chico y tenue: la estela se "derrite" en vez de sólo crecer.
        withTransform({ rotate(-rot * 0.5f, center); scale(0.985f, 0.985f, center) }) { drawImage(prev, alpha = 0.07f, blendMode = BlendMode.Screen) }
    }

    override fun DrawScope.shapes(f: AudioFeatures, time: Float, dt: Float) {
        val w = size.width; val h = size.height; val s = min(w, h)
        val wave = f.wave; val n = wave.size
        val amp = s * (0.18f + 0.35f * f.bass)
        val stroke = Stroke(1.4f + 2.6f * f.level)
        when (Math.floorMod(VizControl.shape, 3)) {
            0 -> for ((k, col) in listOf(green, blue).withIndex()) {
                // Dos listones que cruzan la pantalla, uno arriba y otro abajo, desfasados.
                path.reset()
                val y0 = h * (0.5f + (if (k == 0) -0.1f else 0.1f) * sin(time * 0.6f + k))
                for (j in 0 until n) {
                    val x = j.toFloat() / (n - 1) * w
                    val y = y0 + wave[(j + k * 17) % n] * amp * 1.4f + sin(j * 0.09f + time * (1.2f + k)) * s * 0.02f
                    if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, col, style = stroke)
            }
            1 -> for ((k, col) in listOf(green, blue).withIndex()) {
                path.reset()
                for (j in 0..n) {
                    val th = j.toFloat() / n * 2f * PI.toFloat() + time * (0.5f - k)
                    val r = s * (0.18f + 0.08f * k + 0.12f * f.bass) + wave[j % n] * amp
                    val p = Offset(center.x + cos(th) * r, center.y + sin(th) * r)
                    if (j == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                drawPath(path, col, style = stroke)
            }
            else -> for ((k, col) in listOf(green, aqua).withIndex()) {
                path.reset()
                for (j in 0 until n) {
                    val u = j.toFloat() / n
                    val th = u * 6f * PI.toFloat() + time * 0.8f + k * PI.toFloat()
                    val r = s * 0.04f + u * s * 0.42f + wave[j] * amp * 0.5f
                    val p = Offset(center.x + cos(th) * r, center.y + sin(th) * r)
                    if (j == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                drawPath(path, col, style = stroke)
            }
        }
        // Chispas en los golpes.
        if (f.beats != lastBeats) {
            lastBeats = f.beats
            for (i in 0 until sparks.size step 2) { sparks[i] = rnd.nextFloat() * w; sparks[i + 1] = rnd.nextFloat() * h }
        }
        if (f.beat > 0.2f) drawPointCloud(sparks, sparks.size / 2, aqua.copy(alpha = f.beat), 2f + 3f * f.beat)
    }
}

/**
 * "Neón 360": a la manera del "Neon" de Jeff Minter (Llamasoft) en el Xbox 360. Todo en simetría de
 * espejo de cuatro: arcos parabólicos que se abren con el bajo, chorros de partículas que caen en
 * parábola, explosiones en los golpes y colores que giran más rápido entre más pega la música, sobre
 * una estela que se agranda y gira.
 */
class MinterNeon : Feedback(440) {
    private val path = Path()
    private var hue = 0f
    private val rnd = Random(360)
    private val max = 700
    private val px = FloatArray(max); private val py = FloatArray(max)
    private val vx = FloatArray(max); private val vy = FloatArray(max)
    private val life = FloatArray(max); private val ph = FloatArray(max)
    private var count = 0
    private var lastBeats = 0
    private var emit = 0f
    private val pts = Array(6) { FloatArray(max * 8) }
    private val ptsN = IntArray(6)

    private fun spawn(speed: Float, h: Float) {
        if (count >= max) return
        val a = rnd.nextFloat() * 2f * PI.toFloat()
        val v = speed * (0.4f + rnd.nextFloat())
        px[count] = 0f; py[count] = 0f
        vx[count] = cos(a) * v; vy[count] = sin(a) * v - speed * 0.6f
        life[count] = 1f; ph[count] = h
        count++
    }

    override fun DrawScope.warp(prev: ImageBitmap, f: AudioFeatures, time: Float, dt: Float) {
        val z = 1.018f + 0.05f * f.bass + 0.03f * f.beat + 0.05f * VizControl.zoom
        val rot = 0.4f + 1.4f * f.mid + 0.05f * VizControl.spin
        withTransform({
            translate(size.width * 0.015f * VizControl.driftX, size.height * 0.015f * VizControl.driftY)
            rotate(rot, center); scale(z, z, center)
        }) { drawImage(prev, alpha = 0.79f + 0.07f * f.level) }
        // La estela también en espejo: así todo queda simétrico como en el Xbox.
        withTransform({ scale(-z, z, center) }) { drawImage(prev, alpha = 0.09f, blendMode = BlendMode.Screen) }
    }

    override fun DrawScope.shapes(f: AudioFeatures, time: Float, dt: Float) {
        val s = min(size.width, size.height)
        hue = (hue + dt * (25f + 220f * f.bass + 120f * f.beat)) % 360f
        // Arcos parabólicos (y = a·x²) en los cuatro cuadrantes.
        val arcs = 5
        for (k in 0 until arcs) {
            val col = Color.hsv((hue + k * 28f) % 360f, 0.85f, 1f)
            val a = (0.6f + 2.2f * f.bass + k * 0.5f) * (1f + 0.3f * sin(time * 0.9f + k)) / s
            val reach = s * (0.18f + 0.07f * k + 0.12f * f.mid)
            path.reset()
            for (q in 0 until 4) {
                val sx = if (q % 2 == 0) 1f else -1f; val sy = if (q < 2) 1f else -1f
                for (j in 0..24) {
                    val x = j / 24f * reach
                    val y = a * x * x
                    val p = Offset(center.x + sx * x, center.y + sy * (y - s * 0.02f * k))
                    if (j == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
            }
            drawPath(path, col.copy(alpha = 0.55f + 0.45f * f.level), style = Stroke(1.2f + 2.2f * f.bass))
        }
        // Partículas: un chorro constante y una explosión en cada golpe.
        emit += dt * (40f + 260f * f.level)
        while (emit > 1f) { spawn(s * 0.25f, hue); emit -= 1f }
        if (f.beats != lastBeats) { lastBeats = f.beats; repeat(70) { spawn(s * (0.6f + 0.6f * f.bass), (hue + 180f) % 360f) } }
        val g = s * 0.55f
        var i = 0
        while (i < count) {
            life[i] -= dt * 0.75f
            if (life[i] <= 0f) {
                count--; px[i] = px[count]; py[i] = py[count]; vx[i] = vx[count]; vy[i] = vy[count]; life[i] = life[count]; ph[i] = ph[count]
                continue
            }
            vy[i] += g * dt // caen en parábola
            px[i] += vx[i] * dt; py[i] += vy[i] * dt
            i++
        }
        // En cubetas de color, con sus cuatro reflejos, para dibujarlas en pocas llamadas.
        ptsN.fill(0)
        for (k in 0 until count) {
            val b = ((ph[k] / 60f).toInt()).coerceIn(0, 5)
            val arr = pts[b]; var n = ptsN[b]
            for (q in 0 until 4) {
                val sx = if (q % 2 == 0) 1f else -1f; val sy = if (q < 2) 1f else -1f
                if (n * 2 + 1 >= arr.size) break
                arr[n * 2] = center.x + sx * px[k]; arr[n * 2 + 1] = center.y + sy * py[k]; n++
            }
            ptsN[b] = n
        }
        for (b in 0 until 6) if (ptsN[b] > 0) drawPointCloud(pts[b], ptsN[b], Color.hsv(b * 60f + 30f, 0.75f, 1f), 1.6f + 2.4f * f.level)
        if (f.beat > 0.55f) drawCircle(Color.White.copy(alpha = 0.6f * f.beat), s * 0.32f * (1.25f - f.beat), center, style = Stroke(2.5f))
    }
}
