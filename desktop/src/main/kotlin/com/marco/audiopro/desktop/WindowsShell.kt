package com.marco.audiopro.desktop

import com.marco.audiopro.player.PlaybackState
import com.marco.audiopro.player.PlayerController
import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.Window
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import javax.swing.SwingUtilities

/**
 * Integración con Windows, como Spotify:
 * - Botones ⏮ ⏯ ⏭ en la vista previa que sale al pasar el mouse por el ícono de la barra de tareas.
 * - El control multimedia del sistema (SMTC): lo que suena aparece en el panel de volumen / Configuración
 *   rápida y en la pantalla de bloqueo, con portada y barra de avance, y las teclas multimedia del
 *   teclado funcionan aunque la app no tenga el foco.
 * Todo con JNA directo a Win32/COM/WinRT; si algo falla, la app sigue igual sin esa parte.
 */
object WindowsShell {
    private var installed = false
    private var thumbBar: ThumbBar? = null
    private var media: MediaControls? = null
    /** Para las pruebas: (botones de la barra, control multimedia) quedaron activos. */
    internal val active get() = (thumbBar != null) to (media != null)

    fun install(window: Window, player: PlayerController, http: OkHttpClient, coverUrl: (String?) -> String?) {
        if (installed || !System.getProperty("os.name").startsWith("Windows")) return
        installed = true
        val hwnd = Native.getWindowPointer(window) ?: return
        runCatching { darkTitleBar(hwnd) }
        val onUi: (() -> Unit) -> Unit = { block -> SwingUtilities.invokeLater { runCatching(block) } }
        thumbBar = runCatching { ThumbBar(hwnd) { id -> onUi { thumbClick(player, id) } } }
            .onFailure { System.err.println("Barra de tareas: ${it.message}") }.getOrNull()
        media = runCatching {
            MediaControls(
                hwnd,
                onButton = { b -> onUi { mediaButton(player, b) } },
                onSeek = { ms -> onUi { player.seekTo(ms) } },
            )
        }.onFailure { System.err.println("Controles multimedia de Windows: ${it.message}") }.getOrNull()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            player.state.map { it.isPlaying }.distinctUntilChanged().collect { playing ->
                thumbBar?.setPlaying(playing)
            }
        }
        scope.launch {
            player.state.map { Triple(it.current, it.isPlaying, it.durationMs) }.distinctUntilChanged().collect { (song, _, _) ->
                val st = player.state.value
                media?.update(st, player.positionMs(), coverFile(http, coverUrl(song?.coverArt)))
            }
        }
        // La posición se vuelve a mandar de vez en cuando (y tras adelantar o regresar).
        scope.launch {
            while (true) {
                delay(5_000)
                val st = player.state.value
                if (st.current != null) media?.timeline(st.durationMs, player.positionMs())
            }
        }
    }

    /**
     * Barra de título oscura del mismo color que la app (como Spotify): modo oscuro y, en
     * Windows 11, color de barra, texto y borde.
     */
    private fun darkTitleBar(hwnd: Pointer) {
        fun set(attr: Int, value: Int) {
            val m = Memory(4).apply { setInt(0, value) }
            DwmLib.I.DwmSetWindowAttribute(hwnd, attr, m, 4)
        }
        set(20, 1)              // DWMWA_USE_IMMERSIVE_DARK_MODE
        set(35, 0x000D0A09)     // DWMWA_CAPTION_COLOR (COLORREF = 0x00BBGGRR): el fondo de la app
        set(36, 0x00E8E4E2)     // DWMWA_TEXT_COLOR
        set(34, 0x00211C1A)     // DWMWA_BORDER_COLOR
    }

    private fun thumbClick(player: PlayerController, id: Int) = when (id) {
        ThumbBar.PREV -> player.previous()
        ThumbBar.PLAY -> player.toggle()
        ThumbBar.NEXT -> player.next()
        else -> {}
    }

    private fun mediaButton(player: PlayerController, button: Int) {
        val playing = player.state.value.isPlaying
        when (button) {
            0 -> if (!playing && MediaKeyGate.pass(0)) player.toggle()      // Play
            1, 2 -> if (playing && MediaKeyGate.pass(0)) player.toggle()    // Pause, Stop
            6 -> if (MediaKeyGate.pass(1)) player.next()
            7 -> if (MediaKeyGate.pass(2)) player.previous()
        }
    }

    /** La portada en un archivo temporal (Windows la lee desde una URI file://). */
    private val coverDir by lazy { File(System.getProperty("java.io.tmpdir"), "flacker-now-playing").apply { mkdirs() } }

    private fun coverFile(http: OkHttpClient, url: String?): File? {
        url ?: return null
        val f = File(coverDir, "${url.substringBefore("&t=").hashCode().toUInt()}.jpg")
        if (f.length() > 0) return f
        return runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return null
                f.writeBytes(r.body!!.bytes())
            }
            coverDir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(12)?.forEach { it.delete() }
            f
        }.getOrNull()
    }
}

/**
 * Una tecla multimedia puede llegar dos veces (a la ventana y por el control de Windows):
 * la segunda, si es la misma acción en menos de 400 ms, se ignora. 0 = play/pausa, 1 = siguiente, 2 = anterior.
 */
object MediaKeyGate {
    private var last = 0L
    private var lastKind = -1

    @Synchronized
    fun pass(kind: Int): Boolean {
        val now = System.currentTimeMillis()
        val ok = kind != lastKind || now - last > 400
        last = now; lastKind = kind
        return ok
    }
}

// ------------------------------------------------------------------ Win32 / COM / WinRT a mano

@Suppress("FunctionName")
private interface User32Lib : Library {
    fun SetWindowLongPtrW(hwnd: Pointer, index: Int, newLong: Pointer): Pointer?
    fun CallWindowProcW(prev: Pointer, hwnd: Pointer?, msg: Int, wParam: Pointer?, lParam: Pointer?): Pointer?
    fun RegisterWindowMessageW(name: WString): Int
    fun CreateIconIndirect(info: Pointer): Pointer?
    fun GetSystemMetrics(index: Int): Int

    companion object { val I: User32Lib = Native.load("user32", User32Lib::class.java) }
}

@Suppress("FunctionName")
private interface DwmLib : Library {
    fun DwmSetWindowAttribute(hwnd: Pointer, attr: Int, value: Pointer, size: Int): Int

    companion object { val I: DwmLib = Native.load("dwmapi", DwmLib::class.java) }
}

@Suppress("FunctionName")
private interface Gdi32Lib : Library {
    fun CreateDIBSection(hdc: Pointer?, info: Pointer, usage: Int, bits: PointerByReference, section: Pointer?, offset: Int): Pointer?
    fun CreateBitmap(w: Int, h: Int, planes: Int, bpp: Int, bits: Pointer?): Pointer?
    fun DeleteObject(obj: Pointer): Boolean

    companion object { val I: Gdi32Lib = Native.load("gdi32", Gdi32Lib::class.java) }
}

@Suppress("FunctionName")
private interface Ole32Lib : Library {
    fun CoInitializeEx(reserved: Pointer?, coInit: Int): Int
    fun CoCreateInstance(clsid: Pointer, outer: Pointer?, ctx: Int, iid: Pointer, out: PointerByReference): Int

    companion object { val I: Ole32Lib = Native.load("ole32", Ole32Lib::class.java) }
}

@Suppress("FunctionName")
private interface CombaseLib : Library {
    fun RoInitialize(type: Int): Int
    fun RoGetActivationFactory(classId: Pointer, iid: Pointer, out: PointerByReference): Int
    fun RoActivateInstance(classId: Pointer, out: PointerByReference): Int
    fun WindowsCreateString(s: WString?, length: Int, out: PointerByReference): Int
    fun WindowsDeleteString(s: Pointer?): Int

    companion object { val I: CombaseLib = Native.load("combase", CombaseLib::class.java) }
}

private fun guid(s: String): Memory {
    val u = UUID.fromString(s)
    val hi = u.mostSignificantBits; val lo = u.leastSignificantBits
    return Memory(16).apply {
        setInt(0, (hi ushr 32).toInt())
        setShort(4, (hi ushr 16).toShort())
        setShort(6, hi.toShort())
        for (i in 0 until 8) setByte(8L + i, (lo ushr (56 - 8 * i)).toByte())
    }
}

private fun Memory.sameAs(p: Pointer) = (0L until 16L).all { getByte(it) == p.getByte(it) }

private fun check(hr: Int, what: String): Int {
    if (hr < 0) throw IllegalStateException("$what falló (0x%08X)".format(hr))
    return hr
}

/** Llama al método `slot` de la vtable de un objeto COM. */
private fun Pointer.vcall(slot: Int, vararg args: Any?): Int {
    val fn = Function.getFunction(getPointer(0).getPointer(slot.toLong() * Native.POINTER_SIZE))
    return fn.invokeInt(arrayOf<Any?>(this, *args))
}

private fun Pointer.out(slot: Int, what: String, vararg args: Any?): Pointer {
    val r = PointerByReference()
    check(vcall(slot, *args, r), what)
    return r.value ?: throw IllegalStateException("$what: nulo")
}

private fun Pointer.qi(iid: Memory): Pointer? {
    val r = PointerByReference()
    return if (vcall(0, iid, r) >= 0) r.value else null
}

private fun Pointer.release() { vcall(2) }

private inline fun <T> withHString(s: String, block: (Pointer?) -> T): T {
    val r = PointerByReference()
    check(CombaseLib.I.WindowsCreateString(if (s.isEmpty()) null else WString(s), s.length, r), "WindowsCreateString")
    try { return block(r.value) } finally { CombaseLib.I.WindowsDeleteString(r.value) }
}

private fun activationFactory(cls: String, iid: Memory): Pointer = withHString(cls) { h ->
    val r = PointerByReference()
    check(CombaseLib.I.RoGetActivationFactory(h!!, iid, r), "RoGetActivationFactory($cls)")
    r.value
}

/** Hilo propio para hablar con COM (cada objeto COM vive en el hilo que lo creó). */
private class ComThread(name: String, private val init: () -> Unit) {
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, name).apply { isDaemon = true } }
    init { exec.submit(init).get() }
    fun <T> sync(block: () -> T): T = exec.submit<T>(block).get()
    fun async(block: () -> Unit) { exec.execute { runCatching(block).onFailure { System.err.println("$it") } } }
}

// ------------------------------------------------------------------ objetos COM hechos en Kotlin (delegados)

private interface QiProc : Callback { fun callback(self: Pointer, riid: Pointer, ppv: Pointer?): Int }
private interface RefProc : Callback { fun callback(self: Pointer): Int }
private interface InvokeProc : Callback { fun callback(self: Pointer, sender: Pointer?, args: Pointer?): Int }

private val IID_IUNKNOWN = guid("00000000-0000-0000-c000-000000000046")
private val IID_IAGILE = guid("94ea2b94-e9cc-49e0-c0ff-ee64ca8f5b90")

/**
 * Un TypedEventHandler de WinRT: QueryInterface/AddRef/Release/Invoke. Vive lo que vive la app,
 * así que el conteo de referencias es de adorno.
 */
private class EventDelegate(private val iid: Memory, private val onInvoke: (Pointer?) -> Unit) {
    private var refs = 1
    private val qi = object : QiProc {
        override fun callback(self: Pointer, riid: Pointer, ppv: Pointer?): Int {
            if (ppv == null) return 0x80004003.toInt() // E_POINTER
            return if (iid.sameAs(riid) || IID_IUNKNOWN.sameAs(riid) || IID_IAGILE.sameAs(riid)) {
                ppv.setPointer(0, self); refs++; 0
            } else { ppv.setPointer(0, null); 0x80004002.toInt() } // E_NOINTERFACE
        }
    }
    private val addRef = object : RefProc { override fun callback(self: Pointer) = ++refs }
    private val release = object : RefProc { override fun callback(self: Pointer) = (--refs).coerceAtLeast(1) }
    private val invoke = object : InvokeProc {
        override fun callback(self: Pointer, sender: Pointer?, args: Pointer?): Int {
            runCatching { onInvoke(args) }.onFailure { System.err.println("Evento multimedia: $it") }
            return 0
        }
    }
    private val vtbl = Memory(4L * Native.POINTER_SIZE).apply {
        listOf(qi, addRef, release, invoke).forEachIndexed { i, cb -> setPointer(i.toLong() * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(cb)) }
    }
    val obj: Memory = Memory(Native.POINTER_SIZE.toLong()).apply { setPointer(0, vtbl) }
}

// ------------------------------------------------------------------ controles multimedia (SMTC)

private class MediaControls(hwnd: Pointer, onButton: (Int) -> Unit, onSeek: (Long) -> Unit) {
    private val com = ComThread("smtc") { CombaseLib.I.RoInitialize(1) } // multihilo
    private lateinit var smtc: Pointer
    private var smtc2: Pointer? = null
    private lateinit var updater: Pointer
    private lateinit var music: Pointer
    private var music2: Pointer? = null
    private var timelineProps: Pointer? = null
    private lateinit var uriFactory: Pointer
    private lateinit var streamRefs: Pointer
    private var lastCover: File? = null

    private val buttons = EventDelegate(guid("0557e996-7b23-5bae-aa81-ea0d671143a4")) { args ->
        val b = IntByReference()
        if (args != null && args.vcall(6, b) >= 0) onButton(b.value)
    }
    private val seek = EventDelegate(guid("44e34f15-bdc0-50a7-ace4-39e91fb753f1")) { args ->
        val t = LongByReference()
        if (args != null && args.vcall(6, t) >= 0) onSeek(t.value / 10_000)
    }

    init {
        com.sync {
            val interop = activationFactory("Windows.Media.SystemMediaTransportControls", guid("ddb0472d-c911-4a1f-86d9-dc3d71a95f5a"))
            smtc = interop.out(6, "GetForWindow", hwnd, guid("99fa3ff4-1742-42a6-902e-087d41f965ec"))
            interop.release()
            check(smtc.vcall(11, true), "IsEnabled")
            smtc.vcall(13, true); smtc.vcall(17, true) // play, pausa
            smtc.vcall(25, true); smtc.vcall(27, true) // anterior, siguiente
            check(smtc.vcall(32, buttons.obj, LongByReference()), "ButtonPressed")
            updater = smtc.out(8, "DisplayUpdater")
            updater.vcall(7, 1) // MediaPlaybackType.Music
            music = updater.out(12, "MusicProperties")
            music2 = music.qi(guid("00368462-97d3-44b9-b00f-008afcefaf18"))
            uriFactory = activationFactory("Windows.Foundation.Uri", guid("44a9796f-723e-4fdf-a218-033e75b0c084"))
            streamRefs = activationFactory("Windows.Storage.Streams.RandomAccessStreamReference", guid("857309dc-3fbf-4e7d-986f-ef3b1a07a964"))
            smtc2 = smtc.qi(guid("ea98d2f6-7f3c-4af2-a586-72889808efb1"))
            runCatching {
                timelineProps = withHString("Windows.Media.SystemMediaTransportControlsTimelineProperties") { h ->
                    val r = PointerByReference()
                    check(CombaseLib.I.RoActivateInstance(h!!, r), "TimelineProperties")
                    r.value.qi(guid("5125316a-c3a2-475b-8507-93534dc88f15")).also { r.value.release() }
                }
                smtc2?.vcall(13, seek.obj, LongByReference())
            }
        }
    }

    fun update(st: PlaybackState, positionMs: Long, cover: File?) = com.async {
        val song = st.current
        // MediaPlaybackStatus: 2 = Stopped, 3 = Playing, 4 = Paused.
        smtc.vcall(7, if (song == null) 2 else if (st.isPlaying) 3 else 4)
        if (song == null) return@async
        withHString(song.title) { music.vcall(7, it) }
        withHString(song.artist ?: "") { music.vcall(11, it) }
        withHString(song.artist ?: "") { music.vcall(9, it) }
        music2?.let { m -> withHString(song.album ?: "") { m.vcall(7, it) } }
        if (cover != lastCover) {
            lastCover = cover
            if (cover != null) {
                val uri = withHString(cover.toURI().toString()) { uriFactory.out(6, "Uri", it) }
                val ref = streamRefs.out(7, "CreateFromUri", uri)
                updater.vcall(11, ref)
                ref.release(); uri.release()
            } else updater.vcall(11, null as Pointer?)
        }
        updater.vcall(17) // Update()
        timelineNow(st.durationMs, positionMs)
    }

    fun timeline(durationMs: Long, positionMs: Long) = com.async { timelineNow(durationMs, positionMs) }

    private fun timelineNow(durationMs: Long, positionMs: Long) {
        val t = timelineProps ?: return
        val s2 = smtc2 ?: return
        if (durationMs <= 0) return
        val end = durationMs * 10_000 // TimeSpan = unidades de 100 ns
        t.vcall(7, 0L); t.vcall(9, end); t.vcall(11, 0L); t.vcall(13, end)
        t.vcall(15, positionMs.coerceIn(0, durationMs) * 10_000)
        s2.vcall(12, t)
    }
}

// ------------------------------------------------------------------ botones en la vista previa de la barra de tareas

private interface WndProc : Callback { fun callback(hwnd: Pointer?, msg: Int, wParam: Pointer?, lParam: Pointer?): Pointer? }

private class ThumbBar(private val hwnd: Pointer, private val onClick: (Int) -> Unit) {
    companion object {
        const val PREV = 1
        const val PLAY = 2
        const val NEXT = 3
        private const val STRIDE = 552L // sizeof(THUMBBUTTON) en x64
        private const val WM_COMMAND = 0x0111
        private const val THBN_CLICKED = 0x1800
    }

    private val com = ComThread("taskbar") { Ole32Lib.I.CoInitializeEx(null, 2) } // apartamento propio
    private lateinit var taskbar: Pointer
    private val iconSize = User32Lib.I.GetSystemMetrics(49).takeIf { it > 0 } ?: 16 // SM_CXSMICON
    private val icons = mapOf(
        "prev" to icon(::drawPrev), "next" to icon(::drawNext), "play" to icon(::drawPlay), "pause" to icon(::drawPause),
    )
    @Volatile private var playing = false
    private val created = User32Lib.I.RegisterWindowMessageW(WString("TaskbarButtonCreated"))
    private var oldProc: Pointer? = null
    private val proc = object : WndProc {
        override fun callback(hwnd: Pointer?, msg: Int, wParam: Pointer?, lParam: Pointer?): Pointer? {
            try {
                if (msg == created) com.async { add() }
                else if (msg == WM_COMMAND && wParam != null) {
                    val w = Pointer.nativeValue(wParam)
                    if (((w shr 16) and 0xFFFF).toInt() == THBN_CLICKED) onClick((w and 0xFFFF).toInt())
                }
            } catch (_: Throwable) {}
            return User32Lib.I.CallWindowProcW(oldProc!!, hwnd, msg, wParam, lParam)
        }
    }

    init {
        com.sync {
            val r = PointerByReference()
            check(
                Ole32Lib.I.CoCreateInstance(guid("56fdf344-fd6d-11d0-958a-006097c9a090"), null, 1, guid("ea1afb91-9e28-4b86-90e9-9e9f8a5eefaf"), r),
                "TaskbarList",
            )
            taskbar = r.value
            check(taskbar.vcall(3), "HrInit")
        }
        oldProc = User32Lib.I.SetWindowLongPtrW(hwnd, -4, CallbackReference.getFunctionPointer(proc)) // GWLP_WNDPROC
        // Si el botón de la barra ya existía, no llega "TaskbarButtonCreated": se agregan de una vez.
        com.async { add() }
    }

    fun setPlaying(p: Boolean) {
        playing = p
        com.async { taskbar.vcall(16, hwnd, 3, buttons()) }
    }

    private fun add() { taskbar.vcall(15, hwnd, 3, buttons()) }

    private fun buttons(): Memory {
        val m = Memory(STRIDE * 3).apply { clear() }
        fun put(i: Int, id: Int, icon: Pointer?, tip: String) {
            val o = STRIDE * i
            m.setInt(o, 0x2 or 0x4 or 0x8) // THB_ICON | THB_TOOLTIP | THB_FLAGS
            m.setInt(o + 4, id)
            m.setPointer(o + 16, icon)
            m.setWideString(o + 24, tip.take(259))
            m.setInt(o + 544, 0) // THBF_ENABLED
        }
        put(0, PREV, icons["prev"], "Anterior")
        put(1, PLAY, if (playing) icons["pause"] else icons["play"], if (playing) "Pausa" else "Reproducir")
        put(2, NEXT, icons["next"], "Siguiente")
        return m
    }

    /** Ícono blanco con antialias, dibujado con Java2D y convertido a HICON de 32 bits. */
    private fun icon(draw: (java.awt.Graphics2D, Float) -> Unit): Pointer? = runCatching {
        val n = iconSize
        val img = BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.color = Color.WHITE
        draw(g, n / 16f)
        g.dispose()
        val header = Memory(44).apply {
            clear()
            setInt(0, 40); setInt(4, n); setInt(8, -n) // de arriba hacia abajo
            setShort(12, 1); setShort(14, 32)
        }
        val bits = PointerByReference()
        val color = Gdi32Lib.I.CreateDIBSection(null, header, 0, bits, null, 0) ?: return null
        bits.value.write(0, img.getRGB(0, 0, n, n, null, 0, n), 0, n * n)
        val maskBits = Memory(((n + 15) / 16 * 2L) * n).apply { clear() } // filas alineadas a WORD
        val mask = Gdi32Lib.I.CreateBitmap(n, n, 1, 1, maskBits) ?: return null
        val info = Memory(32).apply {
            clear()
            setInt(0, 1) // fIcon
            setPointer(16, mask); setPointer(24, color)
        }
        User32Lib.I.CreateIconIndirect(info).also { Gdi32Lib.I.DeleteObject(color); Gdi32Lib.I.DeleteObject(mask) }
    }.getOrNull()

    private fun tri(g: java.awt.Graphics2D, s: Float, vararg p: Float) {
        val path = Path2D.Float()
        path.moveTo(p[0] * s, p[1] * s)
        for (i in 2 until p.size step 2) path.lineTo(p[i] * s, p[i + 1] * s)
        path.closePath()
        g.stroke = BasicStroke(1.2f * s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.fill(path); g.draw(path)
    }

    private fun drawPlay(g: java.awt.Graphics2D, s: Float) = tri(g, s, 5f, 3.2f, 5f, 12.8f, 13f, 8f)
    private fun drawPause(g: java.awt.Graphics2D, s: Float) {
        g.fill(RoundRectangle2D.Float(3.8f * s, 3f * s, 2.9f * s, 10f * s, 1.4f * s, 1.4f * s))
        g.fill(RoundRectangle2D.Float(9.3f * s, 3f * s, 2.9f * s, 10f * s, 1.4f * s, 1.4f * s))
    }
    private fun drawPrev(g: java.awt.Graphics2D, s: Float) {
        g.fill(RoundRectangle2D.Float(2.8f * s, 3.6f * s, 2.1f * s, 8.8f * s, 1f * s, 1f * s))
        tri(g, s, 13f, 3.8f, 13f, 12.2f, 6.2f, 8f)
    }
    private fun drawNext(g: java.awt.Graphics2D, s: Float) {
        g.fill(RoundRectangle2D.Float(11.1f * s, 3.6f * s, 2.1f * s, 8.8f * s, 1f * s, 1f * s))
        tri(g, s, 3f, 3.8f, 3f, 12.2f, 9.8f, 8f)
    }
}
