package com.marco.audiopro.desktop

import com.marco.audiopro.data.ServerAccount
import com.marco.audiopro.data.info.LyricsRepository
import com.marco.audiopro.data.meta.GenreMap
import com.marco.audiopro.data.meta.MetadataFixer
import com.marco.audiopro.data.meta.MusicBrainz
import com.marco.audiopro.data.radio.RadioBrowser
import com.marco.audiopro.data.radio.RadioNiches
import com.marco.audiopro.data.subsonic.SubsonicApi
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Con internet de verdad (MARCO_LIVE=1): géneros de MusicBrainz, radio y el arreglador de metadatos
 * sobre una COPIA de MARCO_FIX_DIR (los archivos originales nunca se tocan).
 */
class MetaLiveTest {
    private val http = OkHttpClient()

    @Test fun genresFromMusicBrainz(): Unit = runBlocking {
        assumeTrue(System.getenv("MARCO_LIVE") != null)
        val mb = MusicBrainz(http)
        val g = mb.searchGroup("My Bloody Valentine", "Loveless")
        val genres = g?.tags.orEmpty().mapNotNull { (t, c) -> GenreMap.canonical(t)?.let { it to c } }.sortedByDescending { it.second }
        println("META Loveless → ${genres.take(5)}")
        assertTrue("shoegaze", genres.take(3).any { it.first == "Shoegaze" })
        assertEquals("Indie y alternativo", GenreMap.family("Shoegaze"))
        assertEquals(listOf("Shoegaze", "Dream Pop"), GenreMap.fromTag("shoegazing; dream pop"))
    }

    @Test fun radioNichesPlay(): Unit = runBlocking {
        assumeTrue(System.getenv("MARCO_LIVE") != null)
        val radio = RadioBrowser(http)
        val st = radio.niche(RadioNiches.first { it.title == "Groove ruso" })
        println("META radio groove ruso: ${st.size} · ${st.take(3).map { "${it.name} (${it.codec} ${it.bitrate})" }}")
        assertTrue(st.size >= 5)
        // Alguna de las primeras se tiene que poder decodificar con el reproductor de la PC.
        val ok = st.take(6).firstNotNullOfOrNull { s ->
            runCatching {
                val g = org.bytedeco.javacv.FFmpegFrameGrabber(s.stream).apply { setOption("rw_timeout", "8000000") }
                g.start(); repeat(30) { g.grabSamples() }; val info = "${s.name}: ${g.format} ${g.sampleRate} Hz"; g.stop(); g.release(); info
            }.getOrNull()
        }
        println("META radio suena: $ok")
        assertTrue(ok != null)
    }

    @Test fun fixesACopyAndUndoes(): Unit = runBlocking {
        assumeTrue(System.getenv("MARCO_LIVE") != null)
        val src = System.getenv("MARCO_FIX_DIR")?.let(::File)?.takeIf { it.isDirectory }
        assumeTrue(src != null)
        val work = Files.createTempDirectory("fix").toFile()
        val copy = File(work, src!!.name).also { src.copyRecursively(it) }
        try {
            // Desordenamos tantito la copia, como una descarga con etiquetas feas.
            val first = copy.walkTopDown().first { it.extension.lowercase() == "flac" }
            val af = AudioFileIO.read(first); val goodTitle = af.tag.getFirst(FieldKey.TITLE)
            af.tag.setField(FieldKey.TITLE, "01 - track"); af.tag.deleteField(FieldKey.GENRE); af.commit()

            val io = JaudioTagIO()
            val api = SubsonicApi(http) { ServerAccount("http://127.0.0.1:9", "x", "x", "x") }
            val fixer = MetadataFixer(MusicBrainz(http), io, LyricsRepository(http, api, work), work)
            val albums = fixer.scanFolders(listOf(copy.path))
            println("META discos: ${albums.map { "${it.artist} — ${it.album} (${it.tracks.size})" }}")
            assertTrue(albums.isNotEmpty())
            val p = fixer.propose(albums.first())!!
            println("META propuesta: ${p.release.title} ${p.release.year} conf=${p.confidence} géneros=${p.genres} cambios=${p.renamedTracks}")
            assertTrue("confianza", p.confidence > 0.7f)
            val summary = fixer.apply(p)
            println("META aplicado: $summary")
            val fixed = AudioFileIO.read(first).tag
            assertEquals(goodTitle, fixed.getFirst(FieldKey.TITLE))
            assertTrue("portada", fixed.firstArtwork != null)
            println("META géneros escritos: ${fixed.getAll(FieldKey.GENRE)} · año ${fixed.getFirst(FieldKey.YEAR)} · lrc: ${copy.walkTopDown().count { it.extension == "lrc" }}")
            // Deshacer: regresa el título feo y sin géneros.
            fixer.undoLast()
            val back = AudioFileIO.read(first).tag
            assertEquals("01 - track", back.getFirst(FieldKey.TITLE))
            assertTrue(back.getAll(FieldKey.GENRE).isEmpty())
        } finally { work.deleteRecursively() }
    }

    @Test fun enhancedLrcWordTimes() {
        val l = LyricsRepository.parseLrc("[00:12.00]<00:12.00>Hola <00:12.40>mundo <00:13.10>cruel")
        assertEquals("Hola mundo cruel", l.single().value)
        assertEquals(listOf(12000L, 12400L, 13100L), l.single().words)
    }
}
