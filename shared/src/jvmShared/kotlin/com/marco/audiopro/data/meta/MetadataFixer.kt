package com.marco.audiopro.data.meta

import com.marco.audiopro.data.discover.normName
import com.marco.audiopro.data.info.LyricsRepository
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.data.subsonic.SubsonicJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/** Una canción tal como está en tu archivo. */
@Serializable
data class FixTrack(
    val path: String,
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val track: Int? = null,
    val disc: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val durationMs: Long = 0,
    val hasCover: Boolean = false,
    val hasLyrics: Boolean = false,
    val format: String? = null,
)

/** Un disco: las canciones de una carpeta con el mismo álbum. */
data class FixAlbum(val folder: String, val artist: String, val album: String, val tracks: List<FixTrack>) {
    val key: String get() = folder + "|" + album
    val year: Int? get() = tracks.mapNotNull { it.year }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    val genre: String? get() = tracks.mapNotNull { it.genre }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    val hasCover: Boolean get() = tracks.any { it.hasCover }
}

/** Lo que se va a escribir en un archivo. */
@Serializable
data class NewTags(
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val track: Int? = null,
    val trackTotal: Int? = null,
    val disc: Int? = null,
    val discTotal: Int? = null,
    val year: Int? = null,
    val genres: List<String>? = null,
    val mbReleaseId: String? = null,
    val mbRecordingId: String? = null,
)

/** Leer y escribir etiquetas: cada plataforma pone la suya (en la PC, jaudiotagger). */
interface TagIO {
    /** Los discos dentro de esas carpetas. */
    suspend fun scan(folders: List<String>, progress: (Int) -> Unit = {}): List<FixAlbum>
    /** Lo que tiene ahora un archivo (para poder deshacer). */
    suspend fun read(path: String): NewTags
    /**
     * Escribe etiquetas; [cover] reemplaza la portada incrustada si no es null.
     * @param exact para deshacer: lo que venga en null se borra (en vez de dejarse como está).
     * @param removeCover quita la portada incrustada (deshacer cuando antes no tenía).
     */
    suspend fun write(path: String, tags: NewTags, cover: ByteArray?, exact: Boolean = false, removeCover: Boolean = false)
    /** La portada incrustada actual (para poder deshacer). */
    suspend fun cover(path: String): ByteArray?
}

data class TrackChange(val file: FixTrack, val target: MbTrack?, val newTitle: String?, val newNumber: Int?, val newDisc: Int?)

/** Qué cambiaría en un disco si se aplica lo de MusicBrainz. */
data class Proposal(
    val album: FixAlbum,
    val release: MbRelease,
    val confidence: Float,
    val tracks: List<TrackChange>,
    val genres: List<String>,
    val coverUrl: String,
) {
    val newAlbum get() = release.title.takeIf { it != album.album }
    val newArtist get() = release.artist.takeIf { it != album.artist }
    val newYear get() = release.year?.takeIf { it != album.year }
    val newGenres get() = genres.takeIf { it.isNotEmpty() && GenreMap.fromTag(album.genre) != it }
    val renamedTracks get() = tracks.count { it.newTitle != null || it.newNumber != null }
    /** Nada que cambiar (salvo quizá portada/letras). */
    val clean get() = newAlbum == null && newArtist == null && newYear == null && newGenres == null && renamedTracks == 0
    /** Seguro para aplicar sin revisar uno por uno. */
    val safe get() = confidence >= 0.85f
}

/** Qué partes aplicar. */
data class FixOptions(
    val names: Boolean = true,
    val year: Boolean = true,
    val genres: Boolean = true,
    val cover: Boolean = true,
    val lyrics: Boolean = true,
    /** Reemplazar la portada aunque ya tenga una (si no, sólo se pone donde falte). */
    val replaceCover: Boolean = true,
)

@Serializable
private data class UndoEntry(val path: String, val tags: NewTags, val coverFile: String? = null, val hadCover: Boolean = true)

@Serializable
private data class UndoLog(val time: Long, val album: String, val entries: List<UndoEntry>, val sidecars: List<String> = emptyList())

/**
 * Arregla los metadatos de tus discos para que todo se vea igual de bien venga de donde venga:
 * busca cada disco en MusicBrainz (por artista, álbum, número de canciones y duraciones), propone
 * nombres, número de pista, año original, géneros votados por la comunidad, portada grande (Cover
 * Art Archive) y letras sincronizadas (LRCLIB, guardadas como .lrc). Nada se escribe sin que lo
 * apruebes, y antes de escribir se guarda lo anterior para poder deshacer.
 */
class MetadataFixer(private val mb: MusicBrainz, private val io: TagIO, private val lyrics: LyricsRepository, dir: File, private val genreIndex: GenreIndex? = null) {
    private val undoDir = File(dir, "metadatos_deshacer").apply { mkdirs() }

    private fun sim(a: String, b: String): Float {
        val x = normName(a); val y = normName(b)
        if (x == y) return 1f
        if (x.isEmpty() || y.isEmpty()) return 0f
        val wa = x.split(' ').toSet(); val wb = y.split(' ').toSet()
        return (wa intersect wb).size.toFloat() / max(wa.size, wb.size)
    }

    /** Los discos de esas carpetas, tal como están. */
    suspend fun scanFolders(folders: List<String>, progress: (Int) -> Unit = {}): List<FixAlbum> = io.scan(folders, progress)

    /** Candidatos en MusicBrainz para un disco (o con lo que el usuario escriba). */
    suspend fun candidates(a: FixAlbum, query: String? = null): List<MbHit> {
        if (!query.isNullOrBlank()) return mb.searchFree(query)
        val exact = mb.searchReleases(a.artist, a.album, a.tracks.size)
        return exact.ifEmpty { mb.searchReleases(a.artist, a.album) }
    }

    /** Arma la propuesta: el mejor disco de MusicBrainz (o el elegido) y cómo cuadra cada canción. */
    suspend fun propose(a: FixAlbum, releaseId: String? = null): Proposal? {
        val hits = if (releaseId != null) emptyList() else candidates(a)
        // Se prueban los mejores 3 y se queda el que mejor cuadra en canciones y duraciones.
        val ids = releaseId?.let { listOf(it) } ?: hits.sortedByDescending { h ->
            h.score + (if (h.trackCount == a.tracks.size) 30 else 0) + (if (h.status == "Official") 5 else 0) + (if (h.format?.contains("Digital") == true || h.format == "CD") 3 else 0)
        }.take(3).map { it.id }
        var best: Proposal? = null
        for (id in ids) {
            val r = mb.release(id) ?: continue
            val p = match(a, r, hits.firstOrNull { it.id == id }?.score ?: 100)
            if (best == null || p.confidence > best.confidence) best = p
            if (p.confidence > 0.95f) break
        }
        return best
    }

    private fun match(a: FixAlbum, r: MbRelease, score: Int): Proposal {
        val files = a.tracks.sortedWith(compareBy({ it.disc ?: 1 }, { it.track ?: 999 }, { it.path }))
        val used = HashSet<MbTrack>()
        val changes = files.mapIndexed { i, f ->
            // 1) mismo disco y número; 2) el título más parecido con duración cercana; 3) por orden.
            val byNum = r.tracks.firstOrNull { it.disc == (f.disc ?: 1) && it.number == f.track && it !in used }
            val byTitle = r.tracks.filter { it !in used }.maxByOrNull { t ->
                sim(t.title, f.title ?: File(f.path).nameWithoutExtension) - (if (t.lengthMs != null && f.durationMs > 0) abs(t.lengthMs - f.durationMs) / 60_000f else 0f)
            }
            val byOrder = if (files.size == r.tracks.size) r.tracks[i] else null
            val t = listOfNotNull(byNum, byTitle, byOrder).firstOrNull { c ->
                c.lengthMs == null || f.durationMs == 0L || abs(c.lengthMs - f.durationMs) < 8000
            } ?: byTitle?.takeIf { sim(it.title, f.title ?: "") > 0.6f }
            if (t != null) used += t
            TrackChange(
                f, t,
                newTitle = t?.title?.takeIf { it != f.title },
                newNumber = t?.number?.takeIf { it != f.track },
                newDisc = t?.disc?.takeIf { r.discs > 1 && it != (f.disc ?: 1) },
            )
        }
        val matched = changes.count { it.target != null }
        val durOk = changes.mapNotNull { c -> c.target?.lengthMs?.let { l -> if (c.file.durationMs > 0) abs(l - c.file.durationMs) < 3000 else null } }
        val conf = 0.25f * (score / 100f) +
            0.25f * (if (r.tracks.size == files.size) 1f else max(0f, 1f - abs(r.tracks.size - files.size) / 4f)) +
            0.30f * (if (durOk.isEmpty()) 0.5f else durOk.count { it }.toFloat() / durOk.size) +
            0.10f * sim(r.title, a.album) + 0.10f * (matched.toFloat() / files.size)
        val genres = r.genres.mapNotNull { (g, c) -> GenreMap.canonical(g)?.let { it to c } }
            .groupBy({ it.first }, { it.second }).map { (k, v) -> k to v.sum() }.sortedByDescending { it.second }
            .let { gs -> gs.filter { it.second >= (gs.firstOrNull()?.second ?: 0) / 3 } }.take(3).map { it.first }
        return Proposal(a, r, conf.coerceIn(0f, 1f), changes, genres, mb.coverUrl(r.id))
    }

    /** Aplica la propuesta. Devuelve un resumen ("12 canciones, portada, 9 letras"). */
    suspend fun apply(p: Proposal, o: FixOptions = FixOptions(), progress: (String) -> Unit = {}): String = withContext(Dispatchers.IO) {
        val r = p.release
        val cover = if (o.cover && (o.replaceCover || !p.album.hasCover)) mb.cover(r) else null
        // Copia de lo que había, para deshacer.
        val stamp = System.currentTimeMillis()
        val undo = ArrayList<UndoEntry>()
        val sidecars = ArrayList<String>()
        p.tracks.forEachIndexed { i, c ->
            val old = io.read(c.file.path)
            val oldCover = if (cover != null) io.cover(c.file.path)?.let { bytes -> File(undoDir, "$stamp-$i.img").also { it.writeBytes(bytes) }.name } else null
            undo += UndoEntry(c.file.path, old, oldCover, hadCover = oldCover != null || cover == null)
        }
        File(undoDir, "$stamp.json").writeText(SubsonicJson.encodeToString(UndoLog(stamp, "${p.album.artist} — ${p.album.album}", undo)))

        var lyricsN = 0
        p.tracks.forEachIndexed { i, c ->
            progress("Escribiendo ${i + 1} de ${p.tracks.size}…")
            val t = c.target
            io.write(
                c.file.path,
                NewTags(
                    title = if (o.names) t?.title else null,
                    artist = if (o.names) t?.artist?.ifBlank { null } else null,
                    albumArtist = if (o.names) r.artist else null,
                    album = if (o.names) r.title else null,
                    track = if (o.names) t?.number else null,
                    trackTotal = if (o.names && t != null) r.tracks.count { it.disc == t.disc } else null,
                    disc = if (o.names) t?.disc else null,
                    discTotal = if (o.names && r.discs > 1) r.discs else null,
                    year = if (o.year) r.year else null,
                    genres = if (o.genres) p.genres.ifEmpty { null } else null,
                    mbReleaseId = r.id, mbRecordingId = t?.recordingId,
                ),
                cover,
            )
            // Letra sincronizada (si no tiene ya un .lrc).
            if (o.lyrics && !c.file.hasLyrics) {
                val f = File(c.file.path)
                val lrc = File(f.parentFile, f.nameWithoutExtension + ".lrc")
                if (!lrc.exists()) {
                    val song = Song(
                        id = "fix:" + c.file.path, title = (if (o.names) t?.title else null) ?: c.file.title ?: f.nameWithoutExtension,
                        artist = (if (o.names) t?.artist?.ifBlank { null } else null) ?: c.file.artist ?: r.artist, album = r.title,
                        duration = (c.file.durationMs / 1000).toInt().takeIf { it > 0 },
                    )
                    lyrics.lookup(song)?.takeIf { it.synced }?.let { l ->
                        lrc.writeText(LyricsRepository.toLrc(l)); sidecars += lrc.path; lyricsN++
                    }
                }
            }
        }
        // Portada también como archivo en la carpeta (la leen Navidrome, Plex y los reproductores).
        if (cover != null) {
            val cf = File(p.album.folder, "cover.jpg")
            if (!cf.exists()) { cf.writeBytes(cover); sidecars += cf.path }
        }
        if (sidecars.isNotEmpty()) File(undoDir, "$stamp.json").writeText(SubsonicJson.encodeToString(UndoLog(stamp, "${p.album.artist} — ${p.album.album}", undo, sidecars)))
        if (o.genres && p.genres.isNotEmpty()) genreIndex?.remember(r.artist, r.title, p.genres)
        listOfNotNull("${p.tracks.size} canciones", if (cover != null) "portada" else null, if (lyricsN > 0) "$lyricsN letras" else null).joinToString(", ")
    }

    /** Lo último que se arregló (para el botón "Deshacer"). */
    fun lastChange(): String? = undoDir.listFiles { f -> f.name.endsWith(".json") }?.maxByOrNull { it.name }
        ?.let { runCatching { SubsonicJson.decodeFromString<UndoLog>(it.readText()).album }.getOrNull() }

    /** Regresa el último disco arreglado a como estaba. */
    suspend fun undoLast(): String? = withContext(Dispatchers.IO) {
        val f = undoDir.listFiles { x -> x.name.endsWith(".json") }?.maxByOrNull { it.name } ?: return@withContext null
        val log = SubsonicJson.decodeFromString<UndoLog>(f.readText())
        for (e in log.entries) {
            val img = e.coverFile?.let { File(undoDir, it) }?.takeIf { it.exists() }
            io.write(e.path, e.tags, img?.readBytes(), exact = true, removeCover = !e.hadCover)
            img?.delete()
        }
        log.sidecars.forEach { File(it).delete() }
        f.delete()
        log.album
    }
}
