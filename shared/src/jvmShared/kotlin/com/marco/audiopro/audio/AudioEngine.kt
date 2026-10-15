package com.marco.audiopro.audio

import com.marco.audiopro.data.Settings
import com.marco.audiopro.data.subsonic.SubsonicJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString

/** Lo que la UI necesita saber de la ruta de señal en este momento. */
data class SignalInfo(
    val replayGainDb: Float? = null,
    val replayGainSource: String? = null,
    val transport: String = "",
    /** Descripción de la salida de audio (cambia según plataforma/dispositivo). */
    val output: String = "",
)

/**
 * Dueño de la cadena DSP. El EQ vive aquí (no en el reproductor) para que la UI lo cambie en
 * caliente; el `tap` lo da cada plataforma (sabe cuándo suena realmente cada muestra).
 */
class AudioEngine(private val settings: Settings, val tap: SampleSource) {
    val eq = EqDsp()

    private val _config = MutableStateFlow(load())
    val config: StateFlow<EqConfig> = _config.asStateFlow()

    private val _userPresets = MutableStateFlow(loadPresets())
    val userPresets: StateFlow<List<EqPreset>> = _userPresets.asStateFlow()

    val signal = MutableStateFlow(SignalInfo())

    init { eq.setConfig(_config.value) }

    fun update(f: (EqConfig) -> EqConfig) {
        val c = f(_config.value)
        _config.value = c
        eq.setConfig(c)
        settings.putString("eq", SubsonicJson.encodeToString(c))
    }

    fun updateBand(index: Int, f: (EqBand) -> EqBand) = update { c ->
        c.copy(bands = c.bands.mapIndexed { i, b -> if (i == index) f(b) else b }, presetName = "Personalizado", autoGenre = false)
    }

    fun applyPreset(p: EqPreset) = update { it.copy(bands = p.bands, presetName = p.name, enabled = true, autoGenre = false) }

    /** Volumen parejo: Apagado, Parejo o Fuerte. */
    fun setLeveler(mode: LevelMode) = update { it.copy(leveler = mode) }

    /** "Auto por género": encendido, cada canción pone el preset de su género. */
    fun setAutoGenre(on: Boolean, currentGenre: String?) {
        if (!on) { update { it.copy(autoGenre = false) }; return }
        val p = EqPresets.forGenre(currentGenre)
        update { it.copy(autoGenre = true, enabled = true, bands = p.bands, presetName = p.name) }
    }

    /** Llamar cuando cambia la canción (lo hace la raíz de la app). */
    fun onSongGenre(genre: String?) {
        if (!_config.value.autoGenre) return
        val p = EqPresets.forGenre(genre)
        if (p.name != _config.value.presetName) update { it.copy(bands = p.bands, presetName = p.name) }
    }

    fun savePreset(name: String) {
        val list = _userPresets.value.filter { it.name != name } + EqPreset(name, _config.value.bands)
        _userPresets.value = list
        settings.putString("eq_presets", SubsonicJson.encodeToString(list))
        update { it.copy(presetName = name) }
    }

    fun deletePreset(name: String) {
        val list = _userPresets.value.filter { it.name != name }
        _userPresets.value = list
        settings.putString("eq_presets", SubsonicJson.encodeToString(list))
    }

    private fun load(): EqConfig =
        settings.getString("eq")?.let { runCatching { SubsonicJson.decodeFromString<EqConfig>(it) }.getOrNull() } ?: EqConfig()

    private fun loadPresets(): List<EqPreset> =
        settings.getString("eq_presets")?.let { runCatching { SubsonicJson.decodeFromString<List<EqPreset>>(it) }.getOrNull() } ?: emptyList()
}
