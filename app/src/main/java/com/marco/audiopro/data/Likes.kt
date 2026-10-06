package com.marco.audiopro.data

import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.SubsonicApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Canciones con "me gusta" (= starred en Navidrome). Una sola fuente de verdad para
 * que el corazón se vea igual en todas las pantallas. Cambios optimistas: si el
 * servidor falla, se revierte.
 */
class Likes(private val api: SubsonicApi, private val scope: CoroutineScope) {
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    var loaded = false
        private set

    suspend fun refresh() {
        val s = api.starred().song
        _songs.value = s
        _ids.value = s.mapTo(HashSet()) { it.id }
        loaded = true
    }

    fun isLiked(id: String) = id in _ids.value

    fun toggle(song: Song) {
        val like = song.id !in _ids.value
        val prevSongs = _songs.value
        val prevIds = _ids.value
        if (like) {
            _songs.value = listOf(song) + prevSongs
            _ids.value = prevIds + song.id
        } else {
            _songs.value = prevSongs.filter { it.id != song.id }
            _ids.value = prevIds - song.id
        }
        scope.launch {
            runCatching { api.star(song.id, like) }.onFailure {
                _songs.value = prevSongs
                _ids.value = prevIds
            }
        }
    }
}
