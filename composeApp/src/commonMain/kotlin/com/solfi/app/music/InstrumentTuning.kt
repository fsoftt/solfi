package com.solfi.app.music

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * How an instrument relates the written note (what the learner reads) to the sounding (concert) pitch the
 * microphone hears, plus a comfortable written range for beginners.
 *
 * concert = written + [transpose] semitones. Examples: B♭ trumpet −2 (written C sounds B♭), E♭ alto sax −9
 * (written C sounds the E♭ a major sixth below), guitar −12 (sounds an octave lower than written).
 */
data class InstrumentTuning(
    val instrument: Instrument,
    val transpose: Int,
    /** Lowest/highest written MIDI note a beginner is asked to play. */
    val lowWritten: Int,
    val highWritten: Int,
    /** Voice / other: any octave of the note is accepted (men sing an octave lower, etc.). */
    val octaveFree: Boolean = false,
) {
    val isTransposing: Boolean get() = transpose % 12 != 0

    fun concertMidi(writtenMidi: Int) = writtenMidi + transpose
    fun writtenMidi(concertMidi: Int) = concertMidi - transpose

    /** Concert pitch of a written note, spelled with flats when the instrument is a flat instrument. */
    fun concert(written: Pitch): Pitch = Pitch.fromMidi(written.midi + transpose, preferFlats = written.alter < 0 || transpose in FLAT_TRANSPOSITIONS)

    /** Written pitch that sounds as [concertMidi] on this instrument. */
    fun written(concertMidi: Int): Pitch = Pitch.fromMidi(concertMidi - transpose)

    /** Lowest / highest concert frequency of the beginner range (with a margin). The detector itself listens to the full range. */
    val minFrequency: Double get() = Pitch.frequencyOfMidi(concertMidi(lowWritten) - 5)
    val maxFrequency: Double get() = Pitch.frequencyOfMidi(concertMidi(highWritten) + 7)

    companion object {
        private val FLAT_TRANSPOSITIONS = setOf(-2, -9, -14, -7, -3)

        private fun m(text: String) = Pitch.parse(text).midi

        fun of(i: Instrument): InstrumentTuning = when (i) {
            Instrument.VOICE -> InstrumentTuning(i, 0, m("E2"), m("C6"), octaveFree = true)
            Instrument.PIANO -> InstrumentTuning(i, 0, m("C2"), m("C6"))
            Instrument.GUITAR -> InstrumentTuning(i, -12, m("E3"), m("E5"))
            Instrument.VIOLIN -> InstrumentTuning(i, 0, m("G3"), m("B5"))
            Instrument.CELLO -> InstrumentTuning(i, 0, m("C2"), m("D4"))
            Instrument.BASS -> InstrumentTuning(i, -12, m("E2"), m("G3"))
            Instrument.FLUTE -> InstrumentTuning(i, 0, m("C4"), m("A5"))
            Instrument.CLARINET -> InstrumentTuning(i, -2, m("E3"), m("C6"))
            Instrument.SAXOPHONE -> InstrumentTuning(i, -9, m("Bb3"), m("C6"))
            Instrument.TENOR_SAX -> InstrumentTuning(i, -14, m("Bb3"), m("C6"))
            Instrument.TRUMPET -> InstrumentTuning(i, -2, m("F#3"), m("G5"))
            Instrument.FRENCH_HORN -> InstrumentTuning(i, -7, m("C4"), m("G5"))
            Instrument.TROMBONE -> InstrumentTuning(i, 0, m("E2"), m("F4"))
            Instrument.BASSOON -> InstrumentTuning(i, 0, m("Bb1"), m("F4"))
            Instrument.TUBA -> InstrumentTuning(i, 0, m("D1"), m("F3"))
            Instrument.OTHER -> InstrumentTuning(i, 0, m("C2"), m("C6"), octaveFree = true)
        }
    }
}

/** Result of comparing what was heard with the note that was expected. */
enum class PitchVerdict {
    /** Right note, within ±[PitchJudge.IN_TUNE_CENTS]. */
    IN_TUNE,
    /** Right note but sharp (too high). */
    SLIGHTLY_HIGH,
    /** Right note but flat (too low). */
    SLIGHTLY_LOW,
    /** A different, higher note. */
    TOO_HIGH,
    /** A different, lower note. */
    TOO_LOW,
    ;

    val isRightNote: Boolean get() = this == IN_TUNE || this == SLIGHTLY_HIGH || this == SLIGHTLY_LOW
}

data class PitchJudgement(
    val verdict: PitchVerdict,
    /** Deviation from the expected concert pitch, in cents (after octave folding when allowed). */
    val cents: Double,
    /** Semitones away from the expected note (0 when it is the right note). */
    val semitones: Int,
    /** Concert MIDI note nearest to what was heard. */
    val heardMidi: Int,
)

object PitchJudge {
    /** Inside this window the note counts as in tune. */
    const val IN_TUNE_CENTS = 20.0
    /** Up to this deviation it is still the right note, only out of tune. */
    const val RIGHT_NOTE_CENTS = 50.0

    fun centsBetween(freq: Double, midi: Int): Double = 1200.0 * log2(freq / Pitch.frequencyOfMidi(midi))

    fun midiOf(freq: Double): Double = 69.0 + 12.0 * log2(freq / 440.0)

    /**
     * Judges [freq] (Hz, as heard) against the expected concert MIDI note. With [octaveFree] the closest octave of
     * the expected note is used, so only the note name (and its tuning) matters.
     */
    fun judge(freq: Double, expectedConcertMidi: Int, octaveFree: Boolean = false): PitchJudgement {
        var cents = centsBetween(freq, expectedConcertMidi)
        if (octaveFree) {
            cents %= 1200.0
            if (cents >= 600.0) cents -= 1200.0
            if (cents < -600.0) cents += 1200.0
        }
        val heard = midiOf(freq).roundToInt()
        val verdict = when {
            abs(cents) <= IN_TUNE_CENTS -> PitchVerdict.IN_TUNE
            abs(cents) <= RIGHT_NOTE_CENTS -> if (cents > 0) PitchVerdict.SLIGHTLY_HIGH else PitchVerdict.SLIGHTLY_LOW
            cents > 0 -> PitchVerdict.TOO_HIGH
            else -> PitchVerdict.TOO_LOW
        }
        val semis = if (verdict.isRightNote) 0 else (cents / 100.0).roundToInt()
        return PitchJudgement(verdict, cents, semis, heard)
    }
}
