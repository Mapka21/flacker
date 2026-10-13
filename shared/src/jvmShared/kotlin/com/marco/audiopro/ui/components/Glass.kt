package com.marco.audiopro.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.animation.Crossfade
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import com.marco.audiopro.platform.supportsBlur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.LocalPlatformContext
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.viz.DuskPalette
import com.marco.audiopro.ui.viz.LiveAudio
import com.marco.audiopro.ui.viz.VisualPreset
import com.marco.audiopro.ui.viz.VisualScene

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

/** Play ↔ pausa con un giro y cambio de escala (en vez de saltar de un ícono a otro). */
@Composable
fun PlayPauseIcon(playing: Boolean, tint: Color, size: androidx.compose.ui.unit.Dp) {
    androidx.compose.animation.AnimatedContent(
        playing,
        transitionSpec = {
            (androidx.compose.animation.fadeIn(tween(180)) + androidx.compose.animation.scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f), initialScale = 0.4f)) togetherWith
                (androidx.compose.animation.fadeOut(tween(120)) + androidx.compose.animation.scaleOut(tween(120), targetScale = 0.4f))
        },
        label = "playPause",
    ) { p ->
        androidx.compose.material3.Icon(
            if (p) androidx.compose.material.icons.Icons.Default.Pause else androidx.compose.material.icons.Icons.Default.PlayArrow,
            if (p) "Pausa" else "Reproducir", tint = tint, modifier = Modifier.size(size),
        )
    }
}

/** Resaltado de PC al pasar el mouse (y fijo si está elegido), con transición suave. */
fun Modifier.hoverHighlight(selected: Boolean = false, shape: Shape = RoundedCornerShape(10.dp)): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val bg by animateColorAsState(
        when { selected -> Color.White.copy(alpha = 0.10f); hovered -> Color.White.copy(alpha = 0.055f); else -> Color.Transparent },
        tween(160), label = "hover",
    )
    clip(shape).background(bg).hoverable(source)
}

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
 * Fondo "vivo" como el de Apple Music, que además es el visualizador. Tres capas:
 * la portada derretida (muy difuminada), el brillo de las figuras del preset (difuminado
 * suave) y las figuras nítidas con un velo para leer el texto.
 * @param intensity 1 = reproductor a pantalla completa; menos = fondo más oscuro para leer.
 * @param preset fuerza un preset (vistas previas del selector).
 */
@Composable
fun AmbientBackground(coverArt: String?, modifier: Modifier = Modifier, intensity: Float = 1f, preset: VisualPreset? = null) {
    val c = LocalApp.current
    val ctx = LocalPlatformContext.current
    val prefs by c.settings.prefs.collectAsState()
    val playing by rememberUpdatedState(c.player.state.collectAsState().value.isPlaying)
    val reactive by rememberUpdatedState(preset != null || prefs.visualMode)
    // Apagado = la portada fluyendo tranquila, sin reaccionar a la música.
    val mode = preset ?: if (prefs.visualMode) prefs.visualPreset else VisualPreset.PORTADA
    val offset by rememberUpdatedState(prefs.visualOffsetMs)

    var target by remember { mutableStateOf(DuskPalette) }
    var art by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(coverArt) {
        coverColors(ctx, c.api, coverArt)?.let { target = it.swatches }
        art = if (coverArt == null) null else coverBitmap(ctx, c.api, coverArt) ?: art
    }
    val c0 by animateColorAsState(target[0], tween(1400), label = "a0")
    val c1 by animateColorAsState(target[1], tween(1400), label = "a1")
    val c2 by animateColorAsState(target[2], tween(1400), label = "a2")
    val c3 by animateColorAsState(target[3], tween(1400), label = "a3")

    val scene = remember { VisualScene() }
    scene.measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val tick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { t ->
            val dt = if (last == 0L) 0.016f else ((t - last) / 1e9f).coerceIn(0f, 0.1f)
            last = t
            if (reactive) LiveAudio.update(c.engine.tap, t, playing, offset)
            scene.step(dt, if (reactive) LiveAudio.features else LiveAudio.quiet)
            tick.longValue = t
            if (FpsLog.on) FpsLog.frame(t, mode.name)
        }
    }
    val feats = { if (reactive) LiveAudio.features else LiveAudio.quiet }
    val cols = listOf(c0, c1, c2, c3)
    BoxWithConstraints(modifier.fillMaxSize().background(Hifi.Bg)) {
        // Difuminado proporcional al tamaño (en las vistas previas chicas también se ve igual).
        val big = (minOf(maxWidth, maxHeight) * 0.16f).coerceIn(14.dp, 90.dp)
        // Sin blur (Android < 12) la portada se vería tal cual: ahí van las manchas de color.
        val useArt = if (supportsBlur) art else null
        scene.art = art
        // En ASCII el fondo es liso: no vale la pena difuminarlo cada frame.
        if (mode != VisualPreset.ASCII) Crossfade(useArt, animationSpec = tween(1200), label = "art") { img ->
            Canvas(Modifier.fillMaxSize().blur(big, BlurredEdgeTreatment.Rectangle)) {
                tick.longValue
                scene.drawBase(this, mode, cols, img, feats())
            }
        }
        if (scene.hasFx(mode)) {
            // Brillo (bloom) de las figuras y luego las figuras nítidas.
            // (Las olas son rellenos grandes: su brillo deja manchas, así que van sin él.)
            if (supportsBlur && mode != VisualPreset.OLAS && mode != VisualPreset.ASCII) Canvas(Modifier.fillMaxSize().blur(big / 5, BlurredEdgeTreatment.Unbounded).graphicsLayer { alpha = 0.9f }) {
                tick.longValue
                scene.drawFx(this, mode, cols, feats(), glow = true)
            }
            Canvas(Modifier.fillMaxSize()) {
                tick.longValue
                scene.drawFx(this, mode, cols, feats(), glow = false)
            }
        }
        Canvas(Modifier.fillMaxSize()) { scene.drawVeil(this, intensity) }
    }
}

// ------------------------------------------------------------------ controles de vidrio

/** Píldora de filtro estilo iOS: blanca cuando está elegida, vidrio cuando no. */
@Composable
fun GlassChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.08f), label = "chip")
    Box(
        modifier.clip(RoundedCornerShape(50)).background(bg)
            .border(1.dp, Color.White.copy(alpha = if (selected) 0f else 0.10f), RoundedCornerShape(50))
            .bouncyClick(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.Black else Hifi.TextDim, maxLines = 1)
    }
}

/** Control segmentado de Apple: cápsula de vidrio con una pastilla que se desliza. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier.height(38.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50)).padding(3.dp),
    ) {
        val seg = maxWidth / options.size
        val x by animateDpAsState(seg * selected, spring(dampingRatio = 0.75f, stiffness = 420f), label = "seg")
        Box(
            Modifier.offset(x = x).width(seg).fillMaxHeight().shadow(6.dp, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.22f)),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                val sel = i == selected
                val col by animateColorAsState(if (sel) Color.White else Hifi.TextDim, label = "segText")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50))
                        .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) { Text(label, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium, color = col, maxLines = 1) }
            }
        }
    }
}

/** Tarjeta de vidrio para agrupar ajustes (reemplaza a los paneles grises opacos). */
fun Modifier.glassCard(): Modifier = this.glass(RoundedCornerShape(20.dp), Color.Black.copy(alpha = 0.22f))

/** -Dflacker.debugFps=true: imprime los cuadros por segundo del fondo cada 2 s (para cazar lentitud). */
private object FpsLog {
    val on = runCatching { System.getProperty("flacker.debugFps") == "true" }.getOrDefault(false)
    private var start = 0L
    private var frames = 0
    fun frame(t: Long, what: String) {
        if (start == 0L) start = t
        frames++
        if (t - start >= 2_000_000_000L) {
            println("FPS $what: %.1f".format(frames * 1e9 / (t - start)))
            start = t; frames = 0
        }
    }
}
