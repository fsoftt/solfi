package com.solfi.app.music.generator

import com.solfi.app.music.Clef
import com.solfi.app.music.Dur
import com.solfi.app.music.Interval
import com.solfi.app.music.IntervalQuality
import com.solfi.app.music.KeySignature
import com.solfi.app.music.Melody
import com.solfi.app.music.Pitch
import com.solfi.app.music.TimeSignature
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A generated exercise: the melody in the existing model plus the parameters it was built from. */
data class GeneratedSolfeo(
    val difficulty: Difficulty,
    val seed: Long,
    /** Tonic of the scale (octave is the register used in this melody). */
    val tonic: Pitch,
    val mode: GenMode,
    /** Key signature string understood by the parser (`C`, `Bb`, `F#m`; modes use their parent key). */
    val keyName: String,
    val time: TimeSignature,
    val tempo: Int,
    val clef: Clef,
    /** Compact notation, e.g. `C4:q D4:8 E4:8 | G4:h.`; parsed back with [Melody.parse]. */
    val notation: String,
    /** Seven scale pitches starting at [tonic]. */
    val scale: List<Pitch>,
    /** Indexes (into `melody.events`) of chromatic neighbor notes, empty below AVANZADO. */
    val chromaticEvents: Set<Int>,
    val bars: Int,
) {
    val key: KeySignature get() = KeySignature.parse(keyName)
    val melody: Melody by lazy { Melody.parse(clef.code, time.toString(), keyName, tempo, notation) }
}

/**
 * Deterministic random solfeo generator. Everything is derived from [SeededRandom] (SplitMix64) using integer
 * arithmetic only, so a seed produces the same melody on every platform.
 */
object SolfeoGenerator {
    private class GenKey(val tonic: String, val mode: GenMode, val sig: String)

    private fun major(vararg t: String) = t.map { GenKey(it, GenMode.MAJOR, it) }
    private fun minor(vararg t: String) = t.flatMap { listOf(GenKey(it, GenMode.HARMONIC_MINOR, it + "m"), GenKey(it, GenMode.NATURAL_MINOR, it + "m")) }

    private fun keysFor(d: Difficulty): List<GenKey> = when (d) {
        Difficulty.BASICO -> major("C")
        Difficulty.INTERMEDIO -> major("C", "G", "F")
        Difficulty.AVANZADO -> major("C", "G", "D", "A", "F", "Bb", "Eb") +
            listOf("A", "E", "B", "F#", "D", "G", "C").map { GenKey(it, GenMode.HARMONIC_MINOR, it + "m") }
        Difficulty.EXPERTO -> major("C", "G", "D", "A", "E", "B", "F", "Bb", "Eb", "Ab", "Db") +
            minor("A", "E", "B", "F#", "C#", "D", "G", "C", "F", "Bb") +
            listOf(
                GenKey("D", GenMode.DORIAN, "C"), GenKey("E", GenMode.DORIAN, "D"), GenKey("G", GenMode.DORIAN, "F"),
                GenKey("A", GenMode.DORIAN, "G"), GenKey("C", GenMode.DORIAN, "Bb"),
                GenKey("G", GenMode.MIXOLYDIAN, "C"), GenKey("D", GenMode.MIXOLYDIAN, "G"), GenKey("A", GenMode.MIXOLYDIAN, "D"),
                GenKey("C", GenMode.MIXOLYDIAN, "F"), GenKey("F", GenMode.MIXOLYDIAN, "Bb"), GenKey("E", GenMode.MIXOLYDIAN, "A"),
            )
    }

    /** Weights (x10) for a melodic leap of [index] diatonic steps. */
    private fun leapWeights(d: Difficulty): IntArray = when (d) {
        Difficulty.BASICO -> intArrayOf(10, 60, 20)
        Difficulty.INTERMEDIO -> intArrayOf(8, 55, 28, 14, 8, 2, 0, 2)
        Difficulty.AVANZADO -> intArrayOf(8, 50, 26, 14, 10, 4, 2, 3)
        Difficulty.EXPERTO -> intArrayOf(8, 45, 24, 14, 10, 6, 4, 4, 2, 2)
    }

    // ------------------------------------------------------------------ rhythm cells

    private class Item(val dur: Dur, val rest: Boolean, var tieNext: Boolean = false, var tiePrev: Boolean = false) {
        fun copy() = Item(dur, rest, tieNext, tiePrev)
    }

    /** A rhythmic cell such as `8 8` or `q. 8` (`r` prefix = rest, `~` suffix = tied to the next item). */
    private class Cell(spec: String, val weight: Int, val sync: Boolean = false) {
        val items: List<Item> = spec.split(' ').map { tok ->
            Item(Dur.parse(tok.removePrefix("r").removeSuffix("~")), tok.startsWith("r"), tieNext = tok.endsWith("~"))
        }.also { l -> for (i in 1 until l.size) l[i].tiePrev = l[i - 1].tieNext }
        val ticks: Int = items.sumOf { it.dur.ticks }
        val hasRest: Boolean = items.any { it.rest }
    }

    private fun simpleCells(d: Difficulty): List<Cell> = buildList {
        add(Cell("q", 10)); add(Cell("rq", 1)); add(Cell("h", 3)); add(Cell("h.", 1)); add(Cell("w", 1))
        if (d >= Difficulty.INTERMEDIO) {
            add(Cell("8 8", 5)); add(Cell("q. 8", 2)); add(Cell("q~ q", 1)); add(Cell("h~ q", 1))
        }
        if (d >= Difficulty.AVANZADO) {
            add(Cell("16 16 16 16", 2)); add(Cell("8 16 16", 1)); add(Cell("16 16 8", 1)); add(Cell("8. 16", 2)); add(Cell("r8 8", 1))
        }
        if (d == Difficulty.EXPERTO) {
            // syncopation: may start off the beat
            add(Cell("8 q 8", 3, sync = true)); add(Cell("16 8 16", 1, sync = true)); add(Cell("h", 1, sync = true))
            add(Cell("q. 8", 2, sync = true)); add(Cell("8 q q 8", 1, sync = true))
        }
    }

    /** Compound meters (EXPERTO only): cells are dotted-quarter groups. */
    private fun compoundCells(): List<Cell> = listOf(
        Cell("q.", 4), Cell("8 8 8", 4), Cell("q 8", 2), Cell("8 q", 2), Cell("r8 8 8", 1), Cell("rq.", 1),
        Cell("16 16 8 8", 1), Cell("8 16 16 8", 1), Cell("h.", 2), Cell("q.~ q.", 1),
    )

    private fun wholeBar(ts: TimeSignature): List<Item> {
        val code = when (ts.measureTicks) { 36 -> "q."; 48 -> "h"; 72 -> "h."; else -> "w" }
        return listOf(Item(Dur.parse(code), false))
    }

    private fun genBar(rng: SeededRandom, cells: List<Cell>, ts: TimeSignature, firstNote: Boolean, lastMin: Int, lastLongest: Boolean): List<Item> {
        val m = ts.measureTicks
        repeat(600) { attempt ->
            val out = ArrayList<Item>()
            var pos = 0
            var rests = 0
            while (pos < m) {
                val p = pos
                val fit = cells.filter { it.ticks <= m - p && (it.sync || it.ticks < 48 || p % 48 == 0) && !(it.hasRest && rests >= 1) }
                val c = rng.weighted(fit) { it.weight } ?: break
                if (c.hasRest) rests++
                c.items.forEach { out += it.copy() }
                pos += c.ticks
            }
            if (pos != m) return@repeat
            val last = out.last()
            if (firstNote && out.first().rest) return@repeat
            if (lastMin > 0 && (last.rest || last.dur.ticks < lastMin)) return@repeat
            if (lastLongest) {
                // prefer a final note strictly longer than every other note of the bar, then settle for "at least as long"
                val others = out.dropLast(1)
                if (if (attempt < 400) others.any { it.dur.ticks >= last.dur.ticks } else others.any { it.dur.ticks > last.dur.ticks }) return@repeat
            }
            return out
        }
        return wholeBar(ts)
    }

    // ------------------------------------------------------------------ scale

    private class ScaleSpec(val tonic: Pitch, val semis: List<Int>) {
        fun pitch(d: Int): Pitch {
            val idx = d.mod(7)
            val o = d.floorDiv(7)
            val abs = tonic.step + idx
            val step = abs % 7
            val octave = tonic.octave + abs / 7 + o
            val natural = Pitch(step, 0, octave)
            return Pitch(step, tonic.midi + semis[idx] + 12 * o - natural.midi, octave)
        }
    }

    private class SUnit(val first: Int, val last: Int) { val n: Int get() = last - first + 1 }

    private class Ev(val bar: Int, val startInBar: Int, val item: Item)

    private class Target(val classes: Map<Int, Int>)

    // ------------------------------------------------------------------ main

    fun generate(difficulty: Difficulty, seed: Long): GeneratedSolfeo {
        val rng = SeededRandom(seed)
        val keys = keysFor(difficulty)
        val key = keys[rng.nextInt(keys.size)]
        val ts = difficulty.meters.let { it[rng.nextInt(it.size)] }
        val barCount = when (difficulty) {
            Difficulty.BASICO -> 4
            Difficulty.INTERMEDIO, Difficulty.AVANZADO -> 8
            Difficulty.EXPERTO -> listOf(8, 10, 12)[rng.nextInt(3)]
        }
        val tempo = rng.nextInt(difficulty.tempoRange)
        val clef = if (difficulty >= Difficulty.AVANZADO && rng.chance(30)) Clef.BASS else Clef.TREBLE
        val (lo, hi) = difficulty.range(clef).let { it.first.midi to it.second.midi }

        // register: tonic octave closest to the lower third of the range, tonic inside the range
        val tonicName = Pitch.parse(key.tonic + "4")
        val target = lo + (hi - lo) * 35 / 100
        val tonic = (1..6).map { Pitch(tonicName.step, tonicName.alter, it) }.filter { it.midi in lo..hi }.minByOrNull { abs(it.midi - target) }
            ?: error("no register for ${key.tonic}")
        val scale = ScaleSpec(tonic, key.mode.semis)
        val dLo = (-30..30).first { scale.pitch(it).midi >= lo }
        val dHi = (-30..30).last { scale.pitch(it).midi <= hi }

        val compound = ts.unit == 8
        val cells = if (compound) compoundCells() else simpleCells(difficulty)
        val half = barCount / 2
        val minEnd = if (compound) 36 else 24

        repeat(40) {
            val bars = ArrayList<List<Item>>()
            for (b in 0 until barCount) {
                bars += when {
                    b == half && b != barCount - 1 && rng.chance(70) -> bars[0].map { it.copy() }
                    b == 0 -> genBar(rng, cells, ts, true, 0, false)
                    b == half - 1 -> genBar(rng, cells, ts, false, minEnd, false)
                    b == barCount - 1 -> genBar(rng, cells, ts, false, minEnd, true)
                    else -> genBar(rng, cells, ts, false, 0, false)
                }
            }
            if (difficulty == Difficulty.EXPERTO) {
                for (b in 0 until barCount - 1) {
                    if (b == half - 1 || !rng.chance(30)) continue
                    val last = bars[b].last(); val first = bars[b + 1].first()
                    if (!last.rest && !last.tiePrev && !last.tieNext && !first.rest && !first.tiePrev) { last.tieNext = true; first.tiePrev = true }
                }
            }
            val evs = ArrayList<Ev>()
            bars.forEachIndexed { b, bar -> var p = 0; for (item in bar) { evs += Ev(b, p, item); p += item.dur.ticks } }

            val units = ArrayList<SUnit>()
            var i = 0
            while (i < evs.size) {
                if (evs[i].item.rest || evs[i].item.tiePrev) { i++; continue }
                var j = i
                while (evs[j].item.tieNext && j + 1 < evs.size) j++
                units += SUnit(i, j)
                i = j + 1
            }
            val lastOfAnte = evs.indexOfLast { it.bar == half - 1 }
            val cadenceUnit = units.indexOfFirst { lastOfAnte in it.first..it.last }
            val targets = HashMap<Int, Target>()
            targets[0] = Target(mapOf(0 to 3, 2 to 2, 4 to 3))
            targets[cadenceUnit] = Target(mapOf(1 to 3, 4 to 5, 6 to 2))
            targets[units.size - 1] = Target(mapOf(0 to 1))

            repeat(200) {
                val degs = pickDegrees(rng, difficulty, scale, dLo, dHi, evs, units, targets) ?: return@repeat
                return finish(difficulty, seed, key, tonic, scale, ts, tempo, clef, lo, hi, rng, barCount, evs, units, degs, cadenceUnit)
            }
        }
        error("could not generate $difficulty seed=$seed")
    }

    private fun pickDegrees(
        rng: SeededRandom, d: Difficulty, scale: ScaleSpec, dLo: Int, dHi: Int,
        evs: List<Ev>, units: List<SUnit>, targets: Map<Int, Target>,
    ): IntArray? {
        val lw = leapWeights(d)
        val maxLeap = min(d.maxLeapSteps, lw.size - 1)
        val reach = if (maxLeap <= 2) 2 else 3
        val noAugDim = d <= Difficulty.AVANZADO
        val degs = IntArray(units.size)
        val center = (dLo + dHi) / 2
        var run = 0
        val tIdx = targets.keys.sorted()
        for (i in units.indices) {
            val u = units[i]
            val prev = if (i > 0) degs[i - 1] else 0
            val restBetween = i > 0 && (units[i - 1].last + 1 until u.first).any { evs[it].item.rest }
            val prevLeap = if (i > 1) degs[i - 1] - degs[i - 2] else 0
            val tgt = targets[i]
            val nextT = tIdx.firstOrNull { it > i }
            val nextClasses = nextT?.let { targets[it]!!.classes.keys }
            val rem = if (nextT != null) nextT - i else 0
            val dur = (u.first..u.last).sumOf { evs[it].item.dur.ticks }
            val strongBeat = evs[u.first].startInBar == 0 || dur >= 48

            fun distToTarget(c: Int): Int {
                var best = Int.MAX_VALUE
                for (t in dLo..dHi) if (nextClasses!!.contains(t.mod(7))) best = min(best, abs(c - t))
                return best
            }

            val cands = (dLo..dHi).filter { c ->
                if (tgt != null && !tgt.classes.containsKey(c.mod(7))) return@filter false
                if (i > 0) {
                    val delta = abs(c - prev)
                    if (delta > maxLeap || lw[delta] == 0) return@filter false
                    if (abs(prevLeap) >= 3 && !(delta == 1 && (c - prev) * prevLeap < 0)) return@filter false
                    if (noAugDim && delta != 0) {
                        val q = Interval.between(scale.pitch(prev), scale.pitch(c)).quality
                        if (q == IntervalQuality.AUGMENTED || q == IntervalQuality.DIMINISHED) return@filter false
                    }
                    if (delta == 0 && !restBetween && run + u.n > 3) return@filter false
                }
                if (tgt == null && nextT != null && distToTarget(c) > rem * reach) return@filter false
                true
            }
            val curDist = if (i > 0 && nextT != null) distToTarget(prev) else 0
            val pick = rng.weighted(cands) { c ->
                var w = if (i == 0) 10 else lw[abs(c - prev)] * 4
                val cls = c.mod(7)
                if (tgt != null) w = max(1, w) * tgt.classes.getValue(cls)
                if (i == 0 && c in 0..6) w *= 3
                if (strongBeat && (cls == 0 || cls == 2 || cls == 4)) w *= 2
                if (i > 0) {
                    val mv = c - prev
                    if (abs(prevLeap) == 1 && mv == prevLeap) w = w * 3 / 2
                    if (abs(prev - center) > 4 && abs(c - center) < abs(prev - center)) w = w * 3 / 2
                    if (nextT != null && tgt == null && distToTarget(c) < curDist) w *= 2
                }
                w
            } ?: return null
            degs[i] = pick
            run = if (i > 0 && pick == prev && !restBetween) run + u.n else u.n
        }
        return degs
    }

    private fun finish(
        d: Difficulty, seed: Long, key: GenKey, tonic: Pitch, scale: ScaleSpec, ts: TimeSignature, tempo: Int, clef: Clef,
        lo: Int, hi: Int, rng: SeededRandom, barCount: Int, evs: List<Ev>, units: List<SUnit>, degs: IntArray, cadenceUnit: Int,
    ): GeneratedSolfeo {
        val pitches = Array(units.size) { scale.pitch(degs[it]) }
        val chromatic = HashSet<Int>() // unit indexes
        if (d.allowsChromatic) {
            val prob = if (d == Difficulty.EXPERTO) 70 else 35
            val cap = if (d == Difficulty.EXPERTO) 6 else 2
            for (i in 1 until units.size - 1) {
                if (chromatic.size >= cap) break
                val u = units[i]
                if (i == cadenceUnit || u.n != 1 || evs[u.first].item.dur.ticks > 24) continue
                val k = degs[i - 1]
                if (degs[i + 1] != k || abs(degs[i] - k) != 1) continue
                if (units[i - 1].last + 1 != u.first || u.last + 1 != units[i + 1].first) continue
                val main = scale.pitch(k); val nb = scale.pitch(degs[i])
                val alt: Pitch
                if (degs[i] > k) {
                    if (nb.midi - main.midi != 2) continue
                    alt = nb.copy(alter = nb.alter - 1) // lowered upper neighbor
                } else {
                    if (main.midi - nb.midi != 2) continue
                    alt = nb.copy(alter = nb.alter + 1) // raised lower neighbor
                }
                if (abs(alt.alter) > 1 || alt.midi !in lo..hi) continue
                // avoid awkward spellings (Cb, Fb, E#, B#) and chromatic notes next to each other
                if ((alt.alter < 0 && (alt.step == 0 || alt.step == 3)) || (alt.alter > 0 && (alt.step == 2 || alt.step == 6))) continue
                if ((i - 1) in chromatic) continue
                if (!rng.chance(prob)) continue
                pitches[i] = alt
                chromatic += i
            }
        }
        val sb = StringBuilder()
        val unitOf = IntArray(evs.size) { -1 }
        units.forEachIndexed { ui, u -> for (e in u.first..u.last) unitOf[e] = ui }
        val chromaticEvents = HashSet<Int>()
        var curBar = 0
        evs.forEachIndexed { idx, ev ->
            if (ev.bar != curBar) { sb.append("| "); curBar = ev.bar }
            val item = ev.item
            sb.append(if (item.rest) "r" else pitches[unitOf[idx]].toString()).append(':').append(item.dur.code)
            if (item.tieNext) sb.append('~')
            sb.append(' ')
            if (!item.rest && unitOf[idx] in chromatic) chromaticEvents += idx
        }
        return GeneratedSolfeo(
            difficulty = d, seed = seed, tonic = tonic, mode = key.mode, keyName = key.sig, time = ts, tempo = tempo, clef = clef,
            notation = sb.toString().trim(), scale = (0..6).map { scale.pitch(it) }, chromaticEvents = chromaticEvents, bars = barCount,
        )
    }
}
