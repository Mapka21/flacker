package com.marco.audiopro.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
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
import com.marco.audiopro.ui.DesktopRoot
import com.marco.audiopro.ui.ProfileRoute
import com.marco.audiopro.ui.theme.MarcoTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.swing.SwingUtilities

private object ShellOwner : LifecycleOwner, ViewModelStoreOwner {
    override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val viewModelStore = ViewModelStore()
}

/**
 * La cáscara de PC en varios anchos (la barra de arriba no debe encimarse), el menú del avatar
 * y la pantalla de perfil. Mismos requisitos que [ScreenshotTest].
 */
@OptIn(ExperimentalComposeUiApi::class)
class ShellShotsTest {
    @Test fun captureShell() {
        com.marco.audiopro.ui.components.SplashState.done = true
        val outDir = System.getenv("MARCO_SHOTS_DIR")
        assumeTrue("Sin servidor/carpeta de capturas", System.getenv("MARCO_TEST_SERVER") != null && outDir != null)
        System.setProperty("marco.profile", "dev")
        System.setProperty("flacker.lastfm.base", "http://127.0.0.1:8765/2.0/")
        val graph = DesktopGraph()
        SingletonImageLoader.setSafe { ctx ->
            ImageLoader.Builder(ctx).components { add(OkHttpNetworkFetcherFactory(callFactory = { graph.http })) }.build()
        }
        graph.player.setVolume(0f)
        fun <T> ui(block: () -> T): T {
            var r: Result<T>? = null
            SwingUtilities.invokeAndWait { r = runCatching(block) }
            return r!!.getOrThrow()
        }
        fun shot(scene: ImageComposeScene, name: String, ms: Long) {
            val end = System.currentTimeMillis() + ms
            while (System.currentTimeMillis() < end) { ui { scene.render(System.nanoTime()) }; Thread.sleep(25) }
            File(outDir, "$name.png").writeBytes(ui { scene.render(System.nanoTime()).encodeToData(EncodedImageFormat.PNG)!!.bytes })
        }
        fun click(scene: ImageComposeScene, at: Offset) = ui {
            scene.sendPointerEvent(PointerEventType.Move, at)
            scene.sendPointerEvent(PointerEventType.Press, at)
            scene.sendPointerEvent(PointerEventType.Release, at)
        }
        graph.player.play(runBlocking { graph.api.album("al1").song })

        // (ancho, alto): 1280 = 1920 px al 150 % (con columna derecha, el caso que se encimaba), 1050 = mínimo, 1600 = holgado.
        for ((w, h) in listOf(1280 to 720, 1050 to 700, 1600 to 900)) {
            var nav: NavHostController? = null
            val scene = ui { ImageComposeScene(w, h, Density(1f)) {
                CompositionLocalProvider(LocalLifecycleOwner provides ShellOwner, LocalViewModelStoreOwner provides ShellOwner) {
                    nav = rememberNavController()
                    CompositionLocalProvider(LocalApp provides graph) { MarcoTheme { DesktopRoot(nav!!, onMiniPlayer = {}) } }
                }
            } }
            shot(scene, "s-home-$w", 2500)
            if (w == 1600) {
                ui { nav!!.navigate(ProfileRoute) }; shot(scene, "s-profile-$w", 2500)
                // Avatar: arriba a la derecha de la columna central (antes de la columna derecha de 340 + márgenes).
                val x = (w - 14 - 340 - 14 - 22).toFloat()
                click(scene, Offset(x, 14f + 25f)); shot(scene, "s-avatar-menu-$w", 1200)
            }
            if (w == 1280) {
                val x = (w - 14 - 340 - 14 - 76).toFloat()
                click(scene, Offset(x, 14f + 25f)); shot(scene, "s-more-menu-$w", 1200)
            }
            ui { scene.close() }
        }
        graph.shutdown()
    }
}
