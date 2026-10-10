package com.marco.audiopro.data

import com.marco.audiopro.data.info.LastFm
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.InetAddress

/** Reseñas de respaldo desde Wikipedia (necesita internet; si no hay, se salta). */
class WikipediaReviewTest {
    private val online = runCatching { InetAddress.getByName("es.wikipedia.org"); true }.getOrDefault(false)
    private val lf = LastFm(OkHttpClient())

    @Test fun findsAlbumInSpanish() = runBlocking {
        assumeTrue(online)
        val r = lf.wikipedia("Asking Alexandria", "The Black")
        assertTrue("sin reseña", r != null)
        assertTrue(r!!.first.contains("Asking Alexandria"))
        assertTrue(r.second.startsWith("https://es.wikipedia.org/wiki/"))
    }

    @Test fun ignoresWrongArtist() = runBlocking {
        assumeTrue(online)
        // Un disco que se llama igual que otro famoso, pero de un artista inventado.
        assertNull(lf.wikipedia("Banda Que No Existe Flacker", "Thriller"))
    }
}
