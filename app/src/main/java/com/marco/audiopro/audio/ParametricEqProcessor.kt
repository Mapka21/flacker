package com.marco.audiopro.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.pow

/**
 * EQ paramétrico de N bandas (biquads en double, transposed direct form II) dentro de la
 * cadena de audio de ExoPlayer. Con el EQ apagado copia las muestras sin tocarlas.
 */
class ParametricEqProcessor : BaseAudioProcessor() {

    @Volatile private var pending: EqConfig = EqConfig()
    private var applied: EqConfig? = null
    private var appliedRate = 0

    private var sampleRate = 44100
    private var channels = 2
    private var encoding = C.ENCODING_PCM_16BIT

    private var coefs: Array<DoubleArray> = emptyArray()
    // estado[banda][canal*2 + {0,1}]
    private var state: Array<DoubleArray> = emptyArray()
    private var gain = 1.0
    private var active = false
    private var dither = true
    private var rng = 0x2545F491L

    /** Contador de muestras recortadas (para el LED de CLIP de la UI). */
    @Volatile var clipCount = 0L; private set
    /** Pico de salida del último bloque, lineal 0..1+. */
    @Volatile var outputPeak = 0f; private set
    /** Formato PCM que llega del decodificador (para la "ruta de señal"). */
    @Volatile var currentFormat: AudioFormat? = null; private set

    fun setConfig(c: EqConfig) { pending = c }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        currentFormat = inputAudioFormat
        return inputAudioFormat
    }

    override fun onFlush() {
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        applied = null
        state = emptyArray()
    }

    private fun ensureFilters() {
        val cfg = pending
        if (cfg === applied && appliedRate == sampleRate) return
        val bands = cfg.bands.filter { it.enabled && !(it.type.hasGain && abs(it.gainDb) < 0.01f) }
        val newCoefs = bands.map { EqMath.coefficients(it, sampleRate) }.toTypedArray()
        if (state.size != newCoefs.size) state = Array(newCoefs.size) { DoubleArray(channels * 2) }
        coefs = newCoefs
        gain = 10.0.pow(cfg.effectivePreampDb() / 20.0)
        active = cfg.enabled
        dither = cfg.dither
        applied = cfg
        appliedRate = sampleRate
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        ensureFilters()
        val out = replaceOutputBuffer(size)
        if (!active) {
            out.put(inputBuffer)
            out.flip()
            outputPeak = 0f
            return
        }
        val nb = coefs.size
        var peak = 0f
        var clips = 0
        val isFloat = encoding == C.ENCODING_PCM_FLOAT
        val frames = size / (channels * if (isFloat) 4 else 2)
        for (f in 0 until frames) {
            for (ch in 0 until channels) {
                var x = if (isFloat) inputBuffer.getFloat().toDouble() else inputBuffer.getShort() / 32768.0
                x *= gain
                for (b in 0 until nb) {
                    val c = coefs[b]
                    val s = state[b]
                    val i = ch * 2
                    val y = c[0] * x + s[i]
                    s[i] = c[1] * x - c[3] * y + s[i + 1]
                    s[i + 1] = c[2] * x - c[4] * y
                    x = y
                }
                val ax = abs(x).toFloat()
                if (ax > peak) peak = ax
                if (ax > 1f) { clips++; x = if (x > 0) 1.0 else -1.0 }
                if (isFloat) {
                    out.putFloat(x.toFloat())
                } else {
                    // TPDF dither de ±1 LSB al recuantizar a 16 bits.
                    val d = if (dither) (nextRand() - nextRand()) else 0.0
                    val v = Math.round(x * 32767.0 + d).coerceIn(-32768, 32767)
                    out.putShort(v.toShort())
                }
            }
        }
        // Bytes sobrantes (no debería haber) se descartan.
        inputBuffer.position(inputBuffer.limit())
        out.flip()
        outputPeak = peak
        if (clips > 0) clipCount += clips
    }

    private fun nextRand(): Double {
        var x = rng
        x = x xor (x shl 13); x = x xor (x ushr 7); x = x xor (x shl 17)
        rng = x
        return (x ushr 11).toDouble() / (1L shl 53).toDouble()
    }

    override fun onReset() {
        applied = null
        state = emptyArray()
        currentFormat = null
    }
}
