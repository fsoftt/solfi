package com.melodia.app.audio

import com.melodia.app.music.Melody
import com.melodia.app.music.Pitch
import com.melodia.app.platform.AudioSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Options for [AudioEngine.playMelody]. */
data class PlayOptions(
    val tempo: Int,
    val metronome: Boolean = false,
    val countIn: Boolean = false,
    val loop: Boolean = false,
    /** Only the pulse (clicks), no notes. */
    val pulseOnly: Boolean = false,
    /** Keep clicking after the count-in (only relevant when [metronome] is true). */
    val onFinished: (() -> Unit)? = null,
)

/**
 * High level audio: owns the [Mixer], streams it to the [AudioSink], plays melodies, single notes and
 * feedback sounds, and exposes the playback position in beats for UI highlighting and tap scoring.
 */
class AudioEngine(private val sink: AudioSink? = null) {
    val mixer = Mixer()
    private var started = false

    private val _position = MutableStateFlow(IDLE)
    /** Position in quarter-note beats relative to the melody start (negative during count-in), or [IDLE]. */
    val position: StateFlow<Double> = _position.asStateFlow()

    private val _playing = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _playing.asStateFlow()

    @kotlin.concurrent.Volatile private var runStart = 0L
    @kotlin.concurrent.Volatile private var runClock: BeatClock? = null
    @kotlin.concurrent.Volatile private var runLength = 0.0
    @kotlin.concurrent.Volatile private var runLoop = false
    @kotlin.concurrent.Volatile private var runId = 0

    var volume: Float
        get() = mixer.masterVolume
        set(v) { mixer.masterVolume = v.coerceIn(0f, 1f) }

    fun ensureStarted() {
        if (started || sink == null) return
        started = true
        sink.start(SAMPLE_RATE) { buf, n ->
            mixer.render(buf, n)
            updatePosition()
        }
    }

    fun shutdown() {
        stop()
        if (started) { sink?.stop(); started = false }
    }

    private fun updatePosition() {
        val clock = runClock ?: return
        if (!_playing.value) return
        val played = audioClockFrames()
        var beat = clock.beatForFrame(played, runStart)
        if (runLoop && beat < 0 && runLength > 0) beat += runLength
        _position.value = beat
    }

    /** Frames audibly played (audio clock). Falls back to rendered frames when no sink is available. */
    fun audioClockFrames(): Long = if (sink != null && started) sink.playedFrames() else mixer.frame

    /** Current position of the audio clock in beats relative to the active run's melody start. */
    fun beatNow(): Double = runClock?.beatForFrame(audioClockFrames(), runStart) ?: 0.0

    fun stop() {
        runId++
        mixer.stopAll()
        runClock = null
        _playing.value = false
        _position.value = IDLE
    }

    // ---------------------------------------------------------------- single sounds

    fun playPitch(p: Pitch, seconds: Double = 0.9, velocity: Float = 0.8f) {
        ensureStarted()
        mixer.startNow(PianoVoice(p.frequency, velocity, (seconds * SAMPLE_RATE).toLong()))
    }

    /** Starts a held note (piano key down); returns an id for [noteOff]. */
    fun noteOn(p: Pitch, velocity: Float = 0.85f): Int {
        ensureStarted()
        return mixer.startNow(PianoVoice(p.frequency, velocity))
    }

    fun noteOff(id: Int) = mixer.releaseNow(id)

    fun playTap() {
        ensureStarted()
        mixer.startNow(ClickVoice(false))
    }

    /** Short ascending (success) or descending (failure) jingle. */
    fun playFeedback(success: Boolean) {
        ensureStarted()
        val freqs = if (success) listOf(523.25, 659.25, 783.99) else listOf(311.13, 233.08)
        val step = (SAMPLE_RATE * 0.09).toInt()
        freqs.forEachIndexed { i, f ->
            mixer.schedule(Scheduled.Start(mixer.frame + i * step, BlipVoice(f, step * 2).also { it.id = mixer.newVoiceId() }))
        }
    }

    /** Plays pitches one after another ([together]=false) or as a chord. Returns total length in seconds. */
    fun playPitches(pitches: List<Pitch>, together: Boolean, tempo: Int = 80): Double {
        ensureStarted()
        stop()
        val clock = BeatClock(tempo)
        val base = mixer.frame + 256
        if (together) {
            pitches.forEach { mixer.schedule(Scheduled.Start(base, PianoVoice(it.frequency, 0.7f, (SAMPLE_RATE * 1.6).toLong()).also { v -> v.id = mixer.newVoiceId() })) }
            return 1.6
        }
        pitches.forEachIndexed { i, p ->
            val f = clock.frameForBeat(i.toDouble(), base)
            mixer.schedule(Scheduled.Start(f, PianoVoice(p.frequency, 0.8f, (clock.framesPerBeat * 0.95).toLong()).also { v -> v.id = mixer.newVoiceId() }))
        }
        return pitches.size * 60.0 / tempo
    }

    // ---------------------------------------------------------------- melodies

    fun playMelody(melody: Melody, o: PlayOptions) {
        ensureStarted()
        stop()
        val myRun = ++runId
        val clock = BeatClock(o.tempo)
        val countBeats = if (o.countIn) melody.time.measureTicks / Melody.TICKS_PER_QUARTER.toDouble() else 0.0
        val lead = 512L
        val origin = mixer.frame + lead
        val first = clock.frameForBeat(countBeats, origin)
        runClock = clock
        runStart = first
        runLength = melody.totalBeats
        runLoop = o.loop
        _position.value = -countBeats
        _playing.value = true

        if (o.countIn) scheduleClicks(melody, clock, origin, countBeats, 0.0)

        fun iteration(start: Long) {
            if (myRun != runId) return
            scheduleMelody(melody, clock, start, o)
            val end = clock.frameForBeat(melody.totalBeats, start)
            mixer.schedule(Scheduled.Action(end) {
                if (myRun != runId) return@Action
                if (o.loop) {
                    runStart = end
                    iteration(end)
                } else {
                    _playing.value = false
                    _position.value = IDLE
                    runClock = null
                    o.onFinished?.invoke()
                }
            })
        }
        iteration(first)
    }

    private fun scheduleMelody(melody: Melody, clock: BeatClock, start: Long, o: PlayOptions) {
        if (!o.pulseOnly) {
            for (n in melody.soundNotes()) {
                val f = clock.frameForBeat(n.startBeat, start)
                val sustain = (clock.framesPerBeat * n.durBeats * 0.98).toLong().coerceAtLeast(200)
                for (p in n.pitches) {
                    mixer.schedule(Scheduled.Start(f, PianoVoice(p.frequency, 0.8f, sustain).also { it.id = mixer.newVoiceId() }))
                }
            }
        }
        if (o.metronome || o.pulseOnly) scheduleClicks(melody, clock, start, melody.totalBeats, 0.0)
    }

    private fun scheduleClicks(melody: Melody, clock: BeatClock, start: Long, beats: Double, from: Double) {
        val clickTicks = melody.time.clickTicks
        val total = (beats * Melody.TICKS_PER_QUARTER).toInt()
        var t = 0
        while (t < total) {
            val accent = t % melody.time.measureTicks == 0
            val beat = t / Melody.TICKS_PER_QUARTER.toDouble() + from
            mixer.schedule(Scheduled.Start(clock.frameForBeat(beat, start), ClickVoice(accent).also { it.id = mixer.newVoiceId() }))
            t += clickTicks
        }
    }

    companion object {
        const val IDLE = -1000.0
    }
}
