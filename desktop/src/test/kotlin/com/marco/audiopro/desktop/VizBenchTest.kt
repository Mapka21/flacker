package com.marco.audiopro.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.ui.components.AmbientBackground
import com.marco.audiopro.ui.theme.MarcoTheme
import com.marco.audiopro.ui.viz.VisualPreset
import org.junit.Assume.assumeTrue
import org.junit.Test
import javax.swing.SwingUtilities

/**
 * Cuánto tarda cada modo visual por frame (render en CPU a 1380×860). Sólo con MARCO_BENCH=1.
 * Sirve para cazar modos lentos: arriba de ~16 ms por frame ya no da 60 fps.
 */
@OptIn(ExperimentalComposeUiApi::class)
class VizBenchTest {
    @Test fun bench() {
        assumeTrue(System.getenv("MARCO_BENCH") == "1")
        System.setProperty("marco.profile", "dev")
        val graph = DesktopGraph()
        fun <T> ui(block: () -> T): T {
            var r: Result<T>? = null
            SwingUtilities.invokeAndWait { r = runCatching(block) }
            return r!!.getOrThrow()
        }
        for (p in listOf(VisualPreset.PORTADA, VisualPreset.NEON, VisualPreset.NEXO, VisualPreset.ASCII)) {
            val scene = ui { ImageComposeScene(1380, 860, Density(1.5f)) {
                CompositionLocalProvider(LocalApp provides graph) { MarcoTheme { Box(Modifier.fillMaxSize()) { AmbientBackground(null, preset = p) } } }
            } }
            repeat(20) { ui { scene.render(System.nanoTime()) } }
            val n = 60
            val t0 = System.nanoTime()
            repeat(n) { ui { scene.render(System.nanoTime()) } }
            val ms = (System.nanoTime() - t0) / 1e6 / n
            println("BENCH ${p.name}: %.1f ms/frame".format(ms))
            ui { scene.close() }
        }
        graph.shutdown()
    }
}
