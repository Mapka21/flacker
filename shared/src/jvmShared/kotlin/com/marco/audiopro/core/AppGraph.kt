package com.marco.audiopro.core

import androidx.compose.runtime.staticCompositionLocalOf
import com.marco.audiopro.audio.AudioEngine
import com.marco.audiopro.data.Likes
import com.marco.audiopro.data.Settings
import com.marco.audiopro.data.info.AlbumInfoRepository
import com.marco.audiopro.data.info.LyricsRepository
import com.marco.audiopro.connect.ConnectService
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.download.OfflineStore
import com.marco.audiopro.player.PlayerController

/** Caché de streaming que se puede consultar/vaciar desde Ajustes. */
interface CacheControl {
    val capacityLabel: String
    suspend fun usedBytes(): Long
    suspend fun clear()
}

/** Todo lo que la UI compartida necesita; cada plataforma (Android, PC) da su implementación. */
interface AppGraph {
    val settings: Settings
    val api: SubsonicApi
    val engine: AudioEngine
    val albumInfo: AlbumInfoRepository
    val lyrics: LyricsRepository
    /** Marco Connect: otros dispositivos con la app en la red. */
    val connect: ConnectService
    val player: PlayerController
    val likes: Likes
    /** Descargas offline; `null` donde no aplican (PC). */
    val downloads: OfflineStore?
    val cache: CacheControl?
    val versionName: String
    val isDesktop: Boolean
}

val LocalApp = staticCompositionLocalOf<AppGraph> { error("AppGraph no provisto") }
