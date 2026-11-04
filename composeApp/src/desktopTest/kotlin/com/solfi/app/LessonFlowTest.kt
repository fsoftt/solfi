package com.solfi.app

import com.solfi.app.audio.AudioEngine
import com.solfi.app.content.*
import com.solfi.app.data.Progress
import com.solfi.app.data.ProfileStore
import com.solfi.app.logic.LessonSession
import com.solfi.app.logic.StepOutcome
import com.solfi.app.music.Lang
import com.solfi.app.music.NoteNaming
import com.solfi.app.platform.Haptics
import com.solfi.app.platform.KeyValueStore
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** App-level guarantees: locked routes are refused and only a finished session writes progress. */
class LessonFlowTest {
    private fun t(s: String) = L10n(s, s, s)
    private val quiz = Step.Quiz(t("q"), listOf(Option(text = t("a")), Option(text = t("b"))), 0, t("e"))
    private fun lesson(id: String, exam: Boolean = false) = Lesson(id, t(id), isExam = exam, steps = listOf(quiz, quiz))
    private fun level(n: Int) = LevelContent(n, t("L"), t("d"), "#112233", listOf(lesson("a"), lesson("b", exam = true)), emptyList(), BadgeSpec("b$n", t("b"), t("b")))

    private val kv = KeyValueStore("solfi-flow-test")
    private val app = AppState(ProfileStore(kv), AudioEngine(null), Haptics(), "es").also { a ->
        a.useLevels(listOf(level(1), level(2), null, null, null))
        a.createProfile("Test", "x", kid = true, lang = Lang.ES, naming = NoteNaming.SOLFEGE)
    }

    @AfterTest fun cleanup() { app.profiles.forEach { app.deleteProfile(it.id) }; kv.remove("profiles"); kv.remove("currentProfile") }

    private fun finished(levelIndex: Int, lessonIndex: Int, outcome: StepOutcome = StepOutcome.CORRECT): LessonSession {
        val s = LessonSession(app.levels[levelIndex]!!.lessons[lessonIndex])
        while (!s.isFinished) s.submit(outcome)
        return s
    }

    @Test fun lockedLessonRoutesAreRefused() {
        assertFalse(app.push(Route.Lesson(0, 1)))
        assertFalse(app.push(Route.Lesson(1, 0)))
        assertFalse(app.push(Route.Lesson(4, 0)))
        assertTrue(app.stack.isEmpty())
        assertTrue(app.push(Route.Lesson(0, 0)))
        assertEquals(1, app.stack.size)
    }

    @Test fun abandonedSessionNeverCompletesAnything() {
        val s = LessonSession(app.levels[0]!!.lessons[0])
        s.submit(StepOutcome.CORRECT)               // leave midway
        assertFalse(s.isFinished)
        assertNull(app.completeLesson(0, 0, s))
        assertEquals(Progress(), app.progress)
    }

    @Test fun finishedSessionWritesProgressOnlyForUnlockedLesson() {
        assertNull(app.completeLesson(0, 1, finished(0, 1)), "locked lesson cannot be completed")
        assertTrue(app.progress.lessons.isEmpty())
        val r = app.completeLesson(0, 0, finished(0, 0))
        assertNotNull(r); assertEquals(3, r.stars)
        assertEquals(1, app.progress.lessons.size)
        // a session of another lesson cannot complete this one
        assertNull(app.completeLesson(0, 1, finished(0, 0)))
    }

    @Test fun examFailKeepsNextLevelLockedAndPassUnlocks() {
        app.completeLesson(0, 0, finished(0, 0))
        val bad = app.completeLesson(0, 1, finished(0, 1, StepOutcome.WRONG))
        assertNotNull(bad); assertFalse(bad.passed)
        assertFalse(app.push(Route.Lesson(1, 0)))
        val good = app.completeLesson(0, 1, finished(0, 1))
        assertNotNull(good); assertTrue(good.passed); assertEquals(2, good.unlockedLevel)
        assertTrue(app.push(Route.Lesson(1, 0)))
    }
}
