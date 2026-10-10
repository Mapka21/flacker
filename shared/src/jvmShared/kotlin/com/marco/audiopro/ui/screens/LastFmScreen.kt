package com.marco.audiopro.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.marco.audiopro.core.AppGraph
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.data.discover.normName
import com.marco.audiopro.data.info.LfDashboard
import com.marco.audiopro.data.info.LfItem
import com.marco.audiopro.data.info.LfPeriod
import com.marco.audiopro.data.info.LfScrobble
import com.marco.audiopro.data.info.LfTops
import com.marco.audiopro.ui.components.ArrowRow
import com.marco.audiopro.ui.components.BarChart
import com.marco.audiopro.ui.components.GlassChip
import com.marco.audiopro.ui.components.Load
import com.marco.audiopro.ui.components.RankBar
import com.marco.audiopro.ui.components.ScreenBackButton
import com.marco.audiopro.ui.components.SectionHeader
import com.marco.audiopro.ui.components.ShareBar
import com.marco.audiopro.ui.components.StatTile
import com.marco.audiopro.ui.components.bouncyClick
import com.marco.audiopro.ui.components.fmt
import com.marco.audiopro.ui.components.glassCard
import com.marco.audiopro.ui.components.rememberLoad
import com.marco.audiopro.ui.openAlbum
import com.marco.audiopro.ui.openArtist
import com.marco.audiopro.ui.openExternalAlbum
import com.marco.audiopro.ui.openExternalArtist
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val es = Locale.forLanguageTag("es-MX")

/**
 * "Mi Last.fm": tu perfil como en last.fm pero dentro de la app — números, gráficas de cuándo
 * escuchas, tus tops por periodo, géneros, scrobbles recientes y lo que te encanta. Todo lo que
 * esté en tu biblioteca se abre o se reproduce directo.
 */
@Composable
fun LastFmScreen(nav: NavController) {
    val c = LocalApp.current
    val prefs by c.settings.prefs.collectAsState()
    var editing by remember { mutableStateOf(false) }
    if (prefs.lastFmUser.isBlank() || prefs.lastFmKey.isBlank() || editing) {
        LastFmSetup(prefs.lastFmUser, prefs.lastFmKey, nav, canCancel = editing) { editing = false }
    } else {
        Dashboard(prefs.lastFmUser, prefs.lastFmKey, nav) { editing = true }
    }
}

// ------------------------------------------------------------------ configuración

@Composable
private fun LastFmSetup(user0: String, key0: String, nav: NavController, canCancel: Boolean, onDone: () -> Unit) {
    val c = LocalApp.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf(user0) }
    var key by remember { mutableStateOf(key0) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth()) { ScreenBackButton(nav) }
        Spacer(Modifier.height(30.dp))
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().glassCard().padding(24.dp)) {
            Text("MI LAST.FM", style = Mono.copy(color = LastFmRed))
            Text("Conecta tu perfil", style = Display.copy(fontSize = 28.sp))
            Text(
                "Tu panel de Last.fm aquí adentro: tus números, gráficas, tops y scrobbles. Sólo necesita tu usuario y una API key (gratis, tarda un minuto).",
                fontSize = 13.sp, color = Hifi.TextDim,
            )
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(user, { user = it; error = null }, label = { Text("Usuario de Last.fm") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                key, { key = it; error = null }, label = { Text("API key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { uri.openUri("https://www.last.fm/api/account/create") }) {
                Text("Crear mi API key gratis en last.fm", color = Hifi.Accent)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Hifi.Accent, modifier = Modifier.size(14.dp))
            }
            Text("En ese formulario pon cualquier nombre (\"Flacker\") y deja \"Callback URL\" vacío; copia la \"API key\".", fontSize = 11.sp, color = Hifi.TextFaint)
            error?.let { Text(it, color = Hifi.LedRed, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(LastFmRed).bouncyClick {
                        if (checking || user.isBlank() || key.isBlank()) return@bouncyClick
                        checking = true; error = null
                        scope.launch {
                            runCatching { c.lastFm.user(key.trim(), user.trim()) }
                                .onSuccess {
                                    c.settings.updatePrefs { p -> p.copy(lastFmUser = user.trim(), lastFmKey = key.trim()) }
                                    onDone()
                                }
                                .onFailure { error = it.message }
                            checking = false
                        }
                    }.padding(horizontal = 22.dp, vertical = 12.dp),
                ) {
                    if (checking) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Conectar", color = Color.White, fontWeight = FontWeight.Bold)
                }
                if (canCancel) TextButton(onClick = onDone) { Text("Cancelar", color = Hifi.TextDim) }
            }
        }
    }
}

/** El rojo de Last.fm (sólo para su marca, nunca para datos). */
private val LastFmRed = Color(0xFFD51007)

// ------------------------------------------------------------------ panel

@Composable
private fun Dashboard(user: String, key: String, nav: NavController, onEdit: () -> Unit) {
    val c = LocalApp.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var period by rememberSaveable { mutableStateOf(LfPeriod.MONTH) }
    val dash = rememberLoad(Triple(user, key, refresh)) { c.lastFm.dashboard(key, user) }
    val tops = rememberLoad(Triple(user, period, refresh)) { c.lastFm.tops(key, user, period) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 760.dp
        when (val d = dash.state.value) {
            is Load.Loading -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = LastFmRed, strokeWidth = 2.dp)
                Spacer(Modifier.height(12.dp))
                Text("Leyendo tu Last.fm…", color = Hifi.TextDim)
            }
            is Load.Err -> Column(Modifier.fillMaxSize().padding(30.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No se pudo leer tu Last.fm", style = Display.copy(fontSize = 20.sp))
                Text(d.message, color = Hifi.TextDim)
                Row {
                    TextButton(onClick = { dash.retry() }) { Text("Reintentar", color = Hifi.Accent) }
                    TextButton(onClick = onEdit) { Text("Cambiar usuario / key", color = Hifi.TextDim) }
                }
            }
            is Load.Ok -> {
                val data = d.value
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                    item { Hero(data, wide, onOpen = { data.user.url?.let(uri::openUri) }, onRefresh = { refresh++ }, onEdit = onEdit) }
                    item { Stats(data, wide) }
                    data.recent.firstOrNull { it.time == null }?.let { np -> item { NowPlayingCard(np) { scope.openTrack(c, nav, np.title, np.artist) } } }
                    item { ListeningCharts(data, wide) }
                    item {
                        SectionHeader("Tus tops")
                        ArrowRow(contentPadding = PaddingValues(horizontal = 16.dp), spacing = 8.dp, arrowsOffset = 0.dp) {
                            items(LfPeriod.entries.toList(), key = { it.name }) { p -> GlassChip(p.label, p == period) { period = p } }
                        }
                    }
                    when (val t = tops.state.value) {
                        is Load.Ok -> item { Tops(t.value, period, wide, nav) }
                        is Load.Err -> item { Text(t.message, color = Hifi.LedRed, modifier = Modifier.padding(20.dp)) }
                        else -> item { Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Hifi.Accent, strokeWidth = 2.dp) } }
                    }
                    item { SectionHeader("Scrobbles recientes") }
                    items(data.recent.filter { it.time != null }.take(30)) { s -> ScrobbleRow(s) { scope.openTrack(c, nav, s.title, s.artist) } }
                    if (data.loved.isNotEmpty()) {
                        item { SectionHeader("Te encantan · ${fmt(data.lovedTotal)}") }
                        items(data.loved.take(15)) { s -> ScrobbleRow(s) { scope.openTrack(c, nav, s.title, s.artist) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(d: LfDashboard, wide: Boolean, onOpen: () -> Unit, onRefresh: () -> Unit, onEdit: () -> Unit) {
    val u = d.user
    Box(
        Modifier.fillMaxWidth().statusBarsPadding()
            .background(Brush.verticalGradient(listOf(lerp(LastFmRed, Color.Black, 0.25f).copy(alpha = 0.75f), Color.Transparent))),
    ) {
        Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 28.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(if (wide) 120.dp else 88.dp).shadow(20.dp, CircleShape).clip(CircleShape).background(Hifi.PanelHi), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, null, tint = Hifi.TextFaint, modifier = Modifier.size(48.dp))
                if (u.image != null) NetImage(u.image, Modifier.fillMaxSize(), corner = 100.dp)
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text("MI LAST.FM", style = Mono.copy(color = Color.White.copy(alpha = 0.75f)))
                Text(u.name, style = Display.copy(fontSize = if (wide) 40.sp else 30.sp, color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(u.realName, u.country, u.registered?.let { "scrobbleando desde ${SimpleDateFormat("MMMM 'de' yyyy", es).format(Date(it * 1000))}" })
                        .joinToString(" · "),
                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f),
                )
            }
            HeroButton(Icons.Default.Refresh, "Actualizar", onRefresh)
            HeroButton(Icons.AutoMirrored.Filled.OpenInNew, "Abrir en last.fm", onOpen)
            HeroButton(Icons.Default.Settings, "Cambiar usuario", onEdit)
        }
    }
}

@Composable
private fun HeroButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.padding(start = 6.dp).size(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.3f)).bouncyClick(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Stats(d: LfDashboard, wide: Boolean) {
    val u = d.user
    val days = u.registered?.let { ((System.currentTimeMillis() / 1000 - it) / 86400).coerceAtLeast(1) }
    FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Scrobbles", fmt(u.scrobbles), big = true)
        StatTile("Artistas", fmt(u.artists))
        StatTile("Álbumes", fmt(u.albums))
        StatTile("Canciones", fmt(u.tracks))
        StatTile("Te encantan", fmt(d.lovedTotal))
        days?.let { StatTile("Al día (promedio)", fmt(u.scrobbles / it)) }
        StatTile("Últimos 30 días", fmt(d.stats.total30) + if (d.stats.partial) "+" else "")
    }
}

@Composable
private fun NowPlayingCard(s: LfScrobble, onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "np").animateFloat(0.4f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "p")
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().glassCard().bouncyClick(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetImage(s.image, Modifier.size(56.dp), corner = 8.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GraphicEq, null, tint = LastFmRed, modifier = Modifier.size(14.dp).graphicsLayer { alpha = pulse })
                Spacer(Modifier.width(6.dp))
                Text("SCROBBLEANDO AHORA", style = Mono.copy(color = LastFmRed))
            }
            Text(s.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(s.artist, s.album).joinToString(" · "), fontSize = 13.sp, color = Hifi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ------------------------------------------------------------------ gráficas

@Composable
private fun ListeningCharts(d: LfDashboard, wide: Boolean) {
    val st = d.stats
    val cal = Calendar.getInstance()
    val dayLabels = (0 until 30).map { i ->
        cal.timeInMillis = System.currentTimeMillis() - (29 - i) * 86_400_000L
        SimpleDateFormat("EEE d MMM", es).format(cal.time)
    }
    val dayNums = (0 until 30).map { i -> cal.timeInMillis = System.currentTimeMillis() - (29 - i) * 86_400_000L; cal.get(Calendar.DAY_OF_MONTH).toString() }
    val best = st.daily.indices.maxByOrNull { st.daily[it] }
    val peakHour = st.hourly.indices.maxByOrNull { st.hourly[it] }
    val weekdays = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().glassCard().padding(18.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("ÚLTIMOS 30 DÍAS", style = Mono.copy(color = Hifi.TextDim))
                Text("${fmt(st.total30)}${if (st.partial) "+" else ""} scrobbles", style = Display.copy(fontSize = 24.sp))
            }
            if (best != null && st.daily[best] > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("TU MEJOR DÍA", style = Mono.copy(color = Hifi.TextDim))
                    Text("${dayLabels[best]} · ${st.daily[best]}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        BarChart(st.daily, dayLabels, "scrobbles", height = 170.dp, ticks = setOf(0, 7, 14, 21, 29), tickLabel = { dayNums[it] }, highlight = 29)
    }

    val clock = @Composable { m: Modifier ->
        Column(m.glassCard().padding(18.dp)) {
            Text("TU RELOJ MUSICAL", style = Mono.copy(color = Hifi.TextDim))
            Text(peakHour?.takeIf { st.hourly[it] > 0 }?.let { "Escuchas más a las ${"%02d".format(it)}:00" } ?: "Sin datos", style = Display.copy(fontSize = 18.sp))
            Spacer(Modifier.height(10.dp))
            BarChart(st.hourly, (0..23).map { "%02d:00".format(it) }, "scrobbles", height = 130.dp, ticks = setOf(0, 6, 12, 18, 23), tickLabel = { "${it}h" }, highlight = peakHour)
        }
    }
    val week = @Composable { m: Modifier ->
        val top = st.weekday.indices.maxByOrNull { st.weekday[it] }
        Column(m.glassCard().padding(18.dp)) {
            Text("DÍAS DE LA SEMANA", style = Mono.copy(color = Hifi.TextDim))
            Text(top?.takeIf { st.weekday[it] > 0 }?.let { "Tu día: ${listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")[it]}" } ?: "Sin datos", style = Display.copy(fontSize = 18.sp))
            Spacer(Modifier.height(10.dp))
            BarChart(st.weekday, weekdays, "scrobbles", height = 130.dp, ticks = (0..6).toSet(), highlight = top)
        }
    }
    if (wide) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            clock(Modifier.weight(1.4f)); week(Modifier.weight(1f))
        }
    } else {
        clock(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth())
        week(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth())
    }
}

// ------------------------------------------------------------------ tops

@Composable
private fun Tops(t: LfTops, period: LfPeriod, wide: Boolean, nav: NavController) {
    val c = LocalApp.current
    val scope = rememberCoroutineScope()
    if (t.artists.isEmpty() && t.albums.isEmpty() && t.tracks.isEmpty()) {
        Text("Sin scrobbles en este periodo.", color = Hifi.TextDim, modifier = Modifier.padding(20.dp))
        return
    }
    // Álbumes: portadas grandes con su lugar y reproducciones.
    if (t.albums.isNotEmpty()) {
        Text("Álbumes", style = Display.copy(fontSize = 17.sp), modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp))
        ArrowRow {
            itemsIndexed(t.albums, key = { i, a -> "al$i${a.name}" }) { i, a ->
                Column(Modifier.width(160.dp).bouncyClick { scope.openAlbum(c, nav, a.name, a.artist ?: "") }.padding(6.dp)) {
                    Box {
                        NetImage(a.image, Modifier.fillMaxWidth().aspectRatio(1f).shadow(12.dp, RoundedCornerShape(10.dp)))
                        Text(
                            "#${i + 1}", style = Mono.copy(color = Color.White, fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(a.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${a.artist ?: ""} · ${fmt(a.plays)}", fontSize = 11.sp, color = Hifi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    val artists = @Composable { m: Modifier ->
        Column(m) {
            Text("Artistas", style = Display.copy(fontSize = 17.sp), modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp))
            val max = t.artists.firstOrNull()?.plays?.coerceAtLeast(1) ?: 1
            t.artists.take(10).forEachIndexed { i, a -> RankRow(i + 1, a, max, round = true) { scope.openArtist(c, nav, a.name) } }
        }
    }
    val tracks = @Composable { m: Modifier ->
        Column(m) {
            Text("Canciones", style = Display.copy(fontSize = 17.sp), modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp))
            val max = t.tracks.firstOrNull()?.plays?.coerceAtLeast(1) ?: 1
            t.tracks.take(10).forEachIndexed { i, s -> RankRow(i + 1, s, max, round = false) { scope.openTrack(c, nav, s.name, s.artist ?: "") } }
        }
    }
    if (wide) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) { artists(Modifier.weight(1f)); tracks(Modifier.weight(1f)) }
    } else {
        artists(Modifier.padding(horizontal = 16.dp)); tracks(Modifier.padding(horizontal = 16.dp))
    }
    if (t.genres.isNotEmpty()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth().glassCard().padding(18.dp)) {
            Text("TUS GÉNEROS · ${period.label.uppercase()}", style = Mono.copy(color = Hifi.TextDim))
            Text(t.genres.first().first, style = Display.copy(fontSize = 22.sp))
            Spacer(Modifier.height(12.dp))
            ShareBar(t.genres)
            Text("Según las etiquetas de Last.fm de tus artistas más escuchados.", fontSize = 11.sp, color = Hifi.TextFaint, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun RankRow(rank: Int, item: LfItem, max: Long, round: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).bouncyClick(onClick = onClick).padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$rank", style = Mono.copy(color = Hifi.TextFaint), modifier = Modifier.width(24.dp))
        if (round) ArtistPhoto(item.name, Modifier.size(42.dp)) else NetImage(item.image, Modifier.size(42.dp), corner = 6.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.artist?.let { Text(it, fontSize = 12.sp, color = Hifi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            RankBar(item.plays.toFloat() / max, item.plays, "plays", Modifier.padding(top = 4.dp))
        }
    }
}

/** Foto del artista: Last.fm ya no las da; se usa la de tu biblioteca o la del catálogo. */
@Composable
private fun ArtistPhoto(name: String, modifier: Modifier) {
    val c = LocalApp.current
    val url by produceState<String?>(photoCache[name], name) {
        if (value == null) {
            value = runCatching {
                c.discover.libraryArtist(name)?.coverArt?.let { c.api.coverUrl(it, 200) }
                    ?: c.discover.searchArtists(name).firstOrNull { normName(it.name) == normName(name) }?.picture_medium
            }.getOrNull()?.also { photoCache[name] = it }
        }
    }
    NetImage(url, modifier, corner = 100.dp)
}

private val photoCache = HashMap<String, String>()

@Composable
private fun ScrobbleRow(s: LfScrobble, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().bouncyClick(onClick = onClick).padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetImage(s.image, Modifier.size(44.dp), corner = 6.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(s.artist, s.album).joinToString(" · "), fontSize = 12.sp, color = Hifi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (s.loved) Icon(Icons.Default.Favorite, "Te encanta", tint = LastFmRed, modifier = Modifier.padding(horizontal = 8.dp).size(14.dp))
        Text(ago(s.time), style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp))
    }
}

private fun ago(t: Long?): String {
    t ?: return "ahora"
    val s = System.currentTimeMillis() / 1000 - t
    return when {
        s < 60 -> "hace un momento"
        s < 3600 -> "hace ${s / 60} min"
        s < 86400 -> "hace ${s / 3600} h"
        s < 2 * 86400 -> "ayer"
        s < 7 * 86400 -> "hace ${s / 86400} días"
        else -> SimpleDateFormat("d MMM yyyy", es).format(Date(t * 1000))
    }
}

// ------------------------------------------------------------------ abrir en tu biblioteca o en el catálogo

/** El artista en tu biblioteca si lo tienes; si no, su página del catálogo. */
private fun CoroutineScope.openArtist(c: AppGraph, nav: NavController, name: String) = launch {
    c.discover.libraryArtist(name)?.let { nav.openArtist(it.id); return@launch }
    runCatching { c.discover.searchArtists(name).firstOrNull() }.getOrNull()?.let { nav.openExternalArtist(it.id) }
        ?: run { c.connect.notice.value = "No encontré a $name" }
}

private fun CoroutineScope.openAlbum(c: AppGraph, nav: NavController, title: String, artist: String) = launch {
    c.discover.libraryAlbumId(artist, title)?.let { nav.openAlbum(it); return@launch }
    runCatching { c.discover.searchAlbums("$artist $title").firstOrNull() }.getOrNull()?.let { nav.openExternalAlbum(it.id) }
        ?: run { c.connect.notice.value = "No encontré “$title”" }
}

/** Si la canción está en tu biblioteca, suena; si no, abre al artista para conocerlo. */
private fun CoroutineScope.openTrack(c: AppGraph, nav: NavController, title: String, artist: String) = launch {
    val song = runCatching {
        c.api.search("$artist $title").song.firstOrNull { normName(it.title) == normName(title) && normName(it.artist ?: "") == normName(artist) }
            ?: c.api.search(title).song.firstOrNull { normName(it.title) == normName(title) && normName(it.artist ?: "") == normName(artist) }
    }.getOrNull()
    if (song != null) c.player.play(listOf(song))
    else {
        c.connect.notice.value = "“$title” no está en tu biblioteca"
        openArtist(c, nav, artist)
    }
}
