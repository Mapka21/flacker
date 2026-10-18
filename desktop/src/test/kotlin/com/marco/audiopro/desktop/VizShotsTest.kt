package com.marco.audiopro.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.ui.unit.Density
import com.marco.audiopro.ui.viz.AudioFeatures
import com.marco.audiopro.ui.viz.GeissFeedback
import com.marco.audiopro.ui.viz.MinterNeon
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.swing.SwingUtilities

/** Los visuales Xbox sin la app alrededor, con música simulada (MARCO_SHOTS_DIR). */
class VizShotsTest {
    @Test fun xbox() {
        val out = System.getenv("MARCO_SHOTS_DIR"); assumeTrue(out != null)
        fun <T> ui(b: () -> T): T { var r: Result<T>? = null; SwingUtilities.invokeAndWait { r = runCatching(b) }; return r!!.getOrThrow() }
        val f = AudioFeatures()
        // Bajo que golpea cada medio segundo y una onda senoidal.
        val fake = object {
            var t = 0f
            fun step(dt: Float) {
                t += dt
                val beat = (t % 0.5f) < 0.05f
                for (i in f.bands.indices) f.bands[i] = (0.5f + 0.4f * kotlin.math.sin(t * 3 + i)).coerceIn(0f, 1f)
                for (j in f.wave.indices) f.wave[j] = 0.4f * kotlin.math.sin(j * 0.2f + t * 8) * (if (beat) 1f else 0.6f)
            }
        }
        for ((name, viz) in listOf("geiss" to GeissFeedback(), "neon" to MinterNeon())) {
            var time = 0f
            val tick = androidx.compose.runtime.mutableIntStateOf(0)
            val scene = ui { ImageComposeScene(960, 540, Density(1f)) {
                Canvas(Modifier.fillMaxSize().background(Color.Black)) {
                    tick.intValue
                    fake.step(1 / 60f); f.update(null, 1 / 60f); time += 1 / 60f
                    when (viz) { is GeissFeedback -> viz.frame(this, f, time, 1 / 60f); is MinterNeon -> viz.frame(this, f, time, 1 / 60f) }
                }
            } }
            repeat(150) { i -> ui { tick.intValue = i; scene.render(i * 16_666_667L) } }
            File(out, "v-$name.png").writeBytes(ui { tick.intValue = 999; scene.render(151 * 16_666_667L).encodeToData(EncodedImageFormat.PNG)!!.bytes })
            ui { scene.close() }
        }
    }

    @Test fun neon360Scenes() {
        val out = System.getenv("MARCO_SHOTS_DIR"); assumeTrue(out != null)
        fun <T> ui(b: () -> T): T { var r: Result<T>? = null; SwingUtilities.invokeAndWait { r = runCatching(b) }; return r!!.getOrThrow() }
        val f = AudioFeatures()
        var t = 0f
        for (scene in listOf(0, 3, 6, 8, 10, 11, 16, 18, 24, 25, 26, 31)) {
            com.marco.audiopro.ui.viz.Neon360Control.scene = scene
            com.marco.audiopro.ui.viz.Neon360Control.manual = true
            val viz = com.marco.audiopro.ui.viz.Neon360()
            val tick = androidx.compose.runtime.mutableIntStateOf(0)
            var time = 0f
            val sceneUi = ui { ImageComposeScene(640, 360, Density(1f)) {
                Canvas(Modifier.fillMaxSize().background(Color.Black)) {
                    tick.intValue
                    t += 1 / 60f; time += 1 / 60f
                    for (i in f.bands.indices) f.bands[i] = (0.55f + 0.4f * kotlin.math.sin(t * 3 + i)).coerceIn(0f, 1f)
                    for (j in f.wave.indices) f.wave[j] = 0.4f * kotlin.math.sin(j * 0.2f + t * 8)
                    f.update(null, 1 / 60f)
                    viz.frame(this, f, time, 1 / 60f)
                }
            } }
            repeat(90) { i -> ui { tick.intValue = i; sceneUi.render(i * 16_666_667L) } }
            File(out, "n360-%02d.png".format(scene)).writeBytes(ui { tick.intValue = 999; sceneUi.render(91 * 16_666_667L).encodeToData(EncodedImageFormat.PNG)!!.bytes })
            ui { sceneUi.close() }
        }
    }
}
