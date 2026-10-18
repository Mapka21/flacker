package com.marco.audiopro.platform

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import javax.imageio.ImageIO
import coil3.Image
import java.awt.FileDialog
import java.awt.Frame
import coil3.toBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintStrokeCap
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Mismas firmas que en androidMain: el código de src/jvmShared las usa sin expect/actual.

fun coilImageToImageBitmap(image: Image): ImageBitmap = image.toBitmap().asComposeImageBitmap()

/**
 * Bitmap ARGB que se puede reescribir cada frame (espectrograma, modo ASCII). Se crea UNA vez y los
 * pixeles se copian directo a su memoria nativa: antes cada frame creaba un bitmap nuevo de varios MB
 * y el recolector de basura congelaba la app segundos enteros.
 */
class PixelBuffer(val width: Int, val height: Int) {
    val pixels = IntArray(width * height)
    private val info = ImageInfo.makeN32(width, height, ColorAlphaType.UNPREMUL)
    private val bmp = Bitmap().apply { allocPixels(info) }
    private val img = bmp.asComposeImageBitmap()
    private val pixmap = bmp.peekPixels()
    private val direct: java.nio.IntBuffer? = runCatching {
        // N32 en Windows/x86 = BGRA little-endian = ARGB como int.
        org.jetbrains.skia.impl.BufferUtil.getByteBufferFromPointer(pixmap!!.addr, width * height * 4)
            .order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
    }.getOrNull()
    private val bytes by lazy { ByteBuffer.allocate(width * height * 4).order(ByteOrder.LITTLE_ENDIAN) }

    fun image(): ImageBitmap {
        val d = direct
        if (d != null) {
            d.clear(); d.put(pixels)
            bmp.notifyPixelsChanged()
            return img
        }
        bytes.clear()
        bytes.asIntBuffer().put(pixels)
        val b = Bitmap()
        b.installPixels(info, bytes.array(), width * 4)
        return b.asComposeImageBitmap()
    }
}

private val pointPaint = Paint().apply { isAntiAlias = true; strokeCap = PaintStrokeCap.ROUND }

/** Nube de puntos (goniómetro). `points` = x0,y0,x1,y1… */
fun DrawScope.drawPointCloud(points: FloatArray, count: Int, color: Color, strokePx: Float) {
    pointPaint.color = color.toArgb()
    pointPaint.strokeWidth = strokePx
    val pts = if (points.size == count * 2) points else points.copyOf(count * 2)
    drawIntoCanvas { it.nativeCanvas.drawPoints(pts, pointPaint) }
}

val supportsBlur: Boolean = true

/** Selector de imagen (portadas propias): el diálogo de archivos de Windows. */
@Composable
fun rememberImagePicker(onPicked: (ByteArray) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    return remember {
        {
            val d = FileDialog(null as Frame?, "Elige una imagen", FileDialog.LOAD)
            d.file = "*.jpg;*.jpeg;*.png;*.webp;*.gif;*.bmp" // en Windows así se filtra
            d.isVisible = true
            d.files.firstOrNull()?.takeIf { it.isFile }?.let { callback(it.readBytes()) }
        }
    }
}

fun encodePng(img: ImageBitmap): ByteArray =
    org.jetbrains.skia.Image.makeFromBitmap(img.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes

/** En la PC no hay Instagram para historias: la imagen se copia y se guarda. */
val sharesToInstagram: Boolean = false

/** Carpeta Imágenes de Windows (puede estar en OneDrive y con acento: "Imágenes"); se pide en UTF-8. */
private val picturesDir: File by lazy {
    val path = runCatching {
        val p = ProcessBuilder(
            "powershell", "-NoProfile", "-Command",
            "[Console]::OutputEncoding=[Text.Encoding]::UTF8; [Environment]::GetFolderPath('MyPictures')",
        ).redirectErrorStream(true).start()
        val out = p.inputStream.readBytes().toString(Charsets.UTF_8).trim().lines().lastOrNull()?.trim()
        p.waitFor()
        out
    }.getOrNull()
    path?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isDirectory } ?: File(System.getProperty("user.home"), "Pictures")
}

private class ImageTransfer(private val img: java.awt.Image) : Transferable {
    override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
    override fun isDataFlavorSupported(f: DataFlavor) = f == DataFlavor.imageFlavor
    override fun getTransferData(f: DataFlavor): Any = img
}

/**
 * Compartir la imagen de historia. En la PC: se copia al portapapeles (pégala donde quieras) y se
 * guarda en Imágenes\Flacker. Devuelve el aviso para mostrar.
 */
@Composable
fun rememberStoryShare(): (png: ByteArray, name: String, instagram: Boolean) -> String? = remember {
    { png, name, _ ->
        val dir = File(picturesDir, "Flacker").apply { mkdirs() }
        val f = File(dir, name.replace(Regex("""[\\/:*?"<>|]+"""), " ").trim().take(120) + ".png")
        f.writeBytes(png)
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageTransfer(ImageIO.read(f)), null) }
        "Copiada y guardada en ${dir.path}"
    }
}

/** Modo inmersivo: en la PC la pantalla completa la maneja la ventana (Main.kt). */
@Composable
fun ImmersiveSystem(on: Boolean, onBack: () -> Unit) {}

/** Modo sin servidor: elegir una carpeta de música (devuelve su ruta, o null si se canceló). */
@Composable
fun rememberLocalMusicPicker(onPicked: (String?) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    return remember {
        {
            val ch = javax.swing.JFileChooser().apply {
                fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                dialogTitle = "Elige tu carpeta de música"
                currentDirectory = File(System.getProperty("user.home"), "Music").takeIf { it.isDirectory } ?: File(System.getProperty("user.home"))
            }
            callback(if (ch.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) ch.selectedFile.absolutePath else null)
        }
    }
}

/** En la PC se eligen carpetas; en el celular, toda la música del teléfono. */
val localMusicIsFolders: Boolean = true

/** Fondos que se calculan pixel por pixel en la tarjeta de video (SkSL). */
val supportsFxShaders: Boolean = true

/**
 * Un efecto de pantalla completa escrito como shader: corre en la tarjeta de video, así que cuesta
 * casi nada aunque cada pixel haga cuentas. Uniformes fijos: iRes, iTime, iFlow, iAudio
 * (graves, medios, agudos, golpe) y c0..c2 (colores de la portada).
 */
class FxShader(sksl: String) {
    private val builder: org.jetbrains.skia.RuntimeShaderBuilder? = runCatching {
        org.jetbrains.skia.RuntimeShaderBuilder(org.jetbrains.skia.RuntimeEffect.makeForShader(sksl))
    }.onFailure { System.err.println("FxShader: ${it.message}") }.getOrNull()
    val ok: Boolean get() = builder != null

    /** @param extra hasta 16 valores más para el efecto (iP0..iP3, de 4 en 4). */
    fun brush(w: Float, h: Float, time: Float, flow: Float, audio: FloatArray, cols: List<Color>, extra: FloatArray? = null): androidx.compose.ui.graphics.Brush? {
        val b = builder ?: return null
        if (extra != null) for (k in 0 until extra.size / 4) b.uniform("iP$k", extra[k * 4], extra[k * 4 + 1], extra[k * 4 + 2], extra[k * 4 + 3])
        b.uniform("iRes", w, h)
        b.uniform("iTime", time)
        b.uniform("iFlow", flow)
        b.uniform("iAudio", audio[0], audio[1], audio[2], audio[3])
        b.uniform("c0", cols[0].red, cols[0].green, cols[0].blue)
        b.uniform("c1", cols[1].red, cols[1].green, cols[1].blue)
        b.uniform("c2", cols[2].red, cols[2].green, cols[2].blue)
        return androidx.compose.ui.graphics.ShaderBrush(b.makeShader())
    }
}
