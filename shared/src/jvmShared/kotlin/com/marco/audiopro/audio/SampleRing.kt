package com.marco.audiopro.audio

/** De dónde leen los visualizadores: el audio que está sonando *ahora*, post-EQ. */
interface SampleSource {
    val sampleRate: Int
    /**
     * Copia los `n` frames que deberían estar sonando en este instante.
     * @param extraLatencyMs ajuste de sync elegido por el usuario.
     * @return false si todavía no hay audio.
     */
    fun read(dstL: FloatArray, dstR: FloatArray, n: Int, extraLatencyMs: Int): Boolean
}

/** Buffer circular estéreo de muestras float, indexado por frame absoluto. */
class SampleRing(private val capacity: Int = 1 shl 17) {
    private val mask = (capacity - 1).toLong()
    private val l = FloatArray(capacity)
    private val r = FloatArray(capacity)

    @Volatile var writePos = 0L; private set

    init { require(capacity and (capacity - 1) == 0) }

    /** Agrega `frames` frames intercalados de `channels` canales (se toman los dos primeros). */
    fun write(interleaved: FloatArray, frames: Int, channels: Int) {
        var w = writePos
        var p = 0
        for (f in 0 until frames) {
            val left = interleaved[p]
            val right = if (channels > 1) interleaved[p + 1] else left
            val idx = (w and mask).toInt()
            l[idx] = left; r[idx] = right
            w++
            p += channels
        }
        writePos = w
    }

    /** Copia los `n` frames que terminan en el frame absoluto `end`. */
    fun copyEndingAt(end: Long, dstL: FloatArray, dstR: FloatArray, n: Int): Boolean {
        val begin = end - n
        if (end <= 0 || begin < writePos - capacity) return false
        for (i in 0 until n) {
            val f = begin + i
            if (f < 0) { dstL[i] = 0f; dstR[i] = 0f; continue }
            val idx = (f and mask).toInt()
            dstL[i] = l[idx]; dstR[i] = r[idx]
        }
        return true
    }

    fun reset() {
        writePos = 0
        l.fill(0f); r.fill(0f)
    }
}
