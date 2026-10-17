package com.marco.audiopro.data.local

import com.marco.audiopro.data.subsonic.AlbumInfo
import com.marco.audiopro.data.subsonic.AlbumList
import com.marco.audiopro.data.subsonic.ArtistInfo
import com.marco.audiopro.data.subsonic.ArtistsID3
import com.marco.audiopro.data.subsonic.Genres
import com.marco.audiopro.data.subsonic.LyricsList
import com.marco.audiopro.data.subsonic.Playlists
import com.marco.audiopro.data.subsonic.RandomSongs
import com.marco.audiopro.data.subsonic.SearchResult
import com.marco.audiopro.data.subsonic.SubsonicJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

/**
 * Un "Navidrome" chiquito dentro de la app: contesta la API de Subsonic en 127.0.0.1 con lo que hay
 * en [LocalLibrary] y manda el audio de los archivos (con Range, para adelantar). Así el resto de la
 * app no sabe la diferencia: búsquedas, álbumes, reproductor, letras, me gusta, playlists…
 * Sólo escucha en la propia máquina (127.0.0.1): nadie de la red puede entrar.
 */
class LocalServer(private val lib: LocalLibrary) {
    private var socket: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "flacker-local").apply { isDaemon = true } }

    /** Arranca (intenta el mismo puerto de la vez pasada para que la "dirección" no cambie) y devuelve el puerto. */
    @Synchronized fun start(preferredPort: Int): Int {
        socket?.let { return it.localPort }
        val loop = InetAddress.getByName("127.0.0.1")
        val s = runCatching { ServerSocket(preferredPort, 50, loop) }.getOrElse { ServerSocket(0, 50, loop) }
        socket = s
        Thread({
            while (!s.isClosed) {
                val c = runCatching { s.accept() }.getOrNull() ?: break
                pool.execute { runCatching { handle(c) }; runCatching { c.close() } }
            }
        }, "flacker-local-accept").apply { isDaemon = true }.start()
        return s.localPort
    }

    fun stop() { runCatching { socket?.close() }; socket = null }

    // ---------------------------------------------------------------- HTTP mínimo

    private fun handle(sock: Socket) {
        sock.soTimeout = 30_000
        val input = BufferedInputStream(sock.getInputStream())
        val requestLine = readLine(input) ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val l = readLine(input) ?: break
            if (l.isEmpty()) break
            val i = l.indexOf(':')
            if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
        }
        val target = requestLine.split(" ").getOrNull(1) ?: return
        val path = target.substringBefore('?')
        val params = parseQuery(target.substringAfter('?', ""))
        val endpoint = path.substringAfterLast('/').removeSuffix(".view")
        val out = sock.getOutputStream()
        when (endpoint) {
            "stream", "download" -> stream(out, params.one("id"), headers["range"])
            "getCoverArt" -> cover(out, params.one("id"))
            else -> json(out, api(endpoint, params))
        }
        out.flush()
    }

    private fun readLine(input: BufferedInputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
        }
    }

    private fun parseQuery(q: String): Map<String, List<String>> = q.split('&').filter { it.isNotEmpty() }.map {
        URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
    }.groupBy({ it.first }, { it.second })

    private fun Map<String, List<String>>.one(k: String) = this[k]?.firstOrNull()
    private fun Map<String, List<String>>.int(k: String, def: Int) = one(k)?.toIntOrNull() ?: def

    private fun header(out: OutputStream, status: String, type: String, length: Long, extra: String = "") {
        out.write(("HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: $length\r\nConnection: close\r\n$extra\r\n").toByteArray())
    }

    private fun json(out: OutputStream, body: JsonObject) {
        val bytes = body.toString().toByteArray()
        header(out, "200 OK", "application/json; charset=utf-8", bytes.size.toLong())
        out.write(bytes)
    }

    private fun cover(out: OutputStream, id: String?) {
        val bytes = id?.let { runCatching { lib.cover(it) }.getOrNull() }
        if (bytes == null) { header(out, "404 Not Found", "text/plain", 0); return }
        val type = if (bytes.size > 3 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) "image/png" else "image/jpeg"
        header(out, "200 OK", type, bytes.size.toLong(), "Cache-Control: max-age=86400\r\n")
        out.write(bytes)
    }

    /** El archivo tal cual; con "Range: bytes=a-b" manda sólo ese pedazo (206). */
    private fun stream(out: OutputStream, id: String?, range: String?) {
        val t = id?.let { lib.track(it) }
        val input = t?.let { runCatching { lib.source.open(it.key) }.getOrNull() }
        if (t == null || input == null) { header(out, "404 Not Found", "text/plain", 0); return }
        input.use { ins ->
            val total = t.size
            val m = range?.let { Regex("""bytes=(\d*)-(\d*)""").find(it) }
            if (m != null && total > 0) {
                val start = m.groupValues[1].toLongOrNull() ?: 0L
                val end = (m.groupValues[2].toLongOrNull() ?: (total - 1)).coerceAtMost(total - 1)
                if (start >= total) { header(out, "416 Range Not Satisfiable", "text/plain", 0, "Content-Range: bytes */$total\r\n"); return }
                var skipped = 0L
                while (skipped < start) { val s = ins.skip(start - skipped); if (s <= 0) break; skipped += s }
                val len = end - start + 1
                header(out, "206 Partial Content", LocalLibrary.mime(t.suffix), len, "Accept-Ranges: bytes\r\nContent-Range: bytes $start-$end/$total\r\n")
                copy(ins, out, len)
            } else {
                header(out, "200 OK", LocalLibrary.mime(t.suffix), total, "Accept-Ranges: bytes\r\n")
                copy(ins, out, total)
            }
        }
    }

    private fun copy(ins: java.io.InputStream, out: OutputStream, max: Long) {
        val buf = ByteArray(64 * 1024)
        var left = max
        while (left > 0) {
            val n = ins.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) break
            out.write(buf, 0, n); left -= n
        }
    }

    // ---------------------------------------------------------------- API de Subsonic

    private inline fun <reified T> enc(v: T): JsonElement = SubsonicJson.encodeToJsonElement(v)

    private fun ok(key: String? = null, value: JsonElement? = null): JsonObject = buildJsonObject {
        put("subsonic-response", buildJsonObject {
            put("status", "ok"); put("version", "1.16.1"); put("type", "flacker-local"); put("openSubsonic", true)
            if (key != null && value != null) put(key, value)
        })
    }

    private fun fail(msg: String): JsonObject = buildJsonObject {
        put("subsonic-response", buildJsonObject {
            put("status", "failed"); put("version", "1.16.1")
            put("error", buildJsonObject { put("code", JsonPrimitive(70)); put("message", msg) })
        })
    }

    private fun api(endpoint: String, p: Map<String, List<String>>): JsonObject = runCatching {
        when (endpoint) {
            "ping", "getLicense" -> ok()
            "getAlbumList2", "getAlbumList" -> ok(
                "albumList2",
                enc(AlbumList(lib.albums(p.one("type") ?: "alphabeticalByName", p.int("size", 40), p.int("offset", 0), p.one("genre"), p.one("fromYear")?.toIntOrNull(), p.one("toYear")?.toIntOrNull()))),
            )
            "getGenres" -> ok("genres", enc(Genres(lib.genres())))
            "getAlbum" -> lib.albumById(p.one("id") ?: "")?.let { ok("album", enc(it)) } ?: fail("Álbum no encontrado")
            "getArtists", "getIndexes" -> ok("artists", enc(ArtistsID3(lib.artistIndex())))
            "getArtist" -> lib.artist(p.one("id") ?: "")?.let { ok("artist", enc(it)) } ?: fail("Artista no encontrado")
            "getArtistInfo2", "getArtistInfo" -> ok("artistInfo2", enc(ArtistInfo(similarArtist = lib.similarArtists(p.one("id") ?: ""))))
            "getAlbumInfo2", "getAlbumInfo" -> ok("albumInfo", enc(AlbumInfo()))
            "search3", "search2" -> {
                val (ar, al, so) = lib.search(
                    p.one("query") ?: "", p.int("artistCount", 20), p.int("albumCount", 20), p.int("songCount", 20),
                    p.int("artistOffset", 0), p.int("albumOffset", 0), p.int("songOffset", 0),
                )
                ok("searchResult3", enc(SearchResult(ar, al, so)))
            }
            "getPlaylists" -> ok("playlists", enc(Playlists(lib.playlists())))
            "getPlaylist" -> lib.playlist(p.one("id") ?: "")?.let { ok("playlist", enc(it)) } ?: fail("Playlist no encontrada")
            "createPlaylist" -> {
                val existing = p.one("playlistId")
                if (existing != null) {
                    lib.updatePlaylist(existing, p.one("name"), p["songId"].orEmpty(), emptyList())
                    ok("playlist", enc(lib.playlist(existing)))
                } else ok("playlist", enc(lib.createPlaylist(p.one("name") ?: "Playlist", p["songId"].orEmpty())))
            }
            "updatePlaylist" -> {
                lib.updatePlaylist(p.one("playlistId") ?: "", p.one("name"), p["songIdToAdd"].orEmpty(), p["songIndexToRemove"].orEmpty().mapNotNull { it.toIntOrNull() })
                ok()
            }
            "deletePlaylist" -> { lib.deletePlaylist(p.one("id") ?: ""); ok() }
            "getRandomSongs" -> ok("randomSongs", enc(RandomSongs(lib.randomSongs(p.int("size", 50), p.one("genre")))))
            "getLyricsBySongId" -> ok("lyricsList", enc(LyricsList(listOfNotNull(lib.lyrics(p.one("id") ?: "")))))
            "getSimilarSongs2", "getSimilarSongs" -> ok("similarSongs2", enc(RandomSongs(lib.similarSongs(p.one("id") ?: "", p.int("count", 50)))))
            "getTopSongs" -> ok("topSongs", enc(RandomSongs(lib.topSongs(p.one("artist") ?: "", p.int("count", 10)))))
            "getStarred2", "getStarred" -> {
                val (ar, al, so) = lib.starred()
                ok("starred2", enc(SearchResult(ar, al, so)))
            }
            "star", "unstar" -> {
                lib.star(p["id"].orEmpty() + p["albumId"].orEmpty() + p["artistId"].orEmpty(), endpoint == "star")
                ok()
            }
            "scrobble" -> {
                if (p.one("submission") != "false") p["id"].orEmpty().forEach { lib.scrobble(it, p.one("time")?.toLongOrNull() ?: System.currentTimeMillis()) }
                ok()
            }
            else -> fail("No disponible sin servidor: $endpoint")
        }
    }.getOrElse { fail(it.message ?: "Error") }
}
