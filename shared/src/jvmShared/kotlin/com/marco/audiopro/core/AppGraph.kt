package com.marco.audiopro.core

import androidx.compose.runtime.staticCompositionLocalOf
import com.marco.audiopro.audio.AudioEngine
import com.marco.audiopro.data.CustomCovers
import com.marco.audiopro.data.Likes
import com.marco.audiopro.data.PlaylistStore
import com.marco.audiopro.data.Settings
import com.marco.audiopro.data.info.AlbumInfoRepository
import com.marco.audiopro.data.discover.DiscoverRepository
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
    /** Tu perfil de Last.fm (sección "Mi Last.fm"). */
    val lastFm: com.marco.audiopro.data.info.LastFmProfile
    /** Música que no tienes: recomendaciones y catálogo para explorar. */
    val discover: DiscoverRepository
    /** Marco Connect: otros dispositivos con la app en la red. */
    val connect: ConnectService
    /** Mandar y recibir canciones con otros Flacker de la red. */
    val share: com.marco.audiopro.share.ShareService
    val player: PlayerController
    /** Radio por internet (radio-browser.info). */
    val radio: com.marco.audiopro.data.radio.RadioBrowser
    /** Géneros de verdad para tu biblioteca (MusicBrainz). */
    val genres: com.marco.audiopro.data.meta.GenreIndex
    /** Arreglar metadatos de tus archivos; null donde no se pueden escribir (celular). */
    val fixer: com.marco.audiopro.data.meta.MetadataFixer?
    /** Transmitir a Chromecast, Roku o teles/bocinas DLNA. */
    val cast: com.marco.audiopro.cast.CastService
    val likes: Likes
    /** Playlists del servidor (crear, agregar, quitar). */
    val playlists: PlaylistStore
    /** Portadas propias de playlists, "Me gusta" y artistas. */
    val covers: CustomCovers
    /** Tu música sin servidor (carpetas en la PC, la música del celular); null donde no aplica. */
    val localLibrary: com.marco.audiopro.data.local.LocalLibrary?
    /** Fotos de artista (Navidrome, Deezer o generadas). */
    val artistPhotos: com.marco.audiopro.data.ArtistPhotos
    /** Descargas offline; `null` donde no aplican (PC). */
    val downloads: OfflineStore?
    val cache: CacheControl?
    val versionName: String
    val isDesktop: Boolean
}

val LocalApp = staticCompositionLocalOf<AppGraph> { error("AppGraph no provisto") }
