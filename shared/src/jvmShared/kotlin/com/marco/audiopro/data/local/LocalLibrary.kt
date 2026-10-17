package com.marco.audiopro.data.local

import com.marco.audiopro.data.info.LyricsRepository
import com.marco.audiopro.data.subsonic.Album
import com.marco.audiopro.data.subsonic.Artist
import com.marco.audiopro.data.subsonic.ArtistIndex
import com.marco.audiopro.data.subsonic.Genre
import com.marco.audiopro.data.subsonic.Lyric
import com.marco.audiopro.data.subsonic.Playlist
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.StructuredLyrics
import com.marco.audiopro.data.subsonic.SubsonicJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant

/** Una canción encontrada en tus archivos (lo que se guarda del escaneo). */
@Serializable
data class LocalTrack(
    /** Ruta del archivo (PC) o URI content:// (Android). */
    val key: String,
    val title: String,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val track: Int? = null,
    val disc: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val duration: Int = 0,
    val bitRate: Int? = null,
    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
    val channels: Int? = null,
    val suffix: String = "",
    val size: Long = 0,
    val modified: Long = 0,
    val hasArt: Boolean = false,
    /** Letra (LRC o texto) de un .lrc al lado o del tag. */
    val lyrics: String? = null,
    val addedAt: Long = 0,
)

/** De dónde salen los archivos: carpetas en la PC, la música del celular… (lo da cada plataforma). */
interface LocalSource {
    /** Para mostrar: "D:\Música" o "La música de este celular". */
    val label: String
    /** Lee todo; reutiliza lo de `previous` si el archivo no cambió (mismo tamaño y fecha). */
    suspend fun scan(previous: Map<String, LocalTrack>, progress: (Int) -> Unit): List<LocalTrack>
    fun open(key: String): InputStream?
    /** La portada (incrustada o cover.jpg/folder.jpg al lado). */
    fun cover(key: String): ByteArray?
}

/**
 * Tu biblioteca sin servidor: el índice de tus archivos más tus "me gusta", playlists y cuántas
 * veces pusiste cada canción, todo guardado en disco. [LocalServer] la sirve como si fuera
 * Navidrome, así toda la app funciona igual.
 */
class LocalLibrary(private val dir: File, val source: LocalSource) {
    @Serializable
    private data class Index(val tracks: List<LocalTrack> = emptyList())

    @Serializable
    data class LocalPlaylist(val id: String, val name: String, val songIds: List<String> = emptyList(), val created: Long = 0)

    @Serializable
    private data class State(
        val starred: Map<String, Long> = emptyMap(),
        val playlists: List<LocalPlaylist> = emptyList(),
        val plays: Map<String, Long> = emptyMap(),
        val lastPlayed: Map<String, Long> = emptyMap(),
    )

    /** El "Navidrome" de bolsillo que sirve esta biblioteca a la app. */
    val server = LocalServer(this)

    private val indexFile = File(dir, "biblioteca_local.json")
    private val stateFile = File(dir, "biblioteca_local_estado.json")

    @Volatile private var tracks: List<LocalTrack> = emptyList()
    @Volatile private var byId: Map<String, LocalTrack> = emptyMap()
    @Volatile private var state = State()
    private val _count = MutableStateFlow(0)
    /** Cuántas canciones hay (para Ajustes). */
    val count: StateFlow<Int> = _count
    private val _scanning = MutableStateFlow<Int?>(null)
    /** Mientras escanea: cuántas lleva; null si no está escaneando. */
    val scanning: StateFlow<Int?> = _scanning

    init {
        runCatching { setTracks(SubsonicJson.decodeFromString<Index>(indexFile.readText()).tracks) }
        runCatching { state = SubsonicJson.decodeFromString(stateFile.readText()) }
    }

    // Agrupados una vez por escaneo (una cuadrícula pide cientos de portadas a la vez).
    @Volatile private var albumIdByKey: Map<String, String> = emptyMap()
    @Volatile private var albumMap: Map<String, List<LocalTrack>> = emptyMap()
    @Volatile private var artistMap: Map<String, List<LocalTrack>> = emptyMap()

    private fun setTracks(t: List<LocalTrack>) {
        albumIdByKey = t.associate { it.key to computeAlbumId(it) }
        tracks = t
        byId = t.associateBy { songId(it) }
        albumMap = t.groupBy { albumId(it) }
        artistMap = t.groupBy { albumArtistOf(it) }
        _count.value = t.size
    }

    /** Vuelve a leer los archivos (sólo abre los nuevos o cambiados). */
    suspend fun rescan(): Int = withContext(Dispatchers.IO) {
        _scanning.value = 0
        try {
            val found = source.scan(tracks.associateBy { it.key }) { _scanning.value = it }
            setTracks(found)
            runCatching { indexFile.writeText(SubsonicJson.encodeToString(Index(found))) }
            found.size
        } finally { _scanning.value = null }
    }

    // ---------------------------------------------------------------- ids

    private fun hash(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    fun songId(t: LocalTrack) = "s" + hash(t.key)
    private fun albumArtistOf(t: LocalTrack) = t.albumArtist?.takeIf { it.isNotBlank() } ?: t.artist?.takeIf { it.isNotBlank() } ?: "Desconocido"
    private fun albumNameOf(t: LocalTrack) = t.album?.takeIf { it.isNotBlank() } ?: "Sin álbum"
    private fun computeAlbumId(t: LocalTrack) = "al" + hash(albumArtistOf(t).lowercase() + "\u0000" + albumNameOf(t).lowercase())
    fun albumId(t: LocalTrack) = albumIdByKey[t.key] ?: computeAlbumId(t)
    fun artistId(name: String) = "ar" + hash(name.lowercase())

    fun track(songId: String): LocalTrack? = byId[songId]

    // ---------------------------------------------------------------- modelos de Subsonic

    private fun iso(ms: Long) = Instant.ofEpochMilli(ms).toString()

    fun song(t: LocalTrack): Song {
        val id = songId(t)
        val art = albumArtistOf(t)
        return Song(
            id = id, title = t.title, album = albumNameOf(t), albumId = albumId(t), artist = t.artist ?: art, artistId = artistId(t.artist ?: art),
            track = t.track, discNumber = t.disc, year = t.year, genre = t.genre, coverArt = albumId(t), size = t.size, suffix = t.suffix,
            contentType = mime(t.suffix), duration = t.duration, bitRate = t.bitRate, samplingRate = t.sampleRate, bitDepth = t.bitDepth,
            channelCount = t.channels, starred = state.starred[id]?.let(::iso), playCount = state.plays[id],
        )
    }

    private fun albumsGrouped(): Map<String, List<LocalTrack>> = albumMap

    private fun album(id: String, list: List<LocalTrack>, withSongs: Boolean): Album {
        val sorted = list.sortedWith(compareBy({ it.disc ?: 1 }, { it.track ?: 0 }, { it.title.lowercase() }))
        val first = sorted.first()
        val art = albumArtistOf(first)
        return Album(
            id = id, name = albumNameOf(first), artist = art, artistId = artistId(art), coverArt = id,
            songCount = sorted.size, duration = sorted.sumOf { it.duration }, year = sorted.firstNotNullOfOrNull { it.year },
            genre = sorted.firstNotNullOfOrNull { it.genre }, starred = state.starred[id]?.let(::iso),
            created = iso(sorted.maxOf { it.addedAt }), playCount = sorted.sumOf { state.plays[songId(it)] ?: 0 },
            played = sorted.mapNotNull { state.lastPlayed[songId(it)] }.maxOrNull()?.let(::iso),
            song = if (withSongs) sorted.map(::song) else emptyList(),
        )
    }

    fun albumById(id: String): Album? = albumsGrouped()[id]?.let { album(id, it, true) }

    fun albums(type: String, size: Int, offset: Int, genre: String?, fromYear: Int?, toYear: Int?): List<Album> {
        var all = albumsGrouped().map { (id, l) -> album(id, l, false) }
        all = when (type) {
            "newest" -> all.sortedByDescending { it.created }
            "recent" -> all.filter { it.played != null }.sortedByDescending { it.played }
            "frequent" -> all.filter { (it.playCount ?: 0) > 0 }.sortedByDescending { it.playCount }
            "random" -> all.shuffled()
            "starred" -> all.filter { it.starred != null }
            "byGenre" -> all.filter { a -> albumsGrouped()[a.id].orEmpty().any { it.genre.equals(genre, true) } }
            "byYear" -> {
                val lo = minOf(fromYear ?: 0, toYear ?: 9999); val hi = maxOf(fromYear ?: 0, toYear ?: 9999)
                all.filter { (it.year ?: -1) in lo..hi }.let { if ((fromYear ?: 0) > (toYear ?: 0)) it.sortedByDescending { a -> a.year } else it.sortedBy { a -> a.year } }
            }
            "alphabeticalByArtist" -> all.sortedWith(compareBy({ it.artist?.lowercase() }, { it.name.lowercase() }))
            else -> all.sortedBy { it.name.lowercase() }
        }
        return all.drop(offset).take(size)
    }

    fun genres(): List<Genre> = tracks.filter { !it.genre.isNullOrBlank() }.groupBy { it.genre!! }
        .map { (g, l) -> Genre(g, l.size, l.map { albumId(it) }.distinct().size) }.sortedByDescending { it.songCount }

    private fun artistsGrouped(): Map<String, List<LocalTrack>> = artistMap

    fun artistIndex(): List<ArtistIndex> = artistsGrouped().map { (name, l) ->
        Artist(artistId(name), name, albumCount = l.map { albumId(it) }.distinct().size, coverArt = artistId(name), starred = state.starred[artistId(name)]?.let(::iso))
    }.sortedBy { it.name.lowercase() }.groupBy { initial(it.name) }.map { (k, v) -> ArtistIndex(k, v) }

    private fun initial(s: String): String {
        val c = Normalizer.normalize(s, Normalizer.Form.NFD).firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '#'
        return if (c in 'A'..'Z') c.toString() else "#"
    }

    fun artist(id: String): Artist? {
        val (name, l) = artistsGrouped().entries.firstOrNull { artistId(it.key) == id } ?: return null
        val albums = l.groupBy { albumId(it) }.map { (aid, al) -> album(aid, al, false) }.sortedByDescending { it.year ?: 0 }
        return Artist(id, name, albums.size, coverArt = id, starred = state.starred[id]?.let(::iso), album = albums)
    }

    /** Parecidos: artistas que comparten género (no hay Last.fm). */
    fun similarArtists(id: String): List<Artist> {
        val me = artistsGrouped().entries.firstOrNull { artistId(it.key) == id } ?: return emptyList()
        val genres = me.value.mapNotNull { it.genre?.lowercase() }.toSet()
        if (genres.isEmpty()) return emptyList()
        return artistsGrouped().filter { (n, l) -> n != me.key && l.any { it.genre?.lowercase() in genres } }.keys.shuffled().take(12)
            .map { Artist(artistId(it), it, coverArt = artistId(it)) }
    }

    private fun norm(s: String?) = s?.let { Normalizer.normalize(it.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "") } ?: ""

    fun search(q: String, artistCount: Int, albumCount: Int, songCount: Int, artistOffset: Int, albumOffset: Int, songOffset: Int): Triple<List<Artist>, List<Album>, List<Song>> {
        val n = norm(q.trim().trim('"'))
        val songs = (if (n.isEmpty()) tracks.sortedBy { it.title.lowercase() }
        else tracks.filter { norm(it.title).contains(n) || norm(it.artist).contains(n) || norm(it.album).contains(n) })
        val albums = if (n.isEmpty()) emptyList() else albumsGrouped().filter { (_, l) -> norm(albumNameOf(l.first())).contains(n) || norm(albumArtistOf(l.first())).contains(n) }
            .map { (id, l) -> album(id, l, false) }
        val artists = if (n.isEmpty()) emptyList() else artistIndex().flatMap { it.artist }.filter { norm(it.name).contains(n) }
        return Triple(artists.drop(artistOffset).take(artistCount), albums.drop(albumOffset).take(albumCount), songs.drop(songOffset).take(songCount).map(::song))
    }

    fun randomSongs(size: Int, genre: String?): List<Song> =
        tracks.filter { genre == null || it.genre.equals(genre, true) }.shuffled().take(size).map(::song)

    fun similarSongs(id: String, count: Int): List<Song> {
        val t = track(id) ?: return emptyList()
        val sameArtist = tracks.filter { it.artist == t.artist && it.key != t.key }.shuffled().take(count / 3)
        val sameGenre = tracks.filter { t.genre != null && it.genre.equals(t.genre, true) && it.key != t.key }.shuffled().take(count)
        return (sameArtist + sameGenre).distinctBy { it.key }.take(count).map(::song)
    }

    fun topSongs(artist: String, count: Int): List<Song> =
        tracks.filter { it.artist.equals(artist, true) || it.albumArtist.equals(artist, true) }
            .sortedWith(compareByDescending<LocalTrack> { state.plays[songId(it)] ?: 0 }.thenBy { it.track ?: 99 }).take(count).map(::song)

    fun lyrics(id: String): StructuredLyrics? {
        val raw = track(id)?.lyrics?.takeIf { it.isNotBlank() } ?: return null
        val synced = LyricsRepository.parseLrc(raw)
        return if (synced.isNotEmpty()) StructuredLyrics(true, synced, "Archivo")
        else StructuredLyrics(false, raw.lines().map { Lyric(null, it) }, "Archivo")
    }

    // ---------------------------------------------------------------- me gusta, playlists, escuchas

    private fun save() { runCatching { stateFile.writeText(SubsonicJson.encodeToString(state)) } }

    @Synchronized fun star(ids: List<String>, on: Boolean) {
        val now = System.currentTimeMillis()
        state = state.copy(starred = if (on) state.starred + ids.associateWith { now } else state.starred - ids.toSet())
        save()
    }

    fun starred(): Triple<List<Artist>, List<Album>, List<Song>> {
        val s = state.starred
        val songs = tracks.filter { songId(it) in s }.sortedByDescending { s[songId(it)] }.map(::song)
        val albums = albumsGrouped().filter { it.key in s }.map { (id, l) -> album(id, l, false) }
        val artists = artistIndex().flatMap { it.artist }.filter { it.id in s }
        return Triple(artists, albums, songs)
    }

    @Synchronized fun scrobble(id: String, time: Long) {
        if (track(id) == null) return
        state = state.copy(plays = state.plays + (id to (state.plays[id] ?: 0) + 1), lastPlayed = state.lastPlayed + (id to time))
        save()
    }

    private fun playlistModel(p: LocalPlaylist, withSongs: Boolean): Playlist {
        val songs = p.songIds.mapNotNull { track(it) }
        return Playlist(
            p.id, p.name, songs.size, songs.sumOf { it.duration }, coverArt = songs.firstOrNull()?.let { albumId(it) },
            entry = if (withSongs) songs.map(::song) else emptyList(),
        )
    }

    fun playlists(): List<Playlist> = state.playlists.map { playlistModel(it, false) }
    fun playlist(id: String): Playlist? = state.playlists.firstOrNull { it.id == id }?.let { playlistModel(it, true) }

    @Synchronized fun createPlaylist(name: String, songIds: List<String>): Playlist {
        val p = LocalPlaylist("pl" + hash(name + System.nanoTime()), name, songIds, System.currentTimeMillis())
        state = state.copy(playlists = state.playlists + p); save()
        return playlistModel(p, true)
    }

    @Synchronized fun updatePlaylist(id: String, name: String?, add: List<String>, removeIndexes: List<Int>) {
        state = state.copy(playlists = state.playlists.map { p ->
            if (p.id != id) p else {
                val kept = p.songIds.filterIndexed { i, _ -> i !in removeIndexes.toSet() }
                p.copy(name = name ?: p.name, songIds = kept + add)
            }
        })
        save()
    }

    @Synchronized fun deletePlaylist(id: String) { state = state.copy(playlists = state.playlists.filter { it.id != id }); save() }

    // ---------------------------------------------------------------- archivos

    /** Portada de un álbum (la de su primera canción que traiga) o null. */
    fun cover(coverId: String): ByteArray? {
        val t = albumsGrouped()[coverId]?.sortedBy { if (it.hasArt) 0 else 1 }?.firstOrNull() ?: return null
        return source.cover(t.key)
    }

    companion object {
        fun mime(suffix: String) = when (suffix.lowercase()) {
            "flac" -> "audio/flac"; "mp3" -> "audio/mpeg"; "m4a", "alac", "aac" -> "audio/mp4"; "ogg", "oga" -> "audio/ogg"
            "opus" -> "audio/opus"; "wav" -> "audio/wav"; "aif", "aiff" -> "audio/aiff"; "wma" -> "audio/x-ms-wma"
            "dsf", "dff" -> "audio/dsd"; "ape" -> "audio/ape"; "wv" -> "audio/wavpack"; else -> "application/octet-stream"
        }

        val AUDIO_EXTENSIONS = setOf("flac", "mp3", "m4a", "aac", "alac", "ogg", "oga", "opus", "wav", "aif", "aiff", "wma", "dsf", "dff", "ape", "wv")
    }
}
