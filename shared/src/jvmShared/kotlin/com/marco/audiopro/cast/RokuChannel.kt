package com.marco.audiopro.cast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * La app "Flacker" para Roku. El "Roku Media Player" no acepta música de otras apps (Roku nunca abrió
 * eso), así que la tele necesita su propia app receptora: recibe la canción por el control de red
 * (ECP: /launch/dev y /input), la toca con su reproductor y muestra la portada, el nombre y el avance.
 * Se instala con el "modo desarrollador" de la tele (una vez): Flacker sube el paquete solo.
 */
object RokuChannel {
    private val MANIFEST = """
title=Flacker
subtitle=Tu musica en la tele
major_version=1
minor_version=0
build_version=1
ui_resolutions=hd
splash_color=#000000
splash_min_time=0
""".trimStart()

    private val MAIN = """
' Recibe las canciones de Flacker: al abrirla (/launch/dev?...) y mientras está abierta (/input?...).
sub Main(args as Dynamic)
    screen = CreateObject("roSGScreen")
    port = CreateObject("roMessagePort")
    screen.SetMessagePort(port)
    scene = screen.CreateScene("FlackerScene")
    screen.Show()
    inp = CreateObject("roInput")
    inp.SetMessagePort(port)
    if args <> invalid then scene.cmd = args
    while true
        msg = wait(0, port)
        t = type(msg)
        if t = "roSGScreenEvent" then
            if msg.isScreenClosed() then return
        else if t = "roInputEvent" then
            if msg.IsInput() then scene.cmd = msg.GetInfo()
        end if
    end while
end sub
""".trimStart()

    private val SCENE_XML = """
<?xml version="1.0" encoding="utf-8" ?>
<component name="FlackerScene" extends="Scene">
  <interface>
    <field id="cmd" type="assocarray" onChange="onCmd" />
  </interface>
  <script type="text/brightscript" uri="pkg:/components/FlackerScene.brs" />
  <children>
    <Rectangle id="bg" width="1280" height="720" color="0x000000FF" />
    <Poster id="back" width="1280" height="720" loadDisplayMode="scaleToZoom" opacity="0.22" />
    <Rectangle id="shade" width="1280" height="720" color="0x000000B0" />
    <Poster id="cover" translation="[110,150]" width="420" height="420" loadDisplayMode="scaleToFit" />
    <Label id="kicker" translation="[580,215]" text="FLACKER 333" font="font:SmallBoldSystemFont" color="0x9A9AA2FF" />
    <Label id="title" translation="[580,250]" width="600" wrap="true" maxLines="2" font="font:LargeBoldSystemFont" color="0xFFFFFFFF" />
    <Label id="artist" translation="[580,360]" width="600" font="font:MediumSystemFont" color="0xD8D8DEFF" />
    <Label id="album" translation="[580,400]" width="600" font="font:SmallSystemFont" color="0x9A9AA2FF" />
    <Rectangle id="barBg" translation="[580,470]" width="600" height="6" color="0xFFFFFF33" />
    <Rectangle id="bar" translation="[580,470]" width="0" height="6" color="0xFFFFFFFF" />
    <Label id="time" translation="[580,490]" width="600" font="font:SmallestSystemFont" color="0x9A9AA2FF" />
    <Label id="hint" translation="[110,640]" text="Controla la musica desde Flacker en tu PC o celular" font="font:SmallestSystemFont" color="0x6B6B73FF" />
    <Audio id="audio" />
  </children>
</component>
""".trimStart()

    private val SCENE_BRS = """
sub init()
    m.audio = m.top.findNode("audio")
    m.audio.observeField("position", "onPos")
    m.top.setFocus(true)
    m.duration = 0
end sub

sub onCmd()
    c = m.top.cmd
    if c = invalid then return
    url = c.contentId
    if url = invalid then url = ""
    if url <> "" then
        content = CreateObject("roSGNode", "ContentNode")
        content.url = url
        if c.format <> invalid then content.streamFormat = c.format
        if c.title <> invalid then content.title = c.title
        m.audio.content = content
        m.audio.control = "play"
        if c.position <> invalid then
            if Val(c.position) > 1 then m.audio.seek = Val(c.position)
        end if
        m.duration = 0
        if c.duration <> invalid then m.duration = Val(c.duration)
        setText("title", c.title)
        setText("artist", c.artist)
        setText("album", c.album)
        if c.art <> invalid then
            m.top.findNode("cover").uri = c.art
            m.top.findNode("back").uri = c.art
        end if
    end if
    if c.cmd <> invalid then
        if c.cmd = "pause" then m.audio.control = "pause"
        if c.cmd = "resume" then m.audio.control = "resume"
        if c.cmd = "stop" then m.audio.control = "stop"
        if c.cmd = "seek" then
            if c.position <> invalid then m.audio.seek = Val(c.position)
        end if
    end if
end sub

sub setText(id as String, v as Dynamic)
    if v = invalid then v = ""
    m.top.findNode(id).text = v
end sub

sub onPos()
    p = m.audio.position
    d = m.duration
    if m.audio.duration > 0 then d = m.audio.duration
    if d > 0 then m.top.findNode("bar").width = 600 * p / d
    m.top.findNode("time").text = fmt(p) + " / " + fmt(d)
end sub

function fmt(s as Float) as String
    t = Int(s)
    sec = t mod 60
    pad = ""
    if sec < 10 then pad = "0"
    return Str(Int(t / 60)).Trim() + ":" + pad + Str(sec).Trim()
end function

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    if key = "play" then
        if m.audio.state = "playing" then
            m.audio.control = "pause"
        else
            m.audio.control = "resume"
        end if
        return true
    end if
    if key = "fastforward" then
        m.audio.seek = m.audio.position + 10
        return true
    end if
    if key = "rewind" then
        m.audio.seek = m.audio.position - 10
        return true
    end if
    return false
end function
""".trimStart()

    /** El paquete .zip que se sube a la tele. */
    fun zip(): ByteArray {
        val bo = ByteArrayOutputStream()
        ZipOutputStream(bo).use { z ->
            fun put(name: String, text: String) { z.putNextEntry(ZipEntry(name)); z.write(text.replace("\r\n", "\n").toByteArray()); z.closeEntry() }
            put("manifest", MANIFEST)
            put("source/main.brs", MAIN)
            put("components/FlackerScene.xml", SCENE_XML)
            put("components/FlackerScene.brs", SCENE_BRS)
        }
        return bo.toByteArray()
    }

    private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Autenticación "Digest" del instalador de la tele (usuario rokudev). */
    private fun digest(password: String) = Authenticator { _, response ->
        val h = response.header("WWW-Authenticate") ?: return@Authenticator null
        if (response.request.header("Authorization") != null) return@Authenticator null // contraseña equivocada
        fun field(k: String) = Regex("""$k="?([^",]+)"?""").find(h)?.groupValues?.get(1)
        val realm = field("realm") ?: return@Authenticator null
        val nonce = field("nonce") ?: return@Authenticator null
        val qop = field("qop")
        val uri = response.request.url.encodedPath
        val ha1 = md5("rokudev:$realm:$password")
        val ha2 = md5("${response.request.method}:$uri")
        val nc = "00000001"
        val cnonce = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val resp = if (qop != null) md5("$ha1:$nonce:$nc:$cnonce:auth:$ha2") else md5("$ha1:$nonce:$ha2")
        val auth = buildString {
            append("""Digest username="rokudev", realm="$realm", nonce="$nonce", uri="$uri", response="$resp"""")
            if (qop != null) append(""", qop=auth, nc=$nc, cnonce="$cnonce"""")
        }
        response.request.newBuilder().header("Authorization", auth).build()
    }

    /** ¿La tele tiene prendido el modo desarrollador? (sólo así acepta la app). */
    suspend fun devMode(http: OkHttpClient, host: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url("http://$host:8060/query/device-info").build()).execute().use { it.body?.string().orEmpty() }
        }.getOrDefault("").contains("<developer-enabled>true</developer-enabled>")
    }

    /** ¿Ya está instalada la app de Flacker en la tele? */
    suspend fun installed(http: OkHttpClient, host: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url("http://$host:8060/query/apps").build()).execute().use { it.body?.string().orEmpty() }
        }.getOrDefault("").let { it.contains("id=\"dev\"") && it.contains(">Flacker<") }
    }

    /** Sube la app a la tele. Devuelve null si se instaló, o el error para mostrar. */
    suspend fun install(http: OkHttpClient, host: String, password: String): String? = withContext(Dispatchers.IO) {
        val client = http.newBuilder().authenticator(digest(password)).callTimeout(60, TimeUnit.SECONDS).build()
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("mysubmit", "Install")
            .addFormDataPart("archive", "flacker.zip", zip().toRequestBody("application/zip".toMediaType()))
            .build()
        runCatching {
            client.newCall(Request.Builder().url("http://$host/plugin_install").post(body).build()).execute().use { r ->
                val html = r.body?.string().orEmpty()
                when {
                    r.code == 401 -> "La contraseña no es la del modo desarrollador de la tele."
                    html.contains("Install Success", true) || html.contains("Identical to previous", true) -> null
                    html.contains("Install Failure", true) -> "La tele no aceptó la app: " + Regex("""Install Failure: ([^<"]+)""").find(html)?.groupValues?.get(1).orEmpty()
                    r.isSuccessful -> null
                    else -> "La tele respondió ${r.code}"
                }
            }
        }.getOrElse { "No se pudo conectar con el instalador de la tele (¿está prendido el modo desarrollador?): ${it.message}" }
    }
}

/** La tele es Roku pero aún no tiene la app de Flacker (hay que instalarla una vez). */
class RokuNeedsAppException : IllegalStateException("Tu Roku necesita la app de Flacker (se instala una sola vez).")
