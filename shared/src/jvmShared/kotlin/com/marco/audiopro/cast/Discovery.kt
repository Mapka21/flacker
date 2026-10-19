package com.marco.audiopro.cast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Busca aparatos en la red: Chromecast por mDNS ("_googlecast._tcp") y teles/bocinas DLNA por SSDP
 * (los que tienen AVTransport, o sea, que pueden reproducir una URL). Todo con sockets normales,
 * igual en la PC y en Android (allá la app ya tiene el candado de multicast de Connect).
 */
object CastDiscovery {
    suspend fun scan(http: OkHttpClient, millis: Long = 3500): List<CastDevice> = coroutineScope {
        val cc = async(Dispatchers.IO) { runCatching { chromecasts(millis) }.getOrDefault(emptyList()) }
        val dl = async(Dispatchers.IO) { runCatching { dlna(http, millis) }.getOrDefault(emptyList()) }
        (cc.await() + dl.await()).sortedWith(compareBy({ it.kind }, { it.name.lowercase() }))
    }

    // ------------------------------------------------------------------ mDNS (Chromecast)

    private const val SERVICE = "_googlecast._tcp.local"

    private fun query(unicastResponse: Boolean): ByteArray {
        val b = ByteArrayOutputStream()
        b.write(byteArrayOf(0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0))
        for (label in SERVICE.split('.')) { b.write(label.length); b.write(label.toByteArray()) }
        b.write(0)
        b.write(byteArrayOf(0, 12)) // PTR
        b.write(byteArrayOf(if (unicastResponse) 0x80.toByte() else 0, 1)) // IN (+ "respóndeme directo")
        return b.toByteArray()
    }

    private class Found(var name: String? = null, var port: Int = 8009, var id: String? = null, var model: String? = null)

    private fun chromecasts(millis: Long): List<CastDevice> {
        val group = InetAddress.getByName("224.0.0.251")
        val byHost = ConcurrentHashMap<String, MutableMap<String, Found>>()
        val until = System.currentTimeMillis() + millis
        fun listen(sock: DatagramSocket) {
            val buf = ByteArray(9000)
            while (System.currentTimeMillis() < until) {
                val p = DatagramPacket(buf, buf.size)
                sock.soTimeout = (until - System.currentTimeMillis()).coerceAtLeast(1).toInt()
                try { sock.receive(p) } catch (_: SocketTimeoutException) { break } catch (_: Exception) { break }
                val host = p.address?.hostAddress ?: continue
                runCatching { parseMdns(buf, p.length, byHost.getOrPut(host) { ConcurrentHashMap() }) }
            }
        }
        val sockets = ArrayList<DatagramSocket>()
        for ((ni, addr) in lanInterfaces()) {
            // Pregunta por cada adaptador (si no, Windows la manda por el de más prioridad, p. ej. Tailscale).
            runCatching {
                val s = MulticastSocket(InetSocketAddress(addr, 0)).apply { networkInterface = ni; timeToLive = 4 }
                repeat(2) { s.send(DatagramPacket(query(true), query(true).size, group, 5353)) }
                sockets += s
            }
        }
        // Algunos aparatos contestan sólo al grupo: también se escucha ahí.
        runCatching {
            val m = MulticastSocket(5353).apply { reuseAddress = true }
            lanInterfaces().forEach { (ni, _) -> runCatching { m.joinGroup(InetSocketAddress(group, 5353), ni) } }
            sockets += m
        }
        val threads = sockets.map { s -> Thread { listen(s) }.apply { isDaemon = true; start() } }
        threads.forEach { it.join(millis + 500) }
        sockets.forEach { runCatching { it.close() } }
        return byHost.flatMap { (host, map) ->
            map.values.filter { it.name != null }.map { f ->
                CastDevice(id = "cc:" + (f.id ?: "$host:${f.port}"), name = f.name!!, kind = CastDevice.Kind.CHROMECAST, host = host, port = f.port, model = f.model)
            }
        }.distinctBy { it.id }
    }

    /** Lee una respuesta mDNS: de cada instancia de _googlecast saca el puerto (SRV) y el nombre (TXT "fn"). */
    private fun parseMdns(b: ByteArray, len: Int, out: MutableMap<String, Found>) {
        var pos = 12
        fun u16(i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
        fun name(start: Int): Pair<String, Int> {
            val parts = ArrayList<String>()
            var i = start; var end = -1; var hops = 0
            while (i < len && hops < 40) {
                val l = b[i].toInt() and 0xFF
                when {
                    l == 0 -> { i++; break }
                    l and 0xC0 == 0xC0 -> { if (end < 0) end = i + 2; i = ((l and 0x3F) shl 8) or (b[i + 1].toInt() and 0xFF); hops++ }
                    else -> { parts += String(b, i + 1, l, Charsets.UTF_8); i += l + 1 }
                }
            }
            return parts.joinToString(".") to (if (end >= 0) end else i)
        }
        val qd = u16(4); val total = u16(6) + u16(8) + u16(10)
        repeat(qd) { pos = name(pos).second + 4 }
        repeat(total) {
            if (pos + 10 > len) return
            val (rname, p) = name(pos)
            val type = u16(p); val rdlen = u16(p + 8); val rd = p + 10
            if (rname.endsWith(SERVICE, ignoreCase = true) && rname.length > SERVICE.length) {
                val f = out.getOrPut(rname.lowercase()) { Found() }
                when (type) {
                    33 -> f.port = u16(rd + 4) // SRV
                    16 -> { // TXT: id=…, fn=nombre, md=modelo
                        var i = rd
                        while (i < rd + rdlen) {
                            val l = b[i].toInt() and 0xFF
                            val kv = String(b, i + 1, l, Charsets.UTF_8)
                            when (kv.substringBefore('=')) {
                                "fn" -> f.name = kv.substringAfter('=')
                                "id" -> f.id = kv.substringAfter('=')
                                "md" -> f.model = kv.substringAfter('=')
                            }
                            i += l + 1
                        }
                    }
                }
                if (f.name == null) f.name = rname.substringBefore("._googlecast").substringBeforeLast('-').ifBlank { null }
            }
            pos = rd + rdlen
        }
    }

    // ------------------------------------------------------------------ SSDP (DLNA)

    private fun dlna(http: OkHttpClient, millis: Long): List<CastDevice> {
        fun search(st: String) = (
            "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $st\r\n\r\n"
            ).toByteArray()
        val dlna = ConcurrentHashMap.newKeySet<String>()
        val roku = ConcurrentHashMap.newKeySet<String>()
        val until = System.currentTimeMillis() + millis
        val dst = InetSocketAddress("239.255.255.250", 1900)
        val threads = lanInterfaces().mapNotNull { (ni, addr) ->
            runCatching {
                val s = MulticastSocket(InetSocketAddress(addr, 0)).apply { networkInterface = ni; timeToLive = 4 }
                repeat(2) {
                    for (st in listOf("urn:schemas-upnp-org:service:AVTransport:1", "roku:ecp")) search(st).let { s.send(DatagramPacket(it, it.size, dst)) }
                }
                Thread {
                    val buf = ByteArray(4096)
                    while (System.currentTimeMillis() < until) {
                        val p = DatagramPacket(buf, buf.size)
                        s.soTimeout = (until - System.currentTimeMillis()).coerceAtLeast(1).toInt()
                        try { s.receive(p) } catch (_: Exception) { break }
                        val lines = String(buf, 0, p.length).lines()
                        val loc = lines.firstOrNull { it.startsWith("location:", ignoreCase = true) }?.substringAfter(':')?.trim() ?: continue
                        val st = lines.firstOrNull { it.startsWith("st:", ignoreCase = true) }?.substringAfter(':')?.trim().orEmpty()
                        if (st.startsWith("roku", ignoreCase = true)) roku += loc else dlna += loc
                    }
                    s.close()
                }.apply { isDaemon = true; start() }
            }.getOrNull()
        }
        threads.forEach { it.join(millis + 500) }
        val client = http.newBuilder().callTimeout(3, TimeUnit.SECONDS).build()
        val rokus = roku.mapNotNull { loc -> runCatching { describeRoku(client, loc) }.getOrNull() }
        return (rokus + dlna.mapNotNull { loc -> runCatching { describe(client, loc) }.getOrNull() }).distinctBy { it.id }
    }

    private fun describeRoku(http: OkHttpClient, location: String): CastDevice {
        val uri = URI(location)
        val xml = http.newCall(Request.Builder().url("http://${uri.host}:${uri.port}/query/device-info").build()).execute().use { it.body?.string().orEmpty() }
        fun tag(n: String) = Regex("<$n>(.*?)</$n>").find(xml)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        return CastDevice(
            id = "roku:" + (tag("serial-number") ?: uri.host), name = tag("user-device-name") ?: tag("friendly-device-name") ?: "Roku",
            kind = CastDevice.Kind.ROKU, host = uri.host, port = uri.port, model = tag("friendly-model-name") ?: tag("model-name"),
        )
    }

    private fun describe(http: OkHttpClient, location: String): CastDevice? {
        val xml = http.newCall(Request.Builder().url(location).build()).execute().use { it.body?.string() } ?: return null
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
        fun text(tag: String) = doc.getElementsByTagName(tag).let { if (it.length > 0) it.item(0).textContent.trim() else null }
        val base = text("URLBase")?.takeIf { it.isNotBlank() } ?: location
        var av: String? = null; var rc: String? = null
        val services = doc.getElementsByTagName("service")
        for (i in 0 until services.length) {
            val s = services.item(i) as org.w3c.dom.Element
            val type = s.getElementsByTagName("serviceType").item(0)?.textContent.orEmpty()
            val ctl = s.getElementsByTagName("controlURL").item(0)?.textContent?.trim() ?: continue
            val abs = URI(base).resolve(ctl).toString()
            if (type.contains("AVTransport")) av = abs
            if (type.contains("RenderingControl")) rc = abs
        }
        if (av == null) return null
        val uri = URI(location)
        return CastDevice(
            id = "dlna:" + (text("UDN") ?: location), name = text("friendlyName") ?: uri.host, kind = CastDevice.Kind.DLNA,
            host = uri.host, port = uri.port, model = listOfNotNull(text("manufacturer"), text("modelName")).joinToString(" ").ifBlank { null },
            avControl = av, rcControl = rc,
        )
    }

    // ------------------------------------------------------------------ red local

    /** Adaptadores de la red de la casa (sin loopback, sin VPN tipo Tailscale ni direcciones 169.254). */
    private fun lanInterfaces(): List<Pair<NetworkInterface, Inet4Address>> =
        runCatching { NetworkInterface.getNetworkInterfaces().toList() }.getOrDefault(emptyList())
            .filter { runCatching { it.isUp && !it.isLoopback && it.supportsMulticast() }.getOrDefault(false) }
            .flatMap { ni -> ni.inetAddresses.toList().filterIsInstance<Inet4Address>().map { ni to it } }
            .filter { (_, a) ->
                val b0 = a.address[0].toInt() and 0xFF; val b1 = a.address[1].toInt() and 0xFF
                !a.isLinkLocalAddress && !(b0 == 100 && b1 in 64..127)
            }

    /** La IP de este aparato que está en la misma red que [peer] (para que la tele pueda pedirle el audio). */
    fun lanAddressFor(peer: String): String? {
        val target = runCatching { InetAddress.getByName(peer) as? Inet4Address }.getOrNull()
        if (target?.isLoopbackAddress == true) return "127.0.0.1"
        // Sin VPN tipo Tailscale (100.64/10) ni 169.254: la tele no llega ahí (y Tailscale a veces dice /0 y "cuadraba" con todo).
        val candidates = runCatching { NetworkInterface.getNetworkInterfaces().toList() }.getOrDefault(emptyList())
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { it.interfaceAddresses }
            .filter { ia ->
                val a = ia.address as? Inet4Address ?: return@filter false
                val b0 = a.address[0].toInt() and 0xFF; val b1 = a.address[1].toInt() and 0xFF
                !a.isLinkLocalAddress && !(b0 == 100 && b1 in 64..127) && ia.networkPrefixLength in 8..32
            }
        if (target != null) {
            val t = target.address.fold(0) { acc, x -> (acc shl 8) or (x.toInt() and 0xFF) }
            candidates.firstOrNull { ia ->
                val bits = ia.networkPrefixLength.toInt().coerceIn(1, 32)
                val mask = if (bits == 32) -1 else (-1 shl (32 - bits))
                val a = ia.address.address.fold(0) { acc, x -> (acc shl 8) or (x.toInt() and 0xFF) }
                (a and mask) == (t and mask)
            }?.let { return it.address.hostAddress }
        }
        return candidates.firstOrNull { it.address.isSiteLocalAddress }?.address?.hostAddress
    }
}
