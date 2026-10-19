package com.marco.audiopro.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.DefaultAudioTrackBufferSizeProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.marco.audiopro.MainActivity
import com.marco.audiopro.R
import com.marco.audiopro.audio.AnalyzerTap
import com.marco.audiopro.audio.SignalInfo
import com.marco.audiopro.audio.dbToLinear
import com.marco.audiopro.audio.replayGainFor
import com.marco.audiopro.container
import com.marco.audiopro.data.StreamQuality
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val scope = MainScope()
    private val resolvedTransport = HashMap<String, String>()
    private var scrobbledId: String? = null

    override fun onCreate() {
        super.onCreate()
        val c = container

        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(c.eqProcessor, c.tap))
                .setAudioTrackBufferSizeProvider(
                    // Buffer fijo => latencia conocida => visualizadores en sync.
                    DefaultAudioTrackBufferSizeProvider.Builder()
                        .setMinPcmBufferDurationUs(AnalyzerTap.SINK_BUFFER_MS * 1000)
                        .setMaxPcmBufferDurationUs(AnalyzerTap.SINK_BUFFER_MS * 1000)
                        .build(),
                )
                .build()
        }

        // Descargas (sólo lectura) → caché LRU de streaming → red.
        val upstream = DefaultDataSource.Factory(this, OkHttpDataSource.Factory(c.streamHttp))
        val streamCache = CacheDataSource.Factory()
            .setCache(c.mediaCache)
            .setUpstreamDataSourceFactory(upstream)
            // La radio en vivo no tiene tamaño: no se guarda en la caché (no termina nunca).
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR or CacheDataSource.FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS)
        val dataSource = CacheDataSource.Factory()
            .setCache(c.downloadCache)
            .setUpstreamDataSourceFactory(streamCache)
            .setCacheWriteDataSinkFactory(null)

        val player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply { setShuffleOrder(NextUpShuffleOrder()) }

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                applyReplayGain(player)
                mediaItem?.mediaId?.let { id -> scope.launch { c.api.scrobble(id, submission = false) } }
            }
        })

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> =
                    Futures.immediateFuture(mediaItems.map(::resolve).toMutableList())
            })
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_stat_music) },
        )

        // Re-aplica ReplayGain si cambian los ajustes.
        scope.launch { c.settings.prefs.collect { applyReplayGain(player) } }

        // Scrobble al pasar la mitad de la canción (o 4 min), como Last.fm.
        scope.launch {
            while (true) {
                delay(5000)
                val id = player.currentMediaItem?.mediaId ?: continue
                val dur = player.duration
                if (id != scrobbledId && player.isPlaying && dur > 0 && player.currentPosition > min(dur / 2, 240_000L)) {
                    scrobbledId = id
                    c.api.scrobble(id, submission = true)
                }
            }
        }
    }

    /** La UI manda MediaItems sin URI (no cruzan el IPC): aquí se arma la URL de stream. */
    private fun resolve(item: MediaItem): MediaItem {
        item.toSong()?.externalUrl?.let { url ->
            resolvedTransport[item.mediaId] = if (item.mediaId.startsWith("radio:")) "Radio en vivo" else "Muestra de 30 s (Deezer, MP3)"
            return item.buildUpon().setUri(url).build()
        }
        container.downloads.completed(item.mediaId)?.let { req ->
            val q = req.customCacheKey?.substringAfter(':')?.let { runCatching { StreamQuality.valueOf(it) }.getOrNull() }
            resolvedTransport[item.mediaId] = "Descargado · ${q?.label ?: "local"}"
            return item.buildUpon().setUri(req.uri).setCustomCacheKey(req.customCacheKey).build()
        }
        val q = container.currentQuality()
        resolvedTransport[item.mediaId] = q.label
        return item.buildUpon()
            .setUri(container.api.streamUrl(item.mediaId, q))
            .setCustomCacheKey("${item.mediaId}:${q.name}")
            .build()
    }

    private fun applyReplayGain(player: Player) {
        val item = player.currentMediaItem
        val transport = item?.let { resolvedTransport[it.mediaId] } ?: ""
        // El volumen de ExoPlayer no amplifica: replayGainFor ya devuelve siempre ≤ 0 dB.
        val rg = replayGainFor(item?.toSong(), container.settings.prefs.value)
        player.volume = rg?.let { dbToLinear(it.first) } ?: 1f
        container.engine.signal.value = SignalInfo(rg?.first, rg?.second, transport, OUTPUT_LABEL)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    private companion object {
        const val OUTPUT_LABEL = "AudioTrack de Android → DAC del teléfono / Bluetooth / USB"
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
