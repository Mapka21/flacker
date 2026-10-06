package com.marco.audiopro.data

import android.content.Context
import android.content.SharedPreferences
import com.marco.audiopro.ui.theme.AccentTheme
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
)

/** Todo lo persistente que no es el EQ. Una app personal: SharedPreferences basta. */
class Settings(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("marco", Context.MODE_PRIVATE)

    private val _account = MutableStateFlow(readAccount())
    val account: StateFlow<ServerAccount?> = _account.asStateFlow()

    private val _prefs = MutableStateFlow(readPrefs())
    val prefs: StateFlow<AppPrefs> = _prefs.asStateFlow()

    private fun readAccount(): ServerAccount? {
        val url = sp.getString("url", null) ?: return null
        return ServerAccount(
            url = url,
            user = sp.getString("user", "")!!,
            salt = sp.getString("salt", "")!!,
            token = sp.getString("token", "")!!,
        )
    }

    fun saveAccount(a: ServerAccount?) {
        sp.edit().apply {
            if (a == null) {
                remove("url"); remove("user"); remove("salt"); remove("token")
            } else {
                putString("url", a.url); putString("user", a.user)
                putString("salt", a.salt); putString("token", a.token)
            }
        }.apply()
        _account.value = a
    }

    private fun readPrefs() = AppPrefs(
        wifiQuality = enumOr(sp.getString("q_wifi", null), StreamQuality.ORIGINAL),
        mobileQuality = enumOr(sp.getString("q_mobile", null), StreamQuality.OPUS_160),
        replayGain = enumOr(sp.getString("rg", null), ReplayGainMode.ALBUM),
        replayGainPreampDb = sp.getFloat("rg_pre", 0f),
        visualOffsetMs = sp.getInt("vis_off", 40),
        lastFmKey = sp.getString("lastfm", "")!!,
        anthropicKey = sp.getString("anthropic", "")!!,
        accent = enumOr(sp.getString("accent", null), AccentTheme.AMBER),
        downloadQuality = enumOr(sp.getString("q_download", null), StreamQuality.ORIGINAL),
        downloadWifiOnly = sp.getBoolean("dl_wifi", true),
    )

    fun updatePrefs(f: (AppPrefs) -> AppPrefs) {
        val p = f(_prefs.value)
        sp.edit()
            .putString("q_wifi", p.wifiQuality.name)
            .putString("q_mobile", p.mobileQuality.name)
            .putString("rg", p.replayGain.name)
            .putFloat("rg_pre", p.replayGainPreampDb)
            .putInt("vis_off", p.visualOffsetMs)
            .putString("lastfm", p.lastFmKey.trim())
            .putString("anthropic", p.anthropicKey.trim())
            .putString("accent", p.accent.name)
            .putString("q_download", p.downloadQuality.name)
            .putBoolean("dl_wifi", p.downloadWifiOnly)
            .apply()
        _prefs.value = p
    }

    fun getString(key: String): String? = sp.getString(key, null)
    fun putString(key: String, value: String) = sp.edit().putString(key, value).apply()

    private inline fun <reified E : Enum<E>> enumOr(name: String?, def: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: def
}
