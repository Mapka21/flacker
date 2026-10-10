package com.marco.audiopro.player

import com.marco.audiopro.connect.ConnectService
import com.marco.audiopro.data.subsonic.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * El reproductor que ve la UI. Normalmente es el de este dispositivo; cuando controlas otro
 * con Connect (como "Escuchando en …" de Spotify) muestra lo que suena allá y todos los
 * botones —play, siguiente, barra de tiempo, volumen, la cola— mandan las órdenes allá.
 */
class RoutedPlayer(
    private val local: PlayerController,
    private val connect: ConnectService,
    private val scope: CoroutineScope,
) : PlayerController {

    private val target get() = connect.controlling.value

    override val state: StateFlow<PlaybackState> =
        combine(local.state, connect.controlling, connect.remote) { l, dev, r ->
            if (dev == null) l
            else {
                val st = r?.first
                if (st == null) PlaybackState(isBuffering = true, remoteName = dev.name)
                else PlaybackState(
                    current = st.queue.getOrNull(st.index),
                    currentIndex = st.index,
                    isPlaying = st.playing,
                    durationMs = st.durationMs,
                    shuffle = st.shuffle,
                    repeat = runCatching { RepeatMode.valueOf(st.repeat) }.getOrDefault(RepeatMode.OFF),
                    queue = st.queue,
                    remoteName = dev.name,
                )
            }
        }.stateIn(scope, SharingStarted.Eagerly, local.state.value)

    override val volume: StateFlow<Float>? = local.volume?.let { lv ->
        combine(lv, connect.controlling, connect.remote) { l, dev, r -> if (dev != null) r?.first?.volume ?: l else l }
            .stateIn(scope, SharingStarted.Eagerly, lv.value)
    }

    override fun setVolume(v: Float) = if (target != null) connect.remoteVolume(v) else local.setVolume(v)

    private fun remote(cmd: String, index: Int = 0, positionMs: Long = 0, songs: List<Song> = emptyList()) {
        val d = target ?: return
        scope.launch { connect.remote(d, cmd, index, positionMs, songs) }
    }

    override fun play(songs: List<Song>, startIndex: Int, shuffle: Boolean) {
        if (target == null) return local.play(songs, startIndex, shuffle)
        if (songs.isEmpty()) return
        val list = if (shuffle) songs.shuffled() else songs
        remote("play", if (shuffle) 0 else startIndex.coerceIn(0, list.lastIndex), 0, list)
    }

    override fun playFrom(songs: List<Song>, index: Int, positionMs: Long) =
        if (target == null) local.playFrom(songs, index, positionMs) else remote("play", index, positionMs, songs)

    override fun playNext(songs: List<Song>) = if (target == null) local.playNext(songs) else remote("playNext", songs = songs)
    override fun enqueue(songs: List<Song>) = if (target == null) local.enqueue(songs) else remote("enqueue", songs = songs)
    override fun toggle() = if (target == null) local.toggle() else remote("toggle")
    override fun next() = if (target == null) local.next() else remote("next")
    override fun previous() = if (target == null) local.previous() else remote("previous")
    override fun seekTo(ms: Long) = if (target == null) local.seekTo(ms) else remote("seek", positionMs = ms)
    override fun skipToIndex(i: Int) = if (target == null) local.skipToIndex(i) else remote("skip", index = i)
    override fun removeAt(i: Int) = if (target == null) local.removeAt(i) else remote("remove", index = i)
    override fun toggleShuffle() = if (target == null) local.toggleShuffle() else remote("shuffle")
    override fun cycleRepeat() = if (target == null) local.cycleRepeat() else remote("repeat")

    /** En remoto: la última posición reportada + lo que ha pasado desde entonces. */
    override fun positionMs(): Long {
        if (target == null) return local.positionMs()
        val (st, at) = connect.remote.value ?: return 0
        val p = if (st.playing) st.positionMs + (System.currentTimeMillis() - at) else st.positionMs
        return if (st.durationMs > 0) p.coerceAtMost(st.durationMs) else p
    }
}
