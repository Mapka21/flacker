package com.marco.audiopro.cast

import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.player.PlaybackState
import com.marco.audiopro.player.PlayerController
import com.marco.audiopro.player.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

/**
 * Transmitir a la tele: busca aparatos (Chromecast, Roku, DLNA), abre la conexión y deja un
 * [player] que la app usa igual que el de siempre. La cola la lleva este dispositivo; el audio
 * le llega a la tele por [CastRelay].
 * @param isLocal modo sin servidor (no se puede convertir a MP3: siempre va el archivo original).
 * @param keepAlive en Android mantiene viva la app mientras transmite (aviso en la barra); null = parar.
 */
class CastService(
    private val http: OkHttpClient,
    private val api: SubsonicApi,
    private val scope: CoroutineScope,
    private val isLocal: () -> Boolean = { false },
    private val keepAlive: (String?) -> Unit = {},
) {
    private val relay = CastRelay(http, api)
    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = _devices
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning
    private val _active = MutableStateFlow<CastDevice?>(null)
    /** El aparato al que se está transmitiendo (null = suena aquí). */
    val active: StateFlow<CastDevice?> = _active
    /** Último aviso para mostrar ("Tu Roku no deja…", "Se dejó de transmitir"). */
    val notice = MutableStateFlow<String?>(null)
    val player = CastPlayer()
    /** Una Roku a la que le falta la app de Flacker (la UI muestra cómo instalarla). */
    val rokuSetup = MutableStateFlow<CastDevice?>(null)

    suspend fun rokuDevMode(d: CastDevice) = RokuChannel.devMode(http, d.host)
    /** Instala la app de Flacker en la Roku (modo desarrollador). null = se instaló. */
    suspend fun installRoku(d: CastDevice, password: String): String? = RokuChannel.install(http, d.host, password)

    private var session: CastSession? = null
    private var lanIp = ""
    private val lock = Mutex()

    fun scan() {
        if (_scanning.value) return
        _scanning.value = true
        scope.launch(Dispatchers.IO) {
            val found = runCatching { CastDiscovery.scan(http) }.getOrDefault(emptyList())
            _devices.value = (found + listOfNotNull(_active.value)).distinctBy { it.id }
            _scanning.value = false
        }
    }

    /** Pasa la música a [d] desde donde va ([queue], [index], [positionMs]). Devuelve el error, o null si salió bien. */
    suspend fun start(d: CastDevice, queue: List<Song>, index: Int, positionMs: Long): String? = lock.withLock {
        stopSession()
        val result = runCatching {
            lanIp = CastDiscovery.lanAddressFor(d.host) ?: error("No encontré la red de la casa en este dispositivo")
            relay.start()
            val s = when (d.kind) {
                CastDevice.Kind.CHROMECAST -> ChromecastSession(d, scope).also { it.connect() }
                CastDevice.Kind.ROKU -> RokuSession(d, http).also { it.check() } // modo limitado o sin la app: avisa aquí
                CastDevice.Kind.DLNA -> DlnaSession(d, http)
            }
            session = s
            _active.value = d
            keepAlive(d.name)
            player.begin(d, s, queue, index, positionMs)
        }
        result.exceptionOrNull()?.let { e ->
            if (e is RokuNeedsAppException) rokuSetup.value = d
            stopSession()
            return@withLock e.message ?: "No se pudo conectar con ${d.name}"
        }
        null
    }

    /** Deja de transmitir. Devuelve dónde iba (cola, índice, posición) para seguir aquí. */
    suspend fun stop(): Triple<List<Song>, Int, Long>? = lock.withLock {
        val at = if (_active.value != null) Triple(player.queueSnapshot(), player.state.value.currentIndex, player.positionMs()) else null
        stopSession()
        at
    }

    private suspend fun stopSession() {
        player.end()
        session?.let { runCatching { it.close(stopApp = true) } }
        session = null
        if (_active.value != null) keepAlive(null)
        _active.value = null
        relay.stop()
    }

    // ------------------------------------------------------------------ formatos

    private val chromecastOk = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "mp4")
    private val rokuOk = setOf("mp3", "flac", "m4a", "aac", "wav", "wma", "mp4")
    private val dlnaOk = setOf("mp3", "flac")

    /** ¿Hay que mandarla convertida a MP3 para que ese aparato la toque? */
    private fun needsMp3(kind: CastDevice.Kind, s: Song): Boolean {
        if (isLocal()) return false
        val sfx = s.suffix?.lowercase() ?: return true
        return when (kind) {
            CastDevice.Kind.CHROMECAST -> sfx !in chromecastOk || (s.samplingRate ?: 0) > 96_000
            CastDevice.Kind.ROKU -> sfx !in rokuOk
            CastDevice.Kind.DLNA -> sfx !in dlnaOk
        }
    }

    private fun mime(s: Song): String = when (s.suffix?.lowercase()) {
        "flac" -> "audio/flac"
        "mp3" -> "audio/mpeg"
        "m4a", "aac", "alac", "mp4" -> "audio/mp4"
        "ogg", "opus", "oga" -> "audio/ogg"
        "wav" -> "audio/wav"
        "wma" -> "audio/x-ms-wma"
        else -> s.contentType ?: "audio/mpeg"
    }

    // ------------------------------------------------------------------ reproductor

    /** La cola mientras suena en la tele; mismas órdenes que el reproductor de siempre. */
    inner class CastPlayer : PlayerController {
        private val _state = MutableStateFlow(PlaybackState())
        override val state: StateFlow<PlaybackState> = _state
        private val _volume = MutableStateFlow(0.5f)
        override val volume: StateFlow<Float> = _volume

        private var device: CastDevice? = null
        private var s: CastSession? = null
        private var queue = mutableListOf<Song>()
        private var original = listOf<Song>()
        private var index = -1
        private var repeat = RepeatMode.OFF
        private var shuffle = false
        private var poller: Job? = null
        private var pos = 0L
        private var posAt = 0L
        private var playing = false
        private var sawPlaying = false
        private var triedMp3 = false
        private var loadedAt = 0L
        private var scrobbled = false
        private var listened = 0L
        private var lastPlayingPos = 0L
        private var lastDur = 0L

        fun queueSnapshot(): List<Song> = queue.toList()

        internal fun begin(d: CastDevice, session: CastSession, songs: List<Song>, start: Int, positionMs: Long) {
            device = d; s = session
            queue = songs.toMutableList(); original = songs; index = start.coerceIn(0, (songs.size - 1).coerceAtLeast(0))
            if (queue.isNotEmpty()) load(positionMs)
            publish()
            poller?.cancel()
            poller = scope.launch { while (isActive) { delay(1000); runCatching { tick() } } }
        }

        internal fun end() {
            poller?.cancel(); poller = null
            s = null; device = null; playing = false
            _state.value = PlaybackState()
        }

        private fun publish(buffering: Boolean = false, error: String? = null) {
            _state.value = PlaybackState(
                current = queue.getOrNull(index), currentIndex = index, isPlaying = playing, isBuffering = buffering,
                durationMs = (queue.getOrNull(index)?.duration ?: 0) * 1000L, shuffle = shuffle, repeat = repeat,
                queue = queue.toList(), error = error, remoteName = device?.name,
            )
        }

        private fun load(startMs: Long, mp3: Boolean = false) {
            val sess = s ?: return; val d = device ?: return
            val song = queue.getOrNull(index) ?: return
            triedMp3 = mp3; sawPlaying = false; scrobbled = false; listened = 0; lastPlayingPos = 0; lastDur = 0
            pos = startMs; posAt = System.currentTimeMillis(); loadedAt = posAt; playing = true
            publish(buffering = true)
            val conv = mp3 || needsMp3(d.kind, song)
            val meta = CastMeta(
                song.title, song.artist, song.album,
                song.coverArt?.let { relay.coverUrl(lanIp, it) }, (song.duration ?: 0) * 1000L,
            )
            scope.launch {
                val r = runCatching { sess.load(relay.audioUrl(lanIp, song.id, conv), if (conv) "audio/mpeg" else mime(song), meta, startMs) }
                val e = r.exceptionOrNull()
                when {
                    e is RokuLimitedException -> { notice.value = e.message; publish(error = e.message) }
                    e != null && !conv && !isLocal() -> load(startMs, mp3 = true) // no le gustó el original: va en MP3
                    e != null -> { notice.value = "${d.name} no pudo tocar \"${song.title}\""; advance(auto = true) }
                    else -> runCatching { api.scrobble(song.id, submission = false) }
                }
            }
        }

        private suspend fun tick() {
            val sess = s ?: return
            val st = sess.poll()
            val now = System.currentTimeMillis()
            st.volume?.let { _volume.value = it }
            if (st.ended) { notice.value = "Se dejó de transmitir a ${device?.name}"; scope.launch { stop() }; return }
            val song = queue.getOrNull(index)
            when (st.state) {
                CastState.PLAYING -> {
                    if (sawPlaying) listened += now - posAt
                    sawPlaying = true; playing = true; pos = st.positionMs; posAt = now
                    lastPlayingPos = st.positionMs; if (st.durationMs > 0) lastDur = st.durationMs
                    // Scrobble como en el reproductor normal: a la mitad o a los 4 minutos.
                    val dur = st.durationMs.takeIf { it > 0 } ?: ((song?.duration ?: 0) * 1000L)
                    if (!scrobbled && song != null && dur > 30_000 && (listened > dur / 2 || listened > 240_000)) {
                        scrobbled = true; api.scrobble(song.id, submission = true)
                    }
                    publish()
                }
                CastState.PAUSED -> { playing = false; pos = st.positionMs; posAt = now; publish() }
                CastState.LOADING -> publish(buffering = true)
                CastState.FINISHED -> if (sawPlaying) advance(auto = true)
                CastState.ERROR -> if (!triedMp3 && !isLocal()) load(pos, mp3 = true) else advance(auto = true)
                CastState.IDLE -> {
                    // DLNA y Roku no dicen "terminó": si sonaba y se detuvo cerca del final, es la siguiente.
                    // (Se usa la última posición que se vio sonando: muchas teles la regresan a 0 al detenerse.)
                    val dur = lastDur.takeIf { it > 0 } ?: ((song?.duration ?: 0) * 1000L)
                    if (sawPlaying && (dur <= 0 || lastPlayingPos >= dur - 6000)) advance(auto = true)
                    else if (sawPlaying || now - loadedAt > 20_000) { playing = false; publish() }
                }
            }
        }

        private fun advance(auto: Boolean) {
            if (queue.isEmpty()) return
            when {
                auto && repeat == RepeatMode.ONE -> {}
                index + 1 < queue.size -> index++
                repeat == RepeatMode.ALL -> index = 0
                else -> { playing = false; sawPlaying = false; publish(); return }
            }
            load(0)
        }

        override fun setVolume(v: Float) { _volume.value = v; scope.launch(Dispatchers.IO) { runCatching { s?.setVolume(v) } } }

        override fun play(songs: List<Song>, startIndex: Int, shuffle: Boolean) {
            if (songs.isEmpty()) return
            original = songs
            this.shuffle = shuffle
            queue = (if (shuffle) songs.shuffled() else songs).toMutableList()
            index = if (shuffle) 0 else startIndex.coerceIn(0, songs.lastIndex)
            load(0)
        }

        override fun playFrom(songs: List<Song>, index: Int, positionMs: Long) {
            if (songs.isEmpty()) return
            original = songs; queue = songs.toMutableList(); this.index = index.coerceIn(0, songs.lastIndex)
            load(positionMs)
        }

        override fun playNext(songs: List<Song>) { queue.addAll((index + 1).coerceAtMost(queue.size), songs); publish() }
        override fun enqueue(songs: List<Song>) { queue.addAll(songs); publish() }

        override fun toggle() {
            val sess = s ?: return
            playing = !playing; posAt = System.currentTimeMillis(); publish()
            scope.launch(Dispatchers.IO) { runCatching { if (playing) sess.play() else sess.pause() } }
        }

        override fun next() = advance(auto = false)
        override fun previous() {
            if (positionMs() > 3000 || index == 0) { seekTo(0); return }
            index--; load(0)
        }

        override fun seekTo(ms: Long) {
            val sess = s ?: return
            if (device?.kind == CastDevice.Kind.ROKU) { load(0); return } // Roku no adelanta: sólo puede volver a empezar
            pos = ms; posAt = System.currentTimeMillis(); publish()
            scope.launch(Dispatchers.IO) { runCatching { sess.seek(ms) } }
        }

        override fun skipToIndex(i: Int) { if (i in queue.indices) { index = i; load(0) } }

        override fun removeAt(i: Int) {
            if (i !in queue.indices || i == index) return
            queue.removeAt(i); if (i < index) index--; publish()
        }

        override fun toggleShuffle() {
            val cur = queue.getOrNull(index)
            shuffle = !shuffle
            if (shuffle) {
                val rest = queue.filterIndexed { k, _ -> k != index }.shuffled()
                queue = (listOfNotNull(cur) + rest).toMutableList(); index = 0
            } else {
                queue = original.toMutableList(); index = cur?.let { c -> queue.indexOfFirst { it.id == c.id } }?.takeIf { it >= 0 } ?: 0
            }
            publish()
        }

        override fun cycleRepeat() { repeat = RepeatMode.entries[(repeat.ordinal + 1) % RepeatMode.entries.size]; publish() }

        override fun positionMs(): Long {
            val p = if (playing && posAt > 0) pos + (System.currentTimeMillis() - posAt) else pos
            val dur = (queue.getOrNull(index)?.duration ?: 0) * 1000L
            return if (dur > 0) p.coerceIn(0, dur) else p.coerceAtLeast(0)
        }
    }
}
