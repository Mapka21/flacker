package com.marco.audiopro.desktop

import com.marco.audiopro.connect.ConnectService
import com.marco.audiopro.data.CustomCovers
import com.marco.audiopro.data.KeyValueStore
import com.marco.audiopro.data.Likes
import com.marco.audiopro.data.PlaylistStore
import com.marco.audiopro.data.Settings
import com.marco.audiopro.data.SyncService
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
import okhttp3.OkHttpClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** Portadas propias y ajustes compartidos viajan solos entre dos dispositivos de la misma red. */
class SyncTest {
    private class MemStore : KeyValueStore {
        val m = HashMap<String, Any?>()
        override fun getString(key: String) = m[key] as? String
        override fun getFloat(key: String, def: Float) = m[key] as? Float ?: def
        override fun getInt(key: String, def: Int) = m[key] as? Int ?: def
        override fun getBoolean(key: String, def: Boolean) = m[key] as? Boolean ?: def
        override fun edit(values: Map<String, Any?>) { m.putAll(values) }
    }

    private class Idle : PlayerController {
        override val state: StateFlow<PlaybackState> = MutableStateFlow(PlaybackState())
        override val volume: StateFlow<Float>? = null
        override fun play(songs: List<Song>, startIndex: Int, shuffle: Boolean) {}
        override fun playFrom(songs: List<Song>, index: Int, positionMs: Long) {}
        override fun playNext(songs: List<Song>) {}
        override fun enqueue(songs: List<Song>) {}
        override fun toggle() {}
        override fun next() {}
        override fun previous() {}
        override fun seekTo(ms: Long) {}
        override fun skipToIndex(i: Int) {}
        override fun removeAt(i: Int) {}
        override fun toggleShuffle() {}
        override fun cycleRepeat() {}
        override fun positionMs() = 0L
    }

    private class Device(name: String, scope: CoroutineScope) {
        val settings = Settings(MemStore()).apply { saveAccount(SubsonicApi.login("http://127.0.0.1:9", "marco", "x")) }
        val api = SubsonicApi(OkHttpClient()) { settings.account.value }
        val covers = CustomCovers(Files.createTempDirectory("covers").toFile())
        val connect = ConnectService(name, "windows", settings, Idle(), scope)
        val sync = SyncService(settings, covers, Likes(api, scope), PlaylistStore(api), connect, scope)
        fun start() { connect.start(); sync.start() }
    }

    private fun waitFor(ms: Long, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (cond()) return true; Thread.sleep(100) }
        return cond()
    }

    @Test fun coversAndSettingsTravel() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val pc = Device("PC", scope)
        val phone = Device("Celular", scope)
        pc.start(); phone.start()
        assertTrue("no se vieron", waitFor(8000) { pc.connect.devices.value.isNotEmpty() && phone.connect.devices.value.isNotEmpty() })

        val img = ByteArray(5000) { (it % 251).toByte() }
        pc.covers.set(CustomCovers.LIKED, img)
        pc.settings.updatePrefs { it.copy(lastFmUser = "marcotest") }

        assertTrue("no llegó la portada", waitFor(15000) { phone.covers.get(CustomCovers.LIKED) != null })
        assertArrayEquals(img, phone.covers.get(CustomCovers.LIKED)!!.readBytes())
        assertTrue("no llegó el ajuste", waitFor(15000) { phone.settings.prefs.value.lastFmUser == "marcotest" })

        // Quitarla en el celular también la quita en la PC (gana lo más reciente).
        Thread.sleep(20)
        phone.covers.clear(CustomCovers.LIKED)
        assertTrue("no se quitó en la PC", waitFor(15000) { pc.covers.get(CustomCovers.LIKED) == null })
        // Y no se quedan rebotando cambios: el ajuste sigue igual en los dos.
        Thread.sleep(3000)
        assertEquals("marcotest", pc.settings.prefs.value.lastFmUser)
        pc.connect.stop(); phone.connect.stop()
        scope.cancel()
    }
}
