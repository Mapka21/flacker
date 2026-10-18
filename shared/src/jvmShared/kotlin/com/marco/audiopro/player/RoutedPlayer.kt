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
    /** Transmitir a la tele (Chromecast, Roku, DLNA): mientras está activo, todo va al reproductor de la tele. */
    private val cast: com.marco.audiopro.cast.CastService? = null,
) : PlayerController {

    private val target get() = connect.controlling.value
    private val casting: PlayerController? get() = cast?.takeIf { it.active.value != null }?.player
    private val castOn = cast?.active ?: kotlinx.coroutines.flow.MutableStateFlow(null)
    private val castState = cast?.player?.state ?: kotlinx.coroutines.flow.MutableStateFlow(PlaybackState())

    override val state: StateFlow<PlaybackState> =
        combine(local.state, connect.controlling, connect.remote, castOn, castState) { l, dev, r, tv, tvState ->
            if (tv != null) tvState
            else if (dev == null) l
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
        combine(lv, connect.controlling, connect.remote, castOn, cast?.player?.volume ?: lv) { l, dev, r, tv, tvVol ->
            if (tv != null) tvVol else if (dev != null) r?.first?.volume ?: l else l
        }
            .stateIn(scope, SharingStarted.Eagerly, lv.value)
    }

    override fun setVolume(v: Float) = if (casting != null) casting!!.setVolume(v) else if (target != null) connect.remoteVolume(v) else local.setVolume(v)

    private fun remote(cmd: String, index: Int = 0, positionMs: Long = 0, songs: List<Song> = emptyList()) {
        val d = target ?: return
        scope.launch { connect.remote(d, cmd, index, positionMs, songs) }
    }

    override fun play(songs: List<Song>, startIndex: Int, shuffle: Boolean) {
        casting?.let { return it.play(songs, startIndex, shuffle) }
        if (target == null) return local.play(songs, startIndex, shuffle)
        if (songs.isEmpty()) return
        val list = if (shuffle) songs.shuffled() else songs
        remote("play", if (shuffle) 0 else startIndex.coerceIn(0, list.lastIndex), 0, list)
    }

    override fun playFrom(songs: List<Song>, index: Int, positionMs: Long) =
        if (casting != null) casting!!.playFrom(songs, index, positionMs) else if (target == null) local.playFrom(songs, index, positionMs) else remote("play", index, positionMs, songs)

    override fun playNext(songs: List<Song>) = if (casting != null) casting!!.playNext(songs) else if (target == null) local.playNext(songs) else remote("playNext", songs = songs)
    override fun enqueue(songs: List<Song>) = if (casting != null) casting!!.enqueue(songs) else if (target == null) local.enqueue(songs) else remote("enqueue", songs = songs)
    override fun toggle() = if (casting != null) casting!!.toggle() else if (target == null) local.toggle() else remote("toggle")
    override fun next() = if (casting != null) casting!!.next() else if (target == null) local.next() else remote("next")
    override fun previous() = if (casting != null) casting!!.previous() else if (target == null) local.previous() else remote("previous")
    override fun seekTo(ms: Long) = if (casting != null) casting!!.seekTo(ms) else if (target == null) local.seekTo(ms) else remote("seek", positionMs = ms)
    override fun skipToIndex(i: Int) = if (casting != null) casting!!.skipToIndex(i) else if (target == null) local.skipToIndex(i) else remote("skip", index = i)
    override fun removeAt(i: Int) = if (casting != null) casting!!.removeAt(i) else if (target == null) local.removeAt(i) else remote("remove", index = i)
    override fun toggleShuffle() = if (casting != null) casting!!.toggleShuffle() else if (target == null) local.toggleShuffle() else remote("shuffle")
    override fun cycleRepeat() = if (casting != null) casting!!.cycleRepeat() else if (target == null) local.cycleRepeat() else remote("repeat")

    /** En remoto: la última posición reportada + lo que ha pasado desde entonces. */
    override fun positionMs(): Long {
        casting?.let { return it.positionMs() }
        if (target == null) return local.positionMs()
        val (st, at) = connect.remote.value ?: return 0
        val p = if (st.playing) st.positionMs + (System.currentTimeMillis() - at) else st.positionMs
        return if (st.durationMs > 0) p.coerceAtMost(st.durationMs) else p
    }

    /** Mandar lo que suena aquí a la tele. Devuelve el error para mostrar, o null. */
    suspend fun castTo(d: com.marco.audiopro.cast.CastDevice): String? {
        val c = cast ?: return "No disponible"
        val st = local.state.value
        if (st.queue.isEmpty()) return "Pon una canción primero"
        val pos = local.positionMs()
        if (st.isPlaying) local.toggle()
        return c.start(d, st.queue, st.currentIndex.coerceAtLeast(0), pos)
    }

    /** Dejar de transmitir y seguir aquí donde iba la tele. */
    suspend fun stopCasting() {
        val at = cast?.stop() ?: return
        if (at.first.isNotEmpty()) local.playFrom(at.first, at.second.coerceAtLeast(0), at.third)
    }
}
