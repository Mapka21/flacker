package com.marco.audiopro.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.data.subsonic.SubsonicJson
import kotlinx.serialization.encodeToString

private const val EXTRA_SONG = "song"

/** El Song completo viaja en los extras para que servicio y UI tengan formato, ReplayGain, etc. */
fun Song.toMediaItem(api: SubsonicApi): MediaItem {
    val extras = Bundle().apply { putString(EXTRA_SONG, SubsonicJson.encodeToString(this@toMediaItem)) }
    val meta = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setAlbumArtist(artist)
        .setTrackNumber(track)
        .setDiscNumber(discNumber)
        .setRecordingYear(year)
        .setGenre(genre)
        .setDurationMs(duration?.let { it * 1000L })
        .setArtworkUri(api.coverUrl(coverArt, 800)?.let(Uri::parse))
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .setExtras(extras)
        .build()
    return MediaItem.Builder().setMediaId(id).setMediaMetadata(meta).build()
}

fun MediaItem.toSong(): Song? =
    mediaMetadata.extras?.getString(EXTRA_SONG)?.let { runCatching { SubsonicJson.decodeFromString<Song>(it) }.getOrNull() }
