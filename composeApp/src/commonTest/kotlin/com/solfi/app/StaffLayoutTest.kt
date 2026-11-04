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
}
