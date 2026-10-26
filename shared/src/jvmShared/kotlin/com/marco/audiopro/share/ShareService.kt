package com.marco.audiopro.share

import com.marco.audiopro.connect.ConnectService
import com.marco.audiopro.connect.OfferHandler
import com.marco.audiopro.connect.RemoteDevice
import com.marco.audiopro.data.Settings
import com.marco.audiopro.data.StreamQuality
import com.marco.audiopro.data.local.LocalLibrary
import com.marco.audiopro.data.local.LocalMode
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.data.subsonic.SubsonicJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Dónde se guardan las canciones que te mandan (lo da cada plataforma). */
interface ShareInbox {
    /** Para mostrar: "C:\Users\…\Music\Flacker" o "Music/Flacker". */
    val label: String
    /** Si ya está ese archivo (mismo nombre y tamaño): no se vuelve a mandar. */
    fun has(dirs: List<String>, name: String, size: Long): Boolean
    /** Pasa el archivo recibido ([tmp]) a su lugar; false si no se pudo. */
    fun save(tmp: File, dirs: List<String>, name: String, mime: String): Boolean
    /** Al terminar de recibir (en la PC: que la biblioteca sin servidor incluya la carpeta). */
    suspend fun afterBatch() {}
}

/** Una canción de un envío, lo que el otro ve antes de aceptar. */
@Serializable
data class ShareFile(
    val fid: Int,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val track: Int? = null,
    val disc: Int? = null,
    val suffix: String = "",
    val size: Long = 0,
)

@Serializable private data class Offer(val files: List<ShareFile>)
@Serializable private data class Accept(val port: Int, val token: String, val skip: List<Int> = emptyList())
/** Encabezado de cada archivo en la conexión de subida (o el aviso de fin). */
@Serializable private data class Frame(val token: String, val fid: Int = -1, val size: Long = 0, val end: Boolean = false, val cancel: Boolean = false)
@Serializable private data class FrameReply(val ok: Boolean, val error: String? = null)

/** Un envío de aquí a otro o de otro a aquí (para la barra de progreso). */
data class Transfer(
    val id: String,
    val peer: String,
    val outgoing: Boolean,
    val total: Int,
    val done: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val bytes: Long = 0,
    val totalBytes: Long = 0,
    val current: String? = null,
    val state: State = State.WAITING,
    val message: String? = null,
) {
    enum class State { WAITING, ACTIVE, DONE, FAILED, CANCELLED }
    val finished: Boolean get() = state == State.DONE || state == State.FAILED || state == State.CANCELLED
    val progress: Float get() = if (totalBytes > 0) (bytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

/** Lo que te quieren mandar, esperando que digas que sí o no. */
class IncomingOffer(val from: String, val platform: String, val files: List<ShareFile>) {
    internal val answer = CompletableDeferred<Boolean>()
    val totalBytes: Long get() = files.sumOf { it.size }
}

/**
 * Mandar canciones a otro Flacker de la misma red, al estilo LocalSend: el otro ve qué le mandas y
 * acepta; luego cada archivo original (de tu Navidrome o de tus archivos) viaja directo por la red,
 * con su huella SHA-256 para comprobar que llegó completo. Funciona entre cualquier par de Flacker,
 * aunque sean de otra cuenta o no tengan servidor.
 *
 * El aviso ("offer") viaja por Connect; los archivos, por una conexión aparte que sólo acepta la
 * clave al azar de un envío aceptado y desde la dirección de quien lo mandó.
 *
 * @param onAttention Android: aviso en la barra cuando alguien quiere mandarte algo con la app atrás.
 * @param keepAlive Android: aviso fijo mientras hay envíos (si no, el sistema duerme la app); null = ya no.
 */
class ShareService(
    private val connect: ConnectService,
    private val api: SubsonicApi,
    http: OkHttpClient,
    val inbox: ShareInbox,
    private val tmpDir: File,
    private val library: LocalLibrary?,
    private val settings: Settings,
    private val scope: CoroutineScope,
    private val onAttention: (String) -> Unit = {},
    private val keepAlive: (String?) -> Unit = {},
) {
    private val client = http.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()

    private val _transfers = MutableStateFlow<List<Transfer>>(emptyList())
    val transfers: StateFlow<List<Transfer>> = _transfers.asStateFlow()

    private val _incoming = MutableStateFlow<IncomingOffer?>(null)
    /** Alguien te quiere mandar canciones (la UI pregunta). */
    val incoming: StateFlow<IncomingOffer?> = _incoming.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()
    /** Conexión de subida abierta de cada envío (para cortarla al cancelar). */
    private val sockets = ConcurrentHashMap<String, Socket>()

    init {
        tmpDir.mkdirs()
        // Restos de envíos que se cortaron con la app cerrada.
        tmpDir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
        connect.onOffer = OfferHandler { from, platform, address, payload -> received(from, platform, address, payload) }
        scope.launch {
            transfers.map { l -> l.firstOrNull { !it.finished }?.let { if (it.outgoing) "Mandando canciones a ${it.peer}" else "Recibiendo canciones de ${it.peer}" } }
                .distinctUntilChanged()
                .collect { runCatching { keepAlive(it) } }
        }
    }

    private fun newId() = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    private fun edit(id: String, f: (Transfer) -> Transfer) = synchronized(_transfers) {
        _transfers.value = _transfers.value.map { if (it.id == id) f(it) else it }
    }

    private fun add(t: Transfer) = synchronized(_transfers) { _transfers.value = _transfers.value + t }

    /** Quitar de la lista uno que ya terminó. */
    fun dismiss(id: String) = synchronized(_transfers) { _transfers.value = _transfers.value.filter { it.id != id || !it.finished } }

    /** Cancelar un envío (de ida o de vuelta). */
    fun cancel(id: String) {
        val t = _transfers.value.firstOrNull { it.id == id } ?: return
        if (t.finished) return
        if (t.outgoing) {
            jobs.remove(id)?.cancel()
            sockets.remove(id)?.let { runCatching { it.close() } }
            edit(id) { it.copy(state = Transfer.State.CANCELLED, current = null, message = "Cancelaste el envío") }
        } else {
            sessions.values.firstOrNull { it.tid == id }?.let { s ->
                s.cancelled = true
                runCatching { s.socket?.close() }
                scope.launch(Dispatchers.IO) { finishReceive(s, "Cancelaste") }
            }
        }
    }

    // ---------------------------------------------------------------- mandar

    /** Manda canciones (los archivos originales) a otro Flacker de la red. */
    fun send(to: RemoteDevice, songs: List<Song>) {
        val list = songs.filter { !it.isPreview && !it.isRadio }.distinctBy { it.id }
        if (list.isEmpty()) return
        val t = Transfer(newId(), to.name, outgoing = true, total = list.size, totalBytes = list.sumOf { it.size ?: 0 })
        add(t)
        jobs[t.id] = scope.launch(Dispatchers.IO) {
            try { runSend(t.id, to, list) } finally { jobs.remove(t.id); sockets.remove(t.id) }
        }
    }

    private suspend fun runSend(tid: String, to: RemoteDevice, songs: List<Song>) {
        val files = songs.mapIndexed { i, s ->
            ShareFile(i, s.title, s.artist, s.album, s.track, s.discNumber, (s.suffix ?: "flac").lowercase(), s.size ?: 0)
        }
        val accept = try {
            connect.request(to, "offer", SubsonicJson.encodeToString(Offer(files)), ConnectService.PROMPT_MS + 15_000)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            val msg = when (e.message) {
                "rechazado" -> "${to.name} no aceptó"
                "ocupado" -> "${to.name} está recibiendo otra cosa, intenta en un rato"
                "desconocida", "otra cuenta" -> "${to.name} necesita actualizar Flacker"
                else -> "${to.name} no respondió"
            }
            edit(tid) { it.copy(state = Transfer.State.FAILED, message = msg) }
            return
        }.let { SubsonicJson.decodeFromString<Accept>(it) }
        val skip = accept.skip.toSet()
        edit(tid) { t ->
            t.copy(state = Transfer.State.ACTIVE, skipped = skip.size, totalBytes = songs.filterIndexed { i, _ -> i !in skip }.sumOf { it.size ?: 0 })
        }
        val host = (connect.devices.value + connect.nearby.value).firstOrNull { it.id == to.id }?.address ?: to.address
        var stop: String? = null
        try {
            for ((i, s) in songs.withIndex()) {
                if (i in skip) continue
                currentCoroutineContext().ensureActive()
                edit(tid) { it.copy(current = s.title) }
                val err = try { upload(tid, host, accept, i, s) } catch (e: java.io.IOException) { if (e is java.net.ConnectException) "sin conexión" else "se cortó" }
                currentCoroutineContext().ensureActive()
                when (err) {
                    null -> edit(tid) { it.copy(done = it.done + 1) }
                    "cancelado" -> { stop = "${to.name} canceló el envío"; break }
                    "sin conexión" -> { stop = "Se perdió la conexión con ${to.name}"; break }
                    else -> edit(tid) { it.copy(failed = it.failed + 1) }
                }
            }
        } finally {
            // Avisar que ya no llega nada (también si se canceló aquí).
            val cancelled = stop != null || !currentCoroutineContext().isActive
            withContext(NonCancellable) { runCatching { endFrame(host, accept, cancelled) } }
        }
        edit(tid) { t ->
            t.copy(
                state = if (stop != null) Transfer.State.CANCELLED else if (t.done + t.skipped == 0) Transfer.State.FAILED else Transfer.State.DONE,
                current = null,
                message = stop ?: summary(t, "a ${to.name}", "${to.name} ya las tenía"),
            )
        }
    }

    /** "Listo: 12 canciones a PC · 2 ya las tenía · 1 falló" */
    private fun summary(t: Transfer, where: String, had: String): String = buildString {
        val sent = t.total - t.skipped
        if (sent == 0) return "$had todas".replaceFirstChar { it.uppercase() }
        if (t.failed == 0) append("Listo: ")
        append(if (t.done == sent) "${t.done} ${if (t.done == 1) "canción" else "canciones"}" else "${t.done} de $sent").append(' ').append(where)
        if (t.skipped > 0) append(" · ${t.skipped} $had")
        if (t.failed > 0) append(" · ${t.failed} ${if (t.failed == 1) "falló" else "fallaron"}")
    }

    /** El archivo original: de tu servidor (o de tus archivos sin servidor); null si no se pudo. */
    private class Source(val input: InputStream, val length: Long, val cleanup: () -> Unit)

    private fun open(s: Song): Source? {
        for (url in listOf(api.downloadUrl(s.id), api.streamUrl(s.id, StreamQuality.ORIGINAL))) {
            val r = runCatching { client.newCall(Request.Builder().url(url).build()).execute() }.getOrNull() ?: continue
            val body = r.body
            val type = r.header("Content-Type").orEmpty()
            // Subsonic contesta los errores con 200 y un JSON/XML.
            if (!r.isSuccessful || body == null || "json" in type || "xml" in type) { r.close(); continue }
            val len = body.contentLength()
            if (len >= 0) return Source(body.byteStream(), len) { r.close() }
            // Sin tamaño: primero a un temporal (el otro necesita saber cuánto llega).
            val tmp = File.createTempFile("mandar", ".part", tmpDir)
            val ok = runCatching { r.use { body.byteStream().use { i -> tmp.outputStream().use { i.copyTo(it, 64 * 1024) } } } }.isSuccess
            if (ok) return Source(FileInputStream(tmp), tmp.length()) { tmp.delete() } else tmp.delete()
        }
        return null
    }

    /** Sube un archivo; null = llegó bien, si no el error del otro ("cancelado", "llegó dañado"…). */
    private fun upload(tid: String, host: InetAddress, a: Accept, fid: Int, s: Song): String? {
        val src = open(s) ?: return "no se pudo leer"
        try {
            Socket().use { sock ->
                sockets[tid] = sock
                sock.connect(InetSocketAddress(host, a.port), 5000)
                sock.soTimeout = 60_000
                val input = BufferedInputStream(sock.getInputStream())
                val out = sock.getOutputStream().buffered(64 * 1024)
                writeLine(out, SubsonicJson.encodeToString(Frame(a.token, fid, src.length)))
                val go = readReply(input)
                if (!go.ok) return go.error ?: "error"
                val md = MessageDigest.getInstance("SHA-256")
                val buf = ByteArray(64 * 1024)
                var left = src.length
                var pending = 0L
                while (left > 0) {
                    val n = src.input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (n < 0) throw EOFException()
                    out.write(buf, 0, n); md.update(buf, 0, n)
                    left -= n; pending += n
                    if (pending >= PROGRESS_STEP || left == 0L) { val p = pending; edit(tid) { it.copy(bytes = it.bytes + p) }; pending = 0 }
                }
                out.write(hex(md.digest()).toByteArray()); out.flush()
                val r = readReply(input)
                return if (r.ok) null else r.error ?: "error"
            }
        } finally {
            sockets.remove(tid)
            runCatching { src.input.close() }
            src.cleanup()
        }
    }

    private fun endFrame(host: InetAddress, a: Accept, cancel: Boolean) = Socket().use { sock ->
        sock.connect(InetSocketAddress(host, a.port), 3000)
        sock.soTimeout = 5000
        writeLine(sock.getOutputStream(), SubsonicJson.encodeToString(Frame(a.token, end = true, cancel = cancel)))
        readReply(BufferedInputStream(sock.getInputStream()))
    }

    // ---------------------------------------------------------------- recibir

    private class Session(
        val token: String,
        val tid: String,
        val from: String,
        val address: InetAddress,
        val files: Map<Int, ShareFile>,
        val skip: Set<Int>,
    ) {
        val received: MutableSet<Int> = ConcurrentHashMap.newKeySet()
        @Volatile var cancelled = false
        @Volatile var socket: Socket? = null
        @Volatile var lastActivity = System.currentTimeMillis()
        @Volatile var saved = 0
    }

    private val sessions = ConcurrentHashMap<String, Session>()
    private var server: ServerSocket? = null

    /** Alguien quiere mandarte canciones: se pregunta aquí y se espera la respuesta. */
    private suspend fun received(from: String, platform: String, address: InetAddress, payload: String): String {
        val offer = runCatching { SubsonicJson.decodeFromString<Offer>(payload) }.getOrNull()
        if (offer == null || offer.files.isEmpty() || offer.files.size > MAX_FILES || offer.files.map { it.fid }.toSet().size != offer.files.size) {
            throw Exception("orden inválida")
        }
        val req = IncomingOffer(from, platform, offer.files)
        if (!_incoming.compareAndSet(null, req)) throw Exception("ocupado")
        runCatching { onAttention("$from te quiere mandar ${offer.files.size} ${if (offer.files.size == 1) "canción" else "canciones"}") }
        val ok = withTimeoutOrNull(ConnectService.PROMPT_MS) { req.answer.await() } ?: false
        _incoming.compareAndSet(req, null)
        if (!ok) throw Exception("rechazado")
        val port = startServer() ?: throw Exception("no se pudo recibir")
        val skip = offer.files.filter { f -> val (d, n) = place(f); runCatching { inbox.has(d, n, f.size) }.getOrDefault(false) }.map { it.fid }.toSet()
        val tid = newId()
        val todo = offer.files.filter { it.fid !in skip }
        add(Transfer(tid, from, outgoing = false, total = offer.files.size, skipped = skip.size, totalBytes = todo.sumOf { it.size }, state = Transfer.State.ACTIVE))
        val s = Session(newId(), tid, from, address, offer.files.associateBy { it.fid }, skip)
        sessions[s.token] = s
        if (todo.isEmpty()) scope.launch(Dispatchers.IO) { finishReceive(s, null) } else watch(s)
        return SubsonicJson.encodeToString(Accept(port, s.token, skip.toList()))
    }

    /** La respuesta de la UI a [incoming]. */
    fun answer(offer: IncomingOffer, accept: Boolean) {
        offer.answer.complete(accept)
        _incoming.compareAndSet(offer, null)
    }

    /** Si el que manda desaparece sin avisar, el envío se cierra solo. */
    private fun watch(s: Session) = scope.launch(Dispatchers.IO) {
        while (sessions[s.token] === s) {
            delay(10_000)
            if (s.socket == null && System.currentTimeMillis() - s.lastActivity > IDLE_MS) finishReceive(s, "Se perdió la conexión con ${s.from}")
        }
    }

    @Synchronized private fun startServer(): Int? {
        server?.let { return it.localPort }
        val srv = runCatching { ServerSocket(0, 20, InetAddress.getByName("0.0.0.0")) }.getOrNull() ?: return null
        server = srv
        Thread({
            while (!srv.isClosed) {
                val c = runCatching { srv.accept() }.getOrNull() ?: break
                scope.launch(Dispatchers.IO) { c.use { runCatching { handle(it) } } }
            }
        }, "flacker-share-accept").apply { isDaemon = true }.start()
        return srv.localPort
    }

    private suspend fun handle(sock: Socket) {
        sock.soTimeout = 60_000
        val input = BufferedInputStream(sock.getInputStream())
        val out = sock.getOutputStream()
        fun reply(ok: Boolean, error: String? = null) = writeLine(out, SubsonicJson.encodeToString(FrameReply(ok, error)))
        val f = readLine(input)?.let { runCatching { SubsonicJson.decodeFromString<Frame>(it) }.getOrNull() } ?: return
        // Sólo con la clave de un envío aceptado y desde la misma dirección que lo pidió.
        val s = sessions[f.token]?.takeIf { it.address == sock.inetAddress && !it.cancelled } ?: return reply(false, "cancelado")
        s.lastActivity = System.currentTimeMillis()
        if (f.end) {
            reply(true)
            finishReceive(s, if (f.cancel) "${s.from} canceló el envío" else null)
            return
        }
        val file = s.files[f.fid]
        if (file == null || f.fid in s.skip || f.fid in s.received || f.size <= 0 || f.size > MAX_FILE_BYTES) return reply(false, "archivo inválido")
        if (tmpDir.usableSpace in 0 until f.size + 64L * 1024 * 1024) return reply(false, "no hay espacio")
        s.received += f.fid
        reply(true)
        s.socket = sock
        val tmp = File(tmpDir, "${s.token}-${f.fid}.part")
        try {
            val md = MessageDigest.getInstance("SHA-256")
            FileOutputStream(tmp).use { fo ->
                val buf = ByteArray(64 * 1024)
                var left = f.size
                var pending = 0L
                while (left > 0) {
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (n < 0) throw EOFException()
                    fo.write(buf, 0, n); md.update(buf, 0, n)
                    left -= n; pending += n
                    if (pending >= PROGRESS_STEP || left == 0L) {
                        val p = pending
                        edit(s.tid) { it.copy(bytes = it.bytes + p, current = file.title) }
                        pending = 0
                        s.lastActivity = System.currentTimeMillis()
                    }
                }
            }
            val sha = ByteArray(64).also { readFully(input, it) }.toString(Charsets.US_ASCII)
            if (sha != hex(md.digest())) {
                edit(s.tid) { it.copy(failed = it.failed + 1) }
                return reply(false, "llegó dañado")
            }
            val (dirs, name) = place(file)
            val saved = runCatching { inbox.save(tmp, dirs, name, LocalLibrary.mime(file.suffix)) }.getOrDefault(false)
            if (saved) s.saved++
            edit(s.tid) { if (saved) it.copy(done = it.done + 1) else it.copy(failed = it.failed + 1) }
            reply(saved, if (saved) null else "no se pudo guardar")
        } catch (e: java.io.IOException) {
            if (!s.cancelled) edit(s.tid) { it.copy(failed = it.failed + 1) }
        } finally {
            tmp.delete()
            s.socket = null
            s.lastActivity = System.currentTimeMillis()
        }
    }

    /** Cierra un envío recibido; con lo guardado, la biblioteca sin servidor se vuelve a leer. */
    private suspend fun finishReceive(s: Session, stopped: String?) {
        if (sessions.remove(s.token) == null) return
        if (s.saved > 0) {
            runCatching { inbox.afterBatch() }
            if (LocalMode.isOn(settings)) library?.let { runCatching { it.rescan() } }
        }
        edit(s.tid) { t ->
            t.copy(
                state = when {
                    stopped != null -> Transfer.State.CANCELLED
                    t.done + t.skipped == 0 -> Transfer.State.FAILED
                    else -> Transfer.State.DONE
                },
                current = null,
                message = (if (stopped != null) "$stopped · " else "") + summary(t, "de ${s.from}", "ya las tenías") +
                    if (s.saved > 0) " · en ${inbox.label}" else "",
            )
        }
    }

    companion object {
        private const val PROGRESS_STEP = 256L * 1024
        private const val IDLE_MS = 3 * 60_000L
        private const val MAX_FILES = 5000
        private const val MAX_FILE_BYTES = 8L * 1024 * 1024 * 1024

        /**
         * Carpeta y nombre de un archivo recibido: Artista/Álbum/01 - Título.flac. Todo sale limpio de
         * caracteres raros, así nadie puede escribir fuera de la carpeta de Flacker.
         */
        fun place(f: ShareFile): Pair<List<String>, String> {
            val num = f.track?.takeIf { it > 0 }?.let { t -> (f.disc?.takeIf { it > 1 }?.let { "$it-" } ?: "") + "%02d - ".format(t) } ?: ""
            val ext = clean(f.suffix, "").lowercase().filter { it.isLetterOrDigit() }.take(5).ifEmpty { "flac" }
            return listOf(clean(f.artist, "Desconocido"), clean(f.album, "Sin álbum")) to clean(num + f.title, "Canción") + "." + ext
        }

        private fun clean(s: String?, fallback: String): String {
            val c = s.orEmpty().map { ch -> if (ch < ' ' || ch in "<>:\"/\\|?*") ' ' else ch }.joinToString("")
                .replace(Regex("\\s+"), " ").trim().trim('.', ' ').take(80).trim()
            return c.takeIf { it.isNotEmpty() && it != "." && it != ".." } ?: fallback
        }

        private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

        private fun writeLine(out: OutputStream, s: String) { out.write((s + "\n").toByteArray(Charsets.UTF_8)); out.flush() }

        private fun readLine(input: BufferedInputStream, max: Int = 16 * 1024): String? {
            val b = java.io.ByteArrayOutputStream()
            while (true) {
                val c = input.read()
                if (c < 0) return if (b.size() == 0) null else b.toString("UTF-8")
                if (c == '\n'.code) return b.toString("UTF-8")
                if (b.size() >= max) return null
                b.write(c)
            }
        }

        private fun readReply(input: BufferedInputStream): FrameReply =
            readLine(input)?.let { runCatching { SubsonicJson.decodeFromString<FrameReply>(it) }.getOrNull() } ?: throw EOFException()

        private fun readFully(input: InputStream, buf: ByteArray) {
            var off = 0
            while (off < buf.size) { val n = input.read(buf, off, buf.size - off); if (n < 0) throw EOFException(); off += n }
        }
    }
}
