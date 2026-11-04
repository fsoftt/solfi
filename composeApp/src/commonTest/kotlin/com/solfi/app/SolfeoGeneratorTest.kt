package com.solfi.app

import com.solfi.app.content.MelodySpec
import com.solfi.app.music.Clef
import com.solfi.app.music.Interval
import com.solfi.app.music.IntervalQuality
import com.solfi.app.music.Melody
import com.solfi.app.music.Pitch
import com.solfi.app.music.TimeSignature
import com.solfi.app.music.generator.Difficulty
import com.solfi.app.music.generator.GenMode
import com.solfi.app.music.generator.GeneratedSolfeo
import com.solfi.app.music.generator.SeededRandom
import com.solfi.app.music.generator.SolfeoGenerator
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class SolfeoGeneratorTest {
    private val seeds = (1L..1000L).map { it * 7919L + 100_000L }

    private fun forAll(block: (Difficulty, GeneratedSolfeo, Melody) -> Unit) {
        for (d in Difficulty.entries) for (seed in seeds) {
            val g = SolfeoGenerator.generate(d, seed)
            try { block(d, g, g.melody) } catch (e: Throwable) { fail("$d seed=$seed: ${e.message}\n${g.notation}") }
        }
    }

    private fun sameNote(a: Pitch, b: Pitch) = a.step == b.step && a.alter == b.alter

    private fun soundPitches(m: Melody) = m.soundNotes().map { it.pitches.single() }

    @Test fun barsSumExactlyAndStructure() = forAll { d, g, m ->
        assertEquals(g.bars, m.measures.size)
        for (bar in m.measures) assertEquals(m.time.measureTicks, bar.sumOf { it.dur.ticks })
        when (d) {
            Difficulty.BASICO -> assertEquals(4, g.bars)
            Difficulty.INTERMEDIO, Difficulty.AVANZADO -> assertEquals(8, g.bars)
            Difficulty.EXPERTO -> assertTrue(g.bars in listOf(8, 10, 12))
        }
        assertTrue(g.tempo in d.tempoRange)
        assertEquals(g.tempo, m.tempo)
    }

    @Test fun pitchesStayInRange() = forAll { d, g, m ->
        val (lo, hi) = d.range(g.clef)
        for (e in m.events) for (p in e.pitches) assertTrue(p.midi in lo.midi..hi.midi, "$p outside $lo..$hi")
        if (d <= Difficulty.INTERMEDIO) assertEquals(Clef.TREBLE, g.clef)
    }

    @Test fun notesBelongToKeyExceptChromatics() = forAll { d, g, m ->
        for (p in g.scale) if (!(g.mode == GenMode.HARMONIC_MINOR && p === g.scale[6])) assertEquals(g.key.alterFor(p.step), p.alter, "scale note $p vs key ${g.keyName}")
        assertTrue(g.scale.all { abs(it.alter) <= 1 })
        if (!d.allowsChromatic) assertTrue(g.chromaticEvents.isEmpty())
        m.events.forEachIndexed { i, e ->
            for (p in e.pitches) {
                val inScale = g.scale.any { sameNote(it, p) }
                if (i in g.chromaticEvents) {
                    assertTrue(!inScale, "chromatic flag on scale note $p")
                    assertTrue(g.scale.any { abs(it.midi % 12 - p.midi % 12) == 1 || abs(it.midi % 12 - p.midi % 12) == 11 })
                } else assertTrue(inScale, "$p not in scale of ${g.keyName}")
            }
        }
    }

    @Test fun chromaticNeighborsAreSpelledByDirection() = forAll { _, g, m ->
        // lower neighbor = raised note below (sharp/natural), upper neighbor = lowered note above (flat/natural); never an augmented unison
        val ev = m.events
        for (i in g.chromaticEvents) {
            val p = ev[i].pitches[0]
            val main = ev[i - 1].pitches[0]
            if (p.midi > main.midi) {
                assertTrue(p.alter <= 0, "upper neighbor $p of $main must be lowered")
                assertEquals(1, p.diatonic - main.diatonic, "upper neighbor $p is a second above $main")
            } else {
                assertTrue(p.alter >= 0, "lower neighbor $p of $main must be raised")
                assertEquals(1, main.diatonic - p.diatonic, "lower neighbor $p is a second below $main")
            }
        }
    }

    @Test fun phraseStructureAndCadences() = forAll { d, g, m ->
        val notes = m.events.filter { !it.isRest }
        assertTrue(g.scale.slice(listOf(0, 2, 4)).any { sameNote(it, notes.first().pitches[0]) }, "start")
        val last = m.events.last()
        assertTrue(!last.isRest && sameNote(g.tonic, last.pitches[0]), "ends on tonic")
        assertTrue(m.measures.last().all { it.dur.ticks <= last.dur.ticks }, "final note is the longest")
        val half = g.bars / 2
        val cad = m.measures[half - 1].last()
        assertTrue(!cad.isRest && g.scale.slice(listOf(1, 4, 6)).any { sameNote(it, cad.pitches[0]) }, "half cadence on 2, 5 or 7")
        assertTrue(cad.dur.ticks >= (if (m.time.unit == 8) 36 else 24), "cadence note is long")
    }

    @Test fun onlyAllowedDurationsAndMeters() = forAll { d, g, m ->
        assertTrue(d.meters.contains(g.time))
        for (e in m.events) {
            if (e.isRest) assertTrue(e.dur.code in d.allowedRestDurations, "rest ${e.dur.code}")
            else assertTrue(e.dur.code in d.allowedDurations, "dur ${e.dur.code}")
        }
        val keys = when (d) {
            Difficulty.BASICO -> setOf("C")
            Difficulty.INTERMEDIO -> setOf("C", "G", "F")
            else -> null
        }
        if (keys != null) assertTrue(g.keyName in keys)
        assertTrue(abs(g.key.fifths) <= (if (d == Difficulty.AVANZADO) 3 else 5))
        if (d == Difficulty.AVANZADO) assertTrue(g.mode == GenMode.MAJOR || g.mode == GenMode.HARMONIC_MINOR)
        if (d != Difficulty.EXPERTO) assertTrue(g.time.unit == 4)
        // syncopation / ties across barlines only in EXPERTO
        if (d != Difficulty.EXPERTO) {
            m.events.forEach { e -> if (e.tieNext) assertEquals(e.measure, m.events[e.index + 1].measure, "tie across barline below EXPERTO") }
        }
    }

    @Test fun intervalRules() = forAll { d, g, m ->
        val ps = soundPitches(m)
        val limit = d.maxLeapSteps
        for (i in 1 until ps.size) {
            val steps = abs(ps[i].diatonic - ps[i - 1].diatonic)
            assertTrue(steps <= limit, "leap of ${steps + 1} too large")
            if (d == Difficulty.INTERMEDIO) assertNotEquals(6, steps, "7th in INTERMEDIO")
            if (d <= Difficulty.INTERMEDIO) {
                val q = Interval.between(ps[i - 1], ps[i]).quality
                assertTrue(q != IntervalQuality.AUGMENTED && q != IntervalQuality.DIMINISHED, "${ps[i - 1]}->${ps[i]} is $q")
            }
        }
    }

    @Test fun leapRecovery() = forAll { _, _, m ->
        val ps = soundPitches(m)
        for (i in 2 until ps.size) {
            val prev = ps[i - 1].diatonic - ps[i - 2].diatonic
            if (abs(prev) >= 3) {
                val next = ps[i].diatonic - ps[i - 1].diatonic
                assertTrue(abs(next) == 1 && next * prev < 0, "leap $prev followed by $next")
            }
        }
    }

    @Test fun noMoreThanThreeRepeatedNotes() = forAll { _, _, m ->
        var run = 0
        var prev: Pitch? = null
        for (e in m.events) {
            if (e.isRest) { run = 0; prev = null; continue }
            val p = e.pitches[0]
            run = if (prev != null && prev == p) run + 1 else 1
            prev = p
            assertTrue(run <= 3, "$run repeated ${p}")
        }
    }

    @Test fun roundTripsThroughParser() = forAll { _, g, m ->
        val viaSpec = MelodySpec(g.clef.code, g.time.toString(), g.keyName, g.tempo, g.notation).toMelody()
        assertEquals(m.events, viaSpec.events)
        assertEquals(TimeSignature.parse(g.time.toString()), viaSpec.time)
        // re-serialise from the model and parse again
        val text = m.measures.joinToString(" | ") { bar ->
            bar.joinToString(" ") { e -> (if (e.isRest) "r" else e.pitches[0].toString()) + ":" + e.dur.code + (if (e.tieNext) "~" else "") }
        }
        assertEquals(g.notation, text)
        assertEquals(m.events, Melody.parse(g.clef.code, g.time.toString(), g.keyName, g.tempo, text).events)
    }

    @Test fun deterministicAndSeedSensitive() {
        for (d in Difficulty.entries) {
            var differing = 0
            for (seed in seeds) {
                val a = SolfeoGenerator.generate(d, seed)
                assertEquals(a, SolfeoGenerator.generate(d, seed), "$d seed=$seed not deterministic")
                if (a.notation != SolfeoGenerator.generate(d, seed + 1).notation) differing++
            }
            assertTrue(differing >= 990, "$d: only $differing/1000 neighbouring seeds differ")
        }
    }

    @Test fun prngMatchesSplitMix64ReferenceVectors() {
        // reference values of splitmix64 with seed 0 (same on every platform)
        val r = SeededRandom(0)
        assertEquals(0xE220A8397B1DCDAFUL.toLong(), r.nextLong())
        assertEquals(0x6E789E6AA1B965F4L, r.nextLong())
        assertEquals(0x06C45D188009454FL, r.nextLong())
    }

    @Test fun featuresAppearAcrossSeeds() {
        val all = Difficulty.entries.associateWith { d -> seeds.map { SolfeoGenerator.generate(d, it) } }
        assertTrue(all.getValue(Difficulty.AVANZADO).any { it.chromaticEvents.isNotEmpty() }, "AVANZADO chromatic")
        assertTrue(all.getValue(Difficulty.EXPERTO).any { it.chromaticEvents.isNotEmpty() }, "EXPERTO chromatic")
        for (d in listOf(Difficulty.AVANZADO, Difficulty.EXPERTO)) assertTrue(all.getValue(d).any { it.clef == Clef.BASS }, "$d bass")
        assertTrue(all.getValue(Difficulty.EXPERTO).any { it.time == TimeSignature(6, 8) } && all.getValue(Difficulty.EXPERTO).any { it.time == TimeSignature(3, 8) })
        assertTrue(all.getValue(Difficulty.EXPERTO).any { g -> g.melody.events.any { it.tieNext && g.melody.events[it.index + 1].measure != it.measure } }, "tie across barline")
        assertTrue(all.getValue(Difficulty.INTERMEDIO).any { g -> g.melody.events.any { it.tieNext } }, "tie within bar")
        assertTrue(all.getValue(Difficulty.EXPERTO).map { it.mode }.toSet().containsAll(listOf(GenMode.DORIAN, GenMode.MIXOLYDIAN, GenMode.NATURAL_MINOR)))
        assertTrue(all.getValue(Difficulty.INTERMEDIO).map { it.keyName }.toSet() == setOf("C", "G", "F"))
        // BASICO is mostly stepwise
        var steps = 0; var total = 0
        for (g in all.getValue(Difficulty.BASICO)) { val ps = soundPitches(g.melody); for (i in 1 until ps.size) { total++; if (abs(ps[i].diatonic - ps[i - 1].diatonic) == 1) steps++ } }
        assertTrue(steps * 100 / total >= 55, "BASICO stepwise share ${steps * 100 / total}%")
    }

    @Test fun goldenMelodiesAreStable() {
        // Pinned output: changing the generator changes every seed, so this fails on purpose.
        assertEquals("C4:h D4:q C4:q | r:q D4:q D4:h | D4:h E4:q F4:q | G4:q A4:q C5:h", SolfeoGenerator.generate(Difficulty.BASICO, 123456L).notation)
        assertEquals("C#5:q G#4:16 A4:8 E4:16 F#4:8 A4:q A3:8~ | A3:h B3:8. C4:16 B3:q~ | B3:q C#4:8 E4:16 C#4:16 B4:q A#4:8 B4:16 G#4:16 | A4:8 C#5:q G#5:q F#5:8 E5:q | A4:q Bb4:16 A4:8 G#5:16 F#5:8 E5:q G#5:8 | B4:q. C5:8 B4:16 A4:16 B3:16 C#4:16 E4:q~ | E4:q A4:q G#4:q D5:8 C#5:8 | A5:w", SolfeoGenerator.generate(Difficulty.EXPERTO, 654321L).notation)
    }
}
