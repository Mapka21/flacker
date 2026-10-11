package com.marco.audiopro.data.info

import com.marco.audiopro.data.discover.normName
import com.marco.audiopro.data.subsonic.Lyric
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.StructuredLyrics
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.data.subsonic.SubsonicJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.math.abs

/** Una letra posible en LRCLIB (para "Elegir otra letra"). */
data class LyricsCandidate(
    val id: Long,
    val track: String,
    val artist: String,
    val album: String?,
    val duration: Int,
    val synced: Boolean,
    /** Diferencia de duración con tu archivo, en segundos. */
    val diff: Int?,
)

@Serializable
private data class CachedLyrics(val lyrics: StructuredLyrics? = null, val chosen: Boolean = false, val hidden: Boolean = false)

/**
 * Letras confiables:
 * - Navidrome primero si trae letra sincronizada (tus .lrc / etiquetas).
 * - Si no, LRCLIB, pero sólo la versión que dura lo mismo que tu archivo (±3 s) y del mismo
 *   artista y título; si no hay una así, nada (mejor sin letra que con la de otra versión,
 *   que es lo que hacía que fueran "lentas" o adelantadas).
 * - Se limpian líneas basura (anuncios, marcas de agua, ligas).
 * - Para cada canción puedes elegir otra letra a mano o moverla en el tiempo; se recuerda.
 */
class LyricsRepository(private val http: OkHttpClient, private val api: SubsonicApi, cacheDir: File) {
    private val dir = File(cacheDir, "lyrics2").apply { mkdirs() }
    private fun file(id: String) = File(dir, id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")
    private val offsetsFile = File(dir, "_offsets.json")

    private val _offsets = MutableStateFlow(readOffsets())
    /** Ajuste de tiempo por canción (ms; positivo = la letra va antes). */
    val offsets: StateFlow<Map<String, Long>> = _offsets.asStateFlow()

    suspend fun get(song: Song): StructuredLyrics? {
        val cached = cached(song)
        if (cached?.hidden == true) return null
        if (cached?.chosen == true) return cached.lyrics
        val nav = if (song.isPreview) null else api.lyrics(song.id)?.let(::clean)?.let { it.copy(source = it.source ?: "Navidrome") }
        if (nav?.synced == true) return nav
        val net = if (cached != null) cached.lyrics else fetchLrclib(song).also { save(song, CachedLyrics(it)) }
        return when {
            net?.synced == true -> net
            nav != null -> nav
            else -> net
        }
    }

    // ---------------------------------------------------------------- elegir a mano

    /** Todas las letras de LRCLIB para esta canción, las de duración más parecida primero. */
    suspend fun candidates(song: Song, query: String? = null): List<LyricsCandidate> = withContext(Dispatchers.IO) {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder().apply {
            if (query != null) addQueryParameter("q", query)
            else {
                addQueryParameter("track_name", song.title)
                song.artist?.let { addQueryParameter("artist_name", it) }
            }
        }.build()
        val list = (request(url.toString()) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        list.filter { it.bool("instrumental") != true }.map { o ->
            val d = o.dbl("duration")?.toInt() ?: 0
            LyricsCandidate(
                o.long("id") ?: 0, o.str("trackName") ?: "", o.str("artistName") ?: "", o.str("albumName"), d,
                o.str("syncedLyrics") != null, song.duration?.let { abs(it - d) },
            )
        }.sortedWith(compareBy<LyricsCandidate> { it.diff ?: 999 }.thenByDescending { it.synced })
    }

    /** Usar esta letra para esta canción (se queda guardada). */
    suspend fun choose(song: Song, candidateId: Long): StructuredLyrics? = withContext(Dispatchers.IO) {
        val o = request("https://lrclib.net/api/get/$candidateId")?.jsonObject ?: return@withContext null
        val l = parse(o)?.let(::clean)?.copy(source = "LRCLIB · elegida")
        save(song, CachedLyrics(l, chosen = true))
        l
    }

    /** "Sin letra" para esta canción. */
    suspend fun hide(song: Song) = save(song, CachedLyrics(hidden = true))

    /** Olvida lo elegido y vuelve a buscar automáticamente. */
    suspend fun reset(song: Song) = withContext(Dispatchers.IO) { file(song.id).delete(); Unit }

    fun setOffset(songId: String, ms: Long) {
        _offsets.value = if (ms == 0L) _offsets.value - songId else _offsets.value + (songId to ms)
        runCatching { offsetsFile.writeText(SubsonicJson.encodeToString(_offsets.value)) }
    }

    private fun readOffsets(): Map<String, Long> =
        runCatching { SubsonicJson.decodeFromString<Map<String, Long>>(offsetsFile.readText()) }.getOrDefault(emptyMap())

    // ---------------------------------------------------------------- búsqueda automática (estricta)

    private suspend fun cached(song: Song): CachedLyrics? = withContext(Dispatchers.IO) {
        file(song.id).takeIf { it.exists() }?.let { runCatching { SubsonicJson.decodeFromString<CachedLyrics>(it.readText()) }.getOrNull() }
    }

    private suspend fun save(song: Song, c: CachedLyrics) = withContext(Dispatchers.IO) {
        runCatching { file(song.id).writeText(SubsonicJson.encodeToString(c)) }
        Unit
    }

    private suspend fun fetchLrclib(song: Song): StructuredLyrics? = withContext(Dispatchers.IO) {
        val artist = song.artist ?: return@withContext null
        val dur = song.duration
        // 1) Coincidencia exacta (LRCLIB ya filtra por duración ±2 s).
        val exact = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("artist_name", artist)
            .addQueryParameter("track_name", song.title)
            .apply {
                song.album?.let { addQueryParameter("album_name", it) }
                dur?.let { addQueryParameter("duration", it.toString()) }
            }.build()
        (request(exact.toString()) as? JsonObject)?.takeIf { it.fits(song) }?.let(::parse)?.let(::clean)?.let { sane(it, song) }?.let { return@withContext it }
        // 2) Búsqueda, pero sólo versiones del mismo artista, mismo título y misma duración.
        val search = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", song.title)
            .addQueryParameter("artist_name", artist)
            .build()
        val results = (request(search.toString()) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.filter { it.fits(song) }
        results.sortedWith(
            compareByDescending<JsonObject> { it.str("syncedLyrics") != null }
                .thenBy { o -> dur?.let { abs((o.dbl("duration") ?: 0.0) - it) } ?: 0.0 },
        ).mapNotNull { parse(it)?.let(::clean) }.let { all ->
            // Primero una sincronizada que sí quepa en la canción; si ninguna, el texto sin tiempos.
            all.firstOrNull { it.synced && sane(it, song) === it } ?: all.firstNotNullOfOrNull { sane(it, song) }
        }
    }

    /**
     * Hay letras subidas con la duración de una versión pero los tiempos de otra (p. ej. la
     * versión de radio con los tiempos del álbum). Si la letra sigue después de que la canción
     * terminó, sus tiempos no sirven: se deja como texto sin sincronizar.
     */
    private fun sane(l: StructuredLyrics, song: Song): StructuredLyrics? {
        if (!l.synced) return l
        val end = song.duration?.let { it * 1000L } ?: return l
        val last = l.line.lastOrNull { it.value.isNotBlank() }?.start ?: return l
        return if (last > end + 5_000) l.copy(synced = false, line = l.line.filter { it.value.isNotBlank() }.map { it.copy(start = null) }) else l
    }

    /** ¿Es esta letra de la misma canción y la misma versión que tu archivo? */
    private fun JsonObject.fits(song: Song): Boolean {
        if (bool("instrumental") == true) return false
        val t = normName(str("trackName") ?: return false)
        val a = normName(str("artistName") ?: return false)
        val st = normName(song.title)
        val sa = normName(song.artist ?: "")
        val titleOk = t == st || t.startsWith(st) || st.startsWith(t)
        val artistOk = a == sa || a.contains(sa) || sa.contains(a)
        val durOk = song.duration?.let { d -> abs((dbl("duration") ?: return false) - d) <= 3.0 } ?: true
        return titleOk && artistOk && durOk
    }

    private fun request(url: String) = runCatching {
        http.newCall(Request.Builder().url(url).header("User-Agent", "Flacker (personal music app)").build()).execute().use { r ->
            if (!r.isSuccessful) null else SubsonicJson.parseToJsonElement(r.body!!.string())
        }
    }.getOrNull()

    private fun parse(o: JsonObject): StructuredLyrics? {
        o.str("syncedLyrics")?.let { lrc ->
            val lines = parseLrc(lrc)
            if (lines.isNotEmpty()) return StructuredLyrics(synced = true, line = lines, source = "LRCLIB")
        }
        return o.str("plainLyrics")?.let { plain ->
            StructuredLyrics(synced = false, line = plain.lines().map { Lyric(null, it) }, source = "LRCLIB")
        }
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
    private fun JsonObject.dbl(k: String) = (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()
    private fun JsonObject.long(k: String) = (this[k] as? JsonPrimitive)?.content?.toLongOrNull()
    private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

    companion object {
        private val tag = Regex("""\[(\d+):(\d+)(?:[.:](\d{1,3}))?]""")

        /** Líneas que no son letra: anuncios, marcas de agua, ligas, créditos de quien la subió. */
        private val junk = Regex(
            """(https?://|www\.|\.com\b|lyrics (provided|by)|musixmatch|letras\.|paroles|written by:|\*{3,}|subtitulado|subtitled|^\s*-+\s*$)""",
            RegexOption.IGNORE_CASE,
        )

        /** Quita basura; si casi todo era basura, la letra no sirve. */
        fun clean(l: StructuredLyrics): StructuredLyrics? {
            val kept = l.line.filterNot { junk.containsMatchIn(it.value) }
            val removed = l.line.size - kept.size
            if (kept.count { it.value.isNotBlank() } < 2 || removed > l.line.size / 2) return null
            return l.copy(line = kept)
        }

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
