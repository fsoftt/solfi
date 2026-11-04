package com.melodia.app.ui.notation

import com.melodia.app.music.Clef
import com.melodia.app.music.Melody
import com.melodia.app.music.MelodyEvent
import com.melodia.app.music.Pitch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** One notehead of a note/chord. [pos] is the staff position: 0 = bottom line, 1 = first space, ... 8 = top line. */
class HeadGlyph(val pitch: Pitch, val pos: Int, val side: Int, val accidental: Int?)

class NoteGlyph(
    val event: MelodyEvent,
    val x: Float,
    val heads: List<HeadGlyph>,
    val stemUp: Boolean,
    var beamId: Int,
) {
    val isRest get() = event.isRest
    val lowPos get() = heads.minOfOrNull { it.pos } ?: 4
    val highPos get() = heads.maxOfOrNull { it.pos } ?: 4
}

class BeamGroup(val id: Int, val glyphs: List<NoteGlyph>, val stemUp: Boolean)

class MeasureLayout(val startX: Float, val endX: Float, val glyphs: List<NoteGlyph>, val beams: List<BeamGroup>)

class SystemLayout(
    /** y of the top staff line */
    val staffTop: Float,
    val top: Float,
    val bottom: Float,
    val measures: List<MeasureLayout>,
    val headerEnd: Float,
    val endX: Float,
    val first: Boolean,
    val isLastSystem: Boolean,
)

/**
 * Pure geometry for a melody on a staff. All sizes derive from [gap], the distance between two staff lines.
 */
class StaffLayout(
    val melody: Melody,
    availableWidth: Float,
    private val desiredGap: Float,
    val showTime: Boolean = true,
    val showLabels: Boolean = true,
) {
    var gap: Float = desiredGap
        private set
    val systems = ArrayList<SystemLayout>()
    var totalHeight = 0f
        private set
    val glyphByEvent = HashMap<Int, Pair<NoteGlyph, Int>>() // event index -> glyph, system index

    val keyAccCount = kotlin.math.abs(melody.key.fifths)

    private class RawMeasure(val events: List<MelodyEvent>, val unitsPerEvent: List<Float>) {
        val natural: Float get() = LEFT_PAD + unitsPerEvent.sum() + RIGHT_PAD
    }

    init {
        build(availableWidth)
    }

    private fun eventUnits(e: MelodyEvent, accidental: Boolean): Float {
        val base = 1.7f + 2.1f * sqrt(e.dur.ticks / 24f)
        return base + if (accidental) 1.2f else 0f
    }

    private fun headerUnits(first: Boolean): Float = 3.6f + keyAccCount * 1.0f + (if (first && showTime) 3.1f else 0f) + 0.6f

    private fun build(width: Float) {
        // accidentals to show per event head (needs running state per measure)
        val raw = melody.measures.map { measure ->
            val shown = HashMap<Int, Int>() // diatonic index -> current alter within measure
            val units = measure.map { e ->
                var anyAcc = false
                e.pitches.forEach { p ->
                    val current = shown[p.diatonic] ?: melody.key.alterFor(p.step)
                    if (p.alter != current) { anyAcc = true }
                    shown[p.diatonic] = p.alter
                }
                eventUnits(e, anyAcc)
            }
            RawMeasure(measure, units)
        }
        val maxMeasure = raw.maxOf { it.natural }
        val needed = headerUnits(true) + maxMeasure
        gap = min(desiredGap, width / needed)
        if (raw.size >= 2) {
            // prefer two measures per line when that only needs a modest reduction of the staff size
            val two = width / (headerUnits(true) + 2 * maxMeasure)
            if (two >= desiredGap * 0.8f) gap = min(desiredGap, two)
        }

        // greedy wrap, in units
        val availUnits = width / gap
        val rows = ArrayList<MutableList<Int>>()
        var cur = ArrayList<Int>()
        var used = headerUnits(true)
        raw.forEachIndexed { i, m ->
            if (cur.isNotEmpty() && used + m.natural > availUnits + 0.001f) {
                rows += cur; cur = ArrayList(); used = headerUnits(false)
            }
            if (cur.isEmpty()) used = if (rows.isEmpty()) headerUnits(true) else headerUnits(false)
            cur += i; used += m.natural
        }
        if (cur.isNotEmpty()) rows += cur

        val g = gap
        val topPad = 4.2f * g
        val staffH = 4f * g
        val below = 3.0f * g + (if (showLabels) 1.7f * g else 0f)
        val sysH = topPad + staffH + below
        var y = 0f
        rows.forEachIndexed { si, idxs ->
            val first = si == 0
            val last = si == rows.lastIndex
            val hEnd = headerUnits(first) * g
            val naturalUnits = idxs.sumOf { raw[it].natural.toDouble() }.toFloat()
            val room = availUnits - headerUnits(first)
            val stretch = if (last) min(room / naturalUnits, 1.35f) else room / naturalUnits
            var x = hEnd
            val measures = ArrayList<MeasureLayout>()
            for (mi in idxs) {
                val rm = raw[mi]
                val mStart = x
                var ex = x + LEFT_PAD * g * stretch
                val glyphs = ArrayList<NoteGlyph>()
                val shown = HashMap<Int, Int>()
                rm.events.forEachIndexed { k, e ->
                    val w = rm.unitsPerEvent[k] * g * stretch
                    val cx = ex + w * 0.5f + (if (rm.unitsPerEvent[k] > eventUnits(e, false) + 0.1f) 0.5f * g else 0f)
                    glyphs += makeGlyph(e, cx, shown)
                    ex += w
                }
                x = mStart + rm.natural * g * stretch
                val beams = makeBeams(rm.events, glyphs)
                measures += MeasureLayout(mStart, x, glyphs, beams)
                glyphs.forEach { glyphByEvent[it.event.index] = it to si }
            }
            systems += SystemLayout(y + topPad, y, y + sysH, measures, hEnd, x, first, last)
            y += sysH
        }
        totalHeight = y
    }

    private fun makeGlyph(e: MelodyEvent, cx: Float, shown: MutableMap<Int, Int>): NoteGlyph {
        val bottom = melody.clef.bottomLineDiatonic
        val sorted = e.pitches.sortedBy { it.diatonic }
        val heads = ArrayList<HeadGlyph>()
        var prevPos = -100
        var prevSide = 0
        for (p in sorted) {
            val pos = p.diatonic - bottom
            val current = shown[p.diatonic] ?: melody.key.alterFor(p.step)
            val acc = if (p.alter != current) p.alter else null
            shown[p.diatonic] = p.alter
            val side = if (pos - prevPos == 1 && prevSide == 0) 1 else 0
            heads += HeadGlyph(p, pos, side, acc)
            prevPos = pos; prevSide = side
        }
        val avg = if (heads.isEmpty()) 4f else (heads.first().pos + heads.last().pos) / 2f
        val up = if (heads.isEmpty()) true else avg < 4f
        return NoteGlyph(e, cx, heads, up, -1)
    }

    private fun makeBeams(events: List<MelodyEvent>, glyphs: List<NoteGlyph>): List<BeamGroup> {
        val beatTicks = melody.time.clickTicks
        val groups = ArrayList<BeamGroup>()
        var current = ArrayList<NoteGlyph>()
        fun flush() {
            if (current.size >= 2) {
                val avg = current.map { (it.lowPos + it.highPos) / 2f }.average()
                val up = avg < 4.0
                val id = groups.size
                current.forEach { it.beamId = id }
                groups += BeamGroup(id, current.toList(), up)
            }
            current = ArrayList()
        }
        for (g in glyphs) {
            val e = g.event
            val beamable = !e.isRest && e.dur.ticks < 24 && e.dur.value.ticks <= 12
            if (!beamable) { flush(); continue }
            if (current.isNotEmpty() && current.last().event.startTick / beatTicks != e.startTick / beatTicks) flush()
            current += g
            // a note that crosses the beat boundary ends the group
            if ((e.startTick + e.dur.ticks) % beatTicks == 0) flush()
        }
        flush()
        return groups
    }

    /** Hit test: returns the event index near the given point or null. */
    fun hitTest(px: Float, py: Float): MelodyEvent? {
        var best: MelodyEvent? = null
        var bestD = Float.MAX_VALUE
        for (s in systems) {
            if (py < s.top || py > s.bottom) continue
            for (m in s.measures) for (gl in m.glyphs) {
                val d = kotlin.math.abs(gl.x - px)
                if (d < bestD && d < 2.6f * gap) { bestD = d; best = gl.event }
            }
        }
        return best
    }

    companion object {
        const val LEFT_PAD = 0.9f
        const val RIGHT_PAD = 0.5f
    }

}

/** Staff position (0 = bottom line) of a pitch for the given clef. */
fun staffPos(p: Pitch, clef: Clef) = p.diatonic - clef.bottomLineDiatonic

@Suppress("unused")
private fun clampInt(v: Int, lo: Int, hi: Int) = max(lo, min(hi, v))
