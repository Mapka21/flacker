package com.marco.audiopro.data

import com.marco.audiopro.connect.ConnectService
import com.marco.audiopro.connect.SyncSource
import com.marco.audiopro.data.subsonic.SubsonicJson
import com.marco.audiopro.ui.theme.AccentTheme
import com.marco.audiopro.ui.viz.VisualPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.util.Base64

/** Ajustes que se comparten entre tus dispositivos (no los de audio, que dependen del equipo). */
@Serializable
data class SharedPrefs(
    val lastFmUser: String = "",
    val lastFmKey: String = "",
    val anthropicKey: String = "",
    val accent: String = "",
    val visualMode: Boolean = true,
    val visualPreset: String = "",
    /** Cuándo se cambió alguno (ms). */
    val ts: Long = 0,
    /** Si trae las API keys: sólo entre dispositivos que iniciaron sesión con la contraseña. */
    val withKeys: Boolean = false,
)

@Serializable
private data class SyncManifest(val prefs: SharedPrefs, val covers: Map<String, Long>)

/**
 * Mantiene tus dispositivos al día:
 * - Me gusta y playlists viven en Navidrome: se recargan solos cada minuto y al instante cuando
 *   otro dispositivo de la red avisa que cambió algo.
 * - Portadas propias y ajustes compartidos (Last.fm, IA, color, modo visual) viajan por Connect
 *   entre dispositivos de la misma red: gana el cambio más reciente.
 */
class SyncService(
    private val settings: Settings,
    private val covers: CustomCovers,
    private val likes: Likes,
    private val playlists: PlaylistStore,
    private val connect: ConnectService,
    private val scope: CoroutineScope,
) : SyncSource {
    private val lock = Mutex()
    /** Lo último que llegó de otro dispositivo: al verlo pasar no se re-fecha ni se re-avisa. */
    @Volatile private var lastApplied: List<String>? = null

    private fun subset(p: AppPrefs) = listOf(p.lastFmUser, p.lastFmKey, p.anthropicKey, p.accent.name, p.visualMode.toString(), p.visualPreset.name)

    private fun localPrefs(): SharedPrefs {
        val p = settings.prefs.value
        // Las API keys sólo viajan si las órdenes van protegidas con la contraseña (ver ConnectService).
        val keys = settings.connectSecret() != null
        return SharedPrefs(
            p.lastFmUser, if (keys) p.lastFmKey else "", if (keys) p.anthropicKey else "", p.accent.name, p.visualMode, p.visualPreset.name,
            settings.getString("sync_prefs_ts")?.toLongOrNull() ?: 0, withKeys = keys,
        )
    }

    override fun manifest(): String = SubsonicJson.encodeToString(SyncManifest(localPrefs(), covers.manifest()))

    override fun blob(key: String): String? = covers.bytes(key)?.let { Base64.getEncoder().encodeToString(it) }

    fun start() {
        likes.onChanged = { connect.broadcastRefresh() }
        playlists.onChanged = { connect.broadcastRefresh() }
        covers.onChanged = { connect.broadcastRefresh() }
        connect.sync = this
        connect.onRefresh = { scope.launch { refreshLibrary(); pullAll() } }

        // Un cambio en los ajustes compartidos se fecha y se avisa a los demás.
        scope.launch {
            settings.prefs.map(::subset).distinctUntilChanged().drop(1).collect { v ->
                if (v != lastApplied) {
                    settings.putString("sync_prefs_ts", System.currentTimeMillis().toString())
                    connect.broadcastRefresh()
                }
            }
        }
        // Cada minuto (y cuando aparece un dispositivo nuevo): biblioteca del servidor y lo de los demás.
        scope.launch {
            var seen = emptySet<String>()
            var tick = 0
            while (isActive) {
                val ids = connect.devices.value.filter { !it.paired }.map { it.id }.toSet()
                val newcomers = ids - seen
                seen = ids
                if (newcomers.isNotEmpty() || tick % 12 == 0) pullAll()
                if (tick % 12 == 0 && tick > 0) refreshLibrary()
                tick++
                delay(5_000)
            }
        }
    }

    private suspend fun refreshLibrary() {
        runCatching { likes.refresh() }
        runCatching { playlists.remoteChanged() }
    }

    private suspend fun pullAll() = lock.withLock {
        // Los emparejados son de otra cuenta: con ellos no se comparten ajustes.
        for (d in connect.devices.value.filter { !it.paired }) runCatching { pull(d) }
    }

    private suspend fun pull(d: com.marco.audiopro.connect.RemoteDevice) {
        val m = connect.ask(d, "manifest")?.let { SubsonicJson.decodeFromString<SyncManifest>(it) } ?: return
        val mine = localPrefs()
        if (m.prefs.ts > mine.ts) {
            val next = settings.prefs.value.let { p ->
                p.copy(
                    lastFmUser = m.prefs.lastFmUser,
                    lastFmKey = if (m.prefs.withKeys) m.prefs.lastFmKey else p.lastFmKey,
                    anthropicKey = if (m.prefs.withKeys) m.prefs.anthropicKey else p.anthropicKey,
                    accent = runCatching { AccentTheme.valueOf(m.prefs.accent) }.getOrDefault(p.accent),
                    visualMode = m.prefs.visualMode,
                    visualPreset = runCatching { VisualPreset.valueOf(m.prefs.visualPreset) }.getOrDefault(p.visualPreset),
                )
            }
            lastApplied = subset(next)
            settings.putString("sync_prefs_ts", m.prefs.ts.toString())
            settings.updatePrefs { next }
        }
        val local = covers.manifest()
        for ((key, ts) in m.covers) {
            val have = local[key] ?: 0L
            if (kotlin.math.abs(ts) <= kotlin.math.abs(have)) continue
            if (ts < 0) covers.applyRemote(key, null, ts)
            else {
                val bytes = connect.ask(d, "blob", key)?.let { Base64.getDecoder().decode(it) } ?: continue
                covers.applyRemote(key, bytes, ts)
            }
        }
    }
}
