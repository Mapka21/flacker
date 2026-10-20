package com.marco.audiopro.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.marco.audiopro.ui.DesktopRoot
import com.marco.audiopro.ui.GenresRoute
import com.marco.audiopro.ui.LibraryRoute
import com.marco.audiopro.ui.MarcoRoot
import com.marco.audiopro.ui.PlayerRoute
import com.marco.audiopro.ui.ProfileRoute
import com.marco.audiopro.ui.components.AsciiArt
import com.marco.audiopro.ui.components.AsciiSkull
import com.marco.audiopro.ui.components.BrandColors
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.MarcoTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.swing.SwingUtilities

private object ReadmeOwner : LifecycleOwner, ViewModelStoreOwner {
    override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val viewModelStore = ViewModelStore()
}

/**
 * Las imágenes del README (banner, PC, celular, iPod) contra la biblioteca de muestra
 * (tools/fake-navidrome/showcase.py). Mismos requisitos que [ScreenshotTest].
 */
@OptIn(ExperimentalComposeUiApi::class)
class ReadmeShotsTest {
    @Test fun captureReadme() {
        com.marco.audiopro.ui.components.SplashState.done = true
        val outDir = System.getenv("MARCO_SHOTS_DIR")
        assumeTrue("Sin servidor/carpeta de capturas", System.getenv("MARCO_TEST_SERVER") != null && outDir != null)
        System.setProperty("marco.profile", "dev")
        System.setProperty("flacker.lastfm.base", "http://127.0.0.1:8765/2.0/")
        val desktop = DesktopGraph()
        SingletonImageLoader.setSafe { ctx ->
            ImageLoader.Builder(ctx).components { add(OkHttpNetworkFetcherFactory(callFactory = { desktop.http })) }.build()
        }
        desktop.player.setVolume(0f)
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
        val d = 1.5f
        fun scene(w: Int, h: Int, density: Float = d, content: @androidx.compose.runtime.Composable () -> Unit) = ui {
            ImageComposeScene((w * density).toInt(), (h * density).toInt(), Density(density)) {
                CompositionLocalProvider(LocalLifecycleOwner provides ReadmeOwner, LocalViewModelStoreOwner provides ReadmeOwner) { content() }
            }
        }

        // Banner: la calavera de letras y la marca, en negro, gris y blanco.
        val banner = scene(1600, 520) {
            MarcoTheme {
                Box(Modifier.fillMaxSize().background(BrandColors.Ink), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        AsciiArt(AsciiSkull.BIG, fontSize = 13.sp)
                        Spacer(Modifier.width(70.dp))
                        Column {
                            Text(
                                buildAnnotatedString {
                                    append("FLACKER")
                                    withStyle(SpanStyle(fontSize = 44.sp, color = BrandColors.Bone, baselineShift = BaselineShift(0.9f), letterSpacing = 4.sp)) { append(" 333") }
                                },
                                style = Display.copy(fontSize = 96.sp, fontWeight = FontWeight.Black, letterSpacing = 15.sp, color = BrandColors.White),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Tu música en alta fidelidad · Android y Windows", fontSize = 26.sp, color = Color.White.copy(alpha = 0.72f))
                            Spacer(Modifier.height(4.dp))
                            Text("Cliente de Navidrome / Subsonic · también sin servidor", fontSize = 20.sp, color = Color.White.copy(alpha = 0.42f))
                        }
                    }
                }
            }
        }
        shot(banner, "banner", 1500)
        ui { banner.close() }

        val songs = runBlocking { desktop.api.album("al1").song }
        desktop.player.play(songs)
        desktop.player.seekTo(17_000)

        // PC.
        var nav: NavHostController? = null
        val pc = scene(1600, 1000) {
            nav = rememberNavController()
            CompositionLocalProvider(LocalApp provides desktop) { MarcoTheme { DesktopRoot(nav!!, onMiniPlayer = {}) } }
        }
        shot(pc, "pc-home", 5000)
        ui { nav!!.navigate(AlbumRoute("al3")) }; shot(pc, "pc-album", 3500)
        ui { nav!!.navigate(ArtistRoute("ar6")) }; shot(pc, "pc-artist", 3500)
        ui { nav!!.navigate(GenresRoute) }; shot(pc, "pc-genres", 4000)
        ui { nav!!.navigate(ProfileRoute) }; shot(pc, "pc-profile", 3500)
        desktop.player.seekTo(13_000)
        ui { nav!!.navigate(PlayerRoute) }; shot(pc, "pc-player", 3000)
        ui { pc.close() }
        val imm = scene(1600, 1000) {
            CompositionLocalProvider(LocalApp provides desktop) { MarcoTheme { com.marco.audiopro.ui.ImmersiveScreen {} } }
        }
        desktop.player.seekTo(8_600)
        shot(imm, "pc-lyrics", 2500)
        ui { imm.close() }

        // iPod.
        val ipod = scene(178 * 2, 266 * 2, 1.5f) {
            CompositionLocalProvider(LocalApp provides desktop) {
                MarcoTheme(background = false) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0D)).padding(0.dp)) { IpodBody(desktop.player, onExpand = {}, size = IpodSize.L) { it() } }
                }
            }
        }
        shot(ipod, "ipod", 3000)
        ui { ipod.close() }

        // Celular.
        val phone: AppGraph = object : AppGraph by desktop { override val isDesktop = false }
        var pnav: NavHostController? = null
        val cel = scene(412, 915, 2.5f) {
            pnav = rememberNavController()
            CompositionLocalProvider(LocalApp provides phone) { MarcoTheme { MarcoRoot(pnav!!) } }
        }
        shot(cel, "cel-home", 4000)
        ui { pnav!!.navigate(PlayerRoute) }; shot(cel, "cel-player", 4000)
        ui { pnav!!.popBackStack() }
        ui { pnav!!.navigate(LibraryRoute(0)) }; shot(cel, "cel-library", 3000)
        ui { pnav!!.navigate(ProfileRoute) }; shot(cel, "cel-profile", 3000)
        ui { cel.close() }
        desktop.shutdown()
    }
}
