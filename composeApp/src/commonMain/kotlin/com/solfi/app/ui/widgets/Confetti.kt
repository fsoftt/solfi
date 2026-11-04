package com.melodia.app.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.runtime.withFrameNanos
import com.melodia.app.ui.theme.Palette
import kotlin.math.sin
import kotlin.random.Random

private class Piece(val x: Float, val vx: Float, val vy: Float, val size: Float, val color: Color, val rot: Float, val rotSpeed: Float, val delay: Float)

/** Falling confetti celebration. Draws for [durationMs] after (re)composition when [play] is true. */
@Composable
fun Confetti(modifier: Modifier = Modifier, play: Boolean = true, pieces: Int = 90, durationMs: Int = 3200) {
    if (!play) return
    val colors = remember { listOf(Palette.Violet, Palette.Teal, Palette.Coral, Palette.Sun, Palette.Sky, Palette.Mint, Palette.Pink) }
    val list = remember {
        val r = Random(42)
        List(pieces) {
            Piece(r.nextFloat(), (r.nextFloat() - 0.5f) * 0.25f, 0.35f + r.nextFloat() * 0.5f, 6f + r.nextFloat() * 8f,
                colors[r.nextInt(colors.size)], r.nextFloat() * 360f, (r.nextFloat() - 0.5f) * 540f, r.nextFloat() * 0.6f)
        }
    }
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (elapsed < durationMs) {
            withFrameNanos { elapsed = (it - start) / 1_000_000 }
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val t = elapsed / 1000f
        for (p in list) {
            val tt = t - p.delay
            if (tt < 0) continue
            val x = (p.x + p.vx * tt + 0.02f * sin(tt * 4f + p.rot)) * size.width
            val y = (-0.1f + p.vy * tt) * size.height * 1.0f
            if (y > size.height) continue
            val alpha = (1f - (elapsed / durationMs.toFloat() - 0.6f).coerceAtLeast(0f) / 0.4f).coerceIn(0f, 1f)
            rotate(p.rot + p.rotSpeed * tt, Offset(x, y)) {
                drawRect(p.color.copy(alpha = alpha), Offset(x - p.size / 2, y - p.size / 3), Size(p.size, p.size * 0.6f))
            }
        }
    }
}
