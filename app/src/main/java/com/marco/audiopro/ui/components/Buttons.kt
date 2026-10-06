package com.marco.audiopro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.audiopro.ui.theme.Hifi

@Composable
fun PlayPill(onClick: () -> Unit, label: String = "Reproducir") {
    Row(
        Modifier.height(46.dp).clip(CircleShape).background(Hifi.Accent).clickable(onClick = onClick).padding(start = 16.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.PlayArrow, null, tint = Color.Black)
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun ShufflePill(onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).border(1.dp, Hifi.Line, CircleShape).background(Hifi.Panel).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Default.Shuffle, "Aleatorio", tint = Hifi.Cream) }
}

/** Fila estándar: reproducir, aleatorio y un slot extra a la derecha (descarga, favorito…). */
@Composable
fun PlayActions(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    extra: @Composable () -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PlayPill(onPlay)
        ShufflePill(onShuffle)
        Spacer(Modifier.weight(1f))
        extra()
    }
}
