package com.marco.audiopro.data

import com.marco.audiopro.data.discover.DzArtist
import com.marco.audiopro.data.discover.DzList
import com.marco.audiopro.data.discover.normName
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.data.subsonic.SubsonicJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** De dónde sale la foto de un artista. */
sealed interface ArtistPhoto {
    /** La de Navidrome es una foto de verdad. */
    data object Server : ArtistPhoto
    /** Navidrome sólo tiene su silueta genérica: foto del catálogo de Deezer. */
    data class Web(val url: String) : ArtistPhoto
    /** Nadie tiene foto: se dibuja una portada generada con sus iniciales. */
    data object Generated : ArtistPhoto
}

/**
 * Fotos de artista sin huecos. Navidrome, cuando no tiene foto, manda una silueta gris (un webp
 * chiquito de ~500 bytes); en ese caso se busca al artista en Deezer y, si tampoco está, se
 * genera una portada. El resultado se guarda en disco y se revisa otra vez cada dos semanas.
 */
class ArtistPhotos(private val http: OkHttpClient, private val api: SubsonicApi, dir: File) {
    @Serializable
    private data class Entry(val kind: String, val url: String? = null, val at: Long = 0)

    private val file = File(dir, "artist_photos.json")
    private val _known = MutableStateFlow<Map<String, ArtistPhoto>>(emptyMap())
    /** Lo ya resuelto, por id de artista (la UI lo observa). */
    val known: StateFlow<Map<String, ArtistPhoto>> = _known

    private val entries: MutableMap<String, Entry> = runCatching {
        SubsonicJson.decodeFromString<Map<String, Entry>>(file.readText()).toMutableMap()
    }.getOrDefault(mutableMapOf())
    private val lock = Mutex()
    private val inFlight = HashSet<String>()
    private val parallel = Semaphore(4)
    private val dzGate = Mutex()
    private var dzLast = 0L

    init { _known.value = entries.mapValues { it.value.toPhoto() } }

    private fun Entry.toPhoto(): ArtistPhoto = when (kind) {
        "server" -> ArtistPhoto.Server
        "web" -> url?.let { ArtistPhoto.Web(it) } ?: ArtistPhoto.Generated
        else -> ArtistPhoto.Generated
    }

    /** Resuelve (una sola vez a la vez por artista) y publica en [known]. */
    suspend fun resolve(id: String, name: String, coverArt: String?) {
        val fresh = lock.withLock {
            val e = entries[id]
            if (e != null && (e.kind == "server" || System.currentTimeMillis() - e.at < 14L * 24 * 3600 * 1000)) return
            if (!inFlight.add(id)) return
            e
        }
        try {
            val entry = parallel.withPermit { withContext(Dispatchers.IO) { lookup(name, coverArt ?: "ar-$id") } }
                ?: fresh ?: return
            lock.withLock {
                entries[id] = entry
                runCatching { file.writeText(SubsonicJson.encodeToString(entries.toMap())) }
            }
            _known.value = _known.value + (id to entry.toPhoto())
        } finally {
            lock.withLock { inFlight.remove(id) }
        }
    }

    /** null = no se pudo saber (sin red): se intenta en la próxima. */
    private suspend fun lookup(name: String, coverArt: String): Entry? {
        val now = System.currentTimeMillis()
        val placeholder = runCatching { isPlaceholder(coverArt) }.getOrElse { return null }
        if (!placeholder) return Entry("server", at = now)
        val web = runCatching { deezerPhoto(name) }.getOrElse { return null }
        return if (web != null) Entry("web", web, now) else Entry("generated", at = now)
    }

    private fun isPlaceholder(coverArt: String): Boolean {
        val url = api.coverUrl(coverArt, 64) ?: return true
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) return r.code == 404
            val bytes = r.body?.bytes() ?: return true
            val type = r.header("Content-Type").orEmpty()
            return bytes.isEmpty() || (type.contains("webp") && bytes.size < 2000)
        }
    }

    /** Para comparar nombres; los que no son latinos (japonés, chino, cirílico…) se comparan tal cual. */
    private fun key(s: String) = normName(s).ifBlank { s.lowercase().trim() }

    private suspend fun deezerPhoto(name: String): String? {
        val want = key(name)
        // Las recopilaciones no tienen cara: mejor la portada generada que una foto al azar.
        if (want.isBlank() || want in COMPILATIONS) return null
        dzGate.withLock {
            val wait = 150 - (System.currentTimeMillis() - dzLast)
            if (wait > 0) delay(wait)
            dzLast = System.currentTimeMillis()
        }
        val url = "https://api.deezer.com/search/artist".toHttpUrl().newBuilder()
            .addQueryParameter("q", name).addQueryParameter("limit", "8").build()
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string().orEmpty() }
        val found = SubsonicJson.decodeFromString<DzList<DzArtist>>(body).data
            .filter { key(it.name) == want }
            .maxByOrNull { it.nb_fan ?: 0 } ?: return null
        // Sin foto en Deezer la URL trae el hash vacío (".../images/artist//...").
        return found.picture_xl?.takeIf { it.isNotBlank() && "/artist//" !in it }
    }

    private companion object {
        val COMPILATIONS = setOf("various", "various artists", "va", "varios", "varios artistas", "unknown artist", "unknown", "desconocido")
    }
}
