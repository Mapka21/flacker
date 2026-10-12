package com.marco.audiopro.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.audiopro.core.LocalApp
import com.marco.audiopro.ui.theme.Hifi

/**
 * Menú contextual de la app. En la PC es compacto como los de macOS / Windows 11 (filas de 32 dp,
 * texto de 13 sp, resaltado al pasar el mouse); en el celular, filas grandes para el dedo.
 */
@Composable
fun AppMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    val desktop = LocalApp.current.isDesktop
    DropdownMenu(
        expanded, onDismiss, offset = offset,
        modifier = Modifier.widthIn(min = if (desktop) 228.dp else 220.dp).padding(horizontal = if (desktop) 5.dp else 0.dp),
        containerColor = Color(0xFF1B1C22).copy(alpha = 0.985f),
        shape = RoundedCornerShape(if (desktop) 11.dp else 16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
        shadowElevation = 18.dp,
        tonalElevation = 0.dp,
        content = content,
    )
}

/**
 * Opción del menú. @param shortcut atajo de teclado a la derecha (sólo PC).
 * @param destructive en rojo (borrar, quitar).
 */
@Composable
fun AppMenuItem(
    text: String,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    shortcut: String? = null,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val desktop = LocalApp.current.isDesktop
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val bg by animateColorAsState(
        when {
            pressed -> Color.White.copy(alpha = 0.14f)
            hovered -> Color.White.copy(alpha = 0.085f)
            else -> Color.Transparent
        },
        tween(90), label = "menuItem",
    )
    val fg = if (destructive) Hifi.LedRed else Hifi.Text
    Row(
        Modifier.fillMaxWidth().height(if (desktop) 32.dp else 48.dp)
            .clip(RoundedCornerShape(if (desktop) 6.dp else 10.dp))
            .background(bg)
            .hoverable(source)
            .clickable(source, indication = null, onClick = onClick)
            .padding(horizontal = if (desktop) 10.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon, null, tint = iconTint ?: if (destructive) Hifi.LedRed else if (hovered) Hifi.Text else Hifi.TextDim,
                modifier = Modifier.size(if (desktop) 16.dp else 22.dp),
            )
            Spacer(Modifier.width(if (desktop) 11.dp else 16.dp))
        }
        Text(
            text, color = fg, fontSize = if (desktop) 13.sp else 15.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (shortcut != null && desktop) {
            Spacer(Modifier.width(16.dp))
            Text(shortcut, color = Hifi.TextFaint, fontSize = 12.sp)
        }
    }
}

/** Línea finita entre grupos de opciones. */
@Composable
fun AppMenuDivider() {
    val desktop = LocalApp.current.isDesktop
    Box(
        Modifier.padding(horizontal = if (desktop) 8.dp else 14.dp, vertical = if (desktop) 4.dp else 6.dp)
            .fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)),
    )
}
