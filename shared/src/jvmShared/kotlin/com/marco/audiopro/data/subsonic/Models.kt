package com.marco.audiopro.data.subsonic

import kotlinx.serialization.Serializable

@Serializable
data class ReplayGain(
    val trackGain: Float? = null,
    val albumGain: Float? = null,
    val trackPeak: Float? = null,
    val albumPeak: Float? = null,
)

@Serializable
data class Song(
    val id: String,
    val title: String = "",
    val album: String? = null,
    val albumId: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val track: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val coverArt: String? = null,
    val size: Long? = null,
    val suffix: String? = null,
    val contentType: String? = null,
    val duration: Int? = null,
    val bitRate: Int? = null,
    val samplingRate: Int? = null,
    val bitDepth: Int? = null,
    val channelCount: Int? = null,
    val replayGain: ReplayGain? = null,
    val starred: String? = null,
    val playCount: Long? = null,
    /** Créditos (OpenSubsonic): composición, producción, mezcla… si los archivos los traen. */
    val contributors: List<Contributor>? = null,
    val displayComposer: String? = null,
    /** Muestra de 30 s de una canción que no tienes (Descubrir); se reproduce desde esta URL. */
    val externalUrl: String? = null,
) {
    val isPreview: Boolean get() = externalUrl != null
    /** Estación de radio en vivo (sin duración). */
    val isRadio: Boolean get() = id.startsWith("radio:")

    /** "FLAC · 24/96" */
    val formatLabel: String
        get() = buildString {
            append((suffix ?: "?").uppercase())
            if (bitDepth != null && bitDepth > 0 && samplingRate != null && samplingRate > 0) {
                append(" · ").append(bitDepth).append('/').append(khz(samplingRate))
            } else if (bitRate != null && bitRate > 0) {
                append(" · ").append(bitRate).append(" kbps")
            }
        }

    val isHiRes: Boolean get() = (bitDepth ?: 0) > 16 || (samplingRate ?: 0) > 48000
    val isLossless: Boolean get() = suffix?.lowercase() in setOf("flac", "alac", "wav", "aiff", "aif", "ape", "wv", "dsf", "dff")
}

fun khz(hz: Int): String {
    val k = hz / 1000.0
    return if (k == k.toInt().toDouble()) k.toInt().toString() else "%.1f".format(k)
}

@Serializable
data class NamedItem(val name: String = "")

@Serializable
data class ItemDate(val year: Int? = null, val month: Int? = null, val day: Int? = null)

@Serializable
data class NamedArtist(val id: String = "", val name: String = "")

/** Quién hizo qué en una canción ("producer", "engineer", "mixer", "composer"…). */
@Serializable
data class Contributor(val role: String = "", val subRole: String? = null, val artist: NamedArtist = NamedArtist())

@Serializable
data class Album(
    val id: String,
    val name: String = "",
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val playCount: Long? = null,
    val year: Int? = null,
    val genre: String? = null,
    val starred: String? = null,
    val created: String? = null,
    val recordLabels: List<NamedItem>? = null,
    val genres: List<NamedItem>? = null,
    val releaseDate: ItemDate? = null,
    val originalReleaseDate: ItemDate? = null,
    val releaseTypes: List<String>? = null,
    val isCompilation: Boolean? = null,
    val version: String? = null,
    val moods: List<String>? = null,
    val played: String? = null,
    val song: List<Song> = emptyList(),
)

@Serializable
data class Artist(
    val id: String,
    val name: String = "",
    val albumCount: Int? = null,
    val coverArt: String? = null,
    val artistImageUrl: String? = null,
    val starred: String? = null,
    val album: List<Album> = emptyList(),
)

@Serializable
data class ArtistIndex(val name: String = "", val artist: List<Artist> = emptyList())

@Serializable
data class ArtistsID3(val index: List<ArtistIndex> = emptyList())

@Serializable
data class ArtistInfo(
    val biography: String? = null,
    val lastFmUrl: String? = null,
    val largeImageUrl: String? = null,
    val similarArtist: List<Artist> = emptyList(),
)

@Serializable
data class AlbumInfo(
    val notes: String? = null,
    val lastFmUrl: String? = null,
    val musicBrainzId: String? = null,
    val largeImageUrl: String? = null,
)

@Serializable
data class AlbumList(val album: List<Album> = emptyList())

@Serializable
data class Genre(val value: String = "", val songCount: Int = 0, val albumCount: Int = 0)

@Serializable
data class Genres(val genre: List<Genre> = emptyList())

@Serializable
data class SearchResult(
    val artist: List<Artist> = emptyList(),
    val album: List<Album> = emptyList(),
    val song: List<Song> = emptyList(),
)

@Serializable
data class Playlist(
    val id: String,
    val name: String = "",
    val songCount: Int? = null,
    val duration: Int? = null,
    val coverArt: String? = null,
    val comment: String? = null,
    val entry: List<Song> = emptyList(),
)

@Serializable
data class Playlists(val playlist: List<Playlist> = emptyList())

@Serializable
data class RandomSongs(val song: List<Song> = emptyList())

@Serializable
data class Lyric(
    val start: Long? = null,
    val value: String = "",
    /** Inicio de cada palabra (ms) si la letra lo trae (LRC mejorado); null si sólo hay tiempo por línea. */
    val words: List<Long>? = null,
)

@Serializable
data class StructuredLyrics(
    val synced: Boolean = false,
    val line: List<Lyric> = emptyList(),
    /** De dónde salió: "Navidrome" o "LRCLIB" (no viene del servidor). */
    val source: String? = null,
)

@Serializable
data class LyricsList(val structuredLyrics: List<StructuredLyrics> = emptyList())
