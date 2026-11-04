package com.solfi.app.ui.widgets

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.solfi.app.ui.theme.LocalUi
import com.solfi.app.ui.theme.Palette

/** Big rounded primary button; taller in kid mode. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    content: Color = Color.White,
    leading: String? = null,
) {
    val kid = LocalUi.current.kid
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = if (kid) 62.dp else 52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = content),
        shape = RoundedCornerShape(if (kid) 24.dp else 14.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (kid) 4.dp else 0.dp),
    ) {
        if (leading != null) { Text(leading, fontSize = 20.sp); Spacer(Modifier.width(8.dp)) }
        Text(text, fontSize = if (kid) 20.sp else 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val kid = LocalUi.current.kid
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = if (kid) 56.dp else 48.dp),
        shape = RoundedCornerShape(if (kid) 22.dp else 14.dp), border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { Text(text, fontSize = if (kid) 18.sp else 16.sp, fontWeight = FontWeight.SemiBold) }
}

/** Card that squishes slightly on press. */
@Composable
fun PressCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    container: Color = MaterialTheme.colorScheme.surface,
    border: BorderStroke? = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
    description: String? = null,
    content: @Composable () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "press")
    val m = modifier.scale(s).let { if (description != null) it.semantics { contentDescription = description } else it }
    if (onClick != null) {
        Card(onClick = onClick, modifier = m, colors = CardDefaults.cardColors(containerColor = container), border = border, interactionSource = src, content = { content() })
    } else {
        Card(modifier = m, colors = CardDefaults.cardColors(containerColor = container), border = border, content = { content() })
    }
}

@Composable
fun StatChip(emoji: String, value: String, label: String, tint: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(tint.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(emoji, fontSize = 22.sp)
        Column {
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AvatarCircle(emoji: String, size: androidx.compose.ui.unit.Dp = 44.dp, selected: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = (size.value * 0.55f).sp) }
}

@Composable
fun Stars(count: Int, size: androidx.compose.ui.unit.Dp = 16.dp, modifier: Modifier = Modifier) {
    val desc = LocalUi.current.strings.cdStars(count)
    Row(modifier.semantics { contentDescription = desc }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..3) Text("★", fontSize = (size.value).sp, color = if (i <= count) Palette.Gold else MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Bottom feedback panel (correct / wrong) used by lesson steps. */
@Composable
fun FeedbackPanel(
    correct: Boolean,
    message: String,
    detail: String?,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (correct) Palette.Success else Palette.Danger
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(bg.copy(alpha = 0.97f)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(message, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        if (!detail.isNullOrBlank()) Text(detail, color = Color.White.copy(alpha = 0.95f), fontSize = 15.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(2.dp))
        BigButton(primaryLabel, onPrimary, color = Color.White, content = bg)
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle != null) Text(subtitle, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start)
    }
}

@Composable
fun RowScope.Weighted(content: @Composable () -> Unit) { Box(Modifier.weight(1f)) { content() } }

@Composable
fun Clickable(onClick: () -> Unit, modifier: Modifier = Modifier, role: Role = Role.Button, content: @Composable () -> Unit) {
    Box(modifier.clickable(role = role, onClick = onClick)) { content() }
}
