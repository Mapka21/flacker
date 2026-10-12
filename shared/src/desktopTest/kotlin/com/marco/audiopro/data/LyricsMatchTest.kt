package com.marco.audiopro.data

import com.marco.audiopro.data.info.LyricsRepository
import com.marco.audiopro.data.subsonic.Lyric
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.StructuredLyrics
import com.marco.audiopro.data.subsonic.SubsonicApi
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.InetAddress
import java.nio.file.Files

/** Que la letra sea de la misma versión que tu archivo (necesita internet; si no, se salta). */
class LyricsMatchTest {
    private val online = runCatching { InetAddress.getByName("lrclib.net"); true }.getOrDefault(false)
    private fun repo() = LyricsRepository(OkHttpClient(), SubsonicApi(OkHttpClient()) { null }, Files.createTempDirectory("ly").toFile())

    @Test fun picksTheVersionWithTheSameDuration() = runBlocking {
        assumeTrue(online)
        val r = repo()
        val radio = r.get(Song("a", "Get Lucky", artist = "Daft Punk", duration = 248))
        val album = r.get(Song("b", "Get Lucky", artist = "Daft Punk", album = "Random Access Memories", duration = 369))
        assertTrue(album?.synced == true)
        // La versión corta nunca debe traer tiempos que se pasen de su duración: o trae los
        // suyos, o queda como texto sin sincronizar (LRCLIB tiene entradas mal subidas).
        val r2 = radio!!
        if (r2.synced) assertTrue("tiempos de otra versión", r2.line.last { it.value.isNotBlank() }.start!! <= 253_000)
        assertNotEquals(r2.synced to r2.line.lastOrNull()?.start, album!!.synced to album.line.lastOrNull()?.start)
    }

    @Test fun picksBestOfManyUploads() = runBlocking {
        assumeTrue(online)
        // Korn tiene muchas subidas de esta rola; debe salir una sincronizada que quepa en 4:15.
        val l = repo().get(Song("k", "Freak On A Leash", artist = "Korn", album = "Follow the Leader", duration = 255))
        assertTrue("sin letra sincronizada", l?.synced == true)
        val last = l!!.line.last { it.value.isNotBlank() }.start!!
        assertTrue("tiempos fuera de la canción: $last", last in 150_000..260_000)
    }

    @Test fun noLyricsBeatsWrongLyrics() = runBlocking {
        assumeTrue(online)
        // Duración que no existe para esa canción: mejor sin letra que la de otra versión.
        assertNull(repo().get(Song("c", "Get Lucky", artist = "Daft Punk", duration = 95)))
    }

    @Test fun cleansJunkLines() {
        val l = StructuredLyrics(true, listOf(Lyric(0, "Like the legend of the phoenix"), Lyric(1, "All ends with beginnings"), Lyric(2, "Lyrics provided by www.ejemplo.com")))
        assertEquals(2, LyricsRepository.clean(l)!!.line.size)
    }
}
