package com.marco.audiopro.desktop

import com.marco.audiopro.data.ServerAccount
import com.marco.audiopro.data.local.LocalLibrary
import com.marco.audiopro.data.StreamQuality
import com.marco.audiopro.data.subsonic.SubsonicApi
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Biblioteca sin servidor con archivos reales: MARCO_LOCAL_DIRS="carpeta1;carpeta2" (sólo lectura).
 * Escanea, levanta el "Navidrome de bolsillo" y lo usa con el mismo cliente de la app.
 */
class LocalLibraryTest {
    @Test fun scanServeAndPlay(): Unit = runBlocking {
        val dirs = System.getenv("MARCO_LOCAL_DIRS")?.split(";")?.map(::File)?.filter { it.isDirectory }.orEmpty()
        assumeTrue(dirs.isNotEmpty())
        val data = Files.createTempDirectory("local").toFile()
        val lib = LocalLibrary(data, FolderSource { dirs })
        val t0 = System.currentTimeMillis()
        val n = lib.rescan()
        println("LOCAL escaneo: $n canciones en ${System.currentTimeMillis() - t0} ms")
        assertTrue(n > 0)
        val port = lib.server.start(0)
        val http = OkHttpClient()
        val api = SubsonicApi(http) { ServerAccount("http://127.0.0.1:$port", "local", "local", "local") }

        val artists = api.artists()
        println("LOCAL artistas: ${artists.map { it.name }}")
        assertTrue(artists.isNotEmpty())
        val artist = api.artist(artists.first().id)
        val album = api.album(artist.album.first().id)
        println("LOCAL álbum: ${album.name} (${album.year}) · ${album.song.size} canciones · ${album.song.first().formatLabel}")
        assertTrue(album.song.isNotEmpty())
        val song = album.song.first()
        println("LOCAL canción: ${song.track}. ${song.title} — ${song.artist} · ${song.duration}s · ${song.bitDepth}/${song.samplingRate}")

        val found = api.search(song.title.take(5))
        assertTrue("búsqueda", found.song.any { it.id == song.id })
        assertTrue("albumList", api.albumList("newest", 10).isNotEmpty())

        // Audio: completo y un pedazo (Range), como al adelantar.
        val url = api.streamUrl(song.id, StreamQuality.ORIGINAL)
        http.newCall(Request.Builder().url(url).header("Range", "bytes=1000-1999").build()).execute().use { r ->
            assertEquals(206, r.code)
            assertEquals(1000, r.body!!.bytes().size)
        }
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            assertEquals(200, r.code)
            assertEquals(song.size, r.body!!.contentLength())
        }
        // Portada.
        val cover = http.newCall(Request.Builder().url(api.coverUrl(song.coverArt)!!).build()).execute().use { it.code to (it.body?.bytes()?.size ?: 0) }
        println("LOCAL portada: HTTP ${cover.first}, ${cover.second} bytes")

        // Me gusta, playlist y escucha se guardan.
        api.star(song.id, true)
        assertTrue(api.starred().song.any { it.id == song.id })
        val pl = api.createPlaylist("Prueba local", listOf(song.id))
        assertEquals(1, api.playlist(pl.id).entry.size)
        api.scrobbleAt(song.id, System.currentTimeMillis())
        assertEquals(1L, api.album(album.id).song.first { it.id == song.id }.playCount)

        // El reproductor de la PC (ffmpeg) puede abrir el stream.
        val g = org.bytedeco.javacv.FFmpegFrameGrabber(url)
        g.start(); println("LOCAL ffmpeg: ${g.format} ${g.sampleRate} Hz ${g.audioChannels} canales"); g.stop(); g.release()

        lib.server.stop(); data.deleteRecursively()
    }
}
