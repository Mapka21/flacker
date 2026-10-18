package com.marco.audiopro.ui.viz

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.marco.audiopro.platform.FxShader
import com.marco.audiopro.platform.supportsFxShaders
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val TAU = (2 * PI).toFloat()

/**
 * Las piezas del visualizador "Neon" del Xbox 360 (Jeff Minter / Llamasoft), según su manual:
 * - Túnel con dos texturas mezcladas que ruedan, avanza a su velocidad y las curvas entran desde el
 *   fondo y avanzan hacia ti (en la tarjeta de video).
 * - Boingy: una membrana elástica en simetría (6 u 8) que rebota y gira en espiral.
 * - Star: pirámides transparentes acomodadas como túnel que vuelan y giran.
 * - Psychedelia: un "pincel" que traza en simetría de espejo.
 * - Retroalimentación (feedback): cada cuadro se vuelve a pintar el anterior con intensidad, escala
 *   X/Y y giro propios; con filtros: cuadrantes, horizonte, espejo, color que gira.
 * - La cámara gira y se acerca con movimientos que se disparan en los picos de la música.
 * Una escena = una combinación; hay una matriz de escenas que se recorre sola (o con la cruceta).
 */
object Neon360Control {
    /** Escena actual en la matriz (cruceta: ←/→ ±1, ↑/↓ ±columnas). */
    var scene by mutableIntStateOf(0)
    /** Si alguien eligió escena a mano, ya no cambia sola (como en el 360). */
    var manual = false
    const val COLS = 8
    fun move(dx: Int, dy: Int) { manual = true; scene = Math.floorMod(scene + dx + dy * COLS, Neon360.SCENES.size) }
}

/** Una escena de la matriz. */
data class NeonScene(
    val tunnel: Int = -1,          // -1 sin túnel; si no, textura A (0..5)
    val tunnelB: Int = 1,          // textura B
    val square: Boolean = false,   // túnel cuadrado
    val boingy: Int = 0,           // 0 sin Boingy; si no, simetría (6, 8)
    val star: Boolean = false,
    val psyche: Int = 0,           // 0 sin Psychedelia; si no, simetría
    val fbMode: Int = 0,           // 0 normal, 1 espejo, 2 color que gira, 3 cuadrantes, 4 horizonte
    val fbScale: Float = 1.03f,
    val fbRot: Float = 1f,
    val fbIntensity: Float = 0.80f,
    val palette: Int = 0,
)

/** Paletas de neón (las "generadoras de color"): tres tonos que se mezclan y van girando. */
private val PALETTES = listOf(
    listOf(Color(0xFFFF2BD6), Color(0xFF00E5FF), Color(0xFFFFE600)),
    listOf(Color(0xFFFF6A00), Color(0xFFFF0040), Color(0xFFFFF4C2)),
    listOf(Color(0xFF39FF14), Color(0xFF00A2FF), Color(0xFF00FFC6)),
    listOf(Color(0xFF9D4DFF), Color(0xFFFF4FA3), Color(0xFFFFC94D)),
    listOf(Color(0xFF00F0FF), Color(0xFF7A5CFF), Color(0xFFFFFFFF)),
    listOf(Color(0xFFFF3B3B), Color(0xFFFFB800), Color(0xFF38FF9C)),
    listOf(Color(0xFFB4FF00), Color(0xFFFF00A8), Color(0xFF2B6BFF)),
    listOf(Color(0xFFFFFFFF), Color(0xFF8FA8FF), Color(0xFFFF7AE6)),
)

class Neon360 {
    companion object {
        /** La matriz: combinaciones curadas de túneles, Boingy, Star, Psychedelia y feedback (4 filas × 8). */
        val SCENES: List<NeonScene> = buildList {
            // Fila 1: túneles solos y con feedback girando.
            for (t in 0..5) add(NeonScene(tunnel = t, tunnelB = (t + 2) % 6, square = t % 2 == 1, fbMode = t % 3, fbRot = if (t % 2 == 0) 1.2f else -0.8f, palette = t))
            add(NeonScene(tunnel = 2, tunnelB = 4, boingy = 8, fbMode = 0, palette = 3))
            add(NeonScene(tunnel = 3, tunnelB = 0, square = true, star = true, fbMode = 1, palette = 4))
            // Fila 2: Boingy (la flor elástica) con humo de feedback.
            add(NeonScene(boingy = 8, fbMode = 0, fbScale = 1.05f, fbRot = 0.6f, fbIntensity = 0.90f, palette = 0))
            add(NeonScene(boingy = 6, fbMode = 2, fbScale = 1.04f, fbRot = -1.5f, palette = 1))
            add(NeonScene(boingy = 8, fbMode = 3, fbScale = 0.5f, fbIntensity = 0.92f, palette = 2))
            add(NeonScene(boingy = 8, fbMode = 4, fbRot = 0.2f, palette = 6))
            add(NeonScene(boingy = 6, tunnel = 5, tunnelB = 2, fbMode = 1, palette = 7))
            add(NeonScene(boingy = 8, star = true, fbMode = 0, fbScale = 1.02f, palette = 5))
            add(NeonScene(boingy = 8, psyche = 8, fbMode = 2, palette = 4))
            add(NeonScene(boingy = 6, tunnel = 1, tunnelB = 3, square = true, fbMode = 0, palette = 2))
            // Fila 3: Star (pirámides) y túneles cuadrados.
            add(NeonScene(star = true, fbMode = 0, fbScale = 1.04f, fbRot = 1.6f, palette = 0))
            add(NeonScene(star = true, fbMode = 1, palette = 3))
            add(NeonScene(star = true, fbMode = 3, fbScale = 0.5f, palette = 6))
            add(NeonScene(star = true, tunnel = 4, tunnelB = 1, fbMode = 2, palette = 1))
            add(NeonScene(star = true, psyche = 6, fbMode = 0, palette = 7))
            add(NeonScene(tunnel = 0, tunnelB = 5, square = true, psyche = 8, fbMode = 4, palette = 5))
            add(NeonScene(star = true, fbMode = 4, fbRot = -0.4f, palette = 2))
            add(NeonScene(tunnel = 3, tunnelB = 2, star = true, boingy = 6, fbMode = 0, palette = 4))
            // Fila 4: Psychedelia y feedback "salvaje" (cuadrantes, horizonte, espejo).
            add(NeonScene(psyche = 8, fbMode = 0, fbScale = 1.06f, fbRot = 2f, fbIntensity = 0.9f, palette = 0))
            add(NeonScene(psyche = 6, fbMode = 1, palette = 6))
            add(NeonScene(psyche = 8, fbMode = 3, fbScale = 0.5f, fbIntensity = 0.93f, palette = 3))
            add(NeonScene(psyche = 12, fbMode = 2, fbRot = -2f, palette = 1))
            add(NeonScene(psyche = 8, fbMode = 4, palette = 7))
            add(NeonScene(psyche = 6, tunnel = 2, tunnelB = 0, fbMode = 2, palette = 5))
            add(NeonScene(psyche = 8, boingy = 8, star = true, fbMode = 1, palette = 2))
            add(NeonScene(tunnel = 5, tunnelB = 3, psyche = 6, boingy = 6, fbMode = 0, fbRot = 0.8f, palette = 4))
        }
    }

    private val rnd = Random(360)
    private var sceneIdx = 0
    private var since = 0f
    private var dwell = 12f
    private var lastBeats = 0
    private var bassAvg = 0f

    // Cámara: giro y acercamiento que se disparan en los picos y avanzan hasta terminar.
    private var roll = 0f
    private var rollTarget = 0f
    private var zoomKick = 0f

    // Túnel.
    private val tunnelFx = if (supportsFxShaders) FxShader(ShaderViz.TUNNEL360_SOURCE) else null
    private val extra = FloatArray(16)
    private var tunnelPos = 0f
    private var tunnelRoll = 0f
    private val bendX = FloatArray(64)
    private val bendY = FloatArray(64)
    private var bendHead = 0
    private var bendT = 0f
    private val audio = FloatArray(4)

    private val layer = NeonLayer()

    private fun scene() = SCENES[sceneIdx]

    fun frame(scope: DrawScope, f: AudioFeatures, time: Float, dt: Float) = with(scope) {
        // ¿Cambiar de escena? Sola: cada 9–16 s o en un pico fuerte después de 6 s. A mano: con la cruceta.
        since += dt
        bassAvg += (f.bass - bassAvg) * min(1f, dt * 1.5f)
        val peak = f.beats != lastBeats && f.bass > bassAvg * 1.35f + 0.15f
        if (f.beats != lastBeats) {
            lastBeats = f.beats
            // Movimiento de cámara en los picos: gira un tramo y da un jalón de zoom.
            if (peak || rnd.nextFloat() < 0.18f) rollTarget += (if (rnd.nextBoolean()) 1 else -1) * (25f + rnd.nextFloat() * 70f)
            zoomKick = max(zoomKick, 0.35f * f.beat)
        }
        if (Neon360Control.manual) {
            if (Neon360Control.scene != sceneIdx) { sceneIdx = Neon360Control.scene; since = 0f }
        } else if (since > dwell || (peak && since > 6f)) {
            sceneIdx = (sceneIdx + 1 + rnd.nextInt(5)) % SCENES.size
            Neon360Control.scene = sceneIdx
            since = 0f; dwell = 9f + rnd.nextFloat() * 7f
        }
        roll += (rollTarget - roll) * min(1f, dt * 1.6f)
        zoomKick *= max(0f, 1f - dt * 3f)
        val sc = scene()
        val pal = PALETTES[sc.palette % PALETTES.size]

        withTransform({
            rotate(roll + VizControl.angle, center)
            val z = 1f + zoomKick + 0.15f * VizControl.zoom
            scale(z, z, center)
        }) {
            if (sc.tunnel >= 0) tunnel(sc, pal, f, time, dt)
            layer.scene = sc; layer.pal = pal
            layer.frame(this, f, time, dt)
        }
    }

    fun drawLast(scope: DrawScope) = layer.drawLast(scope)

    private fun DrawScope.tunnel(sc: NeonScene, pal: List<Color>, f: AudioFeatures, time: Float, dt: Float) {
        val fx = tunnelFx ?: return
        tunnelPos += dt * (0.9f + 2.6f * f.level + 3f * f.beat) * VizControl.speed
        tunnelRoll += dt * (0.05f + 0.25f * f.mid + VizControl.driftX * 0.6f)
        // Las curvas: se apunta hacia dónde tuerce (música o stick derecho) y eso viaja por el túnel.
        bendT += dt
        if (bendT > 0.05f) {
            bendT = 0f
            bendHead = (bendHead + 1) % bendX.size
            bendX[bendHead] = 0.28f * sin(time * 0.37f) + 0.12f * sin(time * 1.1f) * f.mid + 0.3f * VizControl.driftX
            bendY[bendHead] = 0.2f * cos(time * 0.29f) + 0.1f * f.bass + 0.3f * VizControl.driftY
        }
        fun lag(k: Int) = Math.floorMod(bendHead - k, bendX.size)
        extra[0] = sc.tunnel.toFloat(); extra[1] = sc.tunnelB.toFloat(); extra[2] = 0.5f + 0.5f * sin(time * 0.3f); extra[3] = if (sc.square) 1f else 0f
        extra[4] = tunnelPos; extra[5] = tunnelRoll; extra[6] = 0.25f * f.beat; extra[7] = if (sc.square) 4f else 8f
        for (k in 0..3) { extra[8 + k] = bendX[lag(k * 14)]; extra[12 + k] = bendY[lag(k * 14)] }
        audio[0] = f.bass; audio[1] = f.mid; audio[2] = f.treble; audio[3] = f.beat
        val b = fx.brush(size.width, size.height, time, tunnelPos, audio, pal, extra) ?: return
        drawRect(b)
    }
}

/** La capa con feedback donde viven Boingy, Star y Psychedelia. */
private class NeonLayer : Feedback(460) {
    var scene = NeonScene()
    var pal: List<Color> = PALETTES[0]
    private var hue = 0f
    /** Matriz que gira el tono (como feHueRotate) sin cambiar la luminosidad. */
    private fun hueRotate(deg: Float): ColorMatrix {
        val a = deg / 180f * PI.toFloat(); val c = cos(a); val s = sin(a)
        return ColorMatrix(floatArrayOf(
            0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
            0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
            0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
    }

    // Boingy: membranas de 14 nodos con resortes.
    private val n = 14
    private val bx = FloatArray(n); private val by = FloatArray(n)
    private val bvx = FloatArray(n); private val bvy = FloatArray(n)
    private var spin = 0f
    private val path = Path()

    // Star.
    private var starZ = 0f
    private var starSpin = 0f

    // Psychedelia: el pincel.
    private var px = 0f; private var py = 0f
    private var ppx = 0f; private var ppy = 0f
    private var pt = 0f

    private fun palette(u: Float, t: Float): Color {
        val x = ((u + t) % 1f + 1f) % 1f * 3f
        val i = x.toInt().coerceIn(0, 2); val k = x - i
        val a = pal[i]; val b = pal[(i + 1) % 3]
        return Color(a.red + (b.red - a.red) * k, a.green + (b.green - a.green) * k, a.blue + (b.blue - a.blue) * k)
    }

    override fun DrawScope.warp(prev: ImageBitmap, f: AudioFeatures, time: Float, dt: Float) {
        val s = scene
        val intensity = (s.fbIntensity + 0.04f * f.level).coerceAtMost(0.9f)
        val rot = s.fbRot * (1f + f.mid) + VizControl.spin * 0.02f
        val z = s.fbScale + 0.03f * f.bass
        when (s.fbMode) {
            1 -> { // Espejo: la estela sale en las cuatro direcciones.
                withTransform({ rotate(rot, center); scale(z, z, center) }) { drawImage(prev, alpha = intensity * 0.62f) }
                withTransform({ scale(-z, z, center) }) { drawImage(prev, alpha = intensity * 0.18f, blendMode = BlendMode.Screen) }
                withTransform({ scale(z, -z, center) }) { drawImage(prev, alpha = intensity * 0.18f, blendMode = BlendMode.Screen) }
            }
            2 -> { // El color de la estela va girando.
                hue = (hue + dt * (40f + 120f * f.bass)) % 360f
                withTransform({ rotate(rot, center); scale(z, z, center) }) { drawImage(prev, alpha = intensity * 0.92f, colorFilter = ColorFilter.colorMatrix(hueRotate(8f + 30f * f.beat))) }
            }
            3 -> { // Cuadrantes: todo se "sucede" hacia el centro en cuatro cuadros que se repiten.
                val q = s.fbScale
                for ((ox, oy) in listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)) {
                    withTransform({ translate(ox * size.width * (1 - q), oy * size.height * (1 - q)); scale(q, q, Offset.Zero); rotate(rot * 0.5f, center) }) {
                        drawImage(prev, alpha = intensity)
                    }
                }
            }
            4 -> { // Horizonte: ancho al máximo, alto al mínimo: rayas horizontales que parten la pantalla.
                withTransform({ rotate(rot * 0.3f, center); scale(1.12f, 0.93f, center) }) { drawImage(prev, alpha = intensity) }
            }
            else -> withTransform({ rotate(rot, center); scale(z, z, center) }) { drawImage(prev, alpha = intensity) }
        }
    }

    override fun DrawScope.shapes(f: AudioFeatures, time: Float, dt: Float) {
        val s = scene
        if (s.boingy > 0) boingy(s.boingy, f, time, dt)
        if (s.star) star(f, time, dt)
        if (s.psyche > 0) psyche(s.psyche, f, time, dt)
        if (s.boingy == 0 && !s.star && s.psyche == 0) {
            // Túnel solo: unos anillos que salen con el bajo para que la estela tenga de qué agarrarse.
            if (f.beat > 0.3f) drawCircle(palette(0f, time * 0.1f).copy(alpha = f.beat), min(size.width, size.height) * (0.5f - 0.3f * f.beat), center, style = Stroke(2f + 4f * f.beat))
        }
    }

    /** Boingy: la cabeza busca un punto que gira en espiral; cada nodo sigue al anterior con resorte. */
    private fun DrawScope.boingy(sym: Int, f: AudioFeatures, time: Float, dt: Float) {
        val sz = min(size.width, size.height)
        spin += dt * (0.6f + 2.2f * f.mid + 0.03f * VizControl.spin)
        val reach = 0.16f + 0.42f * f.bass + 0.18f * f.beat
        val tx = cos(spin) * reach * 0.6f + reach + 0.15f * VizControl.driftX
        val ty = sin(spin * 1.7f) * reach * 0.5f + 0.15f * VizControl.driftY
        for (i in 1 until n) {
            val (gx, gy) = if (i == n - 1) tx to ty else (bx[i + 1] * 0.55f + bx[i - 1] * 0.45f) to (by[i + 1] * 0.55f + by[i - 1] * 0.45f)
            val k = 60f; val damp = 0.86f
            bvx[i] = (bvx[i] + (gx - bx[i]) * k * dt) * damp
            bvy[i] = (bvy[i] + (gy - by[i]) * k * dt) * damp
        }
        for (i in 1 until n) { bx[i] += bvx[i] * dt * 6f; by[i] += bvy[i] * dt * 6f }
        bx[0] = 0f; by[0] = 0f
        for (c in 0 until sym) {
            val a = c * TAU / sym
            val mirror = c % 2 == 1
            path.reset()
            // Borde izquierdo y derecho de la membrana (más ancha en medio).
            for (side in 0..1) {
                val idx = if (side == 0) 0 until n else (n - 1 downTo 0)
                for (i in idx) {
                    val w = sin(PI.toFloat() * i / (n - 1)) * (0.05f + 0.07f * f.level) * (if (side == 0) 1 else -1)
                    val lx = bx[i]; val ly = (if (mirror) -by[i] else by[i]) + w
                    val p = Offset(center.x + (lx * cos(a) - ly * sin(a)) * sz, center.y + (lx * sin(a) + ly * cos(a)) * sz)
                    if (side == 0 && i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
            }
            path.close()
            val col = palette(c.toFloat() / sym, time * 0.08f)
            drawPath(path, Brush.radialGradient(listOf(col.copy(alpha = 0.15f), col.copy(alpha = 0.85f)), center, sz * reach * 1.6f + 1f))
            drawPath(path, Color.White.copy(alpha = 0.35f + 0.5f * f.beat), style = Stroke(1.2f))
        }
    }

    /** Star: anillos de pirámides que vienen hacia ti girando. */
    private fun DrawScope.star(f: AudioFeatures, time: Float, dt: Float) {
        val sz = min(size.width, size.height)
        starZ = (starZ + dt * (0.25f + 0.9f * f.level + 1.2f * f.beat) * VizControl.speed) % 1f
        starSpin += dt * (0.3f + 1.4f * f.mid)
        val rings = 9; val per = 7
        for (j in 0 until rings) {
            val z = ((j.toFloat() / rings - starZ) % 1f + 1f) % 1f
            val depth = 0.12f + z * 2.6f
            val fade = (1f - z) * min(1f, z * 6f)
            for (k in 0 until per) {
                val a = k * TAU / per + starSpin + j * 0.35f
                val base = 0.55f; val tip = 0.12f; val half = 0.22f
                fun pt(r: Float, da: Float) = Offset(center.x + cos(a + da) * r / depth * sz * 0.5f, center.y + sin(a + da) * r / depth * sz * 0.5f)
                path.reset()
                val p1 = pt(base, -half); val p2 = pt(base, half); val p3 = pt(tip, 0f)
                path.moveTo(p1.x, p1.y); path.lineTo(p2.x, p2.y); path.lineTo(p3.x, p3.y); path.close()
                val col = palette(k.toFloat() / per + z, time * 0.05f)
                drawPath(path, col.copy(alpha = 0.18f * fade))
                drawPath(path, col.copy(alpha = 0.8f * fade), style = Stroke(1.3f))
            }
        }
    }

    /** Psychedelia: un pincel que se mueve con la música y deja trazos en espejo. */
    private fun DrawScope.psyche(sym: Int, f: AudioFeatures, time: Float, dt: Float) {
        val sz = min(size.width, size.height)
        pt += dt * (0.8f + 2.5f * f.level)
        ppx = px; ppy = py
        px = (0.32f + 0.1f * f.bass) * sin(pt * 1.3f) + 0.08f * sin(pt * 4.1f) + 0.12f * VizControl.driftX
        py = (0.24f + 0.1f * f.mid) * sin(pt * 1.9f + 1f) + 0.08f * cos(pt * 3.3f) + 0.12f * VizControl.driftY
        val col = palette(pt * 0.05f, 0f)
        val w = 1.4f + 3.5f * f.level + 4f * f.beat
        for (c in 0 until sym) {
            val a = c * TAU / sym
            for (m in 0..1) {
                val my = if (m == 0) 1f else -1f
                fun tr(x: Float, y: Float) = Offset(center.x + (x * cos(a) - y * my * sin(a)) * sz, center.y + (x * sin(a) + y * my * cos(a)) * sz)
                drawLine(col, tr(ppx, ppy), tr(px, py), w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
        if (f.beat > 0.6f) drawCircle(Color.White.copy(alpha = 0.5f * f.beat), sz * 0.02f * (1f + abs(px) * 4f), Offset(center.x + px * sz, center.y + py * sz))
    }
}
