package com.solfi.app.logic

import com.solfi.app.audio.PitchReading
import com.solfi.app.music.InstrumentTuning
import com.solfi.app.music.Melody
import com.solfi.app.music.Pitch
import com.solfi.app.music.PitchJudge
import com.solfi.app.music.PitchJudgement
import com.solfi.app.music.PitchVerdict
import kotlin.math.abs

/** A note the learner must play: what is written on the staff and the concert pitches that count as right. */
data class PlayTarget(
    val written: List<Pitch>,
    /** Index of the melody event (for highlighting), -1 when the target is not part of a melody. */
    val eventIndex: Int = -1,
) {
    init { require(written.isNotEmpty()) }
}

/** How one target went. */
data class NoteResult(
    /** Final verdict of the accepted note: IN_TUNE, SLIGHTLY_HIGH or SLIGHTLY_LOW. */
    val verdict: PitchVerdict,
    /** Average deviation while the note was held, in cents. */
    val cents: Double,
    /** Wrong notes played before the right one. */
    val mistakes: Int,
)

/** A wrong note held long enough to be reported. */
data class WrongNote(val targetIndex: Int, val judgement: PitchJudgement, val serial: Int)

data class FollowState(
    val index: Int = 0,
    val total: Int = 0,
    val results: List<NoteResult> = emptyList(),
    /** Live comparison of what is sounding now with the current target (null = silence / unclear). */
    val live: PitchJudgement? = null,
    /** Raw reading of the last frame (for a tuner display). */
    val heardFrequency: Double? = null,
    val level: Double = 0.0,
    /** Last wrong note reported, for a short "too high / too low" flash. */
    val lastWrong: WrongNote? = null,
    /** True when the next note repeats the previous one and the learner must re-attack it first. */
    val waitingRelease: Boolean = false,
) {
    val done: Boolean get() = total > 0 && index >= total
    val firstTry: Int get() = results.count { it.mistakes == 0 }
    val inTune: Int get() = results.count { it.verdict == PitchVerdict.IN_TUNE }
    val averageAbsCents: Double get() = if (results.isEmpty()) 0.0 else results.sumOf { abs(it.cents) } / results.size
}

/**
 * Follows the learner through [targets] at their own pace (no tempo): the current target is accepted once its
 * pitch has been held for [acceptMs]; a different note held for [wrongMs] is reported as too high / too low.
 * Pure and deterministic: feed it [PitchReading]s with the time each frame covers.
 */
class NoteFollower(
    val targets: List<PlayTarget>,
    val tuning: InstrumentTuning,
    private val acceptMs: Double = 180.0,
    private val wrongMs: Double = 320.0,
    private val minClarity: Double = 0.75,
    /** When true, any octave of the right note is accepted (voice, "ignore octave" option). */
    private val octaveFree: Boolean = tuning.octaveFree,
) {
    var state = FollowState(total = targets.size)
        private set

    private var holdMs = 0.0
    private var centsSum = 0.0
    private var centsFrames = 0
    private var wrongHeld = -1
    private var wrongHoldMs = 0.0
    private var wrongReported = false
    private var mistakes = 0
    private var serial = 0
    private var needRelease = false
    private var peakLevel = 0.0
    private var lastAcceptedMidi = Int.MIN_VALUE

    fun reset() {
        state = FollowState(total = targets.size)
        holdMs = 0.0; centsSum = 0.0; centsFrames = 0
        wrongHeld = -1; wrongHoldMs = 0.0; wrongReported = false
        mistakes = 0; needRelease = false; peakLevel = 0.0; lastAcceptedMidi = Int.MIN_VALUE
    }

    /** Judges [freq] against the closest of the target's concert pitches. */
    fun judge(freq: Double, target: PlayTarget): PitchJudgement =
        target.written.map { PitchJudge.judge(freq, tuning.concertMidi(it.midi), octaveFree) }.minBy { abs(it.cents) }

    fun feed(r: PitchReading, dtMs: Double): FollowState {
        if (state.done) return state.copy(heardFrequency = r.frequency, level = r.level, live = null).also { state = it }
        val target = targets[state.index]
        val f = r.frequency?.takeIf { r.clarity >= minClarity }

        if (needRelease) {
            // A repeated note must be played again: wait for silence, a clear dip in volume or another pitch.
            val released = f == null || r.level < peakLevel * 0.55 ||
                abs(PitchJudge.midiOf(f) - lastAcceptedMidi) > 0.6 && !sameNote(f, lastAcceptedMidi)
            if (released) needRelease = false
            else {
                peakLevel = maxOf(peakLevel * 0.98, r.level)
                state = state.copy(heardFrequency = f, level = r.level, live = f?.let { judge(it, target) }, waitingRelease = true)
                return state
            }
        }

        if (f == null) {
            holdMs = 0.0; centsSum = 0.0; centsFrames = 0
            wrongHeld = -1; wrongHoldMs = 0.0; wrongReported = false
            state = state.copy(heardFrequency = null, level = r.level, live = null, waitingRelease = false)
            return state
        }

        val j = judge(f, target)
        var lastWrong = state.lastWrong
        if (j.verdict.isRightNote) {
            wrongHeld = -1; wrongHoldMs = 0.0; wrongReported = false
            holdMs += dtMs; centsSum += j.cents; centsFrames++
            peakLevel = maxOf(peakLevel, r.level)
            if (holdMs >= acceptMs) {
                val avg = centsSum / centsFrames
                val verdict = when {
                    abs(avg) <= PitchJudge.IN_TUNE_CENTS -> PitchVerdict.IN_TUNE
                    avg > 0 -> PitchVerdict.SLIGHTLY_HIGH
                    else -> PitchVerdict.SLIGHTLY_LOW
                }
                val results = state.results + NoteResult(verdict, avg, mistakes)
                val next = state.index + 1
                lastAcceptedMidi = j.heardMidi
                needRelease = next < targets.size && targets[next].written.any { sameNote(f, tuning.concertMidi(it.midi)) }
                peakLevel = r.level
                holdMs = 0.0; centsSum = 0.0; centsFrames = 0; mistakes = 0
                state = state.copy(index = next, results = results, heardFrequency = f, level = r.level, live = j, waitingRelease = needRelease)
                return state
            }
        } else {
            holdMs = 0.0; centsSum = 0.0; centsFrames = 0
            if (j.heardMidi == wrongHeld) wrongHoldMs += dtMs else { wrongHeld = j.heardMidi; wrongHoldMs = dtMs; wrongReported = false }
            if (!wrongReported && wrongHoldMs >= wrongMs) {
                wrongReported = true
                mistakes++
                lastWrong = WrongNote(state.index, j, ++serial)
            }
        }
        state = state.copy(heardFrequency = f, level = r.level, live = j, lastWrong = lastWrong, waitingRelease = false)
        return state
    }

    private fun sameNote(freq: Double, concertMidi: Int): Boolean =
        PitchJudge.judge(freq, concertMidi, octaveFree).verdict.isRightNote

    companion object {
        /** Targets of a melody: every sounding note after merging ties (rests skipped). */
        fun targetsOf(m: Melody): List<PlayTarget> = m.soundNotes().filter { it.pitches.isNotEmpty() }.map { PlayTarget(it.pitches, it.firstEvent) }
    }
}
