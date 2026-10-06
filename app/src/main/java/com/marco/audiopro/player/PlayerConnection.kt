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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackState(
    val current: Song? = null,
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Song> = emptyList(),
    val error: String? = null,
)

/** Puente UI → PlaybackService vía MediaController. */
class PlayerConnection(private val context: Context) {
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

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
            repeat = p.repeatMode,
            queue = queue,
            error = p.playerError?.let { "Error de reproducción: ${it.errorCodeName}" },
        )
    }

    private fun items(songs: List<Song>): List<MediaItem> {
        val api = context.container.api
        return songs.map { it.toMediaItem(api) }
    }

    fun play(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
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

    fun playNext(songs: List<Song>) = with { c ->
        if (c.mediaItemCount == 0) { c.setMediaItems(items(songs)); c.prepare(); c.play() }
        else c.addMediaItems(c.currentMediaItemIndex + 1, items(songs))
    }

    fun enqueue(songs: List<Song>) = with { c ->
        if (c.mediaItemCount == 0) { c.setMediaItems(items(songs)); c.prepare(); c.play() }
        else c.addMediaItems(items(songs))
    }

    fun toggle() = with { c ->
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = with { it.seekToNextMediaItem() }
    fun previous() = with { c -> if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) = with { it.seekTo(ms) }
    fun skipToIndex(i: Int) = with { it.seekTo(i, 0L); it.play() }
    fun removeAt(i: Int) = with { it.removeMediaItem(i) }
    fun move(from: Int, to: Int) = with { it.moveMediaItem(from, to) }
    fun toggleShuffle() = with { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun cycleRepeat() = with {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun positionMs(): Long = controller?.currentPosition ?: 0L
    fun bufferedMs(): Long = controller?.bufferedPosition ?: 0L
}
