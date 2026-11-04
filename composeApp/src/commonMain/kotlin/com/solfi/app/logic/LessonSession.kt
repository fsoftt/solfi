package com.solfi.app.logic

import com.solfi.app.content.Lesson
import com.solfi.app.content.Step

/** What the learner did on a step (reported by the UI when it leaves the step). */
enum class StepOutcome { UNSCORED, CORRECT, WRONG }

/** Why "Continuar" is still disabled (the UI maps each value to a localized hint). */
enum class StepHint { NONE, LISTEN_EXAMPLE, LISTEN_TO_END, ANSWER, PLAY_SOUND_FIRST, PLAY_NOTES, TAP_RHYTHM, WAIT }

/** Live facts about the step currently on screen; the UI only reports them, [StepCompletionPolicy] decides. */
data class StepState(
    val elapsedMs: Long = 0,
    /** A melody was played until the sequencer reached its end (stopping early does not count). */
    val playedToEnd: Boolean = false,
    /** The ear-training sound was played at least once. */
    val soundPlayed: Boolean = false,
    /** An answer was given and its feedback is showing. */
    val answered: Boolean = false,
    /** All piano targets were played in order. */
    val pianoDone: Boolean = false,
    val rhythmPassed: Boolean = false,
    val rhythmFailedAttempts: Int = 0,
)

/** Pure per-step gating rules for the "Continuar" button. */
object StepCompletionPolicy {
    const val TEXT_ONLY_DELAY_MS = 2000L
    const val RHYTHM_MIN_ACCURACY = 0.6
    const val RHYTHM_MAX_FAILED_ATTEMPTS = 3

    fun canContinue(step: Step, st: StepState): Boolean = when (step) {
        is Step.Explain -> if (step.melody != null) st.playedToEnd else st.elapsedMs >= TEXT_ONLY_DELAY_MS
        is Step.Listen -> st.playedToEnd
        is Step.Quiz, is Step.IdentifyNote, is Step.EarTraining -> st.answered
        is Step.Piano -> st.pianoDone
        is Step.RhythmTap -> st.rhythmPassed || st.rhythmFailedAttempts >= RHYTHM_MAX_FAILED_ATTEMPTS
    }

    fun hint(step: Step, st: StepState): StepHint = if (canContinue(step, st)) StepHint.NONE else when (step) {
        is Step.Explain -> if (step.melody != null) StepHint.LISTEN_EXAMPLE else StepHint.WAIT
        is Step.Listen -> StepHint.LISTEN_TO_END
        is Step.Quiz, is Step.IdentifyNote -> StepHint.ANSWER
        is Step.EarTraining -> if (!st.soundPlayed) StepHint.PLAY_SOUND_FIRST else StepHint.ANSWER
        is Step.Piano -> StepHint.PLAY_NOTES
        is Step.RhythmTap -> StepHint.TAP_RHYTHM
    }

    /** Ear-training options are locked until the sound has been played once. */
    fun answersEnabled(step: Step, st: StepState): Boolean = step !is Step.EarTraining || st.soundPlayed

    fun rhythmPassed(step: Step.RhythmTap, score: Double): Boolean = score >= maxOf(step.passScore, RHYTHM_MIN_ACCURACY)

    /** Rhythm counts as correct only when passed at the first attempt; a later pass or giving up counts as failed. */
    fun rhythmOutcome(st: StepState): StepOutcome =
        if (st.rhythmPassed && st.rhythmFailedAttempts == 0) StepOutcome.CORRECT else StepOutcome.WRONG
}

/**
 * Lesson state machine: a queue of step indexes. Wrong answers on quiz / identifyNote / earTraining / piano are re-queued at
 * the end (never in exams) until answered correctly. Accuracy and stars use first-attempt results only.
 */
class LessonSession(val lesson: Lesson) {
    private val queue = ArrayDeque(lesson.steps.indices.toList())
    private val firstTry = HashMap<Int, Boolean>()
    private val attempts = HashMap<Int, Int>()
    private val initial = lesson.steps.size

    val isExam: Boolean get() = lesson.isExam
    val currentIndex: Int? get() = queue.firstOrNull()
    val currentStep: Step? get() = currentIndex?.let { lesson.steps[it] }
    val isFinished: Boolean get() = queue.isEmpty()
    val totalSteps: Int get() = initial
    /** Steps still waiting (re-queued repeats included). */
    val remaining: Int get() = queue.size
    /** Steps of the original lesson already resolved. */
    val resolvedCount: Int get() = (initial - queue.toSet().size).coerceAtLeast(0)
    fun attemptsOf(index: Int): Int = attempts[index] ?: 0
    fun isRepeat(index: Int): Boolean = attemptsOf(index) > 0

    private val scoredCount: Int get() = lesson.steps.count { isScored(it) }
    private val correctFirstTry: Int get() = firstTry.values.count { it }

    /** First-attempt accuracy 0..1 (1.0 for lessons without scored steps). Only meaningful once finished. */
    val accuracy: Double get() = if (scoredCount == 0) 1.0 else correctFirstTry.toDouble() / scoredCount
    val stars: Int get() = ProgressRepository.starsFor(accuracy)
    /** Finished and, for exams, at or above [ProgressRepository.EXAM_PASS]. */
    val passed: Boolean get() = isFinished && (!isExam || accuracy >= ProgressRepository.EXAM_PASS)

    /** Records the outcome of the current step and moves on. */
    fun submit(outcome: StepOutcome) {
        val idx = currentIndex ?: error("lesson already finished")
        val step = lesson.steps[idx]
        require((outcome == StepOutcome.UNSCORED) == !isScored(step)) { "outcome $outcome does not fit step ${step::class.simpleName}" }
        queue.removeFirst()
        attempts[idx] = attemptsOf(idx) + 1
        if (outcome == StepOutcome.UNSCORED) return
        if (idx !in firstTry) firstTry[idx] = outcome == StepOutcome.CORRECT
        if (outcome == StepOutcome.WRONG && requeueOnWrong(step)) queue.addLast(idx)
    }

    private fun requeueOnWrong(step: Step) =
        !isExam && (step is Step.Quiz || step is Step.IdentifyNote || step is Step.EarTraining || step is Step.Piano)

    companion object {
        fun isScored(step: Step): Boolean = step !is Step.Explain && step !is Step.Listen
    }
}
