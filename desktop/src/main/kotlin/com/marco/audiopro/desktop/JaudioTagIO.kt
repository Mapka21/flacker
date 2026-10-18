package com.marco.audiopro.desktop

import com.marco.audiopro.data.local.LocalLibrary
import com.marco.audiopro.data.meta.FixAlbum
import com.marco.audiopro.data.meta.FixTrack
import com.marco.audiopro.data.meta.NewTags
import com.marco.audiopro.data.meta.TagIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/** Etiquetas en la PC con jaudiotagger (FLAC, MP3, M4A/ALAC, OGG, WAV, AIFF, DSF…). */
class JaudioTagIO : TagIO {
    // Hay que guardar el logger: si no, Java lo suelta y vuelve a imprimir una línea por cada bloque de cada archivo.
    private companion object { val quiet: Logger = Logger.getLogger("org.jaudiotagger").apply { level = Level.OFF } }
    init { quiet.level = Level.OFF }

    private fun Tag?.get(k: FieldKey): String? = runCatching { this?.getFirst(k) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    private fun Tag?.all(k: FieldKey): List<String> = runCatching { this?.getAll(k) }.getOrNull().orEmpty().map { it.trim() }.filter { it.isNotEmpty() }

    override suspend fun scan(folders: List<String>, progress: (Int) -> Unit): List<FixAlbum> = withContext(Dispatchers.IO) {
        val tracks = ArrayList<FixTrack>()
        folders.map(::File).filter { it.isDirectory }.forEach { root ->
            root.walkTopDown().onFail { _, _ -> }.filter { it.isFile && it.extension.lowercase() in LocalLibrary.AUDIO_EXTENSIONS }.forEach { f ->
                tracks += readTrack(f)
                if (tracks.size % 20 == 0) progress(tracks.size)
            }
        }
        progress(tracks.size)
        // Un disco = misma carpeta y mismo álbum (los discos 1 y 2 en subcarpetas se juntan por álbum y artista).
        tracks.groupBy { t ->
            val folder = File(t.path).parentFile
            val base = if (folder.name.matches(Regex("(?i)(cd|disc|disk|disco)\\s*\\d+"))) folder.parentFile else folder
            base.path + "|" + (t.album ?: base.name)
        }.map { (k, ts) ->
            val first = ts.first()
            FixAlbum(
                folder = k.substringBefore('|'), album = first.album ?: File(k.substringBefore('|')).name,
                artist = ts.mapNotNull { it.albumArtist ?: it.artist }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "",
                tracks = ts,
            )
        }.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }))
    }

    private fun readTrack(f: File): FixTrack {
        val lrc = listOf("lrc", "LRC").any { File(f.parentFile, f.nameWithoutExtension + "." + it).isFile }
        return runCatching {
            val af = AudioFileIO.read(f)
            val tag = af.tag
            FixTrack(
                path = f.absolutePath, title = tag.get(FieldKey.TITLE), artist = tag.get(FieldKey.ARTIST), albumArtist = tag.get(FieldKey.ALBUM_ARTIST),
                album = tag.get(FieldKey.ALBUM), track = tag.get(FieldKey.TRACK)?.substringBefore('/')?.toIntOrNull(),
                disc = tag.get(FieldKey.DISC_NO)?.substringBefore('/')?.toIntOrNull(), year = tag.get(FieldKey.YEAR)?.take(4)?.toIntOrNull(),
                genre = tag.all(FieldKey.GENRE).joinToString("; ").ifBlank { null },
                durationMs = af.audioHeader.preciseTrackLength.times(1000).toLong(),
                hasCover = runCatching { tag?.firstArtwork != null }.getOrDefault(false),
                hasLyrics = lrc || tag.get(FieldKey.LYRICS) != null, format = f.extension.uppercase(),
                mbReleaseId = tag.get(FieldKey.MUSICBRAINZ_RELEASEID),
            )
        }.getOrDefault(FixTrack(path = f.absolutePath, title = f.nameWithoutExtension, album = f.parentFile?.name, format = f.extension.uppercase(), hasLyrics = lrc))
    }

    override suspend fun read(path: String): NewTags = withContext(Dispatchers.IO) {
        val tag = AudioFileIO.read(File(path)).tag
        NewTags(
            title = tag.get(FieldKey.TITLE), artist = tag.get(FieldKey.ARTIST), albumArtist = tag.get(FieldKey.ALBUM_ARTIST), album = tag.get(FieldKey.ALBUM),
            track = tag.get(FieldKey.TRACK)?.substringBefore('/')?.toIntOrNull(), trackTotal = tag.get(FieldKey.TRACK_TOTAL)?.toIntOrNull(),
            disc = tag.get(FieldKey.DISC_NO)?.substringBefore('/')?.toIntOrNull(), discTotal = tag.get(FieldKey.DISC_TOTAL)?.toIntOrNull(),
            year = tag.get(FieldKey.YEAR)?.take(4)?.toIntOrNull(), genres = tag.all(FieldKey.GENRE),
            mbReleaseId = tag.get(FieldKey.MUSICBRAINZ_RELEASEID), mbRecordingId = tag.get(FieldKey.MUSICBRAINZ_TRACK_ID),
        )
    }

    override suspend fun cover(path: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { AudioFileIO.read(File(path)).tag?.firstArtwork?.binaryData }.getOrNull()
    }

    override suspend fun write(path: String, tags: NewTags, cover: ByteArray?, exact: Boolean, removeCover: Boolean) = withContext(Dispatchers.IO) {
        val af = AudioFileIO.read(File(path))
        val tag = af.tagOrCreateAndSetDefault
        fun set(k: FieldKey, v: Any?) {
            if (v == null) { if (exact) runCatching { tag.deleteField(k) }; return }
            runCatching { tag.setField(k, v.toString()) }
        }
        set(FieldKey.TITLE, tags.title)
        set(FieldKey.ARTIST, tags.artist)
        set(FieldKey.ALBUM_ARTIST, tags.albumArtist)
        set(FieldKey.ALBUM, tags.album)
        set(FieldKey.TRACK, tags.track)
        set(FieldKey.TRACK_TOTAL, tags.trackTotal)
        set(FieldKey.DISC_NO, tags.disc)
        set(FieldKey.DISC_TOTAL, tags.discTotal)
        set(FieldKey.YEAR, tags.year)
        set(FieldKey.MUSICBRAINZ_RELEASEID, tags.mbReleaseId)
        set(FieldKey.MUSICBRAINZ_TRACK_ID, tags.mbRecordingId)
        // Varios géneros: un campo por género (Navidrome los lee todos).
        val g = tags.genres
        if (g != null || exact) {
            runCatching { tag.deleteField(FieldKey.GENRE) }
            g.orEmpty().forEach { runCatching { tag.addField(FieldKey.GENRE, it) } }
        }
        if (cover != null) {
            val tmp = File.createTempFile("flacker-cover", ".jpg").apply { writeBytes(cover); deleteOnExit() }
            val art = ArtworkFactory.createArtworkFromFile(tmp)
            runCatching { tag.deleteArtworkField() }
            tag.setField(art)
            tmp.delete()
        } else if (removeCover) runCatching { tag.deleteArtworkField() }
        af.commit()
    }
}
