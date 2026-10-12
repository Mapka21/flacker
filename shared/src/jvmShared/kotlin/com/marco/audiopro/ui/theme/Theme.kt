package com.marco.audiopro.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.marco.audiopro.resources.Res
import com.marco.audiopro.resources.inter
import com.marco.audiopro.resources.jetbrainsmono
import com.marco.audiopro.resources.spacegrotesk
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource

enum class AccentTheme(val label: String, val color: Color?) {
    PLATA("Plata", Color(0xFFE6E6EA)),
    AMBER("Ámbar", Color(0xFFFFB000)),
    APPLE("Rojo", Color(0xFFFA2D48)),
    CYAN("Cian", Color(0xFF3DD9EB)),
    ROSE("Rosa", Color(0xFFFF4D8D)),
    PHOSPHOR("Menta", Color(0xFF3CF28E)),
    VIOLET("Violeta", Color(0xFFA48BFF)),
    DYNAMIC("De la portada", null),
}

/**
 * Paleta: grafito casi negro con un toque azul y un acento intercambiable.
 * El acento es estado observable: al cambiarlo se recompone toda la UI.
 */
object Hifi {
    var Accent by mutableStateOf(Color(0xFFE6E6EA))

    /** Fondo de chips/botones seleccionados. */
    val AccentDim: Color get() = lerp(PanelHi, Accent, 0.38f)
    /** Blanco tintado con el acento, para valores destacados. */
    val Cream: Color get() = lerp(Accent, Color.White, 0.72f)

    val Bg = Color(0xFF090A0D)
    val Panel = Color(0xFF121419)
    val PanelHi = Color(0xFF1A1D24)
    val Line = Color(0xFF262A33)
    val Text = Color(0xFFECEEF2)
    val TextDim = Color(0xFF9AA0AC)
    val TextFaint = Color(0xFF5B616E)
    val HiResGold = Color(0xFFFFC94D)
    val LedGreen = Color(0xFF3DDC84)
    val LedRed = Color(0xFFFF3B30)
    val Cyan = Color(0xFF4FD1FF)

    // Vidrio (estilo Apple): blanco muy translúcido sobre el fondo vivo de la portada.
    val Glass = Color.White.copy(alpha = 0.07f)
    val GlassHi = Color.White.copy(alpha = 0.12f)
    val GlassStrokeTop = Color.White.copy(alpha = 0.16f)
    val GlassStrokeBottom = Color.White.copy(alpha = 0.04f)
    /** Texto secundario sobre fondos de color (como el gris de Apple Music). */
    val OnArt = Color.White.copy(alpha = 0.72f)
}

/** Familias tipográficas; se cargan al entrar a [MarcoTheme] (los recursos son @Composable). */
object Fonts {
    var grotesk: FontFamily = FontFamily.SansSerif
    var inter: FontFamily = FontFamily.SansSerif
    var mono: FontFamily = FontFamily.Monospace
}

@Composable
private fun variable(res: FontResource, vararg weights: Int) = FontFamily(
    weights.map { w -> Font(res, FontWeight(w), FontStyle.Normal, FontVariation.Settings(FontVariation.weight(w))) },
)

/**
 * Etiquetas chicas y cifras (duraciones, formatos, rótulos en mayúsculas): Inter seminegrita con
 * números tabulares, como los rótulos de Apple Music. (Antes era monoespaciada estilo terminal.)
 */
val Mono: TextStyle get() = TextStyle(
    fontFamily = Fonts.inter, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp,
    fontFeatureSettings = "tnum",
)

/** Titulares grandes estilo iOS ("Large Title"): Inter pesada y apretada, cercana a SF Pro. */
val Display: TextStyle get() = TextStyle(fontFamily = Fonts.inter, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.7).sp)

private fun typography() = Typography().run {
    fun TextStyle.inter() = copy(fontFamily = Fonts.inter)
    fun TextStyle.grotesk() = copy(fontFamily = Fonts.inter, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
    copy(
        displayLarge = displayLarge.grotesk(),
        displayMedium = displayMedium.grotesk(),
        displaySmall = displaySmall.grotesk(),
        headlineLarge = headlineLarge.grotesk(),
        headlineMedium = headlineMedium.grotesk(),
        headlineSmall = headlineSmall.grotesk(),
        titleLarge = titleLarge.grotesk(),
        titleMedium = titleMedium.inter().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.inter().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.inter(),
        bodyMedium = bodyMedium.inter(),
        bodySmall = bodySmall.inter(),
        labelLarge = labelLarge.inter().copy(fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.inter(),
        labelSmall = labelSmall.copy(fontFamily = Fonts.inter, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
    )
}

@Composable
/** @param background false para ventanas transparentes (miniplayer de PC). */
fun MarcoTheme(background: Boolean = true, content: @Composable () -> Unit) {
    Fonts.grotesk = variable(Res.font.spacegrotesk, 400, 500, 600, 700)
    Fonts.inter = variable(Res.font.inter, 400, 500, 600, 700, 800)
    Fonts.mono = variable(Res.font.jetbrainsmono, 400, 500, 700)
    val typography = remember(Fonts.grotesk, Fonts.inter, Fonts.mono) { typography() }
    val accent = Hifi.Accent
    val scheme = remember(accent) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.Black,
            primaryContainer = Hifi.AccentDim,
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
    }
    MaterialTheme(colorScheme = scheme, typography = typography) {
        // Fondo explícito: en PC la ventana no trae el fondo oscuro del tema de Android.
        // Color de texto por defecto (fuera de un Scaffold sería negro).
        CompositionLocalProvider(LocalContentColor provides Hifi.Text) {
            if (background) Box(Modifier.fillMaxSize().background(Hifi.Bg)) { content() } else content()
        }
    }
}
