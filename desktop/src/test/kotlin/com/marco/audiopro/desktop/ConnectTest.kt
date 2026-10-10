package com.marco.audiopro.desktop

import com.marco.audiopro.connect.ConnectService
import com.marco.audiopro.data.KeyValueStore
import com.marco.audiopro.data.Settings
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.player.PlaybackState
import com.marco.audiopro.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dos "dispositivos" Marco Connect en la misma PC: se descubren por UDP y se pasan la música
 * por TCP (misma cola, misma posición; el que la entrega se pausa).
 */
class ConnectTest {
    private class MemStore : KeyValueStore {
        val m = HashMap<String, Any?>()
        override fun getString(key: String) = m[key] as? String
        override fun getFloat(key: String, def: Float) = m[key] as? Float ?: def
        override fun getInt(key: String, def: Int) = m[key] as? Int ?: def
        override fun getBoolean(key: String, def: Boolean) = m[key] as? Boolean ?: def
        override fun edit(values: Map<String, Any?>) { m.putAll(values) }
    }

    /** Reproductor de mentiras: sólo guarda la cola y si "suena". */
    private class FakePlayer : PlayerController {
        val s = MutableStateFlow(PlaybackState())
        var pos = 0L
        override val state: StateFlow<PlaybackState> = s
        val vol = MutableStateFlow(0.5f)
        override val volume: StateFlow<Float> = vol
        override fun setVolume(v: Float) { vol.value = v }
        override fun play(songs: List<Song>, startIndex: Int, shuffle: Boolean) = playFrom(songs, startIndex, 0)
        override fun playFrom(songs: List<Song>, index: Int, positionMs: Long) {
            pos = positionMs
            s.value = PlaybackState(current = songs[index], currentIndex = index, isPlaying = true, durationMs = 200_000, queue = songs)
        }
        override fun playNext(songs: List<Song>) {}
        override fun enqueue(songs: List<Song>) {}
        override fun toggle() { s.value = s.value.copy(isPlaying = !s.value.isPlaying) }
        override fun next() {}
        override fun previous() {}
        override fun seekTo(ms: Long) { pos = ms }
        override fun skipToIndex(i: Int) { s.value = s.value.copy(currentIndex = i, current = s.value.queue.getOrNull(i)) }
        override fun removeAt(i: Int) {}
        override fun toggleShuffle() {}
        override fun cycleRepeat() {}
        override fun positionMs() = pos
    }

    private fun device(name: String, player: FakePlayer, scope: CoroutineScope): ConnectService {
        val settings = Settings(MemStore())
        settings.saveAccount(SubsonicApi.login("http://127.0.0.1:8765", "marco", "x"))
        return ConnectService(name, "windows", settings, player, scope)
    }

    private fun waitFor(ms: Long, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (cond()) return true; Thread.sleep(50) }
        return cond()
    }

    @Test fun discoverTransferAndPullBack() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val phonePlayer = FakePlayer()
        val pcPlayer = FakePlayer()
        val phone = device("Celular", phonePlayer, scope)
        val pc = device("PC", pcPlayer, scope)
        phone.start(); pc.start()

        val songs = listOf(Song("a", "Uno"), Song("b", "Dos"), Song("c", "Tres"))
        phonePlayer.playFrom(songs, 1, 42_000)

        assertTrue("el celular no vio la PC", waitFor(8000) { phone.devices.value.any { it.name == "PC" } })
        assertTrue("la PC no vio el celular", waitFor(8000) { pc.devices.value.any { it.name == "Celular" } })
        // El faro de la PC trae lo que suena (todavía nada).
        val pcDevice = phone.devices.value.first { it.name == "PC" }

        assertTrue(phone.transferTo(pcDevice))
        assertTrue("la PC no empezó a sonar", waitFor(3000) { pcPlayer.state.value.isPlaying })
        assertEquals(songs, pcPlayer.state.value.queue)
        assertEquals(1, pcPlayer.state.value.currentIndex)
        assertEquals(42_000L, pcPlayer.pos)
        assertTrue("el celular no se pausó", !phonePlayer.state.value.isPlaying)

        // El faro ya anuncia "suena en PC" con la canción correcta.
        assertTrue(waitFor(5000) { phone.devices.value.firstOrNull { it.name == "PC" }?.nowPlaying?.let { it.playing && it.title == "Dos" } == true })

        // Traer de regreso al celular.
        pcPlayer.pos = 50_000
        assertTrue(phone.pullFrom(phone.devices.value.first { it.name == "PC" }))
        assertTrue(phonePlayer.state.value.isPlaying)
        assertEquals(50_000L, phonePlayer.pos)
        assertTrue("la PC no se pausó", waitFor(2000) { !pcPlayer.state.value.isPlaying })

        phone.stop(); pc.stop()
        scope.cancel()
    }

    /** Como Spotify: después de pasar la música, el celular controla la PC (pausa, barra, volumen, otra rola). */
    @Test fun controlsRemoteLikeSpotify() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val phonePlayer = FakePlayer()
        val pcPlayer = FakePlayer()
        val phone = device("Celular", phonePlayer, scope)
        val pc = device("PC", pcPlayer, scope)
        phone.start(); pc.start()
        val songs = listOf(Song("a", "Uno"), Song("b", "Dos"), Song("c", "Tres"))
        phonePlayer.playFrom(songs, 0, 1000)
        assertTrue(waitFor(8000) { phone.devices.value.any { it.name == "PC" } })
        val routed = com.marco.audiopro.player.RoutedPlayer(phonePlayer, phone, scope)

        assertTrue(phone.transferTo(phone.devices.value.first { it.name == "PC" }))
        assertEquals("PC", phone.controlling.value?.name)
        assertTrue("la UI no muestra lo de la PC", waitFor(3000) { routed.state.value.remoteName == "PC" && routed.state.value.current?.title == "Uno" })

        routed.toggle()
        assertTrue("no se pausó la PC", waitFor(3000) { !pcPlayer.state.value.isPlaying })
        routed.seekTo(30_000)
        assertTrue("no se movió la barra en la PC", waitFor(3000) { pcPlayer.pos == 30_000L })
        routed.setVolume(0.2f)
        assertTrue("no cambió el volumen de la PC", waitFor(3000) { pcPlayer.vol.value == 0.2f })
        assertTrue(waitFor(3000) { routed.volume!!.value == 0.2f })
        routed.skipToIndex(2)
        assertTrue(waitFor(3000) { routed.state.value.current?.title == "Tres" })
        routed.play(listOf(Song("z", "Nueva")))
        assertTrue("no puso la rola nueva en la PC", waitFor(3000) { pcPlayer.state.value.current?.title == "Nueva" })
        assertTrue(!phonePlayer.state.value.isPlaying)

        // "Este dispositivo": la música regresa y se deja de controlar.
        assertTrue(phone.pullFrom(phone.devices.value.first { it.name == "PC" }))
        assertEquals(null, phone.controlling.value)
        assertTrue(waitFor(2000) { routed.state.value.remoteName == null && routed.state.value.current?.title == "Nueva" })
        phone.stop(); pc.stop()
        scope.cancel()
    }

    @Test fun ignoresOtherAccounts() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val mine = device("Mío", FakePlayer(), scope)
        val otherSettings = Settings(MemStore()).apply { saveAccount(SubsonicApi.login("http://otro:4533", "alguien", "y")) }
        val other = ConnectService("Ajeno", "windows", otherSettings, FakePlayer(), scope)
        mine.start(); other.start()
        Thread.sleep(5000)
        assertTrue("vio un dispositivo de otra cuenta", mine.devices.value.none { it.name == "Ajeno" })
        mine.stop(); other.stop()
        scope.cancel()
    }
}
