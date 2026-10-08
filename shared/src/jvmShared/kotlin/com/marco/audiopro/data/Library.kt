package com.marco.audiopro.data

import com.marco.audiopro.data.subsonic.Playlist
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.SubsonicApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Las playlists del servidor como estado compartido: la barra lateral, la biblioteca y el
 * menú "Agregar a playlist" se actualizan solos al crear, renombrar o borrar.
 */
class PlaylistStore(private val api: SubsonicApi) {
    private val _lists = MutableStateFlow<List<Playlist>>(emptyList())
    val lists: StateFlow<List<Playlist>> = _lists.asStateFlow()

    /** Sube cada vez que cambia el contenido de alguna playlist (para recargar la pantalla abierta). */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    suspend fun refresh() { _lists.value = api.playlists() }

    suspend fun create(name: String, songs: List<Song> = emptyList()): Playlist {
        val p = api.createPlaylist(name.trim(), songs.map { it.id })
        refresh()
        return p
    }

    suspend fun add(id: String, songs: List<Song>) {
        api.updatePlaylist(id, add = songs.map { it.id })
        bump()
    }

    suspend fun removeAt(id: String, index: Int) {
        api.updatePlaylist(id, removeIndexes = listOf(index))
        bump()
    }

    suspend fun rename(id: String, name: String) {
        api.updatePlaylist(id, name = name.trim())
        bump()
    }

    suspend fun delete(id: String) {
        api.deletePlaylist(id)
        refresh()
    }

    private suspend fun bump() {
        _version.value++
        runCatching { refresh() }
    }
}

/**
 * Portadas propias para playlists, "Me gusta" y artistas (los álbumes usan la del archivo).
 * Se guardan en este dispositivo; cada cambio es un archivo nuevo para que la caché de
 * imágenes no muestre la anterior.
 */
class CustomCovers(root: File) {
    private val dir = File(root, "custom-covers").apply { mkdirs() }
    private val _map = MutableStateFlow(scan())
    val map: StateFlow<Map<String, File>> = _map.asStateFlow()

    private fun safe(key: String) = key.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun scan(): Map<String, File> =
        (dir.listFiles() ?: emptyArray()).filter { it.isFile }
            .groupBy { it.name.substringBeforeLast("__") }
            .mapValues { (_, files) -> files.maxBy { it.lastModified() } }

    fun get(key: String): File? = _map.value[safe(key)]

    suspend fun set(key: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val k = safe(key)
        dir.listFiles { f -> f.name.startsWith("${k}__") }?.forEach { it.delete() }
        File(dir, "${k}__${System.currentTimeMillis()}.img").writeBytes(bytes)
        _map.value = scan()
    }

    suspend fun clear(key: String) = withContext(Dispatchers.IO) {
        val k = safe(key)
        dir.listFiles { f -> f.name.startsWith("${k}__") }?.forEach { it.delete() }
        _map.value = scan()
    }

    companion object {
        const val LIKED = "liked"
        fun playlist(id: String) = "playlist_$id"
        fun artist(id: String) = "artist_$id"
    }
}
