package com.marco.audiopro.platform

import android.graphics.Bitmap
import android.graphics.Paint
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import coil3.Image
import coil3.toBitmap

// Mismas firmas que en desktopMain: el código de src/jvmShared las usa sin expect/actual.

fun coilImageToImageBitmap(image: Image): ImageBitmap {
    val b = image.toBitmap()
    // Los bitmaps de hardware no se pueden leer pixel por pixel.
    val readable = if (b.config == Bitmap.Config.HARDWARE) b.copy(Bitmap.Config.ARGB_8888, false) else b
    return readable.asImageBitmap()
}

/** Bitmap ARGB que se puede reescribir cada frame (espectrograma). */
class PixelBuffer(val width: Int, val height: Int) {
    val pixels = IntArray(width * height)
    private val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val img = bmp.asImageBitmap()

    fun image(): ImageBitmap {
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return img
    }
}

private val pointPaint = Paint().apply { isAntiAlias = true; strokeCap = Paint.Cap.ROUND }

/** Nube de puntos sin asignaciones (goniómetro). `points` = x0,y0,x1,y1… */
fun DrawScope.drawPointCloud(points: FloatArray, count: Int, color: Color, strokePx: Float) {
    pointPaint.color = color.toArgb()
    pointPaint.strokeWidth = strokePx
    drawIntoCanvas { it.nativeCanvas.drawPoints(points, 0, count * 2, pointPaint) }
}

/** Modifier.blur sólo funciona en Android 12+. */
val supportsBlur: Boolean = Build.VERSION.SDK_INT >= 31
