package com.marco.audiopro.desktop

import com.marco.audiopro.audio.Dither
import com.marco.audiopro.audio.EqDsp
import com.marco.audiopro.audio.PcmFormat
import com.marco.audiopro.audio.SampleRing
import com.marco.audiopro.audio.SampleSource
import com.marco.audiopro.audio.SignalInfo
import com.marco.audiopro.audio.dbToLinear
import com.marco.audiopro.audio.replayGainFor
import com.marco.audiopro.data.Settings
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.player.PlaybackState
import com.marco.audiopro.player.PlayerController
import com.marco.audiopro.player.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.nio.FloatBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.min

/**
 * Motor de audio de PC: ffmpeg (JavaCV) decodifica a float → ReplayGain → EQ compartido →
 * visualizadores → volumen → dither → Java Sound (24 bits si la salida lo acepta).
 *
 * Todo el audio corre en un hilo propio; la UI le manda comandos por una cola.
 */
class DesktopPlayer(
    private val api: SubsonicApi,
    private val settings: Settings,
    private val scope: CoroutineScope,
) : PlayerController {

    lateinit var eq: EqDsp
    var onSignal: (SignalInfo) -> Unit = {}

    // ---------------------------------------------------------------- estado público

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _volume = MutableStateFlow(settings.getString("volume")?.toFloatOrNull() ?: 0.8f)
    override val volume: StateFlow<Float> = _volume.asStateFlow()

    override fun setVolume(v: Float) {
        _volume.value = v.coerceIn(0f, 1f)
        settings.putString("volume", _volume.value.toString())
    }

    // ---------------------------------------------------------------- cola (protegida por `lock`)

    private val lock = Any()
    private var queue: List<Song> = emptyList()
    private var order: List<Int> = emptyList() // orden de reproducción (aleatorio o no)
    private var shuffle = false
    private var repeat = RepeatMode.OFF
    @Volatile private var wantPlay = false
    @Volatile private var buffering = false
    @Volatile private var error: String? = null
    /** Posición pedida por la UI que el hilo de audio todavía no aplica (la barra no "rebota"). */
    @Volatile private var seekTarget: Long? = null

    // ---------------------------------------------------------------- tap para visualizadores

    private val ring = SampleRing()
    @Volatile private var line: SourceDataLine? = null
    @Volatile private var frameBytes = 6
    @Volatile private var lineRate = 44100

    /** Frames ya escritos a la salida pero que todavía no suenan. */
    private fun bufferedFrames(): Long {
        val l = line ?: return 0
        return ((l.bufferSize - l.available()).coerceAtLeast(0) / frameBytes).toLong()
    }

    val tap = object : SampleSource {
        override val sampleRate get() = lineRate
        override fun read(dstL: FloatArray, dstR: FloatArray, n: Int, extraLatencyMs: Int): Boolean {
            // La posición real de la salida da el sync exacto; el ajuste del usuario sólo afina.
            val end = ring.writePos - bufferedFrames() - extraLatencyMs.coerceAtLeast(0) * lineRate / 1000L
            return ring.copyEndingAt(end, dstL, dstR, n)
        }
    }

    // ---------------------------------------------------------------- hilo de audio

    private sealed interface Cmd {
        data class Load(val index: Int, val startMs: Long, val play: Boolean) : Cmd
        data class Seek(val ms: Long) : Cmd
        data object Pause : Cmd
        data object Resume : Cmd
        /** La cola cambió (desde la UI); `removed` = índice quitado, si hubo. */
        data class QueueChanged(val removed: Int? = null) : Cmd
    }

    private class Track(var index: Int, val song: Song, val grabber: FFmpegFrameGrabber, val transport: String) {
        // Se leen al abrir: después de cerrar el grabber ya no se pueden consultar.
        val rate = grabber.sampleRate
        val lengthMs = (grabber.lengthInTime / 1000).takeIf { it > 0 } ?: ((song.duration ?: 0) * 1000L)
    }

    /** Qué pista suena a partir de qué frame de la salida (para gapless y posición). */
    private data class Segment(val track: Track, val baseMs: Long, val startFrame: Long)

    private val cmds = LinkedBlockingQueue<Cmd>()
    private var cur: Track? = null
    @Volatile private var segments: List<Segment> = emptyList()
    private var prefetch: CompletableFuture<Track?>? = null
    private var prefetchIndex = -1
    private var buf = FloatArray(0)
    private var bytes = ByteArray(0)
    private val dither = Dither()
    private var bits = 24

    private val thread = Thread(::audioLoop, "marco-audio").apply { isDaemon = true; start() }

    init {
        avutil.av_log_set_level(avutil.AV_LOG_ERROR)
        scope.launch(Dispatchers.Default) {
            while (isActive) { publish(); delay(200) }
        }
        scope.launch(Dispatchers.IO) { scrobbler() }
    }

    private fun audioLoop() {
        while (true) {
            try {
                val c = cur
                var cmd = if (c == null || !wantPlay) cmds.take() else cmds.poll()
                // Al arrastrar la barra pueden llegar varias búsquedas seguidas: sólo cuenta la última.
                while (cmd is Cmd.Seek && cmds.peek() is Cmd.Seek) cmd = cmds.poll()
                if (cmd != null) { handle(cmd); publish(); continue }
                pump(c!!)
            } catch (e: InterruptedException) {
                return
            } catch (e: Exception) {
                error = "Error de audio: ${e.message}"
                wantPlay = false
                runCatching { publish() }
            }
        }
    }

    private fun handle(cmd: Cmd) {
        when (cmd) {
            is Cmd.Load -> {
                closeTrack(); cancelPrefetch()
                val song = synchronized(lock) { queue.getOrNull(cmd.index) } ?: return
                buffering = true; error = null; publish()
                val t = try { open(cmd.index, song) } catch (e: Exception) {
                    buffering = false; error = "No se pudo abrir “${song.title}”: ${e.message}"; wantPlay = false; seekTarget = null
                    return
                }
                if (cmd.startMs > 0) t.grabber.timestamp = cmd.startMs * 1000
                ensureLine(t.rate, flush = true)
                cur = t
                eq.reset(t.rate, 2)
                segments = listOf(Segment(t, cmd.startMs, ring.writePos))
                seekTarget = null
                buffering = false
                wantPlay = cmd.play
                if (wantPlay) line?.start()
                announce(t)
            }
            is Cmd.Seek -> {
                val t = cur ?: run { seekTarget = null; return }
                val ok = runCatching { t.grabber.timestamp = cmd.ms * 1000 }.isSuccess
                if (!ok) {
                    // Algunos streams no aguantan buscar en caliente: se reabre en ese punto.
                    handle(Cmd.Load(t.index, cmd.ms, wantPlay))
                    return
                }
                line?.flush()
                eq.reset(t.rate, 2)
                segments = listOf(Segment(t, cmd.ms, ring.writePos))
                cancelPrefetch()
                seekTarget = null
            }
            Cmd.Pause -> { wantPlay = false; line?.stop() }
            Cmd.Resume -> { if (cur != null) { wantPlay = true; line?.start() } }
            is Cmd.QueueChanged -> {
                cancelPrefetch()
                val r = cmd.removed ?: return
                (segments.map { it.track } + listOfNotNull(cur)).distinct().forEach { if (it.index > r) it.index-- }
            }
        }
    }

    private fun open(index: Int, song: Song): Track {
        val q = settings.prefs.value.wifiQuality
        val g = FFmpegFrameGrabber(api.streamUrl(song.id, q))
        g.sampleFormat = avutil.AV_SAMPLE_FMT_FLT
        g.audioChannels = 2
        g.setOption("reconnect", "1")
        g.setOption("reconnect_streamed", "1")
        g.setOption("rw_timeout", "20000000")
        g.start()
        return Track(index, song, g, q.label)
    }

    /** Abre (o reutiliza) la salida al sample rate de la pista; 24 bits si se puede. */
    private fun ensureLine(rate: Int, flush: Boolean) {
        val l = line
        if (l != null && lineRate == rate) {
            if (flush) l.flush()
            return
        }
        l?.let { it.drain(); it.close() }
        var opened: SourceDataLine? = null
        for (b in intArrayOf(24, 16)) {
            val fmt = AudioFormat(rate.toFloat(), b, 2, true, false)
            opened = runCatching {
                (AudioSystem.getSourceDataLine(fmt)).apply { open(fmt, rate * 2 * (b / 8) / 6) } // ~170 ms
            }.getOrNull()
            if (opened != null) { bits = b; break }
        }
        val out = opened ?: throw IllegalStateException("La salida de audio no acepta $rate Hz")
        frameBytes = 2 * bits / 8
        lineRate = rate
        line = out
        ring.reset()
        segments = emptyList()
    }

    /** Decodifica un bloque, lo procesa y lo escribe a la salida (bloquea hasta que haya espacio). */
    private fun pump(t: Track) {
        val frame = t.grabber.grabSamples()
        if (frame == null) { advance(t); return }
        val fb = frame.samples?.firstOrNull() as? FloatBuffer ?: return
        val n = fb.remaining()
        if (n == 0) return
        if (buf.size < n) buf = FloatArray(n)
        fb.get(buf, 0, n)
        val frames = n / 2

        val rg = replayGainFor(t.song, settings.prefs.value)?.let { dbToLinear(it.first) } ?: 1f
        if (rg != 1f) for (i in 0 until n) buf[i] *= rg
        eq.process(buf, frames)

        val vol = _volume.value.let { it * it } // curva más natural al oído
        val bps = bits / 8
        if (bytes.size < n * bps) bytes = ByteArray(n * bps)
        val ditherOn = eq.dither
        var p = 0
        for (i in 0 until n) {
            val v = dither.quantize(buf[i] * vol, bits, ditherOn)
            bytes[p++] = v.toByte(); bytes[p++] = (v shr 8).toByte()
            if (bps == 3) bytes[p++] = (v shr 16).toByte()
        }
        line?.write(bytes, 0, p)
        ring.write(buf, frames, 2) // después de escribir: el ring va a la par de la salida

        maybePrefetch(t)
    }

    private fun nextIndex(from: Int, manual: Boolean): Int? = synchronized(lock) {
        if (queue.isEmpty()) return null
        if (!manual && repeat == RepeatMode.ONE) return from
        val pos = order.indexOf(from)
        when {
            pos < 0 -> null
            pos + 1 < order.size -> order[pos + 1]
            repeat == RepeatMode.ALL -> order.first()
            else -> null
        }
    }

    /** Si faltan <10 s, abre la siguiente pista en segundo plano para que no haya hueco. */
    private fun maybePrefetch(t: Track) {
        if (prefetch != null) return
        val len = t.lengthMs
        if (len <= 0 || t.grabber.timestamp / 1000 < len - 10_000) return
        val next = nextIndex(t.index, manual = false) ?: return
        val song = synchronized(lock) { queue.getOrNull(next) } ?: return
        prefetchIndex = next
        prefetch = CompletableFuture.supplyAsync { runCatching { open(next, song) }.getOrNull() }
    }

    private fun cancelPrefetch() {
        prefetch?.let { f -> f.thenAccept { it?.grabber?.let(::release) } }
        prefetch = null
        prefetchIndex = -1
    }

    /** Fin de pista: encadena la siguiente sin silencio (gapless) si el formato coincide. */
    private fun advance(t: Track) {
        val next = nextIndex(t.index, manual = false)
        if (next == null) {
            line?.drain()
            wantPlay = false
            cancelPrefetch()
            closeTrack()
            return
        }
        val pre = prefetch?.takeIf { prefetchIndex == next }?.get()
        prefetch = null; prefetchIndex = -1
        val song = synchronized(lock) { queue.getOrNull(next) } ?: return
        val nt = pre ?: runCatching { open(next, song) }.getOrElse {
            error = "No se pudo abrir “${song.title}”"; wantPlay = false; return
        }
        release(t.grabber)
        if (nt.rate != lineRate) {
            ensureLine(nt.rate, flush = false)
            eq.reset(nt.rate, 2)
            segments = listOf(Segment(nt, 0, ring.writePos))
            if (wantPlay) line?.start()
        } else {
            segments = (segments + Segment(nt, 0, ring.writePos)).takeLast(4)
        }
        cur = nt
        announce(nt)
    }

    private fun closeTrack() {
        cur?.grabber?.let(::release)
        cur = null
    }

    private fun release(g: FFmpegFrameGrabber) = runCatching { g.stop(); g.release() }

    private fun announce(t: Track) {
        val rg = replayGainFor(t.song, settings.prefs.value)
        onSignal(
            SignalInfo(
                rg?.first, rg?.second, t.transport,
                "Java Sound · PCM $bits-bit · ${lineRate / 1000.0} kHz → salida predeterminada de Windows",
            ),
        )
        eq.inputFormat = PcmFormat(t.rate, 2, "32-bit float (ffmpeg)")
    }

    // ---------------------------------------------------------------- posición y estado

    private fun audibleSegment(): Pair<Segment, Long>? {
        val segs = segments
        if (segs.isEmpty()) return null
        val audible = ring.writePos - bufferedFrames()
        val seg = segs.lastOrNull { it.startFrame <= audible } ?: segs.first()
        val ms = seg.baseMs + (audible - seg.startFrame).coerceAtLeast(0) * 1000 / lineRate
        return seg to ms
    }

    override fun positionMs(): Long = seekTarget ?: audibleSegment()?.second ?: 0L

    private fun publish() {
        val seg = audibleSegment()?.first
        val track = seg?.track ?: cur
        val (q, sh, rp) = synchronized(lock) { Triple(queue, shuffle, repeat) }
        _state.value = PlaybackState(
            current = track?.song ?: _state.value.current.takeIf { q.isNotEmpty() },
            currentIndex = track?.index ?: -1,
            isPlaying = wantPlay && !buffering,
            isBuffering = buffering,
            durationMs = track?.lengthMs ?: 0,
            shuffle = sh,
            repeat = rp,
            queue = q,
            error = error,
        )
    }

    /** "Reproduciendo ahora" al empezar cada pista y scrobble al pasar la mitad (o 4 min). */
    private suspend fun scrobbler() {
        var nowPlaying: String? = null
        var submitted: String? = null
        while (true) {
            delay(2000)
            val st = _state.value
            val id = st.current?.id ?: continue
            if (id != nowPlaying && st.isPlaying) { nowPlaying = id; api.scrobble(id, submission = false) }
            if (id != submitted && st.isPlaying && st.durationMs > 0 && positionMs() > min(st.durationMs / 2, 240_000L)) {
                submitted = id
                api.scrobble(id, submission = true)
            }
        }
    }

    // ---------------------------------------------------------------- API del reproductor

    private fun rebuildOrder(startAt: Int) {
        order = if (shuffle) listOf(startAt) + (queue.indices - startAt).shuffled() else queue.indices.toList()
    }

    override fun play(songs: List<Song>, startIndex: Int, shuffle: Boolean) {
        if (songs.isEmpty()) return
        val list = if (shuffle) songs.shuffled() else songs
        val start = if (shuffle) 0 else startIndex.coerceIn(0, songs.lastIndex)
        synchronized(lock) {
            queue = list
            this.shuffle = false
            rebuildOrder(start)
        }
        cmds.put(Cmd.Load(start, 0, true))
    }

    override fun playFrom(songs: List<Song>, index: Int, positionMs: Long) {
        if (songs.isEmpty()) return
        val start = index.coerceIn(0, songs.lastIndex)
        synchronized(lock) {
            queue = songs
            shuffle = false
            rebuildOrder(start)
        }
        cmds.put(Cmd.Load(start, positionMs, true))
    }

    override fun playNext(songs: List<Song>) {
        val empty = synchronized(lock) {
            val c = _state.value.currentIndex
            val at = c + 1
            queue = queue.take(at) + songs + queue.drop(at)
            rebuildOrder(c.coerceAtLeast(0))
            queue.size == songs.size
        }
        cmds.put(if (empty) Cmd.Load(0, 0, true) else Cmd.QueueChanged())
    }

    override fun enqueue(songs: List<Song>) {
        val empty = synchronized(lock) {
            queue = queue + songs
            rebuildOrder(_state.value.currentIndex.coerceAtLeast(0))
            queue.size == songs.size
        }
        cmds.put(if (empty) Cmd.Load(0, 0, true) else Cmd.QueueChanged())
    }

    override fun toggle() {
        if (cur == null) {
            // Terminó la cola o hubo un error: vuelve a empezar la pista actual.
            val i = _state.value.currentIndex
            if (i >= 0) cmds.put(Cmd.Load(i, 0, true))
            return
        }
        cmds.put(if (wantPlay) Cmd.Pause else Cmd.Resume)
    }

    override fun next() {
        val from = audibleSegment()?.first?.track?.index ?: _state.value.currentIndex.takeIf { it >= 0 } ?: return
        nextIndex(from, manual = true)?.let { cmds.put(Cmd.Load(it, 0, true)) }
    }

    override fun previous() {
        val from = audibleSegment()?.first?.track?.index ?: _state.value.currentIndex.takeIf { it >= 0 } ?: return
        if (positionMs() > 3000) { seekTo(0); return }
        val prev = synchronized(lock) {
            val pos = order.indexOf(from)
            if (pos > 0) order[pos - 1] else if (repeat == RepeatMode.ALL) order.lastOrNull() else from
        } ?: return
        cmds.put(Cmd.Load(prev, 0, true))
    }

    override fun seekTo(ms: Long) {
        val playing = audibleSegment()?.first?.track?.index
        seekTarget = ms.coerceAtLeast(0)
        if (playing != null && playing != cur?.index) {
            // Ya se precargó la siguiente (gapless): recarga la que realmente está sonando.
            cmds.put(Cmd.Load(playing, ms, wantPlay))
        } else {
            cmds.put(Cmd.Seek(ms))
        }
    }

    override fun skipToIndex(i: Int) = cmds.put(Cmd.Load(i, 0, true))

    override fun removeAt(i: Int) {
        synchronized(lock) {
            if (i == _state.value.currentIndex || i !in queue.indices) return
            queue = queue.filterIndexed { j, _ -> j != i }
            val c = _state.value.currentIndex
            rebuildOrder(if (c > i) c - 1 else c.coerceAtLeast(0))
        }
        cmds.put(Cmd.QueueChanged(removed = i))
    }

    override fun toggleShuffle() {
        synchronized(lock) {
            shuffle = !shuffle
            rebuildOrder(_state.value.currentIndex.coerceAtLeast(0))
        }
        cmds.put(Cmd.QueueChanged())
    }

    override fun cycleRepeat() {
        synchronized(lock) {
            repeat = when (repeat) { RepeatMode.OFF -> RepeatMode.ALL; RepeatMode.ALL -> RepeatMode.ONE; RepeatMode.ONE -> RepeatMode.OFF }
        }
        cmds.put(Cmd.QueueChanged())
    }

    fun shutdown() {
        thread.interrupt()
        closeTrack()
        line?.close()
    }
}
