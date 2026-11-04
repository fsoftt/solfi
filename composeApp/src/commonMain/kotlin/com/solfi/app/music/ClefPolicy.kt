package com.solfi.app.music

/** The learner's instrument, used to recommend a clef. */
enum class Instrument { VOICE, PIANO, GUITAR, VIOLIN, CELLO, BASS, FLUTE, SAXOPHONE, TRUMPET, TROMBONE, BASSOON, TUBA, OTHER;
    companion object {
        fun fromName(name: String?): Instrument = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/** Per-profile clef choice. */
enum class ClefPreference { RECOMMENDED, TREBLE, BASS;
    companion object {
        fun fromName(name: String?): ClefPreference = entries.firstOrNull { it.name == name } ?: RECOMMENDED
    }
}

object ClefPolicy {
    /** Clef recommended for an instrument, or null when the content's own clef should be kept (piano, other). */
    fun recommended(instrument: Instrument): Clef? = when (instrument) {
        Instrument.CELLO, Instrument.BASS, Instrument.BASSOON, Instrument.TROMBONE, Instrument.TUBA -> Clef.BASS
        Instrument.VOICE, Instrument.GUITAR, Instrument.VIOLIN, Instrument.FLUTE, Instrument.SAXOPHONE, Instrument.TRUMPET -> Clef.TREBLE
        Instrument.PIANO, Instrument.OTHER -> null
    }

    /** Clef the learner wants to read in, or null for "keep what the content says". */
    fun effective(pref: ClefPreference, instrument: Instrument): Clef? = when (pref) {
        ClefPreference.TREBLE -> Clef.TREBLE
        ClefPreference.BASS -> Clef.BASS
        ClefPreference.RECOMMENDED -> recommended(instrument)
    }

    /** The clef a lesson/step is actually shown in. Locked content ignores the override. */
    fun resolve(authored: Clef, override: Clef?, locked: Boolean): Clef = if (locked || override == null) authored else override
}

/** Re-clefs melodies by whole octaves so the written notes stay on the staff. Pitch classes (note names) never change. */
object ClefShift {
    private const val MIDDLE_TREBLE = 4 * 7 + 6 // B4
    private const val MIDDLE_BASS = 3 * 7 + 1 // D3

    private fun middle(clef: Clef) = if (clef == Clef.TREBLE) MIDDLE_TREBLE else MIDDLE_BASS

    /** Number of ledger lines a note at [diatonic] needs in [clef]. */
    fun ledgerLines(diatonic: Int, clef: Clef): Int {
        val pos = diatonic - clef.bottomLineDiatonic
        return when {
            pos < 0 -> (-pos) / 2
            pos > 8 -> (pos - 8) / 2
            else -> 0
        }
    }

    /**
     * Octave shift (in octaves, may be negative) that best fits [pitches] on [target]: fewest total ledger lines,
     * ties broken by distance of the median pitch to the middle line (B4 treble, D3 bass), then smaller shift.
     */
    fun octaveShift(pitches: List<Pitch>, target: Clef): Int {
        if (pitches.isEmpty()) return 0
        val ds = pitches.map { it.diatonic }.sorted()
        val median = ds[ds.size / 2]
        return (-6..6).minWith(
            compareBy<Int>({ k -> ds.sumOf { ledgerLines(it + 7 * k, target) } }, { k -> kotlin.math.abs(median + 7 * k - middle(target)) }, { k -> kotlin.math.abs(k) }),
        )
    }

    fun shiftPitch(p: Pitch, octaves: Int) = if (octaves == 0) p else p.copy(octave = p.octave + octaves)

    fun allPitches(m: Melody): List<Pitch> = m.events.flatMap { it.pitches }

    /** Same melody written in [target], transposed by whole octaves. Returns [m] unchanged when it is already in that clef. */
    fun toClef(m: Melody, target: Clef): Melody {
        if (m.clef == target) return m
        val k = octaveShift(allPitches(m), target)
        val measures = m.measures.map { bar -> bar.map { e -> e.copy(pitches = e.pitches.map { shiftPitch(it, k) }) } }
        return Melody(target, m.time, m.key, m.tempo, measures)
    }
}
