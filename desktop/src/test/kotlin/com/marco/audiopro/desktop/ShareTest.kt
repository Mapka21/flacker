package com.marco.audiopro.desktop

import com.marco.audiopro.cast.CastRelay
import com.marco.audiopro.connect.ConnectService
import com.marco.audiopro.data.KeyValueStore
import com.marco.audiopro.data.ServerAccount
import com.marco.audiopro.data.Settings
import com.marco.audiopro.data.local.LocalLibrary
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.player.PlaybackState
import com.marco.audiopro.player.PlayerController
import com.marco.audiopro.share.ShareFile
import com.marco.audiopro.share.ShareService
import com.marco.audiopro.share.Transfer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.random.Random

/**
 * Dos Flacker de cuentas distintas en la misma PC: "PC-A" sin servidor (con 3 FLAC de mentiras) y
 * "Cel-B" con otro Navidrome. Se mandan canciones (aceptar, rechazar, las que ya tiene) y se
 * emparejan para que B toque la música de A sin ver su biblioteca.
 */
class ShareTest {
    private class MemStore : KeyValueStore {
        val m = HashMap<String, Any?>()
        override fun getString(key: String) = m[key] as? String
        override fun getFloat(key: String, def: Float) = m[key] as? Float ?: def
        override fun getInt(key: String, def: Int) = m[key] as? Int ?: def
        override fun getBoolean(key: String, def: Boolean) = m[key] as? Boolean ?: def
        override fun edit(values: Map<String, Any?>) { m.putAll(values) }
    }

    private class FakePlayer : PlayerController {
        val s = MutableStateFlow(PlaybackState())
        override val state: StateFlow<PlaybackState> = s
        override val volume: StateFlow<Float>? = null
        override fun setVolume(v: Float) {}
        override fun play(songs: List<Song>, startIndex: Int, shuffle: Boolean) = playFrom(songs, startIndex, 0)
        override fun playFrom(songs: List<Song>, index: Int, positionMs: Long) {
            s.value = PlaybackState(current = songs[index], currentIndex = index, isPlaying = true, durationMs = 200_000, queue = songs)
        }
        override fun playNext(songs: List<Song>) {}
        override fun enqueue(songs: List<Song>) {}
        override fun toggle() { s.value = s.value.copy(isPlaying = !s.value.isPlaying) }
        override fun next() {}
        override fun previous() {}
        override fun seekTo(ms: Long) {}
        override fun skipToIndex(i: Int) {}
        override fun removeAt(i: Int) {}
        override fun toggleShuffle() {}
        override fun cycleRepeat() {}
        override fun positionMs() = 0L
    }

    private lateinit var scope: CoroutineScope
    private lateinit var tmp: File
    private val http = OkHttpClient()
    private lateinit var lib: LocalLibrary
    private lateinit var apiA: SubsonicApi
    private lateinit var a: ConnectService
    private lateinit var b: ConnectService
    private lateinit var shareA: ShareService
    private lateinit var shareB: ShareService
    private val playerA = FakePlayer()
    private val playerB = FakePlayer()
    private lateinit var inboxB: File

    private fun sha(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

    private fun waitFor(ms: Long, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (cond()) return true; Thread.sleep(50) }
        return cond()
    }

    @Before fun setUp() = runBlocking {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        tmp = Files.createTempDirectory("share").toFile()
        val music = File(tmp, "musica/Artista X/Disco Y").apply { mkdirs() }
        listOf("01 Uno", "02 Dos", "03 Tres").forEach { File(music, "$it.flac").writeBytes(Random.nextBytes(Random.nextInt(200_000, 1_500_000))) }
        lib = LocalLibrary(File(tmp, "datosA").apply { mkdirs() }, FolderSource { listOf(File(tmp, "musica")) })
        assertEquals(3, lib.rescan())
        val port = lib.server.start(0)
        val settingsA = Settings(MemStore()).apply { saveAccount(ServerAccount("http://127.0.0.1:$port", "local", "local", "local")) }
        apiA = SubsonicApi(http) { settingsA.account.value }
        val settingsB = Settings(MemStore()).apply { saveAccount(SubsonicApi.login("http://otro:4533", "alguien", "y")) }
        val apiB = SubsonicApi(http) { settingsB.account.value }
        a = ConnectService("PC-A", "windows", settingsA, playerA, scope, CastRelay(http, apiA))
        b = ConnectService("Cel-B", "android", settingsB, playerB, scope, CastRelay(http, apiB))
        inboxB = File(tmp, "recibidoB")
        shareA = ShareService(a, apiA, http, FolderInbox(settingsA, File(tmp, "recibidoA")), File(tmp, "tmpA"), lib, settingsA, scope)
        shareB = ShareService(b, apiB, http, FolderInbox(settingsB, inboxB), File(tmp, "tmpB"), null, settingsB, scope)
        a.start(); b.start()
        assertTrue("A no vio a B", waitFor(8000) { a.nearby.value.any { it.name == "Cel-B" } })
        assertTrue("B no vio a A", waitFor(8000) { b.nearby.value.any { it.name == "PC-A" } })
    }

    @After fun tearDown() {
        a.stop(); b.stop(); lib.server.stop()
        scope.cancel()
        tmp.deleteRecursively()
    }

    private fun lastOut() = shareA.transfers.value.last()

    @Test fun sendsOriginalFilesAfterAccepting() = runBlocking {
        scope.launch { shareB.incoming.filterNotNull().collect { shareB.answer(it, true) } }
        val songs = apiA.allSongs(0)
        assertEquals(3, songs.size)
        shareA.send(a.nearby.value.first { it.name == "Cel-B" }, songs)
        assertTrue("no terminó", waitFor(20_000) { lastOut().finished })
        println("SHARE A: ${lastOut().message}")
        assertEquals(Transfer.State.DONE, lastOut().state)
        assertEquals(3, lastOut().done)
        assertEquals(lastOut().totalBytes, lastOut().bytes)

        // Llegan idénticos, en Artista/Álbum/NN - Título.
        // (Sin etiquetas, el título es el nombre del archivo.)
        val got = inboxB.walkTopDown().filter { it.isFile }.associateBy { it.nameWithoutExtension }
        val sent = File(tmp, "musica").walkTopDown().filter { it.isFile }.associateBy { it.nameWithoutExtension }
        assertEquals(sent.keys, got.keys)
        sent.forEach { (k, f) -> assertEquals("$k llegó distinto", sha(f), sha(got.getValue(k))) }
        assertTrue(got.values.all { it.parentFile.name == "Disco Y" && it.parentFile.parentFile.name == "Artista X" })
        assertTrue(waitFor(3000) { shareB.transfers.value.lastOrNull()?.state == Transfer.State.DONE })
        println("SHARE B: ${shareB.transfers.value.last().message}")
        assertTrue(File(tmp, "tmpB").listFiles().orEmpty().isEmpty())

        // Otra vez: B ya las tiene, no viaja nada.
        shareA.send(a.nearby.value.first { it.name == "Cel-B" }, songs)
        assertTrue(waitFor(20_000) { shareA.transfers.value.size == 2 && lastOut().finished })
        assertEquals(3, lastOut().skipped)
        assertEquals(0L, lastOut().bytes)
        println("SHARE A otra vez: ${lastOut().message}")
        assertEquals(3, inboxB.walkTopDown().count { it.isFile })
    }

    @Test fun rejectedOfferSendsNothing() = runBlocking {
        scope.launch { shareB.incoming.filterNotNull().collect { shareB.answer(it, false) } }
        shareA.send(a.nearby.value.first { it.name == "Cel-B" }, apiA.allSongs(0))
        assertTrue(waitFor(10_000) { lastOut().finished })
        assertEquals(Transfer.State.FAILED, lastOut().state)
        assertEquals("Cel-B no aceptó", lastOut().message)
        assertFalse(inboxB.exists() && inboxB.walkTopDown().any { it.isFile })
    }

    @Test fun pairedDevicePlaysMusicFromHere() = runBlocking {
        scope.launch { b.pairRequest.filterNotNull().collect { b.answerPair(it, true) } }
        assertTrue(a.pair(a.nearby.value.first { it.name == "Cel-B" }))
        assertTrue("A no ve a B como emparejado", waitFor(5000) { a.devices.value.any { it.name == "Cel-B" && it.paired } })
        assertTrue("B no ve a A como emparejado", waitFor(5000) { b.devices.value.any { it.name == "PC-A" && it.paired } })

        // A pasa su música a B: a B le llega por el puente de A (B no tiene esa biblioteca).
        val songs = apiA.allSongs(0)
        playerA.playFrom(songs, 1, 0)
        assertTrue(a.transferTo(a.devices.value.first { it.name == "Cel-B" }))
        val q = playerB.state.value.queue
        assertEquals(3, q.size)
        assertTrue(q.all { it.isPeer && it.externalUrl != null && it.albumId == null })
        val bytes = http.newCall(Request.Builder().url(q[1].externalUrl!!).build()).execute().use { it.body!!.bytes() }
        val original = lib.track(songs[1].id)!!.key.let(::File).readBytes()
        assertTrue("el audio que suena en B no es el de A", bytes.contentEquals(original))

        // Traerla de vuelta: en A vuelven a ser canciones normales de su biblioteca.
        assertTrue(a.pullFrom(a.devices.value.first { it.name == "Cel-B" }))
        assertEquals(songs.map { it.id }, playerA.state.value.queue.map { it.id })
        assertTrue(playerA.state.value.queue.all { it.externalUrl == null && it.coverArt == songs.first().coverArt })

        // Ajustes y API keys no viajan a un emparejado.
        assertNull(b.ask(b.devices.value.first { it.name == "PC-A" }, "manifest"))

        // Olvidar: se olvida en los dos.
        a.unpair(a.devices.value.first { it.name == "Cel-B" })
        assertTrue(waitFor(5000) { b.devices.value.none { it.name == "PC-A" } && b.nearby.value.any { it.name == "PC-A" } })
        assertTrue(a.nearby.value.any { it.name == "Cel-B" })
    }

    @Test fun receivedNamesStayInsideTheFolder() {
        val (dirs, name) = ShareService.place(ShareFile(0, "../../evil\\x:y", artist = "..", album = "a/b", track = 3, disc = 2, suffix = "fl/ac"))
        assertEquals(listOf("Desconocido", "a b"), dirs)
        assertEquals("2-03 - .. .. evil x y.flac", name)
        assertTrue((dirs + name).none { it.contains('/') || it.contains('\\') || it == ".." })
    }
}
