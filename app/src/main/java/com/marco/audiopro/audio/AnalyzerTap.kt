package com.marco.audiopro.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.min

/**
 * Último eslabón de la cadena: deja pasar el audio intacto y copia las muestras (post-EQ)
 * a un buffer circular estéreo que leen los visualizadores.
 *
 * El audio llega aquí ~[SINK_BUFFER_MS] antes de sonar (lo que tarda en vaciarse el buffer
 * del AudioTrack), así que la lectura se hace "en el pasado" para que la imagen vaya en sync.
 */
class AnalyzerTap : BaseAudioProcessor() {

    private val l = FloatArray(CAPACITY)
    private val r = FloatArray(CAPACITY)

    @Volatile var writePos = 0L; private set
    @Volatile var sampleRate = 44100; private set
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
        val bytesPerSample = if (isFloat) 4 else 2
        val frames = size / (channels * bytesPerSample)
        var w = writePos
        var p = start
        for (f in 0 until frames) {
            val left: Float
            val right: Float
            if (isFloat) {
                left = inputBuffer.getFloat(p)
                right = if (channels > 1) inputBuffer.getFloat(p + 4) else left
            } else {
                left = inputBuffer.getShort(p) / 32768f
                right = if (channels > 1) inputBuffer.getShort(p + 2) / 32768f else left
            }
            val idx = (w and MASK).toInt()
            l[idx] = left
            r[idx] = right
            w++
            p += channels * bytesPerSample
        }
        writePos = w
        lastWriteNanos = System.nanoTime()

        val out = replaceOutputBuffer(size)
        out.put(inputBuffer)
        out.flip()
    }

    /**
     * Copia los `n` frames que deberían estar sonando ahora mismo.
     * @return false si todavía no hay audio.
     */
    fun read(dstL: FloatArray, dstR: FloatArray, n: Int, extraLatencyMs: Int): Boolean {
        val wp = writePos
        if (wp == 0L) return false
        val sr = sampleRate
        val latency = (SINK_BUFFER_MS + extraLatencyMs).coerceAtLeast(0) * sr / 1000L
        val elapsed = (System.nanoTime() - lastWriteNanos) * sr / 1_000_000_000L
        val end = min(wp, wp - latency + min(elapsed, latency))
        val begin = end - n
        if (begin < wp - CAPACITY) return false
        for (i in 0 until n) {
            val idx = ((begin + i) and MASK).toInt()
            dstL[i] = l[idx]
            dstR[i] = r[idx]
        }
        return true
    }

    override fun onReset() {
        writePos = 0L
        l.fill(0f); r.fill(0f)
    }

    companion object {
        const val SINK_BUFFER_MS = 250
        private const val CAPACITY = 1 shl 17
        private const val MASK = (CAPACITY - 1).toLong()
    }
}
