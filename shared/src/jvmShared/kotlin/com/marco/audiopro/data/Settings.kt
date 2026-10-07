package com.marco.audiopro.data

import com.marco.audiopro.ui.theme.AccentTheme
import com.marco.audiopro.ui.viz.VisualPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ServerAccount(val url: String, val user: String, val salt: String, val token: String)

enum class StreamQuality(val label: String, val format: String?, val maxBitRate: Int) {
    ORIGINAL("Original (lossless)", "raw", 0),
    OPUS_256("Opus 256 kbps", "opus", 256),
    OPUS_160("Opus 160 kbps", "opus", 160),
    OPUS_96("Opus 96 kbps", "opus", 96),
    MP3_320("MP3 320 kbps", "mp3", 320),
}

enum class ReplayGainMode(val label: String) { OFF("Apagado"), TRACK("Por pista"), ALBUM("Por álbum") }

data class AppPrefs(
    val wifiQuality: StreamQuality = StreamQuality.ORIGINAL,
    val mobileQuality: StreamQuality = StreamQuality.OPUS_160,
    val replayGain: ReplayGainMode = ReplayGainMode.ALBUM,
    val replayGainPreampDb: Float = 0f,
    val visualOffsetMs: Int = 40,
    val lastFmKey: String = "",
    val anthropicKey: String = "",
    val accent: AccentTheme = AccentTheme.AMBER,
    val downloadQuality: StreamQuality = StreamQuality.ORIGINAL,
    val downloadWifiOnly: Boolean = true,
    /** Modo visual: el fondo reacciona a la música con el preset elegido. */
    val visualMode: Boolean = true,
    val visualPreset: VisualPreset = VisualPreset.AURORA,
)

/** Almacenamiento clave-valor de cada plataforma (SharedPreferences en Android, Preferences en PC). */
interface KeyValueStore {
    fun getString(key: String): String?
    fun getFloat(key: String, def: Float): Float
    fun getInt(key: String, def: Int): Int
    fun getBoolean(key: String, def: Boolean): Boolean
    /** Aplica varios cambios juntos; `null` borra la clave. */
    fun edit(values: Map<String, Any?>)
}

/** Todo lo persistente que no es el EQ. */
class Settings(private val sp: KeyValueStore) {

    private val _account = MutableStateFlow(readAccount())
    val account: StateFlow<ServerAccount?> = _account.asStateFlow()

    private val _prefs = MutableStateFlow(readPrefs())
    val prefs: StateFlow<AppPrefs> = _prefs.asStateFlow()

    private fun readAccount(): ServerAccount? {
        val url = sp.getString("url") ?: return null
        return ServerAccount(
            url = url,
            user = sp.getString("user").orEmpty(),
            salt = sp.getString("salt").orEmpty(),
            token = sp.getString("token").orEmpty(),
        )
    }

    fun saveAccount(a: ServerAccount?) {
        sp.edit(mapOf("url" to a?.url, "user" to a?.user, "salt" to a?.salt, "token" to a?.token))
        _account.value = a
    }

    private fun readPrefs() = AppPrefs(
        wifiQuality = enumOr(sp.getString("q_wifi"), StreamQuality.ORIGINAL),
        mobileQuality = enumOr(sp.getString("q_mobile"), StreamQuality.OPUS_160),
        replayGain = enumOr(sp.getString("rg"), ReplayGainMode.ALBUM),
        replayGainPreampDb = sp.getFloat("rg_pre", 0f),
        visualOffsetMs = sp.getInt("vis_off", 40),
        lastFmKey = sp.getString("lastfm").orEmpty(),
        anthropicKey = sp.getString("anthropic").orEmpty(),
        accent = enumOr(sp.getString("accent"), AccentTheme.AMBER),
        downloadQuality = enumOr(sp.getString("q_download"), StreamQuality.ORIGINAL),
        downloadWifiOnly = sp.getBoolean("dl_wifi", true),
        visualMode = sp.getBoolean("vis_mode", true),
        visualPreset = enumOr(sp.getString("vis_preset"), VisualPreset.AURORA),
    )

    fun updatePrefs(f: (AppPrefs) -> AppPrefs) {
        val p = f(_prefs.value)
        sp.edit(
            mapOf(
                "q_wifi" to p.wifiQuality.name,
                "q_mobile" to p.mobileQuality.name,
                "rg" to p.replayGain.name,
                "rg_pre" to p.replayGainPreampDb,
                "vis_off" to p.visualOffsetMs,
                "lastfm" to p.lastFmKey.trim(),
                "anthropic" to p.anthropicKey.trim(),
                "accent" to p.accent.name,
                "q_download" to p.downloadQuality.name,
                "dl_wifi" to p.downloadWifiOnly,
                "vis_mode" to p.visualMode,
                "vis_preset" to p.visualPreset.name,
            ),
        )
        _prefs.value = p
    }

    fun getString(key: String): String? = sp.getString(key)
    fun putString(key: String, value: String) = sp.edit(mapOf(key to value))

    private inline fun <reified E : Enum<E>> enumOr(name: String?, def: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: def
}
