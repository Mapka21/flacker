package com.marco.audiopro.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.marco.audiopro.ui.viz.AsciiVision
import com.marco.audiopro.ui.viz.AudioFeatures
import org.junit.Test

/**
 * "ASCII 333" tronaba con "Index 64 out of bounds for length 64" (reporte del beta tester): si la
 * ventana cambiaba de alto pero no de ancho, el arreglo del glitch se quedaba con los renglones de antes.
 */
class AsciiVisionTest {
    private fun features(beat: Float, beats: Int) = AudioFeatures().apply {
        AudioFeatures::class.java.getDeclaredField("beat").apply { isAccessible = true }.setFloat(this, beat)
        AudioFeatures::class.java.getDeclaredField("beats").apply { isAccessible = true }.setInt(this, beats)
    }

    @Test fun survivesTallerWindowOnStrongBeat() {
        val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
        val v = AsciiVision()
        val tones = List(4) { Color.Gray }
        fun frame(w: Int, h: Int, f: AudioFeatures, t: Float) {
            val img = ImageBitmap(w, h)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) {
                v.draw(this, measurer, null, tones, f, t, 1f, false)
            }
        }
        // Mismo ancho, primero bajita (pocos renglones) y luego alta (más renglones) con golpes fuertes.
        frame(800, 13 * 40, features(0f, 0), 0.1f)
        for (i in 1..12) frame(800, 13 * 90, features(1f, i), 0.1f + i * 0.02f)
    }
}
