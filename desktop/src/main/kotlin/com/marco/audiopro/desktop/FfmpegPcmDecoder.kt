package com.marco.audiopro.desktop

import com.marco.audiopro.cast.CastWav
import com.marco.audiopro.cast.PcmDecoder
import com.marco.audiopro.cast.PcmStream
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.nio.ShortBuffer

/** Para transmitir: ffmpeg decodifica cualquier formato a PCM estéreo de 16 bits (y baja las hi-res a 44.1/48 kHz). */
class FfmpegPcmDecoder : PcmDecoder {
    override fun open(url: String, maxRate: Int): PcmStream? = runCatching {
        // Primero se ve a cuánto viene, para pedirle a ffmpeg que convierta a la frecuencia de salida.
        val rate = FFmpegFrameGrabber(url).run { try { start(); sampleRate } finally { runCatching { stop(); release() } } }
        val g = FFmpegFrameGrabber(url).apply {
            sampleFormat = avutil.AV_SAMPLE_FMT_S16
            audioChannels = 2
            sampleRate = CastWav.outRate(rate).coerceAtMost(maxRate)
            setOption("reconnect", "1")
            start()
        }
        Stream(g)
    }.getOrNull()

    private class Stream(private val g: FFmpegFrameGrabber) : PcmStream {
        override val sampleRate = g.sampleRate
        override val channels = 2
        private var pending: ShortBuffer? = null

        override fun read(dst: ShortArray): Int {
            var b = pending
            while (b == null || b.remaining() < 2) {
                val f = g.grabSamples() ?: return -1
                b = f.samples?.firstOrNull() as? ShortBuffer
            }
            // Siempre frames completos (pares de muestras).
            val n = minOf(dst.size, b.remaining()) and 1.inv()
            b.get(dst, 0, n)
            pending = b
            return n
        }

        override fun seek(ms: Long) {
            pending = null
            runCatching { g.timestamp = ms * 1000 }
        }

        override fun close() { runCatching { g.stop(); g.release() } }
    }
}
