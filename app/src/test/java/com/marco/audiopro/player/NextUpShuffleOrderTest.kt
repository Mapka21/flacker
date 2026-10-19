package com.marco.audiopro.player

import androidx.media3.common.C
import androidx.media3.exoplayer.source.ShuffleOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class NextUpShuffleOrderTest {
    private fun order(o: ShuffleOrder): List<Int> =
        generateSequence(o.firstIndex.takeIf { it != C.INDEX_UNSET }) { o.getNextIndex(it).takeIf { n -> n != C.INDEX_UNSET } }.toList()

    @Test fun playNextGoesRightAfterCurrent() {
        val o = NextUpShuffleOrder().cloneAndInsert(0, 10)
        assertEquals((0 until 10).toSet(), order(o).toSet())
        // Suena el índice 4; "a continuación" mete 2 canciones en la posición 5 de la cola.
        val cur = 4
        val seq = order(o.cloneAndInsert(cur + 1, 2))
        assertEquals(12, seq.size)
        val at = seq.indexOf(cur)
        assertEquals(listOf(5, 6), seq.subList(at + 1, at + 3))
        // Lo demás sigue en el mismo orden (corrido).
        assertEquals(order(o).map { if (it >= 5) it + 2 else it }, seq.filter { it != 5 && it != 6 })
    }

    @Test fun removeKeepsTheRest() {
        val o = NextUpShuffleOrder().cloneAndInsert(0, 6)
        val after = order(o.cloneAndRemove(2, 4))
        assertEquals(order(o).filter { it < 2 || it >= 4 }.map { if (it >= 4) it - 2 else it }, after)
        assertEquals(0, o.cloneAndClear().length)
    }
}
