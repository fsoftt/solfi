package com.solfi.app

import com.solfi.app.content.ContentJson
import com.solfi.app.content.ContentValidator
import com.solfi.app.content.Step
import com.solfi.app.data.ProfileSettings
import com.solfi.app.music.Clef
import com.solfi.app.music.ClefPolicy
import com.solfi.app.music.ClefPreference
import com.solfi.app.music.ClefShift
import com.solfi.app.music.Instrument
import com.solfi.app.music.Melody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ClefPolicyTest {
    private fun mel(clef: String, notes: String) = Melody.parse(clef, "4/4", "C", 80, notes)

    private fun ledgers(m: Melody) = ClefShift.allPitches(m).sumOf { ClefShift.ledgerLines(it.diatonic, m.clef) }

    @Test fun trebleMelodyShiftsDownIntoBassWithMinimalLedgerLines() {
        val treble = mel("treble", "C5:q E5:q G5:q C6:q")
        val bass = ClefShift.toClef(treble, Clef.BASS)
        assertEquals(Clef.BASS, bass.clef)
        // brute force: no other whole-octave shift has fewer ledger lines
        val best = (-6..6).minOf { k -> ClefShift.allPitches(treble).sumOf { ClefShift.ledgerLines(ClefShift.shiftPitch(it, k).diatonic, Clef.BASS) } }
        assertEquals(best, ledgers(bass))
        assertTrue(ledgers(bass) <= 1)
        // shifted by whole octaves only
        val k = ClefShift.octaveShift(ClefShift.allPitches(treble), Clef.BASS)
        assertTrue(k < 0)
        assertEquals(ClefShift.allPitches(treble).map { it.midi + 12 * k }, ClefShift.allPitches(bass).map { it.midi })
    }

    @Test fun bassMelodyShiftsUpIntoTrebleAndBack() {
        val bass = mel("bass", "C3:q E3:q G3:q C4:q")
        val treble = ClefShift.toClef(bass, Clef.TREBLE)
        assertEquals(Clef.TREBLE, treble.clef)
        assertTrue(ledgers(treble) <= 1)
        val back = ClefShift.toClef(treble, Clef.BASS)
        assertEquals(ledgers(bass), ledgers(back))
        assertEquals(ClefShift.allPitches(bass).map { it.midi % 12 }, ClefShift.allPitches(back).map { it.midi % 12 })
    }

    @Test fun noteNamesAreKeptAfterShift() {
        val src = mel("treble", "F#5:q Bb4:q [C5 E5]:q r:q")
        val out = ClefShift.toClef(src, Clef.BASS)
        val a = src.events.map { e -> e.pitches.map { it.step to it.alter } }
        val b = out.events.map { e -> e.pitches.map { it.step to it.alter } }
        assertEquals(a, b)
        assertEquals(src.events.map { it.dur }, out.events.map { it.dur })
        assertEquals(src.totalTicks, out.totalTicks)
        assertEquals(src.measures.size, out.measures.size)
    }

    @Test fun sameClefIsUntouched() {
        val m = mel("treble", "C4:q D4:q E4:q F4:q")
        assertSame(m, ClefShift.toClef(m, Clef.TREBLE))
    }

    @Test fun tiesStayValidAfterShift() {
        val m = mel("treble", "C5:h~ C5:h | D5:w")
        val out = ClefShift.toClef(m, Clef.BASS)
        assertEquals(1, out.soundNotes().count { it.durTicks == 96 && it.pitches.first().step == 0 })
    }

    @Test fun recommendedResolutionPerInstrument() {
        for (i in listOf(Instrument.CELLO, Instrument.BASS, Instrument.BASSOON, Instrument.TROMBONE, Instrument.TUBA)) assertEquals(Clef.BASS, ClefPolicy.recommended(i), "$i")
        for (i in listOf(Instrument.VOICE, Instrument.GUITAR, Instrument.VIOLIN, Instrument.FLUTE)) assertEquals(Clef.TREBLE, ClefPolicy.recommended(i), "$i")
        assertEquals(null, ClefPolicy.recommended(Instrument.PIANO))
        assertEquals(null, ClefPolicy.recommended(Instrument.OTHER))
    }

    @Test fun explicitPreferenceBeatsInstrument() {
        assertEquals(Clef.TREBLE, ClefPolicy.effective(ClefPreference.TREBLE, Instrument.TUBA))
        assertEquals(Clef.BASS, ClefPolicy.effective(ClefPreference.BASS, Instrument.VOICE))
        assertEquals(Clef.BASS, ClefPolicy.effective(ClefPreference.RECOMMENDED, Instrument.CELLO))
        assertEquals(null, ClefPolicy.effective(ClefPreference.RECOMMENDED, Instrument.PIANO))
    }

    @Test fun lockedContentIgnoresOverride() {
        assertEquals(Clef.TREBLE, ClefPolicy.resolve(Clef.TREBLE, Clef.BASS, locked = true))
        assertEquals(Clef.BASS, ClefPolicy.resolve(Clef.TREBLE, Clef.BASS, locked = false))
        assertEquals(Clef.TREBLE, ClefPolicy.resolve(Clef.TREBLE, null, locked = false))
    }

    @Test fun settingsDefaults() {
        val s = ProfileSettings()
        assertEquals(ClefPreference.RECOMMENDED, ClefPreference.fromName(s.clefPreference))
        assertEquals(Instrument.OTHER, Instrument.fromName(s.instrument))
        assertEquals(Instrument.OTHER, Instrument.fromName("nonsense"))
    }

    @Test fun validatorAcceptsClefLocked() {
        val json = """
        {"level":1,"title":{"es":"a","en":"a","pt":"a"},"description":{"es":"a","en":"a","pt":"a"},"color":"#112233",
         "lessons":[
          ${(1..7).joinToString(",") { lesson("l$it", it == 1, it == 7) }}
         ],
         "solfeos":[${(1..4).joinToString(",") { """{"id":"s$it","title":{"es":"a","en":"a","pt":"a"},"description":{"es":"a","en":"a","pt":"a"},"melody":{"notes":"C4:w"}}""" }}],
         "badge":{"id":"b","name":{"es":"a","en":"a","pt":"a"},"description":{"es":"a","en":"a","pt":"a"}}}
        """.trimIndent()
        val level = ContentJson.decodeLevel(json)
        assertTrue(level.lessons[0].clefLocked)
        assertTrue(level.lessons[0].steps[0].clefLocked)
        assertTrue(!level.lessons[1].clefLocked)
        assertEquals(emptyList(), ContentValidator.validate(level))
    }

    private fun lesson(id: String, locked: Boolean, exam: Boolean): String {
        val l10 = """{"es":"a","en":"a","pt":"a"}"""
        val explain = """{"type":"explain","text":$l10${if (locked) ",\"clefLocked\":true" else ""}}"""
        val quiz = """{"type":"quiz","question":$l10,"options":[{"text":$l10},{"text":$l10}],"answer":0,"explanation":$l10}"""
        return """{"id":"$id","title":$l10,${if (locked) "\"clefLocked\":true," else ""}"isExam":$exam,"steps":[$explain,$quiz,$quiz,$quiz]}"""
    }
}
