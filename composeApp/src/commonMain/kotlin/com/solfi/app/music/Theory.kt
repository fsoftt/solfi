package com.melodia.app.music

import kotlin.math.abs

enum class IntervalQuality(val symbol: String) { DIMINISHED("d"), MINOR("m"), PERFECT("P"), MAJOR("M"), AUGMENTED("A") }

/** Interval between two pitches: [number] (1 = unison, 8 = octave) and [quality]. */
data class Interval(val number: Int, val quality: IntervalQuality) {
    val symbol: String get() = quality.symbol + number

    companion object {
        private val MAJOR_SEMIS = intArrayOf(0, 2, 4, 5, 7, 9, 11)
        private val PERFECT_STEPS = setOf(0, 3, 4)

        fun between(a: Pitch, b: Pitch): Interval {
            val (lo, hi) = if (a <= b) a to b else b to a
            val dn = abs(hi.diatonic - lo.diatonic)
            val semis = hi.midi - lo.midi
            val simple = dn % 7
            val octaves = dn / 7
            val diff = (semis - 12 * octaves) - MAJOR_SEMIS[simple]
            val q = if (simple in PERFECT_STEPS) when {
                diff == 0 -> IntervalQuality.PERFECT
                diff > 0 -> IntervalQuality.AUGMENTED
                else -> IntervalQuality.DIMINISHED
            } else when {
                diff == 0 -> IntervalQuality.MAJOR
                diff == -1 -> IntervalQuality.MINOR
                diff > 0 -> IntervalQuality.AUGMENTED
                else -> IntervalQuality.DIMINISHED
            }
            return Interval(dn + 1, q)
        }
    }
}

/** Spells a list of pitches from a tonic, given semitone offsets and diatonic step offsets. */
internal fun spell(tonic: Pitch, semis: List<Int>, steps: List<Int>): List<Pitch> =
    semis.indices.map { i ->
        val absStep = tonic.step + steps[i]
        val step = absStep % 7
        val oct = tonic.octave + absStep / 7
        val natural = Pitch(step, 0, oct)
        Pitch(step, tonic.midi + semis[i] - natural.midi, oct)
    }

enum class ScaleType(val semis: List<Int>) {
    MAJOR(listOf(0, 2, 4, 5, 7, 9, 11, 12)),
    NATURAL_MINOR(listOf(0, 2, 3, 5, 7, 8, 10, 12)),
    HARMONIC_MINOR(listOf(0, 2, 3, 5, 7, 8, 11, 12)),
}

object Scales {
    /** Eight pitches (tonic to octave) correctly spelled. */
    fun build(tonic: Pitch, type: ScaleType = ScaleType.MAJOR): List<Pitch> = spell(tonic, type.semis, listOf(0, 1, 2, 3, 4, 5, 6, 7))
}

/** Key signature such as `C`, `G`, `Bb`, `Am`, `F#m`. */
data class KeySignature(val tonic: Pitch, val minor: Boolean) {
    /** Number of sharps (positive) or flats (negative). */
    val fifths: Int get() {
        val name = Pitch(tonic.step, tonic.alter, 4).let { it.letter.toString() + (if (it.alter > 0) "#" else if (it.alter < 0) "b" else "") }
        return (if (minor) MINOR_FIFTHS else MAJOR_FIFTHS)[name] ?: 0
    }

    /** Alteration (-1, 0, +1) that this key applies to the given letter step (0..6). */
    fun alterFor(step: Int): Int {
        val f = fifths
        return if (f > 0 && step in SHARP_ORDER.take(f)) 1 else if (f < 0 && step in FLAT_ORDER.take(-f)) -1 else 0
    }

    /** Staff-step order of sharps/flats, as letter steps. */
    fun accidentals(): List<Int> = if (fifths >= 0) SHARP_ORDER.take(fifths) else FLAT_ORDER.take(-fifths)

    companion object {
        /** F C G D A E B */
        val SHARP_ORDER = listOf(3, 0, 4, 1, 5, 2, 6)
        /** B E A D G C F */
        val FLAT_ORDER = listOf(6, 2, 5, 1, 4, 0, 3)
        private val MAJOR_FIFTHS = mapOf("C" to 0, "G" to 1, "D" to 2, "A" to 3, "E" to 4, "B" to 5, "F#" to 6, "C#" to 7,
            "F" to -1, "Bb" to -2, "Eb" to -3, "Ab" to -4, "Db" to -5, "Gb" to -6, "Cb" to -7)
        private val MINOR_FIFTHS = mapOf("A" to 0, "E" to 1, "B" to 2, "F#" to 3, "C#" to 4, "G#" to 5, "D#" to 6, "A#" to 7,
            "D" to -1, "G" to -2, "C" to -3, "F" to -4, "Bb" to -5, "Eb" to -6, "Ab" to -7)

        fun parse(text: String): KeySignature {
            val t = text.trim()
            val minor = t.endsWith("m")
            val core = if (minor) t.dropLast(1) else t
            val p = Pitch.parseOrNull(core + "4") ?: throw IllegalArgumentException("Invalid key: '$text'")
            val sig = KeySignature(p, minor)
            val name = core.first().uppercaseChar() + core.drop(1)
            require((if (minor) MINOR_FIFTHS else MAJOR_FIFTHS).containsKey(name)) { "Unsupported key: '$text'" }
            return sig
        }
    }
}

enum class ChordQuality(val semis: List<Int>, val steps: List<Int>, val symbol: String) {
    MAJOR(listOf(0, 4, 7), listOf(0, 2, 4), ""),
    MINOR(listOf(0, 3, 7), listOf(0, 2, 4), "m"),
    DIMINISHED(listOf(0, 3, 6), listOf(0, 2, 4), "dim"),
    AUGMENTED(listOf(0, 4, 8), listOf(0, 2, 4), "aug"),
    DOMINANT7(listOf(0, 4, 7, 10), listOf(0, 2, 4, 6), "7"),
    MAJOR7(listOf(0, 4, 7, 11), listOf(0, 2, 4, 6), "maj7"),
    MINOR7(listOf(0, 3, 7, 10), listOf(0, 2, 4, 6), "m7"),
    DIMINISHED7(listOf(0, 3, 6, 9), listOf(0, 2, 4, 6), "dim7"),
    HALF_DIMINISHED7(listOf(0, 3, 6, 10), listOf(0, 2, 4, 6), "m7b5"),
}

object Chords {
    fun build(root: Pitch, quality: ChordQuality): List<Pitch> = spell(root, quality.semis, quality.steps)
    fun triad(root: Pitch, minor: Boolean = false) = build(root, if (minor) ChordQuality.MINOR else ChordQuality.MAJOR)
}
