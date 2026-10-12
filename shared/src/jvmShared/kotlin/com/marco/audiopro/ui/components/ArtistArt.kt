package com.marco.audiopro.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.data.ArtistPhoto
import com.marco.audiopro.data.CustomCovers
import com.marco.audiopro.ui.theme.Fonts
import kotlin.math.max

/**
 * Foto de un artista, siempre con algo chido: tu imagen propia, la de Navidrome si es real,
 * la de Deezer si Navidrome sólo tiene la silueta gris o, si nadie tiene, [GeneratedArtistArt].
 * @param serverUrl imagen grande de Navidrome (getArtistInfo) para la cabecera del artista.
 */
@Composable
fun ArtistImage(
    id: String,
    name: String,
    coverArt: String?,
    modifier: Modifier = Modifier,
    size: Int = 300,
    corner: Dp = 0.dp,
    serverUrl: String? = null,
) {
    val c = LocalApp.current
    c.covers.map.collectAsState().value
    val custom = c.covers.get(CustomCovers.artist(id))
    val photo = c.artistPhotos.known.collectAsState().value[id]
    if (custom == null) LaunchedEffect(id) { c.artistPhotos.resolve(id, name, coverArt) }
    Box(modifier.clip(RoundedCornerShape(corner)).background(Color(0xFF15161B))) {
        if (custom != null) {
            AsyncImage(custom, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else Crossfade(photo, animationSpec = tween(350), label = "artistPhoto") { p ->
            when (p) {
                null -> Box(Modifier.fillMaxSize())
                ArtistPhoto.Server -> {
                    val url = serverUrl?.takeIf { it.isNotBlank() }
                    if (url != null) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else CoverArt(coverArt ?: "ar-$id", Modifier.fillMaxSize(), size = size, corner = 0.dp)
                }
                is ArtistPhoto.Web -> AsyncImage(p.url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                ArtistPhoto.Generated -> GeneratedArtistArt(name, Modifier.fillMaxSize())
            }
        }
    }
}

/** Tonos de la portada generada: grises de la marca (uno por artista, elegido por su nombre). */
private val ArtPalettes = listOf(
    Color(0xFF8A8A92) to Color(0xFF1C1C20),
    Color(0xFF5A5A62) to Color(0xFF2A2A30),
    Color(0xFFB4B4BC) to Color(0xFF26262B),
    Color(0xFF6E6E78) to Color(0xFF3A3A42),
    Color(0xFF4A4A52) to Color(0xFF9A9AA2),
    Color(0xFFD0D0D6) to Color(0xFF3C3C44),
)

/**
 * Portada para artistas sin foto: dos luces grises sobre negro, la calavera de la marca como
 * marca de agua, anillos de disco muy tenues, un brillo diagonal y sus iniciales grandes.
 */
@Composable
fun GeneratedArtistArt(name: String, modifier: Modifier = Modifier) {
    val seed = remember(name) { name.lowercase().hashCode() }
    val (a, b) = ArtPalettes[Math.floorMod(seed, ArtPalettes.size)]
    val initials = remember(name) {
        name.split(Regex("[\\s\\-_/&,.]+")).mapNotNull { w -> w.firstOrNull { it.isLetterOrDigit() } }
            .take(2).joinToString("") { it.uppercase() }
            .ifEmpty { name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "♪" }
    }
    val tilt = (Math.floorMod(seed shr 8, 60) - 30) / 100f
    BoxWithConstraints(modifier.clipToBounds().background(Color(0xFF0B0C10)), contentAlignment = Alignment.Center) {
        val side = max(constraints.maxWidth, 1).toFloat()
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val r = max(w, h)
            val p1 = Offset(w * (0.18f + tilt * 0.3f), h * 0.12f)
            val p2 = Offset(w * (0.92f - tilt * 0.3f), h * 0.95f)
            drawCircle(Brush.radialGradient(listOf(a.copy(alpha = 0.95f), a.copy(alpha = 0f)), p1, r * 0.95f), r * 0.95f, p1)
            drawCircle(Brush.radialGradient(listOf(b.copy(alpha = 0.95f), b.copy(alpha = 0f)), p2, r * 0.9f), r * 0.9f, p2)
            val mid = Offset(w * 0.5f, h * 0.55f)
            drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), mid, r * 0.45f), r * 0.45f, mid)
            // Surcos de disco, casi invisibles.
            for (i in 1..9) drawCircle(Color.White.copy(alpha = 0.035f), r * 0.07f * i, Offset(w / 2, h / 2), style = Stroke(1f))
            // La calavera de la marca, como marca de agua.
            drawSkull(Offset(w * 0.78f, h * 0.74f), h * 0.62f, Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.04f), glow = false)
            // Brillo diagonal tipo vidrio.
            drawRect(
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0f), 0.42f to Color.White.copy(alpha = 0.10f), 0.5f to Color.White.copy(alpha = 0f),
                    start = Offset(0f, 0f), end = Offset(w, h),
                ),
            )
        }
        val fontSize = with(LocalDensity.current) { (side * if (initials.length > 1) 0.34f else 0.42f).toSp() }
        Text(
            initials,
            style = TextStyle(
                fontFamily = Fonts.inter, fontWeight = FontWeight.ExtraBold, fontSize = fontSize,
                letterSpacing = (-0.04f * fontSize.value).sp, color = Color.White,
            ),
        )
    }
}
