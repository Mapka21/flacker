package com.marco.audiopro

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.DownloadManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.marco.audiopro.audio.AudioEngine
import com.marco.audiopro.data.Likes
import com.marco.audiopro.data.Settings
import com.marco.audiopro.data.StreamQuality
import com.marco.audiopro.data.info.AlbumInfoRepository
import com.marco.audiopro.data.info.ClaudeReviewer
import com.marco.audiopro.data.info.LastFm
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.download.Downloads
import com.marco.audiopro.player.PlayerConnection
import kotlinx.coroutines.MainScope
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AppContainer(private val context: Context) {
    val settings = Settings(context)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Para streaming: sin timeout de lectura agresivo (los FLAC hi-res pesan). */
    val streamHttp: OkHttpClient = http.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()

    /** Vive lo que vive el proceso; para trabajo que no debe morir con una pantalla. */
    val appScope = MainScope()

    val api = SubsonicApi(http) { settings.account.value }
    val engine = AudioEngine(settings)
    val albumInfo = AlbumInfoRepository(context, api, LastFm(http), ClaudeReviewer(), settings)
    val player = PlayerConnection(context)
    val likes = Likes(api, appScope)

    private val database by lazy { StandaloneDatabaseProvider(context) }

    /** Caché LRU de 3 GB de lo que ya escuchaste: re-escuchar no gasta datos. */
    val mediaCache: SimpleCache by lazy {
        SimpleCache(File(context.cacheDir, "media"), LeastRecentlyUsedCacheEvictor(3L * 1024 * 1024 * 1024), database)
    }

    /** Descargas: en filesDir (Android no la limpia) y sin límite de tamaño. */
    val downloadCache: SimpleCache by lazy {
        SimpleCache(File(context.filesDir, "downloads"), NoOpCacheEvictor(), database)
    }

    val downloads: Downloads by lazy {
        val manager = DownloadManager(
            context, database, downloadCache, OkHttpDataSource.Factory(streamHttp), Executors.newFixedThreadPool(3),
        ).apply { maxParallelDownloads = 3 }
        Downloads(context, manager, api, settings, appScope)
    }

    fun currentQuality(): StreamQuality {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val p = settings.prefs.value
        return if (cm?.isActiveNetworkMetered == true) p.mobileQuality else p.wifiQuality
    }
}

class MarcoApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.http })) }
            .diskCache {
                DiskCache.Builder()
                    // En filesDir para que las portadas de lo descargado sigan ahí sin conexión.
                    .directory(File(filesDir, "covers"))
                    .maxSizeBytes(500L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
}

val Context.container: AppContainer get() = (applicationContext as MarcoApp).container
