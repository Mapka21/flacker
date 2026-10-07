package com.marco.audiopro.desktop

import com.marco.audiopro.audio.AudioEngine
import com.marco.audiopro.data.KeyValueStore
import com.marco.audiopro.data.Settings
import com.marco.audiopro.data.subsonic.SubsonicApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Prueba de integración del motor de PC contra un servidor Subsonic de prueba.
 * Sólo corre si existe MARCO_TEST_SERVER (p. ej. http://127.0.0.1:8765) y hay salida de audio.
 * Reproduce con volumen 0: no se oye nada.
 */
class DesktopPlayerTest {
    private class MemStore : KeyValueStore {
        val m = HashMap<String, Any?>()
        override fun getString(key: String) = m[key] as? String
        override fun getFloat(key: String, def: Float) = m[key] as? Float ?: def
        override fun getInt(key: String, def: Int) = m[key] as? Int ?: def
        override fun getBoolean(key: String, def: Boolean) = m[key] as? Boolean ?: def
        override fun edit(values: Map<String, Any?>) { m.putAll(values) }
    }

    private fun waitFor(timeoutMs: Long, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { if (cond()) return true; Thread.sleep(50) }
        return cond()
    }

    @Test fun playsSeeksAndAdvances() = runBlocking {
        val server = System.getenv("MARCO_TEST_SERVER")
        assumeTrue("Sin servidor de prueba", server != null)
        val settings = Settings(MemStore())
        settings.saveAccount(SubsonicApi.login(server!!, "test", "test"))
        val api = SubsonicApi(OkHttpClient()) { settings.account.value }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val player = DesktopPlayer(api, settings, scope)
        val engine = AudioEngine(settings, player.tap)
        player.eq = engine.eq
        player.onSignal = { engine.signal.value = it }
        player.setVolume(0f)

        val songs = api.album("al1").song
        assertEquals(2, songs.size)
        player.play(songs)

        assertTrue("no empezó a sonar", waitFor(8000) { player.state.value.isPlaying })
        Thread.sleep(1500)
        val pos = player.positionMs()
        assertTrue("la posición no avanza: $pos", pos in 800..4000)

        // Los visualizadores reciben audio (el tap va antes del volumen).
        val l = FloatArray(4096); val r = FloatArray(4096)
        assertTrue(player.tap.read(l, r, 4096, 0))
        val rms = sqrt(l.sumOf { (it * it).toDouble() } / l.size)
        assertTrue("tap en silencio: $rms", rms > 0.01)
        assertTrue(engine.signal.value.output.contains("bit"))
        assertEquals(-4f, engine.signal.value.replayGainDb!!, 0.01f)

        player.seekTo(8000)
        assertTrue("el seek no llegó: ${player.positionMs()}", waitFor(4000) { player.positionMs() in 8000..10500 })

        // Fin natural de la pista 1 → la 2 entra sola (precarga + gapless, sin vaciar la salida).
        player.seekTo(10_500)
        assertTrue("no pasó sola a la pista 2", waitFor(8000) { player.state.value.currentIndex == 1 && player.state.value.isPlaying })
        assertTrue("la pista 2 no arrancó desde el inicio: ${player.positionMs()}", player.positionMs() < 3000)

        // Siguiente manual al final de la cola sin repetir: no hace nada.
        player.next()
        Thread.sleep(300)
        assertEquals(1, player.state.value.currentIndex)

        player.toggle()
        assertTrue(waitFor(2000) { !player.state.value.isPlaying })
        val paused = player.positionMs()
        Thread.sleep(600)
        assertTrue("en pausa la posición siguió avanzando", player.positionMs() - paused < 50)

        player.shutdown()
        scope.cancel()
    }
}
