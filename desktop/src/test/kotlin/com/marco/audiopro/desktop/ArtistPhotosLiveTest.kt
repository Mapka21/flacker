package com.marco.audiopro.desktop

import com.marco.audiopro.data.ArtistPhoto
import com.marco.audiopro.data.ArtistPhotos
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Contra tu Navidrome real (sólo lectura), con MARCO_LIVE=1: cuántos artistas sacan foto de dónde. */
class ArtistPhotosLiveTest {
    @Test fun resolveLibrary() = runBlocking {
        assumeTrue(System.getenv("MARCO_LIVE") == "1")
        val graph = DesktopGraph()
        val tmp = Files.createTempDirectory("photos").toFile()
        val photos = ArtistPhotos(graph.http, graph.api, tmp)
        val artists = graph.api.artists()
        artists.map { a -> async { photos.resolve(a.id, a.name, a.coverArt) } }.awaitAll()
        val known = photos.known.value
        val byKind = artists.groupBy { known[it.id]?.let { p -> p::class.simpleName } ?: "?" }
        byKind.forEach { (k, v) -> println("$k: ${v.size}") }
        artists.filter { known[it.id] !is ArtistPhoto.Server }.forEach { println("  ${it.name} -> ${known[it.id]}") }
        tmp.deleteRecursively()
        graph.shutdown()
    }
}
