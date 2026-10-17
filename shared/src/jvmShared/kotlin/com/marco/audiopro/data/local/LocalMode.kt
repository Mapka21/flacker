package com.marco.audiopro.data.local

import com.marco.audiopro.data.ServerAccount
import com.marco.audiopro.data.Settings

/**
 * Modo "sin servidor": la app usa tu música local a través de [LocalServer] (en 127.0.0.1), como si
 * fuera un Navidrome. Guarda si está prendido, el puerto (para que la dirección no cambie) y, en la
 * PC, las carpetas.
 */
object LocalMode {
    private const val DEFAULT_PORT = 45333

    fun isOn(s: Settings) = s.getString("local_mode") == "1"

    /** Carpetas de música (PC). */
    fun folders(s: Settings): List<String> = s.getString("local_folders")?.split("\n")?.filter { it.isNotBlank() }.orEmpty()
    fun addFolder(s: Settings, path: String) = s.putString("local_folders", (folders(s) + path).distinct().joinToString("\n"))
    fun removeFolder(s: Settings, path: String) = s.putString("local_folders", folders(s).filter { it != path }.joinToString("\n"))

    private fun account(port: Int) = ServerAccount("http://127.0.0.1:$port", "local", "local", "local")

    private fun startServer(lib: LocalLibrary, s: Settings): Int {
        val port = lib.server.start(s.getString("local_port")?.toIntOrNull() ?: DEFAULT_PORT)
        s.putString("local_port", port.toString())
        return port
    }

    /** Al abrir la app: si está en modo local, prende el servidor y la cuenta apunta a él. */
    fun resume(lib: LocalLibrary?, s: Settings) {
        if (lib == null || !isOn(s)) return
        val acc = account(startServer(lib, s))
        if (s.account.value != acc) s.saveAccount(acc)
    }

    /** Prender el modo local: escanea y entra. Devuelve cuántas canciones encontró. */
    suspend fun enable(lib: LocalLibrary, s: Settings): Int {
        s.putString("local_mode", "1")
        val port = startServer(lib, s)
        val n = lib.rescan()
        // Sin canciones no tiene caso entrar: se queda en la pantalla de inicio.
        if (n > 0) s.saveAccount(account(port)) else s.putString("local_mode", "0")
        return n
    }

    /** Salir del modo local (regresa a la pantalla de inicio de sesión). */
    fun disable(s: Settings) {
        s.putString("local_mode", "0")
        s.saveAccount(null)
    }
}
