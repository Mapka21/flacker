package com.marco.audiopro.data.info

import com.marco.audiopro.data.subsonic.SubsonicJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Calendar

/** Periodos de los "top" de Last.fm (los mismos de su web). */
enum class LfPeriod(val api: String, val label: String) {
    WEEK("7day", "7 días"), MONTH("1month", "1 mes"), QUARTER("3month", "3 meses"),
    HALF("6month", "6 meses"), YEAR("12month", "1 año"), ALL("overall", "Siempre"),
}

data class LfUser(
    val name: String,
    val realName: String?,
    val image: String?,
    val country: String?,
    val url: String?,
    val scrobbles: Long,
    val artists: Long,
    val albums: Long,
    val tracks: Long,
    /** Desde cuándo scrobblea (epoch s). */
    val registered: Long?,
)

data class LfItem(
    val name: String,
    val artist: String?,
    val plays: Long,
    val image: String?,
)

data class LfScrobble(
    val title: String,
    val artist: String,
    val album: String?,
    val image: String?,
    /** epoch s; null = suena ahora. */
    val time: Long?,
    val loved: Boolean,
)

data class LfStats(
    /** Scrobbles por día, los últimos 30 (el último es hoy). */
    val daily: IntArray,
    /** Por hora del día (0..23), en tu zona horaria. */
    val hourly: IntArray,
    /** Por día de la semana (0 = lunes). */
    val weekday: IntArray,
    val total30: Int,
    /** true si se cortó por tener muchísimos scrobbles (se cuenta hasta el límite). */
    val partial: Boolean,
)

data class LfTops(val artists: List<LfItem>, val albums: List<LfItem>, val tracks: List<LfItem>, val genres: List<Pair<String, Double>>)

data class LfDashboard(
    val user: LfUser,
    val recent: List<LfScrobble>,
    val loved: List<LfScrobble>,
    val lovedTotal: Long,
    val stats: LfStats,
)

/**
 * Tu perfil de Last.fm (como el panel de last.fm): datos públicos con tu usuario y una API
 * key gratuita. Sólo lectura.
 */
class LastFmProfile(private val http: OkHttpClient) {
    /** Se puede apuntar a un Last.fm falso para las pruebas (-Dflacker.lastfm.base=…). */
    private val base = System.getProperty("flacker.lastfm.base") ?: "https://ws.audioscrobbler.com/2.0/"

    private suspend fun call(key: String, method: String, params: Map<String, Any?>): JsonObject = withContext(Dispatchers.IO) {
        val b = base.toHttpUrl().newBuilder()
            .addQueryParameter("method", method).addQueryParameter("api_key", key).addQueryParameter("format", "json")
        params.forEach { (k, v) -> if (v != null) b.addQueryParameter(k, v.toString()) }
        http.newCall(Request.Builder().url(b.build()).header("User-Agent", "Flacker (app personal)").build()).execute().use { r ->
            val body = r.body?.string() ?: error("Last.fm no respondió")
            val root = SubsonicJson.parseToJsonElement(body).jsonObject
            root.str("message")?.let { msg ->
                error(
                    when (root["error"]?.let { (it as? JsonPrimitive)?.content }) {
                        "6" -> "No existe ese usuario de Last.fm"
                        "10" -> "La API key de Last.fm no es válida"
                        "29" -> "Last.fm pide esperar un poco (demasiadas consultas)"
                        else -> msg
                    },
                )
            }
            root
        }
    }

    suspend fun user(key: String, user: String): LfUser {
        val u = call(key, "user.getinfo", mapOf("user" to user))["user"] as JsonObject
        return LfUser(
            name = u.str("name") ?: user,
            realName = u.str("realname")?.takeIf { it.isNotBlank() },
            image = u.image(),
            country = u.str("country")?.takeIf { it.isNotBlank() && it != "None" },
            url = u.str("url"),
            scrobbles = u.num("playcount"),
            artists = u.num("artist_count"),
            albums = u.num("album_count"),
            tracks = u.num("track_count"),
            registered = (u["registered"] as? JsonObject)?.let { it.str("unixtime") ?: it.str("#text") }?.toLongOrNull(),
        )
    }

    suspend fun recent(key: String, user: String, limit: Int = 50): List<LfScrobble> =
        recentPage(key, user, limit, 1, from = null).first

    /** Una página de scrobbles recientes y el total de páginas. */
    private suspend fun recentPage(key: String, user: String, limit: Int, page: Int, from: Long?): Pair<List<LfScrobble>, Int> {
        val root = call(key, "user.getrecenttracks", mapOf("user" to user, "limit" to limit, "page" to page, "extended" to 1, "from" to from))
        val rt = root["recenttracks"] as? JsonObject ?: return emptyList<LfScrobble>() to 0
        val pages = (rt["@attr"] as? JsonObject)?.str("totalPages")?.toIntOrNull() ?: 1
        return rt.list("track").map { t ->
            val artist = (t["artist"] as? JsonObject)?.let { it.str("name") ?: it.str("#text") } ?: ""
            LfScrobble(
                title = t.str("name") ?: "",
                artist = artist,
                album = (t["album"] as? JsonObject)?.str("#text")?.takeIf { it.isNotBlank() },
                image = t.image(),
                time = (t["date"] as? JsonObject)?.str("uts")?.toLongOrNull(),
                loved = t.str("loved") == "1",
            )
        } to pages
    }

    /** Estadísticas de los últimos 30 días a partir de tus scrobbles (hasta ~4000). */
    suspend fun stats(key: String, user: String): LfStats {
        val now = System.currentTimeMillis() / 1000
        val from = now - 30L * 86400
        val first = recentPage(key, user, 200, 1, from)
        val maxPages = 20
        // De 4 en 4 páginas: Last.fm corta si se le pide mucho de golpe.
        val rest = (2..minOf(first.second, maxPages)).chunked(4).flatMap { chunk ->
            coroutineScope {
                chunk.map { p -> async { runCatching { recentPage(key, user, 200, p, from).first }.getOrDefault(emptyList()) } }.awaitAll()
            }
        }
        val all = (first.first + rest.flatten()).mapNotNull { it.time }
        val daily = IntArray(30); val hourly = IntArray(24); val weekday = IntArray(7)
        val cal = Calendar.getInstance()
        val todayStart = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis / 1000
        for (t in all) {
            val dayIdx = 29 - ((todayStart - t + 86399) / 86400).toInt().coerceAtLeast(0)
            if (dayIdx in 0..29) daily[dayIdx]++
            cal.timeInMillis = t * 1000
            hourly[cal.get(Calendar.HOUR_OF_DAY)]++
            weekday[(cal.get(Calendar.DAY_OF_WEEK) + 5) % 7]++
        }
        return LfStats(daily, hourly, weekday, all.size, first.second > maxPages)
    }

    suspend fun loved(key: String, user: String, limit: Int = 30): Pair<List<LfScrobble>, Long> {
        val root = call(key, "user.getlovedtracks", mapOf("user" to user, "limit" to limit))
        val lt = root["lovedtracks"] as? JsonObject ?: return emptyList<LfScrobble>() to 0
        val total = (lt["@attr"] as? JsonObject)?.str("total")?.toLongOrNull() ?: 0
        return lt.list("track").map { t ->
            LfScrobble(
                t.str("name") ?: "", (t["artist"] as? JsonObject)?.str("name") ?: "", null, t.image(),
                (t["date"] as? JsonObject)?.str("uts")?.toLongOrNull(), true,
            )
        } to total
    }

    suspend fun dashboard(key: String, user: String): LfDashboard = coroutineScope {
        val u = async { user(key, user) }
        val r = async { recent(key, user, 50) }
        val l = async { runCatching { loved(key, user) }.getOrDefault(emptyList<LfScrobble>() to 0L) }
        val s = async { stats(key, user) }
        LfDashboard(u.await(), r.await(), l.await().first, l.await().second, s.await())
    }

    suspend fun tops(key: String, user: String, period: LfPeriod): LfTops = coroutineScope {
        fun items(o: JsonObject?, field: String, withArtist: Boolean) = o?.list(field).orEmpty().map { a ->
            LfItem(
                a.str("name") ?: "",
                if (withArtist) (a["artist"] as? JsonObject)?.let { it.str("name") ?: it.str("#text") } else null,
                a.str("playcount")?.toLongOrNull() ?: 0,
                a.image(),
            )
        }
        val ar = async { items(call(key, "user.gettopartists", mapOf("user" to user, "period" to period.api, "limit" to 20))["topartists"] as? JsonObject, "artist", false) }
        val al = async { items(call(key, "user.gettopalbums", mapOf("user" to user, "period" to period.api, "limit" to 20))["topalbums"] as? JsonObject, "album", true) }
        val tr = async { items(call(key, "user.gettoptracks", mapOf("user" to user, "period" to period.api, "limit" to 20))["toptracks"] as? JsonObject, "track", true) }
        val artists = ar.await()
        // Géneros: etiquetas principales de tus 12 artistas más escuchados, pesadas por reproducciones.
        val weights = HashMap<String, Double>()
        artists.take(12).map { a ->
            async {
                val tags = runCatching { (call(key, "artist.gettoptags", mapOf("artist" to a.name, "autocorrect" to 1))["toptags"] as? JsonObject)?.list("tag").orEmpty() }.getOrDefault(emptyList())
                a to tags.take(3).mapNotNull { t -> t.str("name")?.lowercase()?.let { it to (t.str("count")?.toDoubleOrNull() ?: 50.0) } }
            }
        }.awaitAll().forEach { (a, tags) ->
            val sum = tags.sumOf { it.second }.coerceAtLeast(1.0)
            tags.forEach { (name, c) -> weights[name] = (weights[name] ?: 0.0) + a.plays * c / sum }
        }
        val total = weights.values.sum().coerceAtLeast(1.0)
        val sorted = weights.entries.sortedByDescending { it.value }.map { it.key.replaceFirstChar { c -> c.uppercase() } to it.value / total }
        val genres = sorted.take(5) + listOfNotNull(sorted.drop(5).sumOf { it.second }.takeIf { it > 0.001 }?.let { "Otros" to it })
        LfTops(artists, al.await(), tr.await(), genres)
    }

    // ---------------------------------------------------------------- JSON de Last.fm (con sus mañas)

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.content
    private fun JsonObject.num(k: String): Long = str(k)?.toLongOrNull() ?: 0

    /** Last.fm manda un objeto suelto cuando hay uno solo, y lista cuando hay varios. */
    private fun JsonObject.list(k: String): List<JsonObject> = when (val e: JsonElement? = this[k]) {
        is JsonArray -> e.mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(e)
        else -> emptyList()
    }

    /** La imagen más grande; ignora la estrella gris que Last.fm pone a los artistas desde 2019. */
    private fun JsonObject.image(): String? = (this["image"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.str("#text") }
        ?.lastOrNull { it.isNotBlank() }?.takeIf { !it.contains("2a96cbd8b46e442fc41c2b86b821562f") }
}
