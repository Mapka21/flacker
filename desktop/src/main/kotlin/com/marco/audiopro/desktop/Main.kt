package com.marco.audiopro.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.marco.audiopro.audio.AudioEngine
import com.marco.audiopro.core.AppGraph
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.data.KeyValueStore
import com.marco.audiopro.data.Likes
import com.marco.audiopro.data.Settings
import com.marco.audiopro.data.info.AlbumInfoRepository
import com.marco.audiopro.data.info.ClaudeReviewer
import com.marco.audiopro.data.info.LastFm
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.resources.Res
import com.marco.audiopro.resources.app_icon
import com.marco.audiopro.ui.DesktopRoot
import com.marco.audiopro.ui.theme.MarcoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import org.jetbrains.compose.resources.painterResource
import java.awt.Dimension
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.prefs.Preferences

/** java.util.prefs (registro de Windows) como almacén de [Settings]. */
private class PrefsStore(private val p: Preferences) : KeyValueStore {
    override fun getString(key: String): String? = p.get(key, null)
    override fun getFloat(key: String, def: Float) = p.getFloat(key, def)
    override fun getInt(key: String, def: Int) = p.getInt(key, def)
    override fun getBoolean(key: String, def: Boolean) = p.getBoolean(key, def)
    override fun edit(values: Map<String, Any?>) {
        values.forEach { (k, v) ->
            when (v) {
                null -> p.remove(k)
                is Float -> p.putFloat(k, v)
                is Int -> p.putInt(k, v)
                is Boolean -> p.putBoolean(k, v)
                else -> p.put(k, v.toString())
            }
        }
        runCatching { p.flush() }
    }
}

/** Perfil opcional (-Dmarco.profile=dev): ajustes y caché separados, útil para pruebas. */
private val profileSuffix = System.getProperty("marco.profile")?.takeIf { it.isNotBlank() }?.let { "-$it" } ?: ""

class DesktopGraph : AppGraph {
    val dataDir = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "MarcoAudioPro$profileSuffix").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override val settings = Settings(PrefsStore(Preferences.userRoot().node("marco-audio-pro$profileSuffix")))
    override val api = SubsonicApi(http) { settings.account.value }
    private val desktopPlayer = DesktopPlayer(api, settings, scope)
    override val player = desktopPlayer
    override val engine = AudioEngine(settings, desktopPlayer.tap).also { e ->
        desktopPlayer.eq = e.eq
        desktopPlayer.onSignal = { e.signal.value = it }
    }
    override val albumInfo = AlbumInfoRepository(dataDir, api, LastFm(http), ClaudeReviewer(), settings)
    override val likes = Likes(api, scope)
    override val downloads = null
    override val cache = null
    override val versionName = "1.2.0"
    override val isDesktop = true

    fun shutdown() = desktopPlayer.shutdown()
}

fun main() {
    val graph = DesktopGraph()
    SingletonImageLoader.setSafe { ctx ->
        ImageLoader.Builder(ctx)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { graph.http })) }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(graph.dataDir, "covers").toOkioPath())
                    .maxSizeBytes(500L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    application {
        val windowState = rememberWindowState(width = 1380.dp, height = 880.dp)
        val player = remember { graph.player }
        Window(
            onCloseRequest = { graph.shutdown(); exitApplication() },
            state = windowState,
            title = "Marco Audio Pro",
            icon = painterResource(Res.drawable.app_icon),
            onPreviewKeyEvent = { e ->
                // Atajos: Ctrl+Espacio = play/pausa, Ctrl+→/← = siguiente/anterior, Ctrl+↑/↓ = volumen.
                if (e.type != KeyEventType.KeyDown) return@Window false
                when {
                    e.isCtrlPressed && e.key == Key.Spacebar -> { player.toggle(); true }
                    e.isCtrlPressed && e.key == Key.DirectionRight -> { player.next(); true }
                    e.isCtrlPressed && e.key == Key.DirectionLeft -> { player.previous(); true }
                    e.isCtrlPressed && e.key == Key.DirectionUp -> { player.setVolume(player.volume.value + 0.05f); true }
                    e.isCtrlPressed && e.key == Key.DirectionDown -> { player.setVolume(player.volume.value - 0.05f); true }
                    e.key == Key.MediaPlayPause -> { player.toggle(); true }
                    e.key == Key.MediaNext -> { player.next(); true }
                    e.key == Key.MediaPrevious -> { player.previous(); true }
                    else -> false
                }
            },
        ) {
            window.minimumSize = Dimension(1050, 700)
            CompositionLocalProvider(LocalApp provides graph) {
                MarcoTheme { DesktopRoot() }
            }
        }
    }
}
