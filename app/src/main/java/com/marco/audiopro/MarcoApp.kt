package com.marco.audiopro

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import com.marco.audiopro.connect.ConnectService
import com.marco.audiopro.data.info.LyricsRepository
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
import com.marco.audiopro.audio.AnalyzerTap
import com.marco.audiopro.audio.AudioEngine
import com.marco.audiopro.audio.ParametricEqProcessor
import com.marco.audiopro.core.AppGraph
import com.marco.audiopro.core.CacheControl
import com.marco.audiopro.data.KeyValueStore
import com.marco.audiopro.data.Likes
import com.marco.audiopro.data.Settings
import com.marco.audiopro.data.StreamQuality
import com.marco.audiopro.data.info.AlbumInfoRepository
import com.marco.audiopro.data.info.ClaudeReviewer
import com.marco.audiopro.data.info.LastFm
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.download.Downloads
import com.marco.audiopro.player.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** SharedPreferences como almacén de [Settings]. */
private class PrefsStore(private val sp: SharedPreferences) : KeyValueStore {
    override fun getString(key: String) = sp.getString(key, null)
    override fun getFloat(key: String, def: Float) = sp.getFloat(key, def)
    override fun getInt(key: String, def: Int) = sp.getInt(key, def)
    override fun getBoolean(key: String, def: Boolean) = sp.getBoolean(key, def)
    override fun edit(values: Map<String, Any?>) {
        val e = sp.edit()
        values.forEach { (k, v) ->
            when (v) {
                null -> e.remove(k)
                is String -> e.putString(k, v)
                is Float -> e.putFloat(k, v)
                is Int -> e.putInt(k, v)
                is Boolean -> e.putBoolean(k, v)
                else -> e.putString(k, v.toString())
            }
        }
        e.apply()
    }
}

class AppContainer(private val context: Context) : AppGraph {
    override val settings = Settings(PrefsStore(context.getSharedPreferences("marco", Context.MODE_PRIVATE)))

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Para streaming: sin timeout de lectura agresivo (los FLAC hi-res pesan). */
    val streamHttp: OkHttpClient = http.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()

    /** Vive lo que vive el proceso; para trabajo que no debe morir con una pantalla. */
    val appScope = MainScope()

    override val api = SubsonicApi(http) { settings.account.value }
    val tap = AnalyzerTap()
    override val engine = AudioEngine(settings, tap)
    val eqProcessor = ParametricEqProcessor(engine.eq)
    override val albumInfo = AlbumInfoRepository(context.filesDir, api, LastFm(http), ClaudeReviewer(), settings)
    override val player = PlayerConnection(context)
    override val lyrics = LyricsRepository(http, api, context.filesDir)
    override val connect: ConnectService by lazy {
        ConnectService(Build.MODEL ?: "Android", "android", settings, player, appScope) {
            // Sin esto muchos teléfonos descartan los anuncios UDP de la red.
            context.applicationContext.getSystemService(WifiManager::class.java)
                ?.createMulticastLock("marco-connect")?.apply { setReferenceCounted(false); acquire() }
        }
    }
    override val likes = Likes(api, appScope)
    override val versionName: String = BuildConfig.VERSION_NAME
    override val isDesktop = false

    private val database by lazy { StandaloneDatabaseProvider(context) }

    /** Caché LRU de 3 GB de lo que ya escuchaste: re-escuchar no gasta datos. */
    val mediaCache: SimpleCache by lazy {
        SimpleCache(File(context.cacheDir, "media"), LeastRecentlyUsedCacheEvictor(3L * 1024 * 1024 * 1024), database)
    }

    override val cache = object : CacheControl {
        override val capacityLabel = "3 GB"
        override suspend fun usedBytes() = withContext(Dispatchers.IO) { mediaCache.cacheSpace }
        override suspend fun clear() = withContext(Dispatchers.IO) {
            mediaCache.keys.toList().forEach { mediaCache.removeResource(it) }
        }
    }

    /** Descargas: en filesDir (Android no la limpia) y sin límite de tamaño. */
    val downloadCache: SimpleCache by lazy {
        SimpleCache(File(context.filesDir, "downloads"), NoOpCacheEvictor(), database)
    }

    override val downloads: Downloads by lazy {
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
