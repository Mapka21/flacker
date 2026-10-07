package com.marco.audiopro.audio

import com.marco.audiopro.data.AppPrefs
import com.marco.audiopro.data.ReplayGainMode
import com.marco.audiopro.data.subsonic.Song
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow

/** Ganancia ReplayGain a aplicar (dB, siempre ≤ 0) y su origen ("pista"/"álbum"), o null si no aplica. */
fun replayGainFor(song: Song?, prefs: AppPrefs): Pair<Float, String>? {
    val rg = song?.replayGain ?: return null
    val (gain, peak, label) = when (prefs.replayGain) {
        ReplayGainMode.OFF -> return null
        ReplayGainMode.TRACK -> Triple(rg.trackGain ?: rg.albumGain, rg.trackPeak, "pista")
        ReplayGainMode.ALBUM -> Triple(rg.albumGain ?: rg.trackGain, rg.albumPeak ?: rg.trackPeak, "álbum")
    }
    gain ?: return null
    var db = gain + prefs.replayGainPreampDb
    // Nunca subir por encima del pico real (evita clipping) y no amplificar.
    if (peak != null && peak > 0f) db = min(db, -20f * log10(peak))
    return min(db, 0f) to label
}

fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)
