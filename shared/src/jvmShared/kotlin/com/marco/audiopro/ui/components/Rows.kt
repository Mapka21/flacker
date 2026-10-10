package com.marco.audiopro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marco.audiopro.core.LocalApp
import kotlinx.coroutines.launch

/**
 * Fila horizontal (estantes de álbumes, artistas…). En la PC, al pasar el mouse aparecen
 * flechas a los lados para avanzar por páginas, como en Spotify / Apple Music; en el celular
 * se desliza con el dedo.
 * @param arrowsOffset cuánto subir las flechas para centrarlas en las portadas (no en los textos).
 */
@Composable
fun ArrowRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp),
    spacing: Dp = 0.dp,
    arrowsOffset: Dp = (-18).dp,
    content: LazyListScope.() -> Unit,
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val desktop = LocalApp.current.isDesktop
    var hovered by remember { mutableStateOf(false) }
    Box(
        modifier.fillMaxWidth().then(
            if (!desktop) Modifier else Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        when (e.type) {
                            PointerEventType.Enter -> hovered = true
                            PointerEventType.Exit -> hovered = false
                        }
                    }
                }
            },
        ),
    ) {
        LazyRow(state = state, contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(spacing), content = content)
        if (desktop) {
            fun page(dir: Int) = scope.launch {
                val w = state.layoutInfo.viewportSize.width
                state.animateScrollBy(dir * w * 0.8f)
            }
            AnimatedVisibility(
                hovered && state.canScrollBackward, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart).offset(y = arrowsOffset).padding(start = 8.dp),
            ) { RowArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft) { page(-1) } }
            AnimatedVisibility(
                hovered && state.canScrollForward, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd).offset(y = arrowsOffset).padding(end = 8.dp),
            ) { RowArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight) { page(1) } }
        }
    }
}

@Composable
private fun RowArrow(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).shadow(12.dp, CircleShape).glass(CircleShape, Color.Black.copy(alpha = 0.72f)).bouncyClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
}
