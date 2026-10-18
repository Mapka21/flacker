package com.marco.audiopro.cast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/**
 * Teles y bocinas DLNA / UPnP (Samsung, LG, Sony, Denon, Yamaha, Kodi…): se les manda la dirección
 * de la canción con SetAVTransportURI + Play y se les pregunta en qué segundo van.
 */
internal class DlnaSession(override val device: CastDevice, http: OkHttpClient) : CastSession {
    private val http = http.newBuilder().callTimeout(6, TimeUnit.SECONDS).build()
    private val av = "urn:schemas-upnp-org:service:AVTransport:1"
    private val rc = "urn:schemas-upnp-org:service:RenderingControl:1"
    private var lastVolume: Float? = null

    private suspend fun soap(url: String, service: String, action: String, args: List<Pair<String, String>>): String = withContext(Dispatchers.IO) {
        val body = buildString {
            append("""<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body>""")
            append("<u:$action xmlns:u=\"$service\">")
            args.forEach { (k, v) -> append("<$k>").append(esc(v)).append("</$k>") }
            append("</u:$action></s:Body></s:Envelope>")
        }
        val r = http.newCall(
            Request.Builder().url(url).post(body.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
                .header("SOAPACTION", "\"$service#$action\"").build(),
        ).execute()
        r.use { val text = it.body?.string().orEmpty(); if (!it.isSuccessful) error("DLNA $action: HTTP ${it.code}"); text }
    }

    private fun tag(xml: String, name: String) = Regex("<(?:\\w+:)?$name[^>]*>(.*?)</(?:\\w+:)?$name>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)

    private fun hms(ms: Long): String { val s = ms / 1000; return "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) }
    private fun parseHms(t: String?): Long {
        val p = t?.substringBefore('.')?.split(':')?.mapNotNull { it.toLongOrNull() } ?: return 0
        return if (p.size == 3) (p[0] * 3600 + p[1] * 60 + p[2]) * 1000 else 0
    }

    override suspend fun load(url: String, mime: String, meta: CastMeta, startMs: Long) {
        val didl = buildString {
            append("""<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">""")
            append("""<item id="1" parentID="0" restricted="1"><dc:title>${esc(meta.title)}</dc:title>""")
            meta.artist?.let { append("<upnp:artist>${esc(it)}</upnp:artist><dc:creator>${esc(it)}</dc:creator>") }
            meta.album?.let { append("<upnp:album>${esc(it)}</upnp:album>") }
            meta.coverUrl?.let { append("<upnp:albumArtURI>${esc(it)}</upnp:albumArtURI>") }
            append("<upnp:class>object.item.audioItem.musicTrack</upnp:class>")
            append("""<res protocolInfo="http-get:*:$mime:*"${if (meta.durationMs > 0) " duration=\"${hms(meta.durationMs)}.000\"" else ""}>${esc(url)}</res></item></DIDL-Lite>""")
        }
        val ctl = device.avControl ?: error("Sin control")
        runCatching { soap(ctl, av, "Stop", listOf("InstanceID" to "0")) }
        soap(ctl, av, "SetAVTransportURI", listOf("InstanceID" to "0", "CurrentURI" to url, "CurrentURIMetaData" to didl))
        soap(ctl, av, "Play", listOf("InstanceID" to "0", "Speed" to "1"))
        if (startMs > 2000) runCatching { kotlinx.coroutines.delay(1200); seek(startMs) }
    }

    override suspend fun play() { soap(device.avControl!!, av, "Play", listOf("InstanceID" to "0", "Speed" to "1")) }
    override suspend fun pause() { soap(device.avControl!!, av, "Pause", listOf("InstanceID" to "0")) }
    override suspend fun seek(ms: Long) { soap(device.avControl!!, av, "Seek", listOf("InstanceID" to "0", "Unit" to "REL_TIME", "Target" to hms(ms))) }

    override suspend fun setVolume(v: Float) {
        val c = device.rcControl ?: return
        soap(c, rc, "SetVolume", listOf("InstanceID" to "0", "Channel" to "Master", "DesiredVolume" to (v * 100).toInt().coerceIn(0, 100).toString()))
        lastVolume = v
    }

    override suspend fun poll(): CastStatus {
        val ctl = device.avControl!!
        val info = soap(ctl, av, "GetTransportInfo", listOf("InstanceID" to "0"))
        val pos = runCatching { soap(ctl, av, "GetPositionInfo", listOf("InstanceID" to "0")) }.getOrDefault("")
        if (lastVolume == null) device.rcControl?.let { c ->
            runCatching { tag(soap(c, rc, "GetVolume", listOf("InstanceID" to "0", "Channel" to "Master")), "CurrentVolume")?.toFloatOrNull()?.let { lastVolume = it / 100f } }
        }
        val state = when (tag(info, "CurrentTransportState")) {
            "PLAYING" -> CastState.PLAYING
            "PAUSED_PLAYBACK", "PAUSED_RECORDING" -> CastState.PAUSED
            "TRANSITIONING" -> CastState.LOADING
            else -> CastState.IDLE
        }
        return CastStatus(state, parseHms(tag(pos, "RelTime")), parseHms(tag(pos, "TrackDuration")), lastVolume)
    }

    override suspend fun close(stopApp: Boolean) { if (stopApp) runCatching { soap(device.avControl!!, av, "Stop", listOf("InstanceID" to "0")) } }
}

/**
 * Teles y aparatos Roku, con la app "Flacker" de la tele ([RokuChannel]): se abre con la canción
 * (/launch/dev) y, ya abierta, se le mandan las siguientes y las órdenes (/input) por el control de
 * red (ECP, puerto 8060). La posición y el estado salen de /query/media-player. El volumen se maneja
 * con el control de la tele.
 */
internal class RokuSession(override val device: CastDevice, http: OkHttpClient) : CastSession {
    private val http = http.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()
    private val base = "http://${device.host}:${device.port}"

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(base + path).build()).execute().use { it.body?.string().orEmpty() }
            .also { if (it.contains("Limited mode", ignoreCase = true)) throw RokuLimitedException() }
    }

    private suspend fun post(path: String) = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(base + path).post(ByteArray(0).toRequestBody(null)).build()).execute().use {
            val text = it.body?.string().orEmpty()
            if (text.contains("Limited mode", ignoreCase = true) || it.code == 403) throw RokuLimitedException()
            if (!it.isSuccessful) error("Roku: HTTP ${it.code}")
        }
    }

    private fun e(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    /** Antes de empezar: control por red permitido y la app instalada. */
    suspend fun check() {
        val apps = get("/query/apps")
        if (!(apps.contains("id=\"dev\"") && apps.contains(">Flacker<"))) throw RokuNeedsAppException()
    }

    override suspend fun load(url: String, mime: String, meta: CastMeta, startMs: Long) {
        val fmt = when (mime.substringAfter('/').lowercase()) {
            "flac" -> "flac"; "mpeg", "mp3" -> "mp3"; "wav", "x-wav" -> "wav"; "x-ms-wma" -> "wma"; else -> "mp4"
        }
        val q = buildString {
            append("contentId=").append(e(url)).append("&format=").append(fmt).append("&title=").append(e(meta.title))
            meta.artist?.let { append("&artist=").append(e(it)) }
            meta.album?.let { append("&album=").append(e(it)) }
            meta.coverUrl?.let { append("&art=").append(e(it)) }
            if (meta.durationMs > 0) append("&duration=").append(meta.durationMs / 1000)
            if (startMs > 1000) append("&position=").append(startMs / 1000)
        }
        // Si ya está abierta, la canción entra sin volver a abrir la app (sin parpadeo).
        val open = runCatching { get("/query/active-app") }.getOrDefault("").contains("id=\"dev\"")
        post(if (open) "/input?$q" else "/launch/dev?$q")
    }

    override suspend fun play() = post("/input?cmd=resume")
    override suspend fun pause() = post("/input?cmd=pause")
    override suspend fun seek(ms: Long) = post("/input?cmd=seek&position=${ms / 1000}")
    override suspend fun setVolume(v: Float) {}

    override suspend fun poll(): CastStatus {
        val xml = get("/query/media-player")
        fun ms(tag: String) = Regex("<$tag>(\\d+) ms</$tag>").find(xml)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val st = Regex("state=\"(\\w+)\"").find(xml)?.groupValues?.get(1)
        val ours = xml.contains("id=\"dev\"")
        val state = when {
            !ours && st != null && st != "none" && st != "close" -> CastState.IDLE
            st == "play" -> CastState.PLAYING
            st == "pause" -> CastState.PAUSED
            st == "buffer" || st == "startup" -> CastState.LOADING
            st == "finished" -> CastState.FINISHED
            else -> CastState.IDLE
        }
        return CastStatus(state, ms("position"), ms("duration"), null)
    }

    override suspend fun close(stopApp: Boolean) {
        if (stopApp) runCatching { post("/input?cmd=stop"); post("/keypress/Home") }
    }
}

/** La Roku tiene el "Control por apps móviles" en Limitado: no acepta que otra app le mande música. */
class RokuLimitedException : IllegalStateException(
    "Tu Roku no deja que otras apps le manden música. En la tele: Configuración → Sistema → Configuración avanzada del sistema → " +
        "Control por apps móviles → Acceso a la red → Predeterminado.",
)
