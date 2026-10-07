package com.marco.audiopro.audio

import com.marco.audiopro.ui.viz.Analyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class SharedDspTest {
    private val fs = 48000

    private fun sine(freq: Double, amp: Double, frames: Int) = FloatArray(frames * 2) { i ->
        (sin(2 * PI * freq * (i / 2) / fs) * amp).toFloat()
    }

    private fun rmsDb(x: FloatArray, skip: Int): Double {
        var acc = 0.0
        for (i in skip until x.size) acc += x[i] * x[i]
        return 20 * log10(sqrt(acc / (x.size - skip)))
    }

    @Test fun flatEqIsUnity() {
        EqMath.response(EqPresets.flatBands(), 64).forEach { assertEquals(0f, it, 1e-4f) }
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

    @Test fun dspBoostsSineByBandGain() {
        val dsp = EqDsp()
        val bands = EqPresets.flatBands().mapIndexed { i, b -> if (i == 5) b.copy(gainDb = 6f) else b }
        dsp.setConfig(EqConfig(enabled = true, autoPreamp = false, bands = bands))
        dsp.reset(fs, 2)
        val x = sine(1000.0, 0.1, 9600)
        val inDb = rmsDb(x, 4800)
        dsp.process(x, 9600)
        assertEquals(6.0, rmsDb(x, 4800) - inDb, 0.05)
    }

    @Test fun analyzerReadsFullScaleSineNearZeroDbfs() {
        val ring = SampleRing()
        ring.write(sine(1000.0, 1.0, fs), fs, 2)
        val source = object : SampleSource {
            override val sampleRate = fs
            override fun read(dstL: FloatArray, dstR: FloatArray, n: Int, extraLatencyMs: Int) =
                ring.copyEndingAt(ring.writePos, dstL, dstR, n)
        }
        val a = Analyzer(source)
        a.capture(0, playing = true)
        assertTrue(a.hasData)
        a.computeSpectrum()
        val bin = (1000.0 * a.n / fs).toInt()
        assertEquals(0f, (bin - 2..bin + 2).maxOf { a.magDb[it] }, 1.5f)
        assertEquals(1f, a.correlation(2048), 1e-3f)
    }

    @Test fun replayGainNeverAmplifiesOrClips() {
        val song = com.marco.audiopro.data.subsonic.Song(
            "x", replayGain = com.marco.audiopro.data.subsonic.ReplayGain(trackGain = 4f, albumGain = -7f, trackPeak = 0.9f, albumPeak = 0.95f),
        )
        val prefs = com.marco.audiopro.data.AppPrefs(replayGain = com.marco.audiopro.data.ReplayGainMode.TRACK)
        assertEquals(0f, replayGainFor(song, prefs)!!.first, 1e-6f)
        val album = prefs.copy(replayGain = com.marco.audiopro.data.ReplayGainMode.ALBUM)
        assertEquals(-7f, replayGainFor(song, album)!!.first, 1e-6f)
    }

    @Test fun parsesLrc() {
        val l = com.marco.audiopro.data.info.LyricsRepository.parseLrc(
            listOf("[ar:Alguien]", "[00:01.50]Primera", "[00:03.2]Segunda", "[01:02.345][01:10.00]Coro", "[00:00.00]").joinToString("\n"),
        )
        assertEquals(listOf(0L, 1500L, 3200L, 62345L, 70000L), l.map { it.start })
        assertEquals("Coro", l[3].value)
        assertEquals("Primera", l[1].value)
    }
}
