package com.marco.audiopro.player

import com.marco.audiopro.data.subsonic.Song
import kotlinx.coroutines.flow.StateFlow

enum class RepeatMode { OFF, ALL, ONE }

data class PlaybackState(
    val current: Song? = null,
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val queue: List<Song> = emptyList(),
    val error: String? = null,
)

/** Contrato del reproductor: Media3 en Android, motor propio (ffmpeg + Java Sound) en PC. */
interface PlayerController {
    val state: StateFlow<PlaybackState>

    /** Volumen digital 0..1 en plataformas sin botones físicos (PC); `null` si no aplica. */
    val volume: StateFlow<Float>?
    fun setVolume(v: Float) {}

    fun play(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false)
    /** Reproduce `songs` desde `index` en la posición exacta (para pasar la música entre dispositivos). */
    fun playFrom(songs: List<Song>, index: Int, positionMs: Long)
    fun playNext(songs: List<Song>)
    fun enqueue(songs: List<Song>)
    fun toggle()
    fun next()
    fun previous()
    fun seekTo(ms: Long)
    fun skipToIndex(i: Int)
    fun removeAt(i: Int)
    fun toggleShuffle()
    fun cycleRepeat()
    fun positionMs(): Long
}
