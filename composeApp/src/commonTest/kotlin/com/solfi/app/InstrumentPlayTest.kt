package com.solfi.app

import com.solfi.app.audio.PitchDetector
import com.solfi.app.audio.PitchReading
import com.solfi.app.logic.NoteFollower
import com.solfi.app.logic.PlayTarget
import com.solfi.app.music.Instrument
import com.solfi.app.music.InstrumentTuning
import com.solfi.app.music.Melody
import com.solfi.app.music.Pitch
import com.solfi.app.music.PitchJudge
import com.solfi.app.music.PitchVerdict
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstrumentPlayTest {
    private fun p(s: String) = Pitch.parse(s)

    // ------------------------------------------------------------------ transposition

    @Test fun transposingInstrumentsSoundWhereExpected() {
        // written C5 on a B♭ trumpet sounds B♭4
        assertEquals(p("Bb4").midi, InstrumentTuning.of(Instrument.TRUMPET).concertMidi(p("C5").midi))
        assertEquals(p("Bb4").midi, InstrumentTuning.of(Instrument.CLARINET).concertMidi(p("C5").midi))
        // alto sax: written C5 sounds E♭4 (major sixth lower)
        assertEquals(p("Eb4").midi, InstrumentTuning.of(Instrument.SAXOPHONE).concertMidi(p("C5").midi))
        // tenor sax: written C5 sounds B♭3 (major ninth lower)
        assertEquals(p("Bb3").midi, InstrumentTuning.of(Instrument.TENOR_SAX).concertMidi(p("C5").midi))
        // horn in F: written C5 sounds F4
        assertEquals(p("F4").midi, InstrumentTuning.of(Instrument.FRENCH_HORN).concertMidi(p("C5").midi))
        // guitar and bass sound an octave lower than written
        assertEquals(p("E2").midi, InstrumentTuning.of(Instrument.GUITAR).concertMidi(p("E3").midi))
        assertEquals(p("E1").midi, InstrumentTuning.of(Instrument.BASS).concertMidi(p("E2").midi))
        for (i in listOf(Instrument.PIANO, Instrument.VIOLIN, Instrument.FLUTE, Instrument.CELLO, Instrument.TROMBONE, Instrument.TUBA, Instrument.BASSOON)) {
            assertEquals(0, InstrumentTuning.of(i).transpose, "$i")
        }
    }

    @Test fun concertSpellingUsesFlatsForFlatInstruments() {
        assertEquals("Bb4", InstrumentTuning.of(Instrument.TRUMPET).concert(p("C5")).toString())
        assertEquals("Eb4", InstrumentTuning.of(Instrument.SAXOPHONE).concert(p("C5")).toString())
        assertEquals(p("D5").midi, InstrumentTuning.of(Instrument.TRUMPET).written(p("C5").midi).midi)
    }

    @Test fun everyInstrumentHasASaneRange() {
        for (i in Instrument.entries) {
            val t = InstrumentTuning.of(i)
            assertTrue(t.lowWritten < t.highWritten, "$i")
            assertTrue(t.minFrequency in 25.0..400.0, "$i min ${t.minFrequency}")
        }
    }

    // ------------------------------------------------------------------ judging

    private fun cents(midi: Int, c: Double) = Pitch.frequencyOfMidi(midi) * 2.0.pow(c / 1200.0)

    @Test fun judgeHighAndLow() {
        val a4 = 69
        assertEquals(PitchVerdict.IN_TUNE, PitchJudge.judge(440.0, a4).verdict)
        assertEquals(PitchVerdict.IN_TUNE, PitchJudge.judge(cents(a4, 15.0), a4).verdict)
        assertEquals(PitchVerdict.SLIGHTLY_HIGH, PitchJudge.judge(cents(a4, 35.0), a4).verdict)
        assertEquals(PitchVerdict.SLIGHTLY_LOW, PitchJudge.judge(cents(a4, -35.0), a4).verdict)
        val high = PitchJudge.judge(Pitch.frequencyOfMidi(71), a4)
        assertEquals(PitchVerdict.TOO_HIGH, high.verdict); assertEquals(2, high.semitones)
        val low = PitchJudge.judge(Pitch.frequencyOfMidi(68), a4)
        assertEquals(PitchVerdict.TOO_LOW, low.verdict); assertEquals(-1, low.semitones)
    }

    @Test fun octaveFreeIgnoresTheOctaveOnly() {
        assertEquals(PitchVerdict.TOO_LOW, PitchJudge.judge(220.0, 69).verdict)
        assertEquals(PitchVerdict.IN_TUNE, PitchJudge.judge(220.0, 69, octaveFree = true).verdict)
        assertEquals(PitchVerdict.SLIGHTLY_HIGH, PitchJudge.judge(cents(57, 30.0), 69, octaveFree = true).verdict)
        assertEquals(PitchVerdict.TOO_HIGH, PitchJudge.judge(Pitch.frequencyOfMidi(58), 69, octaveFree = true).verdict)
    }

    // ------------------------------------------------------------------ detector

    private fun tone(freq: Double, sr: Int, n: Int, harmonics: Boolean = true, amp: Double = 0.4): FloatArray =
        FloatArray(n) { i ->
            val t = i.toDouble() / sr
            var v = sin(2 * PI * freq * t)
            if (harmonics) v += 0.6 * sin(2 * PI * 2 * freq * t) + 0.35 * sin(2 * PI * 3 * freq * t) + 0.2 * sin(2 * PI * 4 * freq * t)
            (amp * v / (if (harmonics) 2.15 else 1.0)).toFloat()
        }

    /** Last reading of the full-range detector (the one the app uses) over half a second of [sig]. */
    private fun detectSignal(sig: FloatArray, sr: Int): Double? {
        val d = PitchDetector(sr)
        var last: PitchReading? = null
        var i = 0
        while (i < sig.size) {
            val n = minOf(512, sig.size - i)
            d.push(sig.copyOfRange(i, i + n))?.let { last = it }
            i += n
        }
        return last?.frequency
    }

    private fun assertDetects(name: String, sig: (Double, Int) -> FloatArray, sr: Int = 44100, tolCents: Double = 5.0) {
        val f = Pitch.parse(name).frequency
        val got = assertNotNull(detectSignal(sig(f, sr), sr), "$name @ $sr Hz: nothing detected")
        val err = 1200 * log2(got / f)
        assertTrue(abs(err) < tolCents, "$name @ $sr Hz: expected $f got $got ($err cents)")
    }

    // A0 (below a BB♭ tuba's pedal B♭0) … C8 (top of the piccolo), every few semitones.
    private val fullRange = (21..108 step 5).map { Pitch.fromMidi(it).toString() } + listOf("Bb0", "D1", "E2", "C8")

    @Test fun detectsTheFullRangeFromTubaToPiccolo() {
        for (sr in listOf(44100, 48000)) for (name in fullRange) assertDetects(name, { f, r -> tone(f, r, r / 2) }, sr)
    }

    @Test fun detectsPureSinesAcrossTheRange() {
        for (name in fullRange) assertDetects(name, { f, r -> tone(f, r, r / 2, harmonics = false) })
    }

    /** Low brass: the fundamental is much weaker than the 2nd and 3rd harmonics. It must not read an octave up. */
    @Test fun lowBrassWithWeakFundamentalIsNotAnOctaveOff() {
        for (name in listOf("Bb0", "D1", "F1", "Bb1", "F2")) assertDetects(name, { f, sr ->
            FloatArray(sr / 2) { i ->
                val t = i.toDouble() / sr
                (0.1 * (0.25 * sin(2 * PI * f * t) + sin(2 * PI * 2 * f * t) + 0.8 * sin(2 * PI * 3 * f * t) + 0.4 * sin(2 * PI * 4 * f * t))).toFloat()
            }
        })
    }

    /** Voices from bass (E2) to soprano (C6), with a ±20-cent vibrato at 5.5 Hz: the reading stays near the note. */
    @Test fun detectsVoicesWithVibrato() {
        for (name in listOf("E2", "A2", "D3", "G3", "C4", "F4", "A4", "D5", "G5", "C6")) assertDetects(name, { f, sr ->
            var phase = 0.0
            FloatArray(sr / 2) { i ->
                val inst = f * 2.0.pow(20.0 / 1200.0 * sin(2 * PI * 5.5 * i / sr))
                phase += 2 * PI * inst / sr
                (0.2 * (sin(phase) + 0.5 * sin(2 * phase) + 0.25 * sin(3 * phase))).toFloat()
            }
        }, tolCents = 25.0)
    }

    @Test fun detectsPureSineAndSilence() {
        val sr = 48000
        val d = PitchDetector(sr, 80.0, 1500.0)
        var r: PitchReading? = null
        val s = tone(330.0, sr, sr / 4, harmonics = false)
        for (k in 0 until s.size / 480) d.push(s.copyOfRange(k * 480, k * 480 + 480))?.let { r = it }
        assertTrue(abs(1200 * log2(r!!.frequency!! / 330.0)) < 3.0)
        val quiet = PitchDetector(sr, 80.0, 1500.0)
        var q: PitchReading? = null
        repeat(40) { quiet.push(FloatArray(480))?.let { q = it } }
        assertNull(q!!.frequency)
    }

    // ------------------------------------------------------------------ follower

    private fun reading(freq: Double?) = PitchReading(freq, if (freq == null) 0.0 else 0.95, if (freq == null) 0.0 else 0.2)

    private fun NoteFollower.hold(freq: Double?, ms: Int) { repeat(ms / 20) { feed(reading(freq), 20.0) } }

    @Test fun followerAcceptsTheRightNoteAndAdvances() {
        val f = NoteFollower(listOf(PlayTarget(listOf(p("C4"))), PlayTarget(listOf(p("E4")))), InstrumentTuning.of(Instrument.PIANO))
        f.hold(p("C4").frequency, 100)
        assertEquals(0, f.state.index)
        f.hold(p("C4").frequency, 200)
        assertEquals(1, f.state.index)
        assertEquals(PitchVerdict.IN_TUNE, f.state.results[0].verdict)
        f.hold(p("E4").frequency, 300)
        assertTrue(f.state.done)
        assertEquals(2, f.state.firstTry)
    }

    @Test fun followerReportsWrongNotesHighOrLow() {
        val f = NoteFollower(listOf(PlayTarget(listOf(p("G4")))), InstrumentTuning.of(Instrument.FLUTE))
        f.hold(p("A4").frequency, 400)
        val w = assertNotNull(f.state.lastWrong)
        assertEquals(PitchVerdict.TOO_HIGH, w.judgement.verdict)
        f.hold(null, 100)
        f.hold(p("F4").frequency, 400)
        assertEquals(PitchVerdict.TOO_LOW, f.state.lastWrong!!.judgement.verdict)
        f.hold(cents(p("G4").midi, 35.0), 300)
        assertTrue(f.state.done)
        assertEquals(PitchVerdict.SLIGHTLY_HIGH, f.state.results[0].verdict)
        assertEquals(2, f.state.results[0].mistakes)
    }

    @Test fun followerAppliesTransposition() {
        // a B♭ trumpet reading written D5 must sound C5
        val f = NoteFollower(listOf(PlayTarget(listOf(p("D5")))), InstrumentTuning.of(Instrument.TRUMPET))
        f.hold(p("D5").frequency, 400)
        assertFalse(f.state.done)
        assertEquals(PitchVerdict.TOO_HIGH, f.state.lastWrong!!.judgement.verdict)
        f.hold(null, 60)
        f.hold(p("C5").frequency, 300)
        assertTrue(f.state.done)
    }

    @Test fun repeatedNotesNeedToBePlayedAgain() {
        val m = Melody.parseLoose("C4:q C4:q D4:q")
        val f = NoteFollower(NoteFollower.targetsOf(m), InstrumentTuning.of(Instrument.VIOLIN))
        f.hold(p("C4").frequency, 600)
        assertEquals(1, f.state.index, "a long note counts once")
        assertTrue(f.state.waitingRelease)
        f.hold(null, 60)
        f.hold(p("C4").frequency, 250)
        assertEquals(2, f.state.index)
    }

    @Test fun tiedNotesAreOneTargetAndRestsAreSkipped() {
        val m = Melody.parseLoose("C4:h~ C4:q r:q E4:q")
        assertEquals(2, NoteFollower.targetsOf(m).size)
    }
}

class InstrumentListenerTest {
    @Test fun listenerFollowsASynthesizedMelodyThroughTheDetector() {
        val tuning = InstrumentTuning.of(Instrument.CLARINET)
        val l = com.solfi.app.audio.InstrumentListener(null)
        val m = Melody.parseLoose("C5:q D5:q E5:q")
        l.follow(NoteFollower.targetsOf(m), tuning)
        val sr = 44100
        for (name in listOf("C5", "D5", "E5")) {
            val f = tuning.concert(Pitch.parse(name)).frequency
            val n = sr * 4 / 10
            val buf = FloatArray(n) { i -> (0.3 * sin(2 * PI * f * i / sr)).toFloat() }
            var i = 0
            while (i < n) { val k = minOf(512, n - i); l.feedForTest(buf.copyOfRange(i, i + k), k, sr); i += k }
            l.feedForTest(FloatArray(sr / 10), sr / 10, sr) // short gap between notes
        }
        assertTrue(l.follow.value.done, "state ${l.follow.value}")
        assertEquals(3, l.follow.value.inTune)
    }

    @Test fun practiceNotesStayInsideTheInstrumentRange() {
        val rnd = kotlin.random.Random(1)
        for (i in Instrument.entries) {
            val t = InstrumentTuning.of(i)
            val clef = com.solfi.app.music.ClefPolicy.recommended(i) ?: com.solfi.app.music.Clef.TREBLE
            val c = com.solfi.app.logic.PracticeNotes.candidates(t, clef, accidentals = true)
            assertTrue(c.isNotEmpty(), "$i")
            assertTrue(c.all { it.midi in t.lowWritten..t.highWritten }, "$i")
            assertTrue(c.none { (it.step == 2 || it.step == 6) && it.alter == 1 || (it.step == 3 || it.step == 0) && it.alter == -1 })
            var prev: Pitch? = null
            repeat(20) { val n = com.solfi.app.logic.PracticeNotes.next(t, clef, true, rnd, prev); if (c.size > 1) assertTrue(n != prev); prev = n }
        }
    }
}

class TunerStaffTest {
    private fun staff(n: String) = com.solfi.app.logic.PracticeNotes.staffFor(Pitch.parse(n))

    @Test fun extremeNotesUseOttavaInsteadOfManyLedgerLines() {
        assertEquals(com.solfi.app.music.Clef.TREBLE to 0, staff("A4"))
        assertEquals(com.solfi.app.music.Clef.BASS to 0, staff("G2"))
        assertEquals(com.solfi.app.music.Clef.TREBLE to 1, staff("C7"))
        assertEquals(com.solfi.app.music.Clef.TREBLE to 2, staff("C8"))
        assertEquals(com.solfi.app.music.Clef.BASS to -1, staff("D1"))
        for (m in 21..108) {
            val p = Pitch.fromMidi(m)
            val (clef, oct) = com.solfi.app.logic.PracticeNotes.staffFor(p)
            assertTrue(com.solfi.app.music.ClefShift.ledgerLines(p.diatonic - 7 * oct, clef) <= 3, "$p")
        }
    }
}
