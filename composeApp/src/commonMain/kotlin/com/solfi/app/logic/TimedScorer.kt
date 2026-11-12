package com.solfi.app.logic

import com.solfi.app.audio.PitchReading
import com.solfi.app.music.InstrumentTuning
import com.solfi.app.music.Melody
import com.solfi.app.music.PitchJudge
import com.solfi.app.music.PitchVerdict
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** A note of a part played in time: where it sits (beats from the start, quarter notes) and what must sound. */
data class TimedNote(
    val startBeat: Double,
    val endBeat: Double,
    /** Concert MIDI notes that count as right (a chord accepts any of them). */
    val concertMidis: List<Int>,
    /** Melody events the note covers (ties merged): for colouring the staff. */
    val firstEvent: Int,
    val lastEvent: Int,
    /** 0-based measure where the note starts. */
    val measure: Int,
)

enum class TimedVerdict { ON_TIME, EARLY, LATE, WRONG_NOTE, MISSED;
    val played: Boolean get() = this == ON_TIME || this == EARLY || this == LATE
}

data class TimedResult(
    val noteIndex: Int,
    val verdict: TimedVerdict,
    /** Onset relative to the written start, in ms (negative = early); null when not played or not measurable. */
    val offsetMs: Double? = null,
    /** Median tuning deviation while the right note sounded. */
    val cents: Double? = null,
    /** IN_TUNE / SLIGHTLY_HIGH / SLIGHTLY_LOW for played notes; TOO_HIGH / TOO_LOW for wrong notes. */
    val pitch: PitchVerdict? = null,
)

data class TimedSummary(val total: Int, val played: Int, val onTime: Int, val inTune: Int, val measuresToReview: List<Int>)

/**
 * Scores a part played in time against [notes]. Pure: feed it the pitch readings with the beat they belong to
 * (already corrected for detection latency) and ask for a verdict once a note is over.
 *
 * A note counts as played when the right pitch sounds for at least [minCoverage] of its length. Its onset is the
 * first moment the right pitch is heard (two readings in a row) from a little before the written start; within
 * ±[onTimeMs] it is on time. Repeated notes of the same pitch with no gap cannot show their onset and count as on time.
 */
class TimedScorer(
    val notes: List<TimedNote>,
    val tempo: Int,
    private val octaveFree: Boolean = true,
    private val minClarity: Double = 0.75,
    private val onTimeMs: Double = 140.0,
    private val earlyWindowMs: Double = 260.0,
    private val minCoverage: Double = 0.3,
) {
    private class Sample(val beat: Double, val freq: Double?)

    private val samples = ArrayList<Sample>()
    private val msPerBeat = 60_000.0 / tempo

    fun feed(beat: Double, r: PitchReading) {
        samples += Sample(beat, r.frequency?.takeIf { r.clarity >= minClarity })
    }

    /** True once enough time has passed after note [i] to judge it. */
    fun canScore(i: Int, nowBeat: Double): Boolean = nowBeat >= notes[i].endBeat + 80.0 / msPerBeat

    fun scoreAll(): List<TimedResult> = notes.indices.map(::score)

    fun score(i: Int): TimedResult {
        val n = notes[i]
        val prev = notes.getOrNull(i - 1)
        val maxEarly = min(earlyWindowMs / msPerBeat, if (prev != null) (n.startBeat - prev.startBeat) / 2 else earlyWindowMs / msPerBeat)
        val windowStart = n.startBeat - maxEarly
        // ignore the release tail: the last 15% of the note (or 120 ms) may already move towards the next one
        val bodyEnd = n.endBeat - min(0.15 * (n.endBeat - n.startBeat), 120.0 / msPerBeat)
        val window = samples.filter { it.beat >= windowStart && it.beat < n.endBeat }
        val body = window.filter { it.beat >= n.startBeat && it.beat < bodyEnd }
        if (body.isEmpty()) return TimedResult(i, TimedVerdict.MISSED)

        fun judge(f: Double) = n.concertMidis.map { PitchJudge.judge(f, it, octaveFree) }.minBy { abs(it.cents) }
        val right = body.filter { it.freq != null && judge(it.freq).verdict.isRightNote }
        if (right.size >= minCoverage * body.size && right.isNotEmpty()) {
            val cents = right.map { judge(it.freq!!).cents }.sorted().let { it[it.size / 2] }
            val pitch = when {
                abs(cents) <= PitchJudge.IN_TUNE_CENTS -> PitchVerdict.IN_TUNE
                cents > 0 -> PitchVerdict.SLIGHTLY_HIGH
                else -> PitchVerdict.SLIGHTLY_LOW
            }
            val isRight = window.map { it.freq != null && judge(it.freq).verdict.isRightNote }
            val onsetIdx = isRight.indices.firstOrNull { k -> isRight[k] && (k + 1 >= isRight.size || isRight[k + 1]) }
            val legato = prev != null && abs(prev.endBeat - n.startBeat) < 1e-6 && prev.concertMidis.any { it in n.concertMidis }
            if (onsetIdx == null) return TimedResult(i, TimedVerdict.ON_TIME, null, cents, pitch)
            if (legato && onsetIdx == 0) return TimedResult(i, TimedVerdict.ON_TIME, null, cents, pitch)
            val offset = (window[onsetIdx].beat - n.startBeat) * msPerBeat
            val verdict = when {
                abs(offset) <= onTimeMs -> TimedVerdict.ON_TIME
                offset < 0 -> TimedVerdict.EARLY
                else -> TimedVerdict.LATE
            }
            return TimedResult(i, verdict, offset, cents, pitch)
        }
        val wrong = body.filter { it.freq != null }
        if (wrong.size >= minCoverage * body.size && wrong.isNotEmpty()) {
            val midis = wrong.map { PitchJudge.midiOf(it.freq!!).roundToInt() }
            val heard = midis.groupingBy { it }.eachCount().maxBy { it.value }.key
            val expected = n.concertMidis.minBy { abs(it - heard) }
            return TimedResult(i, TimedVerdict.WRONG_NOTE, pitch = if (heard > expected) PitchVerdict.TOO_HIGH else PitchVerdict.TOO_LOW)
        }
        return TimedResult(i, TimedVerdict.MISSED)
    }

    companion object {
        /** Notes of a written part with the concert pitches [tuning] produces for them. */
        fun notesOf(written: Melody, tuning: InstrumentTuning): List<TimedNote> {
            val events = written.events
            return written.soundNotes().filter { it.pitches.isNotEmpty() }.map { s ->
                TimedNote(s.startBeat, s.startBeat + s.durBeats, s.pitches.map { tuning.concertMidi(it.midi) }, s.firstEvent, s.lastEvent, events[s.firstEvent].measure)
            }
        }

        fun summary(notes: List<TimedNote>, results: List<TimedResult>): TimedSummary = TimedSummary(
            total = notes.size,
            played = results.count { it.verdict.played },
            onTime = results.count { it.verdict == TimedVerdict.ON_TIME },
            inTune = results.count { it.verdict.played && it.pitch == PitchVerdict.IN_TUNE },
            measuresToReview = results.filter { it.verdict != TimedVerdict.ON_TIME || it.pitch != PitchVerdict.IN_TUNE }
                .map { notes[it.noteIndex].measure + 1 }.distinct().sorted(),
        )
    }
}
