package com.marco.audiopro.data.meta

import com.marco.audiopro.data.subsonic.SubsonicJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Un disco encontrado en MusicBrainz al buscar. */
data class MbHit(
    val id: String, val title: String, val artist: String, val score: Int, val date: String?, val country: String?,
    val trackCount: Int, val groupId: String?, val format: String?, val status: String?,
) {
    val year: Int? get() = date?.take(4)?.toIntOrNull()
}

data class MbTrack(val disc: Int, val number: Int, val title: String, val artist: String, val lengthMs: Long?, val recordingId: String?)

/** Un disco completo: canciones, fecha original y géneros (votados por la comunidad). */
data class MbRelease(
    val id: String, val title: String, val artist: String, val date: String?, val groupId: String?, val firstDate: String?,
    val genres: List<Pair<String, Int>>, val tracks: List<MbTrack>, val label: String?,
) {
    /** Año del lanzamiento original (no el de la reedición). */
    val year: Int? get() = (firstDate ?: date)?.take(4)?.toIntOrNull()
    val discs: Int get() = tracks.maxOfOrNull { it.disc } ?: 1
}

/** Un grupo de lanzamientos (el "álbum" sin importar la edición), con sus etiquetas de género. */
data class MbGroup(val id: String, val title: String, val artist: String, val score: Int, val tags: List<Pair<String, Int>>)

/**
 * MusicBrainz: la base de datos de música abierta (la que usan Picard, Navidrome y Plex). Sin
 * llave; pide una consulta por segundo como máximo, así que todo pasa por una fila.
 * Portadas: Cover Art Archive (la de MusicBrainz).
 */
class MusicBrainz(http: OkHttpClient) {
    private val http = http.newBuilder().callTimeout(20, TimeUnit.SECONDS).build()
    private val lock = Mutex()
    private var last = 0L

    private suspend fun get(path: String, params: Map<String, String>): JsonObject? = lock.withLock {
        val wait = last + 1100 - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        last = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            val url = "https://musicbrainz.org/ws/2/$path".toHttpUrl().newBuilder()
                .apply { params.forEach { (k, v) -> addQueryParameter(k, v) }; addQueryParameter("fmt", "json") }.build()
            runCatching {
                http.newCall(Request.Builder().url(url).header("User-Agent", "Flacker/2.11 (personal music player)").header("Accept", "application/json").build())
                    .execute().use { r -> if (!r.isSuccessful) null else SubsonicJson.parseToJsonElement(r.body!!.string()).jsonObject }
            }.getOrNull()
        }
    }

    /** Escapa lo que Lucene trata como operador. */
    private fun q(s: String) = "\"" + s.replace(Regex("""([+\-!(){}\[\]^"~*?:\\/]|&&|\|\|)"""), "\\\\$1") + "\""

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.content?.toIntOrNull()
    private fun JsonObject.arr(k: String) = (this[k] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    private fun credit(o: JsonObject) = o.arr("artist-credit").joinToString("") { (it.str("name") ?: it["artist"]?.jsonObject?.str("name") ?: "") + (it.str("joinphrase") ?: "") }
    private fun tags(o: JsonObject, key: String) = o.arr(key).mapNotNull { t -> t.str("name")?.let { it to (t.int("count") ?: 0) } }

    suspend fun searchReleases(artist: String, album: String, tracks: Int? = null): List<MbHit> {
        val query = buildString {
            append("release:").append(q(album))
            if (artist.isNotBlank()) append(" AND artist:").append(q(artist))
            if (tracks != null && tracks > 0) append(" AND tracks:").append(tracks)
        }
        val r = get("release", mapOf("query" to query, "limit" to "15")) ?: return emptyList()
        return r.arr("releases").map { o ->
            MbHit(
                id = o.str("id")!!, title = o.str("title") ?: "", artist = credit(o), score = o.int("score") ?: 0,
                date = o.str("date"), country = o.str("country"), trackCount = o.int("track-count") ?: 0,
                groupId = o["release-group"]?.jsonObject?.str("id"), format = o.arr("media").firstOrNull()?.str("format"), status = o.str("status"),
            )
        }
    }

    /** Búsqueda libre (cuando el usuario escribe "artista disco" a mano). */
    suspend fun searchFree(text: String): List<MbHit> {
        val r = get("release", mapOf("query" to text, "limit" to "15")) ?: return emptyList()
        return r.arr("releases").map { o ->
            MbHit(o.str("id")!!, o.str("title") ?: "", credit(o), o.int("score") ?: 0, o.str("date"), o.str("country"), o.int("track-count") ?: 0,
                o["release-group"]?.jsonObject?.str("id"), o.arr("media").firstOrNull()?.str("format"), o.str("status"))
        }
    }

    suspend fun release(id: String): MbRelease? {
        val o = get("release/$id", mapOf("inc" to "recordings artist-credits release-groups genres tags labels")) ?: return null
        val group = o["release-group"] as? JsonObject
        val tracks = o.arr("media").flatMapIndexed { mi, m ->
            val disc = m.int("position") ?: (mi + 1)
            m.arr("tracks").map { t ->
                val rec = t["recording"] as? JsonObject
                MbTrack(
                    disc, t.int("position") ?: 0, t.str("title") ?: rec?.str("title") ?: "",
                    credit(t).ifBlank { rec?.let(::credit) ?: "" }, (t.int("length") ?: rec?.int("length"))?.toLong(), rec?.str("id"),
                )
            }
        }
        // Géneros: los del grupo (más votados) y si no, los del disco.
        val g = (group?.let { tags(it, "genres") }.orEmpty() + tags(o, "genres")).groupBy({ it.first }, { it.second }).map { (k, v) -> k to v.sum() }
        return MbRelease(
            id = id, title = o.str("title") ?: "", artist = credit(o), date = o.str("date"), groupId = group?.str("id"),
            firstDate = group?.str("first-release-date"), genres = g.sortedByDescending { it.second },
            tracks = tracks, label = o.arr("label-info").firstOrNull()?.get("label")?.let { it as? JsonObject }?.str("name"),
        )
    }

    /** Para los géneros: el álbum como grupo, con sus etiquetas (una sola consulta). */
    suspend fun searchGroup(artist: String, album: String): MbGroup? {
        val query = "releasegroup:${q(album)}" + if (artist.isNotBlank()) " AND artist:${q(artist)}" else ""
        val r = get("release-group", mapOf("query" to query, "limit" to "5")) ?: return null
        return r.arr("release-groups").map { o -> MbGroup(o.str("id")!!, o.str("title") ?: "", credit(o), o.int("score") ?: 0, tags(o, "tags")) }
            .firstOrNull { it.score >= 90 }
    }

    /** Las etiquetas del artista (cuando el álbum no tiene). */
    suspend fun artistTags(artist: String): List<Pair<String, Int>> {
        val r = get("artist", mapOf("query" to "artist:${q(artist)}", "limit" to "3")) ?: return emptyList()
        return r.arr("artists").firstOrNull { (it.int("score") ?: 0) >= 95 }?.let { tags(it, "tags") }.orEmpty()
    }

    fun coverUrl(releaseId: String) = "https://coverartarchive.org/release/$releaseId/front-1200"
    fun groupCoverUrl(groupId: String) = "https://coverartarchive.org/release-group/$groupId/front-1200"

    /** Baja la portada (del disco o, si no tiene, la del grupo). */
    suspend fun cover(r: MbRelease): ByteArray? = withContext(Dispatchers.IO) {
        listOfNotNull(coverUrl(r.id), r.groupId?.let(::groupCoverUrl)).firstNotNullOfOrNull { url ->
            runCatching {
                http.newCall(Request.Builder().url(url).header("User-Agent", "Flacker/2.11").build()).execute().use { res ->
                    if (res.isSuccessful) res.body?.bytes()?.takeIf { it.size > 5_000 } else null
                }
            }.getOrNull()
        }
    }
}
