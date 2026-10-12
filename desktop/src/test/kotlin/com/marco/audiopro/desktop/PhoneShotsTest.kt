package com.marco.audiopro.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
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
import com.marco.audiopro.core.AppGraph
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.ui.AlbumRoute
import com.marco.audiopro.ui.ArtistRoute
import com.marco.audiopro.ui.DiscoverRoute
import com.marco.audiopro.ui.LibraryRoute
import com.marco.audiopro.ui.LikedRoute
import com.marco.audiopro.ui.MarcoRoot
import com.marco.audiopro.ui.PlayerRoute
import com.marco.audiopro.ui.SearchRoute
import com.marco.audiopro.ui.SettingsRoute
import com.marco.audiopro.ui.theme.MarcoTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.swing.SwingUtilities

private object PhoneOwner : LifecycleOwner, ViewModelStoreOwner {
    override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val viewModelStore = ViewModelStore()
}

/**
 * La app del celular tal cual (isDesktop = false), a 412×915 dp con densidad de teléfono, contra
 * el servidor de prueba. Mismos requisitos que [ScreenshotTest].
 */
@OptIn(ExperimentalComposeUiApi::class)
class PhoneShotsTest {
    @Test fun capturePhone() {
        val outDir = System.getenv("MARCO_SHOTS_DIR")
        assumeTrue("Sin servidor/carpeta de capturas", System.getenv("MARCO_TEST_SERVER") != null && outDir != null)
        System.setProperty("marco.profile", "dev")
        val desktop = DesktopGraph()
        // La misma app, pero como celular (sin volumen digital: el del sistema).
        val graph: AppGraph = object : AppGraph by desktop { override val isDesktop = false }
        SingletonImageLoader.setSafe { ctx ->
            ImageLoader.Builder(ctx).components { add(OkHttpNetworkFetcherFactory(callFactory = { desktop.http })) }.build()
        }
        desktop.player.setVolume(0f)
        fun <T> ui(block: () -> T): T {
            var r: Result<T>? = null
            SwingUtilities.invokeAndWait { r = runCatching(block) }
            return r!!.getOrThrow()
        }
        val d = 2.5f
        var nav: NavHostController? = null
        val scene = ui { ImageComposeScene((412 * d).toInt(), (915 * d).toInt(), Density(d)) {
            CompositionLocalProvider(LocalLifecycleOwner provides PhoneOwner, LocalViewModelStoreOwner provides PhoneOwner) {
                nav = rememberNavController()
                CompositionLocalProvider(LocalApp provides graph) { MarcoTheme { MarcoRoot(nav!!) } }
            }
        } }
        fun shot(name: String, ms: Long) {
            val end = System.currentTimeMillis() + ms
            while (System.currentTimeMillis() < end) { ui { scene.render(System.nanoTime()) }; Thread.sleep(25) }
            File(outDir, "$name.png").writeBytes(ui { scene.render(System.nanoTime()).encodeToData(EncodedImageFormat.PNG)!!.bytes })
        }
        shot("p01-home-empty", 3000)
        val songs = runBlocking { desktop.api.album("al1").song }
        desktop.player.play(songs)
        shot("p02-home", 3000)
        ui { nav!!.navigate(LibraryRoute(0)) }; shot("p03-library-albums", 2500)
        ui { nav!!.navigate(LibraryRoute(1)) }; shot("p04-library-artists", 2500)
        ui { nav!!.navigate(LibraryRoute(2)) }; shot("p05-library-songs", 2500)
        ui { nav!!.navigate(LibraryRoute(3)) }; shot("p06-library-playlists", 2500)
        ui { nav!!.navigate(AlbumRoute("al1")) }; shot("p07-album", 3000)
        ui { nav!!.navigate(ArtistRoute("ar1")) }; shot("p08-artist", 3000)
        ui { nav!!.navigate(LikedRoute) }; shot("p09-liked", 2500)
        ui { nav!!.navigate(SearchRoute) }; shot("p10-search", 2000)
        ui { nav!!.navigate(DiscoverRoute) }; shot("p11-discover", 12000)
        ui { nav!!.navigate(SettingsRoute) }; shot("p12-settings", 2000)
        ui { nav!!.navigate(PlayerRoute) }; shot("p13-player", 3500)
        ui { scene.close() }
        desktop.shutdown()
    }
}
