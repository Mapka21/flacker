package com.marco.audiopro.data.info

import com.marco.audiopro.data.subsonic.Lyric
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.StructuredLyrics
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.data.subsonic.SubsonicJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Letras: primero las de Navidrome (archivos .lrc / tags); si no hay, LRCLIB (lrclib.net,
 * base libre de letras sincronizadas, sin API key). Lo de internet se guarda en disco.
 */
class LyricsRepository(private val http: OkHttpClient, private val api: SubsonicApi, cacheDir: File) {
    private val dir = File(cacheDir, "lyrics").apply { mkdirs() }
    private fun file(id: String) = File(dir, id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")

    suspend fun get(song: Song): StructuredLyrics? {
        api.lyrics(song.id)?.let { return it.copy(source = it.source ?: "Navidrome") }
        cached(song)?.let { return it.takeIf { l -> l.line.isNotEmpty() } }
        val found = fetchLrclib(song)
        // También se guarda "no hay letra" para no volver a preguntar cada vez.
        withContext(Dispatchers.IO) {
            runCatching { file(song.id).writeText(SubsonicJson.encodeToString(found ?: StructuredLyrics(source = "LRCLIB"))) }
        }
        return found
    }

    private suspend fun cached(song: Song): StructuredLyrics? = withContext(Dispatchers.IO) {
        file(song.id).takeIf { it.exists() }?.let { runCatching { SubsonicJson.decodeFromString<StructuredLyrics>(it.readText()) }.getOrNull() }
    }

    private suspend fun fetchLrclib(song: Song): StructuredLyrics? = withContext(Dispatchers.IO) {
        val artist = song.artist ?: return@withContext null
        val exact = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("artist_name", artist)
            .addQueryParameter("track_name", song.title)
            .apply {
                song.album?.let { addQueryParameter("album_name", it) }
                song.duration?.let { addQueryParameter("duration", it.toString()) }
            }.build()
        request(exact.toString())?.let { parse(it.jsonObject) }?.let { return@withContext it }
        // Sin coincidencia exacta: búsqueda y nos quedamos con la primera que tenga letra.
        val search = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", song.title)
            .addQueryParameter("artist_name", artist)
            .build()
        val results = request(search.toString()) as? JsonArray ?: return@withContext null
        results.mapNotNull { (it as? JsonObject)?.let(::parse) }.sortedByDescending { it.synced }.firstOrNull()
    }

    private fun request(url: String) = runCatching {
        http.newCall(Request.Builder().url(url).header("User-Agent", "MarcoAudioPro (app personal)").build()).execute().use { r ->
            if (!r.isSuccessful) null else SubsonicJson.parseToJsonElement(r.body!!.string())
        }
    }.getOrNull()

    private fun parse(o: JsonObject): StructuredLyrics? {
        fun str(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        str("syncedLyrics")?.let { lrc ->
            val lines = parseLrc(lrc)
            if (lines.isNotEmpty()) return StructuredLyrics(synced = true, line = lines, source = "LRCLIB")
        }
        return str("plainLyrics")?.let { plain ->
            StructuredLyrics(synced = false, line = plain.lines().map { Lyric(null, it) }, source = "LRCLIB")
        }
    }

    companion object {
        private val tag = Regex("""\[(\d+):(\d+)(?:[.:](\d{1,3}))?]""")

        /** "[01:23.45] texto" → Lyric(83450, "texto"). Soporta varias marcas por línea. */
        fun parseLrc(lrc: String): List<Lyric> = lrc.lines().flatMap { raw ->
            val stamps = tag.findAll(raw).toList()
            if (stamps.isEmpty()) return@flatMap emptyList()
            val text = raw.substring(stamps.last().range.last + 1).trim()
            stamps.map { m ->
                val (mm, ss, frac) = m.destructured
                val ms = when (frac.length) { 0 -> 0; 1 -> frac.toInt() * 100; 2 -> frac.toInt() * 10; else -> frac.take(3).toInt() }
                Lyric(mm.toLong() * 60_000 + ss.toLong() * 1000 + ms, text)
            }
        }.sortedBy { it.start }
    }
}
