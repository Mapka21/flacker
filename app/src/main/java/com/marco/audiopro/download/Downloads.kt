package com.marco.audiopro.download

import android.content.Context
import android.net.Uri
import android.os.StatFs
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.marco.audiopro.data.Settings
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.data.subsonic.SubsonicJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

/**
 * Descargas offline. Cada request lleva el Song completo en `data`, así la pantalla de
 * descargas y la reproducción funcionan aunque el servidor no esté accesible.
 * Se reproducen solas: el PlaybackService lee primero de la caché de descargas.
 */
class Downloads(
    private val context: Context,
    val manager: DownloadManager,
    private val api: SubsonicApi,
    private val settings: Settings,
    private val scope: CoroutineScope,
) : OfflineStore {
    private val _items = MutableStateFlow<Map<String, OfflineSong>>(emptyMap())
    override val items: StateFlow<Map<String, OfflineSong>> = _items.asStateFlow()

    init {
        val map = LinkedHashMap<String, OfflineSong>()
        manager.downloadIndex.getDownloads().use { c ->
            while (c.moveToNext()) c.download.toOffline()?.let { map[it.song.id] = it }
        }
        _items.value = map
        manager.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(dm: DownloadManager, download: Download, finalException: Exception?) {
                download.toOffline()?.let { _items.value = _items.value + (it.song.id to it) }
            }

            override fun onDownloadRemoved(dm: DownloadManager, download: Download) {
                _items.value = _items.value - download.request.id
            }
        })
        scope.launch {
            settings.prefs.collect { applyRequirements(it.downloadWifiOnly) }
        }
    }

    private fun applyRequirements(wifiOnly: Boolean) {
        manager.requirements = Requirements(if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)
    }

    private fun Download.toOffline(): OfflineSong? {
        val song = request.data.takeIf { it.isNotEmpty() }
            ?.let { runCatching { SubsonicJson.decodeFromString<Song>(Util.fromUtf8Bytes(it)) }.getOrNull() }
            ?: return null
        val status = when (state) {
            Download.STATE_COMPLETED -> OfflineStatus.DONE
            Download.STATE_DOWNLOADING -> OfflineStatus.DOWNLOADING
            Download.STATE_FAILED -> OfflineStatus.FAILED
            else -> OfflineStatus.QUEUED
        }
        return OfflineSong(song, status, percentDownloaded.coerceAtLeast(0f), bytesDownloaded)
    }

    /** Completado → (uri, cacheKey) para que el reproductor lea la copia local. */
    fun completed(songId: String): DownloadRequest? =
        manager.downloadIndex.getDownload(songId)?.takeIf { it.state == Download.STATE_COMPLETED }?.request

    override fun download(songs: List<Song>) {
        val q = settings.prefs.value.downloadQuality
        val current = _items.value
        songs.filter { current[it.id]?.let { o -> o.done || o.active } != true }.forEach { s ->
            val req = DownloadRequest.Builder(s.id, Uri.parse(api.streamUrl(s.id, q)))
                .setCustomCacheKey("${s.id}:${q.name}")
                .setData(Util.getUtf8Bytes(SubsonicJson.encodeToString(s)))
                .build()
            DownloadService.sendAddDownload(context, MarcoDownloadService::class.java, req, false)
        }
        prefetchCovers(songs)
    }

    override fun remove(songs: List<Song>) = songs.forEach {
        DownloadService.sendRemoveDownload(context, MarcoDownloadService::class.java, it.id, false)
    }

    /** Calienta la caché de Coil para que las portadas se vean sin conexión. */
    private fun prefetchCovers(songs: List<Song>) {
        val loader = SingletonImageLoader.get(context)
        songs.mapNotNull { it.coverArt }.distinct().forEach { id ->
            for (size in COVER_SIZES) {
                api.coverUrl(id, size)?.let { loader.enqueue(ImageRequest.Builder(context).data(it).build()) }
            }
        }
    }

    /** Progreso fino de lo que se está bajando ahora mismo (el listener sólo avisa cambios de estado). */
    override fun pollProgress() {
        val active = manager.currentDownloads.mapNotNull { it.toOffline() }
        if (active.isNotEmpty()) _items.value = _items.value + active.associateBy { it.song.id }
    }

    override fun freeBytes(): Long = runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(0L)

    companion object {
        /** Tamaños de portada que usa la UI: filas, tarjetas y vista grande. */
        val COVER_SIZES = intArrayOf(120, 400, 800)
    }
}
