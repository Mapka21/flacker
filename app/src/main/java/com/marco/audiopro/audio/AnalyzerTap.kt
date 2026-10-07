package com.marco.audiopro.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.min

/**
 * Último eslabón de la cadena: deja pasar el audio intacto y copia las muestras (post-EQ)
 * a un [SampleRing] que leen los visualizadores.
 *
 * El audio llega aquí ~[SINK_BUFFER_MS] antes de sonar (lo que tarda en vaciarse el buffer
 * del AudioTrack), así que la lectura se hace "en el pasado" para que la imagen vaya en sync.
 */
class AnalyzerTap : BaseAudioProcessor(), SampleSource {
    private val ring = SampleRing()
    private var buf = FloatArray(0)

    @Volatile override var sampleRate = 44100; private set
    @Volatile private var lastWriteNanos = 0L

    private var channels = 2
    private var isFloat = false

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        channels = inputAudioFormat.channelCount
        isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        sampleRate = inputAudioFormat.sampleRate
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val start = inputBuffer.position()
        val bps = if (isFloat) 4 else 2
        val samples = size / bps
        if (buf.size < samples) buf = FloatArray(samples)
        for (i in 0 until samples) {
            val p = start + i * bps
            buf[i] = if (isFloat) inputBuffer.getFloat(p) else inputBuffer.getShort(p) / 32768f
        }
        ring.write(buf, samples / channels, channels)
        lastWriteNanos = System.nanoTime()

        val out = replaceOutputBuffer(size)
        out.put(inputBuffer)
        out.flip()
    }

    override fun read(dstL: FloatArray, dstR: FloatArray, n: Int, extraLatencyMs: Int): Boolean {
        val wp = ring.writePos
        if (wp == 0L) return false
        val sr = sampleRate
        val latency = (SINK_BUFFER_MS + extraLatencyMs).coerceAtLeast(0) * sr / 1000L
        val elapsed = (System.nanoTime() - lastWriteNanos) * sr / 1_000_000_000L
        val end = min(wp, wp - latency + min(elapsed, latency))
        return ring.copyEndingAt(end, dstL, dstR, n)
    }

    override fun onReset() = ring.reset()

    companion object {
        const val SINK_BUFFER_MS = 250
    }
}
