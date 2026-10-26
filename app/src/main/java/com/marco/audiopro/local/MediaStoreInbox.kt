package com.marco.audiopro.local

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.marco.audiopro.share.ShareInbox
import kotlinx.coroutines.delay
import java.io.File

/**
 * Lo que te mandan otros Flacker cae en Música/Flacker/Artista/Álbum del celular (lo ven también
 * las demás apps de música). En Android 9 o antes, en la carpeta de música de la app.
 */
class MediaStoreInbox(private val context: Context) : ShareInbox {
    override val label = "Music/Flacker"

    private fun relative(dirs: List<String>) = (listOf(Environment.DIRECTORY_MUSIC, "Flacker") + dirs).joinToString("/") + "/"
    private fun legacyDir(dirs: List<String>) = dirs.fold(File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Flacker")) { d, s -> File(d, s) }

    override fun has(dirs: List<String>, name: String, size: Long): Boolean {
        if (Build.VERSION.SDK_INT < 29) return File(legacyDir(dirs), name).let { it.isFile && it.length() == size }
        val cols = arrayOf(MediaStore.Audio.Media._ID)
        val where = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.SIZE} = ?"
        return context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols, where, arrayOf(relative(dirs), name, size.toString()), null)
            ?.use { it.count > 0 } ?: false
    }

    override fun save(tmp: File, dirs: List<String>, name: String, mime: String): Boolean {
        if (Build.VERSION.SDK_INT < 29) {
            val target = File(legacyDir(dirs).apply { mkdirs() }, name)
            tmp.copyTo(target, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(target.path), null, null)
            return true
        }
        val r = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative(dirs))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = r.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return false
        return try {
            r.openOutputStream(uri)?.use { out -> tmp.inputStream().use { it.copyTo(out, 64 * 1024) } } ?: error("sin salida")
            r.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            true
        } catch (e: Exception) {
            r.delete(uri, null, null)
            false
        }
    }

    /** Android lee las etiquetas de los archivos nuevos un momento después. */
    override suspend fun afterBatch() = delay(3000)
}
