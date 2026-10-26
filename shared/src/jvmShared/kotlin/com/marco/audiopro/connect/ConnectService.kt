package com.marco.audiopro.connect

import com.marco.audiopro.cast.CastDiscovery
import com.marco.audiopro.cast.CastRelay
import com.marco.audiopro.data.Settings
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.SubsonicJson
import com.marco.audiopro.player.PlayerController
import kotlinx.coroutines.CompletableDeferred
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
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
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

/** Otro dispositivo con Flacker en la red (con la misma cuenta de Navidrome, emparejado o ninguno). */
data class RemoteDevice(
    val id: String,
    val name: String,
    val platform: String,
    val address: InetAddress,
    val port: Int,
    val nowPlaying: NowPlaying?,
    val lastSeen: Long,
    /** Emparejado con código (otra cuenta o sin servidor): se controla, pero no se sincronizan ajustes. */
    val paired: Boolean = false,
)

/** Otro dispositivo pide emparejarse; se ve aquí hasta que digas que sí o no. */
class PairRequest(val name: String, val platform: String, val code: String) {
    internal val answer = CompletableDeferred<Boolean>()
}

/** Alguien de la red quiere mandarte archivos (lo atiende [com.marco.audiopro.share.ShareService]). */
fun interface OfferHandler {
    /** Lo que se le contesta al que manda; si no, lanza el error ("rechazado", "ocupado"…). */
    suspend fun offer(fromName: String, platform: String, address: InetAddress, payload: String): String
}

@Serializable
private data class Command(
    val cmd: String,
    val account: String = "",
    val from: String = "",
    val songs: List<Song> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0,
    val value: Float = 0f,
    val key: String = "",
    /** Id de Connect del que manda (para encontrar su emparejamiento). */
    val fromId: String = "",
    val platform: String = "",
    val payload: String? = null,
)

/** Dispositivos emparejados con código: id → nombre y secreto compartido. */
@Serializable
private data class Pairing(val id: String, val name: String, val secret: String)

private class Pairings(private val settings: Settings) {
    @Volatile private var list: List<Pairing> =
        runCatching { SubsonicJson.decodeFromString<List<Pairing>>(settings.getString("connect_pairs") ?: "[]") }.getOrDefault(emptyList())

    fun secret(id: String): String? = list.firstOrNull { it.id == id }?.secret
    fun has(id: String) = secret(id) != null

    @Synchronized fun put(id: String, name: String, secret: String) = save(list.filter { it.id != id } + Pairing(id, name, secret))
    @Synchronized fun remove(id: String) = save(list.filter { it.id != id })

    private fun save(l: List<Pairing>) {
        list = l
        settings.putString("connect_pairs", SubsonicJson.encodeToString(l))
    }
}

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
 * Se aceptan dispositivos con la misma cuenta de Navidrome o emparejados con código; a un
 * emparejado (que no ve tu biblioteca) la música le llega desde este dispositivo por [relay].
 *
 * @param relay sirve el audio y las portadas de aquí a los emparejados.
 * @param onNetworkStart hook de plataforma (en Android: tomar el MulticastLock del Wi-Fi).
 */
class ConnectService(
    private val deviceName: String,
    private val platform: String,
    private val settings: Settings,
    private val player: PlayerController,
    private val scope: CoroutineScope,
    private val relay: CastRelay? = null,
    private val onNetworkStart: () -> Unit = {},
) {
    private val _devices = MutableStateFlow<List<RemoteDevice>>(emptyList())
    /** Dispositivos que puedes controlar (tu cuenta o emparejados) vistos en los últimos segundos. */
    val devices: StateFlow<List<RemoteDevice>> = _devices.asStateFlow()

    private val _nearby = MutableStateFlow<List<RemoteDevice>>(emptyList())
    /** Los demás Flacker de la red (otra cuenta, sin emparejar): se les pueden mandar canciones o emparejarse. */
    val nearby: StateFlow<List<RemoteDevice>> = _nearby.asStateFlow()

    /** Todos los faros recientes y si son de tu cuenta. */
    private class Seen(val dev: RemoteDevice, val mine: Boolean)
    private val seen = ConcurrentHashMap<String, Seen>()
    private val pairs = Pairings(settings)

    /** Alguien pide emparejarse (la UI pregunta). */
    val pairRequest = MutableStateFlow<PairRequest?>(null)
    /** Emparejando desde aquí: con quién y el código que también sale allá. */
    val pairing = MutableStateFlow<Pair<RemoteDevice, String>?>(null)
    @Volatile var onOffer: OfferHandler? = null

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

    private fun hash(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).take(8).joinToString("") { "%02x".format(it) }

    /**
     * Huella de la cuenta (servidor + usuario) que va en el faro, para ver a "nosotros mismos" en la red.
     * Sin servidor todos tendrían la misma cuenta local: ahí cada dispositivo es su propia biblioteca.
     */
    private fun beaconKey(): String {
        val a = settings.account.value ?: return ""
        val local = if (com.marco.audiopro.data.local.LocalMode.isOn(settings)) "|$id" else ""
        return hash("beacon|${a.url}|${a.user}$local")
    }

    /**
     * Clave de las órdenes: distinta de la del faro (que cualquiera en la red puede leer) y, si se
     * inició sesión con la contraseña, derivada de ella ([Settings.connectSecret]).
     */
    private fun accountKey(): String {
        val a = settings.account.value ?: return ""
        val local = if (com.marco.audiopro.data.local.LocalMode.isOn(settings)) "|$id" else ""
        return hash("cmd|${a.url}|${a.user}|${settings.connectSecret().orEmpty()}$local")
    }

    private fun pairKey(secret: String) = hash("pair|$secret")

    /** La clave con que se le habla a [d]: la de la cuenta o la del emparejamiento. */
    private fun keyFor(d: RemoteDevice): String = if (d.paired) pairs.secret(d.id)?.let(::pairKey).orEmpty() else accountKey()

    private enum class Trust { NONE, PAIRED, ACCOUNT }

    private fun trustOf(c: Command): Trust = when {
        c.account.isEmpty() -> Trust.NONE
        c.account == accountKey() -> Trust.ACCOUNT
        pairs.secret(c.fromId)?.let { c.account == pairKey(it) } == true -> Trust.PAIRED
        else -> Trust.NONE
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
                seen.values.removeIf { now - it.dev.lastSeen >= STALE_MS }
                publish()
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
                val account = beaconKey()
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
            if (b.id == id) continue
            val dev = RemoteDevice(b.id, b.name, b.platform, packet.address, b.port, b.np, System.currentTimeMillis())
            seen[b.id] = Seen(dev, b.account.isNotEmpty() && b.account == beaconKey())
            publish()
        }
    }

    /** Reparte lo visto: los que controlas (tu cuenta o emparejados) y los demás. */
    @Synchronized private fun publish() {
        val all = seen.values.map { s -> s.dev.copy(paired = !s.mine && pairs.has(s.dev.id)) to s.mine }
        _devices.value = all.filter { (d, mine) -> mine || d.paired }.map { it.first }.sortedBy { it.name }
        _nearby.value = all.filter { (d, mine) -> !mine && !d.paired }.map { it.first }.sortedBy { it.name }
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
        val trust = cmd?.let(::trustOf) ?: Trust.NONE
        val reply = when {
            cmd == null -> Reply(false, error = "orden inválida")
            // Cualquiera de la red puede pedir; aquí se pregunta antes de hacer nada.
            cmd.cmd == "pair" -> pairRequested(cmd)
            cmd.cmd == "offer" -> onOffer?.let { h ->
                runCatching { h.offer(cmd.from, cmd.platform, s.inetAddress, cmd.payload.orEmpty()) }
                    .fold({ Reply(true, payload = it) }, { Reply(false, error = it.message) })
            } ?: Reply(false, error = "desconocida")
            trust == Trust.NONE -> Reply(false, error = "otra cuenta")
            // Los ajustes y las API keys sólo viajan entre dispositivos de tu cuenta.
            trust == Trust.PAIRED && cmd.cmd in ACCOUNT_ONLY -> Reply(false, error = "no permitido")
            else -> {
                val peerIp = if (trust == Trust.PAIRED) CastDiscovery.lanAddressFor(s.inetAddress.hostAddress) else null
                withContext(Dispatchers.Main) { execute(cmd, peerIp) }
            }
        }
        out.println(SubsonicJson.encodeToString(reply))
    }

    private fun localStatus(): RemoteStatus {
        val st = player.state.value
        return RemoteStatus(
            st.queue, st.currentIndex, st.isPlaying, player.positionMs(), st.durationMs, st.shuffle, st.repeat.name, player.volume?.value,
        )
    }

    /** @param peerIp si la orden viene de un emparejado: la dirección por la que le llega la música de aquí. */
    private fun execute(c: Command, peerIp: String?): Reply {
        val songs = c.songs.map(::restore)
        when (c.cmd) {
            "play" -> {
                // Si otro dispositivo nos manda música, dejamos de controlar al que fuera.
                stopControlling()
                player.playFrom(songs, c.index, c.positionMs)
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
            "playNext" -> player.playNext(songs)
            "enqueue" -> player.enqueue(songs)
            "story" -> {
                storyRequest.value = songs.firstOrNull() ?: return Reply(false, error = "sin canción")
                notice.value = "${c.from} te mandó una historia para compartir"
                return Reply(true)
            }
            // Entrega la cola para que otro dispositivo la "jale" (Traer aquí).
            "handoff" -> {
                val st = player.state.value
                val r = Reply(true, st.queue.map { forPeer(it, peerIp) }, st.currentIndex.coerceAtLeast(0), player.positionMs())
                if (st.isPlaying) player.toggle()
                return r
            }
            "unpair" -> {
                pairs.remove(c.fromId)
                if (_controlling.value?.id == c.fromId) stopControlling()
                publish()
                notice.value = "${c.from} ya no está emparejado"
                return Reply(true)
            }
            else -> return Reply(false, error = "desconocida")
        }
        val st = localStatus()
        return Reply(true, status = if (peerIp == null) st else st.copy(queue = st.queue.map { forPeer(it, peerIp) }))
    }

    // ------------------------------------------------------------ música para emparejados

    /**
     * Una canción de aquí lista para sonar en un emparejado (que no ve tu biblioteca): el audio y la
     * portada salen de este dispositivo. Muestras, radio y lo que ya viene de otro se quedan igual.
     */
    private fun forPeer(s: Song, peerIp: String?): Song {
        val r = relay
        if (peerIp == null || r == null || s.isPreview || s.isRadio) return s
        r.start()
        return s.copy(
            id = "peer:$id:${s.id}", externalUrl = r.audioUrl(peerIp, s.id, CastRelay.Mode.ORIGINAL),
            coverArt = s.coverArt?.let { r.coverUrl(peerIp, it) }, albumId = null, artistId = null,
        )
    }

    /** Lo que regresa de un emparejado y era de aquí vuelve a ser canción de tu biblioteca. */
    private fun restore(s: Song): Song {
        val prefix = "peer:$id:"
        if (!s.id.startsWith(prefix)) return s
        return s.copy(
            id = s.id.removePrefix(prefix), externalUrl = null,
            coverArt = s.coverArt?.substringAfterLast("/c/", "")?.takeIf { it.isNotEmpty() }?.let { URLDecoder.decode(it, "UTF-8") },
        )
    }

    private fun songsFor(to: RemoteDevice, songs: List<Song>): List<Song> =
        if (!to.paired) songs else CastDiscovery.lanAddressFor(to.address.hostAddress).let { ip -> songs.map { forPeer(it, ip) } }

    // ------------------------------------------------------------ emparejar

    private fun randomHex(bytes: Int) = ByteArray(bytes).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    /** Otro pide emparejarse: se pregunta aquí (con el código que también ve allá) y se espera la respuesta. */
    private suspend fun pairRequested(c: Command): Reply {
        if (c.fromId.isEmpty() || c.fromId == id) return Reply(false, error = "orden inválida")
        val req = PairRequest(c.from, c.platform, c.payload.orEmpty().take(8))
        if (!pairRequest.compareAndSet(null, req)) return Reply(false, error = "ocupado")
        val ok = withTimeoutOrNull(PROMPT_MS) { req.answer.await() } ?: false
        pairRequest.compareAndSet(req, null)
        if (!ok) return Reply(false, error = "rechazado")
        val secret = randomHex(32)
        pairs.put(c.fromId, c.from, secret)
        publish()
        notice.value = "Emparejado con ${c.from}"
        return Reply(true, payload = secret)
    }

    /** La respuesta de la UI a [pairRequest]. */
    fun answerPair(req: PairRequest, accept: Boolean) {
        req.answer.complete(accept)
        pairRequest.compareAndSet(req, null)
    }

    /** Emparejarse con otro Flacker de la red: allá sale el mismo código para aceptar. */
    suspend fun pair(d: RemoteDevice): Boolean {
        val code = "%04d".format(SecureRandom().nextInt(10_000))
        pairing.value = d to code
        val r = try {
            send(fresh(d), Command("pair", payload = code), timeoutMs = PROMPT_MS + 10_000)
        } finally { pairing.value = null }
        val secret = r?.payload
        if (r?.ok == true && secret != null) {
            pairs.put(d.id, d.name, secret)
            publish()
            notice.value = "Emparejado con ${d.name}"
            return true
        }
        notice.value = when (r?.error) {
            "rechazado" -> "${d.name} no aceptó"
            "ocupado" -> "${d.name} está ocupado, intenta otra vez"
            // Antes de la v3.2 no se sabía emparejar (y contestaba "otra cuenta").
            "desconocida", "otra cuenta" -> "${d.name} necesita actualizar Flacker"
            else -> "${d.name} no responde"
        }
        return false
    }

    /** [pair] desde la UI: sigue aunque se cierre la ventana. */
    fun startPairing(d: RemoteDevice) { if (pairing.value == null) scope.launch { pair(d) } }

    /** Dejar de estar emparejado (aquí y, si responde, allá). */
    fun unpair(d: RemoteDevice) {
        val dev = fresh(d)
        val cmd = Command("unpair", keyFor(dev), deviceName)
        pairs.remove(d.id)
        if (_controlling.value?.id == d.id) stopControlling()
        publish()
        scope.launch { send(dev, cmd) }
    }

    /** Una orden que se contesta con datos (p. ej. "offer"); si no, lanza el error del otro. */
    suspend fun request(to: RemoteDevice, cmd: String, payload: String, timeoutMs: Long): String {
        val r = send(fresh(to), Command(cmd, keyFor(fresh(to)), deviceName, payload = payload), timeoutMs)
        return if (r?.ok == true) r.payload.orEmpty() else throw Exception(r?.error ?: "sin respuesta")
    }

    private suspend fun send(to: RemoteDevice, cmd: Command, timeoutMs: Long = 6000): Reply? = withContext(Dispatchers.IO) {
        val full = cmd.copy(fromId = id, platform = platform)
        withTimeoutOrNull(timeoutMs) {
            runCatching {
                Socket().use { s ->
                    s.connect(InetSocketAddress(to.address, to.port), 3000)
                    s.soTimeout = (timeoutMs - 1000).toInt()
                    PrintWriter(s.getOutputStream().bufferedWriter(Charsets.UTF_8), true).println(SubsonicJson.encodeToString(full))
                    val line = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8)).readLine()
                    line?.let { SubsonicJson.decodeFromString<Reply>(it) }
                }
            }.getOrNull()
        }?.also { r ->
            if (r.error != "otra cuenta") return@also
            if (to.paired) {
                // El otro olvidó el emparejamiento (o se reinstaló): se olvida aquí también.
                pairs.remove(to.id)
                if (_controlling.value?.id == to.id) stopControlling()
                publish()
                notice.value = "${to.name} ya no está emparejado; vuelve a emparejarlo"
            } else if (cmd.cmd != "pair" && cmd.cmd != "offer") {
                // Mismo servidor y usuario, pero el otro inició sesión con otra contraseña (o antes de la v3).
                notice.value = "Para usar ${to.name}, cierra sesión y vuelve a entrar en los dos dispositivos"
            }
        }
    }

    /** Manda lo que suena aquí a otro dispositivo (misma cola y posición) y pausa aquí. */
    suspend fun transferTo(to: RemoteDevice): Boolean {
        val (songs, index, pos) = withContext(Dispatchers.Main) {
            val st = player.state.value
            Triple(st.queue, st.currentIndex.coerceAtLeast(0), player.positionMs())
        }
        if (songs.isEmpty()) { notice.value = "No hay nada sonando aquí"; return false }
        val r = send(fresh(to), Command("play", keyFor(fresh(to)), deviceName, songsFor(fresh(to), songs), index, pos))
        if (r?.ok == true) {
            withContext(Dispatchers.Main) { if (player.state.value.isPlaying) player.toggle() }
            notice.value = "Ahora suena en ${to.name}"
            // Como en Spotify: después de pasarla, la app controla ese dispositivo.
            _controlling.value = to
            r.status?.let { _remote.value = restored(it) to System.currentTimeMillis() }
            return true
        }
        failed(r, "No se pudo conectar con ${to.name}")
        return false
    }

    /** Trae aquí lo que suena en otro dispositivo (y lo pausa allá). */
    suspend fun pullFrom(from: RemoteDevice): Boolean {
        val r = send(fresh(from), Command("handoff", keyFor(fresh(from)), deviceName))
        if (r?.ok == true && r.songs.isNotEmpty()) {
            stopControlling()
            withContext(Dispatchers.Main) { player.playFrom(r.songs.map(::restore), r.index, r.positionMs) }
            notice.value = "Traído de ${from.name}"
            return true
        }
        failed(r, "No se pudo traer de ${from.name}")
        return false
    }

    /** Control remoto: toggle / next / previous / seek / skip… (responde con el estado nuevo). */
    suspend fun remote(to: RemoteDevice, cmd: String, index: Int = 0, positionMs: Long = 0, songs: List<Song> = emptyList()) {
        val dev = fresh(to)
        val r = send(dev, Command(cmd, keyFor(dev), deviceName, songsFor(dev, songs), index, positionMs))
        if (r?.ok != true) { failed(r, "${to.name} no responde"); return }
        if (_controlling.value?.id == to.id) r.status?.let { _remote.value = restored(it) to System.currentTimeMillis() }
    }

    /** Pide datos de sincronización a otro dispositivo ("manifest" o "blob"). */
    suspend fun ask(d: RemoteDevice, cmd: String, key: String = ""): String? =
        send(fresh(d), Command(cmd, keyFor(fresh(d)), deviceName, key = key), timeoutMs = 25_000)?.takeIf { it.ok }?.payload

    /** Avisa a los demás que algo cambió aquí (me gusta, playlists, portadas, ajustes). */
    /** Manda una canción al celular para compartirla en historias de Instagram desde ahí. */
    suspend fun sendStory(to: RemoteDevice, song: Song): Boolean {
        val dev = fresh(to)
        val r = send(dev, Command("story", keyFor(dev), deviceName, songsFor(dev, listOf(song))))
        val ok = r?.ok == true
        if (ok) notice.value = "Listo: ábrela en ${to.name} y compártela" else failed(r, "${to.name} no responde")
        return ok
    }

    fun broadcastRefresh() {
        val list = _devices.value.filter { !it.paired }
        if (list.isEmpty()) return
        scope.launch(Dispatchers.IO) { list.forEach { d -> send(fresh(d), Command("refresh", keyFor(fresh(d)), deviceName)) } }
    }

    /** Aviso de error, salvo que ya se explicó que hay que volver a iniciar sesión. */
    private fun failed(r: Reply?, msg: String) { if (r?.error != "otra cuenta") notice.value = msg }

    /** Pasa lo que suena en `from` (otro dispositivo) a `to` y controla `to`. */
    suspend fun moveBetween(from: RemoteDevice, to: RemoteDevice): Boolean {
        val h = send(fresh(from), Command("handoff", keyFor(fresh(from)), deviceName))
        if (h?.ok != true || h.songs.isEmpty()) { failed(h, "No se pudo mover desde ${from.name}"); return false }
        val dev = fresh(to)
        val r = send(dev, Command("play", keyFor(dev), deviceName, songsFor(dev, h.songs.map(::restore)), h.index, h.positionMs))
        if (r?.ok != true) { failed(r, "No se pudo conectar con ${to.name}"); return false }
        _controlling.value = to
        r.status?.let { _remote.value = restored(it) to System.currentTimeMillis() }
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
    private fun fresh(d: RemoteDevice) = _devices.value.firstOrNull { it.id == d.id } ?: _nearby.value.firstOrNull { it.id == d.id } ?: d

    private fun restored(st: RemoteStatus) = st.copy(queue = st.queue.map(::restore))

    private suspend fun pollLoop() {
        var fails = 0
        while (scope.isActive) {
            val d = _controlling.value
            if (d != null) {
                val r = send(fresh(d), Command("status", keyFor(fresh(d)), deviceName))
                if (_controlling.value?.id == d.id) {
                    if (r?.status != null) {
                        fails = 0
                        _remote.value = restored(r.status) to System.currentTimeMillis()
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
                send(fresh(d), Command("volume", keyFor(fresh(d)), deviceName, value = v))
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
        /** Cuánto se espera a que alguien diga que sí (emparejar, recibir canciones). */
        const val PROMPT_MS = 60_000L
        /** Órdenes sólo para tu cuenta: ajustes, API keys y portadas propias. */
        private val ACCOUNT_ONLY = setOf("manifest", "blob", "refresh")
    }
}
