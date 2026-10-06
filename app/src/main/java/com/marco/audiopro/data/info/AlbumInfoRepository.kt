package com.marco.audiopro.data.info

import android.content.Context
import com.marco.audiopro.data.Settings
import com.marco.audiopro.data.subsonic.Album
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.data.subsonic.SubsonicJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File

enum class InfoSource(val label: String) { LASTFM("Last.fm"), NAVIDROME("Last.fm vía Navidrome"), CLAUDE("IA · Claude"), NONE("") }

@Serializable
data class AlbumSummary(
    val source: InfoSource = InfoSource.NONE,
    val text: String? = null,
    val tags: List<String> = emptyList(),
    val listeners: Long? = null,
    val playcount: Long? = null,
    val url: String? = null,
    val error: String? = null,
)

/**
 * Prioridad: Last.fm directo (si hay API key) → notas de Navidrome (que también salen de Last.fm)
 * → reseña de Claude (si hay API key de Anthropic). Todo se cachea en disco por álbum.
 */
class AlbumInfoRepository(
    context: Context,
    private val api: SubsonicApi,
    private val lastFm: LastFm,
    private val claude: ClaudeReviewer,
    private val settings: Settings,
) {
    private val dir = File(context.filesDir, "albuminfo").apply { mkdirs() }

    private fun file(id: String) = File(dir, id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")

    suspend fun cached(albumId: String): AlbumSummary? = withContext(Dispatchers.IO) {
        file(albumId).takeIf { it.exists() }?.let { runCatching { SubsonicJson.decodeFromString<AlbumSummary>(it.readText()) }.getOrNull() }
    }

    private suspend fun save(albumId: String, s: AlbumSummary) = withContext(Dispatchers.IO) {
        if (s.text != null) file(albumId).writeText(SubsonicJson.encodeToString(s))
    }

    suspend fun summary(album: Album): AlbumSummary {
        cached(album.id)?.let { return it }
        val prefs = settings.prefs.value
        var tags = emptyList<String>()
        var base = AlbumSummary()

        if (prefs.lastFmKey.isNotBlank() && album.artist != null) {
            lastFm.albumInfo(prefs.lastFmKey, album.artist, album.name)?.let { lf ->
                tags = lf.tags
                base = AlbumSummary(InfoSource.LASTFM, lf.summary, lf.tags, lf.listeners, lf.playcount, lf.url)
                if (lf.summary != null) return base.also { save(album.id, it) }
            }
        }

        val nav = api.albumInfo(album.id)
        nav.notes?.let(LastFm::cleanLastFmText)?.takeIf { it.length > 40 }?.let { notes ->
            return base.copy(source = InfoSource.NAVIDROME, text = notes, url = base.url ?: nav.lastFmUrl).also { save(album.id, it) }
        }

        if (prefs.anthropicKey.isNotBlank()) return generateWithClaude(album, base, tags)

        return base.copy(source = InfoSource.NONE, error = "Sin reseña en Last.fm. Agrega tu API key de Anthropic en Ajustes para generar una con IA.")
    }

    suspend fun generateWithClaude(album: Album, base: AlbumSummary = AlbumSummary(), tags: List<String> = base.tags): AlbumSummary {
        val key = settings.prefs.value.anthropicKey
        if (key.isBlank()) return base.copy(error = "Falta la API key de Anthropic (Ajustes).")
        return runCatching { claude.review(key, album, tags) }
            .map { base.copy(source = InfoSource.CLAUDE, text = it, error = null).also { s -> save(album.id, s) } }
            .getOrElse { base.copy(error = "No se pudo generar con IA: ${it.message}") }
    }
}
