package com.marco.audiopro.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import com.marco.audiopro.ui.viz.Analyzer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class DspTest {
    private val fs = 48000
    private val fmt = AudioFormat(fs, 2, C.ENCODING_PCM_16BIT)

    private fun sine(freq: Double, amp: Double, frames: Int): ByteBuffer {
        val b = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            val v = (sin(2 * PI * freq * i / fs) * amp * 32767).toInt().toShort()
            b.putShort(v); b.putShort(v)
        }
        b.flip()
        return b
    }

    private fun run(p: androidx.media3.common.audio.AudioProcessor, input: ByteBuffer): ByteBuffer {
        val out = ByteBuffer.allocate(input.remaining()).order(ByteOrder.nativeOrder())
        p.queueInput(input)
        out.put(p.output)
        out.flip()
        return out
    }

    private fun rmsDb(b: ByteBuffer, skipFrames: Int): Double {
        val s = b.asShortBuffer()
        var acc = 0.0; var n = 0
        for (i in skipFrames * 2 until s.limit()) { val x = s[i] / 32768.0; acc += x * x; n++ }
        return 20 * log10(sqrt(acc / n))
    }

    @Test fun flatEqIsUnity() {
        val r = EqMath.response(EqPresets.flatBands(), 64)
        r.forEach { assertEquals(0f, it, 1e-4f) }
    }

    @Test fun peakBandHitsGainAtCenter() {
        val band = EqBand(FilterType.PEAK, 1000f, 6f, 1f)
        assertEquals(6.0, EqMath.magnitudeDb(EqMath.coefficients(band, fs), 1000.0, fs.toDouble()), 1e-6)
        assertEquals(0.0, EqMath.magnitudeDb(EqMath.coefficients(band, fs), 30.0, fs.toDouble()), 0.05)
    }

    @Test fun autoPreampCancelsMaxBoost() {
        val cfg = EqConfig(bands = EqPresets.flatBands().mapIndexed { i, b -> if (i == 5) b.copy(gainDb = 6f) else b })
        assertEquals(-6f, cfg.effectivePreampDb(), 0.05f)
    }

    @Test fun bypassIsBitExact() {
        val p = ParametricEqProcessor()
        p.setConfig(EqConfig(enabled = false))
        p.configure(fmt); p.flush()
        val input = sine(997.0, 0.5, 4800)
        val copy = ByteArray(input.remaining()).also { input.duplicate().get(it) }
        val out = run(p, input)
        assertArrayEquals(copy, ByteArray(out.remaining()).also { out.get(it) })
    }

    @Test fun eqBoostsSineByBandGain() {
        val p = ParametricEqProcessor()
        val bands = EqPresets.flatBands().mapIndexed { i, b -> if (i == 5) b.copy(gainDb = 6f) else b }
        p.setConfig(EqConfig(enabled = true, autoPreamp = false, dither = false, bands = bands))
        p.configure(fmt); p.flush()
        val input = sine(1000.0, 0.1, 9600)
        val inDb = rmsDb(input.duplicate().order(ByteOrder.nativeOrder()), 2400)
        val outDb = rmsDb(run(p, input), 2400)
        assertEquals(6.0, outDb - inDb, 0.1)
    }

    @Test fun analyzerReadsFullScaleSineNearZeroDbfs() {
        val tap = AnalyzerTap()
        tap.configure(fmt); tap.flush()
        // 1 s de un seno de 1 kHz a 0 dBFS
        run(tap, sine(1000.0, 1.0, fs))
        val a = Analyzer(tap)
        val ok = tap.read(a.left, a.right, a.n, -AnalyzerTap.SINK_BUFFER_MS)
        assertTrue(ok)
        a.computeSpectrum()
        val bin = (1000.0 * a.n / fs).toInt()
        val peak = (bin - 2..bin + 2).maxOf { a.magDb[it] }
        assertEquals(0f, peak, 1.5f)
        assertEquals(1f, a.correlation(2048), 1e-3f)
    }
}
