package com.marco.audiopro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Fonts
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/** La animación de entrada sólo sale una vez por arranque (no al girar el celular ni al cerrar sesión). */
object SplashState {
    var done by mutableStateOf(System.getProperty("flacker.noSplash") == "true")
}

private const val TOTAL = 3.1f

/** Qué tan abierta va la mandíbula en el segundo [t]: "ja, ja, ja" que empieza fuerte y se apaga. */
private fun laugh(t: Float): Float {
    val l = t - 0.85f
    if (l < 0f || l > 1.5f) return 0f
    val env = (l / 0.15f).coerceAtMost(1f) * exp(-l * 1.6f)
    return max(0f, sin(l * 2f * PI.toFloat() * 4.3f)) * env
}

/**
 * Pantalla de carga: la calavera de letras se arma (los caracteres aparecen revueltos), se ríe
 * abriendo y cerrando la mandíbula con la cabeza hacia atrás, sale "FLACKER 333" letra por letra y
 * se desvanece. Un clic o toque la salta.
 */
@Composable
fun SkullSplash(onDone: () -> Unit) {
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val t0 = withFrameNanos { it }
        while (t.floatValue < TOTAL) withFrameNanos { t.floatValue = (it - t0) / 1e9f }
        onDone()
    }
    // Las letras se revuelven a 30 cuadros por segundo (no hace falta más y así cuesta poco).
    val frame by remember { derivedStateOf { (t.floatValue * 30).toInt() } }
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = (1f - (t.floatValue - (TOTAL - 0.45f)) / 0.45f).coerceIn(0f, 1f) }
            .background(Color.Black)
            .clickable(remember { MutableInteractionSource() }, null) { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        val rows = 34
        val cols = JollyRoger.colsFor(rows)
        val density = LocalDensity.current
        val skullH = minOf(maxWidth * 0.9f, maxHeight * 0.74f)
        val fs = with(density) { (skullH.toPx() / rows / 1.02f).toSp() }
        val ft = frame / 30f
        val art = remember(frame) {
            val open = laugh(ft)
            JollyRoger.text(
                cols, rows, seed = 333 + frame / 2, open = open,
                tilt = -0.10f * open - 0.03f * (if (ft > 0.85f && ft < 2.4f) 1f else 0f),
                reveal = ((ft - 0.05f) / 0.65f).coerceIn(0f, 1f),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                art,
                style = TextStyle(
                    fontFamily = Fonts.mono, fontWeight = FontWeight.Bold, fontSize = fs, lineHeight = fs * 1.02f, letterSpacing = 0.sp,
                    brush = Brush.verticalGradient(listOf(BrandColors.White, Color(0xFFC9C9CF), BrandColors.Bone)),
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                ),
                softWrap = false,
                // Cada carcajada sacude la cabeza tantito hacia arriba.
                modifier = Modifier.graphicsLayer { translationY = -abs(laugh(t.floatValue)) * 14f * density.density },
            )
            Spacer(Modifier.height(18.dp))
            val typed = (((t.floatValue - 1.9f) / 0.5f) * 7f).toInt().coerceIn(0, 7)
            Text(
                buildAnnotatedString {
                    append("FLACKER".take(typed).padEnd(7, ' '))
                    withStyle(SpanStyle(fontSize = 13.sp, color = BrandColors.Bone, baselineShift = BaselineShift(0.9f))) {
                        append(if (typed == 7) " 333" else "    ")
                    }
                },
                style = Display.copy(fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp, color = BrandColors.White),
            )
        }
    }
}
