package com.marco.audiopro.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.ui.theme.Hifi
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Panel de "vidrio" estilo Apple: blanco muy translúcido con un filo de luz arriba.
 * Sobre el fondo vivo de la portada se lee como cristal esmerilado.
 */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(16.dp),
    fill: Color = Hifi.Glass,
): Modifier = this
    .clip(shape)
    .background(fill)
    .border(1.dp, Brush.verticalGradient(listOf(Hifi.GlassStrokeTop, Hifi.GlassStrokeBottom)), shape)

/** Toque estilo iOS: sin ripple, la pieza se hunde un poco con resorte y se oscurece. */
fun Modifier.bouncyClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "press",
    )
    graphicsLayer {
        scaleX = scale; scaleY = scale
        alpha = if (pressed) 0.85f else 1f
    }.clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
}

/**
 * Fondo "vivo" como el de Apple Music: manchas de los colores de la portada que se mueven
 * lentamente, bajo un velo oscuro para que el texto se lea.
 * @param intensity 1 = reproductor a pantalla completa; ~0.4 = fondo sutil de la app.
 */
@Composable
fun AmbientBackground(coverArt: String?, modifier: Modifier = Modifier, intensity: Float = 1f) {
    val ctx = LocalPlatformContext.current
    val api = LocalApp.current.api
    var target by remember { mutableStateOf(listOf(Hifi.PanelHi, Hifi.Panel, Hifi.PanelHi, Hifi.Panel)) }
    LaunchedEffect(coverArt) { coverColors(ctx, api, coverArt)?.let { target = it.swatches } }
    val c0 by animateColorAsState(target[0], tween(1400), label = "a0")
    val c1 by animateColorAsState(target[1], tween(1400), label = "a1")
    val c2 by animateColorAsState(target[2], tween(1400), label = "a2")
    val c3 by animateColorAsState(target[3], tween(1400), label = "a3")

    val t = rememberInfiniteTransition(label = "ambient")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(28_000, easing = LinearEasing)), label = "phase")

    Canvas(modifier.fillMaxSize()) {
        drawRect(Hifi.Bg)
        val w = size.width; val h = size.height
        val r = max(w, h) * 0.72f
        // Cada mancha orbita a su propio ritmo; juntas forman un "mesh gradient" que respira.
        val blobs = listOf(
            Triple(c0, 1.0f, 0.0f),
            Triple(c1, -0.7f, 1.9f),
            Triple(c2, 0.5f, 3.6f),
            Triple(c3, -1.3f, 5.1f),
        )
        for ((color, speed, offset) in blobs) {
            val a = phase * speed + offset
            val center = Offset(w * (0.5f + 0.38f * cos(a)), h * (0.5f + 0.34f * sin(a * 1.3f)))
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = 0.62f * intensity), Color.Transparent), center, r),
                r, center,
            )
        }
        // Velo: más oscuro abajo, donde van los controles y el texto.
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.62f))))
    }
}
