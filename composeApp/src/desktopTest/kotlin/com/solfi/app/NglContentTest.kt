package com.solfi.app

import com.solfi.app.content.ContentJson
import com.solfi.app.content.EnsemblePart
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Validates `ngl.json`: every part parses, fills its measures and lines up with the rest of the ensemble. */
class NglContentTest {
    private val file = listOf(
        "src/commonMain/composeResources/files/content/ngl.json",
        "composeApp/src/commonMain/composeResources/files/content/ngl.json",
    ).map(::File).first { it.isFile }

    private val pieces = ContentJson.decodeEnsembleBook(file.readText()).pieces

    @Test fun introHasAllTenParts() {
        val intro = pieces.single { it.id == "intro" }
        assertEquals(listOf("TRUMPET", "TROMBONE", "TUBA", EnsemblePart.GLOCKENSPIEL), intro.instruments)
        assertEquals(listOf(1, 2, 3), intro.partsOf("TRUMPET").map { it.voice })
        assertEquals(listOf(1, 2, 3), intro.partsOf("TROMBONE").map { it.voice })
        assertEquals(listOf(1), intro.partsOf("TUBA").map { it.voice })
        assertEquals(listOf(1, 2, 3), intro.partsOf(EnsemblePart.GLOCKENSPIEL).map { it.voice })
        assertEquals(10, intro.parts.size)
    }

    @Test fun everyPartParsesAndLinesUp() {
        for (piece in pieces) {
            assertTrue(piece.parts.map { it.id }.toSet().size == piece.parts.size, "${piece.id}: duplicate part ids")
            val lengths = piece.parts.map { p ->
                val m = p.written(piece)
                for ((i, bar) in m.measures.withIndex()) assertEquals(m.time.measureTicks, bar.sumOf { it.dur.ticks }, "${piece.id}/${p.id} measure ${i + 1}")
                assertTrue(m.soundNotes().isNotEmpty(), "${piece.id}/${p.id} has no notes")
                m.totalTicks
            }
            assertEquals(1, lengths.toSet().size, "${piece.id}: parts have different lengths $lengths")
        }
    }

    @Test fun nglTabOnlyAtNglAddresses() {
        for (p in listOf("/solfi/app/ngl", "/solfi/app/ngl/", "/piccoloreader/app/ngl", "/app/NGL/")) assertTrue(com.solfi.app.platform.isNglPath(p), p)
        for (p in listOf("/solfi/app/", "/solfi/app/index.html", "/ngl/app/", "/solfi/app/angl")) assertTrue(!com.solfi.app.platform.isNglPath(p), p)
    }

    @Test fun transposingPartsSoundTogether() {
        // bar 1: trumpet written D4 (B-flat, sounds C4), trombone 1 C4, tuba C2, glockenspiel written C4 (sounds C6)
        val intro = pieces.single { it.id == "intro" }
        fun firstConcert(id: String) = intro.parts.single { it.id == id }.concert(intro).soundNotes().first().pitches.first().midi % 12
        val notes = listOf("p1", "p4", "p7", "p8").map(::firstConcert)
        assertEquals(setOf(0), notes.toSet(), "all parts start on a concert C: $notes")
        val glock = intro.parts.single { it.id == "p8" }
        assertEquals(84, glock.concert(intro).soundNotes().first().pitches.first().midi)
        assertTrue(glock.tuning(intro).octaveFree)
        assertEquals(-2, intro.parts.single { it.id == "p1" }.tuning(intro).transpose)
    }
}
