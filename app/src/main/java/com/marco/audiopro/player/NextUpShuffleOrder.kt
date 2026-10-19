package com.marco.audiopro.player

import androidx.media3.common.C
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlin.random.Random

/**
 * Orden aleatorio como el de Spotify: lo que se mete con "Reproducir a continuación" (justo
 * después de la actual en la cola) suena justo después de la actual también en aleatorio, en vez
 * de caer en un lugar al azar como con el orden de Media3.
 */
class NextUpShuffleOrder private constructor(private val shuffled: IntArray) : ShuffleOrder {
    constructor() : this(IntArray(0))

    private val posOf = IntArray(shuffled.size).also { p -> shuffled.forEachIndexed { pos, i -> p[i] = pos } }

    override fun getLength() = shuffled.size
    override fun getNextIndex(index: Int) = posOf[index].let { if (it + 1 < shuffled.size) shuffled[it + 1] else C.INDEX_UNSET }
    override fun getPreviousIndex(index: Int) = posOf[index].let { if (it > 0) shuffled[it - 1] else C.INDEX_UNSET }
    override fun getLastIndex() = shuffled.lastOrNull() ?: C.INDEX_UNSET
    override fun getFirstIndex() = shuffled.firstOrNull() ?: C.INDEX_UNSET

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        val added = IntArray(insertionCount) { insertionIndex + it }
        if (shuffled.isEmpty()) return NextUpShuffleOrder(added.also { it.shuffle(Random) })
        val shifted = shuffled.map { if (it >= insertionIndex) it + insertionCount else it }
        // Van después de la canción que quedó justo antes en la cola (con "a continuación", la actual).
        val anchor = insertionIndex - 1
        val pos = if (anchor < 0) 0 else shifted.indexOf(anchor) + 1
        return NextUpShuffleOrder((shifted.take(pos) + added.toList() + shifted.drop(pos)).toIntArray())
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val n = indexToExclusive - indexFrom
        return NextUpShuffleOrder(
            shuffled.filter { it < indexFrom || it >= indexToExclusive }.map { if (it >= indexToExclusive) it - n else it }.toIntArray(),
        )
    }

    override fun cloneAndClear(): ShuffleOrder = NextUpShuffleOrder()
}
