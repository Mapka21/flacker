package com.marco.audiopro.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
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
import com.marco.audiopro.data.info.LyricsRepository
import com.marco.audiopro.connect.ConnectService
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.resources.Res
import com.marco.audiopro.resources.app_icon
import com.marco.audiopro.player.PlayerController
import com.marco.audiopro.player.RoutedPlayer
import com.marco.audiopro.ui.DesktopHistory
import com.marco.audiopro.ui.DesktopRoot
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.MouseEvent
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
    // Carpeta y nodo de ajustes conservan el nombre viejo para no perder sesión, EQ ni caché.
    val dataDir = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "MarcoAudioPro$profileSuffix").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override val settings = Settings(PrefsStore(Preferences.userRoot().node("marco-audio-pro$profileSuffix")))
    override val api = SubsonicApi(http) { settings.account.value }
    /** El motor de audio de esta PC (atajos e iPod usan [player], que puede ser remoto). */
    val desktopPlayer = DesktopPlayer(api, settings, scope)
    override val engine = AudioEngine(settings, desktopPlayer.tap).also { e ->
        desktopPlayer.eq = e.eq
        desktopPlayer.onSignal = { e.signal.value = it }
    }
    override val albumInfo = AlbumInfoRepository(dataDir, api, LastFm(http), ClaudeReviewer(), settings)
    override val likes = Likes(api, scope)
    override val discover = com.marco.audiopro.data.discover.DiscoverRepository(http, api, dataDir)
    override val playlists = com.marco.audiopro.data.PlaylistStore(api)
    override val covers = com.marco.audiopro.data.CustomCovers(dataDir)
    override val lyrics = LyricsRepository(http, api, dataDir)
    override val lastFm = com.marco.audiopro.data.info.LastFmProfile(http)
    override val connect = ConnectService(
        System.getenv("COMPUTERNAME")?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "PC", "windows", settings, desktopPlayer, scope,
    )
    /** Lo que ve la UI: este reproductor o el del dispositivo que controlas con Connect. */
    override val player = RoutedPlayer(desktopPlayer, connect, CoroutineScope(SupervisorJob() + Dispatchers.Main))
    /** Me gusta, playlists, portadas y ajustes al día entre tus dispositivos. */
    val sync by lazy { com.marco.audiopro.data.SyncService(settings, covers, likes, playlists, connect, scope) }
    override val downloads = null
    override val cache = null
    override val versionName = "2.3.0"
    override val isDesktop = true

    fun shutdown() = desktopPlayer.shutdown()
}

fun main() {
    val graph = DesktopGraph()
    graph.connect.start()
    graph.sync.start()
    // Botones laterales del mouse (atrás / adelante), como en el navegador o Spotify.
    Toolkit.getDefaultToolkit().addAWTEventListener({ e ->
        if (e is MouseEvent && e.id == MouseEvent.MOUSE_PRESSED) when (e.button) {
            4 -> DesktopHistory.events.tryEmit(false)
            5 -> DesktopHistory.events.tryEmit(true)
        }
    }, AWTEvent.MOUSE_EVENT_MASK)
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
        // -Dmarco.startMini=true arranca directo en el miniplayer (útil para probarlo).
        var mini by remember { mutableStateOf(System.getProperty("marco.startMini") == "true") }
        Window(
            onCloseRequest = { graph.shutdown(); exitApplication() },
            state = windowState,
            // La ventana grande se oculta (no se cierra) mientras el miniplayer está activo.
            visible = !mini,
            title = "Flacker",
            icon = painterResource(Res.drawable.app_icon),
            onPreviewKeyEvent = { e -> handleShortcut(e, player) },
        ) {
            window.minimumSize = Dimension(1050, 700)
            CompositionLocalProvider(LocalApp provides graph) {
                MarcoTheme { DesktopRoot(onMiniPlayer = { mini = true }) }
            }
        }
        if (mini) {
            IpodWindow(graph, onExpand = { mini = false }, onKey = { e -> handleShortcut(e, player) })
        }
    }
}

/** Atajos: Ctrl+Espacio = play/pausa, Ctrl+→/← = siguiente/anterior, Ctrl+↑/↓ = volumen, Alt+←/→ = atrás/adelante, teclas multimedia. */
fun handleShortcut(e: KeyEvent, player: PlayerController): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    return when {
        e.isCtrlPressed && e.key == Key.Spacebar -> { player.toggle(); true }
        e.isCtrlPressed && e.key == Key.DirectionRight -> { player.next(); true }
        e.isCtrlPressed && e.key == Key.DirectionLeft -> { player.previous(); true }
        e.isCtrlPressed && e.key == Key.DirectionUp -> { player.setVolume((player.volume?.value ?: 0f) + 0.05f); true }
        e.isCtrlPressed && e.key == Key.DirectionDown -> { player.setVolume((player.volume?.value ?: 0f) - 0.05f); true }
        e.isAltPressed && e.key == Key.DirectionLeft -> { DesktopHistory.events.tryEmit(false); true }
        e.isAltPressed && e.key == Key.DirectionRight -> { DesktopHistory.events.tryEmit(true); true }
        e.key == Key.MediaPlayPause -> { player.toggle(); true }
        e.key == Key.MediaNext -> { player.next(); true }
        e.key == Key.MediaPrevious -> { player.previous(); true }
        else -> false
    }
}
