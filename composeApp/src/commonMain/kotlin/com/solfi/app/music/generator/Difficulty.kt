package com.solfi.app.music.generator

import com.solfi.app.music.Clef
import com.solfi.app.music.Pitch
import com.solfi.app.music.TimeSignature

/** Difficulty of a randomly generated solfeo. All difficulties are available from the start (no gating). */
enum class Difficulty {
    BASICO, INTERMEDIO, AVANZADO, EXPERTO;

    /** Meters this difficulty may use. */
    val meters: List<TimeSignature>
        get() = when (this) {
            BASICO, INTERMEDIO, AVANZADO -> SIMPLE
            EXPERTO -> SIMPLE + listOf(TimeSignature(6, 8), TimeSignature(3, 8))
        }

    /** Duration codes (value plus dots) of sounding notes this difficulty may use. */
    val allowedDurations: Set<String>
        get() = when (this) {
            BASICO -> setOf("w", "h", "q", "h.")
            INTERMEDIO -> setOf("w", "h", "q", "h.", "8", "q.")
            AVANZADO, EXPERTO -> setOf("w", "h", "q", "h.", "8", "q.", "16", "8.")
        }

    /** Duration codes of rests this difficulty may use. */
    val allowedRestDurations: Set<String>
        get() = when (this) {
            BASICO, INTERMEDIO -> setOf("q")
            AVANZADO -> setOf("q", "8")
            EXPERTO -> setOf("q", "8", "q.")
        }

    val allowsChromatic: Boolean get() = this >= AVANZADO

    /** Largest melodic leap, in diatonic steps (2 = a 3rd, 7 = an octave, 9 = a 10th). */
    val maxLeapSteps: Int
        get() = when (this) { BASICO -> 2; INTERMEDIO -> 7; AVANZADO -> 7; EXPERTO -> 9 }

    /** Inclusive written range of the generated melody in [clef] (before any clef override shift). */
    fun range(clef: Clef): Pair<Pitch, Pitch> = when {
        clef == Clef.BASS -> Pitch.parse("E2") to Pitch.parse("C4")
        this == BASICO -> Pitch.parse("C4") to Pitch.parse("C5")
        this == INTERMEDIO -> Pitch.parse("A3") to Pitch.parse("E5")
        this == AVANZADO -> Pitch.parse("B3") to Pitch.parse("G5")
        else -> Pitch.parse("A3") to Pitch.parse("A5")
    }

    val tempoRange: IntRange
        get() = when (this) { BASICO -> 60..80; INTERMEDIO -> 70..100; AVANZADO -> 80..110; EXPERTO -> 80..120 }

    private companion object {
        val SIMPLE = listOf(TimeSignature(2, 4), TimeSignature(3, 4), TimeSignature(4, 4))
    }
}

/** Scale/mode a generated melody is written in. */
enum class GenMode(val semis: List<Int>) {
    MAJOR(listOf(0, 2, 4, 5, 7, 9, 11)),
    NATURAL_MINOR(listOf(0, 2, 3, 5, 7, 8, 10)),
    HARMONIC_MINOR(listOf(0, 2, 3, 5, 7, 8, 11)),
    DORIAN(listOf(0, 2, 3, 5, 7, 9, 10)),
    MIXOLYDIAN(listOf(0, 2, 4, 5, 7, 9, 10)),
}
