package com.marco.audiopro.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.marco.audiopro.container
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.player.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Puente UI → PlaybackService vía MediaController. */
class PlayerConnection(private val context: Context) : PlayerController {
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** En el teléfono el volumen va con los botones físicos. */
    override val volume: StateFlow<Float>? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) =
            refresh(player, rebuildQueue = events.contains(Player.EVENT_TIMELINE_CHANGED))
    }
    private var queue: List<Song> = emptyList()

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: run { future = null; return@addListener }
            controller = c
            c.addListener(listener)
            refresh(c)
            pending.forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun with(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null && c.isConnected) action(c) else {
            if (c != null) { controller = null; future = null }
            pending += action
            connect()
        }
    }

    private fun refresh(p: Player, rebuildQueue: Boolean = true) {
        if (rebuildQueue) {
            queue = List(p.mediaItemCount) { p.getMediaItemAt(it).let { m -> m.toSong() ?: Song(m.mediaId) } }
        }
        _state.value = PlaybackState(
            current = p.currentMediaItem?.toSong(),
            currentIndex = p.currentMediaItemIndex,
            isPlaying = p.isPlaying,
            isBuffering = p.playbackState == Player.STATE_BUFFERING,
            durationMs = p.duration.coerceAtLeast(0),
            shuffle = p.shuffleModeEnabled,
            repeat = when (p.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            },
            queue = queue,
            error = p.playerError?.let { "Error de reproducción: ${it.errorCodeName}" },
        )
    }

    private fun items(songs: List<Song>): List<MediaItem> {
        val api = context.container.api
        return songs.map { it.toMediaItem(api) }
    }

    override fun play(songs: List<Song>, startIndex: Int, shuffle: Boolean) {
        if (songs.isEmpty()) return
        val list = if (shuffle) songs.shuffled() else songs
        val start = if (shuffle) 0 else startIndex.coerceIn(0, songs.lastIndex)
        with { c ->
            c.shuffleModeEnabled = false
            c.setMediaItems(items(list), start, 0L)
            c.prepare()
            c.play()
        }
    }

    override fun playFrom(songs: List<Song>, index: Int, positionMs: Long) {
        if (songs.isEmpty()) return
        with { c ->
            c.shuffleModeEnabled = false
            c.setMediaItems(items(songs), index.coerceIn(0, songs.lastIndex), positionMs)
            c.prepare()
            c.play()
        }
    }

    override fun playNext(songs: List<Song>) = with { c ->
        if (c.mediaItemCount == 0) { c.setMediaItems(items(songs)); c.prepare(); c.play() }
        else c.addMediaItems(c.currentMediaItemIndex + 1, items(songs))
    }

    override fun enqueue(songs: List<Song>) = with { c ->
        if (c.mediaItemCount == 0) { c.setMediaItems(items(songs)); c.prepare(); c.play() }
        else c.addMediaItems(items(songs))
    }

    override fun toggle() = with { c ->
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        if (c.isPlaying) c.pause() else c.play()
    }

    override fun next() = with { it.seekToNextMediaItem() }
    override fun previous() = with { c -> if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem() }
    override fun seekTo(ms: Long) = with { it.seekTo(ms) }
    override fun skipToIndex(i: Int) = with { it.seekTo(i, 0L); it.play() }
    override fun removeAt(i: Int) = with { it.removeMediaItem(i) }
    override fun toggleShuffle() = with { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    override fun cycleRepeat() = with {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    override fun positionMs(): Long = controller?.currentPosition ?: 0L
    fun bufferedMs(): Long = controller?.bufferedPosition ?: 0L
}
