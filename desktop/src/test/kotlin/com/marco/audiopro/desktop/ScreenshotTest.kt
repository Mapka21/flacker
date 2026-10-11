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
import com.marco.audiopro.ui.LibraryRoute
import com.marco.audiopro.ui.PlaylistRoute
import com.marco.audiopro.ui.SearchRoute
import com.marco.audiopro.ui.openTab
import com.marco.audiopro.ui.components.AmbientBackground
import com.marco.audiopro.ui.viz.VisualPreset
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
        System.setProperty("flacker.lastfm.base", System.getenv("MARCO_TEST_SERVER") + "/2.0/")
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
        pump(4000); save("3a-player-later")
        ui { nav!!.popBackStack() }
        ui { nav!!.navigate(LibraryRoute(0)) }; pump(3000); save("3b-library")
        ui { nav!!.navigate(LibraryRoute(1)) }; pump(3000); save("3bb-artists")
        ui { nav!!.navigate(LibraryRoute(2)) }; pump(3000); save("3c-songs")
        ui { nav!!.navigate(LibraryRoute(3)) }; pump(2500); save("3d-playlists")
        ui { nav!!.navigate(PlaylistRoute("pl1")) }; pump(2500); save("3e-playlist")
        ui { nav!!.navigate(SearchRoute) }; pump(1500); save("3f-search")
        // Descubrir (catálogo real de Deezer: necesita internet).
        ui { nav!!.navigate(com.marco.audiopro.ui.DiscoverRoute) }; pump(20000); save("3g-discover")
        ui { nav!!.navigate(com.marco.audiopro.ui.ExternalArtistRoute(288567)) }; pump(6000); save("3h-ext-artist")
        // Muestra de 30 s de Deezer sonando en el motor de PC (necesita internet).
        runCatching { runBlocking { graph.discover.topPreviews(288567) } }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { previews ->
            graph.player.play(previews)
            ui { nav!!.navigate(PlayerRoute) }; pump(6000); save("3j-preview-player")
            check(graph.player.state.value.current?.isPreview == true) { "no suena la muestra" }
            check(graph.player.positionMs() > 500) { "la muestra no avanza: ${graph.player.positionMs()} ${graph.player.state.value.error}" }
            ui { nav!!.popBackStack() }
            graph.player.play(songs)
        }
        // Sin internet no hay catálogo: se omite esa captura.
        runCatching { runBlocking { graph.discover.discography(288567).first { it.record_type == "album" }.id } }.getOrNull()?.let { extAlbum ->
            ui { nav!!.navigate(com.marco.audiopro.ui.ExternalAlbumRoute(extAlbum)) }; pump(5000); save("3i-ext-album")
        }
        // Portada propia de "Me gusta" (la del servidor de prueba sirve de imagen).
        val img = java.net.URI(graph.api.coverUrl("al1", 300)!!).toURL().readBytes()
        runBlocking { graph.covers.set(com.marco.audiopro.data.CustomCovers.LIKED, img) }
        ui { nav!!.navigate(DspRoute) }; pump(2500); save("4-dsp")
        ui { nav!!.navigate(LikedRoute) }; pump(2000); save("5-liked")
        ui { nav!!.navigate(SettingsRoute) }; pump(1500); save("6-settings")
        // "Mi Last.fm" con un perfil falso.
        graph.settings.updatePrefs { it.copy(lastFmUser = "marcotest", lastFmKey = "prueba") }
        ui { nav!!.navigate(com.marco.audiopro.ui.LastFmRoute) }; pump(6000); save("16-lastfm")
        // La página completa (alta) en PC y en celular, para ver todas las secciones.
        for ((name, w, h) in listOf(Triple("16b-lastfm-full", 1000, 3600), Triple("16c-lastfm-phone", 412, 4200))) {
            val sc = ui { ImageComposeScene(w, h, Density(1f)) {
                WithOwners {
                    val n = rememberNavController()
                    CompositionLocalProvider(LocalApp provides graph) {
                        MarcoTheme { com.marco.audiopro.ui.screens.LastFmScreen(n) }
                    }
                }
            } }
            val e = System.currentTimeMillis() + 7000
            while (System.currentTimeMillis() < e) { ui { sc.render(System.nanoTime()) }; Thread.sleep(40) }
            File(outDir, "$name.png").writeBytes(ui { sc.render(System.nanoTime()).encodeToData(EncodedImageFormat.PNG)!!.bytes })
            ui { sc.close() }
        }
        graph.settings.updatePrefs { it.copy(lastFmUser = "", lastFmKey = "") }
        // "Inicio" desde un álbum regresa a Inicio (antes restauraba el álbum).
        ui { nav!!.navigate(AlbumRoute("al1")) }; pump(1500)
        ui { nav!!.openTab(com.marco.audiopro.ui.HomeRoute) }; pump(3000); save("6b-home-again")
        runBlocking { graph.covers.clear(com.marco.audiopro.data.CustomCovers.LIKED) }
        ui { scene.close() }

        // Todos los modos visuales a la vez, con el barrido sonando.
        val viz = ui { ImageComposeScene(1380, 860, Density(1f)) {
            CompositionLocalProvider(LocalApp provides graph) {
                MarcoTheme {
                    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        VisualPreset.entries.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { for (p in row) PresetPanel(p, graph.player.state.value.current?.coverArt) }
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

        // "Dentro de la portada": la tarjeta con toda la info del álbum, en PC y en celular.
        val fullAlbum = runBlocking { graph.api.album("al1") }
        for ((name, w, h) in listOf(Triple("14-album-info", 1380, 860), Triple("15-album-info-phone", 412, 915))) {
            val sc = ui { ImageComposeScene(w, h, Density(1f)) {
                WithOwners {
                    val n = rememberNavController()
                    CompositionLocalProvider(LocalApp provides graph) {
                        MarcoTheme { com.marco.audiopro.ui.screens.AlbumDetails(fullAlbum, n) {} }
                    }
                }
            } }
            val e = System.currentTimeMillis() + 3500
            while (System.currentTimeMillis() < e) { ui { sc.render(System.nanoTime()) }; Thread.sleep(30) }
            File(outDir, "$name.png").writeBytes(ui { sc.render(System.nanoTime()).encodeToData(EncodedImageFormat.PNG)!!.bytes })
            ui { sc.close() }
        }

        // Miniplayer iPod (sin ventana: sólo el cuerpo, sobre fondo gris para ver la sombra):
        // los tres tamaños y la pantalla con cada visualizador.
        fun ipodShot(name: String, size: IpodSize, screen: IpodScreen) {
            graph.settings.putString("ipod_screen", screen.name)
            val w = (178 * size.scale).toInt(); val h = (266 * size.scale).toInt()
            val ipod = ui { ImageComposeScene(w, h, Density(1f)) {
                WithOwners {
                    CompositionLocalProvider(LocalApp provides graph) {
                        MarcoTheme(background = false) {
                            Box(Modifier.fillMaxSize().background(Color(0xFF7A7F88))) { IpodBody(graph.player, onExpand = {}, size = size) { it() } }
                        }
                    }
                }
            } }
            val iEnd = System.currentTimeMillis() + 2500
            while (System.currentTimeMillis() < iEnd) { ui { ipod.render(System.nanoTime()) }; Thread.sleep(30) }
            File(outDir, "$name.png").writeBytes(ui { ipod.render(System.nanoTime()).encodeToData(EncodedImageFormat.PNG)!!.bytes })
            ui { ipod.close() }
        }
        ipodShot("12-ipod", IpodSize.M, IpodScreen.INFO)
        ipodShot("12b-ipod-s", IpodSize.S, IpodScreen.INFO)
        ipodShot("12c-ipod-l-neon", IpodSize.L, IpodScreen.NEON)
        ipodShot("12d-ipod-hiper", IpodSize.M, IpodScreen.HIPER)
        ipodShot("12e-ipod-halo", IpodSize.M, IpodScreen.HALO)
        graph.settings.putString("ipod_screen", IpodScreen.INFO.name)
        graph.shutdown()
    }
}

@androidx.compose.runtime.Composable
private fun PresetPanel(p: VisualPreset, cover: String?) {
    Column {
        Text(p.label, style = Mono.copy(color = Hifi.TextDim))
        Box(Modifier.size(320.dp, 380.dp)) { AmbientBackground(cover, preset = p) }
    }
}
