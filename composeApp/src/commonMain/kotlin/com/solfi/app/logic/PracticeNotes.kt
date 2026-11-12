package com.solfi.app.logic

import com.solfi.app.music.Clef
import com.solfi.app.music.InstrumentTuning
import com.solfi.app.music.Pitch
import kotlin.random.Random

/** Random written notes for the "read and play" exercise. */
object PracticeNotes {
    /** Clef used for an instrument when the learner has no preference (piano / other read treble). */
    fun clefFor(effective: Clef?): Clef = effective ?: Clef.TREBLE

    /**
     * Written notes a learner of [tuning] can be asked to play on [clef]: inside the instrument's beginner range and
     * at most two ledger lines away from the staff. Falls back to the staff range when the two do not overlap
     * (the follower then accepts any octave).
     */
    fun candidates(tuning: InstrumentTuning, clef: Clef, accidentals: Boolean): List<Pitch> {
        val lo = clef.bottomLineDiatonic - 4
        val hi = clef.bottomLineDiatonic + 12
        val staff = (lo..hi).map { d -> Pitch(d % 7, 0, d / 7) }
        val withAcc = if (!accidentals) staff else staff.flatMap { p ->
            buildList {
                add(p)
                if (p.step != 2 && p.step != 6) add(p.copy(alter = 1)) // no E#, B#
                if (p.step != 3 && p.step != 0) add(p.copy(alter = -1)) // no Fb, Cb
            }
        }
        val inRange = withAcc.filter { it.midi in tuning.lowWritten..tuning.highWritten }
        return inRange.ifEmpty { withAcc }
    }

    /** A random candidate different from [previous] when possible. */
    fun next(tuning: InstrumentTuning, clef: Clef, accidentals: Boolean, rnd: Random, previous: Pitch? = null): Pitch {
        val c = candidates(tuning, clef, accidentals)
        val pool = c.filter { it != previous }.ifEmpty { c }
        return pool[rnd.nextInt(pool.size)]
    }
}
