package com.marco.audiopro.ui.viz

import com.marco.audiopro.audio.SampleSource
import com.marco.audiopro.audio.Fft
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/** Lado UI del análisis: lee el tap, hace FFT y métricas. Sin asignaciones por frame. */
class Analyzer(private val tap: SampleSource, val n: Int = 4096) {
    private val fft = Fft(n)
    val left = FloatArray(n)
    val right = FloatArray(n)
    private val re = FloatArray(n)
    private val im = FloatArray(n)
    private val window = FloatArray(n) { (0.5 - 0.5 * cos(2 * PI * it / (n - 1))).toFloat() }
    /** Magnitud en dBFS por bin (n/2). */
    val magDb = FloatArray(n / 2)
    var sampleRate = 44100; private set
    var hasData = false; private set

    fun capture(offsetMs: Int, playing: Boolean) {
        hasData = playing && tap.read(left, right, n, offsetMs)
        if (!hasData) { left.fill(0f); right.fill(0f) }
        sampleRate = tap.sampleRate
    }

    fun computeSpectrum() {
        for (i in 0 until n) {
            re[i] = (left[i] + right[i]) * 0.5f * window[i]
            im[i] = 0f
        }
        fft.transform(re, im)
        // Normaliza: seno a 0 dBFS → 0 dB (ganancia coherente de Hann = 0.5).
        val norm = 4f / n
        for (k in 0 until n / 2) {
            val m = sqrt(re[k] * re[k] + im[k] * im[k]) * norm
            magDb[k] = 20f * log10(m + 1e-9f)
        }
    }

    /** Agrupa los bins en `out.size` bandas logarítmicas entre fMin y fMax (máximo por banda). */
    fun logBands(out: FloatArray, fMin: Float = 25f, fMax: Float = 20000f) {
        val bins = n / 2
        val hzPerBin = sampleRate.toFloat() / n
        val top = minOf(fMax, sampleRate / 2f)
        val ratio = top / fMin
        for (b in out.indices) {
            val f0 = fMin * ratio.pow(b.toFloat() / out.size)
            val f1 = fMin * ratio.pow((b + 1f) / out.size)
            val k0 = (f0 / hzPerBin).toInt().coerceIn(0, bins - 1)
            val k1 = (f1 / hzPerBin).toInt().coerceIn(k0, bins - 1)
            var m = -160f
            if (k1 == k0) {
                // Banda más angosta que un bin: interpola entre vecinos.
                val pos = (sqrt(f0 * f1) / hzPerBin).coerceIn(0f, bins - 1.001f)
                val i = pos.toInt(); val t = pos - i
                m = magDb[i] * (1 - t) + magDb[minOf(i + 1, bins - 1)] * t
            } else {
                for (k in k0..k1) if (magDb[k] > m) m = magDb[k]
            }
            out[b] = m
        }
    }

    fun rmsDb(ch: FloatArray, frames: Int): Float {
        var s = 0.0
        for (i in n - frames until n) s += ch[i] * ch[i]
        return (10 * log10(s / frames + 1e-12)).toFloat()
    }

    fun peak(ch: FloatArray, frames: Int): Float {
        var p = 0f
        for (i in n - frames until n) { val a = kotlin.math.abs(ch[i]); if (a > p) p = a }
        return p
    }

    /** Correlación de fase L/R en [-1, 1]. */
    fun correlation(frames: Int): Float {
        var lr = 0.0; var ll = 0.0; var rr = 0.0
        for (i in n - frames until n) {
            lr += left[i] * right[i]; ll += left[i] * left[i]; rr += right[i] * right[i]
        }
        val d = sqrt(ll * rr)
        return if (d < 1e-9) 0f else (lr / d).toFloat()
    }

    fun framesFor(ms: Int): Int = max(64, minOf(n, sampleRate * ms / 1000))
}
