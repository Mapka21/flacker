package com.marco.audiopro.cast

import java.io.Closeable
import java.io.OutputStream

/**
 * Decodificador de la plataforma (ffmpeg en la PC, MediaCodec en Android) para cuando la tele no
 * acepta el archivo original: lo convierte a WAV aquí mismo, sin depender del servidor (y funciona
 * igual sin servidor, con tu música local).
 */
interface PcmDecoder {
    /** Abre [url]; [maxRate] es una pista: si puede, que entregue a esa frecuencia o menos. null = no se pudo. */
    fun open(url: String, maxRate: Int): PcmStream?
}

/** PCM de 16 bits intercalado. */
interface PcmStream : Closeable {
    val sampleRate: Int
    val channels: Int
    /** Llena [dst] con muestras intercaladas; devuelve cuántas, o -1 al terminar. */
    fun read(dst: ShortArray): Int
    fun seek(ms: Long)
}

/**
 * WAV estéreo de 16 bits para la tele (lo aceptan Chromecast, Roku y casi todo DLNA). Se manda con
 * tamaño exacto (calculado con la duración) para que la tele pueda adelantar con Range: lo que falte
 * al final se rellena con silencio y lo que sobre se corta.
 */
object CastWav {
    const val HEADER = 44
    const val MAX_RATE = 48_000

    /** La frecuencia que se manda: la original hasta 48 kHz; las hi-res bajan a 44.1 o 48 kHz. */
    fun outRate(src: Int) = when {
        src <= MAX_RATE -> src
        src % 44_100 == 0 -> 44_100
        else -> 48_000
    }

    /** Bytes de audio para [durationMs] (múltiplo de un frame de 4 bytes). */
    fun dataBytes(rate: Int, durationMs: Long): Long = durationMs * rate / 1000 * 4

    fun header(rate: Int, dataBytes: Long): ByteArray {
        val data = dataBytes.coerceAtMost(0xFFFFFFFFL - 36)
        val b = java.nio.ByteBuffer.allocate(HEADER).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt((data + 36).toInt()).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(2).putInt(rate).putInt(rate * 4).putShort(4).putShort(16)
        b.put("data".toByteArray()).putInt(data.toInt())
        return b.array()
    }

    /**
     * Escribe el WAV desde el byte [from] (para Range) hasta [total] bytes en total (header incluido).
     * [stream] ya debe venir abierto; aquí se adelanta a donde toque.
     */
    fun write(stream: PcmStream, out: OutputStream, total: Long, from: Long) {
        val rate = outRate(stream.sampleRate)
        val header = header(rate, total - HEADER)
        var pos = from
        if (pos < HEADER) { out.write(header, pos.toInt(), HEADER - pos.toInt()); pos = HEADER.toLong() }
        // Frame de inicio y el desfase dentro de él (si la tele pidió a media muestra).
        val frame = (pos - HEADER) / 4
        var skip = ((pos - HEADER) % 4).toInt()
        if (frame > 0) stream.seek(frame * 1000 / rate)
        val res = Resampler(stream.sampleRate, rate, stream.channels)
        val inBuf = ShortArray(8192)
        val bytes = ByteArray(16384 * 4)
        while (pos < total) {
            val n = stream.read(inBuf)
            if (n < 0) break
            val frames = res.process(inBuf, n)
            var p = 0
            for (i in 0 until frames * 2) {
                val v = res.out[i].toInt()
                bytes[p++] = v.toByte(); bytes[p++] = (v shr 8).toByte()
            }
            val start = skip; skip = 0
            val len = (p - start).coerceAtMost((total - pos).toInt().coerceAtLeast(0))
            if (len > 0) { out.write(bytes, start, len); pos += len }
        }
        // Silencio hasta completar lo prometido (la duración del servidor viene redondeada al segundo).
        val zeros = ByteArray(16384)
        while (pos < total) { val len = minOf(zeros.size.toLong(), total - pos).toInt(); out.write(zeros, 0, len); pos += len }
        out.flush()
    }

    /** A estéreo y a la frecuencia de salida (promedio de muestras al bajar en múltiplos exactos; lineal si no). */
    private class Resampler(private val src: Int, private val dst: Int, private val ch: Int) {
        var out = ShortArray(0)
        private val ratio = src.toDouble() / dst
        private val integer = src % dst == 0
        private var t = 0.0
        private var lastL = 0f; private var lastR = 0f

        fun process(inp: ShortArray, n: Int): Int {
            val frames = n / ch.coerceAtLeast(1)
            fun l(i: Int) = inp[i * ch].toFloat()
            fun r(i: Int) = if (ch >= 2) inp[i * ch + 1].toFloat() else inp[i * ch].toFloat()
            val max = (frames / ratio).toInt() + 2
            if (out.size < max * 2) out = ShortArray(max * 2)
            var o = 0
            if (src == dst) {
                for (i in 0 until frames) { out[o++] = l(i).toInt().toShort(); out[o++] = r(i).toInt().toShort() }
                return frames
            }
            if (integer) {
                val k = src / dst
                var i = 0
                while (i + k <= frames) {
                    var sl = 0f; var sr = 0f
                    for (j in 0 until k) { sl += l(i + j); sr += r(i + j) }
                    out[o++] = (sl / k).toInt().toShort(); out[o++] = (sr / k).toInt().toShort()
                    i += k
                }
                return o / 2
            }
            while (t < frames) {
                val i = t.toInt(); val f = (t - i).toFloat()
                val l0 = if (i == 0) lastL else l(i - 1); val r0 = if (i == 0) lastR else r(i - 1)
                out[o++] = (l0 + (l(i) - l0) * f).toInt().toShort(); out[o++] = (r0 + (r(i) - r0) * f).toInt().toShort()
                t += ratio
            }
            t -= frames
            if (frames > 0) { lastL = l(frames - 1); lastR = r(frames - 1) }
            return o / 2
        }
    }
}

/** Cierra sin que un error al cerrar tape el de verdad. */
internal fun Closeable.closeQuietly() { runCatching { close() } }
