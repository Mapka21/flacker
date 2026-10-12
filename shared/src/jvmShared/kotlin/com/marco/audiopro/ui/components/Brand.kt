package com.marco.audiopro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.marco.audiopro.ui.theme.Display

/** Nombre de la app. */
const val APP_NAME = "Flacker"

/** Paleta de marca: negro, grafito, gris hueso y blanco. */
object BrandColors {
    val Ink = Color(0xFF070708)
    val Graphite = Color(0xFF232428)
    val Bone = Color(0xFF8A8A92)
    val White = Color(0xFFF4F4F6)
}

/**
 * La calavera pirata cibernética (viewBox 100×100): parche con cinta, ojo biónico, pistas de
 * circuito en el cráneo y huesos cruzados. Los huecos van con relleno par-impar y no se traslapan
 * (así se ve igual aquí, en el ícono de Android y en el .ico). Generada con tools/brand/skull.py.
 */
object Skull {
    const val SKULL = "M50,10 C30,10 18,23 18,40 C18,49 21,55 26,59 C27.5,60.2 28,61.5 28,63.5 L28,66 C28,68.8 30.2,71 33,71 L35,71 L35,77 C35,80.3 37.7,83 41,83 L59,83 C62.3,83 65,80.3 65,77 L65,71 L67,71 C69.8,71 72,68.8 72,66 L72,63.5 C72,61.5 72.5,60.2 74,59 C79,55 82,49 82,40 C82,23 70,10 50,10 Z M20.6,26.9 L54.6,34.4 C56,33.9 58,33.8 60.5,34 L69,34.9 C70.6,35.1 71.8,35.8 72.5,36.9 L80.6,34.4 L81.2,37.4 L73.1,39.9 L72.2,47.2 C71.5,51.5 67.8,54 63.5,54 C57.5,54 53.2,49.5 53.2,43.8 L53.8,37.5 L20.2,30.0 Z M28.5,44 C28.5,38.6 33,35.6 38.5,36.1 C43.5,36.5 46.5,39.8 46,44.6 C45.5,49.5 41.5,52.5 37,52 C32,51.5 28.5,48.5 28.5,44 Z M50,53.5 C48.5,56 46.8,58.8 46.6,60.4 C46.5,61.4 47.4,62 48.4,61.6 L50,60.8 L51.6,61.6 C52.6,62 53.5,61.4 53.4,60.4 C53.2,58.8 51.5,56 50,53.5 Z M36.00,74.60 L64.00,74.60 L64.00,76.10 L36.00,76.10 Z M40.40,71.20 L41.90,71.20 L41.90,74.60 L40.40,74.60 Z M40.40,76.10 L41.90,76.10 L41.90,80.60 L40.40,80.60 Z M45.00,71.20 L46.50,71.20 L46.50,74.60 L45.00,74.60 Z M45.00,76.10 L46.50,76.10 L46.50,80.60 L45.00,80.60 Z M49.25,71.20 L50.75,71.20 L50.75,74.60 L49.25,74.60 Z M49.25,76.10 L50.75,76.10 L50.75,80.60 L49.25,80.60 Z M53.50,71.20 L55.00,71.20 L55.00,74.60 L53.50,74.60 Z M53.50,76.10 L55.00,76.10 L55.00,80.60 L53.50,80.60 Z M58.10,71.20 L59.60,71.20 L59.60,74.60 L58.10,74.60 Z M58.10,76.10 L59.60,76.10 L59.60,80.60 L58.10,80.60 Z M57.80,18.75 L63.69,18.75 L68.25,23.31 L68.25,26.70 L69.75,26.70 L69.75,22.69 L64.31,17.25 L57.80,17.25 Z M53.10,18.00 C53.10,16.95 53.95,16.10 55.00,16.10 C56.05,16.10 56.90,16.95 56.90,18.00 C56.90,19.05 56.05,19.90 55.00,19.90 C53.95,19.90 53.10,19.05 53.10,18.00 Z M67.10,29.50 C67.10,28.45 67.95,27.60 69.00,27.60 C70.05,27.60 70.90,28.45 70.90,29.50 C70.90,30.55 70.05,31.40 69.00,31.40 C67.95,31.40 67.10,30.55 67.10,29.50 Z M29.31,22.75 L31.91,20.15 L37.70,20.15 L37.70,18.65 L31.29,18.65 L28.25,21.69 Z M24.90,24.20 C24.90,23.15 25.75,22.30 26.80,22.30 C27.85,22.30 28.70,23.15 28.70,24.20 C28.70,25.25 27.85,26.10 26.80,26.10 C25.75,26.10 24.90,25.25 24.90,24.20 Z M38.60,19.40 C38.60,18.35 39.45,17.50 40.50,17.50 C41.55,17.50 42.40,18.35 42.40,19.40 C42.40,20.45 41.55,21.30 40.50,21.30 C39.45,21.30 38.60,20.45 38.60,19.40 Z"
    const val EYE = "M32.70,44.00 C32.70,41.40 34.80,39.30 37.40,39.30 C40.00,39.30 42.10,41.40 42.10,44.00 C42.10,46.60 40.00,48.70 37.40,48.70 C34.80,48.70 32.70,46.60 32.70,44.00 Z M34.30,44.00 C34.30,42.29 35.69,40.90 37.40,40.90 C39.11,40.90 40.50,42.29 40.50,44.00 C40.50,45.71 39.11,47.10 37.40,47.10 C35.69,47.10 34.30,45.71 34.30,44.00 Z M36.00,44.00 C36.00,43.23 36.63,42.60 37.40,42.60 C38.17,42.60 38.80,43.23 38.80,44.00 C38.80,44.77 38.17,45.40 37.40,45.40 C36.63,45.40 36.00,44.77 36.00,44.00 Z"
    val BONES = listOf(
        "M19.21,81.63 L79.21,99.63 L80.79,94.37 L20.79,76.37 Z",
        "M79.21,76.37 L19.21,94.37 L20.79,99.63 L80.79,81.63 Z",
        "M15.30,76.60 C15.30,74.78 16.78,73.30 18.60,73.30 C20.42,73.30 21.90,74.78 21.90,76.60 C21.90,78.42 20.42,79.90 18.60,79.90 C16.78,79.90 15.30,78.42 15.30,76.60 Z",
        "M12.30,81.80 C12.30,79.98 13.78,78.50 15.60,78.50 C17.42,78.50 18.90,79.98 18.90,81.80 C18.90,83.62 17.42,85.10 15.60,85.10 C13.78,85.10 12.30,83.62 12.30,81.80 Z",
        "M78.10,76.60 C78.10,74.78 79.58,73.30 81.40,73.30 C83.22,73.30 84.70,74.78 84.70,76.60 C84.70,78.42 83.22,79.90 81.40,79.90 C79.58,79.90 78.10,78.42 78.10,76.60 Z",
        "M81.10,81.80 C81.10,79.98 82.58,78.50 84.40,78.50 C86.22,78.50 87.70,79.98 87.70,81.80 C87.70,83.62 86.22,85.10 84.40,85.10 C82.58,85.10 81.10,83.62 81.10,81.80 Z",
        "M12.50,94.60 C12.50,92.78 13.98,91.30 15.80,91.30 C17.62,91.30 19.10,92.78 19.10,94.60 C19.10,96.42 17.62,97.90 15.80,97.90 C13.98,97.90 12.50,96.42 12.50,94.60 Z",
        "M17.70,99.20 C17.70,97.38 19.18,95.90 21.00,95.90 C22.82,95.90 24.30,97.38 24.30,99.20 C24.30,101.02 22.82,102.50 21.00,102.50 C19.18,102.50 17.70,101.02 17.70,99.20 Z",
        "M80.90,94.60 C80.90,92.78 82.38,91.30 84.20,91.30 C86.02,91.30 87.50,92.78 87.50,94.60 C87.50,96.42 86.02,97.90 84.20,97.90 C82.38,97.90 80.90,96.42 80.90,94.60 Z",
        "M75.70,99.20 C75.70,97.38 77.18,95.90 79.00,95.90 C80.82,95.90 82.30,97.38 82.30,99.20 C82.30,101.02 80.82,102.50 79.00,102.50 C77.18,102.50 75.70,101.02 75.70,99.20 Z",
    )

    private fun parse(d: String, evenOdd: Boolean): Path =
        PathParser().parsePathString(d).toPath().apply { fillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero }

    val skull: Path by lazy { parse(SKULL, true) }
    val eye: Path by lazy { parse(EYE, true) }
    val bones: List<Path> by lazy { BONES.map { parse(it, false) } }

    /** Caja que ocupa el dibujo dentro del viewBox (los huesos bajan hasta 102.5). */
    const val LEFT = 12f
    const val TOP = 10f
    const val WIDTH = 76f
    const val HEIGHT = 92.5f
}

/** Dibuja la calavera para que su caja quepa en `height` px, centrada en `center`. */
fun DrawScope.drawSkull(center: Offset, height: Float, color: Color = BrandColors.White, bones: Color = BrandColors.Bone, glow: Boolean = true) {
    val k = height / Skull.HEIGHT
    val x = center.x - Skull.WIDTH * k / 2 - Skull.LEFT * k
    val y = center.y - Skull.HEIGHT * k / 2 - Skull.TOP * k
    translate(x, y) {
        scale(k, k, Offset.Zero) {
            Skull.bones.forEach { drawPath(it, bones) }
            drawPath(Skull.skull, color)
            // Brillo del ojo biónico.
            if (glow) drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), Offset(37.4f, 44f), 9f), 9f, Offset(37.4f, 44f))
            drawPath(Skull.eye, color)
        }
    }
}

/** Ícono de la app: cuadro negro con la calavera blanca. */
@Composable
fun FlackerMark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawFlackerMark(Offset.Zero, this.size.minDimension) }
}

/** El ícono dibujado en cualquier lienzo (también en las imágenes para compartir). */
fun DrawScope.drawFlackerMark(at: Offset, s: Float) {
    val corner = CornerRadius(s * 0.26f)
    drawRoundRect(Brush.verticalGradient(listOf(BrandColors.Graphite, BrandColors.Ink), at.y, at.y + s), at, Size(s, s), corner)
    drawRoundRect(Color.White.copy(alpha = 0.10f), at, Size(s, s), corner, style = Stroke(maxOf(1f, s * 0.02f)))
    drawSkull(at + Offset(s / 2, s / 2), s * 0.78f, glow = s >= 40f)
}

/** Logo: el ícono y "FLACKER" en mayúsculas espaciadas, con el 333 chiquito. */
@Composable
fun FlackerLogo(modifier: Modifier = Modifier, size: TextUnit = 19.sp) {
    val mark = with(LocalDensity.current) { (size * 1.7f).toDp() }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        FlackerMark(mark)
        Spacer(Modifier.width(mark * 0.36f))
        Text(
            buildAnnotatedString {
                append("FLACKER")
                withStyle(SpanStyle(fontSize = size * 0.48f, color = BrandColors.Bone, baselineShift = BaselineShift(0.9f), letterSpacing = size * 0.04f)) { append(" 333") }
            },
            style = Display.copy(fontSize = size, fontWeight = FontWeight.Black, letterSpacing = size * 0.14f, color = BrandColors.White),
        )
    }
}
