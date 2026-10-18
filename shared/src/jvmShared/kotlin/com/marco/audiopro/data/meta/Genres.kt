package com.marco.audiopro.data.meta

import com.marco.audiopro.data.discover.normName
import com.marco.audiopro.data.subsonic.Album
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.data.subsonic.SubsonicJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * El mapa de géneros: nombre bonito, familia y los nombres con que aparece en etiquetas, Last.fm o
 * MusicBrainz. Así "shoegazing", "Shoegaze" y "shoe gaze" son lo mismo y quedan en "Indie y alternativo".
 */
object GenreMap {
    private val FAMILIES: Map<String, List<String>> = mapOf(
        "Rock" to listOf("Rock", "Classic Rock", "Hard Rock", "Psychedelic Rock", "Progressive Rock", "Blues Rock", "Garage Rock", "Stoner Rock", "Space Rock", "Southern Rock", "Rock en español", "Rock and Roll", "Krautrock", "Art Rock", "Glam Rock"),
        "Indie y alternativo" to listOf("Alternative Rock", "Indie Rock", "Indie Pop", "Shoegaze", "Dream Pop", "Post-Punk", "Post-Rock", "Noise Rock", "Grunge", "Britpop", "Math Rock", "Emo", "Midwest Emo", "Slowcore", "Sadcore", "Indie Folk", "Lo-Fi", "Bedroom Pop", "Jangle Pop", "Noise Pop", "Neo-Psychedelia", "New Wave", "Darkwave", "Coldwave", "Gothic Rock", "Madchester", "Indie"),
        "Metal" to listOf("Metal", "Heavy Metal", "Thrash Metal", "Death Metal", "Black Metal", "Doom Metal", "Sludge Metal", "Nu Metal", "Metalcore", "Deathcore", "Progressive Metal", "Post-Metal", "Djent", "Power Metal", "Groove Metal", "Alternative Metal", "Industrial Metal", "Blackgaze"),
        "Punk" to listOf("Punk", "Punk Rock", "Hardcore Punk", "Pop Punk", "Post-Hardcore", "Skate Punk", "Ska Punk", "Crust Punk", "Screamo"),
        "Pop" to listOf("Pop", "Synth-Pop", "Electropop", "Dance-Pop", "Art Pop", "Hyperpop", "K-Pop", "J-Pop", "City Pop", "Teen Pop", "Baroque Pop", "Chamber Pop", "Sophisti-Pop", "Pop Rock", "Power Pop", "Europop", "Latin Pop"),
        "Hip-hop y rap" to listOf("Hip Hop", "Rap", "Trap", "Boom Bap", "Drill", "Conscious Hip Hop", "Gangsta Rap", "Cloud Rap", "Emo Rap", "Jazz Rap", "Abstract Hip Hop", "Phonk", "Memphis Rap", "Hardcore Hip Hop", "Southern Hip Hop", "Grime", "Rap en español", "Trap latino"),
        "Electrónica" to listOf("Electronic", "House", "Deep House", "Tech House", "Techno", "Minimal Techno", "Dub Techno", "Trance", "Drum and Bass", "Jungle", "Dubstep", "UK Garage", "Breakbeat", "Big Beat", "IDM", "Electro", "Synthwave", "Vaporwave", "Chillwave", "Downtempo", "Trip Hop", "Electroclash", "French House", "Nu Disco", "Italo Disco", "Hardstyle", "EDM", "Future Bass", "Footwork", "Glitch", "Industrial", "EBM", "Witch House", "Ambient Techno"),
        "R&B y soul" to listOf("R&B", "Contemporary R&B", "Alternative R&B", "Neo-Soul", "Soul", "Motown", "Quiet Storm", "New Jack Swing"),
        "Funk y disco" to listOf("Funk", "Disco", "Boogie", "Rare Groove", "P-Funk", "Afrobeat", "Afrobeats", "Go-Go"),
        "Regional mexicano" to listOf("Regional Mexicano", "Corridos", "Corridos Tumbados", "Banda", "Norteño", "Mariachi", "Ranchera", "Sierreño", "Cumbia Norteña", "Grupera", "Duranguense", "Bolero"),
        "Latina" to listOf("Reggaeton", "Latin", "Cumbia", "Salsa", "Bachata", "Merengue", "Dembow", "Latin Alternative", "Bossa Nova", "MPB", "Samba", "Tango", "Nueva Canción", "Trova", "Son Cubano", "Champeta", "Latin Rock"),
        "Jazz" to listOf("Jazz", "Bebop", "Hard Bop", "Cool Jazz", "Free Jazz", "Jazz Fusion", "Smooth Jazz", "Modal Jazz", "Spiritual Jazz", "Vocal Jazz", "Nu Jazz", "Acid Jazz", "Swing", "Big Band"),
        "Clásica" to listOf("Classical", "Baroque", "Romantic", "Opera", "Contemporary Classical", "Minimalism", "Modern Classical", "Neoclassical", "Soundtrack", "Film Score", "Video Game Music"),
        "Folk y country" to listOf("Folk", "Folk Rock", "Singer-Songwriter", "Americana", "Country", "Alt-Country", "Bluegrass", "Freak Folk", "Chamber Folk"),
        "Blues" to listOf("Blues", "Delta Blues", "Chicago Blues", "Electric Blues"),
        "Reggae" to listOf("Reggae", "Dub", "Ska", "Dancehall", "Roots Reggae", "Rocksteady"),
        "Ambient y experimental" to listOf("Ambient", "Dark Ambient", "Drone", "Experimental", "Noise", "Musique Concrète", "New Age", "Field Recordings", "Plunderphonics", "Avant-Garde"),
    )

    private val ALIASES: Map<String, String> = mapOf(
        "shoegazing" to "Shoegaze", "shoe gaze" to "Shoegaze", "nu gaze" to "Shoegaze", "nugaze" to "Shoegaze",
        "dreampop" to "Dream Pop", "post punk" to "Post-Punk", "postpunk" to "Post-Punk", "post rock" to "Post-Rock",
        "hip hop" to "Hip Hop", "hiphop" to "Hip Hop", "hip-hop" to "Hip Hop", "rap hip hop" to "Hip Hop", "rnb" to "R&B", "r b" to "R&B",
        "rhythm and blues" to "R&B", "synthpop" to "Synth-Pop", "synth pop" to "Synth-Pop", "electro pop" to "Electropop",
        "dnb" to "Drum and Bass", "drum n bass" to "Drum and Bass", "d b" to "Drum and Bass", "electronica" to "Electronic",
        "electronic music" to "Electronic", "dance" to "EDM", "idm" to "IDM", "trip-hop" to "Trip Hop", "triphop" to "Trip Hop",
        "alt rock" to "Alternative Rock", "alternative" to "Alternative Rock", "indie" to "Indie", "lofi" to "Lo-Fi", "lo fi" to "Lo-Fi",
        "regional mexican" to "Regional Mexicano", "musica mexicana" to "Regional Mexicano", "corrido" to "Corridos",
        "corridos belicos" to "Corridos Tumbados", "corridos tumbados" to "Corridos Tumbados", "norteno" to "Norteño", "sierreno" to "Sierreño",
        "reggaeton" to "Reggaeton", "reggaetón" to "Reggaeton", "urbano latino" to "Reggaeton", "latin urban" to "Reggaeton",
        "rock en espanol" to "Rock en español", "rock latino" to "Latin Rock", "latin trap" to "Trap latino", "trap latino" to "Trap latino",
        "neo soul" to "Neo-Soul", "classical music" to "Classical", "ost" to "Soundtrack", "score" to "Film Score",
        "singer songwriter" to "Singer-Songwriter", "cantautor" to "Singer-Songwriter", "alt country" to "Alt-Country",
        "heavy" to "Heavy Metal", "nu-metal" to "Nu Metal", "numetal" to "Nu Metal", "post hardcore" to "Post-Hardcore",
        "pop rock" to "Pop Rock", "pop punk" to "Pop Punk", "kpop" to "K-Pop", "jpop" to "J-Pop", "citypop" to "City Pop",
        "rock n roll" to "Rock and Roll", "rock & roll" to "Rock and Roll", "funk rock" to "Funk", "disco funk" to "Disco",
        "french touch" to "French House", "nu-disco" to "Nu Disco", "italo" to "Italo Disco", "dark wave" to "Darkwave", "cold wave" to "Coldwave",
        "goth rock" to "Gothic Rock", "gothic" to "Gothic Rock", "slowcore" to "Slowcore", "emo rap" to "Emo Rap", "cloud rap" to "Cloud Rap",
        "uk drill" to "Drill", "boom bap" to "Boom Bap", "jazz-rap" to "Jazz Rap", "abstract hip-hop" to "Abstract Hip Hop",
        "bossanova" to "Bossa Nova", "trova cubana" to "Trova", "nueva trova" to "Trova", "musica clasica" to "Classical",
    )

    private val canon: Map<String, String> = buildMap {
        FAMILIES.values.flatten().forEach { put(key(it), it) }
        ALIASES.forEach { (k, v) -> put(key(k), v) }
    }
    private val familyOf: Map<String, String> = FAMILIES.flatMap { (f, gs) -> gs.map { it to f } }.toMap()

    private fun key(s: String) = normName(s.replace("&", " and ").replace("-", " "))

    /** El nombre bonito, o null si no es un género que conozcamos (etiquetas tipo "favorites", "seen live"). */
    fun canonical(raw: String): String? = canon[key(raw)]

    /** Familia del género (o "Otros"). */
    fun family(genre: String): String = familyOf[genre] ?: "Otros"

    val families: List<String> get() = FAMILIES.keys.toList() + "Otros"

    /** De una etiqueta de archivo como "Rock; Shoegaze / Dream Pop" saca los géneros conocidos. */
    fun fromTag(tag: String?): List<String> = tag.orEmpty().split(Regex("[;/,|]")).map { it.trim() }.filter { it.isNotEmpty() }
        .map { canonical(it) ?: it.replaceFirstChar(Char::uppercase) }.distinct()
}

@Serializable
private data class GenreCache(val byAlbum: Map<String, List<String>> = emptyMap())

/** Un género con sus álbumes (de tu biblioteca). */
data class GenreEntry(val name: String, val family: String, val albums: List<Album>) {
    val covers: List<String> get() = albums.mapNotNull { it.coverArt }.distinct().take(4)
}

data class GenreLibrary(val genres: List<GenreEntry> = emptyList(), val loaded: Boolean = false, val tuned: Int = 0, val toTune: Int = 0) {
    fun get(name: String) = genres.firstOrNull { it.name.equals(name, true) }
}

/**
 * Géneros de verdad para tu biblioteca. Las etiquetas de los archivos muchas veces dicen "Rock" a
 * todo; aquí cada álbum se revisa en MusicBrainz (los géneros que vota la comunidad) y se queda con
 * los más votados. Lo revisado se guarda (genres.json) y la primera vez se hace poco a poco, en
 * segundo plano (MusicBrainz pide una consulta por segundo). Funciona igual con servidor o sin él.
 */
class GenreIndex(private val api: SubsonicApi, private val mb: MusicBrainz, dir: File, private val scope: CoroutineScope) {
    private val file = File(dir, "genres_mb.json")
    private var cache: Map<String, List<String>> = runCatching { SubsonicJson.decodeFromString<GenreCache>(file.readText()).byAlbum }.getOrDefault(emptyMap())
    private val _state = MutableStateFlow(GenreLibrary())
    val state: StateFlow<GenreLibrary> = _state
    private var albums: List<Album> = emptyList()
    private var job: Job? = null

    private fun key(a: Album) = normName(a.artist ?: "") + "|" + normName(a.name)

    /** Los géneros de un álbum: los de MusicBrainz si ya se revisó, si no los de la etiqueta. */
    fun genresOf(a: Album): List<String> =
        cache[key(a)]?.takeIf { it.isNotEmpty() } ?: GenreMap.fromTag(a.genres?.joinToString(";") { it.name }?.ifBlank { null } ?: a.genre)

    private fun rebuild() {
        val by = HashMap<String, MutableList<Album>>()
        for (a in albums) for (g in genresOf(a)) by.getOrPut(g) { mutableListOf() }.add(a)
        val list = by.map { (g, al) -> GenreEntry(g, GenreMap.family(g), al.sortedByDescending { it.playCount ?: 0 }) }
            .sortedByDescending { it.albums.size }
        _state.value = _state.value.copy(genres = list, loaded = true, tuned = albums.count { cache.containsKey(key(it)) }, toTune = albums.size)
    }

    /** Carga toda tu biblioteca de álbumes (en páginas) y arma los géneros; después los afina solo. */
    suspend fun load(force: Boolean = false) {
        if (_state.value.loaded && !force) { tune(); return }
        val all = ArrayList<Album>()
        var off = 0
        while (true) {
            val page = runCatching { api.albumList("alphabeticalByName", 500, off) }.getOrDefault(emptyList())
            all += page; off += page.size
            if (page.size < 500) break
        }
        albums = all
        rebuild()
        tune()
    }

    /** Afina en segundo plano los álbumes que no se han revisado. */
    fun tune() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            var n = 0
            for (a in albums.sortedByDescending { it.playCount ?: 0 }) {
                if (!isActive) break
                if (cache.containsKey(key(a))) continue
                val g = runCatching { mb.searchGroup(a.artist ?: "", a.name) }.getOrNull()
                val found = g?.tags.orEmpty().mapNotNull { (t, c) -> GenreMap.canonical(t)?.let { it to c } }
                    .groupBy({ it.first }, { it.second }).map { (k, v) -> k to v.sum() }.sortedByDescending { it.second }
                // Los más votados (hasta 3) y que no sean puro ruido de un solo voto si hay mejores.
                val top = found.filter { it.second >= (found.firstOrNull()?.second ?: 0) / 3 }.take(3).map { it.first }
                cache = cache + (key(a) to top)
                if (++n % 10 == 0) { save(); rebuild() }
            }
            save(); rebuild()
        }
    }

    private suspend fun save() = withContext(Dispatchers.IO) { runCatching { file.writeText(SubsonicJson.encodeToString(GenreCache(cache))) } }

    /** Cuando el arreglador de metadatos ya sabe los géneros de un álbum, se guardan aquí también. */
    fun remember(artist: String, album: String, genres: List<String>) {
        cache = cache + ((normName(artist) + "|" + normName(album)) to genres)
        scope.launch { save() }
    }
}
