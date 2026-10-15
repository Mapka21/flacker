package com.marco.audiopro.audio

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** Formato PCM que llega del decodificador (para la "ruta de señal"). */
data class PcmFormat(val sampleRate: Int, val channels: Int, val encoding: String)

/**
 * Núcleo del EQ paramétrico: N biquads en double (transposed direct form II) sobre muestras
 * float intercaladas. Lo usan el AudioProcessor de Media3 (Android) y el motor de la PC.
 */
class EqDsp {
    @Volatile private var pending: EqConfig = EqConfig()
    private var applied: EqConfig? = null
    private var appliedRate = 0

    private var sampleRate = 44100
    private var channels = 2
    private var coefs: Array<DoubleArray> = emptyArray()
    // estado[banda][canal*2 + {0,1}]
    private var state: Array<DoubleArray> = emptyArray()
    private var gain = 1.0
    private var active = false
    private var eqOn = false
    private var level = LevelMode.OFF
    private var ditherOn = true
    val leveler = Leveler()

    /** Contador de muestras recortadas (LED de CLIP). */
    @Volatile var clipCount = 0L; private set
    /** Pico de salida del último bloque, lineal 0..1+. */
    @Volatile var outputPeak = 0f; private set
    @Volatile var inputFormat: PcmFormat? = null

    fun setConfig(c: EqConfig) { pending = c }

    /** Llamar al cambiar de formato o al hacer seek (limpia el estado de los filtros). */
    fun reset(sampleRate: Int, channels: Int) {
        this.sampleRate = sampleRate
        this.channels = channels
        applied = null
        state = emptyArray()
        leveler.reset()
    }

    private fun ensureFilters() {
        val cfg = pending
        if (cfg === applied && appliedRate == sampleRate) return
        val bands = cfg.bands.filter { it.enabled && !(it.type.hasGain && abs(it.gainDb) < 0.01f) }
        val newCoefs = bands.map { EqMath.coefficients(it, sampleRate) }.toTypedArray()
        if (state.size != newCoefs.size || state.firstOrNull()?.size != channels * 2) {
            state = Array(newCoefs.size) { DoubleArray(channels * 2) }
        }
        coefs = newCoefs
        eqOn = cfg.enabled
        level = cfg.leveler
        gain = if (eqOn) 10.0.pow(cfg.effectivePreampDb() / 20.0) else 1.0
        active = eqOn || level != LevelMode.OFF
        ditherOn = cfg.dither
        applied = cfg
        appliedRate = sampleRate
    }

    /** ¿Hay que procesar? Si no, el llamador puede copiar el audio tal cual (bit-exacto). */
    val isActive: Boolean get() { ensureFilters(); return active }
    val dither: Boolean get() { ensureFilters(); return ditherOn }

    /** Procesa en sitio `frames` frames intercalados. Recorta a [-1, 1] y cuenta los recortes. */
    fun process(buf: FloatArray, frames: Int) {
        ensureFilters()
        if (!active) { outputPeak = 0f; return }
        val ch = channels
        if (eqOn) {
            val nb = coefs.size
            var i = 0
            for (f in 0 until frames) {
                for (c in 0 until ch) {
                    var x = buf[i] * gain
                    for (b in 0 until nb) {
                        val k = coefs[b]
                        val s = state[b]
                        val j = c * 2
                        val y = k[0] * x + s[j]
                        s[j] = k[1] * x - k[3] * y + s[j + 1]
                        s[j + 1] = k[2] * x - k[4] * y
                        x = y
                    }
                    buf[i] = x.toFloat()
                    i++
                }
            }
        }
        // Volumen parejo después del EQ (así también nivela lo que el EQ subió).
        leveler.process(buf, frames, ch, sampleRate, level)
        var peak = 0f
        var clips = 0
        for (i in 0 until frames * ch) {
            val ax = abs(buf[i])
            if (ax > peak) peak = ax
            if (ax > 1f) { clips++; buf[i] = if (buf[i] > 0) 1f else -1f }
        }
        outputPeak = peak
        if (clips > 0) clipCount += clips
    }
}

/** Ruido TPDF de ±1 LSB para re-cuantizar sin distorsión de truncado. */
class Dither {
    private var rng = 0x2545F491L
    private fun next(): Double {
        var x = rng
        x = x xor (x shl 13); x = x xor (x ushr 7); x = x xor (x shl 17)
        rng = x
        return (x ushr 11).toDouble() / (1L shl 53).toDouble()
    }

    fun tpdf(): Double = next() - next()

    /** float [-1,1] → entero de `bits` bits con dither opcional. */
    fun quantize(x: Float, bits: Int, on: Boolean): Int {
        val max = (1 shl (bits - 1)) - 1
        val v = x * max + if (on) tpdf() else 0.0
        return v.roundToInt().coerceIn(-max - 1, max)
    }
}
