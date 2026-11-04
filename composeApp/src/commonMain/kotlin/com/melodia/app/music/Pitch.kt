package com.melodia.app.music

import kotlin.math.pow

/** Application languages. */
enum class Lang(val code: String, val label: String) {
    ES("es", "Español"), EN("en", "English"), PT("pt", "Português");

    companion object {
        fun fromCode(code: String?): Lang = entries.firstOrNull { it.code == code?.lowercase()?.take(2) } ?: ES
    }
}

/** How notes are named in the UI, independent of the language. */
enum class NoteNaming { SOLFEGE, LETTERS;
    companion object {
        fun defaultFor(lang: Lang) = if (lang == Lang.EN) LETTERS else SOLFEGE
    }
}

/**
 * A musical pitch. [step] 0..6 = C D E F G A B, [alter] -2..2 (flats/sharps), [octave] scientific (C4 = middle C).
 */
data class Pitch(val step: Int, val alter: Int, val octave: Int) : Comparable<Pitch> {
    init { require(step in 0..6) { "step out of range: $step" } }

    val midi: Int get() = 12 * (octave + 1) + SEMITONES[step] + alter

    /** Diatonic index used for staff positions: C0 = 0, D0 = 1 ... */
    val diatonic: Int get() = octave * 7 + step

    val frequency: Double get() = frequencyOfMidi(midi)

    val letter: Char get() = LETTERS[step]

    fun transposeSemitones(n: Int): Pitch = fromMidi(midi + n, preferFlats = alter < 0)

    fun withoutAlter() = copy(alter = 0)

    /** Text such as `F#4`, `Bb3`, `C4`. */
    override fun toString(): String = letter.toString() + when {
        alter > 0 -> "#".repeat(alter)
        alter < 0 -> "b".repeat(-alter)
        else -> ""
    } + octave

    override fun compareTo(other: Pitch): Int = midi.compareTo(other.midi).let { if (it != 0) it else diatonic.compareTo(other.diatonic) }

    companion object {
        private val SEMITONES = intArrayOf(0, 2, 4, 5, 7, 9, 11)
        const val LETTERS = "CDEFGAB"

        fun frequencyOfMidi(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

        fun parseOrNull(text: String): Pitch? {
            val t = text.trim()
            if (t.length < 2) return null
            val step = LETTERS.indexOf(t[0].uppercaseChar())
            if (step < 0) return null
            var i = 1
            var alter = 0
            while (i < t.length && (t[i] == '#' || t[i] == 'b')) { alter += if (t[i] == '#') 1 else -1; i++ }
            val oct = t.substring(i).toIntOrNull() ?: return null
            if (oct !in -1..9) return null
            return Pitch(step, alter, oct)
        }

        fun parse(text: String): Pitch = parseOrNull(text) ?: throw IllegalArgumentException("Invalid pitch: '$text'")

        private val SHARP_STEPS = intArrayOf(0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6)
        private val SHARP_ALTER = intArrayOf(0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 1, 0)
        private val FLAT_STEPS = intArrayOf(0, 1, 1, 2, 2, 3, 4, 4, 5, 5, 6, 6)
        private val FLAT_ALTER = intArrayOf(0, -1, 0, -1, 0, 0, -1, 0, -1, 0, -1, 0)

        fun fromMidi(midi: Int, preferFlats: Boolean = false): Pitch {
            val pc = ((midi % 12) + 12) % 12
            val oct = (midi - pc) / 12 - 1
            return if (preferFlats) Pitch(FLAT_STEPS[pc], FLAT_ALTER[pc], oct) else Pitch(SHARP_STEPS[pc], SHARP_ALTER[pc], oct)
        }
    }
}

object NoteNames {
    private val SOLFEGE = listOf("Do", "Re", "Mi", "Fa", "Sol", "La", "Si")
    private val LETTER = listOf("C", "D", "E", "F", "G", "A", "B")

    private val SOLFEGE_PT = listOf("Dó", "Ré", "Mi", "Fá", "Sol", "Lá", "Si")

    fun base(step: Int, naming: NoteNaming, lang: Lang = Lang.ES): String = when {
        naming == NoteNaming.LETTERS -> LETTER[step]
        lang == Lang.PT -> SOLFEGE_PT[step]
        else -> SOLFEGE[step]
    }

    /** Short name with symbols, e.g. `Do♯`, `B♭`, optionally with octave number. */
    fun short(p: Pitch, naming: NoteNaming, withOctave: Boolean = false, lang: Lang = Lang.ES): String {
        val acc = when {
            p.alter > 0 -> "♯".repeat(p.alter)
            p.alter < 0 -> "♭".repeat(-p.alter)
            else -> ""
        }
        return base(p.step, naming, lang) + acc + if (withOctave) p.octave.toString() else ""
    }

    /** Long name localized: "Do sostenido", "C sharp", "Si bemol". */
    fun long(p: Pitch, naming: NoteNaming, lang: Lang, withOctave: Boolean = false): String {
        val sharp = when (lang) { Lang.ES -> "sostenido"; Lang.EN -> "sharp"; Lang.PT -> "sustenido" }
        val flat = when (lang) { Lang.ES -> "bemol"; Lang.EN -> "flat"; Lang.PT -> "bemol" }
        val double = when (lang) { Lang.ES -> "doble "; Lang.EN -> "double "; Lang.PT -> "dobrado " }
        val acc = when {
            p.alter == 1 -> " $sharp"
            p.alter == -1 -> " $flat"
            p.alter >= 2 -> " $double$sharp"
            p.alter <= -2 -> " $double$flat"
            else -> ""
        }
        return base(p.step, naming, lang) + acc + if (withOctave) p.octave.toString() else ""
    }
}
