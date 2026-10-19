package com.marco.audiopro.local

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.marco.audiopro.cast.PcmDecoder
import com.marco.audiopro.cast.PcmStream
import java.nio.ByteOrder

/**
 * Para transmitir en el celular: los decodificadores del propio Android (MediaCodec) pasan la canción
 * a PCM de 16 bits; el puente la manda a la tele como WAV. La frecuencia la ajusta [com.marco.audiopro.cast.CastWav].
 */
class MediaCodecPcmDecoder : PcmDecoder {
    override fun open(url: String, maxRate: Int): PcmStream? {
        val ex = MediaExtractor()
        return runCatching {
            ex.setDataSource(url)
            val track = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0)
            codec.start()
            Stream(ex, codec, fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE), fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
        }.getOrElse { ex.release(); null }
    }

    private class Stream(
        private val ex: MediaExtractor,
        private val codec: MediaCodec,
        override val sampleRate: Int,
        override val channels: Int,
    ) : PcmStream {
        private val info = MediaCodec.BufferInfo()
        private var left = ShortArray(0)
        private var leftPos = 0
        private var leftLen = 0
        private var inputDone = false
        private var outputDone = false

        override fun read(dst: ShortArray): Int {
            while (leftPos >= leftLen) {
                if (outputDone) return -1
                pump()
            }
            // Siempre frames completos.
            val n = (minOf(dst.size, leftLen - leftPos) / channels) * channels
            System.arraycopy(left, leftPos, dst, 0, n)
            leftPos += n
            return n
        }

        private fun pump() {
            if (!inputDone) {
                val i = codec.dequeueInputBuffer(10_000)
                if (i >= 0) {
                    val buf = codec.getInputBuffer(i)!!
                    val size = ex.readSampleData(buf, 0)
                    if (size < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                    else { codec.queueInputBuffer(i, 0, size, ex.sampleTime, 0); ex.advance() }
                }
            }
            val o = codec.dequeueOutputBuffer(info, 10_000)
            if (o >= 0) {
                val out = codec.getOutputBuffer(o)!!
                out.position(info.offset); out.limit(info.offset + info.size)
                val shorts = out.order(ByteOrder.nativeOrder()).asShortBuffer()
                val n = shorts.remaining()
                if (left.size < n) left = ShortArray(n)
                shorts.get(left, 0, n)
                leftPos = 0; leftLen = n
                codec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }

        override fun seek(ms: Long) {
            ex.seekTo(ms * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            codec.flush()
            leftPos = 0; leftLen = 0; inputDone = false; outputDone = false
        }

        override fun close() {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { ex.release() }
        }
    }
}
