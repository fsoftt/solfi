package com.solfi.app.ui.notation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.solfi.app.music.Clef
import com.solfi.app.music.Melody
import com.solfi.app.music.MelodyEvent
import com.solfi.app.music.NoteValue
import com.solfi.app.music.Pitch
import kotlin.math.max
import kotlin.math.min

data class StaffColors(val ink: Color, val staff: Color, val highlight: Color, val label: Color, val paper: Color = Color.White)

@Composable
fun defaultStaffColors(): StaffColors {
    val c = MaterialTheme.colorScheme
    return StaffColors(ink = c.onSurface, staff = c.onSurface.copy(alpha = 0.55f), highlight = c.primary, label = c.onSurfaceVariant, paper = c.surface)
}

/**
 * Staff renderer drawn on a Compose Canvas.
 * [labelFor] gives the text under each note (null hides labels); [highlightIndex] is the active event index.
 */
@Composable
fun StaffView(
    melody: Melody,
    modifier: Modifier = Modifier,
    labelFor: ((Pitch) -> String)? = null,
    highlightIndex: Int = -1,
    onNoteClick: ((MelodyEvent) -> Unit)? = null,
    showTime: Boolean = true,
    gap: Dp = 11.dp,
    colors: StaffColors = defaultStaffColors(),
    description: String = "",
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val gapPx = with(density) { gap.toPx() }
        val layout = remember(melody, widthPx, gapPx, showTime, labelFor != null) {
            StaffLayout(melody, widthPx, gapPx, showTime, labelFor != null)
        }
        val scale = remember { Animatable(1f) }
        LaunchedEffect(highlightIndex) {
            if (highlightIndex >= 0) {
                scale.snapTo(0f)
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            }
        }
        val pulse = scale.value
        val h = with(density) { layout.totalHeight.toDp() }
        val tap = if (onNoteClick == null) Modifier else Modifier.pointerInput(layout) {
            detectTapGestures { pos -> layout.hitTest(pos.x, pos.y)?.let(onNoteClick) }
        }
        Canvas(Modifier.fillMaxWidth().height(h).then(tap).semantics { if (description.isNotEmpty()) contentDescription = description }) {
            drawStaff(layout, colors, measurer, labelFor, highlightIndex, pulse)
        }
    }
}

private fun DrawScope.drawStaff(
    L: StaffLayout, colors: StaffColors, tm: TextMeasurer, labelFor: ((Pitch) -> String)?, hi: Int, pulse: Float,
) {
    val g = L.gap
    val melody = L.melody
    val lineW = max(1f, g * 0.09f)
    for ((si, s) in L.systems.withIndex()) {
        val top = s.staffTop
        val bottom = top + 4 * g
        val endX = s.endX
        for (i in 0..4) drawLine(colors.staff, Offset(0f, top + i * g), Offset(endX, top + i * g), lineW)
        drawLine(colors.staff, Offset(0f, top), Offset(0f, bottom), lineW)
        // clef
        var x = 0.5f * g
        if (melody.clef == Clef.TREBLE) drawTrebleClef(x, bottom, g, colors.ink) else drawBassClef(x, bottom, g, colors.ink)
        x += 3.1f * g
        // key signature
        val fifths = melody.key.fifths
        val order = melody.key.accidentals()
        val posTable = if (fifths >= 0) intArrayOf(8, 5, 9, 6, 3, 7, 4) else intArrayOf(4, 7, 3, 6, 2, 5, 1)
        order.forEachIndexed { i, _ ->
            val pos = posTable[i] - if (melody.clef == Clef.BASS) 2 else 0
            val y = bottom - pos * g / 2f
            if (fifths >= 0) drawSharp(x + i * g * 1.0f, y, g, colors.ink) else drawFlat(x + i * g * 1.0f, y, g, colors.ink)
        }
        x += order.size * g
        if (s.first && L.showTime) {
            val style = TextStyle(fontSize = (g * 2.0f).toSp(), fontWeight = FontWeight.Bold, color = colors.ink)
            val num = tm.measure(melody.time.beats.toString(), style)
            val den = tm.measure(melody.time.unit.toString(), style)
            val cx = x + 1.2f * g
            drawText(num, topLeft = Offset(cx - num.size.width / 2f, top + 1f * g - num.size.height / 2f))
            drawText(den, topLeft = Offset(cx - den.size.width / 2f, top + 3f * g - den.size.height / 2f))
        }

        for ((mi, m) in s.measures.withIndex()) {
            val lastMeasureOfPiece = s.isLastSystem && mi == s.measures.lastIndex
            if (lastMeasureOfPiece) {
                drawLine(colors.ink, Offset(m.endX - 0.45f * g, top), Offset(m.endX - 0.45f * g, bottom), lineW * 1.2f)
                drawLine(colors.ink, Offset(m.endX - 0.1f * g, top), Offset(m.endX - 0.1f * g, bottom), g * 0.3f)
            } else drawLine(colors.ink, Offset(m.endX, top), Offset(m.endX, bottom), lineW * 1.2f)

            // notes
            for (gl in m.glyphs) {
                val active = gl.event.index == hi
                val col = if (active) colors.highlight else colors.ink
                val sc = if (active) 1f + 0.3f * pulse else 1f
                if (gl.isRest) drawRest(gl, bottom, g, col, sc) else drawNote(gl, m, melody, bottom, g, col, sc, active, colors)
                if (labelFor != null && !gl.isRest) {
                    val p = gl.heads.last().pitch
                    val style = TextStyle(fontSize = (g * 0.95f).toSp().let { if (it.value < 10f) 10.sp else it }, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, color = if (active) colors.highlight else colors.label)
                    val r = tm.measure(labelFor(p), style)
                    drawText(r, topLeft = Offset(gl.x - r.size.width / 2f, bottom + 3.0f * g))
                }
            }
            // beams
            for (b in m.beams) drawBeam(b, bottom, g, if (b.glyphs.any { it.event.index == hi }) colors.highlight else colors.ink, hi, colors)
            // ties
            for (gl in m.glyphs) if (gl.event.tieNext) {
                val next = L.glyphByEvent[gl.event.index + 1] ?: continue
                if (next.second != si) continue
                drawTie(gl, next.first, bottom, g, colors.ink)
            }
        }
        // ties between measures of same system handled above only when next in same system and same measure loop;
        // cross-measure ties: the next glyph is looked up globally, so they are covered.
    }
}

private fun posY(bottom: Float, g: Float, pos: Int) = bottom - pos * g / 2f

private fun DrawScope.drawNote(gl: NoteGlyph, m: MeasureLayout, melody: Melody, bottom: Float, g: Float, color: Color, sc: Float, active: Boolean, colors: StaffColors) {
    val headW = 1.35f * g
    val headH = 1.0f * g
    val e = gl.event
    val hollow = e.dur.value == NoteValue.HALF || e.dur.value == NoteValue.WHOLE
    val whole = e.dur.value == NoteValue.WHOLE
    val lineW = max(1f, g * 0.09f)
    // ledger lines
    val low = gl.lowPos
    val high = gl.highPos
    if (low <= -2) { var p = -2; while (p >= low) { val y = posY(bottom, g, p); drawLine(colors.staff, Offset(gl.x - headW * 0.95f, y), Offset(gl.x + headW * 0.95f, y), lineW * 1.2f); p -= 2 } }
    if (high >= 10) { var p = 10; while (p <= high) { val y = posY(bottom, g, p); drawLine(colors.staff, Offset(gl.x - headW * 0.95f, y), Offset(gl.x + headW * 0.95f, y), lineW * 1.2f); p += 2 } }

    if (active) {
        val cy = posY(bottom, g, (low + high) / 2)
        drawCircle(color.copy(alpha = 0.16f), radius = 1.7f * g * sc, center = Offset(gl.x, cy))
    }
    for (h in gl.heads) {
        val cx = gl.x + h.side * headW * 0.92f * (if (gl.stemUp) 1 else -1)
        val cy = posY(bottom, g, h.pos)
        val w = headW * sc
        val hh = headH * sc
        if (whole) {
            drawOval(color, Offset(cx - w / 2, cy - hh / 2), Size(w, hh))
            rotate(-40f, Offset(cx, cy)) { drawOval(colors.paper, Offset(cx - w * 0.17f, cy - hh * 0.34f), Size(w * 0.34f, hh * 0.68f)) }
        } else if (hollow) {
            rotate(-22f, Offset(cx, cy)) {
                drawOval(color, Offset(cx - w / 2, cy - hh / 2), Size(w, hh), style = Stroke(g * 0.24f))
                drawOval(color, Offset(cx - w * 0.27f, cy - hh * 0.12f), Size(w * 0.54f, hh * 0.24f))
            }
        } else {
            rotate(-22f, Offset(cx, cy)) { drawOval(color, Offset(cx - w / 2, cy - hh / 2), Size(w, hh)) }
        }
        // accidental
        h.accidental?.let { a ->
            val ax = gl.x - headW * 1.15f - g * 0.5f
            when {
                a > 0 -> drawSharp(ax, cy, g, color)
                a < 0 -> drawFlat(ax, cy, g, color)
                else -> drawNatural(ax, cy, g, color)
            }
        }
        // dots
        for (d in 0 until e.dur.dots) {
            val dy = if (h.pos % 2 == 0) cy - g * 0.5f else cy
            drawCircle(color, g * 0.17f, Offset(gl.x + headW * 0.9f + g * 0.45f + d * g * 0.45f + (if (h.side == 1 && gl.stemUp) headW * 0.9f else 0f), dy))
        }
    }
    if (whole) return
    // stem
    val stemW = max(1.2f, g * 0.12f)
    val lowY = posY(bottom, g, low)
    val highY = posY(bottom, g, high)
    val mid = posY(bottom, g, 4)
    val beamed = gl.beamId >= 0
    val stemX = if (gl.stemUp) gl.x + headW * 0.5f - stemW / 2 else gl.x - headW * 0.5f + stemW / 2
    var endY = if (gl.stemUp) min(highY - 3.4f * g, mid) else max(lowY + 3.4f * g, mid)
    if (beamed) endY = beamLevel(m, gl, bottom, g)
    if (gl.stemUp) drawLine(color, Offset(stemX, lowY), Offset(stemX, endY), stemW) else drawLine(color, Offset(stemX, highY), Offset(stemX, endY), stemW)
    // flags
    if (!beamed) {
        val flags = when (e.dur.value) { NoteValue.EIGHTH -> 1; NoteValue.SIXTEENTH -> 2; else -> 0 }
        for (f in 0 until flags) {
            val dir = if (gl.stemUp) 1f else -1f
            val y0 = endY + dir * f * g * 0.95f
            val path = Path().apply {
                moveTo(stemX, y0)
                cubicTo(stemX + g * 0.1f, y0 + dir * g * 1.0f, stemX + g * 1.5f, y0 + dir * g * 1.3f, stemX + g * 0.95f, y0 + dir * g * 2.6f)
            }
            drawPath(path, color, style = Stroke(g * 0.3f, cap = StrokeCap.Round))
        }
    }
}

private fun beamLevel(m: MeasureLayout, gl: NoteGlyph, bottom: Float, g: Float): Float {
    val b = m.beams.first { it.id == gl.beamId }
    val mid = posY(bottom, g, 4)
    return if (b.stemUp) min(b.glyphs.minOf { posY(bottom, g, it.highPos) - 3.4f * g }, mid) else max(b.glyphs.maxOf { posY(bottom, g, it.lowPos) + 3.4f * g }, mid)
}

private fun DrawScope.drawBeam(b: BeamGroup, bottom: Float, g: Float, color: Color, hi: Int, colors: StaffColors) {
    val headW = 1.35f * g
    val stemW = max(1.2f, g * 0.12f)
    val level = b.glyphs.let { gs ->
        val mid = posY(bottom, g, 4)
        if (b.stemUp) min(gs.minOf { posY(bottom, g, it.highPos) - 3.4f * g }, mid) else max(gs.maxOf { posY(bottom, g, it.lowPos) + 3.4f * g }, mid)
    }
    fun sx(gl: NoteGlyph) = if (b.stemUp) gl.x + headW * 0.5f - stemW / 2 else gl.x - headW * 0.5f + stemW / 2
    val th = g * 0.5f
    val dir = if (b.stemUp) 1f else -1f // beams stack toward the noteheads
    fun bar(x1: Float, x2: Float, off: Float) {
        val yA = level + dir * off
        val yB = yA + dir * th
        val p = Path().apply { moveTo(x1, yA); lineTo(x2, yA); lineTo(x2, yB); lineTo(x1, yB); close() }
        drawPath(p, color)
    }
    bar(sx(b.glyphs.first()) - stemW / 2, sx(b.glyphs.last()) + stemW / 2, 0f)
    // secondary beams for sixteenths
    for (i in b.glyphs.indices) {
        val gi = b.glyphs[i]
        if (gi.event.dur.value != NoteValue.SIXTEENTH) continue
        val next = b.glyphs.getOrNull(i + 1)
        val prev = b.glyphs.getOrNull(i - 1)
        if (next != null && next.event.dur.value == NoteValue.SIXTEENTH) bar(sx(gi) - stemW / 2, sx(next) + stemW / 2, th * 1.6f)
        else if (prev == null || prev.event.dur.value != NoteValue.SIXTEENTH) {
            val x2 = if (next != null) sx(gi) + g * 0.9f else sx(gi) - g * 0.9f
            bar(min(sx(gi), x2), max(sx(gi), x2), th * 1.6f)
        }
    }
}

private fun DrawScope.drawTie(a: NoteGlyph, b: NoteGlyph, bottom: Float, g: Float, color: Color) {
    val up = !a.stemUp // tie on the side opposite the stem
    val pos = if (up) a.highPos else a.lowPos
    val y = posY(bottom, g, pos) + (if (up) -g * 0.65f else g * 0.65f)
    val x1 = a.x + g * 0.7f
    val x2 = b.x - g * 0.7f
    val cy = y + (if (up) -g * 0.9f else g * 0.9f)
    val path = Path().apply { moveTo(x1, y); quadraticTo((x1 + x2) / 2, cy, x2, y) }
    drawPath(path, color, style = Stroke(g * 0.2f, cap = StrokeCap.Round))
}

private fun DrawScope.drawRest(gl: NoteGlyph, bottom: Float, g: Float, color: Color, sc: Float) {
    val x = gl.x
    val e = gl.event
    val mid = posY(bottom, g, 4)
    val sw = g * 0.3f
    when (e.dur.value) {
        NoteValue.WHOLE -> drawRect(color, Offset(x - g * 0.65f, posY(bottom, g, 6)), Size(g * 1.3f, g * 0.5f))
        NoteValue.HALF -> drawRect(color, Offset(x - g * 0.65f, mid - g * 0.5f), Size(g * 1.3f, g * 0.5f))
        NoteValue.QUARTER -> {
            val p = Path().apply {
                moveTo(x - g * 0.25f, mid - g * 1.5f)
                lineTo(x + g * 0.35f, mid - g * 0.6f)
                lineTo(x - g * 0.3f, mid + g * 0.25f)
                lineTo(x + g * 0.3f, mid + g * 1.0f)
                cubicTo(x - g * 0.4f, mid + g * 0.8f, x - g * 0.5f, mid + g * 1.5f, x + g * 0.05f, mid + g * 1.7f)
            }
            drawPath(p, color, style = Stroke(sw, cap = StrokeCap.Round))
        }
        NoteValue.EIGHTH, NoteValue.SIXTEENTH -> {
            val n = if (e.dur.value == NoteValue.EIGHTH) 1 else 2
            for (i in 0 until n) {
                val oy = mid - g * 0.7f + i * g * 0.9f
                val ox = x - i * g * 0.25f
                drawCircle(color, g * 0.28f, Offset(ox - g * 0.3f, oy))
                drawPath(Path().apply { moveTo(ox - g * 0.3f, oy + g * 0.1f); cubicTo(ox, oy + g * 0.25f, ox + g * 0.2f, oy - g * 0.1f, ox + g * 0.35f, oy - g * 0.2f) }, color, style = Stroke(sw * 0.8f, cap = StrokeCap.Round))
            }
            drawLine(color, Offset(x + g * 0.15f - (n - 1) * g * 0.25f, mid - g * 0.95f), Offset(x - g * 0.25f - (n - 1) * g * 0.25f, mid + g * (0.6f + (n - 1) * 0.9f)), sw * 0.8f, StrokeCap.Round)
        }
    }
    for (d in 0 until e.dur.dots) drawCircle(color, g * 0.17f, Offset(x + g * 1.0f + d * g * 0.45f, mid - g * 0.5f))
}

private fun DrawScope.drawSharp(cx: Float, cy: Float, g: Float, color: Color) {
    val w = g * 0.11f
    drawLine(color, Offset(cx - g * 0.18f, cy - g * 1.1f), Offset(cx - g * 0.18f, cy + g * 1.2f), w)
    drawLine(color, Offset(cx + g * 0.18f, cy - g * 1.2f), Offset(cx + g * 0.18f, cy + g * 1.1f), w)
    drawLine(color, Offset(cx - g * 0.4f, cy + g * 0.15f), Offset(cx + g * 0.4f, cy - g * 0.15f), g * 0.3f)
    drawLine(color, Offset(cx - g * 0.4f, cy + g * 0.7f), Offset(cx + g * 0.4f, cy + g * 0.4f), g * 0.3f)
}

private fun DrawScope.drawFlat(cx: Float, cy: Float, g: Float, color: Color) {
    drawLine(color, Offset(cx - g * 0.2f, cy - g * 1.6f), Offset(cx - g * 0.2f, cy + g * 0.5f), g * 0.11f)
    val p = Path().apply {
        moveTo(cx - g * 0.2f, cy + g * 0.5f)
        cubicTo(cx + g * 0.6f, cy + g * 0.1f, cx + g * 0.7f, cy - g * 0.7f, cx - g * 0.2f, cy - g * 0.25f)
    }
    drawPath(p, color, style = Stroke(g * 0.2f, cap = StrokeCap.Round))
}

private fun DrawScope.drawNatural(cx: Float, cy: Float, g: Float, color: Color) {
    val w = g * 0.11f
    drawLine(color, Offset(cx - g * 0.2f, cy - g * 1.2f), Offset(cx - g * 0.2f, cy + g * 0.55f), w)
    drawLine(color, Offset(cx + g * 0.2f, cy - g * 0.55f), Offset(cx + g * 0.2f, cy + g * 1.2f), w)
    drawLine(color, Offset(cx - g * 0.2f, cy - g * 0.25f), Offset(cx + g * 0.2f, cy - g * 0.5f), g * 0.25f)
    drawLine(color, Offset(cx - g * 0.2f, cy + g * 0.5f), Offset(cx + g * 0.2f, cy + g * 0.25f), g * 0.25f)
}

/** Treble (G) clef drawn with bezier strokes. [bottom] is the y of the bottom staff line. */
fun DrawScope.drawTrebleClef(x: Float, bottom: Float, g: Float, color: Color) {
    val yG = bottom - g // the G line
    fun px(u: Float) = x + g * 0.7f + u * g
    fun py(v: Float) = yG + v * g
    val st = Stroke(g * 0.26f, cap = StrokeCap.Round)
    val body = Path().apply {
        moveTo(px(0.95f), py(-4.2f))
        cubicTo(px(1.5f), py(-4.2f), px(1.7f), py(-3.0f), px(1.0f), py(-1.6f))
        lineTo(px(0.8f), py(1.0f))
        lineTo(px(0.8f), py(2.3f))
        cubicTo(px(0.8f), py(3.2f), px(-0.1f), py(3.3f), px(-0.05f), py(2.6f))
    }
    drawPath(body, color, style = st)
    val loop = Path().apply {
        moveTo(px(0.85f), py(1.3f))
        cubicTo(px(-1.0f), py(1.5f), px(-1.1f), py(-1.4f), px(0.7f), py(-1.7f))
        cubicTo(px(2.2f), py(-1.8f), px(2.3f), py(0.5f), px(0.9f), py(0.5f))
        cubicTo(px(0.1f), py(0.5f), px(0.0f), py(-0.5f), px(0.6f), py(-0.7f))
    }
    drawPath(loop, color, style = st)
    drawCircle(color, g * 0.17f, Offset(px(0.0f), py(2.62f)))
}

fun DrawScope.drawBassClef(x: Float, bottom: Float, g: Float, color: Color) {
    val yF = bottom - 3 * g // F line
    fun px(u: Float) = x + g * 0.3f + u * g
    fun py(v: Float) = yF + v * g
    drawCircle(color, g * 0.48f, Offset(px(0.5f), py(0f)))
    val p = Path().apply {
        moveTo(px(0.5f), py(-0.45f))
        cubicTo(px(1.9f), py(-0.9f), px(2.5f), py(0.6f), px(1.8f), py(1.5f))
        cubicTo(px(1.2f), py(2.3f), px(0.4f), py(2.7f), px(-0.2f), py(3.0f))
    }
    drawPath(p, color, style = Stroke(g * 0.3f, cap = StrokeCap.Round))
    drawCircle(color, g * 0.19f, Offset(px(3.0f), py(-0.5f)))
    drawCircle(color, g * 0.19f, Offset(px(3.0f), py(0.5f)))
}
