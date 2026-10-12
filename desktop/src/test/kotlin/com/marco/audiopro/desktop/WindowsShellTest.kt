package com.marco.audiopro.desktop

import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.player.PlaybackState
import com.marco.audiopro.player.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.swing.JFrame
import javax.swing.SwingUtilities

/** Botones de la barra de tareas y control multimedia de Windows en una ventana de prueba. */
class WindowsShellTest {
    private class FakePlayer : PlayerController {
        override val state = MutableStateFlow(PlaybackState())
        override val volume = null
        var toggles = 0
        override fun play(songs: List<Song>, startIndex: Int, shuffle: Boolean) {}
        override fun playFrom(songs: List<Song>, index: Int, positionMs: Long) {}
        override fun playNext(songs: List<Song>) {}
        override fun enqueue(songs: List<Song>) {}
        override fun toggle() { toggles++; state.value = state.value.copy(isPlaying = !state.value.isPlaying) }
        override fun next() {}
        override fun previous() {}
        override fun seekTo(ms: Long) {}
        override fun skipToIndex(i: Int) {}
        override fun removeAt(i: Int) {}
        override fun toggleShuffle() {}
        override fun cycleRepeat() {}
        override fun positionMs() = 42_000L
    }

    @Test
    fun installsTaskbarButtonsAndMediaControls() {
        val player = FakePlayer()
        lateinit var frame: JFrame
        SwingUtilities.invokeAndWait { frame = JFrame("Flacker prueba").apply { setSize(400, 300); isVisible = true } }
        WindowsShell.install(frame, player, OkHttpClient()) { null }
        player.state.value = PlaybackState(Song(id = "1", title = "Hey You", artist = "Pink Floyd", album = "The Wall"), isPlaying = true, durationMs = 280_000)
        Thread.sleep(1500)
        player.state.value = player.state.value.copy(isPlaying = false)
        Thread.sleep(800)
        assertEquals(true to true, WindowsShell.active)
        SwingUtilities.invokeAndWait { frame.dispose() }
    }
}
