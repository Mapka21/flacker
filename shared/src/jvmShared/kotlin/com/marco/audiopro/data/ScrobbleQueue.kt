package com.marco.audiopro.data

import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.data.subsonic.SubsonicJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * Scrobbles sin pérdida: cada canción escuchada se guarda en disco con la hora en que la oíste y
 * se manda a Navidrome (que la pasa a Last.fm) en cuanto hay conexión, en orden y con su hora
 * original. Sin internet (o con el servidor apagado) esperan; se reintenta cada 2 minutos y en
 * cuanto el sistema avisa que volvió la red ([flush]).
 */
class ScrobbleQueue(private val api: SubsonicApi, dir: File, scope: CoroutineScope) {
    @Serializable
    private data class Pending(val id: String, val time: Long)

    private val file = File(dir, "scrobbles_pendientes.json")
    private val lock = Mutex()
    private val list: MutableList<Pending> = runCatching {
        SubsonicJson.decodeFromString<List<Pending>>(file.readText()).toMutableList()
    }.getOrDefault(mutableListOf())

    private val _pending = MutableStateFlow(list.size)
    /** Cuántos esperan conexión (para mostrarlo en Ajustes). */
    val pending: StateFlow<Int> = _pending

    /** Se llama con cuántos se mandaron cuando se vacía algo que estaba esperando. */
    var onSynced: ((Int) -> Unit)? = null

    init {
        scope.launch {
            while (true) { flush(); delay(120_000) }
        }
    }

    /** Una canción se escuchó (pasó la mitad o 4 min): se guarda y se intenta mandar ya. */
    suspend fun submit(id: String) {
        lock.withLock { list += Pending(id, System.currentTimeMillis()); save() }
        flush()
    }

    /** Manda lo pendiente en orden; se detiene en el primero que falle (sin red). Devuelve cuántos mandó. */
    suspend fun flush(): Int {
        val sent = lock.withLock {
            // Más de 60 días: ya no tiene caso (Last.fm sólo acepta 14; Navidrome sí los cuenta).
            val old = System.currentTimeMillis() - 60L * 24 * 3600 * 1000
            list.removeAll { it.time < old }
            var n = 0
            while (list.isNotEmpty()) {
                val p = list.first()
                if (!api.scrobbleAt(p.id, p.time)) break
                list.removeAt(0); n++
            }
            if (n > 0) save()
            n
        }
        _pending.value = list.size
        // Sólo se avisa si había varios guardados (no cada canción normal con red).
        if (sent > 1) onSynced?.invoke(sent)
        return sent
    }

    private suspend fun save() = withContext(Dispatchers.IO) {
        runCatching { file.writeText(SubsonicJson.encodeToString(list.toList())) }
        _pending.value = list.size
    }
}
