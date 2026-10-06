package com.marco.audiopro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Paleta "hi-fi de rack": negro profundo, ámbar de VU retroiluminado, verde/rojo de LED. */
object Hifi {
    val Bg = Color(0xFF0A0A0B)
    val Panel = Color(0xFF141416)
    val PanelHi = Color(0xFF1C1C1F)
    val Line = Color(0xFF2A2A2E)
    val Amber = Color(0xFFFFB000)
    val AmberDim = Color(0xFF8A5E00)
    val Cream = Color(0xFFF2E6CF)
    val Text = Color(0xFFEDEDED)
    val TextDim = Color(0xFF9A9AA0)
    val TextFaint = Color(0xFF5E5E66)
    val LedGreen = Color(0xFF3DDC84)
    val LedRed = Color(0xFFFF3B30)
    val Cyan = Color(0xFF4FD1FF)
}

private val scheme = darkColorScheme(
    primary = Hifi.Amber,
    onPrimary = Color.Black,
    primaryContainer = Hifi.AmberDim,
    onPrimaryContainer = Hifi.Cream,
    secondary = Hifi.Cream,
    onSecondary = Color.Black,
    background = Hifi.Bg,
    onBackground = Hifi.Text,
    surface = Hifi.Bg,
    onSurface = Hifi.Text,
    surfaceVariant = Hifi.Panel,
    onSurfaceVariant = Hifi.TextDim,
    surfaceContainer = Hifi.Panel,
    surfaceContainerHigh = Hifi.PanelHi,
    surfaceContainerHighest = Hifi.PanelHi,
    surfaceContainerLow = Hifi.Panel,
    surfaceContainerLowest = Hifi.Bg,
    outline = Hifi.Line,
    outlineVariant = Hifi.Line,
    error = Hifi.LedRed,
)

/** Texto de "pantalla LCD": monoespaciado, espaciado amplio. */
val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.2.sp)

private val typography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp),
    )
}

@Composable
fun MarcoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
