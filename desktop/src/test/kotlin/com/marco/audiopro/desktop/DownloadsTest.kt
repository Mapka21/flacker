package com.marco.audiopro.desktop

import com.marco.audiopro.audio.AudioEngine
import com.marco.audiopro.data.KeyValueStore
import com.marco.audiopro.data.Settings
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.download.DesktopDownloads
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.Executors

/** Descargas de PC contra un servidor chiquito propio: bajar, cerrar, apagar el servidor y que siga sonando. */
class DownloadsTest {
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

    private fun wav(): ByteArray {
        val n = 44100 * 4
        val fmt = javax.sound.sampled.AudioFormat(44100f, 16, 2, true, false)
        val out = java.io.ByteArrayOutputStream()
        javax.sound.sampled.AudioSystem.write(javax.sound.sampled.AudioInputStream(ByteArray(n * 4).inputStream(), fmt, n.toLong()), javax.sound.sampled.AudioFileFormat.Type.WAVE, out)
        return out.toByteArray()
    }

    @Test fun downloadsAndPlaysWithServerOff() {
        val audio = wav()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/rest/") { ex ->
            val q = ex.requestURI.query.orEmpty()
            when {
                "getCoverArt" in ex.requestURI.path -> { ex.responseHeaders["Content-Type"] = "image/jpeg"; ex.sendResponseHeaders(200, 3); ex.responseBody.write(byteArrayOf(1, 2, 3)) }
                // Subsonic avisa de los errores con un 200 y JSON.
                "id=rota" in q -> { val b = "{}".toByteArray(); ex.responseHeaders["Content-Type"] = "application/json"; ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.write(b) }
                "id=lenta" in q -> {
                    ex.responseHeaders["Content-Type"] = "audio/wav"; ex.sendResponseHeaders(200, 0)
                    runCatching { repeat(200) { ex.responseBody.write(ByteArray(4096)); ex.responseBody.flush(); Thread.sleep(50) } }
                }
                else -> { ex.responseHeaders["Content-Type"] = "audio/wav"; ex.sendResponseHeaders(200, audio.size.toLong()); ex.responseBody.write(audio) }
            }
            runCatching { ex.close() }
        }
        server.start()
        val dir = Files.createTempDirectory("flacker-descargas").toFile()
        val settings = Settings(MemStore())
        settings.saveAccount(SubsonicApi.login("http://127.0.0.1:${server.address.port}", "test", "test"))
        val api = SubsonicApi(OkHttpClient()) { settings.account.value }
        val a = Song(id = "1", title = "Uno: ¿qué?", album = "Disco / A", albumId = "al", artist = "AC/DC", track = 1, suffix = "wav", coverArt = "c")
        val b = a.copy(id = "2", title = "Dos", artist = "Otro invitado", track = 2)
        val twin = a.copy(id = "3")
        val broken = a.copy(id = "rota", title = "Rota", track = 4)
        val slow = a.copy(id = "lenta", title = "Lenta", track = 5)

        val s1 = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val d = DesktopDownloads(dir, OkHttpClient(), api, settings, s1)
        d.download(listOf(a, b, twin, a, a.copy(id = "x", externalUrl = "http://x")))
        assertTrue("bajan", waitFor(20_000) { d.items.value.size == 3 && d.items.value.values.all { it.done } })
        assertEquals("AC_DC/Disco _ A/01 Uno_ ¿qué_.wav", d.file("1")!!.relativeTo(dir).invariantSeparatorsPath)
        assertEquals("el invitado se queda en la carpeta del disco", d.file("1")!!.parentFile, d.file("2")!!.parentFile)
        assertFalse("mismo nombre, otro archivo", d.file("1") == d.file("3"))
        assertEquals(audio.size.toLong(), d.file("1")!!.length())
        assertTrue(File(d.file("1")!!.parentFile, "cover.jpg").exists())

        d.download(listOf(broken))
        assertTrue("la rota falla", waitFor(30_000) { d.items.value["rota"]?.failed == true })
        assertNull(d.file("rota"))

        d.download(listOf(slow))
        assertTrue("va bajando", waitFor(5000) { d.pollProgress(); (d.items.value["lenta"]?.bytes ?: 0) > 0 })
        d.remove(listOf(slow, broken))
        assertTrue("cancelar limpia", waitFor(5000) { dir.walk().none { it.name.endsWith(".part") } })

        // Se cierra la app y se apaga el servidor.
        s1.cancel(); server.stop(0)
        val s2 = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val d2 = DesktopDownloads(dir, OkHttpClient(), api, settings, s2)
        assertEquals(setOf("1", "2", "3"), d2.items.value.keys)
        assertTrue(d2.items.value.values.all { it.done })
        val player = DesktopPlayer(api, settings, s2)
        player.eq = AudioEngine(settings, player.tap).eq
        player.offline = { d2.file(it) }
        player.setVolume(0f)
        player.play(listOf(a, b), 0, false)
        assertTrue("suena sin servidor: ${player.state.value.error}", waitFor(8000) { player.positionMs() > 800 })
        player.next()
        assertTrue("y la siguiente", waitFor(8000) { player.state.value.current?.id == "2" && player.positionMs() in 300..3500 })
        player.shutdown()

        Thread.sleep(500)
        d2.remove(listOf(a, b, twin))
        assertTrue(d2.items.value.isEmpty())
        // Un archivo borrado a mano tampoco cuenta al volver a abrir.
        assertTrue(DesktopDownloads(dir, OkHttpClient(), api, settings, s2).items.value.isEmpty())
        s2.cancel()
        dir.deleteRecursively()
    }
}
