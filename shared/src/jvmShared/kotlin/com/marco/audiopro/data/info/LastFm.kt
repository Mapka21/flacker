package com.marco.audiopro.data.info

import com.marco.audiopro.data.subsonic.SubsonicJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

data class LastFmAlbum(
    val summary: String?,
    val tags: List<String>,
    val listeners: Long?,
    val playcount: Long?,
    val url: String?,
)

class LastFm(private val http: OkHttpClient) {

    /**
     * Reseña de Wikipedia (primero en español, luego en inglés) cuando Last.fm no tiene texto:
     * busca el artículo del álbum, comprueba que sea del artista correcto y toma la introducción.
     * @return texto y liga al artículo.
     */
    suspend fun wikipedia(artist: String, album: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        for ((lang, word) in listOf("es" to "álbum", "en" to "album")) {
            runCatching { wikiLookup(lang, word, artist, album) }.getOrNull()?.let { return@withContext it }
        }
        null
    }

    private fun wikiGet(url: okhttp3.HttpUrl): JsonObject? =
        http.newCall(Request.Builder().url(url).header("User-Agent", "Flacker/2.0 (personal music app)").build()).execute().use { r ->
            if (!r.isSuccessful) null else SubsonicJson.parseToJsonElement(r.body!!.string()).jsonObject
        }

    private fun wikiLookup(lang: String, word: String, artist: String, album: String): Pair<String, String>? {
        val api = "https://$lang.wikipedia.org/w/api.php".toHttpUrl()
        val search = wikiGet(
            api.newBuilder().addQueryParameter("action", "query").addQueryParameter("list", "search")
                .addQueryParameter("srsearch", "$album $artist $word").addQueryParameter("srlimit", "5")
                .addQueryParameter("format", "json").build(),
        ) ?: return null
        val hits = ((search["query"] as? JsonObject)?.get("search") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val a = norm(album); val ar = norm(artist)
        val title = hits.firstOrNull { h ->
            val t = norm(h.str("title") ?: "")
            val snip = norm(h.str("snippet") ?: "")
            t.startsWith(a) && (t.contains(ar) || snip.contains(ar))
        }?.str("title") ?: return null
        val page = wikiGet(
            api.newBuilder().addQueryParameter("action", "query").addQueryParameter("prop", "extracts")
                .addQueryParameter("exintro", "1").addQueryParameter("explaintext", "1").addQueryParameter("redirects", "1")
                .addQueryParameter("titles", title).addQueryParameter("format", "json").build(),
        ) ?: return null
        val pages = (page["query"] as? JsonObject)?.get("pages") as? JsonObject ?: return null
        val text = pages.values.firstNotNullOfOrNull { (it as? JsonObject)?.str("extract") }?.trim()
            ?.takeIf { it.length > 80 && norm(it).contains(ar) } ?: return null
        return text to "https://$lang.wikipedia.org/wiki/${title.replace(' ', '_')}"
    }

    /** Sin acentos, signos ni etiquetas HTML (los fragmentos de búsqueda traen <span>). */
    private fun norm(s: String) = java.text.Normalizer.normalize(s.replace(Regex("<[^>]+>"), "").lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()

    suspend fun albumInfo(apiKey: String, artist: String, album: String): LastFmAlbum? {
        // Primero en español; si no hay wiki traducida, en inglés.
        val es = fetch(apiKey, artist, album, "es") ?: return null
        if (es.summary != null) return es
        return fetch(apiKey, artist, album, null)?.takeIf { it.summary != null }?.copy(
            tags = es.tags, listeners = es.listeners, playcount = es.playcount,
        ) ?: es
    }

    private suspend fun fetch(apiKey: String, artist: String, album: String, lang: String?): LastFmAlbum? =
        withContext(Dispatchers.IO) {
            val url = "https://ws.audioscrobbler.com/2.0/".toHttpUrl().newBuilder()
                .addQueryParameter("method", "album.getinfo")
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("artist", artist)
                .addQueryParameter("album", album)
                .addQueryParameter("autocorrect", "1")
                .addQueryParameter("format", "json")
                .apply { if (lang != null) addQueryParameter("lang", lang) }
                .build()
            runCatching {
                http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val root = SubsonicJson.parseToJsonElement(resp.body!!.string()).jsonObject
                    val a = root["album"] as? JsonObject ?: return@use null
                    val wiki = a["wiki"] as? JsonObject
                    val content = wiki?.str("content")?.let(::cleanLastFmText)
                    val summary = wiki?.str("summary")?.let(::cleanLastFmText)
                    // Last.fm devuelve "tags": "" cuando no hay, u objeto con tag como lista u objeto suelto.
                    val tagEl: JsonElement? = (a["tags"] as? JsonObject)?.get("tag")
                    val tags = when (tagEl) {
                        is JsonArray -> tagEl.mapNotNull { (it as? JsonObject)?.str("name") }
                        is JsonObject -> listOfNotNull(tagEl.str("name"))
                        else -> emptyList()
                    }
                    LastFmAlbum(
                        summary = (content?.takeIf { it.length > (summary?.length ?: 0) } ?: summary)?.takeIf { it.length > 40 },
                        tags = tags.take(8),
                        listeners = a.str("listeners")?.toLongOrNull(),
                        playcount = a.str("playcount")?.toLongOrNull(),
                        url = a.str("url"),
                    )
                }
            }.getOrNull()
        }

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() }

    companion object {
        /** Quita HTML y el pie "Read more on Last.fm" / licencia CC. */
        fun cleanLastFmText(raw: String): String = raw
            .replace(Regex("<a [^>]*>.*?</a>\\.?", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("User-contributed text is available.*$", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<[^>]+>"), "")
            .replace("&quot;", "\"").replace("&amp;", "&").replace("&#39;", "'")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
