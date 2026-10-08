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
