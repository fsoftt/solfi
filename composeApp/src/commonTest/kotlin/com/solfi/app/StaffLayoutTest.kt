package com.solfi.app

import com.solfi.app.music.Melody
import com.solfi.app.ui.notation.StaffLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StaffLayoutTest {
    private val long = Melody.parse("treble", "4/4", "C", 80, "C4:q D4:q E4:q F4:q | G4:q F4:q E4:q D4:q | C4:q D4:q E4:q F4:q | G4:w")

    @Test fun wrapsIntoSeveralSystemsOnNarrowWidth() {
        val narrow = StaffLayout(long, 300f, 12f)
        assertTrue(narrow.systems.size >= 2)
        val wide = StaffLayout(long, 3000f, 12f)
        assertEquals(1, wide.systems.size)
        assertEquals(4, narrow.systems.sumOf { it.measures.size })
    }

    @Test fun everyEventIsPlacedInsideTheWidthInOrder() {
        val l = StaffLayout(long, 400f, 11f)
        var last = -1
        l.systems.forEach { s ->
            s.measures.forEach { m ->
                m.glyphs.forEach { g ->
                    assertTrue(g.x in 0f..400f, "x=${g.x}")
                    assertTrue(g.event.index > last)
                    last = g.event.index
                }
            }
        }
        assertEquals(long.events.size - 1, last)
    }

    @Test fun staffPositionsAndStems() {
        val m = Melody.parse("treble", "4/4", "C", 80, "C4:q E4:q B4:q C5:q")
        val l = StaffLayout(m, 600f, 10f)
        val g = l.systems[0].measures[0].glyphs
        assertEquals(-2, g[0].heads[0].pos) // C4: first ledger line below
        assertEquals(0, g[1].heads[0].pos)  // E4: bottom line
        assertEquals(4, g[2].heads[0].pos)  // B4: middle line
        assertTrue(g[0].stemUp)
        assertTrue(!g[2].stemUp)
    }

    @Test fun accidentalsFollowKeySignatureAndMeasure() {
        val m = Melody.parse("treble", "4/4", "G", 80, "F#4:q F4:q F4:q G4:q")
        val g = StaffLayout(m, 600f, 10f).systems[0].measures[0].glyphs
        assertEquals(null, g[0].heads[0].accidental)   // F# is in the key signature
        assertEquals(0, g[1].heads[0].accidental)      // F natural needs a natural sign
        assertEquals(null, g[2].heads[0].accidental)   // already natural in this measure
    }

    @Test fun beamsGroupEighthsWithinABeat() {
        val m = Melody.parse("treble", "4/4", "C", 80, "C4:8 D4:8 E4:8 F4:8 G4:q G4:q")
        val beams = StaffLayout(m, 600f, 10f).systems[0].measures[0].beams
        assertEquals(2, beams.size)
        assertEquals(2, beams[0].glyphs.size)
    }

    @Test fun hitTestFindsNearestNote() {
        val l = StaffLayout(long, 600f, 10f)
        val first = l.systems[0].measures[0].glyphs[0]
        val hit = l.hitTest(first.x + 2f, l.systems[0].staffTop + 10f)
        assertNotNull(hit)
        assertEquals(0, hit.index)
    }

    @Test fun oneMeasureLayoutPerBarWithContiguousBarlines() {
        for (width in listOf(300f, 400f, 900f, 3000f)) {
            val l = StaffLayout(long, width, 12f)
            assertEquals(long.measures.size, l.systems.sumOf { it.measures.size }, "one barline per bar at width $width")
            l.systems.forEach { s ->
                s.measures.zipWithNext { a, b -> assertEquals(a.endX, b.startX, 0.01f) }
                assertEquals(s.endX, s.measures.last().endX, 0.01f)
                s.measures.forEach { m -> assertTrue(m.endX > m.startX); assertTrue(m.glyphs.all { it.x in m.startX..m.endX }) }
            }
        }
        // two bars forced onto one system keep a barline between them
        val two = StaffLayout(Melody.parse("treble", "4/4", "C", 80, "C4:h D4:h | E4:h F4:h"), 2000f, 10f)
        assertEquals(1, two.systems.size)
        assertEquals(2, two.systems[0].measures.size)
    }

    private fun segs(notes: String, time: String = "4/4") =
        StaffLayout(Melody.parse("treble", time, "C", 80, notes), 900f, 10f).systems[0].measures[0].beams

    @Test fun sixteenthsShareASecondBeam() {
        val b = segs("C4:16 D4:16 E4:16 F4:16 G4:q G4:h")
        assertEquals(1, b.size)
        assertEquals(listOf(1 to (0 to 3), 2 to (0 to 3)), b[0].segments.map { it.level to (it.from to it.to) })
    }

    @Test fun dottedEighthSixteenthHasFullBeamAndBackwardStub() {
        val b = segs("C4:8. D4:16 E4:q G4:h")
        val s = b[0].segments
        assertEquals(2, s.size)
        assertEquals(1, s[0].level); assertEquals(0, s[0].from); assertEquals(1, s[0].to)
        assertTrue(s[1].isStub && s[1].from == 1 && s[1].stubDir == -1)
    }

    @Test fun sixteenthThenDottedEighthStubPointsForward() {
        val s = segs("C4:16 D4:8. E4:q G4:h")[0].segments
        assertTrue(s[1].isStub && s[1].from == 0 && s[1].stubDir == 1)
    }

    @Test fun eighthSixteenthSixteenthHasPartialSecondBeamOnTheSixteenths() {
        val s = segs("C4:8 D4:16 E4:16 G4:q G4:h")[0].segments
        assertEquals(listOf(1 to (0 to 2), 2 to (1 to 2)), s.map { it.level to (it.from to it.to) })
    }

    @Test fun beamedNotesAreMarkedSoNoFlagsAreDrawn() {
        val m = Melody.parse("treble", "4/4", "C", 80, "C4:16 D4:16 E4:8 G4:q G4:h")
        val l = StaffLayout(m, 900f, 10f).systems[0].measures[0]
        val beamedIds = l.beams.flatMap { it.glyphs }.map { it.event.index }.toSet()
        l.glyphs.forEach { if (it.event.index in beamedIds) assertTrue(it.beamId >= 0) else assertEquals(-1, it.beamId) }
        assertEquals(setOf(0, 1, 2), beamedIds)
    }
}
