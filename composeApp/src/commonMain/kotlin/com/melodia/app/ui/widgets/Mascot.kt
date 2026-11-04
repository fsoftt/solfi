package com.melodia.app.ui.widgets

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.melodia.app.ui.theme.LocalUi
import com.melodia.app.ui.theme.Palette

enum class Mood { HAPPY, CHEER, THINKING, SAD }

/**
 * Melodía's mascot: a smiling eighth-note with a face, drawn on a Canvas.
 * Bounces gently (more when [mood] is CHEER) and blinks.
 */
@Composable
fun Mascot(modifier: Modifier = Modifier, size: Dp = 120.dp, mood: Mood = Mood.HAPPY, body: Color = Palette.Violet) {
    val t = rememberInfiniteTransition(label = "mascot")
    val cheer = mood == Mood.CHEER
    val bounce by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (cheer) 450 else 1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bounce")
    val blink by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "blink")
    val desc = LocalUi.current.strings.cdMascot
    Canvas(modifier.size(size).semantics { contentDescription = desc }.graphicsLayer {
        translationY = -bounce * this.size.height * (if (cheer) 0.12f else 0.04f)
        rotationZ = (bounce - 0.5f) * (if (cheer) 10f else 4f)
        scaleY = 1f - (if (cheer) 0.04f else 0.02f) * (1f - bounce)
    }) {
        drawMascot(body, mood, blinking = blink > 0.965f)
    }
}

private fun DrawScope.drawMascot(body: Color, mood: Mood, blinking: Boolean) {
    val w = size.width
    val h = size.height
    val dark = Color(0xFF2B2250)
    // stem + flag (behind head)
    val stemX = w * 0.74f
    drawLine(body, Offset(stemX, h * 0.12f), Offset(stemX, h * 0.62f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
    val flag = Path().apply {
        moveTo(stemX, h * 0.12f)
        cubicTo(stemX + w * 0.05f, h * 0.30f, stemX + w * 0.28f, h * 0.32f, stemX + w * 0.20f, h * 0.52f)
    }
    drawPath(flag, body, style = Stroke(w * 0.08f, cap = StrokeCap.Round))
    // head
    val cx = w * 0.42f
    val cy = h * 0.66f
    val rx = w * 0.34f
    val ry = h * 0.28f
    drawOval(Color.Black.copy(alpha = 0.12f), Offset(cx - rx, cy - ry + h * 0.04f), Size(rx * 2, ry * 2))
    drawOval(body, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))
    drawOval(Color.White.copy(alpha = 0.22f), Offset(cx - rx * 0.7f, cy - ry * 0.78f), Size(rx * 0.7f, ry * 0.45f))
    // cheeks
    drawCircle(Palette.Pink.copy(alpha = 0.6f), w * 0.045f, Offset(cx - rx * 0.62f, cy + ry * 0.2f))
    drawCircle(Palette.Pink.copy(alpha = 0.6f), w * 0.045f, Offset(cx + rx * 0.62f, cy + ry * 0.2f))
    // eyes
    val ex = rx * 0.38f
    val ey = cy - ry * 0.15f
    val er = w * 0.055f
    for (s in listOf(-1f, 1f)) {
        val ox = cx + s * ex
        if (blinking || mood == Mood.CHEER) {
            val p = Path().apply { moveTo(ox - er, ey + er * 0.2f); quadraticTo(ox, ey - er * 1.1f, ox + er, ey + er * 0.2f) }
            drawPath(p, dark, style = Stroke(w * 0.03f, cap = StrokeCap.Round))
        } else {
            drawCircle(Color.White, er * 1.35f, Offset(ox, ey))
            drawCircle(dark, er * 0.85f, Offset(ox + (if (mood == Mood.THINKING) er * 0.4f else 0f), ey + (if (mood == Mood.THINKING) -er * 0.4f else 0f)))
            drawCircle(Color.White, er * 0.3f, Offset(ox - er * 0.2f, ey - er * 0.25f))
        }
    }
    // mouth
    val my = cy + ry * 0.38f
    val mouth = Path()
    when (mood) {
        Mood.SAD -> { mouth.moveTo(cx - rx * 0.22f, my + ry * 0.12f); mouth.quadraticTo(cx, my - ry * 0.12f, cx + rx * 0.22f, my + ry * 0.12f) }
        Mood.THINKING -> { mouth.moveTo(cx - rx * 0.12f, my); mouth.lineTo(cx + rx * 0.16f, my - ry * 0.04f) }
        else -> { mouth.moveTo(cx - rx * 0.28f, my - ry * 0.05f); mouth.quadraticTo(cx, my + ry * (if (mood == Mood.CHEER) 0.5f else 0.3f), cx + rx * 0.28f, my - ry * 0.05f) }
    }
    if (mood == Mood.CHEER) {
        mouth.close()
        drawPath(mouth, dark)
    } else drawPath(mouth, dark, style = Stroke(w * 0.03f, cap = StrokeCap.Round))
}
