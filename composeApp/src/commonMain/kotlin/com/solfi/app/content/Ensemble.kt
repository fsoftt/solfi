package com.solfi.app.content

import com.solfi.app.music.Instrument
import com.solfi.app.music.InstrumentTuning
import com.solfi.app.music.Melody
import com.solfi.app.music.Pitch
import kotlinx.serialization.Serializable

/**
 * NGL book (`files/content/ngl.json`): ensemble pieces converted from MusicXML with `tools/musicxml_to_solfi.py`.
 * Each part is one practisable voice, written exactly as the player reads it.
 */
@Serializable
data class EnsembleBook(val pieces: List<EnsemblePiece>)

@Serializable
data class EnsemblePiece(
    val id: String,
    val title: String,
    val time: String = "4/4",
    /** Quarter notes per minute. */
    val tempo: Int = 80,
    val parts: List<EnsemblePart>,
) {
    /** Instrument codes in score order, each once. */
    val instruments: List<String> get() = parts.map { it.instrument }.distinct()

    fun partsOf(instrument: String): List<EnsemblePart> = parts.filter { it.instrument == instrument }.sortedBy { it.voice }
}

@Serializable
data class EnsemblePart(
    val id: String,
    /** Part name as printed in the score, e.g. "Trompeta en Sib 1". */
    val name: String,
    /** A [Instrument] name or an ensemble-only instrument such as [GLOCKENSPIEL]. */
    val instrument: String,
    val voice: Int = 1,
    val clef: String = "treble",
    val key: String = "C",
    /** Semitones from written to sounding pitch (B♭ trumpet −2, glockenspiel +24). */
    val transpose: Int = 0,
    val notes: String,
) {
    /** What the player reads. */
    fun written(piece: EnsemblePiece): Melody = Melody.parse(clef, piece.time, key, piece.tempo, notes)

    /** What the part sounds like (for playback). */
    fun concert(piece: EnsemblePiece): Melody = written(piece).transposed(transpose)

    /** The profile instrument this part is played on, or null for ensemble-only instruments. */
    val profileInstrument: Instrument? get() = Instrument.entries.firstOrNull { it.name == instrument }

    /**
     * How the microphone judges this part: the score's own transposition, and the part's written range so no note
     * counts as "out of range". Mallet percussion (rich overtones) accepts any octave.
     */
    fun tuning(piece: EnsemblePiece): InstrumentTuning {
        val pitches = written(piece).events.flatMap { it.pitches }.map { it.midi }
        val base = profileInstrument?.let { InstrumentTuning.of(it) }
        return InstrumentTuning(
            instrument = profileInstrument ?: Instrument.OTHER,
            transpose = transpose,
            lowWritten = pitches.minOrNull() ?: base?.lowWritten ?: 48,
            highWritten = pitches.maxOrNull() ?: base?.highWritten ?: 84,
            octaveFree = instrument == GLOCKENSPIEL || base?.octaveFree == true,
        )
    }

    companion object {
        const val GLOCKENSPIEL = "GLOCKENSPIEL"
    }
}

/** The same rhythm moved by [semitones] (spelled with flats when going down by a flat interval). */
fun Melody.transposed(semitones: Int): Melody {
    if (semitones == 0) return this
    val flats = semitones % 12 != 0 && semitones < 0
    fun move(p: Pitch) = if (semitones % 12 == 0) p.copy(octave = p.octave + semitones / 12) else Pitch.fromMidi(p.midi + semitones, preferFlats = flats || p.alter < 0)
    val ms = measures.map { bar -> bar.map { e -> e.copy(pitches = e.pitches.map(::move)) } }
    return Melody(clef, time, key, tempo, ms)
}
