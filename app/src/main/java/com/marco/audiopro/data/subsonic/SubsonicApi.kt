package com.marco.audiopro.data.subsonic

import com.marco.audiopro.data.ServerAccount
import com.marco.audiopro.data.StreamQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom

class SubsonicException(message: String) : Exception(message)

val SubsonicJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/**
 * Cliente mínimo de la API Subsonic/OpenSubsonic que expone Navidrome.
 * Auth por token: t = md5(password + salt). La sal se fija al iniciar sesión,
 * así las URLs de stream y portadas son estables y se pueden cachear.
 */
class SubsonicApi(private val http: OkHttpClient, private val accountProvider: () -> ServerAccount?) {

    private val account: ServerAccount
        get() = accountProvider() ?: throw SubsonicException("No hay servidor configurado")

    private fun url(endpoint: String, params: Map<String, Any?> = emptyMap(), acc: ServerAccount = account): HttpUrl {
        val b = (acc.url.trimEnd('/') + "/rest/" + endpoint).toHttpUrl().newBuilder()
            .addQueryParameter("u", acc.user)
            .addQueryParameter("t", acc.token)
            .addQueryParameter("s", acc.salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "MarcoAudioPro")
            .addQueryParameter("f", "json")
        params.forEach { (k, v) -> if (v != null) b.addQueryParameter(k, v.toString()) }
        return b.build()
    }

    private suspend fun call(endpoint: String, params: Map<String, Any?> = emptyMap(), acc: ServerAccount = account): JsonObject =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url(endpoint, params, acc)).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw SubsonicException("HTTP ${resp.code}")
                val body = resp.body?.string() ?: throw SubsonicException("Respuesta vacía")
                val root = SubsonicJson.parseToJsonElement(body).jsonObject["subsonic-response"]?.jsonObject
                    ?: throw SubsonicException("Respuesta inválida (¿es un servidor Subsonic?)")
                if (root["status"]?.jsonPrimitive?.content != "ok") {
                    val err = root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                    throw SubsonicException(err ?: "Error del servidor")
                }
                root
            }
        }

    private suspend inline fun <reified T> get(endpoint: String, key: String, params: Map<String, Any?> = emptyMap(), default: T): T {
        val el: JsonElement = call(endpoint, params)[key] ?: return default
        return SubsonicJson.decodeFromJsonElement(el)
    }

    suspend fun ping(acc: ServerAccount) { call("ping", acc = acc) }

    suspend fun albumList(type: String, size: Int = 40, offset: Int = 0, extra: Map<String, Any?> = emptyMap()): List<Album> =
        get<AlbumList>("getAlbumList2", "albumList2", mapOf("type" to type, "size" to size, "offset" to offset) + extra, AlbumList()).album

    suspend fun album(id: String): Album = get("getAlbum", "album", mapOf("id" to id), Album(id))

    suspend fun artists(): List<Artist> = get("getArtists", "artists", default = ArtistsID3()).index.flatMap { it.artist }

    suspend fun artist(id: String): Artist = get("getArtist", "artist", mapOf("id" to id), Artist(id))

    suspend fun artistInfo(id: String): ArtistInfo =
        runCatching { get("getArtistInfo2", "artistInfo2", mapOf("id" to id, "count" to 12), ArtistInfo()) }.getOrDefault(ArtistInfo())

    suspend fun albumInfo(id: String): AlbumInfo =
        runCatching { get("getAlbumInfo2", "albumInfo", mapOf("id" to id), AlbumInfo()) }.getOrDefault(AlbumInfo())

    suspend fun search(query: String): SearchResult =
        get("search3", "searchResult3", mapOf("query" to query, "artistCount" to 10, "albumCount" to 20, "songCount" to 30), SearchResult())

    suspend fun playlists(): List<Playlist> = get("getPlaylists", "playlists", default = Playlists()).playlist

    suspend fun playlist(id: String): Playlist = get("getPlaylist", "playlist", mapOf("id" to id), Playlist(id))

    suspend fun randomSongs(size: Int = 50): List<Song> = get("getRandomSongs", "randomSongs", mapOf("size" to size), RandomSongs()).song

    suspend fun lyrics(songId: String): StructuredLyrics? = runCatching {
        get("getLyricsBySongId", "lyricsList", mapOf("id" to songId), LyricsList()).structuredLyrics
            .sortedByDescending { it.synced }.firstOrNull { it.line.isNotEmpty() }
    }.getOrNull()

    suspend fun star(id: String, starred: Boolean) { call(if (starred) "star" else "unstar", mapOf("id" to id)) }

    suspend fun scrobble(id: String, submission: Boolean) {
        runCatching { call("scrobble", mapOf("id" to id, "submission" to submission, "time" to System.currentTimeMillis())) }
    }

    fun coverUrl(coverArt: String?, size: Int = 600): String? =
        coverArt?.let { runCatching { url("getCoverArt", mapOf("id" to it, "size" to size)).toString() }.getOrNull() }

    fun streamUrl(id: String, quality: StreamQuality): String =
        url(
            "stream",
            mapOf(
                "id" to id,
                "format" to quality.format,
                "maxBitRate" to quality.maxBitRate.takeIf { it > 0 },
            ),
        ).toString()

    companion object {
        fun login(url: String, user: String, password: String): ServerAccount {
            val bytes = ByteArray(8).also { SecureRandom().nextBytes(it) }
            val salt = bytes.joinToString("") { "%02x".format(it) }
            val token = MessageDigest.getInstance("MD5").digest((password + salt).toByteArray())
                .joinToString("") { "%02x".format(it) }
            var u = url.trim().trimEnd('/')
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
            return ServerAccount(u, user.trim(), salt, token)
        }
    }
}
