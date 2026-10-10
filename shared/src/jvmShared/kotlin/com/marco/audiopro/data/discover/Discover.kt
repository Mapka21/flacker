package com.marco.audiopro.data.discover

import com.marco.audiopro.data.subsonic.Artist
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.data.subsonic.SubsonicJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.Normalizer

// ------------------------------------------------------------------ catálogo (API pública de Deezer)

@Serializable
data class DzArtist(
    val id: Long = 0,
    val name: String = "",
    val picture_medium: String? = null,
    val picture_xl: String? = null,
    val nb_fan: Int? = null,
    val nb_album: Int? = null,
)

@Serializable
data class DzGenre(val name: String = "")

@Serializable
data class DzList<T>(val data: List<T> = emptyList())

@Serializable
data class DzTrack(
    val id: Long = 0,
    val title: String = "",
    val duration: Int = 0,
    val track_position: Int? = null,
    val disk_number: Int? = null,
    val rank: Long? = null,
    val preview: String? = null,
    val artist: DzArtist? = null,
    val album: DzAlbum? = null,
)

/** Canción reproducible (muestra de 30 s) para el reproductor normal. */
fun DzTrack.toPreviewSong(fallbackAlbum: DzAlbum? = null, fallbackArtist: DzArtist? = null): Song {
    val al = album ?: fallbackAlbum
    return Song(
        id = "dz:$id", title = title, album = al?.title, artist = (artist ?: fallbackArtist ?: al?.artist)?.name,
        coverArt = al?.cover_xl ?: al?.cover_medium, duration = 30, suffix = "mp3", bitRate = 128, externalUrl = preview,
    )
}

@Serializable
data class DzAlbum(
    val id: Long = 0,
    val title: String = "",
    val cover_medium: String? = null,
    val cover_xl: String? = null,
    val release_date: String? = null,
    val record_type: String? = null,
    val label: String? = null,
    val nb_tracks: Int? = null,
    val duration: Int? = null,
    val fans: Int? = null,
    val artist: DzArtist? = null,
    val genres: DzList<DzGenre>? = null,
    val tracks: DzList<DzTrack>? = null,
) {
    val year: String? get() = release_date?.take(4)
}

/** Cliente mínimo de api.deezer.com: sin llave, sólo lectura (catálogo, portadas, parecidos). */
private class DeezerClient(private val http: OkHttpClient) {
    private val gate = Mutex()
    private var last = 0L

    private suspend fun raw(path: String, params: Map<String, Any> = emptyMap()): String = withContext(Dispatchers.IO) {
        val b = "https://api.deezer.com/$path".toHttpUrl().newBuilder()
        params.forEach { (k, v) -> b.addQueryParameter(k, v.toString()) }
        val url = b.build()
        repeat(3) { attempt ->
            // Deezer permite ~50 llamadas cada 5 s: se espacian un poco.
            gate.withLock {
                val wait = 110 - (System.currentTimeMillis() - last)
                if (wait > 0) delay(wait)
                last = System.currentTimeMillis()
            }
            val body = http.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() ?: "" }
            val err = runCatching { SubsonicJson.parseToJsonElement(body).jsonObject["error"] }.getOrNull()
            if (err == null) return@withContext body
            delay(1200L * (attempt + 1))
        }
        throw IllegalStateException("Deezer no respondió")
    }

    private suspend inline fun <reified T> get(path: String, params: Map<String, Any> = emptyMap()): T =
        SubsonicJson.decodeFromString(raw(path, params))

    suspend fun searchArtists(q: String, limit: Int = 10) = get<DzList<DzArtist>>("search/artist", mapOf("q" to q, "limit" to limit)).data
    suspend fun searchAlbums(q: String, limit: Int = 24) = get<DzList<DzAlbum>>("search/album", mapOf("q" to q, "limit" to limit)).data
    suspend fun artist(id: Long) = get<DzArtist>("artist/$id")
    suspend fun related(id: Long, limit: Int = 25) = get<DzList<DzArtist>>("artist/$id/related", mapOf("limit" to limit)).data
    suspend fun top(id: Long, limit: Int = 10) = get<DzList<DzTrack>>("artist/$id/top", mapOf("limit" to limit)).data
    suspend fun albums(id: Long, limit: Int = 100) = get<DzList<DzAlbum>>("artist/$id/albums", mapOf("limit" to limit)).data
    suspend fun album(id: Long) = get<DzAlbum>("album/$id")
    suspend fun track(id: Long) = get<DzTrack>("track/$id")
}

// ------------------------------------------------------------------ recomendaciones

@Serializable
data class RecArtist(val artist: DzArtist, val because: String)

@Serializable
data class MissingGroup(val artist: String, val libraryArtistId: String, val albums: List<DzAlbum>)

@Serializable
data class Discoveries(
    val createdAt: Long = 0,
    val artists: List<RecArtist> = emptyList(),
    val albums: List<DzAlbum> = emptyList(),
    val tracks: List<DzTrack> = emptyList(),
    val missing: List<MissingGroup> = emptyList(),
)

/** Para comparar nombres entre tu biblioteca y el catálogo: sin acentos, ediciones ni signos. */
fun normName(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
        .replace(Regex("\\b(deluxe|remaster(ed)?|expanded|edition|version)\\b.*"), " ")
        .replace(Regex("^the "), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

/**
 * Descubrir música que NO tienes: artistas parecidos a los que más escuchas (que no están en
 * tu biblioteca), sus discos y canciones más populares, y los álbumes que te faltan de tus
 * artistas, con muestras de 30 s para conocerlas.
 */
class DiscoverRepository(http: OkHttpClient, private val api: SubsonicApi, cacheDir: File) {
    private val dz = DeezerClient(http)
    private val cacheFile = File(cacheDir, "discover.json")
    private var library: Map<String, Artist>? = null
    private val buildLock = Mutex()

    private suspend fun libraryArtists(): Map<String, Artist> =
        library ?: api.artists().associateBy { normName(it.name) }.also { library = it }

    /** El artista de tu biblioteca con ese nombre, si lo tienes. */
    suspend fun libraryArtist(name: String): Artist? = runCatching { libraryArtists()[normName(name)] }.getOrNull()

    /** Nombres (normalizados) de los álbumes que ya tienes de un artista. */
    suspend fun ownedAlbums(artistName: String): Set<String> {
        val a = libraryArtist(artistName) ?: return emptySet()
        return runCatching { api.artist(a.id).album.mapTo(HashSet()) { normName(it.name) } }.getOrDefault(emptySet())
    }

    /** Id del álbum en tu biblioteca si ya lo tienes. */
    suspend fun libraryAlbumId(artistName: String, title: String): String? = runCatching {
        val t = normName(title); val ar = normName(artistName)
        api.search(title).album.firstOrNull { normName(it.name) == t && normName(it.artist ?: "") == ar }?.id
    }.getOrNull()

    suspend fun searchArtists(q: String) = dz.searchArtists(q)
    suspend fun searchAlbums(q: String) = dz.searchAlbums(q)
    suspend fun artist(id: Long) = dz.artist(id)
    suspend fun top(id: Long) = dz.top(id, 10)
    suspend fun related(id: Long) = dz.related(id, 20)
    suspend fun album(id: Long) = dz.album(id)

    /**
     * Muestras listas para sonar. Deezer firma las URLs por ~15 min, así que se piden de nuevo
     * justo antes de reproducir (las guardadas en las recomendaciones ya caducaron).
     */
    suspend fun previewSongs(tracks: List<DzTrack>): List<Song> = tracks.mapNotNull { t ->
        val fresh = runCatching { dz.track(t.id) }.getOrNull() ?: return@mapNotNull null
        fresh.preview?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        fresh.toPreviewSong(fallbackAlbum = t.album, fallbackArtist = t.artist)
    }

    /** Todas las muestras de un álbum (una sola llamada, URLs recién firmadas). */
    suspend fun albumPreviews(id: Long): List<Song> {
        val a = dz.album(id)
        return a.tracks?.data.orEmpty().filter { !it.preview.isNullOrBlank() }.map { it.toPreviewSong(a, a.artist) }
    }

    /** Populares del artista como muestras (una llamada). */
    suspend fun topPreviews(id: Long): List<Song> =
        dz.top(id, 10).filter { !it.preview.isNullOrBlank() }.map { it.toPreviewSong() }

    /** Discografía sin duplicados (Deezer repite ediciones), lo más nuevo primero. */
    suspend fun discography(id: Long): List<DzAlbum> =
        dz.albums(id).distinctBy { normName(it.title) to it.record_type }.sortedByDescending { it.release_date ?: "" }

    fun cached(): Discoveries? = runCatching { SubsonicJson.decodeFromString<Discoveries>(cacheFile.readText()) }.getOrNull()

    /** Recomendaciones; se recalculan cada 12 h (o al pedirlo). */
    suspend fun recommendations(force: Boolean = false): Discoveries = buildLock.withLock {
        val c = cached()
        if (!force && c != null && System.currentTimeMillis() - c.createdAt < 12 * 3600_000L && c.artists.isNotEmpty()) return c
        build().also { d -> withContext(Dispatchers.IO) { runCatching { cacheFile.writeText(SubsonicJson.encodeToString(d)) } } }
    }

    private suspend fun build(): Discoveries {
        val lib = libraryArtists()
        // Semillas: lo que más escuchas + tus artistas favoritos.
        val frequent = runCatching { api.albumList("frequent", 80) }.getOrDefault(emptyList())
        val bySeed = frequent.filter { it.artist != null }.groupBy { normName(it.artist!!) }
            .entries.sortedByDescending { (_, al) -> al.sumOf { it.playCount ?: 0 } }.map { it.value.first().artist!! }
        val starred = runCatching { api.starred().artist.map { it.name } }.getOrDefault(emptyList())
        val seeds = (bySeed + starred).distinctBy(::normName).take(8)
            .ifEmpty { runCatching { api.albumList("random", 10) }.getOrDefault(emptyList()).mapNotNull { it.artist }.distinct().take(5) }

        data class Seed(val name: String, val dz: DzArtist)
        val dzSeeds = seeds.mapNotNull { name ->
            runCatching { dz.searchArtists(name, 3).firstOrNull { normName(it.name) == normName(name) } }.getOrNull()?.let { Seed(name, it) }
        }

        // Artistas parecidos que no tienes, puntuados por cuántas semillas los recomiendan.
        val score = LinkedHashMap<Long, Pair<RecArtist, Int>>()
        for (s in dzSeeds) {
            val rel = runCatching { dz.related(s.dz.id, 25) }.getOrDefault(emptyList())
            for (a in rel) {
                if (normName(a.name) in lib) continue
                val prev = score[a.id]
                score[a.id] = (prev?.first ?: RecArtist(a, s.name)) to ((prev?.second ?: 0) + 1)
            }
        }
        val recArtists = score.values.sortedWith(compareByDescending<Pair<RecArtist, Int>> { it.second }.thenByDescending { it.first.artist.nb_fan ?: 0 })
            .map { it.first }.take(24)

        // Sus canciones más populares y el disco de cada una.
        val tracks = mutableListOf<DzTrack>()
        val albums = mutableListOf<DzAlbum>()
        for (r in recArtists.take(10)) {
            val top = runCatching { dz.top(r.artist.id, 5) }.getOrDefault(emptyList()).map { it.copy(artist = it.artist ?: r.artist) }
            tracks += top.take(2)
            top.groupBy { it.album?.id }.maxByOrNull { it.value.size }?.value?.first()?.album
                ?.let { albums += it.copy(artist = r.artist) }
        }

        // Discos que te faltan de tus artistas.
        val missing = mutableListOf<MissingGroup>()
        for (s in dzSeeds.take(4)) {
            val libArtist = lib[normName(s.name)] ?: continue
            val owned = runCatching { api.artist(libArtist.id).album.mapTo(HashSet()) { normName(it.name) } }.getOrDefault(emptySet())
            val theirs = runCatching { dz.albums(s.dz.id, 100) }.getOrDefault(emptyList())
                .filter { it.record_type == "album" }
                .distinctBy { normName(it.title) }
                .filter { normName(it.title) !in owned && normName(it.title).isNotBlank() }
                .sortedByDescending { it.release_date ?: "" }
                .map { it.copy(artist = s.dz) }
                .take(12)
            if (theirs.isNotEmpty()) missing += MissingGroup(libArtist.name, libArtist.id, theirs)
        }
        return Discoveries(System.currentTimeMillis(), recArtists, albums.distinctBy { it.id }, tracks.distinctBy { it.id }, missing)
    }
}
