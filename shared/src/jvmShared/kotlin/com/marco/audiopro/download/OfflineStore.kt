package com.marco.audiopro.download

import com.marco.audiopro.data.subsonic.Song
import kotlinx.coroutines.flow.StateFlow

enum class OfflineStatus { QUEUED, DOWNLOADING, DONE, FAILED }

/** Una canción descargada (o descargándose), con su metadata guardada para usarla sin conexión. */
data class OfflineSong(val song: Song, val status: OfflineStatus, val percent: Float, val bytes: Long) {
    val done get() = status == OfflineStatus.DONE
    val active get() = status == OfflineStatus.QUEUED || status == OfflineStatus.DOWNLOADING
    val failed get() = status == OfflineStatus.FAILED
}

interface OfflineStore {
    /** Por id de canción. */
    val items: StateFlow<Map<String, OfflineSong>>
    fun download(songs: List<Song>)
    fun remove(songs: List<Song>)
    /** Refresca el porcentaje de lo que se está bajando (llamar ~1 vez por segundo). */
    fun pollProgress()
    fun freeBytes(): Long
}
