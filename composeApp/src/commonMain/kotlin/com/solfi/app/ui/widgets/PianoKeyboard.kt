package com.solfi.app.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import com.solfi.app.ui.theme.LocalBaseFontFamily
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.solfi.app.music.Pitch
import com.solfi.app.ui.theme.LocalUi

private val WHITE_PCS = setOf(0, 2, 4, 5, 7, 9, 11)

private fun isWhite(midi: Int) = ((midi % 12) + 12) % 12 in WHITE_PCS

/**
 * On-screen keyboard from [firstMidi] to [lastMidi] (both should be white keys).
 * Multitouch: every pointer plays the key under it and glides when dragged to another key.
 * If [keyWidth] is null the keys are fitted to the available width, otherwise the keyboard is
 * [keyWidth] per white key and scrolls with [scroll].
 */
@Composable
fun PianoKeyboard(
    firstMidi: Int,
    lastMidi: Int,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    keyWidth: Dp? = null,
    scroll: ScrollState? = null,
    showLabels: Boolean = true,
    highlight: Set<Int> = emptySet(),
    correct: Set<Int> = emptySet(),
    wrong: Set<Int> = emptySet(),
    onDown: (Pitch) -> Unit = {},
    onUp: (Pitch) -> Unit = {},
) {
    val ui = LocalUi.current
    val measurer = rememberTextMeasurer()
    val fontFamily = LocalBaseFontFamily.current
    val density = LocalDensity.current
    val whites = remember(firstMidi, lastMidi) { (firstMidi..lastMidi).filter(::isWhite) }
    val pressed: SnapshotStateMap<Int, Int> = remember { mutableStateMapOf() } // pointerId -> midi
    val colors = MaterialTheme.colorScheme
    val primary = colors.primary
    val desc = ui.strings.cdPianoKey

    @Composable
    fun Keys(totalW: Dp) {
        val widthPx = with(density) { totalW.toPx() }
        val heightPx = with(density) { height.toPx() }
        val wk = widthPx / whites.size
        val bw = wk * 0.62f
        val bh = heightPx * 0.62f

        fun midiAt(x: Float, y: Float): Int? {
            if (y < 0 || y > heightPx || x < 0 || x >= widthPx) return null
            if (y <= bh) {
                // check black keys around x
                val wi = (x / wk).toInt()
                for (i in listOf(wi, wi + 1)) {
                    if (i in 1 until whites.size) {
                        val m = whites[i] - 1
                        if (!isWhite(m)) {
                            val bx = i * wk
                            if (x >= bx - bw / 2 && x <= bx + bw / 2) return m
                        }
                    }
                }
            }
            return whites.getOrNull((x / wk).toInt())
        }

        Canvas(
            Modifier.width(totalW).height(height)
                .semantics { contentDescription = desc }
                .pointerInput(whites) {
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent()
                            for (c in ev.changes) {
                                val id = c.id.value.toInt()
                                val cur = pressed[id]
                                when {
                                    !c.pressed -> {
                                        if (cur != null) { pressed.remove(id); if (!pressed.containsValue(cur)) onUp(Pitch.fromMidi(cur)) }
                                        c.consume()
                                    }
                                    else -> {
                                        val m = midiAt(c.position.x, c.position.y)
                                        if (m != cur) {
                                            if (cur != null) { pressed.remove(id); if (!pressed.containsValue(cur)) onUp(Pitch.fromMidi(cur)) }
                                            if (m != null) { pressed[id] = m; onDown(Pitch.fromMidi(m)) }
                                        }
                                        c.consume()
                                    }
                                }
                            }
                        }
                    }
                },
        ) {
            val active = pressed.values.toSet()
            // white keys
            whites.forEachIndexed { i, m ->
                val x = i * wk
                val base = when {
                    m in wrong -> Color(0xFFFFB3B0)
                    m in correct -> Color(0xFFA8EBC4)
                    m in active -> primary.copy(alpha = 0.35f)
                    m in highlight -> Color(0xFFFFE08A)
                    else -> Color(0xFFFDFDFF)
                }
                drawRoundRect(base, Offset(x + 1.5f, 0f), Size(wk - 3f, heightPx), CornerRadius(8f, 8f))
                drawRoundRect(Color(0x33000000), Offset(x + 1.5f, 0f), Size(wk - 3f, heightPx), CornerRadius(8f, 8f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
                if (showLabels) {
                    val p = Pitch.fromMidi(m)
                    val label = ui.noteName(p) + if (p.step == 0) p.octave.toString() else ""
                    val r = measurer.measure(label, TextStyle(fontFamily = fontFamily, fontSize = (wk * 0.26f).coerceIn(9f, 17f).let { with(density) { it.toSp() } }, fontWeight = FontWeight.SemiBold, color = Color(0xFF4A4568)))
                    drawText(r, topLeft = Offset(x + (wk - r.size.width) / 2f, heightPx - r.size.height - 8f))
                }
            }
            // black keys
            whites.forEachIndexed { i, m ->
                val bm = m - 1
                if (i >= 1 && !isWhite(bm)) {
                    val x = i * wk - bw / 2
                    val color = when {
                        bm in wrong -> Color(0xFFE5534B)
                        bm in correct -> Color(0xFF2EBD6B)
                        bm in active -> primary
                        bm in highlight -> Color(0xFFE0A800)
                        else -> Color(0xFF26223A)
                    }
                    drawRoundRect(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.9f))), Offset(x, 0f), Size(bw, bh), CornerRadius(7f, 7f))
                    if (showLabels && bw > 26f) {
                        val p = Pitch.fromMidi(bm)
                        val r = measurer.measure(ui.noteName(p), TextStyle(fontFamily = fontFamily, fontSize = with(density) { (bw * 0.3f).coerceIn(8f, 12f).toSp() }, color = Color.White.copy(alpha = 0.85f)))
                        drawText(r, topLeft = Offset(x + (bw - r.size.width) / 2f, bh - r.size.height - 6f))
                    }
                }
            }
        }
    }

    if (keyWidth == null) {
        BoxWithConstraints(modifier.fillMaxWidth()) { Keys(maxWidth) }
    } else {
        val total = keyWidth * whites.size
        val sc = scroll ?: remember { ScrollState(0) }
        androidx.compose.foundation.layout.Box(modifier.fillMaxWidth().horizontalScroll(sc, enabled = false)) { Keys(total) }
    }
}
