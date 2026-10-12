package com.marco.audiopro.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.platform.coilImageToImageBitmap
import com.marco.audiopro.platform.encodePng
import com.marco.audiopro.platform.rememberStoryShare
import com.marco.audiopro.platform.sharesToInstagram
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Fonts
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.viz.DuskPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

// ------------------------------------------------------------------ botón

/** Botón para compartir la canción en tu historia de Instagram (abre la vista previa). */
@Composable
fun ShareStoryButton(song: Song, modifier: Modifier = Modifier, size: Int = 20, tint: Color = Hifi.OnArt) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.size((size + 22).dp).bouncyClick { open = true }, contentAlignment = Alignment.Center) {
        Icon(Icons.Default.IosShare, "Compartir en historia", tint = tint, modifier = Modifier.size(size.dp))
    }
    if (open) ShareStoryDialog(song) { open = false }
}

// ------------------------------------------------------------------ diálogo

/** Estilos de la imagen para historias (1080×1920, como Spotify). */
enum class StoryStyle(val label: String) { AURA("Aura"), VINILO("Vinilo"), POSTER("Póster") }

/**
 * "Compartir en historia": arma la imagen con la portada, la canción y la calidad (para presumir
 * el FLAC 24/96), con tres estilos. En el celular la manda directo a historias de Instagram; en
 * la PC la copia y la guarda, o la manda a tu celular por Connect para subirla desde ahí.
 */
@Composable
fun ShareStoryDialog(song: Song, onDismiss: () -> Unit) {
    val c = LocalApp.current
    val ctx = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val share = rememberStoryShare()
    var style by remember { mutableStateOf(StoryStyle.AURA) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // La posición sólo cuenta si es lo que está sonando.
    val progress = remember(song.id) {
        val st = c.player.state.value
        if (st.current?.id == song.id && st.durationMs > 0) (c.player.positionMs() to st.durationMs) else null
    }
    val assets by produceState<StoryAssets?>(null, song.coverArt) {
        value = StoryAssets(loadStoryArt(ctx, c.api, song.coverArt), coverColors(ctx, c.api, song.coverArt)?.swatches)
    }
    val image by produceState<ImageBitmap?>(null, assets, style) {
        val a = assets ?: return@produceState
        value = withContext(Dispatchers.Default) { renderStory(measurer, StoryData(song, a.art, a.swatches, progress), style) }
    }
    val phones = c.connect.devices.collectAsState().value.filter { it.platform == "android" }
    val fileName = "Flacker - ${song.artist ?: ""} - ${song.title}"

    fun go(block: suspend (ImageBitmap) -> Unit) {
        val img = image ?: return
        if (busy) return
        busy = true
        scope.launch { runCatching { block(img) }.onFailure { status = "No se pudo: ${it.message}" }; busy = false }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 460.dp).fillMaxWidth()
                .shadow(30.dp, RoundedCornerShape(28.dp))
                .glass(RoundedCornerShape(28.dp), Color(0xFF15161C).copy(alpha = 0.98f))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Compartir en historia", style = Display.copy(fontSize = 22.sp))
            Text("Presume lo que estás escuchando", fontSize = 13.sp, color = Hifi.TextDim)
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.heightIn(max = 470.dp).aspectRatio(9f / 16f).shadow(24.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp)).background(Color(0xFF0B0C10)),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(image, animationSpec = tween(250), label = "story") { img ->
                    if (img == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Hifi.Accent, strokeWidth = 2.dp)
                    } else Image(img, "Vista previa de la historia", Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StoryStyle.entries.forEach { s -> GlassChip(s.label, style == s) { style = s } }
            }
            Spacer(Modifier.height(16.dp))
            if (sharesToInstagram) {
                StoryAction("Compartir en Instagram", Icons.Default.IosShare, primary = true, busy = busy) {
                    go { img -> status = share(withContext(Dispatchers.Default) { encodePng(img) }, fileName, true) }
                }
                Spacer(Modifier.height(8.dp))
                StoryAction("Otras apps", Icons.Default.IosShare, busy = busy) {
                    go { img -> status = share(withContext(Dispatchers.Default) { encodePng(img) }, fileName, false) }
                }
            } else {
                phones.forEach { d ->
                    StoryAction("Mandar a ${d.name}", Icons.Default.PhoneAndroid, primary = true, busy = busy) {
                        go { c.connect.sendStory(d, song); status = "Ábrela en ${d.name} y tócale a Compartir en Instagram" }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                StoryAction("Copiar y guardar imagen", Icons.Default.ContentCopy, primary = phones.isEmpty(), busy = busy) {
                    go { img -> status = withContext(Dispatchers.IO) { share(encodePng(img), fileName, true) } }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (phones.isEmpty()) "Instagram no deja subir historias desde la PC: abre Flacker en tu celular (misma red) para mandársela, o pega la imagen donde quieras."
                    else "Instagram no deja subir historias desde la PC: mándala a tu celular y súbela desde ahí.",
                    fontSize = 12.sp, color = Hifi.TextFaint, textAlign = TextAlign.Center,
                )
            }
            status?.let { Text(it, fontSize = 13.sp, color = Hifi.Text, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp)) }
        }
    }
}

/** Degradado de Instagram para el botón principal. */
private val InstaGradient = listOf(Color(0xFFF58529), Color(0xFFDD2A7B), Color(0xFF8134AF), Color(0xFF515BD4))

@Composable
private fun StoryAction(label: String, icon: ImageVector, primary: Boolean = false, busy: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(50))
            .then(if (primary) Modifier.background(Brush.horizontalGradient(InstaGradient)) else Modifier.background(Color.White.copy(alpha = 0.10f)))
            .bouncyClick(enabled = !busy, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------------------------------------------ la imagen

private class StoryAssets(val art: ImageBitmap?, val swatches: List<Color>?)

/** Lo que lleva la historia. @param progress (posición, duración) si es lo que está sonando. */
class StoryData(val song: Song, val art: ImageBitmap?, val swatches: List<Color>?, val progress: Pair<Long, Long>?)

suspend fun loadStoryArt(ctx: PlatformContext, api: SubsonicApi, coverArt: String?): ImageBitmap? {
    val url = api.coverUrl(coverArt, 1000) ?: return null
    val res = SingletonImageLoader.get(ctx).execute(ImageRequest.Builder(ctx).data(url).build()) as? SuccessResult ?: return null
    return runCatching { coilImageToImageBitmap(res.image) }.getOrNull()
}

private const val W = 1080f
private const val H = 1920f

/** Dibuja la historia de 1080×1920. */
fun renderStory(measurer: TextMeasurer, d: StoryData, style: StoryStyle): ImageBitmap {
    val img = ImageBitmap(W.toInt(), H.toInt())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(W, H)) {
        val sw = List(4) { i -> d.swatches?.getOrNull(i) ?: DuskPalette[i] }
        when (style) {
            StoryStyle.AURA -> aura(measurer, d, sw)
            StoryStyle.VINILO -> vinyl(measurer, d, sw)
            StoryStyle.POSTER -> poster(measurer, d, sw)
        }
    }
    return img
}

private val saturate = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.5f) })

/** La portada "derretida": se achica a unos pocos pixeles y se vuelve a agrandar suave. */
private fun DrawScope.meltedArt(art: ImageBitmap?, sw: List<Color>) {
    if (art == null) {
        drawRect(Brush.linearGradient(listOf(sw[0], sw[1], sw[3]), Offset.Zero, Offset(W, H)))
        return
    }
    val small = ImageBitmap(48, 48)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(small), Size(48f, 48f)) {
        drawImage(art, IntOffset.Zero, IntSize(art.width, art.height), IntOffset.Zero, IntSize(48, 48), filterQuality = FilterQuality.High)
    }
    val tiny = ImageBitmap(6, 10)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(tiny), Size(6f, 10f)) {
        drawImage(small, IntOffset(8, 0), IntSize(32, 48), IntOffset.Zero, IntSize(6, 10), filterQuality = FilterQuality.High)
    }
    drawImage(tiny, IntOffset.Zero, IntSize(6, 10), IntOffset.Zero, IntSize(W.toInt(), H.toInt()), filterQuality = FilterQuality.High, colorFilter = saturate)
}

private fun DrawScope.text(
    m: TextMeasurer, s: String, x: Float, y: Float, w: Float, size: Float,
    weight: FontWeight = FontWeight.Normal, color: Color = Color.White, lines: Int = 1,
    align: TextAlign = TextAlign.Start, spacing: Float = 0f,
): TextLayoutResult {
    val layout = m.measure(
        s,
        TextStyle(
            fontFamily = Fonts.inter, fontWeight = weight, fontSize = size.sp, color = color,
            textAlign = align, letterSpacing = spacing.sp, lineHeight = (size * 1.1f).sp,
        ),
        overflow = TextOverflow.Ellipsis, maxLines = lines, constraints = Constraints.fixedWidth(w.toInt()),
        density = this, layoutDirection = LayoutDirection.Ltr,
    )
    drawText(layout, topLeft = Offset(x, y))
    return layout
}

/** Portada con sombra suave y esquinas redondas. */
private fun DrawScope.cover(art: ImageBitmap?, x: Float, y: Float, s: Float, corner: Float, sw: List<Color>) {
    for (i in 14 downTo 1) {
        val g = i * 5f
        drawRoundRect(Color.Black.copy(alpha = 0.035f), Offset(x - g * 0.6f, y - g * 0.2f + 26f), Size(s + g * 1.2f, s + g * 1.2f), CornerRadius(corner + g))
    }
    val clip = Path().apply { addRoundRect(RoundRect(x, y, x + s, y + s, CornerRadius(corner))) }
    clipPath(clip) {
        if (art != null) drawImage(art, IntOffset.Zero, IntSize(art.width, art.height), IntOffset(x.toInt(), y.toInt()), IntSize(s.toInt(), s.toInt()), filterQuality = FilterQuality.High)
        else drawRect(Brush.linearGradient(listOf(sw[0], sw[1])), Offset(x, y), Size(s, s))
    }
    drawRoundRect(Color.White.copy(alpha = 0.12f), Offset(x, y), Size(s, s), CornerRadius(corner), style = Stroke(2f))
}

/** "Escuchando en Flacker" con el ícono. */
private fun DrawScope.header(m: TextMeasurer, x: Float, y: Float) {
    drawFlackerMark(Offset(x, y), 70f)
    text(m, "Escuchando en Flacker", x + 92f, y + 14f, 700f, 34f, FontWeight.SemiBold, Color.White.copy(alpha = 0.92f))
}

/** Pastilla de calidad: "FLAC · 24/96 · HI-RES" (dorada si es Hi-Res). */
private fun DrawScope.qualityPill(m: TextMeasurer, song: Song, cx: Float?, x: Float, y: Float) {
    if (song.suffix == null || song.isPreview) return
    val label = song.formatLabel + if (song.isHiRes) "  ·  HI-RES" else if (song.isLossless) "  ·  LOSSLESS" else ""
    val color = if (song.isHiRes) Hifi.HiResGold else Color.White
    val layout = m.measure(
        label, TextStyle(fontFamily = Fonts.inter, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 2.sp, color = color),
        density = this, layoutDirection = LayoutDirection.Ltr,
    )
    val pw = layout.size.width + 56f; val ph = layout.size.height + 26f
    val px = if (cx != null) cx - pw / 2 else x
    drawRoundRect(color.copy(alpha = 0.14f), Offset(px, y), Size(pw, ph), CornerRadius(ph / 2))
    drawRoundRect(color.copy(alpha = 0.45f), Offset(px, y), Size(pw, ph), CornerRadius(ph / 2), style = Stroke(2f))
    drawText(layout, topLeft = Offset(px + 28f, y + 13f))
}

/** Barra de avance (la posición real si está sonando). */
private fun DrawScope.progressBar(m: TextMeasurer, d: StoryData, x: Float, y: Float, w: Float) {
    val dur = d.progress?.second ?: (d.song.duration ?: 0) * 1000L
    if (dur <= 0) return
    val pos = d.progress?.first ?: (dur * 0.38f).toLong()
    val frac = (pos.toFloat() / dur).coerceIn(0f, 1f)
    drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(x, y), Size(w, 10f), CornerRadius(5f))
    drawRoundRect(Color.White, Offset(x, y), Size(w * frac, 10f), CornerRadius(5f))
    drawCircle(Color.White, 15f, Offset(x + w * frac, y + 5f))
    text(m, formatMs(pos), x, y + 30f, 300f, 28f, FontWeight.SemiBold, Color.White.copy(alpha = 0.7f))
    text(m, "-" + formatMs(dur - pos), x + w - 300f, y + 30f, 300f, 28f, FontWeight.SemiBold, Color.White.copy(alpha = 0.7f), align = TextAlign.End)
}

private fun subtitle(song: Song) = listOfNotNull(song.album?.takeIf { it.isNotBlank() }, song.year?.toString()).joinToString(" · ")

private fun DrawScope.aura(m: TextMeasurer, d: StoryData, sw: List<Color>) {
    meltedArt(d.art, sw)
    drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.25f to Color.Black.copy(alpha = 0.05f), 0.6f to Color.Black.copy(alpha = 0.25f), 1f to Color.Black.copy(alpha = 0.7f)))
    drawCircle(Brush.radialGradient(listOf(sw[0].copy(alpha = 0.35f), Color.Transparent), Offset(W * 0.2f, H * 0.15f), 700f), 700f, Offset(W * 0.2f, H * 0.15f))
    header(m, 108f, 130f)
    val s = 864f
    cover(d.art, (W - s) / 2, 300f, s, 46f, sw)
    var y = 300f + s + 74f
    y += text(m, d.song.title, 108f, y, 864f, 76f, FontWeight.ExtraBold, lines = 2, align = TextAlign.Center, spacing = -1.5f).size.height + 10f
    y += text(m, d.song.artist ?: "", 108f, y, 864f, 44f, FontWeight.SemiBold, Color.White.copy(alpha = 0.85f), align = TextAlign.Center).size.height + 6f
    subtitle(d.song).takeIf { it.isNotEmpty() }?.let { y += text(m, it, 108f, y, 864f, 32f, FontWeight.Medium, Color.White.copy(alpha = 0.55f), align = TextAlign.Center).size.height }
    qualityPill(m, d.song, W / 2, 0f, max(y + 34f, 1560f).coerceAtMost(1620f))
    progressBar(m, d, 108f, 1720f, 864f)
}

private fun DrawScope.vinyl(m: TextMeasurer, d: StoryData, sw: List<Color>) {
    drawRect(Brush.verticalGradient(listOf(lerp(sw[0], Color.Black, 0.45f), lerp(sw[1], Color.Black, 0.8f), Color.Black)))
    val disc = Offset(700f, 800f)
    drawCircle(Brush.radialGradient(listOf(sw[2].copy(alpha = 0.45f), Color.Transparent), disc, 620f), 620f, disc)
    header(m, 96f, 130f)
    // El disco que se asoma detrás de la portada.
    val r = 410f
    drawCircle(Color(0xFF0A0A0C), r, disc)
    for (i in 0 until 26) drawCircle(Color.White.copy(alpha = if (i % 3 == 0) 0.07f else 0.035f), r - 18f - i * 9.5f, disc, style = Stroke(1.5f))
    drawCircle(
        Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent, Color.Transparent, Color.White.copy(alpha = 0.08f), Color.Transparent), disc),
        r, disc,
    )
    val label = 150f
    clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(disc, label)) }) {
        if (d.art != null) drawImage(d.art, IntOffset.Zero, IntSize(d.art.width, d.art.height), IntOffset((disc.x - label).toInt(), (disc.y - label).toInt()), IntSize((label * 2).toInt(), (label * 2).toInt()), filterQuality = FilterQuality.High)
        else drawCircle(sw[0], label, disc)
    }
    drawCircle(Color.Black, 14f, disc)
    cover(d.art, 96f, 420f, 680f, 28f, sw)
    var y = 1290f
    y += text(m, d.song.title, 96f, y, 888f, 80f, FontWeight.ExtraBold, lines = 2, spacing = -1.5f).size.height + 10f
    y += text(m, d.song.artist ?: "", 96f, y, 888f, 46f, FontWeight.SemiBold, Color.White.copy(alpha = 0.85f)).size.height + 6f
    subtitle(d.song).takeIf { it.isNotEmpty() }?.let { y += text(m, it, 96f, y, 888f, 32f, FontWeight.Medium, Color.White.copy(alpha = 0.55f)).size.height }
    qualityPill(m, d.song, null, 96f, min(y + 34f, 1620f))
    progressBar(m, d, 96f, 1730f, 888f)
}

private fun DrawScope.poster(m: TextMeasurer, d: StoryData, sw: List<Color>) {
    val base = lerp(sw[0], Color.Black, 0.62f)
    drawRect(base)
    if (d.art != null) drawImage(d.art, IntOffset.Zero, IntSize(d.art.width, d.art.height), IntOffset.Zero, IntSize(W.toInt(), W.toInt()), filterQuality = FilterQuality.High)
    else drawRect(Brush.linearGradient(listOf(sw[0], sw[1])), size = Size(W, W))
    // La portada se funde con el color de abajo.
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, base), startY = W - 320f, endY = W), Offset(0f, W - 320f), Size(W, 321f))
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), 0f, 260f), size = Size(W, 260f))
    var y = 1110f
    y += text(m, d.song.title, 80f, y, 920f, 100f, FontWeight.ExtraBold, lines = 3, spacing = -2.5f).size.height + 14f
    y += text(m, d.song.artist ?: "", 80f, y, 920f, 50f, FontWeight.Bold, lerp(sw[0], Color.White, 0.55f)).size.height + 6f
    subtitle(d.song).takeIf { it.isNotEmpty() }?.let { y += text(m, it, 80f, y, 920f, 34f, FontWeight.Medium, Color.White.copy(alpha = 0.6f)).size.height }
    qualityPill(m, d.song, null, 80f, min(y + 36f, 1600f))
    drawRect(Color.White.copy(alpha = 0.15f), Offset(80f, 1730f), Size(920f, 2f))
    drawFlackerMark(Offset(80f, 1770f), 60f)
    text(m, "Flacker", 158f, 1778f, 400f, 38f, FontWeight.ExtraBold, spacing = -0.8f)
    text(m, "escuchando ahora", 580f, 1784f, 420f, 30f, FontWeight.Medium, Color.White.copy(alpha = 0.6f), align = TextAlign.End)
}
