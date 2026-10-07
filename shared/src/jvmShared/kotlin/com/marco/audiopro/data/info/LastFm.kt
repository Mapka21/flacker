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
