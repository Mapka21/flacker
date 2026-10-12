package com.marco.audiopro.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.graphics.luminance
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

/** Estilos de la imagen con la portada (1080×1920). */
enum class StoryStyle(val label: String) { AURA("Aura"), VINILO("Vinilo"), POSTER("Póster") }

/** Estilos de la tarjeta de letra (como la de Spotify). */
enum class LyricStyle(val label: String) { COLOR("Color"), NEGRO("Negro") }

/** Como mucho tantos versos en una tarjeta de letra (y no más de ~230 letras). */
private const val MAX_LINES = 5
private const val MAX_CHARS = 230

/**
 * "Compartir en historia", dos modos:
 * - **Portada**: la portada, la canción y la calidad (para presumir el FLAC 24/96) en tres estilos.
 * - **Letra**: eliges uno o varios versos seguidos (tocándolos, como en Spotify) y sale la tarjeta
 *   con la letra en grande.
 * En el celular va directo a historias de Instagram; en la PC se copia y se guarda, o se manda a tu
 * celular por Connect para subirla desde ahí.
 */
@Composable
fun ShareStoryDialog(song: Song, onDismiss: () -> Unit) {
    val c = LocalApp.current
    val ctx = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val share = rememberStoryShare()
    var lyricMode by remember { mutableStateOf(false) }
    var style by remember { mutableStateOf(StoryStyle.AURA) }
    var lyricStyle by remember { mutableStateOf(LyricStyle.COLOR) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val assets by produceState<StoryAssets?>(null, song.coverArt) {
        value = StoryAssets(loadStoryArt(ctx, c.api, song.coverArt), coverColors(ctx, c.api, song.coverArt)?.swatches)
    }
    // La letra (sin líneas vacías) y, si está sonando, el verso de ahorita para empezar ahí.
    var startLine by remember(song.id) { mutableStateOf(0) }
    val lyrics by produceState<List<String>?>(null, song.id) {
        val l = runCatching { c.lyrics.get(song) }.getOrNull()
        val lines = l?.line?.filter { it.value.isNotBlank() }.orEmpty()
        // Si es lo que está sonando y la letra es sincronizada, se empieza en el verso de ahorita.
        if (l?.synced == true && c.player.state.value.current?.id == song.id) {
            val pos = c.player.positionMs() + (c.lyrics.offsets.value[song.id] ?: 0L)
            startLine = lines.indexOfLast { (it.start ?: 0) <= pos }.coerceAtLeast(0)
        }
        value = lines.map { it.value.trim() }
    }
    var selection by remember(song.id) { mutableStateOf<IntRange?>(null) }
    val sel = selection ?: lyrics?.takeIf { it.isNotEmpty() }?.let { startLine..startLine }
    val chosen = sel?.let { r -> lyrics?.slice(r) }.orEmpty()

    val image by produceState<ImageBitmap?>(null, assets, style, lyricMode, lyricStyle, chosen) {
        val a = assets ?: return@produceState
        val data = StoryData(song, a.art, a.swatches)
        value = withContext(Dispatchers.Default) {
            if (lyricMode && chosen.isNotEmpty()) renderLyricStory(measurer, data, chosen, lyricStyle)
            else if (lyricMode) null
            else renderStory(measurer, data, style)
        }
    }
    val phones = c.connect.devices.collectAsState().value.filter { it.platform == "android" }
    val fileName = "Flacker - ${song.artist ?: ""} - ${song.title}"

    fun go(block: suspend (ImageBitmap) -> Unit) {
        val img = image ?: return
        if (busy) return
        busy = true
        scope.launch { runCatching { block(img) }.onFailure { status = "No se pudo: ${it.message}" }; busy = false }
    }

    /** Tocar un verso: si está pegado a la selección la agranda; si no, empieza una nueva. */
    fun tap(i: Int) {
        val l = lyrics ?: return
        val r = sel
        selection = when {
            r == null -> i..i
            i == r.first && r.first < r.last -> (r.first + 1)..r.last
            i == r.last && r.first < r.last -> r.first until r.last
            i == r.last + 1 && r.count() < MAX_LINES && l.slice(r.first..i).sumOf { it.length } <= MAX_CHARS -> r.first..i
            i == r.first - 1 && r.count() < MAX_LINES && l.slice(i..r.last).sumOf { it.length } <= MAX_CHARS -> i..r.last
            else -> i..i
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).widthIn(max = if (lyricMode) 760.dp else 460.dp).fillMaxWidth()
                .shadow(30.dp, RoundedCornerShape(28.dp))
                .glass(RoundedCornerShape(28.dp), Color(0xFF111114).copy(alpha = 0.98f))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Compartir en historia", style = Display.copy(fontSize = 22.sp))
            Text("Presume lo que estás escuchando", fontSize = 13.sp, color = Hifi.TextDim)
            Spacer(Modifier.height(12.dp))
            SegmentedControl(listOf("Portada", "Letra"), if (lyricMode) 1 else 0, { lyricMode = it == 1 }, Modifier.widthIn(max = 300.dp).fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier.heightIn(max = 470.dp).aspectRatio(9f / 16f).shadow(24.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp)).background(Color(0xFF0B0C10)),
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(image, animationSpec = tween(220), label = "story") { img ->
                        when {
                            img != null -> Image(img, "Vista previa de la historia", Modifier.fillMaxSize())
                            lyricMode && lyrics?.isEmpty() == true -> Text(
                                "Esta canción no tiene letra", color = Hifi.TextDim, fontSize = 13.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp),
                            )
                            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Hifi.Accent, strokeWidth = 2.dp)
                            }
                        }
                    }
                }
                // Selector de versos (sólo en modo Letra).
                if (lyricMode && !lyrics.isNullOrEmpty()) {
                    Column(Modifier.weight(1f).heightIn(max = 470.dp)) {
                        Text("Toca los versos (hasta $MAX_LINES seguidos)", fontSize = 12.sp, color = Hifi.TextFaint)
                        Spacer(Modifier.height(6.dp))
                        androidx.compose.foundation.lazy.LazyColumn(
                            state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (startLine - 2).coerceAtLeast(0)),
                        ) {
                            items(lyrics!!.size) { i ->
                                val on = sel?.contains(i) == true
                                Text(
                                    lyrics!![i], fontSize = 14.sp, lineHeight = 18.sp,
                                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                    color = if (on) Color.Black else Hifi.TextDim,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp))
                                        .background(if (on) Color.White else Color.Transparent)
                                        .bouncyClick { tap(i) }.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            AnimatedContent(lyricMode, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "chips") { lm ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (lm) LyricStyle.entries.forEach { s -> GlassChip(s.label, lyricStyle == s) { lyricStyle = s } }
                    else StoryStyle.entries.forEach { s -> GlassChip(s.label, style == s) { style = s } }
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
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
}

@Composable
private fun StoryAction(label: String, icon: ImageVector, primary: Boolean = false, busy: Boolean, onClick: () -> Unit) {
    val fg = if (primary) Color.Black else Color.White
    Row(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(50))
            .background(if (primary) Color.White else Color.White.copy(alpha = 0.10f))
            .bouncyClick(enabled = !busy, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------------------------------------------ la imagen

private class StoryAssets(val art: ImageBitmap?, val swatches: List<Color>?)

/** Lo que lleva la historia. */
class StoryData(val song: Song, val art: ImageBitmap?, val swatches: List<Color>?)

suspend fun loadStoryArt(ctx: PlatformContext, api: SubsonicApi, coverArt: String?): ImageBitmap? {
    val url = api.coverUrl(coverArt, 1000) ?: return null
    val res = SingletonImageLoader.get(ctx).execute(ImageRequest.Builder(ctx).data(url).build()) as? SuccessResult ?: return null
    return runCatching { coilImageToImageBitmap(res.image) }.getOrNull()
}

private const val W = 1080f
private const val H = 1920f

private inline fun story(block: DrawScope.() -> Unit): ImageBitmap {
    val img = ImageBitmap(W.toInt(), H.toInt())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(W, H), block)
    return img
}

private fun StoryData.palette() = List(4) { i -> swatches?.getOrNull(i) ?: DuskPalette[i] }

/** Dibuja la historia de portada (1080×1920). */
fun renderStory(measurer: TextMeasurer, d: StoryData, style: StoryStyle): ImageBitmap = story {
    val sw = d.palette()
    when (style) {
        StoryStyle.AURA -> aura(measurer, d, sw)
        StoryStyle.VINILO -> vinyl(measurer, d, sw)
        StoryStyle.POSTER -> poster(measurer, d, sw)
    }
}

/** Dibuja la tarjeta con versos de la letra (1080×1920). */
fun renderLyricStory(measurer: TextMeasurer, d: StoryData, lines: List<String>, style: LyricStyle): ImageBitmap = story {
    val sw = d.palette()
    val dark = style == LyricStyle.NEGRO
    // Fondo: la portada derretida y oscura (Color) o negro grafito (Negro).
    if (dark) drawRect(Brush.verticalGradient(listOf(BrandColors.Graphite, BrandColors.Ink)))
    else { meltedArt(d.art, sw); drawRect(Color.Black.copy(alpha = 0.45f)) }
    // Color de la tarjeta: el tono de la portada, oscurecido si sale muy claro para el texto blanco.
    var card = if (dark) Color(0xFF141417) else lerp(sw[0], Color.Black, 0.28f)
    while (card.luminance() > 0.32f) card = lerp(card, Color.Black, 0.2f)
    val cx = 90f; val cw = 900f; val pad = 64f
    // Letra grande; se achica si no cabe.
    var size = 72f
    var lyric: TextLayoutResult
    while (true) {
        lyric = measureText(measurer, lines.joinToString("\n"), cw - pad * 2, size, FontWeight.ExtraBold, lines = 12, spacing = -size * 0.015f, lineHeight = 1.2f)
        if (lyric.size.height <= 820 || size <= 44f) break
        size -= 6f
    }
    val head = 128f
    val cardH = pad + head + 60f + lyric.size.height + 70f + 58f + pad
    val cy = (H - cardH) / 2
    for (i in 12 downTo 1) {
        val g = i * 6f
        drawRoundRect(Color.Black.copy(alpha = 0.04f), Offset(cx - g * 0.5f, cy - g * 0.2f + 30f), Size(cw + g, cardH + g), CornerRadius(56f + g))
    }
    drawRoundRect(card, Offset(cx, cy), Size(cw, cardH), CornerRadius(56f))
    drawRoundRect(Color.White.copy(alpha = 0.10f), Offset(cx, cy), Size(cw, cardH), CornerRadius(56f), style = Stroke(2f))
    // Cabecera: portada chiquita, canción y artista.
    cover(d.art, cx + pad, cy + pad, head, 20f, sw, shadow = false)
    val tx = cx + pad + head + 28f; val tw = cw - pad * 2 - head - 28f
    text(measurer, d.song.title, tx, cy + pad + 18f, tw, 40f, FontWeight.Bold)
    text(measurer, d.song.artist ?: "", tx, cy + pad + 70f, tw, 32f, FontWeight.Medium, Color.White.copy(alpha = 0.72f))
    // Los versos.
    drawText(lyric, topLeft = Offset(cx + pad, cy + pad + head + 60f))
    // Pie: la calavera y FLACKER.
    val fy = cy + cardH - pad - 58f
    drawFlackerMark(Offset(cx + pad, fy), 58f)
    text(measurer, "FLACKER", cx + pad + 76f, fy + 12f, 400f, 30f, FontWeight.Black, spacing = 4f)
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

private fun DrawScope.measureText(
    m: TextMeasurer, s: String, w: Float, size: Float, weight: FontWeight, lines: Int = 1,
    align: TextAlign = TextAlign.Start, spacing: Float = 0f, color: Color = Color.White, lineHeight: Float = 1.1f,
): TextLayoutResult = m.measure(
    s,
    TextStyle(
        fontFamily = Fonts.inter, fontWeight = weight, fontSize = size.sp, color = color,
        textAlign = align, letterSpacing = spacing.sp, lineHeight = (size * lineHeight).sp,
    ),
    overflow = TextOverflow.Ellipsis, maxLines = lines, constraints = Constraints.fixedWidth(w.toInt()),
    density = this, layoutDirection = LayoutDirection.Ltr,
)

private fun DrawScope.text(
    m: TextMeasurer, s: String, x: Float, y: Float, w: Float, size: Float,
    weight: FontWeight = FontWeight.Normal, color: Color = Color.White, lines: Int = 1,
    align: TextAlign = TextAlign.Start, spacing: Float = 0f,
): TextLayoutResult {
    val layout = measureText(m, s, w, size, weight, lines, align, spacing, color)
    drawText(layout, topLeft = Offset(x, y))
    return layout
}

/** Portada con sombra suave y esquinas redondas. */
private fun DrawScope.cover(art: ImageBitmap?, x: Float, y: Float, s: Float, corner: Float, sw: List<Color>, shadow: Boolean = true) {
    if (shadow) for (i in 14 downTo 1) {
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

/** "Escuchando en Flacker" con la calavera. */
private fun DrawScope.header(m: TextMeasurer, x: Float, y: Float) {
    drawFlackerMark(Offset(x, y), 70f)
    text(m, "Escuchando en Flacker", x + 92f, y + 14f, 700f, 34f, FontWeight.SemiBold, Color.White.copy(alpha = 0.92f))
}

/** Pastilla de calidad en blanco: "FLAC · 24/96 · HI-RES". */
private fun DrawScope.qualityPill(m: TextMeasurer, song: Song, cx: Float?, x: Float, y: Float) {
    if (song.suffix == null || song.isPreview) return
    val label = song.formatLabel + if (song.isHiRes) "  ·  HI-RES" else if (song.isLossless) "  ·  LOSSLESS" else ""
    val layout = m.measure(
        label, TextStyle(fontFamily = Fonts.inter, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 2.sp, color = Color.White),
        density = this, layoutDirection = LayoutDirection.Ltr,
    )
    val pw = layout.size.width + 56f; val ph = layout.size.height + 26f
    val px = if (cx != null) cx - pw / 2 else x
    drawRoundRect(Color.White.copy(alpha = 0.12f), Offset(px, y), Size(pw, ph), CornerRadius(ph / 2))
    drawRoundRect(Color.White.copy(alpha = 0.40f), Offset(px, y), Size(pw, ph), CornerRadius(ph / 2), style = Stroke(2f))
    drawText(layout, topLeft = Offset(px + 28f, y + 13f))
}

private fun subtitle(song: Song) = listOfNotNull(song.album?.takeIf { it.isNotBlank() }, song.year?.toString()).joinToString(" · ")

private fun DrawScope.aura(m: TextMeasurer, d: StoryData, sw: List<Color>) {
    meltedArt(d.art, sw)
    drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.25f to Color.Black.copy(alpha = 0.05f), 0.6f to Color.Black.copy(alpha = 0.25f), 1f to Color.Black.copy(alpha = 0.7f)))
    header(m, 108f, 150f)
    val s = 864f
    cover(d.art, (W - s) / 2, 330f, s, 46f, sw)
    var y = 330f + s + 80f
    y += text(m, d.song.title, 108f, y, 864f, 78f, FontWeight.ExtraBold, lines = 2, align = TextAlign.Center, spacing = -1.5f).size.height + 12f
    y += text(m, d.song.artist ?: "", 108f, y, 864f, 46f, FontWeight.SemiBold, Color.White.copy(alpha = 0.85f), align = TextAlign.Center).size.height + 6f
    subtitle(d.song).takeIf { it.isNotEmpty() }?.let { y += text(m, it, 108f, y, 864f, 32f, FontWeight.Medium, Color.White.copy(alpha = 0.55f), align = TextAlign.Center).size.height }
    qualityPill(m, d.song, W / 2, 0f, min(y + 44f, 1680f))
}

private fun DrawScope.vinyl(m: TextMeasurer, d: StoryData, sw: List<Color>) {
    drawRect(Brush.verticalGradient(listOf(lerp(sw[0], Color.Black, 0.45f), lerp(sw[1], Color.Black, 0.8f), Color.Black)))
    val disc = Offset(700f, 820f)
    drawCircle(Brush.radialGradient(listOf(sw[2].copy(alpha = 0.45f), Color.Transparent), disc, 620f), 620f, disc)
    header(m, 96f, 150f)
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
    cover(d.art, 96f, 440f, 680f, 28f, sw)
    var y = 1320f
    y += text(m, d.song.title, 96f, y, 888f, 80f, FontWeight.ExtraBold, lines = 2, spacing = -1.5f).size.height + 10f
    y += text(m, d.song.artist ?: "", 96f, y, 888f, 46f, FontWeight.SemiBold, Color.White.copy(alpha = 0.85f)).size.height + 6f
    subtitle(d.song).takeIf { it.isNotEmpty() }?.let { y += text(m, it, 96f, y, 888f, 32f, FontWeight.Medium, Color.White.copy(alpha = 0.55f)).size.height }
    qualityPill(m, d.song, null, 96f, min(y + 40f, 1690f))
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
    drawFlackerMark(Offset(80f, 1766f), 64f)
    text(m, "FLACKER", 166f, 1780f, 400f, 34f, FontWeight.Black, spacing = 5f)
    text(m, "escuchando ahora", 580f, 1784f, 420f, 30f, FontWeight.Medium, Color.White.copy(alpha = 0.6f), align = TextAlign.End)
}
