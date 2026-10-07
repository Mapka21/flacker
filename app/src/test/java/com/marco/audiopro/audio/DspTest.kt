package com.marco.audiopro.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/** Pruebas del adaptador de Media3 (el núcleo DSP se prueba en :shared). */
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

    @Test fun bypassIsBitExact() {
        val dsp = EqDsp().apply { setConfig(EqConfig(enabled = false)) }
        val p = ParametricEqProcessor(dsp)
        p.configure(fmt); p.flush()
        val input = sine(997.0, 0.5, 4800)
        val copy = ByteArray(input.remaining()).also { input.duplicate().get(it) }
        val out = run(p, input)
        assertArrayEquals(copy, ByteArray(out.remaining()).also { out.get(it) })
    }

    @Test fun eqBoostsSineByBandGain() {
        val bands = EqPresets.flatBands().mapIndexed { i, b -> if (i == 5) b.copy(gainDb = 6f) else b }
        val dsp = EqDsp().apply { setConfig(EqConfig(enabled = true, autoPreamp = false, dither = false, bands = bands)) }
        val p = ParametricEqProcessor(dsp)
        p.configure(fmt); p.flush()
        val input = sine(1000.0, 0.1, 9600)
        val inDb = rmsDb(input.duplicate().order(ByteOrder.nativeOrder()), 2400)
        val outDb = rmsDb(run(p, input), 2400)
        assertEquals(6.0, outDb - inDb, 0.1)
    }
}
