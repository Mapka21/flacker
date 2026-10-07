package com.marco.audiopro.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono

/** Nombre de la app: FLAC + hacker. */
const val APP_NAME = "Flacker"

/**
 * Logo de texto: "FLAC" en blanco, "KER" en el acento y un cursor de terminal parpadeando.
 * @param tagline abajo, chiquito y espaciado; null para ocultarlo.
 */
@Composable
fun FlackerLogo(modifier: Modifier = Modifier, size: TextUnit = 22.sp, tagline: String? = "HI-FI · HACKER") {
    val blink = rememberInfiniteTransition(label = "cursor")
    val cursor by blink.animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes { durationMillis = 1100; 1f at 0 using LinearEasing; 1f at 549; 0f at 550; 0f at 1099 }),
        label = "c",
    )
    Column(modifier) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Hifi.Text)) { append("FLAC") }
                withStyle(SpanStyle(color = Hifi.Accent)) { append("KER") }
                withStyle(SpanStyle(color = Hifi.Accent.copy(alpha = cursor))) { append("_") }
            },
            style = Mono.copy(fontSize = size, fontWeight = FontWeight.Bold, letterSpacing = size * 0.22f),
        )
        if (tagline != null) {
            Text(tagline, style = Mono.copy(color = Hifi.TextDim, fontSize = size * 0.42f, letterSpacing = size * 0.2f))
        }
    }
}
