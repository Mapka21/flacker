package com.marco.audiopro.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Mosaico de hasta 4 portadas (si hay menos, la primera llena todo). */
@Composable
fun CoverCollage(covers: List<String>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.background(BrandColors.Graphite)) {
        val half = maxWidth / 2
        when {
            covers.size >= 4 -> Column {
                Row { CoverArt(covers[0], Modifier.size(half), size = 300, corner = 0.dp); CoverArt(covers[1], Modifier.size(half), size = 300, corner = 0.dp) }
                Row { CoverArt(covers[2], Modifier.size(half), size = 300, corner = 0.dp); CoverArt(covers[3], Modifier.size(half), size = 300, corner = 0.dp) }
            }
            covers.isNotEmpty() -> CoverArt(covers[0], Modifier.fillMaxSize(), size = 600, corner = 0.dp)
        }
    }
}

/**
 * Tarjeta con portadas (géneros, mixes): mosaico, degradado negro abajo, título grande y un
 * renglón chico. Monocroma como la marca: el color lo ponen las portadas.
 */
@Composable
fun ArtTile(title: String, subtitle: String?, covers: List<String>, modifier: Modifier = Modifier, kicker: String? = null, onClick: () -> Unit) {
    Box(
        modifier.aspectRatio(1f).shadow(14.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp)).bouncyClick(onClick = onClick),
    ) {
        if (covers.isEmpty()) GlyphArt(title, Modifier.fillMaxSize()) else CoverCollage(covers, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Black.copy(alpha = 0.15f), 1f to Color.Black.copy(alpha = 0.88f))))
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            kicker?.let { Text(it.uppercase(), style = Mono.copy(color = Color.White.copy(alpha = 0.65f), fontSize = 9.sp, letterSpacing = 1.2.sp)) }
            Text(title, style = Display.copy(fontSize = 20.sp, color = Color.White, lineHeight = 22.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, fontSize = 12.sp, color = Color.White.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/**
 * Arte generado (sin portadas), en grises: anillos, ondas o rayos según el nombre, siempre igual
 * para el mismo nombre. Para nichos de radio y géneros vacíos.
 */
@Composable
fun GlyphArt(seed: String, modifier: Modifier = Modifier) {
    val h = seed.hashCode().absoluteValue
    Canvas(modifier.background(Brush.linearGradient(listOf(Color(0xFF1C1C21), Color(0xFF0A0A0C))))) {
        val r = Random(h)
        val s = size.minDimension
        val c = Offset(size.width * (0.55f + r.nextFloat() * 0.35f), size.height * (0.15f + r.nextFloat() * 0.4f))
        val ink = Color.White
        when (h % 4) {
            0 -> repeat(9) { i -> drawCircle(ink.copy(alpha = 0.04f + i * 0.012f), s * (0.08f + i * 0.09f), c, style = Stroke(1.2f + i * 0.25f)) }
            1 -> repeat(14) { i ->
                val y = size.height * (0.1f + i * 0.06f)
                val ph = r.nextFloat() * 6f
                var prev = Offset(0f, y)
                for (x in 1..40) {
                    val px = size.width * x / 40f
                    val p = Offset(px, y + sin(px / s * 9f + ph) * s * 0.035f * (1f + i * 0.08f))
                    drawLine(ink.copy(alpha = 0.05f + i * 0.008f), prev, p, 1.4f); prev = p
                }
            }
            2 -> repeat(28) { i ->
                val a = i / 28f * 2f * PI.toFloat()
                drawLine(ink.copy(alpha = 0.05f + (i % 3) * 0.03f), c, c + Offset(cos(a), sin(a)) * s * (0.5f + r.nextFloat() * 0.6f), 1.2f)
            }
            else -> repeat(70) {
                val p = Offset(r.nextFloat() * size.width, r.nextFloat() * size.height * 0.75f)
                drawCircle(ink.copy(alpha = 0.06f + r.nextFloat() * 0.16f), 1f + r.nextFloat() * s * 0.012f, p)
            }
        }
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), c, s * 0.5f), s * 0.5f, c)
    }
}

/** Pastilla "EN VIVO" con un punto que late (radio). */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    val animating by rememberAnimating()
    val a = if (!animating) 1f else rememberInfiniteTransition(label = "live")
        .animateFloat(0.35f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "a").value
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFFF3B3B).copy(alpha = a)))
        Spacer(Modifier.width(6.dp))
        Text("EN VIVO", style = Mono.copy(color = Color.White, fontSize = 9.sp, letterSpacing = 1.sp))
    }
}

/** Barritas de ecualizador que bailan (lo que está sonando). */
@Composable
fun EqBars(modifier: Modifier = Modifier, color: Color = Hifi.Accent, bars: Int = 4, height: Dp = 14.dp) {
    val animating by rememberAnimating()
    val ph = if (!animating) 0f else rememberInfiniteTransition(label = "eq")
        .animateFloat(0f, 2f * PI.toFloat(), infiniteRepeatable(tween(900, easing = LinearEasing)), label = "ph").value
    Canvas(modifier.size(width = (bars * 4).dp, height = height)) {
        val w = size.width / (bars * 2 - 1)
        repeat(bars) { i ->
            val v = 0.25f + 0.75f * (0.5f + 0.5f * sin(ph * (1f + i * 0.37f) + i * 1.7f))
            drawRect(color, Offset(i * 2 * w, size.height * (1 - v)), androidx.compose.ui.geometry.Size(w, size.height * v))
        }
    }
}

/** Encabezado de sección: título y, a la derecha, una acción opcional ("Ver todo"). */
@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Display.copy(fontSize = 21.sp), modifier = Modifier.weight(1f))
        if (action != null && onAction != null) Text(
            action, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Hifi.TextDim,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).bouncyClick(onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** Logo de una estación: su ícono o, si no tiene, sus iniciales sobre arte generado. */
@Composable
fun StationLogo(name: String, url: String?, modifier: Modifier = Modifier, corner: Dp = 12.dp) {
    Box(modifier.clip(RoundedCornerShape(corner)), contentAlignment = Alignment.Center) {
        GlyphArt(name, Modifier.fillMaxSize())
        Text(
            name.split(' ', '-', '.').filter { it.isNotBlank() && it[0].isLetterOrDigit() }.take(2).joinToString("") { it.take(1).uppercase() },
            style = Display.copy(fontSize = 18.sp, color = Color.White.copy(alpha = 0.85f)),
        )
        if (url != null) coil3.compose.AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    }
}
