package com.marco.audiopro.data

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
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Collections

/** Sin red se guardan; al volver, llegan todos, en orden y con su hora original. */
class ScrobbleQueueTest {
    @Test fun offlineScrobblesSurviveAndArriveInOrder(): Unit = runBlocking {
        var online = false
        val got = Collections.synchronizedList(mutableListOf<Pair<String, Long>>())
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/rest/scrobble") { ex ->
                val q = ex.requestURI.query.split("&").associate { it.substringBefore("=") to it.substringAfter("=") }
                val code = if (online) 200 else 503
                if (online) got += q.getValue("id") to q.getValue("time").toLong()
                val body = """{"subsonic-response":{"status":"ok","version":"1.16.1"}}""".toByteArray()
                ex.sendResponseHeaders(code, body.size.toLong()); ex.responseBody.use { it.write(body) }
            }
            start()
        }
        val acc = ServerAccount("http://127.0.0.1:${server.address.port}", "u", "s", "t")
        val api = SubsonicApi(OkHttpClient()) { acc }
        val dir = Files.createTempDirectory("scrob").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val q1 = ScrobbleQueue(api, dir, scope)
        api.offlineQueue = q1
        for (id in listOf("a", "b", "c")) { api.scrobble(id, submission = true); Thread.sleep(5) }
        assertEquals(3, q1.pending.value)
        assertTrue(got.isEmpty())

        // La app se cerró sin red: al abrirla, siguen ahí.
        val q2 = ScrobbleQueue(api, dir, scope)
        assertEquals(3, q2.pending.value)

        online = true
        // Puede mandarlos este flush o el automático que la cola lanza al crearse (van con candado):
        // lo que importa es que lleguen los 3, una sola vez y en orden.
        q2.flush()
        assertEquals(listOf("a", "b", "c"), got.map { it.first })
        assertTrue("horas en orden", got.zipWithNext().all { (x, y) -> x.second <= y.second })
        assertEquals(0, q2.pending.value)

        scope.cancel(); server.stop(0); dir.deleteRecursively()
    }
}
