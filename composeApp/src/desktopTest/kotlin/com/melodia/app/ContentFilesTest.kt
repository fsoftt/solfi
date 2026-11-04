package com.melodia.app

import com.melodia.app.content.*
import com.melodia.app.music.Melody
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Validates every `levelN.json` that is present in the content directory. */
class ContentFilesTest {
    private val dir: File = listOf(
        "src/commonMain/composeResources/files/content",
        "composeApp/src/commonMain/composeResources/files/content",
    ).map { File(it) }.first { it.isDirectory }

    private val files = dir.listFiles { f -> Regex("level[1-5]\\.json").matches(f.name) }!!.sortedBy { it.name }

    @Test fun levelOneExists() {
        assertTrue(files.any { it.name == "level1.json" }, "level1.json must exist")
    }

    @Test fun everyPresentLevelFileIsValid() {
        for (f in files) {
            val level = ContentJson.decodeLevel(f.readText())
            assertEquals(f.name, "level${level.level}.json", "file name must match level number")
            val errors = ContentValidator.validate(level)
            assertTrue(errors.isEmpty(), "${f.name}:\n" + errors.joinToString("\n"))
        }
    }

    @Test fun everyMelodyParsesAndFillsMeasuresExactly() {
        for (f in files) {
            val level = ContentJson.decodeLevel(f.readText())
            val melodies = ContentValidator.allMelodies(level)
            assertTrue(melodies.isNotEmpty())
            for (spec in melodies) {
                val m = Melody.parse(spec.clef, spec.time, spec.key, spec.tempo, spec.notes)
                for ((i, measure) in m.measures.withIndex()) {
                    assertEquals(m.time.measureTicks, measure.sumOf { it.dur.ticks }, "${f.name}: '${spec.notes}' measure ${i + 1}")
                }
            }
        }
    }

    @Test fun levelOneStructure() {
        val level = ContentJson.decodeLevel(File(dir, "level1.json").readText())
        assertEquals(1, level.level)
        assertTrue(level.lessons.size in 6..8)
        assertTrue(level.lessons.last().isExam)
        assertTrue(level.solfeos.size >= 4)
        // all step types are exercised
        val kinds = level.lessons.flatMap { it.steps }.map { it::class.simpleName }.toSet()
        assertEquals(setOf("Explain", "Listen", "Quiz", "IdentifyNote", "EarTraining", "Piano", "RhythmTap"), kinds)
    }

    @Test fun validatorCatchesProblems() {
        val level = ContentJson.decodeLevel(File(dir, "level1.json").readText())
        val bad = level.copy(
            lessons = level.lessons.take(3).map { it.copy(steps = it.steps.take(2)) },
            solfeos = level.solfeos.take(1),
            color = "red",
        )
        val errors = ContentValidator.validate(bad)
        assertTrue(errors.any { "6..8 lessons" in it })
        assertTrue(errors.any { "3..6 steps" in it })
        assertTrue(errors.any { "last lesson must have isExam" in it })
        assertTrue(errors.any { "at least 4 solfeos" in it })
        assertTrue(errors.any { "#RRGGBB" in it })
    }

    @Test fun validatorCatchesBadAnswerAndEmptyLanguage() {
        val blank = L10n("x", "", "z")
        val step = Step.Quiz(question = blank, options = listOf(Option(text = L10n("a", "a", "a")), Option(text = L10n("b", "b", "b"))), answer = 5, explanation = L10n("e", "e", "e"))
        val level = ContentJson.decodeLevel(File(dir, "level1.json").readText())
        val lessons = level.lessons.toMutableList()
        lessons[0] = lessons[0].copy(steps = listOf(step, step, step))
        val errors = ContentValidator.validate(level.copy(lessons = lessons))
        assertTrue(errors.any { "answer index 5 invalid" in it })
        assertTrue(errors.any { "question.en is blank" in it })
    }
}
