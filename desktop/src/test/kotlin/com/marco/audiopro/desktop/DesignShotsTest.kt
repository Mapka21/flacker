package com.marco.audiopro.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
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
import com.marco.audiopro.ui.AlbumRoute
import com.marco.audiopro.ui.ArtistRoute
import com.marco.audiopro.ui.DesktopRoot
import com.marco.audiopro.ui.LibraryRoute
import com.marco.audiopro.ui.PlayerRoute
import com.marco.audiopro.ui.components.AmbientBackground
import com.marco.audiopro.ui.components.GeneratedArtistArt
import com.marco.audiopro.ui.components.drawFlackerMark
import com.marco.audiopro.ui.components.drawAsciiMark
import com.marco.audiopro.ui.theme.MarcoTheme
import com.marco.audiopro.ui.viz.VisualPreset
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.swing.SwingUtilities

private object DesignOwner : LifecycleOwner, ViewModelStoreOwner {
    override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val viewModelStore = ViewModelStore()
}

/**
 * Capturas rápidas para revisar el diseño (las pantallas principales, el menú de clic derecho,
 * las portadas generadas y el modo visual "Nexo 2029"). Mismos requisitos que [ScreenshotTest].
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesignShotsTest {
    @Test fun captureDesign() {
        val outDir = System.getenv("MARCO_SHOTS_DIR")
        assumeTrue("Sin servidor/carpeta de capturas", System.getenv("MARCO_TEST_SERVER") != null && outDir != null)
        System.setProperty("marco.profile", "dev")
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

        var nav: NavHostController? = null
        val scene = ui { ImageComposeScene(1380, 860, Density(1f)) {
            CompositionLocalProvider(LocalLifecycleOwner provides DesignOwner, LocalViewModelStoreOwner provides DesignOwner) {
                nav = rememberNavController()
                CompositionLocalProvider(LocalApp provides graph) { MarcoTheme { DesktopRoot(nav!!) } }
            }
        } }
        shot(scene, "d1-home-empty", 3000)
        val songs = runBlocking { graph.api.album("al1").song }
        graph.player.play(songs)
        shot(scene, "d2-home", 3000)
        ui { nav!!.navigate(AlbumRoute("al1")) }; shot(scene, "d3-album", 3000)
        // Clic derecho sobre la primera canción del álbum.
        val at = Offset(560f, System.getenv("MARCO_MENU_Y")?.toFloat() ?: 520f)
        ui {
            scene.sendPointerEvent(PointerEventType.Move, at)
            scene.sendPointerEvent(PointerEventType.Press, at, buttons = PointerButtons(isSecondaryPressed = true), button = PointerButton.Secondary)
            scene.sendPointerEvent(PointerEventType.Release, at, button = PointerButton.Secondary)
        }
        shot(scene, "d4-menu", 1200)
        ui { scene.sendPointerEvent(PointerEventType.Press, Offset(5f, 5f)); scene.sendPointerEvent(PointerEventType.Release, Offset(5f, 5f)) }
        ui { nav!!.navigate(LibraryRoute(1)) }; shot(scene, "d5-artists", 2500)
        ui { nav!!.navigate(ArtistRoute("ar1")) }; shot(scene, "d6-artist", 3000)
        graph.settings.updatePrefs { it.copy(visualMode = true, visualPreset = VisualPreset.NEXO) }
        ui { nav!!.navigate(PlayerRoute) }; shot(scene, "d7-player-nexo", 5000)
        ui { scene.close() }

        val viz = ui { ImageComposeScene(1200, 760, Density(1f)) {
            CompositionLocalProvider(LocalApp provides graph) {
                MarcoTheme { Box(Modifier.fillMaxSize()) { AmbientBackground(graph.player.state.value.current?.coverArt, preset = VisualPreset.NEXO) } }
            }
        } }
        shot(viz, "d8-nexo", 6000)
        ui { viz.close() }
        val viz2 = ui { ImageComposeScene(1200, 760, Density(1f)) {
            CompositionLocalProvider(LocalApp provides graph) {
                MarcoTheme { Box(Modifier.fillMaxSize()) { AmbientBackground(graph.player.state.value.current?.coverArt, preset = VisualPreset.ASCII) } }
            }
        } }
        shot(viz2, "d8b-ascii", 4000)
        ui { viz2.close() }

        // Fondos de shader (en la prueba corren en CPU: lentos, pero se ven igual).
        VisualPreset.entries.filter { it.gpu }.forEach { p ->
            val s = ui { ImageComposeScene(960, 540, Density(1f)) {
                CompositionLocalProvider(LocalApp provides graph) {
                    MarcoTheme { Box(Modifier.fillMaxSize()) { AmbientBackground(graph.player.state.value.current?.coverArt, preset = p) } }
                }
            } }
            shot(s, "d8c-${p.name.lowercase()}", 2500)
            ui { s.close() }
        }

        // Pantalla completa: efectos mezclados + letra.
        val imm = ui { ImageComposeScene(1380, 860, Density(1f)) {
            CompositionLocalProvider(LocalLifecycleOwner provides DesignOwner, LocalViewModelStoreOwner provides DesignOwner, LocalApp provides graph) {
                MarcoTheme { com.marco.audiopro.ui.ImmersiveScreen {} }
            }
        } }
        graph.player.seekTo(4000)
        shot(imm, "d12-immersive", 5000)
        listOf(0.5f, 1f).forEach { pr ->
            val ls = ui { ImageComposeScene(1200, 1100, Density(1f)) { MarcoTheme { com.marco.audiopro.ui.LyricStylesPreview(pr) } } }
            shot(ls, "d12-styles-${(pr * 100).toInt()}", 600)
            ui { ls.close() }
        }
        // Varios versos: cada uno entra con su estilo.
        listOf(0L, 1_500L, 3_000L, 4_500L, 6_000L).forEachIndexed { n, ms ->
            graph.player.seekTo(ms)
            shot(imm, "d12-immersive-$n", 900)
        }
        com.marco.audiopro.ui.Immersive.mode = com.marco.audiopro.ui.ImmersiveMode.PORTADA
        shot(imm, "d12-immersive-portada", 1500)
        com.marco.audiopro.ui.Immersive.mode = com.marco.audiopro.ui.ImmersiveMode.LETRA
        ui { imm.close() }

        val login = ui { ImageComposeScene(1380, 860, Density(1f)) {
            CompositionLocalProvider(LocalLifecycleOwner provides DesignOwner, LocalViewModelStoreOwner provides DesignOwner, LocalApp provides graph) {
                MarcoTheme { com.marco.audiopro.ui.screens.LoginScreen() }
            }
        } }
        shot(login, "d13-login", 1500)
        ui { login.close() }

        val gen = ui { ImageComposeScene(900, 300, Density(1f)) {
            MarcoTheme {
                androidx.compose.foundation.layout.Row {
                    listOf("¡Cazamos Cometas!", "Mint Field", "125, Rue Montmartre", "Natanael Cano", "毛不易", "\$uicideboy\$").forEach {
                        GeneratedArtistArt(it, Modifier.weight(1f).fillMaxSize())
                    }
                }
            }
        } }
        shot(gen, "d9-generated", 800)
        ui { gen.close() }
        // Historias para Instagram: los tres estilos (PNG reales) y el diálogo.
        val song = graph.player.state.value.current!!
        val done = java.util.concurrent.CountDownLatch(1)
        val stories = ui { ImageComposeScene(300, 300, Density(1f)) {
            CompositionLocalProvider(LocalApp provides graph) {
                MarcoTheme {
                    val m = androidx.compose.ui.text.rememberTextMeasurer()
                    val ctx = coil3.compose.LocalPlatformContext.current
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        val art = com.marco.audiopro.ui.components.loadStoryArt(ctx, graph.api, song.coverArt)
                        val sw = com.marco.audiopro.ui.components.coverColors(ctx, graph.api, song.coverArt)?.swatches
                        com.marco.audiopro.ui.components.StoryStyle.entries.forEach { st ->
                            val img = com.marco.audiopro.ui.components.renderStory(m, com.marco.audiopro.ui.components.StoryData(song, art, sw), st)
                            File(outDir, "d10-story-${st.name.lowercase()}.png").writeBytes(com.marco.audiopro.platform.encodePng(img))
                        }
                        val verses = listOf("de veinte hercios", "hasta veinte mil", "para probar", "las letras sincronizadas")
                        com.marco.audiopro.ui.components.LyricStyle.entries.forEach { st ->
                            val img = com.marco.audiopro.ui.components.renderLyricStory(m, com.marco.audiopro.ui.components.StoryData(song, art, sw), verses, st)
                            File(outDir, "d10-lyric-${st.name.lowercase()}.png").writeBytes(com.marco.audiopro.platform.encodePng(img))
                        }
                        // El ícono en grande (para el .ico de Windows y el PNG de la ventana).
                        for ((name, px, margin) in listOf(Triple("icon-512", 512, 0.04f), Triple("icon-full-512", 512, 0f))) {
                            val ic = androidx.compose.ui.graphics.ImageBitmap(px, px)
                            androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(ic), androidx.compose.ui.geometry.Size(px.toFloat(), px.toFloat())) {
                                val mg = px * margin
                                drawFlackerMark(androidx.compose.ui.geometry.Offset(mg, mg), px - 2 * mg)
                            }
                            File(outDir, "$name.png").writeBytes(com.marco.audiopro.platform.encodePng(ic))
                        }
                        // La calavera ASCII: ícono de PC (grande) y frente del ícono adaptativo de Android (432 px, zona segura 264).
                        for ((name, px, bg) in listOf(Triple("icon-ascii-512", 512, true), Triple("android-fg-432", 432, false))) {
                            val ic = androidx.compose.ui.graphics.ImageBitmap(px, px)
                            androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(ic), androidx.compose.ui.geometry.Size(px.toFloat(), px.toFloat())) {
                                if (bg) drawAsciiMark(m, androidx.compose.ui.geometry.Offset(px * 0.04f, px * 0.04f), px * 0.92f)
                                else drawAsciiMark(m, androidx.compose.ui.geometry.Offset(84f, 84f), 264f, background = false, fill = 0.98f)
                            }
                            File(outDir, "$name.png").writeBytes(com.marco.audiopro.platform.encodePng(ic))
                        }
                        done.countDown()
                    }
                }
            }
        } }
        val sEnd = System.currentTimeMillis() + 8000
        while (done.count > 0 && System.currentTimeMillis() < sEnd) { ui { stories.render(System.nanoTime()) }; Thread.sleep(30) }
        ui { stories.close() }
        val dialog = ui { ImageComposeScene(1380, 860, Density(1f)) {
            CompositionLocalProvider(LocalLifecycleOwner provides DesignOwner, LocalViewModelStoreOwner provides DesignOwner, LocalApp provides graph) {
                MarcoTheme { com.marco.audiopro.ui.components.ShareStoryDialog(song) {} }
            }
        } }
        shot(dialog, "d11-share-dialog", 3000)
        ui { dialog.close() }
        graph.settings.updatePrefs { it.copy(visualMode = false) }
        graph.shutdown()
    }
}
