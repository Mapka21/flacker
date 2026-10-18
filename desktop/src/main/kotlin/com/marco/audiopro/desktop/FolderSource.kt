package com.marco.audiopro.desktop

import com.marco.audiopro.data.local.LocalLibrary
import com.marco.audiopro.data.local.LocalSource
import com.marco.audiopro.data.local.LocalTrack
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Música de carpetas de la PC (sin servidor). Lee los tags con jaudiotagger (título, artista,
 * álbum, número, disco, año, género, calidad, portada y letra) y busca cover.jpg / folder.jpg y
 * .lrc al lado. Si un archivo no tiene tags, usa el nombre del archivo y de sus carpetas.
 */
class FolderSource(private val folders: () -> List<File>) : LocalSource {
    // Hay que guardar el logger: si no, Java lo suelta y vuelve a imprimir una línea por cada bloque de cada archivo.
    private companion object { val quiet: Logger = Logger.getLogger("org.jaudiotagger").apply { level = Level.OFF } }
    init { quiet.level = Level.OFF }

    override val label: String get() = folders().joinToString(" · ") { it.path }

    override suspend fun scan(previous: Map<String, LocalTrack>, progress: (Int) -> Unit): List<LocalTrack> {
        val out = ArrayList<LocalTrack>()
        val now = System.currentTimeMillis()
        folders().filter { it.isDirectory }.forEach { root ->
            root.walkTopDown().onFail { _, _ -> }.filter { it.isFile && it.extension.lowercase() in LocalLibrary.AUDIO_EXTENSIONS }.forEach { f ->
                val key = f.absolutePath
                val old = previous[key]
                out += if (old != null && old.size == f.length() && old.modified == f.lastModified()) old
                else read(f, old?.addedAt ?: now)
                if (out.size % 25 == 0) progress(out.size)
            }
        }
        progress(out.size)
        return out
    }

    private fun Tag?.get(k: FieldKey): String? = runCatching { this?.getFirst(k) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    private fun read(f: File, addedAt: Long): LocalTrack {
        val lrc = sidecar(f)
        val fallback = LocalTrack(
            key = f.absolutePath, title = f.nameWithoutExtension, artist = f.parentFile?.parentFile?.name, album = f.parentFile?.name,
            suffix = f.extension.lowercase(), size = f.length(), modified = f.lastModified(), hasArt = folderArt(f) != null,
            lyrics = lrc, addedAt = addedAt,
        )
        return runCatching {
            val af = AudioFileIO.read(f)
            val h = af.audioHeader
            val tag = af.tag
            LocalTrack(
                key = f.absolutePath,
                title = tag.get(FieldKey.TITLE) ?: f.nameWithoutExtension,
                artist = tag.get(FieldKey.ARTIST) ?: fallback.artist,
                albumArtist = tag.get(FieldKey.ALBUM_ARTIST),
                album = tag.get(FieldKey.ALBUM) ?: fallback.album,
                track = tag.get(FieldKey.TRACK)?.substringBefore('/')?.toIntOrNull(),
                disc = tag.get(FieldKey.DISC_NO)?.substringBefore('/')?.toIntOrNull(),
                year = tag.get(FieldKey.YEAR)?.take(4)?.toIntOrNull(),
                genre = tag.get(FieldKey.GENRE),
                duration = h.trackLength,
                bitRate = h.bitRateAsNumber.toInt().takeIf { it > 0 },
                sampleRate = h.sampleRateAsNumber.takeIf { it > 0 },
                bitDepth = h.bitsPerSample.takeIf { it > 0 },
                channels = h.channels?.let { c -> c.toIntOrNull() ?: if (c.contains("mono", true)) 1 else 2 },
                suffix = f.extension.lowercase(),
                size = f.length(), modified = f.lastModified(),
                hasArt = runCatching { tag?.firstArtwork != null }.getOrDefault(false) || folderArt(f) != null,
                lyrics = lrc ?: tag.get(FieldKey.LYRICS),
                addedAt = addedAt,
            )
        }.getOrDefault(fallback)
    }

    /** Letra en un .lrc con el mismo nombre (o .txt). */
    private fun sidecar(f: File): String? = listOf("lrc", "LRC", "txt").map { File(f.parentFile, f.nameWithoutExtension + "." + it) }
        .firstOrNull { it.isFile && it.length() < 200_000 }?.let { runCatching { it.readText() }.getOrNull() }

    /** cover.jpg, folder.jpg, front.png… en la carpeta del archivo. */
    private fun folderArt(f: File): File? {
        val files = f.parentFile?.listFiles() ?: return null
        val images = files.filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png") }
        val names = listOf("cover", "folder", "front", "albumart", "album")
        return images.firstOrNull { img -> names.any { img.nameWithoutExtension.equals(it, true) } }
            ?: images.firstOrNull { img -> names.any { img.nameWithoutExtension.lowercase().startsWith(it) } }
            ?: images.singleOrNull()
    }

    override fun open(key: String): InputStream? = File(key).takeIf { it.isFile }?.let { FileInputStream(it) }

    override fun cover(key: String): ByteArray? {
        val f = File(key)
        val embedded = runCatching { AudioFileIO.read(f).tag?.firstArtwork?.binaryData }.getOrNull()
        return embedded ?: folderArt(f)?.readBytes()
    }
}
