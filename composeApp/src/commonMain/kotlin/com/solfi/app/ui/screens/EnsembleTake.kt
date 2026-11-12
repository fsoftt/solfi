package com.solfi.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.solfi.app.AppState
import com.solfi.app.audio.PlayOptions
import com.solfi.app.logic.TimedResult
import com.solfi.app.logic.TimedScorer
import com.solfi.app.logic.TimedVerdict
import com.solfi.app.music.InstrumentTuning
import com.solfi.app.music.Melody
import com.solfi.app.music.PitchVerdict
import com.solfi.app.platform.PlatformLock
import com.solfi.app.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * "Play with the ensemble": the other parts (or only the click) play in time while the learner plays [written];
 * the microphone readings are placed on the playback clock and every note is judged as soon as it is over.
 */
@Stable
class EnsembleTake(
    private val app: AppState,
    val written: Melody,
    /** The learner's part at concert pitch: only its timing is used (it stays silent). */
    private val concert: Melody,
    private val others: List<Melody>,
    val tuning: InstrumentTuning,
    private val octaveFree: Boolean,
) {
    enum class Phase { IDLE, RUNNING, DONE }

    val notes = TimedScorer.notesOf(written, tuning)

    private val lock = PlatformLock()
    private var scorer: TimedScorer? = null
    private var scored = 0
    private var session = -1
    private var run = 0

    private val _phase = MutableStateFlow(Phase.IDLE)
    val phase: StateFlow<Phase> = _phase
    private val _results = MutableStateFlow<List<TimedResult>>(emptyList())
    /** Verdicts so far, in note order (grows while playing). */
    val results: StateFlow<List<TimedResult>> = _results

    fun start(tempo: Int, metronome: Boolean, accompaniment: Boolean) {
        stop()
        val sc = TimedScorer(notes, tempo, octaveFree)
        val myRun = lock.withLock { scorer = sc; scored = 0; ++run }
        _results.value = emptyList()
        _phase.value = Phase.RUNNING
        val beatsPerSecond = tempo / 60.0
        // the clock starts first so every reading lands on this run's beats; the count-in gives the mic time to open
        app.engine.playMelody(
            concert,
            PlayOptions(
                tempo = tempo, metronome = metronome || !accompaniment, countIn = true,
                accompaniment = if (accompaniment) others else emptyList(), melodyAudible = false,
                onFinished = { finish(myRun) },
            ),
        )
        session = app.listener.record(tuning) { reading, delay ->
            val beat = app.engine.beatNow() - (delay + INPUT_LATENCY_S) * beatsPerSecond
            val fresh = lock.withLock {
                if (myRun != run) return@withLock emptyList()
                sc.feed(beat, reading)
                val out = ArrayList<TimedResult>()
                while (scored < notes.size && sc.canScore(scored, beat)) out += sc.score(scored++)
                out
            }
            if (fresh.isNotEmpty()) _results.value = _results.value + fresh
        }
    }

    private fun finish(myRun: Int) {
        val rest: List<TimedResult> = lock.withLock {
            val sc = scorer
            if (myRun != run || sc == null) null
            else {
                val out = (scored until notes.size).map { sc.score(it) }
                scored = notes.size
                run++
                out
            }
        } ?: return
        app.listener.stop(session)
        _results.value = _results.value + rest
        _phase.value = Phase.DONE
        app.recordSolfeo()
    }

    /** Stops a take in progress (results so far stay on the staff). */
    fun stop() {
        if (_phase.value != Phase.RUNNING) return
        lock.withLock { run++ }
        app.engine.stop()
        app.listener.stop(session)
        _phase.value = Phase.IDLE
    }

    /** Colour of a melody event after it was judged, or null. */
    fun colorOf(results: List<TimedResult>): (Int) -> Color? {
        val map = HashMap<Int, Color>()
        for (r in results) {
            val n = notes[r.noteIndex]
            val c = when {
                r.verdict == TimedVerdict.ON_TIME && r.pitch == PitchVerdict.IN_TUNE -> Palette.Success
                r.verdict.played -> Palette.Gold
                else -> Palette.Danger
            }
            for (e in n.firstEvent..n.lastEvent) map[e] = c
        }
        return { map[it] }
    }

    companion object {
        /** Typical microphone input latency on top of the detector's own delay. */
        const val INPUT_LATENCY_S = 0.03
    }
}

@Composable
fun rememberEnsembleTake(
    app: AppState, written: Melody, concert: Melody, others: List<Melody>, tuning: InstrumentTuning, octaveFree: Boolean,
): EnsembleTake {
    val take = remember(written, concert, others, tuning, octaveFree) { EnsembleTake(app, written, concert, others, tuning, octaveFree) }
    DisposableEffect(take) { onDispose { take.stop() } }
    return take
}
