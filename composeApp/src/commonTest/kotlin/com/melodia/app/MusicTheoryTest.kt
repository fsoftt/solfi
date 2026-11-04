package com.melodia.app

import com.melodia.app.music.*
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MusicTheoryTest {
    private fun p(s: String) = Pitch.parse(s)

    @Test fun parsesPitches() {
        assertEquals(Pitch(0, 0, 4), p("C4"))
        assertEquals(Pitch(3, 1, 4), p("F#4"))
        assertEquals(Pitch(6, -1, 3), p("Bb3"))
        assertNull(Pitch.parseOrNull("H4"))
        assertNull(Pitch.parseOrNull("C"))
        assertEquals("F#4", p("F#4").toString())
        assertEquals("Bb3", p("Bb3").toString())
    }

    @Test fun midiNumbers() {
        assertEquals(60, p("C4").midi)
        assertEquals(69, p("A4").midi)
        assertEquals(61, p("C#4").midi)
        assertEquals(61, p("Db4").midi)
        assertEquals(21, p("A0").midi)
        assertEquals(p("C#4"), Pitch.fromMidi(61))
        assertEquals(p("Db4"), Pitch.fromMidi(61, preferFlats = true))
    }

    @Test fun frequencies() {
        assertTrue(abs(p("A4").frequency - 440.0) < 1e-9)
        assertTrue(abs(p("C4").frequency - 261.6256) < 0.001)
        assertTrue(abs(p("A5").frequency - 880.0) < 1e-9)
        assertTrue(abs(p("A3").frequency - 220.0) < 1e-9)
    }

    @Test fun namesInBothSystems() {
        assertEquals("Do", NoteNames.short(p("C4"), NoteNaming.SOLFEGE))
        assertEquals("Sol", NoteNames.short(p("G4"), NoteNaming.SOLFEGE))
        assertEquals("Si", NoteNames.short(p("B4"), NoteNaming.SOLFEGE))
        assertEquals("C", NoteNames.short(p("C4"), NoteNaming.LETTERS))
        assertEquals("B", NoteNames.short(p("B4"), NoteNaming.LETTERS))
        assertEquals("Fa♯4", NoteNames.short(p("F#4"), NoteNaming.SOLFEGE, withOctave = true))
        assertEquals("B♭", NoteNames.short(p("Bb3"), NoteNaming.LETTERS))
        assertEquals("Do sostenido", NoteNames.long(p("C#4"), NoteNaming.SOLFEGE, Lang.ES))
        assertEquals("C sharp", NoteNames.long(p("C#4"), NoteNaming.LETTERS, Lang.EN))
        assertEquals("Si bemol", NoteNames.long(p("Bb3"), NoteNaming.SOLFEGE, Lang.ES))
        assertEquals("Si bemol", NoteNames.long(p("Bb3"), NoteNaming.SOLFEGE, Lang.PT))
        assertEquals("Sol sustenido", NoteNames.long(p("G#4"), NoteNaming.SOLFEGE, Lang.PT))
        assertEquals("Dó", NoteNames.short(p("C4"), NoteNaming.SOLFEGE, lang = Lang.PT))
    }

    @Test fun defaultNamingFollowsLanguage() {
        assertEquals(NoteNaming.SOLFEGE, NoteNaming.defaultFor(Lang.ES))
        assertEquals(NoteNaming.SOLFEGE, NoteNaming.defaultFor(Lang.PT))
        assertEquals(NoteNaming.LETTERS, NoteNaming.defaultFor(Lang.EN))
    }

    @Test fun intervals() {
        assertEquals(Interval(5, IntervalQuality.PERFECT), Interval.between(p("C4"), p("G4")))
        assertEquals(Interval(3, IntervalQuality.MAJOR), Interval.between(p("C4"), p("E4")))
        assertEquals(Interval(3, IntervalQuality.MINOR), Interval.between(p("E4"), p("G4")))
        assertEquals(Interval(1, IntervalQuality.PERFECT), Interval.between(p("C4"), p("C4")))
        assertEquals(Interval(8, IntervalQuality.PERFECT), Interval.between(p("C4"), p("C5")))
        assertEquals(Interval(4, IntervalQuality.AUGMENTED), Interval.between(p("F4"), p("B4")))
        assertEquals(Interval(5, IntervalQuality.DIMINISHED), Interval.between(p("B4"), p("F5")))
        assertEquals(Interval(2, IntervalQuality.MAJOR), Interval.between(p("D4"), p("C4")))
        assertEquals(Interval(7, IntervalQuality.MINOR), Interval.between(p("D4"), p("C5")))
        assertEquals("M6", Interval.between(p("C4"), p("A4")).symbol)
    }

    @Test fun scales() {
        assertEquals(listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5"), Scales.build(p("C4")).map { it.toString() })
        assertEquals(listOf("G4", "A4", "B4", "C5", "D5", "E5", "F#5", "G5"), Scales.build(p("G4")).map { it.toString() })
        assertEquals(listOf("F4", "G4", "A4", "Bb4", "C5", "D5", "E5", "F5"), Scales.build(p("F4")).map { it.toString() })
        assertEquals(listOf("A4", "B4", "C5", "D5", "E5", "F5", "G5", "A5"), Scales.build(p("A4"), ScaleType.NATURAL_MINOR).map { it.toString() })
        assertEquals("G#5", Scales.build(p("A4"), ScaleType.HARMONIC_MINOR)[6].toString())
    }

    @Test fun keySignatures() {
        assertEquals(0, KeySignature.parse("C").fifths)
        assertEquals(1, KeySignature.parse("G").fifths)
        assertEquals(2, KeySignature.parse("D").fifths)
        assertEquals(-1, KeySignature.parse("F").fifths)
        assertEquals(-2, KeySignature.parse("Bb").fifths)
        assertEquals(0, KeySignature.parse("Am").fifths)
        assertEquals(1, KeySignature.parse("Em").fifths)
        assertEquals(-1, KeySignature.parse("Dm").fifths)
        assertEquals(1, KeySignature.parse("G").alterFor(3))
        assertEquals(0, KeySignature.parse("G").alterFor(0))
        assertEquals(-1, KeySignature.parse("F").alterFor(6))
        assertEquals(listOf(3, 0), KeySignature.parse("D").accidentals())
        assertFailsWith<IllegalArgumentException> { KeySignature.parse("H") }
    }

    @Test fun chords() {
        fun names(root: String, q: ChordQuality) = Chords.build(p(root), q).map { it.toString() }
        assertEquals(listOf("C4", "E4", "G4"), names("C4", ChordQuality.MAJOR))
        assertEquals(listOf("A4", "C5", "E5"), names("A4", ChordQuality.MINOR))
        assertEquals(listOf("B4", "D5", "F5"), names("B4", ChordQuality.DIMINISHED))
        assertEquals(listOf("C4", "E4", "G#4"), names("C4", ChordQuality.AUGMENTED))
        assertEquals(listOf("G4", "B4", "D5", "F5"), names("G4", ChordQuality.DOMINANT7))
        assertEquals(listOf("C4", "E4", "G4", "B4"), names("C4", ChordQuality.MAJOR7))
        assertEquals(listOf("D4", "F4", "A4", "C5"), names("D4", ChordQuality.MINOR7))
        assertEquals(listOf("F#4", "A#4", "C#5"), names("F#4", ChordQuality.MAJOR))
    }

    @Test fun durationMath() {
        assertEquals(24, Dur.parse("q").ticks)
        assertEquals(36, Dur.parse("q.").ticks)
        assertEquals(96, Dur.parse("w").ticks)
        assertEquals(72, Dur.parse("h.").ticks)
        assertEquals(6, Dur.parse("16").ticks)
        assertEquals(9, Dur.parse("16.").ticks)
        assertEquals(18, Dur.parse("8.").ticks)
        assertEquals(1.5, Dur.parse("q.").beats)
        assertEquals(96, TimeSignature.parse("4/4").measureTicks)
        assertEquals(72, TimeSignature.parse("3/4").measureTicks)
        assertEquals(72, TimeSignature.parse("6/8").measureTicks)
        assertEquals(36, TimeSignature.parse("6/8").clickTicks)
    }

    @Test fun parsesMelodyAndValidatesMeasures() {
        val m = Melody.parse("treble", "4/4", "C", 80, "C4:q D4:q E4:h | r:q G4:8 G4:8 A4:h")
        assertEquals(2, m.measures.size)
        assertEquals(7, m.events.size)
        assertEquals(192, m.totalTicks)
        assertTrue(m.events[3].isRest)
        assertEquals(8.0, m.totalBeats)
        assertFailsWith<MelodyParseException> { Melody.parse("treble", "4/4", "C", 80, "C4:q D4:q E4:h | C4:q") }
        assertFailsWith<MelodyParseException> { Melody.parse("treble", "4/4", "C", 80, "C4:q D4:q E4:h C4:q") }
        assertFailsWith<MelodyParseException> { Melody.parse("treble", "4/4", "C", 80, "C4 D4:q E4:h") }
    }

    @Test fun melodyChordsTiesAndDots() {
        val m = Melody.parse("treble", "3/4", "C", 90, "[C4 E4 G4]:h.| C4:h ~ C4:q | D4:q. E4:8 F4:q")
        assertEquals(3, m.measures.size)
        assertEquals(3, m.events[0].pitches.size)
        val sound = m.soundNotes()
        // chord, C4 tied (h+q), D4., E4, F4
        assertEquals(5, sound.size)
        assertEquals(72, sound[1].durTicks)
        assertEquals(listOf(0.0, 3.0, 6.0, 7.5, 8.0), sound.map { it.startBeat })
        val suffixTie = Melody.parse("treble", "4/4", "C", 90, "C4:h~ C4:h")
        assertEquals(1, suffixTie.soundNotes().size)
        assertFailsWith<MelodyParseException> { Melody.parse("treble", "4/4", "C", 90, "C4:h~ D4:h") }
        assertFailsWith<MelodyParseException> { Melody.parse("treble", "4/4", "C", 90, "Z4:w") }
    }

    @Test fun rhythmOnlyNotation() {
        val m = Melody.parse("treble", "4/4", "C", 80, "x:q x:q x:h")
        assertEquals(listOf(0.0, 1.0, 2.0), m.onsetBeats())
    }

    @Test fun eventAtBeat() {
        val m = Melody.parse("treble", "4/4", "C", 80, "C4:q D4:q E4:h")
        assertEquals(0, m.eventAtBeat(0.0)?.index)
        assertEquals(1, m.eventAtBeat(1.5)?.index)
        assertEquals(2, m.eventAtBeat(3.9)?.index)
        assertNull(m.eventAtBeat(4.0))
        assertNull(m.eventAtBeat(-1.0))
    }
}
