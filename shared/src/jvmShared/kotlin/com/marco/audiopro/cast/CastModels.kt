package com.marco.audiopro.cast

/** Un aparato al que se le puede mandar la música: un Chromecast (o tele/bocina con Chromecast) o una tele/bocina DLNA. */
data class CastDevice(
    val id: String,
    val name: String,
    val kind: Kind,
    val host: String,
    val port: Int,
    val model: String? = null,
    /** DLNA: dirección de control de AVTransport y de RenderingControl (volumen). */
    val avControl: String? = null,
    val rcControl: String? = null,
) {
    enum class Kind(val label: String) { CHROMECAST("Chromecast"), ROKU("Roku"), DLNA("Tele / bocina (DLNA)") }
}

/** Lo que se muestra en la tele: canción, artista, álbum y portada. */
data class CastMeta(val title: String, val artist: String?, val album: String?, val coverUrl: String?, val durationMs: Long)

enum class CastState { IDLE, LOADING, PLAYING, PAUSED, FINISHED, ERROR }

data class CastStatus(val state: CastState, val positionMs: Long = 0, val durationMs: Long = 0, val volume: Float? = null, val ended: Boolean = false)

/** Una conexión abierta con un aparato: reproduce una canción a la vez; [CastPlayer] lleva la cola. */
interface CastSession {
    val device: CastDevice
    suspend fun load(url: String, mime: String, meta: CastMeta, startMs: Long)
    suspend fun play()
    suspend fun pause()
    suspend fun seek(ms: Long)
    suspend fun setVolume(v: Float)
    /** Estado actual (pide uno nuevo al aparato; la posición se calcula entre respuestas). */
    suspend fun poll(): CastStatus
    /** Cerrar; con [stopApp] también se quita la app de la tele (como "dejar de transmitir"). */
    suspend fun close(stopApp: Boolean)
}
