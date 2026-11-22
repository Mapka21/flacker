package com.marco.audiopro.download

import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.marco.audiopro.data.Settings
import com.marco.audiopro.data.StreamQuality
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.data.subsonic.SubsonicJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/**
 * Descargas en la PC: archivos normales en Artista/Álbum/NN Título.ext (sirven también fuera de
 * Flacker) y un índice con el Song completo, para que todo funcione sin servidor. Lo que quedó a
 * medias se vuelve a bajar al abrir.
 */
class DesktopDownloads(
    private val dir: File,
    private val http: OkHttpClient,
    private val api: SubsonicApi,
    private val settings: Settings,
    scope: CoroutineScope,
) : OfflineStore {
    private val index = File(dir, ".flacker-descargas.json")
    private val paths = ConcurrentHashMap<String, String>() // id → ruta relativa de lo ya bajado
    private val received = ConcurrentHashMap<String, Pair<Float, Long>>() // id → (%, bytes) de lo que va bajando
    private val folders = HashMap<String, String>() // albumId → Artista/Álbum
    private val queue = Channel<Song>(Channel.UNLIMITED)
    private val _items = MutableStateFlow<Map<String, OfflineSong>>(emptyMap())
    override val items: StateFlow<Map<String, OfflineSong>> = _items.asStateFlow()

    init {
        load()
        repeat(2) { scope.launch(Dispatchers.IO) { for (s in queue) fetch(s) } }
        // Las que se quedaron a medias la vez pasada.
        _items.value.values.filter { !it.done }.forEach { queue.trySend(it.song) }
    }

    /** La copia local, si ya está completa. */
    fun file(songId: String): File? = paths[songId]?.let { File(dir, it) }?.takeIf { it.isFile }

    override fun download(songs: List<Song>) {
        val now = _items.value
        val new = songs.filter { it.externalUrl == null && now[it.id]?.let { o -> o.done || o.active } != true }.distinctBy { it.id }
        if (new.isEmpty()) return
        _items.update { m -> m + new.associate { it.id to OfflineSong(it, OfflineStatus.QUEUED, 0f, 0) } }
        save()
        new.forEach { queue.trySend(it) }
    }

    override fun remove(songs: List<Song>) {
        _items.update { m -> m - songs.map { it.id }.toSet() } // lo que se esté bajando se detiene solo
        songs.forEach { s ->
            val f = paths.remove(s.id)?.let { File(dir, it) } ?: return@forEach
            if (!f.delete()) f.deleteOnExit() // Windows no deja borrar lo que está sonando
            f.parentFile?.let { album ->
                if (album.listFiles()?.all { it.name == COVER } == true) {
                    album.deleteRecursively()
                    album.parentFile?.takeIf { it != dir }?.delete() // sólo se borra si quedó vacía
                }
            }
        }
        save()
    }

    override fun pollProgress() {
        if (received.isEmpty()) return
        _items.update { m ->
            m + received.mapNotNull { (id, p) -> m[id]?.takeIf { it.status == OfflineStatus.DOWNLOADING }?.let { id to it.copy(percent = p.first, bytes = p.second) } }
        }
    }

    override fun freeBytes(): Long = dir.usableSpace

    // ---------------------------------------------------------------- bajar

    private fun set(song: Song, status: OfflineStatus, bytes: Long = 0) =
        _items.update { m -> if (song.id in m) m + (song.id to OfflineSong(song, status, if (status == OfflineStatus.DONE) 100f else 0f, bytes)) else m }

    private suspend fun fetch(song: Song) {
        if (_items.value[song.id]?.status.let { it == null || it == OfflineStatus.DONE }) return // la cancelaste antes de empezar
        set(song, OfflineStatus.DOWNLOADING)
        for (attempt in 1..3) {
            val r = runCatching { save(song) }
            received.remove(song.id)
            if (song.id !in _items.value) return // cancelada a media descarga
            if (r.isSuccess) {
                set(song, OfflineStatus.DONE, file(song.id)?.length() ?: 0)
                save()
                warmCovers(song)
                return
            }
            delay(attempt * 2000L)
        }
        set(song, OfflineStatus.FAILED)
    }

    private fun save(song: Song) {
        if (dir.usableSpace < MIN_FREE) throw IOException("Ya casi no hay espacio")
        val q = settings.prefs.value.downloadQuality
        val ext = if (q == StreamQuality.ORIGINAL) song.suffix ?: "audio" else q.format ?: "audio"
        val target = target(song, ext)
        val part = File(target.path + ".part")
        target.parentFile.mkdirs()
        try {
            http.newCall(Request.Builder().url(api.streamUrl(song.id, q)).build()).execute().use { res ->
                // Subsonic contesta 200 con un texto cuando algo falla.
                val type = res.header("Content-Type").orEmpty()
                if (!res.isSuccessful || "json" in type || "xml" in type || "text" in type) throw IOException("El servidor no mandó la canción (${res.code})")
                val body = res.body ?: throw IOException("Sin datos")
                val total = body.contentLength().takeIf { it > 0 }
                val guess = total ?: song.size?.takeIf { q == StreamQuality.ORIGINAL } ?: song.duration?.let { it * q.maxBitRate * 125L }?.takeIf { it > 0 }
                var got = 0L
                body.byteStream().use { inp ->
                    part.outputStream().use { out ->
                        val b = ByteArray(64 * 1024)
                        while (true) {
                            val n = inp.read(b)
                            if (n < 0) break
                            if (song.id !in _items.value) throw IOException("cancelada")
                            out.write(b, 0, n); got += n
                            received[song.id] = (if (guess != null) (got * 100f / guess).coerceAtMost(99f) else 0f) to got
                        }
                    }
                }
                if (got == 0L || (total != null && got != total)) throw IOException("Llegó incompleta")
            }
            Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            paths[song.id] = target.relativeTo(dir).invariantSeparatorsPath
        } finally {
            part.delete()
        }
        val cover = File(target.parentFile, COVER)
        if (!cover.exists()) runCatching {
            http.newCall(Request.Builder().url(api.coverUrl(song.coverArt, 800)!!).build()).execute().use { r ->
                if (r.isSuccessful && r.header("Content-Type").orEmpty().startsWith("image")) r.body?.let { cover.writeBytes(it.bytes()) }
            }
        }
    }

    /** Artista/Álbum/NN Título.ext; un álbum con varios artistas se queda junto en la carpeta que ya tenga. */
    @Synchronized
    private fun target(song: Song, ext: String): File {
        val album = song.albumId?.let { a -> folders.getOrPut(a) { clean(song.artist) + "/" + clean(song.album) } } ?: (clean(song.artist) + "/" + clean(song.album))
        val disc = song.discNumber?.takeIf { it > 1 }?.let { "$it-" } ?: ""
        val name = clean((song.track?.let { "%s%02d ".format(disc, it) } ?: "") + song.title)
        val f = File(dir, "$album/$name.$ext")
        // Otra canción con el mismo nombre: se distingue con su id.
        val rel = f.relativeTo(dir).invariantSeparatorsPath
        return if (paths.any { (id, p) -> id != song.id && p == rel }) File(dir, "$album/$name ${clean(song.id)}.$ext") else f
    }

    private fun clean(s: String?) = (s ?: "").replace(Regex("[<>:\"/\\\\|?*\\x00-\\x1f]"), "_").trim().take(80).trimEnd('.', ' ').ifBlank { "Desconocido" }

    /** Que las portadas se vean sin conexión: se calienta la caché en disco de Coil con los tamaños de la UI. */
    private fun warmCovers(song: Song) {
        val ctx = PlatformContext.INSTANCE
        for (size in intArrayOf(120, 400, 800)) api.coverUrl(song.coverArt, size)?.let {
            SingletonImageLoader.get(ctx).enqueue(ImageRequest.Builder(ctx).data(it).memoryCachePolicy(CachePolicy.DISABLED).build())
        }
    }

    // ---------------------------------------------------------------- índice

    @Synchronized
    private fun save() {
        val arr = buildJsonArray {
            _items.value.values.forEach { o ->
                add(buildJsonObject {
                    put("song", SubsonicJson.encodeToJsonElement(Song.serializer(), o.song))
                    paths[o.song.id]?.let { put("path", JsonPrimitive(it)) }
                })
            }
        }
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, index.name + ".tmp")
            tmp.writeText(SubsonicJson.encodeToString(JsonArray.serializer(), arr))
            Files.move(tmp.toPath(), index.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun load() {
        dir.mkdirs()
        val arr = runCatching { SubsonicJson.parseToJsonElement(index.readText()).jsonArray }.getOrNull() ?: return
        val map = LinkedHashMap<String, OfflineSong>()
        for (e in arr) runCatching {
            val song = SubsonicJson.decodeFromJsonElement(Song.serializer(), e.jsonObject.getValue("song"))
            val path = e.jsonObject["path"]?.jsonPrimitive?.content
            if (path == null) map[song.id] = OfflineSong(song, OfflineStatus.QUEUED, 0f, 0)
            else File(dir, path).takeIf { it.isFile }?.let { f -> // si borraste el archivo a mano, ya no cuenta
                paths[song.id] = path
                song.albumId?.let { a -> folders.putIfAbsent(a, path.substringBeforeLast('/')) }
                map[song.id] = OfflineSong(song, OfflineStatus.DONE, 100f, f.length())
            }
        }
        _items.value = map
    }

    private companion object {
        const val COVER = "cover.jpg"
        const val MIN_FREE = 300L * 1024 * 1024
    }
}
