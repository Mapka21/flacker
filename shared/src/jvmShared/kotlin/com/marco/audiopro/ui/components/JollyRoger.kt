package com.marco.audiopro.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.marco.audiopro.platform.PixelBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * La calavera pirata con huesos cruzados de la marca, como figura matemática (distancias con signo)
 * en un cuadro de x −0.92..0.92, y −0.86..1.06 (y hacia abajo). De aquí salen el logo, el arte de
 * letras, los íconos (tools/brand/jolly.py es la misma figura) y la animación de carga, donde la
 * mandíbula se abre ([open]) para reírse.
 */
object JollyRoger {
    const val X0 = -0.92f
    const val X1 = 0.92f
    const val Y0 = -0.86f
    const val Y1 = 1.06f

    private fun len(x: Float, y: Float) = sqrt(x * x + y * y)

    private fun ellipse(x: Float, y: Float, cx: Float, cy: Float, rx: Float, ry: Float, rot: Float = 0f): Float {
        val dx = x - cx; val dy = y - cy
        val c = cos(rot); val s = sin(rot)
        val px = c * dx + s * dy; val py = -s * dx + c * dy
        return (len(px / rx, py / ry) - 1f) * min(rx, ry)
    }

    private fun box(x: Float, y: Float, cx: Float, cy: Float, hx: Float, hy: Float, r: Float): Float {
        val qx = abs(x - cx) - hx + r; val qy = abs(y - cy) - hy + r
        return len(max(qx, 0f), max(qy, 0f)) + min(max(qx, qy), 0f) - r
    }

    private fun segment(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float, r: Float): Float {
        val pax = x - ax; val pay = y - ay; val bax = bx - ax; val bay = by - ay
        val h = ((pax * bax + pay * bay) / (bax * bax + bay * bay)).coerceIn(0f, 1f)
        return len(pax - bax * h, pay - bay * h) - r
    }

    /** Separaciones entre dientes dentro de una franja. */
    private fun toothGap(x: Float, y: Float, top: Float, bottom: Float): Boolean {
        if (y < top || y > bottom || abs(x) > 0.27f) return false
        val f = ((x + 0.27f) / 0.0675f) % 1f
        return f < 0.17f
    }

    /** Cráneo con ojos, nariz y dientes de arriba (distancia: negativa = adentro). */
    fun skull(x: Float, y: Float): Float {
        val cranium = ellipse(x, y, 0f, -0.30f, 0.58f, 0.50f)
        val cheeks = box(x, y, 0f, 0.06f, 0.42f, 0.20f, 0.16f)
        val teeth = box(x, y, 0f, 0.275f, 0.27f, 0.045f, 0.02f)
        var d = min(min(cranium, cheeks), teeth)
        val eyeL = ellipse(x, y, -0.22f, -0.05f, 0.155f, 0.13f, 0.28f)
        val eyeR = ellipse(x, y, 0.22f, -0.05f, 0.155f, 0.13f, -0.28f)
        val nose = ellipse(x, y, 0f, 0.165f, 0.05f, 0.065f)
        d = max(d, -min(min(eyeL, eyeR), nose))
        if (toothGap(x, y, 0.235f, 0.32f)) d = max(d, 0.01f)
        return d
    }

    /** Mandíbula; [open] 0 cerrada … 1 abierta a carcajada. */
    fun jaw(x: Float, y: Float, open: Float): Float {
        val jy = y - open * 0.17f
        val teeth = box(x, jy, 0f, 0.36f, 0.26f, 0.035f, 0.02f)
        val bone = box(x, jy, 0f, 0.45f, 0.30f, 0.08f, 0.08f)
        var d = min(teeth, bone)
        if (toothGap(x, jy, 0.325f, 0.395f)) d = max(d, 0.01f)
        return d
    }

    /** Huesos cruzados con sus nudos en las puntas. */
    fun bones(x: Float, y: Float): Float {
        var d = min(segment(x, y, -0.80f, 0.30f, 0.80f, 0.96f, 0.062f), segment(x, y, 0.80f, 0.30f, -0.80f, 0.96f, 0.062f))
        for ((ex, ey, dx, dy) in listOf(
            floatArrayOf(-0.80f, 0.30f, -1f, -0.82f), floatArrayOf(0.80f, 0.96f, 1f, 0.82f),
            floatArrayOf(0.80f, 0.30f, 1f, -0.82f), floatArrayOf(-0.80f, 0.96f, -1f, 0.82f),
        )) {
            val l = len(dx, dy); val ux = dx / l; val uy = dy / l
            val nx = -uy; val ny = ux
            d = min(d, len(x - (ex + nx * 0.065f + ux * 0.01f), y - (ey + ny * 0.065f + uy * 0.01f)) - 0.08f)
            d = min(d, len(x - (ex - nx * 0.065f + ux * 0.01f), y - (ey - ny * 0.065f + uy * 0.01f)) - 0.08f)
        }
        return d
    }

    /** Qué hay en (x, y): 0 nada, 1 calavera, 2 huesos; con su distancia (negativa = adentro). */
    fun sample(x: Float, y: Float, open: Float = 0f): Pair<Int, Float> {
        val head = min(skull(x, y), jaw(x, y, open))
        if (head < 0f) return 1 to head
        val b = bones(x, y)
        // Los huesos van detrás, con una orilla de aire alrededor de la calavera.
        if (b < 0f && head > 0.05f) return 2 to b
        return 0 to min(head, b)
    }

    private const val ALNUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    /**
     * La figura hecha de letras y números al azar (como la de la imagen de referencia), con
     * [cols] × [rows] caracteres. Las orillas usan caracteres ligeros para que se vea suave.
     * @param tilt giro de la cabeza (radianes) alrededor del cuello.
     */
    fun text(cols: Int, rows: Int, seed: Int = 333, open: Float = 0f, tilt: Float = 0f, reveal: Float = 1f): String {
        val rnd = Random(seed)
        val order = Random(7)
        val sb = StringBuilder(rows * (cols + 1))
        val cellW = (X1 - X0) / cols; val cellH = (Y1 - Y0) / rows
        val c = cos(tilt); val s = sin(tilt)
        for (r in 0 until rows) {
            val line = CharArray(cols) { ' ' }
            var last = -1
            for (k in 0 until cols) {
                // Cuánto de la celda cubre la figura (3×3 muestras): así las partes delgadas no se pierden.
                var hit = 0
                for (sy in 0..2) for (sx in 0..2) {
                    var x = X0 + (k + (sx + 0.5f) / 3f) * cellW; var y = Y0 + (r + (sy + 0.5f) / 3f) * cellH
                    if (tilt != 0f) { val dx = x; val dy = y - 0.2f; x = c * dx + s * dy; y = 0.2f - s * dx + c * dy }
                    if (sample(x, y, open).first != 0) hit++
                }
                val ch = ALNUM[rnd.nextInt(ALNUM.length)]
                val light = ".:;il"[rnd.nextInt(5)]
                val shown = order.nextFloat() < reveal
                if (hit >= 3 && shown) {
                    line[k] = if (hit >= 5) ch else light
                    last = k
                }
            }
            sb.append(line, 0, last + 1)
            if (r < rows - 1) sb.append('\n')
        }
        return sb.toString()
    }

    /** Columnas para [rows] renglones en monoespaciada (la letra mide ~1.7 veces más alta que ancha). */
    fun colsFor(rows: Int) = (rows * 1.7f * (X1 - X0) / (Y1 - Y0)).toInt()

    private val cache = HashMap<Pair<Int, Int>, ImageBitmap>()

    /** Silueta en blanco de la calavera (part = 1) o de los huesos (part = 2), suavizada, de [px] × [px]. */
    fun silhouette(px: Int, part: Int): ImageBitmap = cache.getOrPut(px to part) {
        val buf = PixelBuffer(px, px)
        val span = max(X1 - X0, Y1 - Y0)
        val ox = X0 - (span - (X1 - X0)) / 2f; val oy = Y0 - (span - (Y1 - Y0)) / 2f
        val aa = 1.2f * span / px
        for (j in 0 until px) for (i in 0 until px) {
            val x = ox + (i + 0.5f) * span / px; val y = oy + (j + 0.5f) * span / px
            val head = min(skull(x, y), jaw(x, y, 0f))
            val d = if (part == 1) head else {
                val b = bones(x, y); if (head < 0.05f) max(b, 0.05f - head) else b
            }
            val a = (0.5f - d / aa).coerceIn(0f, 1f)
            buf.pixels[j * px + i] = ((a * 255).toInt() shl 24) or 0xFFFFFF
        }
        buf.image()
    }
}

/** La calavera con huesos, sólida, centrada en [center] con alto [height] (logo chico, historias). */
fun DrawScope.drawJollyRoger(center: Offset, height: Float, color: Color, bones: Color) {
    val px = height.toInt().coerceIn(8, 640)
    val dst = IntSize(height.toInt(), height.toInt())
    val at = IntOffset((center.x - height / 2).toInt(), (center.y - height / 2).toInt())
    drawImage(JollyRoger.silhouette(px, 2), srcSize = IntSize(px, px), dstOffset = at, dstSize = dst, colorFilter = ColorFilter.tint(bones))
    drawImage(JollyRoger.silhouette(px, 1), srcSize = IntSize(px, px), dstOffset = at, dstSize = dst, colorFilter = ColorFilter.tint(color))
}
