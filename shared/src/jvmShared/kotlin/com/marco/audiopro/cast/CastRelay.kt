package com.marco.audiopro.cast

import com.marco.audiopro.data.StreamQuality
import com.marco.audiopro.data.subsonic.SubsonicApi
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Puente para la tele: un servidor HTTP en la red local que le pasa a la tele el audio y la portada
 * que esta app pide a tu servidor (o a tu biblioteca local). Así la tele sólo necesita ver a este
 * dispositivo, aunque tu Navidrome esté por Tailscale, en otra red o no exista (modo sin servidor).
 * Cada dirección lleva una clave al azar: nadie más de la red puede pedir nada.
 */
class CastRelay(http: OkHttpClient, private val api: SubsonicApi) {
    private val client = http.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()
    private val key = ByteArray(12).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private var socket: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "flacker-cast-relay").apply { isDaemon = true } }

    val port: Int get() = socket?.localPort ?: 0

    @Synchronized fun start(): Int {
        socket?.let { return it.localPort }
        val any = InetAddress.getByName("0.0.0.0")
        val s = runCatching { ServerSocket(45334, 50, any) }.getOrElse { ServerSocket(0, 50, any) }
        socket = s
        Thread({
            while (!s.isClosed) {
                val c = runCatching { s.accept() }.getOrNull() ?: break
                pool.execute { runCatching { handle(c) }; runCatching { c.close() } }
            }
        }, "flacker-cast-accept").apply { isDaemon = true }.start()
        return s.localPort
    }

    @Synchronized fun stop() { runCatching { socket?.close() }; socket = null }

    /** Dirección del audio para la tele; [mp3] = convertido a MP3 320 (para aparatos que no aceptan el original). */
    fun audioUrl(lanIp: String, songId: String, mp3: Boolean) = "http://$lanIp:$port/$key/a/${enc(songId)}" + if (mp3) "?mp3=1" else ""
    fun coverUrl(lanIp: String, coverArt: String) = "http://$lanIp:$port/$key/c/${enc(coverArt)}"

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    private fun handle(sock: Socket) {
        sock.soTimeout = 30_000
        val input = BufferedInputStream(sock.getInputStream())
        val line = readLine(input) ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val l = readLine(input) ?: break
            if (l.isEmpty()) break
            val i = l.indexOf(':')
            if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
        }
        val method = line.substringBefore(' ')
        val target = line.split(' ').getOrNull(1) ?: return
        val parts = target.substringBefore('?').trim('/').split('/')
        val out = sock.getOutputStream()
        fun fail(code: Int) { out.write("HTTP/1.1 $code No\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); out.flush() }
        if (parts.size != 3 || parts[0] != key) return fail(404)
        val id = java.net.URLDecoder.decode(parts[2], "UTF-8")
        val upstream = when (parts[1]) {
            "a" -> api.streamUrl(id, if (target.contains("mp3=1")) StreamQuality.MP3_320 else StreamQuality.ORIGINAL)
            "c" -> api.coverUrl(id, 800)
            else -> null
        } ?: return fail(404)
        val req = Request.Builder().url(upstream).apply { headers["range"]?.let { header("Range", it) } }.build()
        client.newCall(req).execute().use { r ->
            val body = r.body
            val sb = StringBuilder("HTTP/1.1 ${r.code} ${if (r.code == 206) "Partial Content" else "OK"}\r\n")
            sb.append("Content-Type: ").append(r.header("Content-Type") ?: "application/octet-stream").append("\r\n")
            r.header("Content-Length")?.let { sb.append("Content-Length: ").append(it).append("\r\n") }
            r.header("Content-Range")?.let { sb.append("Content-Range: ").append(it).append("\r\n") }
            if (r.header("Accept-Ranges") != null || r.code == 206) sb.append("Accept-Ranges: bytes\r\n")
            sb.append("Access-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n")
            out.write(sb.toString().toByteArray())
            if (method != "HEAD" && body != null) body.byteStream().use { it.copyTo(out, 64 * 1024) }
            out.flush()
        }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
        }
    }
}
