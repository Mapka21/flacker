package com.marco.audiopro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SendToMobile
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.data.subsonic.Song
import com.marco.audiopro.share.Transfer
import com.marco.audiopro.ui.theme.Display
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import kotlinx.coroutines.delay

private fun deviceIcon(platform: String) = if (platform == "android") Icons.Default.PhoneAndroid else Icons.Default.Computer

private fun songsCount(n: Int) = if (n == 1) "1 canción" else "$n canciones"

@Composable
private fun ShareDialogCard(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth()
                .shadow(30.dp, RoundedCornerShape(26.dp))
                .glass(RoundedCornerShape(26.dp), Hifi.PanelHi.copy(alpha = 0.97f))
                .padding(20.dp),
        ) { content() }
    }
}

/** Botón blanco con letra negra (principal) o de vidrio (secundario). */
@Composable
private fun DialogButton(text: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(if (primary) Color.White else Hifi.Glass)
            .bouncyClick(onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (primary) Color.Black else Hifi.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun PeerRow(name: String, platform: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).bouncyClick(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Hifi.Glass), contentAlignment = Alignment.Center) {
            Icon(deviceIcon(platform), null, tint = Hifi.Text)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, fontSize = 12.sp, color = Hifi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.SendToMobile, null, tint = Hifi.TextDim, modifier = Modifier.size(18.dp))
    }
}

/**
 * "Mandar a…": elegir otro Flacker de la red (cualquier cuenta, con o sin servidor). El otro
 * acepta y le llegan los archivos originales.
 */
@Composable
fun SendToDialog(songs: List<Song>, onDismiss: () -> Unit) {
    val c = LocalApp.current
    val mine by c.connect.devices.collectAsState()
    val others by c.connect.nearby.collectAsState()
    val list = songs.filter { !it.isPreview && !it.isRadio }
    val size = list.sumOf { it.size ?: 0 }
    ShareDialogCard(onDismiss) {
        Text("Mandar a…", style = Display.copy(fontSize = 22.sp))
        Spacer(Modifier.height(4.dp))
        Text(
            songsCount(list.size) + (if (size > 0) " · ${formatBytes(size)}" else "") + " · archivos originales, por tu red",
            style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp),
        )
        Spacer(Modifier.height(14.dp))
        val all = (mine + others).distinctBy { it.id }.sortedBy { it.name }
        if (all.isEmpty()) {
            Text(
                "No veo otros Flacker en la red. Ábrelo en el otro celular o PC, en el mismo Wi-Fi." +
                    if (c.isDesktop) " Si Windows preguntó por el firewall, permite el acceso en redes privadas." else "",
                color = Hifi.TextDim, fontSize = 13.sp,
            )
        }
        all.forEach { d ->
            PeerRow(d.name, d.platform, if (d in others) "Tiene que aceptar allá" else if (d.paired) "Emparejado" else "Tu cuenta") {
                c.share.send(d, list)
                onDismiss()
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Se guardan allá en Música/Flacker.", fontSize = 11.sp, color = Hifi.TextFaint)
    }
}

/** Alguien te quiere mandar canciones: ver qué es y aceptar o no. */
@Composable
fun IncomingShareDialog() {
    val c = LocalApp.current
    val offer by c.share.incoming.collectAsState()
    val o = offer ?: return
    ShareDialogCard({ c.share.answer(o, false) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(Hifi.Glass), contentAlignment = Alignment.Center) {
                Icon(deviceIcon(o.platform), null, tint = Hifi.Text)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("${o.from} te quiere mandar", color = Hifi.TextDim, fontSize = 13.sp)
                Text(songsCount(o.files.size), style = Display.copy(fontSize = 22.sp))
            }
        }
        Spacer(Modifier.height(12.dp))
        o.files.take(5).forEach { f ->
            Text(
                "${f.title}${f.artist?.let { " · $it" } ?: ""}", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        if (o.files.size > 5) Text("y ${o.files.size - 5} más", fontSize = 12.sp, color = Hifi.TextDim)
        Spacer(Modifier.height(8.dp))
        val albums = o.files.mapNotNull { it.album }.distinct()
        Text(
            listOfNotNull(
                o.totalBytes.takeIf { it > 0 }?.let(::formatBytes),
                o.files.map { it.suffix.uppercase() }.distinct().joinToString("/").takeIf { it.isNotBlank() },
                albums.singleOrNull(),
            ).joinToString(" · ") + "\nSe guardan en ${c.share.inbox.label}",
            style = Mono.copy(color = Hifi.TextFaint, fontSize = 10.sp),
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogButton("Rechazar", false, Modifier.weight(1f)) { c.share.answer(o, false) }
            DialogButton("Aceptar", true, Modifier.weight(1f)) { c.share.answer(o, true) }
        }
    }
}

/** Otro Flacker pide emparejarse (para controlarse y pasarse la música sin la misma cuenta). */
@Composable
fun PairRequestDialog() {
    val c = LocalApp.current
    val req by c.connect.pairRequest.collectAsState()
    val r = req ?: return
    ShareDialogCard({ c.connect.answerPair(r, false) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Link, null, tint = Hifi.Text, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("${r.name} quiere emparejarse", style = Display.copy(fontSize = 20.sp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Podrán pasarse la música y controlarse entre los dos, aunque no tengan el mismo servidor. Tus ajustes y claves no se comparten.",
            fontSize = 13.sp, color = Hifi.TextDim,
        )
        Spacer(Modifier.height(16.dp))
        PairCode(r.code)
        Text(
            "Acepta sólo si en ${r.name} sale el mismo código.", fontSize = 12.sp, color = Hifi.TextDim,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogButton("Rechazar", false, Modifier.weight(1f)) { c.connect.answerPair(r, false) }
            DialogButton("Emparejar", true, Modifier.weight(1f)) { c.connect.answerPair(r, true) }
        }
    }
}

/** El código de emparejamiento, grande y separado: "4 8 2 1". */
@Composable
fun PairCode(code: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Hifi.Glass).padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
        Text(code.toList().joinToString("  "), style = Display.copy(fontSize = 34.sp, letterSpacing = 4.sp))
    }
}

/**
 * Envíos en curso ("Mandando a PC-MARCO · 3/12") con su barra y cancelar; al terminar se queda
 * unos segundos con el resultado.
 */
@Composable
fun TransfersPill(modifier: Modifier = Modifier) {
    val c = LocalApp.current
    val list by c.share.transfers.collectAsState()
    val t = list.firstOrNull { !it.finished } ?: list.lastOrNull()
    LaunchedEffect(t?.id, t?.finished) {
        if (t != null && t.finished) { delay(if ((t.message?.length ?: 0) > 50) 7000 else 4500); c.share.dismiss(t.id) }
    }
    AnimatedVisibility(t != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut(), modifier = modifier) {
        val x = t ?: return@AnimatedVisibility
        Column(
            Modifier.widthIn(min = 280.dp, max = 460.dp).shadow(16.dp, RoundedCornerShape(20.dp))
                .glass(RoundedCornerShape(20.dp), Hifi.PanelHi.copy(alpha = 0.96f)).padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SendToMobile, null, tint = Hifi.Text, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    val sent = x.total - x.skipped
                    Text(
                        when (x.state) {
                            Transfer.State.WAITING -> "Esperando a que ${x.peer} acepte…"
                            Transfer.State.ACTIVE -> (if (x.outgoing) "Mandando a ${x.peer}" else "Recibiendo de ${x.peer}") +
                                " · ${(x.done + x.failed + 1).coerceAtMost(sent)}/$sent"
                            else -> x.message ?: ""
                        },
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    if (x.state == Transfer.State.ACTIVE) x.current?.let { Text(it, fontSize = 11.sp, color = Hifi.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Icon(
                    Icons.Default.Close, if (x.finished) "Cerrar" else "Cancelar", tint = Hifi.TextDim,
                    modifier = Modifier.size(28.dp).clip(CircleShape)
                        .bouncyClick { if (x.finished) c.share.dismiss(x.id) else c.share.cancel(x.id) }.padding(5.dp),
                )
            }
            if (x.state == Transfer.State.ACTIVE) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { x.progress }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = Color.White, trackColor = Hifi.Line,
                )
            }
        }
    }
}
