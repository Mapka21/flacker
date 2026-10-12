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

/** Bitmap ARGB que se puede reescribir cada frame (espectrograma). */
class PixelBuffer(val width: Int, val height: Int) {
    val pixels = IntArray(width * height)
    private val bytes = ByteBuffer.allocate(width * height * 4).order(ByteOrder.LITTLE_ENDIAN)
    private val info = ImageInfo.makeN32(width, height, ColorAlphaType.PREMUL)

    fun image(): ImageBitmap {
        bytes.clear()
        bytes.asIntBuffer().put(pixels) // N32 en Windows/x86 = BGRA little-endian = ARGB como int
        val bmp = Bitmap()
        bmp.installPixels(info, bytes.array(), width * 4)
        return bmp.asComposeImageBitmap()
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
