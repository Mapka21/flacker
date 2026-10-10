package com.marco.audiopro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import java.text.NumberFormat

/**
 * Categóricos para fondo oscuro (validados: CVD ΔE ≥ 8.4, visión normal ≥ 19.3, contraste ≥ 3:1
 * sobre #15161C). Orden fijo; lo que pase de 5 va a "Otros" en gris.
 */
val ChartCategorical = listOf(Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500), Color(0xFFD55181))
val ChartOther = Color(0xFF6B6E78)

private val nf: NumberFormat = NumberFormat.getIntegerInstance()
fun fmt(n: Number): String = nf.format(n)

/**
 * Barras verticales de una sola serie (magnitud = un solo tono). Puntas redondeadas (4dp)
 * ancladas a la base, 2dp de separación, rejilla tenue, y tooltip al pasar el mouse o tocar.
 * @param labels etiqueta de cada barra (para el tooltip).
 * @param ticks índices que llevan etiqueta en el eje X.
 */
@Composable
fun BarChart(
    values: IntArray,
    labels: List<String>,
    unit: String,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    ticks: Set<Int> = emptySet(),
    tickLabel: (Int) -> String = { labels[it] },
    highlight: Int? = null,
) {
    val tm = rememberTextMeasurer()
    var hover by remember { mutableStateOf<Int?>(null) }
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val bar = Hifi.Accent
    val axisStyle = Mono.copy(color = Hifi.TextFaint, fontSize = 9.sp)
    val tipStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Canvas(
        modifier.fillMaxWidth().height(height)
            .pointerInput(values.size) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        val x = e.changes.firstOrNull()?.position?.x ?: continue
                        hover = when (e.type) {
                            PointerEventType.Exit -> null
                            else -> ((x / size.width) * values.size).toInt().coerceIn(0, values.size - 1)
                        }
                    }
                }
            }
            .pointerInput(values.size) { detectTapGestures { o -> hover = ((o.x / size.width) * values.size).toInt().coerceIn(0, values.size - 1) } },
    ) {
        val axisH = 16.dp.toPx()
        val top = 22.dp.toPx() // espacio para el tooltip
        val chartH = size.height - axisH - top
        val slot = size.width / values.size
        val gap = 2.dp.toPx()
        val r = 4.dp.toPx()
        // Rejilla tenue: base y mitad.
        drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, top + chartH), Offset(size.width, top + chartH), 1f)
        drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, top + chartH / 2), Offset(size.width, top + chartH / 2), 1f)
        values.forEachIndexed { i, v ->
            val h = chartH * v / max
            val x = i * slot + gap / 2
            val w = (slot - gap).coerceAtLeast(1f)
            val alpha = when {
                hover == i -> 1f
                hover != null -> 0.45f
                highlight == i -> 1f
                else -> 0.8f
            }
            if (h > 0f) {
                // Redondeado sólo arriba (la punta del dato); abajo queda recto sobre la base.
                val path = Path().apply {
                    addRoundRect(RoundRect(x, top + chartH - h, x + w, top + chartH, topLeftCornerRadius = CornerRadius(minOf(r, w / 2)), topRightCornerRadius = CornerRadius(minOf(r, w / 2))))
                }
                drawPath(path, bar.copy(alpha = alpha))
            }
            if (i in ticks) {
                val l = tm.measure(tickLabel(i), axisStyle)
                drawText(l, topLeft = Offset((i * slot + slot / 2 - l.size.width / 2f).coerceIn(0f, size.width - l.size.width), top + chartH + 4.dp.toPx()))
            }
        }
        hover?.let { i ->
            val text = "${labels[i]} · ${fmt(values[i])} $unit"
            val l = tm.measure(text, tipStyle)
            val pad = 8.dp.toPx()
            val w = l.size.width + pad * 2
            val cx = (i * slot + slot / 2 - w / 2).coerceIn(0f, size.width - w)
            drawRoundRect(Color(0xFF26272E), Offset(cx, 0f), Size(w, l.size.height + pad), CornerRadius(8.dp.toPx()))
            drawText(l, topLeft = Offset(cx + pad, pad / 2))
        }
    }
}

/** Fila de ranking con barra horizontal (magnitud, un solo tono) y el número escrito al lado. */
@Composable
fun RankBar(fraction: Float, value: Long, unit: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.08f))) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Hifi.Accent.copy(alpha = 0.85f)))
        }
        Spacer(Modifier.width(10.dp))
        Text("${fmt(value)} $unit", style = Mono.copy(color = Hifi.TextDim, fontSize = 10.sp))
    }
}

/**
 * Reparto en una barra apilada al 100 % (p. ej. géneros): colores categóricos en orden fijo,
 * 2dp de separación entre segmentos, y leyenda con porcentaje (la identidad nunca va sólo en
 * el color).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareBar(parts: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    val colors = parts.mapIndexed { i, (name, _) -> if (name == "Otros") ChartOther else ChartCategorical.getOrElse(i) { ChartOther } }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(6.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            parts.forEachIndexed { i, (_, f) ->
                if (f > 0.0) Box(Modifier.weight(f.toFloat()).fillMaxHeight().background(colors[i]))
            }
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            parts.forEachIndexed { i, (name, f) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(colors[i]))
                    Spacer(Modifier.width(6.dp))
                    Text(name, fontSize = 13.sp, color = Hifi.Text)
                    Spacer(Modifier.width(6.dp))
                    Text("${(f * 100).toInt()} %", style = Mono.copy(color = Hifi.TextDim, fontSize = 11.sp))
                }
            }
        }
    }
}

/** Número protagonista con su etiqueta (stat tile). */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, big: Boolean = false) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.07f)).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label.uppercase(), style = Mono.copy(color = Hifi.TextDim, fontSize = 9.sp))
        Text(value, style = com.marco.audiopro.ui.theme.Display.copy(fontSize = if (big) 32.sp else 22.sp, color = Hifi.Text))
    }
}
