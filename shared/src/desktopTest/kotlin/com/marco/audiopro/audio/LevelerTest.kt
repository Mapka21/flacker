package com.marco.audiopro.audio

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class LevelerTest {
    private val sr = 48000

    /** Pasa `seconds` de un seno de amplitud `amp` por el nivelador; devuelve (RMS dBFS, pico) del último segundo. */
    private fun run(lev: Leveler, amp: Double, mode: LevelMode, seconds: Int = 8): Pair<Double, Double> {
        val block = 1024
        val buf = FloatArray(block * 2)
        var t = 0L
        var sum = 0.0; var n = 0; var peak = 0.0
        val total = sr * seconds
        while (t < total) {
            for (f in 0 until block) {
                val v = (amp * sin(2 * PI * 1000.0 * (t + f) / sr)).toFloat()
                buf[f * 2] = v; buf[f * 2 + 1] = v
            }
            lev.process(buf, block, 2, sr, mode)
            if (t > total - sr) for (x in buf) { sum += x.toDouble() * x; n++; peak = maxOf(peak, abs(x.toDouble())) }
            t += block
        }
        return 20 * log10(sqrt(sum / n)) to peak
    }

    @Test fun quietAndLoudEndUpEven() {
        val lev = Leveler()
        val (quiet, _) = run(lev, 0.1, LevelMode.PAREJO)    // master bajito (~ -20 LUFS)
        lev.reset()
        val (loud, pk) = run(lev, 0.7, LevelMode.PAREJO)    // master fuerte (~ -3 dBFS)
        assertTrue("parejo: $quiet vs $loud", abs(quiet - loud) < 2.0)
        assertTrue("pico $pk", pk <= 1.0)
    }

    @Test fun strongIsLouderAndNeverClips() {
        val (parejo, _) = run(Leveler(), 0.05, LevelMode.PAREJO)
        val (fuerte, pk) = run(Leveler(), 0.05, LevelMode.FUERTE)
        assertTrue("fuerte $fuerte > parejo $parejo", fuerte > parejo + 3)
        assertTrue("pico $pk", pk <= 1.0)
    }
}
