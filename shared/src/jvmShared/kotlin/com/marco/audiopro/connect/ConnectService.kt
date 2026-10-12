package com.marco.audiopro.connect

import com.marco.audiopro.data.Settings
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.SubsonicJson
import com.marco.audiopro.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.UUID

/** Lo que está sonando en un dispositivo (viaja en cada "faro"). */
@Serializable
data class NowPlaying(
    val title: String? = null,
    val artist: String? = null,
    val coverArt: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

@Serializable
private data class Beacon(
    val v: Int = 1,
    val id: String,
    val name: String,
    val platform: String,
    val port: Int,
    val account: String,
    val np: NowPlaying? = null,
)

/** Otro dispositivo con Flacker en la red, con la misma cuenta de Navidrome. */
data class RemoteDevice(
    val id: String,
    val name: String,
    val platform: String,
    val address: InetAddress,
    val port: Int,
    val nowPlaying: NowPlaying?,
    val lastSeen: Long,
)

@Serializable
private data class Command(
    val cmd: String,
    val account: String,
    val from: String = "",
    val songs: List<Song> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0,
    val value: Float = 0f,
    val key: String = "",
)

/** Estado completo de un reproductor remoto (para controlarlo como si fuera local). */
@Serializable
data class RemoteStatus(
    val queue: List<Song> = emptyList(),
    val index: Int = -1,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: String = "OFF",
    val volume: Float? = null,
)

@Serializable
private data class Reply(
    val ok: Boolean,
    val songs: List<Song> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0,
    val error: String? = null,
    val status: RemoteStatus? = null,
    /** Datos de sincronización (manifiesto o archivo en base64). */
    val payload: String? = null,
)

/** Lo que este dispositivo comparte con los demás (ajustes y portadas propias). */
interface SyncSource {
    fun manifest(): String
    fun blob(key: String): String?
}

/**
 * "Marco Connect", al estilo Spotify Connect pero sin nube: cada app anuncia por UDP en la red
 * local quién es y qué suena, y escucha órdenes (reproducir esta cola, pausa, siguiente…) por TCP.
 * Sólo se aceptan dispositivos con la misma cuenta de Navidrome.
 *
 * @param onNetworkStart hook de plataforma (en Android: tomar el MulticastLock del Wi-Fi).
 */
class ConnectService(
    private val deviceName: String,
    private val platform: String,
    private val settings: Settings,
    private val player: PlayerController,
    private val scope: CoroutineScope,
    private val onNetworkStart: () -> Unit = {},
) {
    private val _devices = MutableStateFlow<List<RemoteDevice>>(emptyList())
    /** Otros dispositivos vistos en los últimos segundos. */
    val devices: StateFlow<List<RemoteDevice>> = _devices.asStateFlow()

    /** Último aviso para la UI ("Pasó a PC-MARCO", errores de conexión…). */
    val notice = MutableStateFlow<String?>(null)
    /** Canción que otro dispositivo (la PC) pidió compartir en historias desde aquí (el celular). */
    val storyRequest = MutableStateFlow<Song?>(null)

    val id: String = settings.getString("connect_id") ?: UUID.randomUUID().toString().also { settings.putString("connect_id", it) }

    /** Dispositivo que estás controlando (como "Escuchando en …" de Spotify); null = éste. */
    private val _controlling = MutableStateFlow<RemoteDevice?>(null)
    val controlling: StateFlow<RemoteDevice?> = _controlling.asStateFlow()

    /** Último estado del dispositivo controlado y cuándo llegó (para extrapolar la posición). */
    private val _remote = MutableStateFlow<Pair<RemoteStatus, Long>?>(null)
    val remote: StateFlow<Pair<RemoteStatus, Long>?> = _remote.asStateFlow()

    /** Sincronización: quién responde "manifest"/"blob" y qué hacer cuando otro avisa de cambios. */
    @Volatile var sync: SyncSource? = null
    @Volatile var onRefresh: () -> Unit = {}

    /** Volumen pendiente de mandar (al arrastrar la barra sólo viaja el último valor). */
    private val pendingVolume = MutableStateFlow<Float?>(null)

    private var server: ServerSocket? = null
    private var udp: DatagramSocket? = null
    private val jobs = ArrayList<Job>()

    /** Huella de la cuenta (servidor + usuario): sólo hablamos con "nosotros mismos". */
    private fun accountKey(): String {
        val a = settings.account.value ?: return ""
        val d = MessageDigest.getInstance("SHA-256").digest("${a.url}|${a.user}".toByteArray())
        return d.take(8).joinToString("") { "%02x".format(it) }
    }

    fun start() {
        if (jobs.isNotEmpty()) return
        runCatching { onNetworkStart() }
        val srv = runCatching { ServerSocket(0) }.getOrNull() ?: return
        server = srv
        udp = runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(PORT))
            }
        }.getOrNull()

        jobs += scope.launch(Dispatchers.IO) { acceptLoop(srv) }
        jobs += scope.launch(Dispatchers.IO) { listenLoop() }
        jobs += scope.launch { beaconLoop(srv.localPort) }
        jobs += scope.launch { pollLoop() }
        jobs += scope.launch { volumeLoop() }
        jobs += scope.launch {
            // Olvida a los que dejaron de anunciarse.
            while (isActive) {
                delay(2000)
                val now = System.currentTimeMillis()
                _devices.value = _devices.value.filter { now - it.lastSeen < STALE_MS }
            }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        runCatching { server?.close() }
        runCatching { udp?.close() }
    }

    // ------------------------------------------------------------ anuncio (faro UDP)

    private suspend fun beaconLoop(tcpPort: Int) {
        val socket = DatagramSocket().apply { broadcast = true }
        try {
            while (scope.isActive) {
                val account = accountKey()
                if (account.isNotEmpty()) {
                    // Leer el reproductor en su hilo (Media3 lo exige en Android).
                    val np = withContext(Dispatchers.Main) {
                        val st = player.state.value
                        st.current?.let {
                            NowPlaying(it.title, it.artist, it.coverArt, st.isPlaying, player.positionMs(), st.durationMs)
                        }
                    }
                    val data = SubsonicJson.encodeToString(Beacon(id = id, name = deviceName, platform = platform, port = tcpPort, account = account, np = np)).toByteArray()
                    withContext(Dispatchers.IO) {
                        for (addr in broadcastAddresses()) {
                            runCatching { socket.send(DatagramPacket(data, data.size, addr, PORT)) }
                        }
                    }
                }
                delay(BEACON_MS)
            }
        } finally {
            socket.close()
        }
    }

    /** 255.255.255.255 y la dirección de broadcast de cada red activa (Windows con varias tarjetas). */
    private fun broadcastAddresses(): Set<InetAddress> {
        val out = linkedSetOf<InetAddress>(InetAddress.getByName("255.255.255.255"))
        runCatching {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                ni.interfaceAddresses.mapNotNullTo(out) { it.broadcast }
            }
        }
        return out
    }

    private fun listenLoop() {
        val sock = udp ?: return
        val buf = ByteArray(8192)
        while (!sock.isClosed) {
            val packet = DatagramPacket(buf, buf.size)
            runCatching { sock.receive(packet) }.getOrElse { if (sock.isClosed) return else continue }
            val b = runCatching { SubsonicJson.decodeFromString<Beacon>(String(packet.data, 0, packet.length)) }.getOrNull() ?: continue
            if (b.id == id || b.account != accountKey()) continue
            val dev = RemoteDevice(b.id, b.name, b.platform, packet.address, b.port, b.np, System.currentTimeMillis())
            _devices.value = (_devices.value.filter { it.id != b.id } + dev).sortedBy { it.name }
        }
    }

    // ------------------------------------------------------------ órdenes (TCP)

    private fun acceptLoop(srv: ServerSocket) {
        while (!srv.isClosed) {
            val s = runCatching { srv.accept() }.getOrNull() ?: continue
            scope.launch(Dispatchers.IO) { s.use { handle(it) } }
        }
    }

    private suspend fun handle(s: Socket) {
        s.soTimeout = 5000
        val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
        val out = PrintWriter(s.getOutputStream().bufferedWriter(Charsets.UTF_8), true)
        val cmd = runCatching { SubsonicJson.decodeFromString<Command>(reader.readLine() ?: return) }.getOrNull()
        val reply = when {
            cmd == null -> Reply(false, error = "orden inválida")
            cmd.account != accountKey() -> Reply(false, error = "otra cuenta")
            else -> withContext(Dispatchers.Main) { execute(cmd) }
        }
        out.println(SubsonicJson.encodeToString(reply))
    }

    private fun localStatus(): RemoteStatus {
        val st = player.state.value
        return RemoteStatus(
            st.queue, st.currentIndex, st.isPlaying, player.positionMs(), st.durationMs, st.shuffle, st.repeat.name, player.volume?.value,
        )
    }

    private fun execute(c: Command): Reply {
        when (c.cmd) {
            "play" -> {
                // Si otro dispositivo nos manda música, dejamos de controlar al que fuera.
                stopControlling()
                player.playFrom(c.songs, c.index, c.positionMs)
                notice.value = "Reproduciendo desde ${c.from}"
            }
            "status" -> {}
            "refresh" -> { onRefresh(); return Reply(true) }
            "manifest" -> return Reply(true, payload = sync?.manifest())
            "blob" -> return Reply(true, payload = sync?.blob(c.key))
            "toggle" -> player.toggle()
            "pause" -> if (player.state.value.isPlaying) player.toggle()
            "next" -> player.next()
            "previous" -> player.previous()
            "seek" -> player.seekTo(c.positionMs)
            "volume" -> player.setVolume(c.value)
            "shuffle" -> player.toggleShuffle()
            "repeat" -> player.cycleRepeat()
            "skip" -> player.skipToIndex(c.index)
            "remove" -> player.removeAt(c.index)
            "playNext" -> player.playNext(c.songs)
            "enqueue" -> player.enqueue(c.songs)
            "story" -> {
                storyRequest.value = c.songs.firstOrNull() ?: return Reply(false, error = "sin canción")
                notice.value = "${c.from} te mandó una historia para compartir"
                return Reply(true)
            }
            // Entrega la cola para que otro dispositivo la "jale" (Traer aquí).
            "handoff" -> {
                val st = player.state.value
                val r = Reply(true, st.queue, st.currentIndex.coerceAtLeast(0), player.positionMs())
                if (st.isPlaying) player.toggle()
                return r
            }
            else -> return Reply(false, error = "desconocida")
        }
        return Reply(true, status = localStatus())
    }

    private suspend fun send(to: RemoteDevice, cmd: Command, timeoutMs: Long = 6000): Reply? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeoutMs) {
            runCatching {
                Socket().use { s ->
                    s.connect(InetSocketAddress(to.address, to.port), 3000)
                    s.soTimeout = (timeoutMs - 1000).toInt()
                    PrintWriter(s.getOutputStream().bufferedWriter(Charsets.UTF_8), true).println(SubsonicJson.encodeToString(cmd))
                    val line = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8)).readLine()
                    line?.let { SubsonicJson.decodeFromString<Reply>(it) }
                }
            }.getOrNull()
        }
    }

    /** Manda lo que suena aquí a otro dispositivo (misma cola y posición) y pausa aquí. */
    suspend fun transferTo(to: RemoteDevice): Boolean {
        val (songs, index, pos) = withContext(Dispatchers.Main) {
            val st = player.state.value
            Triple(st.queue, st.currentIndex.coerceAtLeast(0), player.positionMs())
        }
        if (songs.isEmpty()) { notice.value = "No hay nada sonando aquí"; return false }
        val r = send(to, Command("play", accountKey(), deviceName, songs, index, pos))
        if (r?.ok == true) {
            withContext(Dispatchers.Main) { if (player.state.value.isPlaying) player.toggle() }
            notice.value = "Ahora suena en ${to.name}"
            // Como en Spotify: después de pasarla, la app controla ese dispositivo.
            _controlling.value = to
            r.status?.let { _remote.value = it to System.currentTimeMillis() }
            return true
        }
        notice.value = "No se pudo conectar con ${to.name}"
        return false
    }

    /** Trae aquí lo que suena en otro dispositivo (y lo pausa allá). */
    suspend fun pullFrom(from: RemoteDevice): Boolean {
        val r = send(from, Command("handoff", accountKey(), deviceName))
        if (r?.ok == true && r.songs.isNotEmpty()) {
            stopControlling()
            withContext(Dispatchers.Main) { player.playFrom(r.songs, r.index, r.positionMs) }
            notice.value = "Traído de ${from.name}"
            return true
        }
        notice.value = "No se pudo traer de ${from.name}"
        return false
    }

    /** Control remoto: toggle / next / previous / seek / skip… (responde con el estado nuevo). */
    suspend fun remote(to: RemoteDevice, cmd: String, index: Int = 0, positionMs: Long = 0, songs: List<Song> = emptyList()) {
        val r = send(fresh(to), Command(cmd, accountKey(), deviceName, songs, index, positionMs))
        if (r?.ok != true) { notice.value = "${to.name} no responde"; return }
        if (_controlling.value?.id == to.id) r.status?.let { _remote.value = it to System.currentTimeMillis() }
    }

    /** Pide datos de sincronización a otro dispositivo ("manifest" o "blob"). */
    suspend fun ask(d: RemoteDevice, cmd: String, key: String = ""): String? =
        send(fresh(d), Command(cmd, accountKey(), deviceName, key = key), timeoutMs = 25_000)?.takeIf { it.ok }?.payload

    /** Avisa a los demás que algo cambió aquí (me gusta, playlists, portadas, ajustes). */
    /** Manda una canción al celular para compartirla en historias de Instagram desde ahí. */
    suspend fun sendStory(to: RemoteDevice, song: Song): Boolean {
        val ok = send(fresh(to), Command("story", accountKey(), deviceName, listOf(song)))?.ok == true
        notice.value = if (ok) "Listo: ábrela en ${to.name} y compártela" else "${to.name} no responde"
        return ok
    }

    fun broadcastRefresh() {
        val list = _devices.value
        if (list.isEmpty()) return
        scope.launch(Dispatchers.IO) { list.forEach { d -> send(fresh(d), Command("refresh", accountKey(), deviceName)) } }
    }

    /** Pasa lo que suena en `from` (otro dispositivo) a `to` y controla `to`. */
    suspend fun moveBetween(from: RemoteDevice, to: RemoteDevice): Boolean {
        val h = send(fresh(from), Command("handoff", accountKey(), deviceName))
        if (h?.ok != true || h.songs.isEmpty()) { notice.value = "No se pudo mover desde ${from.name}"; return false }
        val r = send(fresh(to), Command("play", accountKey(), deviceName, h.songs, h.index, h.positionMs))
        if (r?.ok != true) { notice.value = "No se pudo conectar con ${to.name}"; return false }
        _controlling.value = to
        r.status?.let { _remote.value = it to System.currentTimeMillis() }
        notice.value = "Ahora suena en ${to.name}"
        return true
    }

    /** Controlar un dispositivo sin pasarle la música (lo que ya suena allá). */
    fun control(d: RemoteDevice) {
        _remote.value = null
        _controlling.value = d
    }

    fun stopControlling() {
        _controlling.value = null
        _remote.value = null
        pendingVolume.value = null
    }

    /** Volumen del dispositivo controlado; se manda el último valor, sin saturar la red. */
    fun remoteVolume(v: Float) {
        pendingVolume.value = v.coerceIn(0f, 1f)
        _remote.value = _remote.value?.let { (st, t) -> st.copy(volume = v.coerceIn(0f, 1f)) to t }
    }

    /** La dirección más reciente del dispositivo (pudo cambiar de IP). */
    private fun fresh(d: RemoteDevice) = _devices.value.firstOrNull { it.id == d.id } ?: d

    private suspend fun pollLoop() {
        var fails = 0
        while (scope.isActive) {
            val d = _controlling.value
            if (d != null) {
                val r = send(fresh(d), Command("status", accountKey(), deviceName))
                if (_controlling.value?.id == d.id) {
                    if (r?.status != null) {
                        fails = 0
                        _remote.value = r.status to System.currentTimeMillis()
                    } else if (++fails >= 4) {
                        notice.value = "Se perdió la conexión con ${d.name}"
                        stopControlling()
                        fails = 0
                    }
                }
            }
            delay(if (d != null) 700 else 1000)
        }
    }

    private suspend fun volumeLoop() {
        var sent: Float? = null
        while (scope.isActive) {
            val v = pendingVolume.value
            val d = _controlling.value
            if (v != null && v != sent && d != null) {
                send(fresh(d), Command("volume", accountKey(), deviceName, value = v))
                sent = v
            }
            if (v == null) sent = null
            delay(90)
        }
    }

    companion object {
        const val PORT = 47810
        private const val BEACON_MS = 2000L
        private const val STALE_MS = 8000L
    }
}
