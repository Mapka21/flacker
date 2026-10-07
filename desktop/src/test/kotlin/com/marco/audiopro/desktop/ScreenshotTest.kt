package com.marco.audiopro.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.ui.AlbumRoute
import com.marco.audiopro.ui.ArtistRoute
import com.marco.audiopro.ui.DesktopRoot
import com.marco.audiopro.ui.DspRoute
import com.marco.audiopro.ui.LikedRoute
import com.marco.audiopro.ui.MarcoRoot
import com.marco.audiopro.ui.PlayerRoute
import com.marco.audiopro.ui.SettingsRoute
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.MarcoTheme
import com.marco.audiopro.ui.theme.Mono
import com.marco.audiopro.ui.viz.Visualizer
import com.marco.audiopro.ui.viz.VizMode
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.swing.SwingUtilities

/** Lo que normalmente provee la ventana de Compose; fuera de pantalla hay que darlo a mano. */
private object TestOwner : LifecycleOwner, ViewModelStoreOwner {
    override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val viewModelStore = ViewModelStore()
}

@androidx.compose.runtime.Composable
private fun WithOwners(content: @androidx.compose.runtime.Composable () -> Unit) =
    CompositionLocalProvider(LocalLifecycleOwner provides TestOwner, LocalViewModelStoreOwner provides TestOwner, content = content)

/**
 * Renderiza la app de escritorio fuera de pantalla contra el servidor de prueba y guarda
 * capturas en MARCO_SHOTS_DIR. Sólo corre si existen MARCO_TEST_SERVER y MARCO_SHOTS_DIR.
 * Usa el perfil "dev" (con la cuenta del servidor de prueba ya guardada).
 */
@OptIn(ExperimentalComposeUiApi::class)
class ScreenshotTest {

    @Test fun captureScreens() {
        val outDir = System.getenv("MARCO_SHOTS_DIR")
        assumeTrue("Sin servidor/carpeta de capturas", System.getenv("MARCO_TEST_SERVER") != null && outDir != null)
        System.setProperty("marco.profile", "dev")
        val graph = DesktopGraph()
        SingletonImageLoader.setSafe { ctx ->
            ImageLoader.Builder(ctx).components { add(OkHttpNetworkFetcherFactory(callFactory = { graph.http })) }.build()
        }
        graph.player.setVolume(0f)

        // La navegación exige el hilo principal (EDT); se espera fuera de él para no bloquearlo.
        fun <T> ui(block: () -> T): T {
            var r: Result<T>? = null
            SwingUtilities.invokeAndWait { r = runCatching(block) }
            return r!!.getOrThrow()
        }

        var nav: NavHostController? = null
        val scene = ui { ImageComposeScene(1380, 860, Density(1f)) {
            WithOwners {
                nav = rememberNavController()
                CompositionLocalProvider(LocalApp provides graph) { MarcoTheme { DesktopRoot(nav!!) } }
            }
        } }
        fun pump(ms: Long) {
            val end = System.currentTimeMillis() + ms
            while (System.currentTimeMillis() < end) { ui { scene.render(System.nanoTime()) }; Thread.sleep(30) }
        }
        fun save(name: String) {
            val png = ui { scene.render(System.nanoTime()).encodeToData(EncodedImageFormat.PNG)!!.bytes }
            File(outDir, "$name.png").writeBytes(png)
        }

        pump(4000); save("1-home")
        val songs = runBlocking { graph.api.album("al1").song }
        graph.player.play(songs)
        pump(2500)
        ui { nav!!.navigate(AlbumRoute("al1")) }; pump(4000); save("2-album")
        ui { nav!!.navigate(PlayerRoute) }; pump(3000); save("3-player")
        ui { nav!!.popBackStack() }
        ui { nav!!.navigate(DspRoute) }; pump(2500); save("4-dsp")
        ui { nav!!.navigate(LikedRoute) }; pump(2000); save("5-liked")
        ui { nav!!.navigate(SettingsRoute) }; pump(1500); save("6-settings")
        ui { scene.close() }

        // Todos los visualizadores a la vez, con el barrido sonando.
        val viz = ui { ImageComposeScene(1380, 860, Density(1f)) {
            CompositionLocalProvider(LocalApp provides graph) {
                MarcoTheme {
                    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            for (m in listOf(VizMode.SPECTRUM, VizMode.SPECTROGRAM, VizMode.VU)) Panel(m)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            for (m in listOf(VizMode.GONIO, VizMode.SCOPE)) Panel(m)
                        }
                    }
                }
            }
        } }
        val end = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < end) { ui { viz.render(System.nanoTime()) }; Thread.sleep(16) }
        File(outDir, "7-visualizers.png").writeBytes(ui { viz.render(System.nanoTime()).encodeToData(EncodedImageFormat.PNG)!!.bytes })
        ui { viz.close() }

        // Ventana de PC angosta: la columna derecha se vuelve píldora flotante.
        val narrow = ui { ImageComposeScene(1100, 760, Density(1f)) {
            WithOwners {
                val n = rememberNavController()
                CompositionLocalProvider(LocalApp provides graph) { MarcoTheme { DesktopRoot(n) } }
            }
        } }
        val nEnd = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < nEnd) { ui { narrow.render(System.nanoTime()) }; Thread.sleep(30) }
        File(outDir, "13-narrow.png").writeBytes(ui { narrow.render(System.nanoTime()).encodeToData(EncodedImageFormat.PNG)!!.bytes })
        ui { narrow.close() }

        // La misma UI en tamaño de teléfono (la cáscara de Android, con barra inferior).
        var pnav: NavHostController? = null
        val phone = ui { ImageComposeScene(412, 915, Density(1f)) {
            WithOwners {
                pnav = rememberNavController()
                CompositionLocalProvider(LocalApp provides graph) { MarcoTheme { MarcoRoot(pnav!!) } }
            }
        } }
        fun ppump(ms: Long) {
            val e = System.currentTimeMillis() + ms
            while (System.currentTimeMillis() < e) { ui { phone.render(System.nanoTime()) }; Thread.sleep(30) }
        }
        fun psave(name: String) = File(outDir, "$name.png").writeBytes(ui { phone.render(System.nanoTime()).encodeToData(EncodedImageFormat.PNG)!!.bytes })
        ppump(3000); psave("8-phone-home")
        ui { pnav!!.navigate(AlbumRoute("al1")) }; ppump(3000); psave("9-phone-album")
        ui { pnav!!.navigate(PlayerRoute) }; ppump(3000); psave("10-phone-player")
        ui { pnav!!.popBackStack(); pnav!!.navigate(ArtistRoute("ar1")) }; ppump(3000); psave("11-phone-artist")
        ui { phone.close() }

        // Miniplayer iPod (sin ventana: sólo el cuerpo, sobre fondo gris para ver la sombra).
        val ipod = ui { ImageComposeScene(178, 266, Density(1f)) {
            WithOwners {
                CompositionLocalProvider(LocalApp provides graph) {
                    MarcoTheme(background = false) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF7A7F88))) { IpodBody(graph.player, onExpand = {}) { it() } }
                    }
                }
            }
        } }
        val iEnd = System.currentTimeMillis() + 2500
        while (System.currentTimeMillis() < iEnd) { ui { ipod.render(System.nanoTime()) }; Thread.sleep(30) }
        File(outDir, "12-ipod.png").writeBytes(ui { ipod.render(System.nanoTime()).encodeToData(EncodedImageFormat.PNG)!!.bytes })
        ui { ipod.close() }
        graph.shutdown()
    }
}

@androidx.compose.runtime.Composable
private fun Panel(m: VizMode) {
    Column {
        Text(m.label, style = Mono.copy(color = Hifi.TextDim))
        Box(Modifier.size(430.dp, 360.dp)) { Visualizer(m, Modifier.fillMaxSize()) }
    }
}
