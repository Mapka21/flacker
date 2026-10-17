package com.marco.audiopro.local

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import com.marco.audiopro.data.local.LocalSource
import com.marco.audiopro.data.local.LocalTrack
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * La música guardada en el celular (sin servidor), leída de MediaStore: título, artista, álbum,
 * número, año, género y duración; la portada con la miniatura del sistema o la incrustada.
 */
class MediaStoreSource(private val context: Context) : LocalSource {
    override val label = "La música de este celular"

    override suspend fun scan(previous: Map<String, LocalTrack>, progress: (Int) -> Unit): List<LocalTrack> {
        val out = ArrayList<LocalTrack>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cols = buildList {
            add(MediaStore.Audio.Media._ID); add(MediaStore.Audio.Media.TITLE); add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM); add(MediaStore.Audio.Media.TRACK); add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DURATION); add(MediaStore.Audio.Media.SIZE); add(MediaStore.Audio.Media.DATE_MODIFIED)
            add(MediaStore.Audio.Media.DATE_ADDED); add(MediaStore.Audio.Media.DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= 30) { add(MediaStore.Audio.Media.ALBUM_ARTIST); add(MediaStore.Audio.Media.GENRE); add(MediaStore.Audio.Media.BITRATE) }
        }.toTypedArray()
        context.contentResolver.query(uri, cols, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null)?.use { c ->
            fun str(col: String) = c.getColumnIndex(col).takeIf { it >= 0 }?.let { c.getString(it) }?.takeIf { it.isNotBlank() && it != "<unknown>" }
            fun long(col: String) = c.getColumnIndex(col).takeIf { it >= 0 }?.let { if (c.isNull(it)) null else c.getLong(it) }
            while (c.moveToNext()) {
                val id = long(MediaStore.Audio.Media._ID) ?: continue
                val key = ContentUris.withAppendedId(uri, id).toString()
                val size = long(MediaStore.Audio.Media.SIZE) ?: 0
                val modified = (long(MediaStore.Audio.Media.DATE_MODIFIED) ?: 0) * 1000
                val old = previous[key]
                if (old != null && old.size == size && old.modified == modified) { out += old; continue }
                val trackNo = long(MediaStore.Audio.Media.TRACK)?.toInt() ?: 0
                val name = str(MediaStore.Audio.Media.DISPLAY_NAME) ?: ""
                out += LocalTrack(
                    key = key,
                    title = str(MediaStore.Audio.Media.TITLE) ?: name.substringBeforeLast('.'),
                    artist = str(MediaStore.Audio.Media.ARTIST),
                    albumArtist = if (Build.VERSION.SDK_INT >= 30) str(MediaStore.Audio.Media.ALBUM_ARTIST) else null,
                    album = str(MediaStore.Audio.Media.ALBUM),
                    // TRACK viene como disco*1000 + pista.
                    track = (trackNo % 1000).takeIf { it > 0 }, disc = (trackNo / 1000).takeIf { it > 0 },
                    year = long(MediaStore.Audio.Media.YEAR)?.toInt()?.takeIf { it > 0 },
                    genre = if (Build.VERSION.SDK_INT >= 30) str(MediaStore.Audio.Media.GENRE) else null,
                    duration = ((long(MediaStore.Audio.Media.DURATION) ?: 0) / 1000).toInt(),
                    bitRate = if (Build.VERSION.SDK_INT >= 30) long(MediaStore.Audio.Media.BITRATE)?.let { (it / 1000).toInt() } else null,
                    suffix = name.substringAfterLast('.', "").lowercase(),
                    size = size, modified = modified, hasArt = true,
                    addedAt = (long(MediaStore.Audio.Media.DATE_ADDED) ?: 0) * 1000,
                )
                if (out.size % 50 == 0) progress(out.size)
            }
        }
        progress(out.size)
        return out
    }

    override fun open(key: String): InputStream? = runCatching { context.contentResolver.openInputStream(Uri.parse(key)) }.getOrNull()

    override fun cover(key: String): ByteArray? {
        val uri = Uri.parse(key)
        val bmp: Bitmap? = if (Build.VERSION.SDK_INT >= 29) runCatching { context.contentResolver.loadThumbnail(uri, Size(600, 600), null) }.getOrNull() else null
        if (bmp != null) return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        return runCatching {
            MediaMetadataRetriever().run { try { setDataSource(context, uri); embeddedPicture } finally { release() } }
        }.getOrNull()
    }
}
