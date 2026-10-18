package com.marco.audiopro.cast

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * Chromecast (protocolo CASTV2): conexión TLS al puerto 8009, mensajes protobuf con JSON adentro.
 * Abre el "Default Media Receiver" de Google (la app de reproducción que trae todo Chromecast) y le
 * manda la canción con su portada; play, pausa, adelantar y volumen van por el mismo canal.
 */
internal class ChromecastSession(override val device: CastDevice, private val scope: CoroutineScope) : CastSession {
    private companion object {
        const val APP = "CC1AD845" // Default Media Receiver
        const val NS_CONN = "urn:x-cast:com.google.cast.tp.connection"
        const val NS_BEAT = "urn:x-cast:com.google.cast.tp.heartbeat"
        const val NS_RECV = "urn:x-cast:com.google.cast.receiver"
        const val NS_MEDIA = "urn:x-cast:com.google.cast.media"
        const val ME = "sender-flacker"
    }

    private lateinit var sock: SSLSocket
    private lateinit var out: DataOutputStream
    private val req = AtomicInteger(1)
    private val waiting = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()
    private var appReady = CompletableDeferred<Unit>()
    @Volatile private var transportId: String? = null
    @Volatile private var sessionId: String? = null
    @Volatile private var mediaSessionId: Int? = null
    @Volatile private var state = CastState.IDLE
    @Volatile private var position = 0.0
    @Volatile private var positionAt = 0L
    @Volatile private var duration = 0.0
    @Volatile private var volume: Float? = null
    @Volatile private var ended = false
    private var beat: Job? = null

    suspend fun connect() = withContext(Dispatchers.IO) {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?) {}
            override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }
        sock = (ctx.socketFactory.createSocket() as SSLSocket).apply { connect(InetSocketAddress(device.host, device.port), 6000); startHandshake() }
        out = DataOutputStream(sock.outputStream)
        Thread({ readLoop() }, "flacker-cast-read").apply { isDaemon = true }.start()
        send(NS_CONN, "receiver-0", buildJsonObject { put("type", "CONNECT") })
        beat = scope.launch(Dispatchers.IO) { while (true) { delay(5000); runCatching { send(NS_BEAT, "receiver-0", buildJsonObject { put("type", "PING") }) } } }
        send(NS_RECV, "receiver-0", buildJsonObject { put("type", "LAUNCH"); put("appId", APP); put("requestId", req.getAndIncrement()) })
        withTimeout(20_000) { appReady.await() }
        send(NS_CONN, transportId!!, buildJsonObject { put("type", "CONNECT") })
    }

    // ---------------------------------------------------------------- mensajes

    private fun frame(ns: String, dst: String, payload: String): ByteArray {
        val b = ByteArrayOutputStream()
        fun varint(v: Int) { var x = v; while (x and 0x7F.inv() != 0) { b.write((x and 0x7F) or 0x80); x = x ushr 7 }; b.write(x) }
        fun str(field: Int, s: String) { val bytes = s.toByteArray(); b.write((field shl 3) or 2); varint(bytes.size); b.write(bytes) }
        b.write(0x08); varint(0) // protocol_version = CASTV2_1_0
        str(2, ME); str(3, dst); str(4, ns)
        b.write(0x28); varint(0) // payload_type = STRING
        str(6, payload)
        return b.toByteArray()
    }

    private fun send(ns: String, dst: String, msg: JsonObject) {
        val bytes = frame(ns, dst, msg.toString())
        synchronized(this) { out.writeInt(bytes.size); out.write(bytes); out.flush() }
    }

    private suspend fun request(ns: String, dst: String, msg: JsonObject, timeoutMs: Long = 20_000): JsonObject {
        val id = req.getAndIncrement()
        val d = CompletableDeferred<JsonObject>()
        waiting[id] = d
        withContext(Dispatchers.IO) { send(ns, dst, JsonObject(msg + ("requestId" to JsonPrimitive(id)))) }
        return try { withTimeout(timeoutMs) { d.await() } } finally { waiting.remove(id) }
    }

    private fun readLoop() {
        val inp = DataInputStream(sock.inputStream)
        try {
            while (true) {
                val len = inp.readInt()
                if (len <= 0 || len > 1_000_000) break
                val buf = ByteArray(len).also { inp.readFully(it) }
                val (ns, payload) = decode(buf)
                val msg = runCatching { Json.parseToJsonElement(payload ?: continue).jsonObject }.getOrNull() ?: continue
                onMessage(ns, msg)
            }
        } catch (_: Exception) {}
        ended = true
        appReady.completeExceptionally(IllegalStateException("Se cerró la conexión"))
    }

    private fun decode(b: ByteArray): Pair<String?, String?> {
        var i = 0
        fun varint(): Long { var r = 0L; var s = 0; while (true) { val x = b[i++].toInt() and 0xFF; r = r or ((x and 0x7F).toLong() shl s); if (x and 0x80 == 0) return r; s += 7 } }
        var ns: String? = null; var payload: String? = null
        while (i < b.size) {
            val key = varint().toInt(); val field = key ushr 3
            when (key and 7) {
                0 -> varint()
                2 -> { val l = varint().toInt(); val s = String(b, i, l, Charsets.UTF_8); i += l; if (field == 4) ns = s; if (field == 6) payload = s }
                5 -> i += 4
                1 -> i += 8
                else -> return ns to payload
            }
        }
        return ns to payload
    }

    private fun str(o: JsonObject?, k: String) = (o?.get(k) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun onMessage(ns: String?, m: JsonObject) {
        val type = str(m, "type")
        when (ns) {
            NS_BEAT -> if (type == "PING") runCatching { send(NS_BEAT, "receiver-0", buildJsonObject { put("type", "PONG") }) }
            NS_CONN -> if (type == "CLOSE") ended = true
            NS_RECV -> if (type == "RECEIVER_STATUS") {
                val st = m["status"]?.jsonObject
                st?.get("volume")?.jsonObject?.get("level")?.jsonPrimitive?.doubleOrNull?.let { volume = it.toFloat() }
                val app = (st?.get("applications") as? JsonArray)?.map { it.jsonObject }?.firstOrNull { str(it, "appId") == APP }
                if (app != null) {
                    transportId = str(app, "transportId"); sessionId = str(app, "sessionId")
                    appReady.complete(Unit)
                } else if (appReady.isCompleted && transportId != null) ended = true // alguien más cerró o cambió de app
            }
            NS_MEDIA -> when (type) {
                "MEDIA_STATUS" -> (m["status"] as? JsonArray)?.firstOrNull()?.jsonObject?.let { s ->
                    s["mediaSessionId"]?.jsonPrimitive?.intOrNull?.let { mediaSessionId = it }
                    s["currentTime"]?.jsonPrimitive?.doubleOrNull?.let { position = it; positionAt = System.currentTimeMillis() }
                    s["media"]?.jsonObject?.get("duration")?.jsonPrimitive?.doubleOrNull?.let { duration = it }
                    s["volume"]?.jsonObject?.get("level")?.jsonPrimitive?.doubleOrNull?.let { volume = it.toFloat() }
                    state = when (str(s, "playerState")) {
                        "PLAYING" -> CastState.PLAYING
                        "PAUSED" -> CastState.PAUSED
                        "BUFFERING", "LOADING" -> CastState.LOADING
                        "IDLE" -> when (str(s, "idleReason")) { "FINISHED" -> CastState.FINISHED; "ERROR" -> CastState.ERROR; else -> CastState.IDLE }
                        else -> state
                    }
                }
                "LOAD_FAILED", "LOAD_CANCELLED", "INVALID_REQUEST" -> state = CastState.ERROR
            }
        }
        m["requestId"]?.jsonPrimitive?.intOrNull?.let { waiting[it]?.complete(m) }
    }

    // ---------------------------------------------------------------- CastSession

    override suspend fun load(url: String, mime: String, meta: CastMeta, startMs: Long) {
        val t = transportId ?: error("Sin sesión")
        state = CastState.LOADING; mediaSessionId = null
        val r = request(NS_MEDIA, t, buildJsonObject {
            put("type", "LOAD"); put("sessionId", sessionId); put("autoplay", true); put("currentTime", startMs / 1000.0)
            putJsonObject("media") {
                put("contentId", url); put("contentType", mime); put("streamType", "BUFFERED")
                if (meta.durationMs > 0) put("duration", meta.durationMs / 1000.0)
                putJsonObject("metadata") {
                    put("metadataType", 3) // MusicTrackMediaMetadata
                    put("title", meta.title); meta.artist?.let { put("artist", it) }; meta.album?.let { put("albumName", it) }
                    meta.coverUrl?.let { u -> putJsonArray("images") { add(buildJsonObject { put("url", u) }) } }
                }
            }
        })
        if (str(r, "type") != "MEDIA_STATUS") { state = CastState.ERROR; error("El Chromecast no pudo abrir la canción") }
    }

    private fun media(type: String, extra: JsonObject.() -> JsonObject = { this }) {
        val t = transportId ?: return; val ms = mediaSessionId ?: return
        send(NS_MEDIA, t, buildJsonObject { put("type", type); put("mediaSessionId", ms); put("requestId", req.getAndIncrement()) }.extra())
    }

    override suspend fun play() = withContext(Dispatchers.IO) { media("PLAY") }
    override suspend fun pause() = withContext(Dispatchers.IO) { media("PAUSE") }
    override suspend fun seek(ms: Long) = withContext(Dispatchers.IO) {
        media("SEEK") { JsonObject(this + ("currentTime" to JsonPrimitive(ms / 1000.0))) }
        position = ms / 1000.0; positionAt = System.currentTimeMillis()
    }
    override suspend fun setVolume(v: Float) = withContext(Dispatchers.IO) {
        send(NS_RECV, "receiver-0", buildJsonObject {
            put("type", "SET_VOLUME"); put("requestId", req.getAndIncrement()); putJsonObject("volume") { put("level", v.coerceIn(0f, 1f).toDouble()) }
        })
        volume = v
    }

    override suspend fun poll(): CastStatus {
        withContext(Dispatchers.IO) { runCatching { media("GET_STATUS") } }
        val now = if (state == CastState.PLAYING && positionAt > 0) position + (System.currentTimeMillis() - positionAt) / 1000.0 else position
        return CastStatus(state, (now * 1000).toLong(), (duration * 1000).toLong(), volume, ended)
    }

    override suspend fun close(stopApp: Boolean) = withContext(Dispatchers.IO) {
        beat?.cancel()
        runCatching {
            if (stopApp) sessionId?.let { s -> send(NS_RECV, "receiver-0", buildJsonObject { put("type", "STOP"); put("sessionId", s); put("requestId", req.getAndIncrement()) }) }
            transportId?.let { send(NS_CONN, it, buildJsonObject { put("type", "CLOSE") }) }
            send(NS_CONN, "receiver-0", buildJsonObject { put("type", "CLOSE") })
        }
        runCatching { sock.close() }
        Unit
    }
}
