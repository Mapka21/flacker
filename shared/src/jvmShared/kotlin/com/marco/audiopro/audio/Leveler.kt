package com.marco.audiopro.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.tanh

/** Volumen parejo: cada canción se lleva a la misma sonoridad (LUFS) en tiempo real. */
enum class LevelMode(val label: String, val blurb: String, val targetLufs: Double, val maxBoostDb: Double, val ceiling: Double) {
    OFF("Apagado", "Cada canción suena como viene.", 0.0, 0.0, 1.0),
    PAREJO("Parejo", "Todas al mismo nivel, como Spotify (−14 LUFS).", -14.0, 12.0, 0.97),
    FUERTE("Fuerte", "Todas al mismo nivel y FUERTE (−8 LUFS), con limitador para que no truene.", -8.0, 18.0, 0.99),
}

/**
 * Nivelador de sonoridad. Mide como BS.1770 (filtro K aproximado: paso altos a 60 Hz + realce de
 * agudos), promedia ~3 s (rápido al empezar una canción), sube o baja la ganancia despacio para no
 * "bombear" y al final un limitador de picos con saturación suave evita que truene en el modo Fuerte.
 */
class Leveler {
    private var rate = 0
    private var channels = 0
    private var kCoefs: Array<DoubleArray> = emptyArray()
    private var kState: Array<DoubleArray> = emptyArray()
    private var ms = 0.0
    private var fresh = 0.0
    private var gainDb = 0.0
    private var env = 0.0

    /** Ganancia que está aplicando ahora (dB), para mostrarla. */
    @Volatile var currentGainDb = 0.0; private set
    /** Sonoridad medida de lo que suena (LUFS aproximados). */
    @Volatile var measuredLufs = -70.0; private set

    /** Al cambiar de canción o hacer seek: vuelve a medir rápido (la ganancia se conserva, sin saltos). */
    fun reset() { ms = 0.0; fresh = 0.0; env = 0.0 }

    private fun setup(sr: Int, ch: Int) {
        if (sr == rate && ch == channels) return
        rate = sr; channels = ch
        kCoefs = arrayOf(
            EqMath.coefficients(EqBand(FilterType.HIGH_PASS, 60f, 0f, 0.5f), sr),
            EqMath.coefficients(EqBand(FilterType.HIGH_SHELF, 1500f, 4f, 0.71f), sr),
        )
        kState = Array(2) { DoubleArray(ch * 2) }
    }

    fun process(buf: FloatArray, frames: Int, ch: Int, sr: Int, mode: LevelMode) {
        if (mode == LevelMode.OFF || frames == 0) return
        setup(sr, ch)
        // 1) Medición con filtro K: energía por cuadro sumando canales (como BS.1770).
        var sum = 0.0
        var i = 0
        for (f in 0 until frames) {
            for (c in 0 until ch) {
                var x = buf[i++].toDouble()
                for (b in 0 until 2) {
                    val k = kCoefs[b]; val s = kState[b]; val j = c * 2
                    val y = k[0] * x + s[j]
                    s[j] = k[1] * x - k[3] * y + s[j + 1]
                    s[j + 1] = k[2] * x - k[4] * y
                    x = y
                }
                sum += x * x
            }
        }
        val blockMs = sum / frames
        val dt = frames.toDouble() / sr
        fresh += dt
        // Las partes casi en silencio no cuentan (si no, subiría el volumen en los silencios).
        if (10 * log10(blockMs + 1e-12) - 0.691 > -55) {
            val tau = if (fresh < 1.5) 0.4 else 3.0
            ms += (1 - exp(-dt / tau)) * (blockMs - ms)
        }
        val lufs = 10 * log10(ms + 1e-12) - 0.691
        measuredLufs = lufs
        val want = if (ms < 1e-7) gainDb else (mode.targetLufs - lufs).coerceIn(-12.0, mode.maxBoostDb)
        // Baja rápido (que no truene) y sube despacio (que no bombee).
        val tc = if (want < gainDb) 0.6 else if (fresh < 1.5) 0.5 else 2.5
        val newDb = gainDb + (want - gainDb) * (1 - exp(-dt / tc))
        // 2) Ganancia en rampa + limitador de picos + saturación suave.
        val g0 = 10.0.pow(gainDb / 20); val g1 = 10.0.pow(newDb / 20)
        val ceil = mode.ceiling
        val knee = ceil * 0.82
        val att = exp(-1.0 / (0.0015 * sr)); val rel = exp(-1.0 / (0.08 * sr))
        i = 0
        for (f in 0 until frames) {
            val g = g0 + (g1 - g0) * f / frames
            var p = 0.0
            for (c in 0 until ch) p = max(p, abs(buf[i + c] * g))
            env = if (p > env) att * env + (1 - att) * p else rel * env + (1 - rel) * p
            val lim = if (env > ceil) ceil / env else 1.0
            for (c in 0 until ch) {
                var v = buf[i] * g * lim
                val a = abs(v)
                if (a > knee) v = (if (v < 0) -1 else 1) * (knee + (ceil - knee) * tanh((a - knee) / (ceil - knee)))
                buf[i++] = v.toFloat()
            }
        }
        gainDb = newDb
        currentGainDb = newDb
    }
}
