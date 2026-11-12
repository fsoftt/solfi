package com.solfi.app.audio

import com.solfi.app.logic.FollowState
import com.solfi.app.logic.NoteFollower
import com.solfi.app.logic.PlayTarget
import com.solfi.app.music.InstrumentTuning
import com.solfi.app.platform.MicInput
import com.solfi.app.platform.MicStatus
import com.solfi.app.platform.PlatformLock
import com.solfi.app.platform.currentTimeMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Listens to the learner's instrument: microphone → [PitchDetector] → (optionally) a [NoteFollower].
 * One shared instance lives in AppState; screens call [listen] / [follow] and [stop] when they leave.
 * Readings arrive on the capture thread and are published through state flows for the UI.
 */
class InstrumentListener(private val mic: MicInput?) {
    private val lock = PlatformLock()
    private val _status = MutableStateFlow<MicStatus?>(null)
    /** null = not listening. */
    val status: StateFlow<MicStatus?> = _status

    private val _reading = MutableStateFlow<PitchReading?>(null)
    /** Latest (lightly smoothed) reading, for tuner displays. */
    val reading: StateFlow<PitchReading?> = _reading

    private val _follow = MutableStateFlow(FollowState())
    val follow: StateFlow<FollowState> = _follow

    private var detector: PitchDetector? = null
    private var tuning: InstrumentTuning? = null
    private var follower: NoteFollower? = null
    private var mutedUntil = 0L
    private val recent = ArrayDeque<Double>()

    private var session = 0

    /** Starts the microphone for [tuning] without a target sequence (tuner). Returns a session id for [stop]. */
    fun listen(tuning: InstrumentTuning): Int {
        val id = lock.withLock { follower = null; _follow.value = FollowState(); ++session }
        open(tuning)
        return id
    }

    /** Starts the microphone and follows [targets]. Restarts the sequence when already listening. */
    fun follow(targets: List<PlayTarget>, tuning: InstrumentTuning, octaveFree: Boolean = tuning.octaveFree): Int {
        val id = lock.withLock {
            val f = NoteFollower(targets, tuning, octaveFree = octaveFree)
            follower = f
            _follow.value = f.state
            ++session
        }
        open(tuning)
        return id
    }

    /** Stops only if [id] is still the current session (a newer screen may already be listening). */
    fun stop(id: Int) { if (lock.withLock { id == session }) stop() }

    /** Ignores the microphone for [ms] (while the app itself plays a reference note). */
    fun mute(ms: Long) = lock.withLock { mutedUntil = currentTimeMillis() + ms }

    private fun open(t: InstrumentTuning) {
        lock.withLock { if (tuning != t) detector = null; tuning = t }
        val st = _status.value
        if (st == MicStatus.LISTENING || st == MicStatus.STARTING) return
        if (mic == null) { _status.value = MicStatus.UNAVAILABLE; return }
        _status.value = MicStatus.STARTING
        mic.start(onStatus = { _status.value = it }, onSamples = ::onSamples)
    }

    /** Feeds samples as if they came from the microphone (tests and previews). */
    internal fun feedForTest(buf: FloatArray, n: Int, rate: Int) {
        _status.value = MicStatus.LISTENING
        onSamples(buf, n, rate)
    }

    private fun onSamples(buf: FloatArray, n: Int, rate: Int) {
        lock.withLock {
            val t = tuning ?: return@withLock
            val d = detector?.takeIf { it.sampleRate == rate }
                ?: PitchDetector(rate, t.minFrequency.coerceAtLeast(28.0), t.maxFrequency.coerceAtMost(2500.0)).also { detector = it }
            var r = d.push(buf, n) ?: return@withLock
            if (currentTimeMillis() < mutedUntil) r = PitchReading(null, 0.0, 0.0)
            val smoothed = smooth(r)
            _reading.value = smoothed
            follower?.let { _follow.value = it.feed(smoothed, d.hop * 1000.0 / rate) }
        }
    }

    /** Median of the last three detected frequencies: removes single-frame octave jumps. */
    private fun smooth(r: PitchReading): PitchReading {
        val f = r.frequency
        if (f == null) { recent.clear(); return r }
        recent.addLast(f)
        while (recent.size > 3) recent.removeFirst()
        if (recent.size < 3) return r
        return r.copy(frequency = recent.sorted()[1])
    }

    fun stop() {
        mic?.stop()
        lock.withLock { follower = null; detector?.reset(); recent.clear() }
        _status.value = null
        _reading.value = null
    }
}
