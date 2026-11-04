package com.solfi.app

import com.solfi.app.content.*
import com.solfi.app.data.Progress
import com.solfi.app.logic.*
import com.solfi.app.logic.StepOutcome.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LessonSessionTest {
    private fun t(s: String) = L10n(s, s, s)
    private val mel = MelodySpec(notes = "C4:q D4:q")
    private val explainText = Step.Explain(text = t("x"))
    private val explainMel = Step.Explain(text = t("x"), melody = mel)
    private val listen = Step.Listen(t("p"), mel)
    private val quiz = Step.Quiz(t("q"), listOf(Option(text = t("a")), Option(text = t("b"))), 0, t("e"))
    private val ident = Step.IdentifyNote(note = "C4")
    private val ear = Step.EarTraining(t("p"), SoundSpec(listOf("C4")), listOf(Option(text = t("a")), Option(text = t("b"))), 0, t("e"))
    private val piano = Step.Piano(t("p"), listOf("C4"))
    private val rhythm = Step.RhythmTap(t("p"), mel)

    private fun lesson(vararg steps: Step, exam: Boolean = false) = Lesson("l", t("l"), isExam = exam, steps = steps.toList())

    // ---- gating per step type

    @Test fun explainWithMelodyNeedsFullPlayback() {
        assertFalse(StepCompletionPolicy.canContinue(explainMel, StepState()))
        assertFalse(StepCompletionPolicy.canContinue(explainMel, StepState(elapsedMs = 60_000)))
        assertEquals(StepHint.LISTEN_EXAMPLE, StepCompletionPolicy.hint(explainMel, StepState()))
        assertTrue(StepCompletionPolicy.canContinue(explainMel, StepState(playedToEnd = true)))
        assertEquals(StepHint.NONE, StepCompletionPolicy.hint(explainMel, StepState(playedToEnd = true)))
    }

    @Test fun explainTextOnlyNeedsMinimumDelay() {
        assertFalse(StepCompletionPolicy.canContinue(explainText, StepState(elapsedMs = 0)))
        assertFalse(StepCompletionPolicy.canContinue(explainText, StepState(elapsedMs = 1999)))
        assertTrue(StepCompletionPolicy.canContinue(explainText, StepState(elapsedMs = 2000)))
        assertEquals(StepHint.WAIT, StepCompletionPolicy.hint(explainText, StepState()))
    }

    @Test fun listenNeedsCompletePlayback() {
        assertFalse(StepCompletionPolicy.canContinue(listen, StepState(soundPlayed = true, elapsedMs = 99999)))
        assertEquals(StepHint.LISTEN_TO_END, StepCompletionPolicy.hint(listen, StepState()))
        assertTrue(StepCompletionPolicy.canContinue(listen, StepState(playedToEnd = true)))
    }

    @Test fun quizAndIdentifyNeedAnswerWithFeedback() {
        for (s in listOf(quiz, ident)) {
            assertFalse(StepCompletionPolicy.canContinue(s, StepState()))
            assertEquals(StepHint.ANSWER, StepCompletionPolicy.hint(s, StepState()))
            assertTrue(StepCompletionPolicy.canContinue(s, StepState(answered = true)))
        }
    }

    @Test fun earTrainingLocksAnswersUntilSoundPlayed() {
        assertFalse(StepCompletionPolicy.answersEnabled(ear, StepState()))
        assertEquals(StepHint.PLAY_SOUND_FIRST, StepCompletionPolicy.hint(ear, StepState()))
        assertTrue(StepCompletionPolicy.answersEnabled(ear, StepState(soundPlayed = true)))
        assertEquals(StepHint.ANSWER, StepCompletionPolicy.hint(ear, StepState(soundPlayed = true)))
        assertFalse(StepCompletionPolicy.canContinue(ear, StepState(soundPlayed = true)))
        assertTrue(StepCompletionPolicy.canContinue(ear, StepState(soundPlayed = true, answered = true)))
        assertTrue(StepCompletionPolicy.answersEnabled(quiz, StepState()))
    }

    @Test fun pianoNeedsTargetsPlayed() {
        assertFalse(StepCompletionPolicy.canContinue(piano, StepState()))
        assertEquals(StepHint.PLAY_NOTES, StepCompletionPolicy.hint(piano, StepState()))
        assertTrue(StepCompletionPolicy.canContinue(piano, StepState(pianoDone = true)))
    }

    @Test fun rhythmNeeds60PercentOrThreeFailures() {
        assertFalse(StepCompletionPolicy.rhythmPassed(rhythm, 0.59))
        assertTrue(StepCompletionPolicy.rhythmPassed(rhythm, 0.6))
        // content with a lower passScore cannot go under 60%
        assertFalse(StepCompletionPolicy.rhythmPassed(rhythm.copy(passScore = 0.5), 0.55))
        assertFalse(StepCompletionPolicy.canContinue(rhythm, StepState()))
        assertFalse(StepCompletionPolicy.canContinue(rhythm, StepState(rhythmFailedAttempts = 2)))
        assertTrue(StepCompletionPolicy.canContinue(rhythm, StepState(rhythmFailedAttempts = 3)))
        assertTrue(StepCompletionPolicy.canContinue(rhythm, StepState(rhythmPassed = true)))
        // giving up, or passing late, counts as failed; passing first time counts as correct
        assertEquals(WRONG, StepCompletionPolicy.rhythmOutcome(StepState(rhythmFailedAttempts = 3)))
        assertEquals(WRONG, StepCompletionPolicy.rhythmOutcome(StepState(rhythmPassed = true, rhythmFailedAttempts = 1)))
        assertEquals(CORRECT, StepCompletionPolicy.rhythmOutcome(StepState(rhythmPassed = true)))
    }

    // ---- re-queue and scoring

    @Test fun wrongAnswerIsRequeuedAtEndUntilCorrect() {
        val sess = LessonSession(lesson(explainText, quiz, ident))
        sess.submit(UNSCORED)
        assertEquals(1, sess.currentIndex)
        sess.submit(WRONG)                       // quiz wrong -> goes to the end
        assertEquals(2, sess.currentIndex)
        sess.submit(CORRECT)
        assertFalse(sess.isFinished)
        assertEquals(1, sess.currentIndex)       // quiz again
        assertTrue(sess.isRepeat(1))
        sess.submit(WRONG)                       // wrong again -> re-queued again
        assertEquals(1, sess.currentIndex)
        assertFalse(sess.isFinished)
        sess.submit(CORRECT)
        assertTrue(sess.isFinished)
        assertTrue(sess.passed)
        assertEquals(0.5, sess.accuracy)         // first attempt: 1 of 2 correct
    }

    @Test fun everyRequeuedTypeRepeats() {
        for (s in listOf(quiz, ident, ear, piano)) {
            val sess = LessonSession(lesson(s))
            sess.submit(WRONG)
            assertFalse(sess.isFinished, "${s::class.simpleName} must repeat")
        }
    }

    @Test fun rhythmWrongIsNotRequeued() {
        val sess = LessonSession(lesson(rhythm))
        sess.submit(WRONG)
        assertTrue(sess.isFinished)
        assertEquals(0.0, sess.accuracy)
    }

    @Test fun starsFromFirstAttemptAccuracy() {
        fun stars(correct: Int, total: Int): Int {
            val sess = LessonSession(lesson(*Array(total) { quiz }))
            repeat(total) { i -> sess.submit(if (i < correct) CORRECT else WRONG) }
            while (!sess.isFinished) sess.submit(CORRECT)   // retries never improve the score
            return sess.stars
        }
        assertEquals(3, stars(10, 10)); assertEquals(3, stars(9, 10))
        assertEquals(2, stars(8, 10)); assertEquals(2, stars(7, 10))
        assertEquals(1, stars(6, 10)); assertEquals(1, stars(0, 10))
    }

    @Test fun lessonWithoutScoredStepsHasFullAccuracy() {
        val sess = LessonSession(lesson(explainText, listen))
        sess.submit(UNSCORED); sess.submit(UNSCORED)
        assertTrue(sess.isFinished); assertEquals(1.0, sess.accuracy); assertEquals(3, sess.stars)
    }

    @Test fun outcomeMustFitStepType() {
        val sess = LessonSession(lesson(explainText))
        assertTrue(runCatching { sess.submit(CORRECT) }.isFailure)
        val s2 = LessonSession(lesson(quiz))
        assertTrue(runCatching { s2.submit(UNSCORED) }.isFailure)
    }

    // ---- exams

    @Test fun examDoesNotRequeueAndPassesAt70() {
        fun run(correct: Int): LessonSession {
            val sess = LessonSession(lesson(*Array(10) { quiz }, exam = true))
            repeat(10) { i -> sess.submit(if (i < correct) CORRECT else WRONG) }
            return sess
        }
        val pass = run(7)
        assertTrue(pass.isFinished, "no re-queue in exams"); assertTrue(pass.passed)
        val fail = run(6)
        assertTrue(fail.isFinished); assertFalse(fail.passed); assertEquals(0.6, fail.accuracy)
    }

    @Test fun examPassUnlocksNextLevelAndBadgeFailDoesNot() {
        val lv = LevelContent(1, t("L"), t("d"), "#112233", listOf(lesson(quiz, quiz, exam = true)), emptyList(), BadgeSpec("b1", t("b"), t("b")))
        val base = Progress()
        val failed = ProgressRepository.complete(base, lv, 0, 0.69, 1)
        assertFalse(failed.passed); assertNull(failed.newBadge); assertNull(failed.unlockedLevel)
        assertFalse(ProgressRepository.isLevelUnlocked(2, failed.progress))
        assertTrue(failed.progress.badges.isEmpty())
        val ok = ProgressRepository.complete(base, lv, 0, 0.7, 1)
        assertTrue(ok.passed); assertEquals("b1", ok.newBadge?.id); assertEquals(2, ok.unlockedLevel)
        assertTrue(ProgressRepository.isLevelUnlocked(2, ok.progress))
    }

    // ---- lock guard

    @Test fun lockedLessonsAndLevelsCannotBeOpened() {
        fun lv(n: Int) = LevelContent(n, t("L"), t("d"), "#112233", listOf(lesson(quiz), lesson(quiz, exam = true)), emptyList(), BadgeSpec("b$n", t("b"), t("b")))
        val levels = listOf(lv(1), lv(2), null)
        var p = Progress()
        val can = { l: Int, i: Int -> ProgressRepository.canOpenLesson(levels, l, i, p) }
        assertTrue(can(0, 0)); assertFalse(can(0, 1)); assertFalse(can(1, 0)); assertFalse(can(2, 0))
        assertFalse(can(5, 0)); assertFalse(can(0, 9)); assertFalse(can(-1, 0))
        p = ProgressRepository.complete(p, levels[0]!!, 0, 1.0, 1).progress
        assertTrue(can(0, 1)); assertFalse(can(1, 0))
        p = ProgressRepository.complete(p, levels[0]!!, 1, 1.0, 1).progress
        assertTrue(can(1, 0)); assertFalse(can(1, 1))
    }
}
