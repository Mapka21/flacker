package com.marco.audiopro.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.audiopro.ui.components.AsciiArt
import com.marco.audiopro.ui.components.AsciiSkull
import com.marco.audiopro.ui.components.FlackerLogo
import com.marco.audiopro.ui.components.FlackerMark
import com.marco.audiopro.ui.components.SkullSplash
import com.marco.audiopro.ui.theme.MarcoTheme
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.swing.SwingUtilities

/** Capturas de la marca: el logo, el arte de letras y cuadros de la animación de entrada (MARCO_SHOTS_DIR). */
class BrandShotsTest {
    @Test fun captureBrand() {
        val out = System.getenv("MARCO_SHOTS_DIR")
        assumeTrue(out != null)
        fun <T> ui(block: () -> T): T { var r: Result<T>? = null; SwingUtilities.invokeAndWait { r = runCatching(block) }; return r!!.getOrThrow() }

        val still = ui { ImageComposeScene(900, 560, Density(1f)) {
            MarcoTheme {
                Row(Modifier.fillMaxSize().background(Color.Black).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(30.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsciiArt(AsciiSkull.BIG, fontSize = 11.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        FlackerLogo(size = 22.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf(16, 24, 32, 48, 96).forEach { FlackerMark(it.dp) }
                        }
                    }
                }
            }
        } }
        repeat(3) { ui { still.render(it * 16_000_000L) } }
        File(out, "b1-brand.png").writeBytes(ui { still.render(100_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes })
        ui { still.close() }

        val splash = ui { ImageComposeScene(1100, 700, Density(1f)) { MarcoTheme { SkullSplash {} } } }
        var t = 0L
        for (ms in listOf(250, 600, 1000, 1080, 1150, 1400, 2500)) {
            while (t < ms * 1_000_000L) { t += 16_666_667L; ui { splash.render(t) } }
            File(out, "b2-splash-$ms.png").writeBytes(ui { splash.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes })
        }
        ui { splash.close() }
    }
}
