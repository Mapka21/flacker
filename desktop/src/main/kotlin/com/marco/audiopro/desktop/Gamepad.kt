package com.marco.audiopro.desktop

import com.marco.audiopro.player.PlayerController
import com.marco.audiopro.ui.Immersive
import com.marco.audiopro.ui.viz.VizControl
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Structure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * El control de Xbox (XInput), como en los visualizadores de la Xbox: con la app al frente,
 * stick izquierdo gira y acerca, stick derecho mueve la estela, gatillos aceleran o frenan,
 * X cambia la forma de las ondas, A cambia de efecto, Y abre la pantalla completa (o cambia el modo),
 * B sale, LB/RB anterior/siguiente, Start play/pausa, Back regresa los visuales a como estaban,
 * cruceta arriba/abajo volumen. Si la app no está al frente no se lee (no estorba al jugar).
 */
object Gamepad {
    @Suppress("FunctionName")
    private interface XInput : Library { fun XInputGetState(userIndex: Int, state: State): Int }

    @Structure.FieldOrder("packet", "buttons", "lt", "rt", "lx", "ly", "rx", "ry")
    class State : Structure() {
        @JvmField var packet = 0
        @JvmField var buttons: Short = 0
        @JvmField var lt: Byte = 0
        @JvmField var rt: Byte = 0
        @JvmField var lx: Short = 0
        @JvmField var ly: Short = 0
        @JvmField var rx: Short = 0
        @JvmField var ry: Short = 0
    }

    private val lib: XInput? = runCatching { Native.load("xinput1_4", XInput::class.java) }
        .recoverCatching { Native.load("xinput9_1_0", XInput::class.java) }.getOrNull()

    private const val UP = 0x1; private const val DOWN = 0x2; private const val LEFT = 0x4; private const val RIGHT = 0x8
    private const val START = 0x10; private const val BACK = 0x20; private const val LB = 0x100; private const val RB = 0x200
    private const val A = 0x1000; private const val B = 0x2000; private const val X = 0x4000; private const val Y = 0x8000

    private fun axis(v: Short): Float { val f = v / 32767f; return if (abs(f) < 0.24f) 0f else (f - 0.24f * Math.signum(f)) / 0.76f }

    /** Corre en el hilo de la interfaz; [focused] = la ventana está al frente. */
    fun start(scope: CoroutineScope, player: PlayerController, focused: () -> Boolean) {
        val x = lib ?: return
        scope.launch {
            val st = State()
            var prev = 0
            var idx = 0
            while (true) {
                delay(if (Immersive.on) 16 else 120)
                if (!focused()) continue
                var ok = x.XInputGetState(idx, st) == 0
                if (!ok) { idx = (idx + 1) % 4; VizControl.pad = false; continue }
                VizControl.pad = true
                val b = st.buttons.toInt() and 0xFFFF
                val pressed = b and prev.inv()
                prev = b
                // Sticks y gatillos: los visuales en vivo.
                val lx = axis(st.lx); val ly = axis(st.ly)
                VizControl.spin = lx * 120f
                VizControl.zoom = (VizControl.zoom + ly * 0.016f).coerceIn(-0.4f, 0.8f)
                VizControl.driftX = axis(st.rx); VizControl.driftY = -axis(st.ry)
                val lt = (st.lt.toInt() and 0xFF) / 255f; val rt = (st.rt.toInt() and 0xFF) / 255f
                if (lt > 0.05f || rt > 0.05f) VizControl.speed = (1f + rt * 3f - lt * 0.75f).coerceIn(0.25f, 4f)
                // Botones (sólo al apretarlos).
                if (pressed and Y != 0) { if (Immersive.on) Immersive.cycleMode() else Immersive.on = true }
                if (pressed and B != 0 && Immersive.on) Immersive.on = false
                if (pressed and A != 0 && Immersive.on) Immersive.swapNow = true
                if (pressed and X != 0) VizControl.shape++
                if (pressed and BACK != 0) { VizControl.reset(); com.marco.audiopro.ui.viz.Neon360Control.manual = false }
                if (pressed and START != 0) player.toggle()
                if (pressed and LB != 0) player.previous()
                if (pressed and RB != 0) player.next()
                if (Immersive.on) {
                    // Como en el 360: la cruceta recorre la matriz de escenas.
                    val nc = com.marco.audiopro.ui.viz.Neon360Control
                    if (pressed and LEFT != 0) nc.move(-1, 0)
                    if (pressed and RIGHT != 0) nc.move(1, 0)
                    if (pressed and UP != 0) nc.move(0, -1)
                    if (pressed and DOWN != 0) nc.move(0, 1)
                    if (pressed and (LEFT or RIGHT or UP or DOWN) != 0) Immersive.toast = "Escena ${nc.scene + 1} de ${com.marco.audiopro.ui.viz.Neon360.SCENES.size}"
                } else {
                    if (pressed and LEFT != 0) player.previous()
                    if (pressed and RIGHT != 0) player.next()
                    if (pressed and UP != 0) player.setVolume(((player.volume?.value ?: 0f) + 0.05f).coerceIn(0f, 1f))
                    if (pressed and DOWN != 0) player.setVolume(((player.volume?.value ?: 0f) - 0.05f).coerceIn(0f, 1f))
                }
                ok = true
            }
        }
    }
}
