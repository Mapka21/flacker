package com.marco.audiopro.data.radio

import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.SubsonicJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Una estación de radio por internet (de radio-browser.info, el directorio abierto). */
@Serializable
data class Station(
    val stationuuid: String,
    val name: String = "",
    val url_resolved: String = "",
    val url: String = "",
    val favicon: String = "",
    val tags: String = "",
    val country: String = "",
    val countrycode: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val votes: Int = 0,
    val clickcount: Int = 0,
    val hls: Int = 0,
) {
    val stream: String get() = url_resolved.ifBlank { url }
    val tagList: List<String> get() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** Como canción, para que la toque el reproductor de siempre (sin duración: en vivo). */
    fun toSong() = Song(
        id = "radio:$stationuuid", title = name.trim(),
        artist = listOf(country.ifBlank { null }, tagList.take(2).joinToString(" · ").ifBlank { null }).filterNotNull().joinToString(" · ").ifBlank { "Radio" },
        album = "Radio en vivo", coverArt = favicon.takeIf { it.startsWith("http") },
        suffix = codec.lowercase().ifBlank { null }, bitRate = bitrate.takeIf { it > 0 }, externalUrl = stream,
    )
}

/** Un nicho armado a mano: una o varias búsquedas (etiqueta, país) que se juntan. */
data class RadioNiche(val title: String, val blurb: String, val queries: List<Pair<String, String?>>)

/** Nichos curados (comprobados contra el directorio: todos traen estaciones). */
val RadioNiches = listOf(
    RadioNiche("Groove ruso", "Disco, funk y groove de Rusia y la era soviética", listOf("disco" to "RU", "funk" to "RU", "soviet" to null)),
    RadioNiche("Techno francés", "Techno desde Francia", listOf("techno" to "FR")),
    RadioNiche("French touch", "French house y nu disco", listOf("french house" to null, "nu disco" to null)),
    RadioNiche("Minimal y dub techno", "Techno hipnótico y profundo", listOf("minimal techno" to null, "dub techno" to null)),
    RadioNiche("Italo disco", "Sintetizadores y bola de espejos de los 80", listOf("italo disco" to null)),
    RadioNiche("Post-punk y darkwave", "Guitarras frías, bajo al frente", listOf("post-punk" to null, "darkwave" to null)),
    RadioNiche("Shoegaze", "Muros de guitarra y voces de ensueño", listOf("shoegaze" to null)),
    RadioNiche("City pop", "Pop japonés de los 80", listOf("city pop" to null)),
    RadioNiche("Synthwave y vaporwave", "Retro futuro", listOf("synthwave" to null, "vaporwave" to null)),
    RadioNiche("Phonk", "Cencerro y bajo saturado", listOf("phonk" to null)),
    RadioNiche("Rare groove", "Funk y soul difíciles de encontrar", listOf("rare groove" to null)),
    RadioNiche("Deep house", "Para dejarla toda la noche", listOf("deep house" to null)),
    RadioNiche("Breakbeat", "Breaks, big beat y bass", listOf("breakbeat" to null)),
    RadioNiche("Jazz fusión", "Jazz eléctrico", listOf("jazz fusion" to null)),
    RadioNiche("Afrobeat", "De Lagos al mundo", listOf("afrobeat" to null)),
    RadioNiche("Cumbia", "Cumbia de todos lados", listOf("cumbia" to null)),
    RadioNiche("Lo-fi", "Para concentrarse", listOf("lofi" to null)),
)

/**
 * Directorio radio-browser.info (abierto, gratis). Sólo estaciones que funcionan y sin HLS
 * (el reproductor toca las normales: MP3, AAC, OGG…), ordenadas por votos.
 */
class RadioBrowser(http: OkHttpClient) {
    private val http = http.newBuilder().callTimeout(12, TimeUnit.SECONDS).build()
    private val servers = listOf("https://de1.api.radio-browser.info", "https://nl1.api.radio-browser.info", "https://at1.api.radio-browser.info")

    private suspend fun get(path: String, params: Map<String, String>): List<Station> = withContext(Dispatchers.IO) {
        for (base in servers) {
            val url = "$base$path".toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
            val r = runCatching {
                http.newCall(Request.Builder().url(url).header("User-Agent", "Flacker/2.11").build()).execute().use { res ->
                    if (!res.isSuccessful) null else SubsonicJson.decodeFromString<List<Station>>(res.body!!.string())
                }
            }.getOrNull()
            if (r != null) return@withContext r.filter { it.hls == 0 && it.stream.startsWith("http") }
        }
        emptyList()
    }

    suspend fun search(tag: String? = null, country: String? = null, name: String? = null, limit: Int = 40): List<Station> =
        get("/json/stations/search", buildMap {
            tag?.let { put("tag", it) }; country?.let { put("countrycode", it) }; name?.let { put("name", it) }
            put("hidebroken", "true"); put("order", "votes"); put("reverse", "true"); put("limit", limit.toString())
        })

    suspend fun niche(n: RadioNiche): List<Station> = coroutineScope {
        n.queries.map { (t, c) -> async { search(tag = t, country = c, limit = 40) } }.awaitAll()
            .flatten().distinctBy { it.stationuuid }.distinctBy { it.name.lowercase().trim() }.sortedByDescending { it.votes }
    }

    /** Buscar por nombre o por género (lo que escribas). */
    suspend fun find(q: String): List<Station> = coroutineScope {
        val byName = async { search(name = q, limit = 30) }
        val byTag = async { search(tag = q.lowercase(), limit = 30) }
        (byTag.await() + byName.await()).distinctBy { it.stationuuid }.sortedByDescending { it.votes }
    }

    /** Avisa al directorio que se escuchó (así mide qué estaciones sirven). */
    suspend fun click(uuid: String) = withContext(Dispatchers.IO) {
        runCatching { http.newCall(Request.Builder().url("${servers[0]}/json/url/$uuid").header("User-Agent", "Flacker/2.11").build()).execute().close() }
        Unit
    }
}
