package com.marco.audiopro.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.window.WindowPlacement
import com.marco.audiopro.ui.WindowChrome
import com.marco.audiopro.ui.Immersive
import com.marco.audiopro.ui.WindowControls
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
import com.marco.audiopro.ui.components.TextInputFocus
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.MouseEvent
import com.marco.audiopro.ui.theme.MarcoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
        // Al arrastrar el volumen o un slider llegan decenas de cambios por segundo: se guarda a disco
        // una sola vez cuando se calman (vaciar el registro en cada uno da tirones).
        synchronized(this) {
            pendingFlush?.cancel(false)
            pendingFlush = flusher.schedule({ runCatching { p.flush() } }, 700, TimeUnit.MILLISECONDS)
        }
    }

    private var pendingFlush: java.util.concurrent.ScheduledFuture<*>? = null
    private val flusher = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "flacker-prefs").apply { isDaemon = true } }

    /** Al cerrar: lo pendiente se guarda ya. */
    fun flushNow() { synchronized(this) { pendingFlush?.cancel(false) }; runCatching { p.flush() } }
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

    private val prefsStore = PrefsStore(Preferences.userRoot().node("marco-audio-pro$profileSuffix"))
    override val settings = Settings(prefsStore)
    override val api = SubsonicApi(http) { settings.account.value }
    /** Scrobbles que se guardan si no hay red y se mandan después. */
    val scrobbles = com.marco.audiopro.data.ScrobbleQueue(api, dataDir, scope).also { api.offlineQueue = it }
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
    override val artistPhotos = com.marco.audiopro.data.ArtistPhotos(http, api, dataDir)
    override val localLibrary = com.marco.audiopro.data.local.LocalLibrary(
        dataDir, FolderSource { com.marco.audiopro.data.local.LocalMode.folders(settings).map(::File) },
    ).also { com.marco.audiopro.data.local.LocalMode.resume(it, settings) }
    override val lyrics = LyricsRepository(http, api, dataDir)
    override val lastFm = com.marco.audiopro.data.info.LastFmProfile(http)
    override val connect = ConnectService(
        System.getenv("COMPUTERNAME")?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "PC", "windows", settings, desktopPlayer, scope,
    )
    /** Lo que ve la UI: este reproductor o el del dispositivo que controlas con Connect. */
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val musicBrainz = com.marco.audiopro.data.meta.MusicBrainz(http)
    override val radio = com.marco.audiopro.data.radio.RadioBrowser(http)
    override val genres = com.marco.audiopro.data.meta.GenreIndex(api, musicBrainz, dataDir, scope)
    override val fixer = com.marco.audiopro.data.meta.MetadataFixer(musicBrainz, JaudioTagIO(), lyrics, dataDir, genres)
    override val cast = com.marco.audiopro.cast.CastService(http, api, uiScope, isLocal = { com.marco.audiopro.data.local.LocalMode.isOn(settings) }, decoder = FfmpegPcmDecoder())
    override val player = RoutedPlayer(desktopPlayer, connect, uiScope, cast)
    /** Me gusta, playlists, portadas y ajustes al día entre tus dispositivos. */
    val sync by lazy { com.marco.audiopro.data.SyncService(settings, covers, likes, playlists, connect, scope) }

    init { scrobbles.onSynced = { n -> connect.notice.value = "Se sincronizaron $n scrobbles que tenías sin conexión" } }
    override val downloads = null
    override val cache = null
    override val versionName = "3.1.0"
    override val isDesktop = true

    fun shutdown() { desktopPlayer.shutdown(); prefsStore.flushNow() }
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
            LaunchedEffect(Unit) { window.useFlackerIcons() }
            // Pantalla completa de verdad para el modo inmersivo: la ventana tapa todo el monitor.
            LaunchedEffect(Unit) {
                var saved: java.awt.Rectangle? = null
                var wasMax = false
                androidx.compose.runtime.snapshotFlow { Immersive.on }.collect { on ->
                    if (on && saved == null) {
                        wasMax = windowState.placement == WindowPlacement.Maximized
                        if (wasMax) { windowState.placement = WindowPlacement.Floating; kotlinx.coroutines.delay(150) }
                        saved = window.bounds
                        val b = window.graphicsConfiguration.bounds
                        val ins = window.insets
                        // Los bordes invisibles de Windows quedan fuera: el área de la app = el monitor exacto.
                        window.setBounds(b.x - ins.left, b.y - ins.top, b.width + ins.left + ins.right, b.height + ins.top + ins.bottom)
                        window.toFront()
                    } else if (!on && saved != null) {
                        window.bounds = saved
                        saved = null
                        if (wasMax) windowState.placement = WindowPlacement.Maximized
                    }
                }
            }
            // Botones ⏮ ⏯ ⏭ en la barra de tareas, "Reproduciendo" en el control multimedia de Windows
            // y la barra de título propia (como Discord): si se pudo, la app dibuja sus botones.
            val maximized = remember { derivedStateOf { windowState.placement == WindowPlacement.Maximized } }
            LaunchedEffect(Unit) { Gamepad.start(this, player) { window.isFocused } }
            LaunchedEffect(Unit) {
                val custom = WindowsShell.install(
                    window, player, graph.http, { graph.api.coverUrl(it, 400) },
                    // -Dflacker.nativeTitle=true deja la barra de título de Windows (por si algo falla).
                    isDragArea = if (System.getProperty("flacker.nativeTitle") == "true") null else { x, y -> WindowChrome.isDragArea(x, y) },
                )
                // -Dflacker.debugImmersive=true: entra a pantalla completa a los 6 s (para probarla).
                if (System.getProperty("flacker.debugImmersive") == "true") launch { kotlinx.coroutines.delay(6000); Immersive.on = true }
                // -Dflacker.debugWindow=true: maximiza a los 8 s y restaura a los 16 s (para probar el marco propio).
                if (System.getProperty("flacker.debugWindow") == "true") launch {
                    kotlinx.coroutines.delay(8000); repeat(3) { System.gc() }; windowState.placement = WindowPlacement.Maximized
                    kotlinx.coroutines.delay(8000); windowState.placement = WindowPlacement.Floating
                }
                if (custom) WindowChrome.controls = WindowControls(
                    minimize = { windowState.isMinimized = true },
                    toggleMaximize = {
                        windowState.placement = if (windowState.placement == WindowPlacement.Maximized) WindowPlacement.Floating else WindowPlacement.Maximized
                    },
                    close = { graph.shutdown(); exitApplication() },
                    maximized = maximized,
                )
            }
            CompositionLocalProvider(LocalApp provides graph) {
                MarcoTheme { DesktopRoot(onMiniPlayer = { mini = true }) }
            }
        }
        if (mini) {
            IpodWindow(graph, onExpand = { mini = false }, onKey = { e -> handleShortcut(e, player) })
        }
    }
}

/** Espacio suelto: se ignora la repetición al dejarlo apretado. */
private var spaceHeld = false

/**
 * Atajos como Spotify: Espacio (o Ctrl+Espacio) = play/pausa, Ctrl+→/← = siguiente/anterior,
 * Ctrl+↑/↓ = volumen, Alt+←/→ = atrás/adelante, teclas multimedia. Espacio no pausa mientras
 * escribes en un campo de texto.
 */
fun handleShortcut(e: KeyEvent, player: PlayerController): Boolean {
    if (e.key == Key.Spacebar && !e.isAltPressed && (e.isCtrlPressed || !TextInputFocus.active)) {
        if (e.type == KeyEventType.KeyUp) { spaceHeld = false; return true }
        if (e.type == KeyEventType.KeyDown && !spaceHeld) { spaceHeld = true; player.toggle() }
        return true
    }
    if (e.type != KeyEventType.KeyDown) return false
    return when {
        e.key == Key.F11 -> { Immersive.on = !Immersive.on; true }
        e.key == Key.Escape && Immersive.on -> { Immersive.on = false; true }
        e.key == Key.L && Immersive.on -> { Immersive.cycleMode(); true }
        Immersive.on && !e.isCtrlPressed && !e.isAltPressed && immersiveKey(e) -> true
        e.isCtrlPressed && e.key == Key.DirectionRight -> { player.next(); true }
        e.isCtrlPressed && e.key == Key.DirectionLeft -> { player.previous(); true }
        e.isCtrlPressed && e.key == Key.DirectionUp -> { player.setVolume(((player.volume?.value ?: 0f) + 0.05f).coerceIn(0f, 1f)); true }
        e.isCtrlPressed && e.key == Key.DirectionDown -> { player.setVolume(((player.volume?.value ?: 0f) - 0.05f).coerceIn(0f, 1f)); true }
        e.isAltPressed && e.key == Key.DirectionLeft -> { DesktopHistory.events.tryEmit(false); true }
        e.isAltPressed && e.key == Key.DirectionRight -> { DesktopHistory.events.tryEmit(true); true }
        e.key == Key.MediaPlayPause -> { if (MediaKeyGate.pass(0)) player.toggle(); true }
        e.key == Key.MediaNext -> { if (MediaKeyGate.pass(1)) player.next(); true }
        e.key == Key.MediaPrevious -> { if (MediaKeyGate.pass(2)) player.previous(); true }
        else -> false
    }
}

/**
 * La calavera ASCII en todos los tamaños (16 a 256 px, hechos con tools/brand/make_icons.py): así la
 * barra de tareas usa la versión chica nítida en vez de encoger la grande.
 */
fun java.awt.Window.useFlackerIcons() {
    val imgs = listOf(16, 20, 24, 32, 40, 48, 64, 128, 256).mapNotNull { n ->
        DesktopGraph::class.java.getResource("/icons/flacker-$n.png")?.let { runCatching { javax.imageio.ImageIO.read(it) }.getOrNull() }
    }
    if (imgs.isNotEmpty()) iconImages = imgs
}

/**
 * En pantalla completa, como con el control: ←/→ giran, ↑/↓ acercan, +/− velocidad, F forma de las
 * ondas, Espacio sigue siendo pausa, [ y ] mueven la letra, 0 regresa todo a como estaba.
 */
private fun immersiveKey(e: KeyEvent): Boolean {
    val vc = com.marco.audiopro.ui.viz.VizControl
    when (e.key) {
        Key.DirectionLeft -> vc.spin = (vc.spin - 30f).coerceAtLeast(-180f)
        Key.DirectionRight -> vc.spin = (vc.spin + 30f).coerceAtMost(180f)
        Key.DirectionUp -> vc.zoom = (vc.zoom + 0.08f).coerceAtMost(0.8f)
        Key.DirectionDown -> vc.zoom = (vc.zoom - 0.08f).coerceAtLeast(-0.4f)
        Key.Equals, Key.Plus, Key.NumPadAdd -> { vc.speed = (vc.speed * 1.25f).coerceAtMost(4f); Immersive.toast = "Velocidad ×%.1f".format(vc.speed) }
        Key.Minus, Key.NumPadSubtract -> { vc.speed = (vc.speed / 1.25f).coerceAtLeast(0.25f); Immersive.toast = "Velocidad ×%.1f".format(vc.speed) }
        Key.F -> vc.shape++
        Key.N, Key.PageDown -> { com.marco.audiopro.ui.viz.Neon360Control.move(1, 0); Immersive.toast = "Escena del 360: ${com.marco.audiopro.ui.viz.Neon360Control.scene + 1}" }
        Key.PageUp -> { com.marco.audiopro.ui.viz.Neon360Control.move(-1, 0); Immersive.toast = "Escena del 360: ${com.marco.audiopro.ui.viz.Neon360Control.scene + 1}" }
        Key.A -> Immersive.swapNow = true
        Key.LeftBracket -> Immersive.nudgeLyrics(-100)
        Key.RightBracket -> Immersive.nudgeLyrics(100)
        Key.Zero -> { vc.reset(); com.marco.audiopro.ui.viz.Neon360Control.manual = false; Immersive.toast = "Visuales como estaban (escenas solas)" }
        else -> return false
    }
    return true
}
