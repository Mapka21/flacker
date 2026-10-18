package com.marco.audiopro.desktop

import com.marco.audiopro.cast.CastDevice
import com.marco.audiopro.cast.CastDiscovery
import com.marco.audiopro.cast.CastService
import com.marco.audiopro.data.ServerAccount
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.Collections

/**
 * Transmitir: buscar aparatos en la red de verdad (MARCO_CAST_SCAN=1) y mandar música a una tele
 * DLNA y a una Roku de mentiras que de verdad bajan y decodifican el audio por el puente
 * (MARCO_TEST_SERVER con el Navidrome falso).
 */
class CastTest {
    @Test fun scanNetwork(): Unit = runBlocking {
        assumeTrue(System.getenv("MARCO_CAST_SCAN") != null)
        val t0 = System.currentTimeMillis()
        val found = CastDiscovery.scan(OkHttpClient())
        println("CAST encontrados en ${System.currentTimeMillis() - t0} ms: ${found.size}")
        found.forEach { println("CAST  ${it.kind} · ${it.name} · ${it.host}:${it.port} · ${it.model} · ${it.avControl ?: ""}") }
    }

    /** Tele de mentiras: guarda qué le pidieron, decodifica unos segundos y dice que terminó. */
    private class FakeTv(val roku: Boolean) {
        val loaded: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val decoded: MutableList<String> = Collections.synchronizedList(mutableListOf())
        @Volatile var state = "STOPPED"
        @Volatile var startedAt = 0L
        @Volatile var ended = false
        /** "Canciones" de 3 s: la posición avanza con el reloj, como en una tele de verdad. */
        val dur = 3000L
        val pos: Long get() = if (ended) 0L else (System.currentTimeMillis() - startedAt).coerceIn(0, dur)
        val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        private fun play(url: String) {
            state = "PLAYING"; startedAt = System.currentTimeMillis(); ended = false
            Thread {
                runCatching {
                    val g = org.bytedeco.javacv.FFmpegFrameGrabber(url)
                    g.start(); repeat(20) { g.grabSamples() }
                    decoded += "${g.format} ${g.sampleRate} Hz"
                    g.stop(); g.release()
                }.onFailure { println("CAST fallo al decodificar $url: $it") }
                Thread.sleep((dur - (System.currentTimeMillis() - startedAt)).coerceAtLeast(0) + 300)
                ended = true; state = "STOPPED" // "terminó" (y como muchas teles, regresa la posición a 0)
            }.start()
        }

        fun start(): CastDevice {
            if (roku) {
                server.createContext("/launch/2213") { ex ->
                    val q = ex.requestURI.rawQuery.split("&").associate { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), "UTF-8") }
                    loaded += q.getValue("u"); play(q.getValue("u"))
                    ex.sendResponseHeaders(200, -1); ex.close()
                }
                server.createContext("/query/media-player") { ex ->
                    val st = if (state == "PLAYING") "play" else "stop"
                    val b = """<player error="false" state="$st"><position>$pos ms</position><duration>$dur ms</duration></player>""".toByteArray()
                    ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.use { it.write(b) }
                }
            } else {
                server.createContext("/av") { ex ->
                    val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
                    val action = ex.requestHeaders.getFirst("SOAPACTION").substringAfter('#').trim('"')
                    if (action == "SetAVTransportURI") loaded += Regex("<CurrentURI>(.*?)</CurrentURI>").find(body)!!.groupValues[1].replace("&amp;", "&")
                    if (action == "Play") play(loaded.last())
                    fun hms(ms: Long) = "0:%02d:%02d".format(ms / 60000, ms / 1000 % 60)
                    val r = when (action) {
                        "GetTransportInfo" -> "<CurrentTransportState>$state</CurrentTransportState>"
                        "GetPositionInfo" -> "<RelTime>${hms(pos)}</RelTime><TrackDuration>${hms(dur)}</TrackDuration>"
                        else -> ""
                    }
                    val b = "<s:Envelope><s:Body><u:${action}Response>$r</u:${action}Response></s:Body></s:Envelope>".toByteArray()
                    ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.use { it.write(b) }
                }
            }
            server.start()
            val port = server.address.port
            return if (roku) CastDevice("roku:fake", "Roku de prueba", CastDevice.Kind.ROKU, "127.0.0.1", port)
            else CastDevice("dlna:fake", "Tele de prueba", CastDevice.Kind.DLNA, "127.0.0.1", port, avControl = "http://127.0.0.1:$port/av")
        }
    }

    @Test fun castsToFakeDlnaAndRoku(): Unit = runBlocking {
        val url = System.getenv("MARCO_TEST_SERVER")
        assumeTrue(url != null)
        val http = OkHttpClient()
        val api = SubsonicApi(http) { SubsonicApi.login(url!!, "marco", "x") }
        val songs = api.albumList("newest", 5).flatMap { api.album(it.id).song }.take(2)
        println("CAST canciones: ${songs.map { "${it.title} (${it.suffix}, ${it.duration}s)" }}")
        assumeTrue("faltan canciones en el servidor de prueba", songs.size == 2)
        for (roku in listOf(false, true)) {
            val tv = FakeTv(roku)
            val dev = tv.start()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val cast = CastService(http, api, scope)
            val err = cast.start(dev, songs, 0, 0)
            assertEquals(null, err)
            assertEquals(dev.name, cast.player.state.value.remoteName)
            // Debe tocar la primera, ver que terminó y pasar sola a la segunda.
            val until = System.currentTimeMillis() + 25_000
            while (tv.decoded.size < 2 && System.currentTimeMillis() < until) Thread.sleep(200)
            println("CAST ${dev.kind}: pedidas=${tv.loaded.size} decodificadas=${tv.decoded} ahora=${cast.player.state.value.current?.title}")
            assertTrue("pasó a la siguiente", tv.loaded.size >= 2)
            assertEquals(2, tv.decoded.size)
            assertTrue("va por el puente", tv.loaded.all { it.contains(":45334/") || it.contains("/a/") })
            val back = cast.stop()
            assertEquals(2, back!!.first.size)
            assertEquals(null, cast.active.value)
            scope.cancel(); tv.server.stop(0)
        }
    }
}
