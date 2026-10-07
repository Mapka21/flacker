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

/** Otro dispositivo con Marco Audio Pro en la red, con la misma cuenta de Navidrome. */
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
)

@Serializable
private data class Reply(
    val ok: Boolean,
    val songs: List<Song> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0,
    val error: String? = null,
)

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

    val id: String = settings.getString("connect_id") ?: UUID.randomUUID().toString().also { settings.putString("connect_id", it) }

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

    private fun execute(c: Command): Reply {
        when (c.cmd) {
            "play" -> {
                player.playFrom(c.songs, c.index, c.positionMs)
                notice.value = "Reproduciendo desde ${c.from}"
            }
            "toggle" -> player.toggle()
            "pause" -> if (player.state.value.isPlaying) player.toggle()
            "next" -> player.next()
            "previous" -> player.previous()
            "seek" -> player.seekTo(c.positionMs)
            // Entrega la cola para que otro dispositivo la "jale" (Traer aquí).
            "handoff" -> {
                val st = player.state.value
                val r = Reply(true, st.queue, st.currentIndex.coerceAtLeast(0), player.positionMs())
                if (st.isPlaying) player.toggle()
                return r
            }
            else -> return Reply(false, error = "desconocida")
        }
        return Reply(true)
    }

    private suspend fun send(to: RemoteDevice, cmd: Command): Reply? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(6000) {
            runCatching {
                Socket().use { s ->
                    s.connect(InetSocketAddress(to.address, to.port), 3000)
                    s.soTimeout = 5000
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
            return true
        }
        notice.value = "No se pudo conectar con ${to.name}"
        return false
    }

    /** Trae aquí lo que suena en otro dispositivo (y lo pausa allá). */
    suspend fun pullFrom(from: RemoteDevice): Boolean {
        val r = send(from, Command("handoff", accountKey(), deviceName))
        if (r?.ok == true && r.songs.isNotEmpty()) {
            withContext(Dispatchers.Main) { player.playFrom(r.songs, r.index, r.positionMs) }
            notice.value = "Traído de ${from.name}"
            return true
        }
        notice.value = "No se pudo traer de ${from.name}"
        return false
    }

    /** Control remoto: toggle / next / previous. */
    suspend fun remote(to: RemoteDevice, cmd: String) {
        if (send(to, Command(cmd, accountKey(), deviceName))?.ok != true) notice.value = "${to.name} no responde"
    }

    companion object {
        const val PORT = 47810
        private const val BEACON_MS = 2000L
        private const val STALE_MS = 8000L
    }
}
