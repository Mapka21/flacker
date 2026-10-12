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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi

/** Nombre de la app. */
const val APP_NAME = "Flacker"

/** Degradado de marca (coral → magenta → violeta): fijo, no cambia con el acento. */
val BrandGradient = listOf(Color(0xFFFF7A45), Color(0xFFFF2D78), Color(0xFF7C4DFF))

/**
 * Ícono de la app: cuadro redondeado con el degradado de marca y una "F" hecha de tres barras
 * redondeadas, como las de un ecualizador.
 */
@Composable
fun FlackerMark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val corner = CornerRadius(s * 0.28f)
        drawRoundRect(Brush.linearGradient(BrandGradient, Offset.Zero, Offset(s, s)), cornerRadius = corner)
        // Brillo de vidrio arriba.
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), 0f, s * 0.55f),
            cornerRadius = corner,
        )
        val bar = s * 0.13f
        val r = CornerRadius(bar / 2)
        val x0 = s * 0.29f
        drawRoundRect(Color.White, Offset(x0, s * 0.22f), Size(bar, s * 0.56f), r)
        drawRoundRect(Color.White, Offset(x0, s * 0.22f), Size(s * 0.45f, bar), r)
        drawRoundRect(Color.White.copy(alpha = 0.92f), Offset(x0, s * 0.445f), Size(s * 0.33f, bar), r)
    }
}

/** Logo: el ícono y "Flacker" en Inter negrita, apretada. */
@Composable
fun FlackerLogo(modifier: Modifier = Modifier, size: TextUnit = 21.sp) {
    val mark = with(LocalDensity.current) { (size * 1.45f).toDp() }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        FlackerMark(mark)
        Spacer(Modifier.width(mark * 0.42f))
        Text(
            APP_NAME,
            style = Display.copy(fontSize = size, fontWeight = FontWeight.ExtraBold, letterSpacing = size * -0.03f, color = Hifi.Text),
        )
    }
}
