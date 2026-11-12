package com.solfi.app

import com.solfi.app.audio.PitchReading
import com.solfi.app.logic.TimedScorer
import com.solfi.app.logic.TimedVerdict
import com.solfi.app.music.Instrument
import com.solfi.app.music.InstrumentTuning
import com.solfi.app.music.Melody
import com.solfi.app.music.Pitch
import com.solfi.app.music.PitchVerdict
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimedScorerTest {
    private val tempo = 120 // 500 ms per quarter
    private val trumpet = InstrumentTuning.of(Instrument.TRUMPET)
    private val melody = Melody.parse("treble", "4/4", "C", tempo, "D4:q E4:q r:q F#4:q | G4:h~ G4:q G4:q")
    private val notes = TimedScorer.notesOf(melody, trumpet)

    private fun freq(midi: Int, cents: Double = 0.0) = Pitch.frequencyOfMidi(midi) * 2.0.pow(cents / 1200)

    /**
     * Simulates the learner: each note sounds from its start + [offsetMs] to its end with the given pitch
     * (concert MIDI, cents); readings every 40 ms.
     */
    private fun play(shape: (Int) -> Triple<Double, Int?, Double>?): TimedScorer {
        val sc = TimedScorer(notes, tempo, octaveFree = false)
        var t = -1.0
        while (t < melody.totalBeats + 1) {
            val i = notes.indexOfLast { n -> shape(notes.indexOf(n))?.let { (off, _, _) -> t >= n.startBeat + off / 500.0 } ?: false }
            val f = notes.getOrNull(i)?.let { n ->
                val (off, midi, cents) = shape(i)!!
                if (midi != null && t < n.endBeat + off / 500.0 - 0.05) freq(midi, cents) else null
            }
            sc.feed(t, PitchReading(f, if (f != null) 0.95 else 0.1, 0.2))
            t += 40.0 / 500.0
        }
        return sc
    }

    @Test fun concertPitchesFollowTheInstrument() {
        // written D4 on a B-flat trumpet sounds C4 (60); the tie merges G4:h~G4:q into one note
        assertEquals(listOf(60, 62, 64, 65, 65), notes.map { it.concertMidis.single() })
        assertEquals(5, notes.size)
        assertEquals(listOf(0, 0, 0, 1, 1), notes.map { it.measure })
    }

    @Test fun perfectTakeIsAllOnTimeAndInTune() {
        val r = play { i -> Triple(0.0, notes[i].concertMidis.single(), 3.0) }.scoreAll()
        assertTrue(r.all { it.verdict == TimedVerdict.ON_TIME && it.pitch == PitchVerdict.IN_TUNE }, r.toString())
        val s = TimedScorer.summary(notes, r)
        assertEquals(5, s.onTime); assertEquals(5, s.inTune); assertTrue(s.measuresToReview.isEmpty())
    }

    @Test fun lateEarlyWrongMissedAndOutOfTune() {
        val r = play { i ->
            val m = notes[i].concertMidis.single()
            when (i) {
                0 -> Triple(250.0, m, 0.0)      // late
                1 -> Triple(0.0, m + 2, 0.0)    // wrong, higher
                2 -> Triple(-200.0, m, 0.0)     // early (after the rest)
                3 -> null                        // silent
                else -> Triple(0.0, m, -35.0)   // on time, flat
            }
        }.scoreAll()
        assertEquals(TimedVerdict.LATE, r[0].verdict)
        assertEquals(TimedVerdict.WRONG_NOTE, r[1].verdict); assertEquals(PitchVerdict.TOO_HIGH, r[1].pitch)
        assertEquals(TimedVerdict.EARLY, r[2].verdict)
        assertEquals(TimedVerdict.MISSED, r[3].verdict)
        assertEquals(TimedVerdict.ON_TIME, r[4].verdict); assertEquals(PitchVerdict.SLIGHTLY_LOW, r[4].pitch)
        assertEquals(listOf(1, 2), TimedScorer.summary(notes, r).measuresToReview)
    }

    @Test fun notesAreJudgedOnlyAfterTheyEnd() {
        val sc = TimedScorer(notes, tempo)
        assertTrue(!sc.canScore(0, 0.9))
        assertTrue(sc.canScore(0, 1.2))
    }
}
