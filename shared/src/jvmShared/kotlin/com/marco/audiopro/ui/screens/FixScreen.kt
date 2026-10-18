package com.marco.audiopro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.audiopro.core.AppGraph
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.data.local.LocalMode
import com.marco.audiopro.data.meta.FixAlbum
import com.marco.audiopro.data.meta.FixOptions
import com.marco.audiopro.data.meta.GenreMap
import com.marco.audiopro.data.meta.MbHit
import com.marco.audiopro.data.meta.Proposal
import com.marco.audiopro.ui.components.CoverArt
import com.marco.audiopro.ui.components.GlassChip
import com.marco.audiopro.ui.components.bouncyClick
import com.marco.audiopro.ui.components.textInput
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Cómo va cada disco. */
sealed interface FixState {
    data object Waiting : FixState
    data object Looking : FixState
    data object NotFound : FixState
    data class Ready(val p: Proposal) : FixState
    data object Applying : FixState
    data class Done(val summary: String) : FixState
    data class Failed(val error: String) : FixState
}

/** La sesión de arreglo vive aquí (no en la pantalla): si te vas a otra sección, sigue revisando. */
object FixSession {
    val albums = mutableStateListOf<FixAlbum>()
    val states = mutableStateMapOf<String, FixState>()
    var status by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    private var job: Job? = null

    fun folders(c: AppGraph): List<String> =
        (c.settings.getString("fix_folders")?.split("\n").orEmpty() + LocalMode.folders(c.settings)).filter { it.isNotBlank() }.distinct()

    fun addFolder(c: AppGraph, path: String) = c.settings.putString("fix_folders", (folders(c) + path).distinct().joinToString("\n"))
    fun removeFolder(c: AppGraph, path: String) = c.settings.putString("fix_folders", folders(c).filter { it != path }.joinToString("\n"))

    /** Lee los discos y los busca uno por uno en MusicBrainz (una consulta por segundo). */
    fun review(c: AppGraph, scope: CoroutineScope) {
        val fixer = c.fixer ?: return
        if (busy) return
        job = scope.launch {
            busy = true
            status = "Leyendo tus archivos…"
            val found = runCatching { fixer.scanFolders(folders(c)) { n -> status = "Leyendo tus archivos… $n canciones" } }.getOrElse { status = "No se pudo leer: ${it.message}"; busy = false; return@launch }
            albums.clear(); albums += found
            states.clear(); found.forEach { states[it.key] = FixState.Waiting }
            found.forEachIndexed { i, a ->
                status = "Buscando en MusicBrainz… ${i + 1} de ${found.size}"
                states[a.key] = FixState.Looking
                states[a.key] = runCatching { fixer.propose(a)?.let { FixState.Ready(it) } ?: FixState.NotFound }.getOrElse { FixState.Failed(it.message ?: "error") }
            }
            val ready = states.values.filterIsInstance<FixState.Ready>()
            status = "Listo: ${found.size} discos · ${ready.count { !it.p.clean }} con cambios · ${states.values.count { it == FixState.NotFound }} sin encontrar"
            busy = false
        }
    }

    fun apply(c: AppGraph, scope: CoroutineScope, p: Proposal, o: FixOptions) {
        val fixer = c.fixer ?: return
        scope.launch {
            states[p.album.key] = FixState.Applying
            states[p.album.key] = runCatching { FixState.Done(fixer.apply(p, o) { status = it }) }.getOrElse { FixState.Failed(it.message ?: "error") }
            afterChange(c)
        }
    }

    fun applySafe(c: AppGraph, scope: CoroutineScope) {
        val fixer = c.fixer ?: return
        val list = states.values.filterIsInstance<FixState.Ready>().map { it.p }.filter { it.safe }
        scope.launch {
            busy = true
            list.forEachIndexed { i, p ->
                status = "Arreglando ${i + 1} de ${list.size}: ${p.release.title}"
                states[p.album.key] = FixState.Applying
                states[p.album.key] = runCatching { FixState.Done(fixer.apply(p)) }.getOrElse { FixState.Failed(it.message ?: "error") }
            }
            status = "Se arreglaron ${list.size} discos"
            busy = false
            afterChange(c)
        }
    }

    /** Que la biblioteca vea los cambios: en modo local se vuelve a leer; con Navidrome se le pide re-escanear. */
    private suspend fun afterChange(c: AppGraph) {
        val lib = c.localLibrary
        if (lib != null && LocalMode.isOn(c.settings)) lib.rescan() else c.api.startScan()
        runCatching { c.genres.load(force = true) }
    }
}

/**
 * Arreglar metadatos (PC): revisa tus discos contra MusicBrainz y propone nombres, número de pista,
 * año original, géneros, portada grande y letras sincronizadas. Ves el antes y el después de cada
 * disco; "Aplicar todos los seguros" arregla de una vez los que cuadran casi perfecto. Todo se
 * puede deshacer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FixScreen() {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    val fixer = c.fixer
    var folders by remember { mutableStateOf(FixSession.folders(c)) }
    var undoName by remember { mutableStateOf(fixer?.lastChange()) }
    var open by remember { mutableStateOf<String?>(null) }
    val pick = com.marco.audiopro.platform.rememberLocalMusicPicker { p ->
        if (p != null) { FixSession.addFolder(c, p); folders = FixSession.folders(c) }
    }
    val safe = FixSession.states.values.filterIsInstance<FixState.Ready>().count { it.p.safe && !it.p.clean }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 30.dp)) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 18.dp)) {
                Text("Arreglar metadatos", style = Display.copy(fontSize = 34.sp))
                Text(
                    "Revisa tus discos contra MusicBrainz (la base abierta que usan Picard y Navidrome) y les pone los nombres bien, el año " +
                        "original, géneros de verdad, portada grande y letras sincronizadas. Así todo se ve igual de bien venga de donde venga. " +
                        "Nada se cambia sin que lo apruebes y todo se puede deshacer.",
                    fontSize = 13.sp, color = Hifi.TextDim,
                )
                if (fixer == null) {
                    Text("Esto se hace desde la PC (ahí están tus archivos).", color = Hifi.LedRed, modifier = Modifier.padding(top = 12.dp))
                    return@Column
                }
                Text("CARPETAS", style = Mono.copy(color = Hifi.TextFaint), modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    folders.forEach { f ->
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(f, style = Mono.copy(color = Hifi.Text, fontSize = 11.sp))
                            Icon(Icons.Default.Close, "Quitar", tint = Hifi.TextDim, modifier = Modifier.size(24.dp).clip(RoundedCornerShape(50)).bouncyClick { FixSession.removeFolder(c, f); folders = FixSession.folders(c) }.padding(4.dp))
                        }
                    }
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).bouncyClick(onClick = pick).padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Add, null, tint = Hifi.Text, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Agregar carpeta", fontSize = 12.sp)
                    }
                }
                if (folders.isEmpty()) Text(
                    "Agrega la carpeta donde tienes tus discos (por ejemplo la de tu Navidrome, si está en esta PC o en tu NAS).",
                    fontSize = 12.sp, color = Hifi.TextDim, modifier = Modifier.padding(top = 6.dp),
                )
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillButton("Revisar mis discos", filled = true, enabled = !FixSession.busy && folders.isNotEmpty()) { FixSession.review(c, scope) }
                    if (safe > 0) PillButton("Aplicar los seguros ($safe)", enabled = !FixSession.busy) { FixSession.applySafe(c, scope) }
                    undoName?.let { n ->
                        PillButton("Deshacer: ${n.take(40)}", enabled = !FixSession.busy) {
                            scope.launch { fixer.undoLast()?.let { FixSession.status = "Se regresó \"$it\" a como estaba" }; undoName = fixer.lastChange() }
                        }
                    }
                }
                if (FixSession.albums.isNotEmpty()) {
                    val vals = FixSession.states.values
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat("Discos", FixSession.albums.size, Modifier.weight(1f))
                        Stat("Con cambios", vals.count { it is FixState.Ready && !it.p.clean }, Modifier.weight(1f))
                        Stat("Seguros", vals.count { it is FixState.Ready && it.p.safe && !it.p.clean }, Modifier.weight(1f))
                        Stat("Sin encontrar", vals.count { it == FixState.NotFound }, Modifier.weight(1f))
                        Stat("Arreglados", vals.count { it is FixState.Done }, Modifier.weight(1f))
                    }
                }
                FixSession.status?.let {
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (FixSession.busy) { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Hifi.TextDim); Spacer(Modifier.width(8.dp)) }
                        Text(it, fontSize = 13.sp, color = Hifi.Text)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        items(FixSession.albums, key = { it.key }) { a ->
            AlbumFixRow(a, FixSession.states[a.key] ?: FixState.Waiting, open == a.key, { open = if (open == a.key) null else a.key }) { undoName = fixer?.lastChange() }
        }
    }
}

@Composable
private fun PillButton(label: String, filled: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (filled) Color.Black else Hifi.Text,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (filled) Color.White.copy(alpha = if (enabled) 1f else 0.4f) else Color.White.copy(alpha = if (enabled) 0.12f else 0.05f))
            .then(if (enabled) Modifier.bouncyClick(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlbumFixRow(a: FixAlbum, st: FixState, expanded: Boolean, toggle: () -> Unit, onApplied: () -> Unit) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    val p = (st as? FixState.Ready)?.p
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = if (expanded) 0.07f else 0.03f))) {
        Row(Modifier.fillMaxWidth().bouncyClick(onClick = toggle).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverArt(p?.coverUrl, Modifier.size(46.dp), size = 200, corner = 8.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.album, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${a.artist} · ${a.tracks.size} canciones" + (a.year?.let { " · $it" } ?: "") + (a.genre?.let { " · $it" } ?: ""), fontSize = 12.sp, color = Hifi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val (label, color) = when (st) {
                FixState.Waiting -> "En fila" to Hifi.TextFaint
                FixState.Looking -> "Buscando…" to Hifi.TextDim
                FixState.NotFound -> "No lo encontré" to Hifi.TextDim
                is FixState.Ready -> if (st.p.clean) "Ya está bien" to Color(0xFF6BD49A) else "${changes(st.p)} · ${(st.p.confidence * 100).toInt()}%" to (if (st.p.safe) Hifi.Text else Color(0xFFE8B04A))
                FixState.Applying -> "Escribiendo…" to Hifi.TextDim
                is FixState.Done -> "Arreglado · ${st.summary}" to Color(0xFF6BD49A)
                is FixState.Failed -> "Error: ${st.error.take(40)}" to Hifi.LedRed
            }
            Text(label, fontSize = 12.sp, color = color, maxLines = 1)
        }
        if (expanded) FixDetail(a, p) { pr, o -> FixSession.apply(c, scope, pr, o); onApplied() }
    }
}

private fun changes(p: Proposal): String = listOfNotNull(
    if (p.newAlbum != null || p.newArtist != null) "nombre" else null,
    if (p.renamedTracks > 0) "${p.renamedTracks} canciones" else null,
    if (p.newYear != null) "año" else null,
    if (p.newGenres != null) "géneros" else null,
).joinToString(", ").ifBlank { "portada/letras" }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FixDetail(a: FixAlbum, p: Proposal?, apply: (Proposal, FixOptions) -> Unit) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    var o by remember { mutableStateOf(FixOptions()) }
    var query by remember { mutableStateOf("${a.artist} ${a.album}") }
    var hits by remember { mutableStateOf<List<MbHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
        if (p != null) {
            Row(verticalAlignment = Alignment.Top) {
                CoverArt(p.coverUrl, Modifier.size(140.dp), size = 600, corner = 10.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Diff("Álbum", a.album, p.release.title)
                    Diff("Artista", a.artist, p.release.artist)
                    Diff("Año", a.year?.toString() ?: "—", p.release.year?.toString() ?: "—")
                    Diff("Géneros", a.genre ?: "—", p.genres.joinToString(", ").ifBlank { "—" })
                    Text(
                        "MusicBrainz: ${p.release.title} (${p.release.date ?: "s/f"})${p.release.label?.let { " · $it" } ?: ""} · ${p.release.tracks.size} canciones",
                        style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp), modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            Text("CANCIONES", style = Mono.copy(color = Hifi.TextFaint), modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            p.tracks.forEach { t ->
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${t.file.track ?: "?"}", style = Mono.copy(color = Hifi.TextFaint), modifier = Modifier.width(28.dp))
                    Text(t.file.title ?: java.io.File(t.file.path).nameWithoutExtension, fontSize = 12.sp, color = if (t.newTitle != null) Hifi.TextDim else Hifi.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (t.newTitle != null || t.newNumber != null) {
                        Text("→  ${t.target?.number ?: ""}. ${t.target?.title ?: ""}", fontSize = 12.sp, color = Hifi.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else if (t.target == null) Text("sin pareja", fontSize = 12.sp, color = Color(0xFFE8B04A), modifier = Modifier.weight(1f))
                    else Spacer(Modifier.weight(1f))
                }
            }
            Text("QUÉ CAMBIAR", style = Mono.copy(color = Hifi.TextFaint), modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip("Nombres y números", o.names) { o = o.copy(names = !o.names) }
                GlassChip("Año original", o.year) { o = o.copy(year = !o.year) }
                GlassChip("Géneros", o.genres) { o = o.copy(genres = !o.genres) }
                GlassChip(if (a.hasCover) "Portada nueva" else "Portada", o.cover) { o = o.copy(cover = !o.cover) }
                GlassChip("Letras sincronizadas", o.lyrics) { o = o.copy(lyrics = !o.lyrics) }
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Aplicar a este disco", filled = true) { apply(p, o) }
            }
        } else {
            Text("No encontré este disco en MusicBrainz con su nombre. Búscalo a mano:", fontSize = 13.sp, color = Hifi.TextDim)
        }
        // Buscar otra edición / a mano.
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, Modifier.weight(1f).textInput(), singleLine = true, label = { Text("Buscar otra edición") })
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                searching = true
                scope.launch { hits = runCatching { c.fixer!!.candidates(a, query) }.getOrDefault(emptyList()); searching = false }
            }) { Text(if (searching) "Buscando…" else "Buscar") }
        }
        hits.forEach { h ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).bouncyClick {
                    FixSession.states[a.key] = FixState.Looking
                    scope.launch {
                        FixSession.states[a.key] = runCatching { c.fixer!!.propose(a, h.id)?.let { FixState.Ready(it) } ?: FixState.NotFound }.getOrElse { FixState.Failed(it.message ?: "") }
                        hits = emptyList()
                    }
                }.padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text("${h.title} — ${h.artist}", fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(h.date, h.country, h.format, "${h.trackCount} canc.").joinToString(" · "), style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp))
            }
        }
    }
}

@Composable
private fun Diff(label: String, old: String, new: String) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp), modifier = Modifier.width(70.dp))
        if (old == new) Text(old, fontSize = 13.sp, color = Hifi.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        else {
            Text(old, fontSize = 13.sp, color = Hifi.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text("  →  ", fontSize = 13.sp, color = Hifi.TextDim)
            Text(new, fontSize = 13.sp, color = Hifi.Text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
    }
}

@Composable
private fun Stat(label: String, n: Int, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text("$n", style = Display.copy(fontSize = 26.sp))
        Text(label.uppercase(), style = Mono.copy(color = Hifi.TextFaint, fontSize = 9.sp, letterSpacing = 1.sp))
    }
}
