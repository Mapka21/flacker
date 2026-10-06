package com.marco.audiopro

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.marco.audiopro.audio.AudioEngine
import com.marco.audiopro.data.Settings
import com.marco.audiopro.data.StreamQuality
import com.marco.audiopro.data.info.AlbumInfoRepository
import com.marco.audiopro.data.info.ClaudeReviewer
import com.marco.audiopro.data.info.LastFm
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.player.PlayerConnection
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class AppContainer(private val context: Context) {
    val settings = Settings(context)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Para streaming: sin timeout de lectura agresivo (los FLAC hi-res pesan). */
    val streamHttp: OkHttpClient = http.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()

    val api = SubsonicApi(http) { settings.account.value }
    val engine = AudioEngine(settings)
    val albumInfo = AlbumInfoRepository(context, api, LastFm(http), ClaudeReviewer(), settings)
    val player = PlayerConnection(context)

    /** Caché LRU de 3 GB de lo que ya escuchaste: re-escuchar no gasta datos. */
    val mediaCache: SimpleCache by lazy {
        SimpleCache(
            File(context.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(3L * 1024 * 1024 * 1024),
            StandaloneDatabaseProvider(context),
        )
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
                    .directory(File(cacheDir, "covers"))
                    .maxSizeBytes(300L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
}

val Context.container: AppContainer get() = (applicationContext as MarcoApp).container
