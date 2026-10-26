package com.marco.audiopro.desktop

import com.marco.audiopro.data.Settings
import com.marco.audiopro.data.local.LocalMode
import com.marco.audiopro.share.ShareInbox
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.io.File
import java.nio.file.Files

private interface Shell32Lib : Library {
    fun SHGetFolderPathW(hwnd: Pointer?, csidl: Int, token: Pointer?, flags: Int, path: CharArray): Int
}

/** La carpeta Música de Windows (aunque esté en OneDrive u otro disco). */
private fun musicDir(): File = runCatching {
    val buf = CharArray(260)
    if (Native.load("shell32", Shell32Lib::class.java).SHGetFolderPathW(null, 0x000D, null, 0, buf) == 0) File(Native.toString(buf)) else null
}.getOrNull()?.takeIf { it.isDirectory } ?: File(System.getProperty("user.home"), "Music")

/**
 * Lo que te mandan otros Flacker cae en Música\Flacker\Artista\Álbum. Sin servidor, esa carpeta
 * se agrega a tu biblioteca para que lo recibido aparezca solo.
 */
class FolderInbox(private val settings: Settings, val root: File = File(musicDir(), "Flacker")) : ShareInbox {
    override val label: String get() = root.path

    private fun dir(dirs: List<String>) = dirs.fold(root) { d, s -> File(d, s) }

    override fun has(dirs: List<String>, name: String, size: Long): Boolean = File(dir(dirs), name).let { it.isFile && it.length() == size }

    override fun save(tmp: File, dirs: List<String>, name: String, mime: String): Boolean {
        val d = dir(dirs).apply { mkdirs() }
        var target = File(d, name)
        var n = 2
        while (target.exists()) target = File(d, "${name.substringBeforeLast('.')} (${n++}).${name.substringAfterLast('.')}")
        return runCatching { Files.move(tmp.toPath(), target.toPath()) }.isSuccess ||
            runCatching { tmp.copyTo(target); true }.getOrDefault(false)
    }

    override suspend fun afterBatch() {
        if (!LocalMode.isOn(settings)) return
        val inside = LocalMode.folders(settings).any { f -> runCatching { root.canonicalFile.toPath().startsWith(File(f).canonicalFile.toPath()) }.getOrDefault(false) }
        if (!inside) LocalMode.addFolder(settings, root.path)
    }
}
