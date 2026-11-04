package com.solfi.app.audio

import com.solfi.app.platform.PlatformLock
import kotlin.math.roundToLong

/** Something scheduled at an absolute mixer frame. */
sealed class Scheduled(val frame: Long) {
    class Start(frame: Long, val voice: Voice) : Scheduled(frame)
    class Release(frame: Long, val voiceId: Int) : Scheduled(frame)
    class Action(frame: Long, val action: () -> Unit) : Scheduled(frame)
}

/** Converts between musical time (beats at a tempo) and sample frames. */
class BeatClock(val tempo: Int, val sampleRate: Int = SAMPLE_RATE) {
    val framesPerBeat: Double get() = sampleRate * 60.0 / tempo
    fun frameForBeat(beat: Double, origin: Long = 0): Long = origin + (beat * framesPerBeat).roundToLong()
    fun beatForFrame(frame: Long, origin: Long = 0): Double = (frame - origin) / framesPerBeat
}

/**
 * Sample-accurate mixer: voices scheduled at absolute frames are started at exactly that sample while
 * the output buffer is being rendered. Thread safe (the render callback and UI use [lock]).
 */
class Mixer(val sampleRate: Int = SAMPLE_RATE) {
    private val lock = PlatformLock()
    private val pending = ArrayList<Scheduled>()
    private val active = ArrayList<Voice>()
    private var nextId = 1

    /** Number of frames rendered so far (position of the next buffer's first sample). */
    var frame: Long = 0
        private set
    var masterVolume: Float = 0.8f

    /** Frames at which voices actually began, recorded for tests (bounded). */
    val startLog = ArrayList<Pair<Int, Long>>()
    var logStarts = false

    fun newVoiceId(): Int = lock.withLock { nextId++ }

    fun schedule(item: Scheduled) = lock.withLock {
        var i = pending.size
        while (i > 0 && pending[i - 1].frame > item.frame) i--
        pending.add(i, item)
    }

    fun startNow(voice: Voice): Int = lock.withLock {
        voice.id = nextId++
        schedule(Scheduled.Start(frame, voice))
        voice.id
    }

    fun releaseNow(id: Int) = schedule(Scheduled.Release(frame, id))

    fun hasWork(): Boolean = lock.withLock { pending.isNotEmpty() || active.isNotEmpty() }

    fun clearPending() = lock.withLock { pending.clear() }

    fun stopAll() = lock.withLock { pending.clear(); active.clear() }

    /** Renders [count] frames into [out] (overwritten) and advances the clock. */
    fun render(out: FloatArray, count: Int) = lock.withLock {
        for (i in 0 until count) out[i] = 0f
        var pos = 0
        while (pos < count) {
            val cur = frame + pos
            val next = pending.firstOrNull()
            if (next != null && next.frame <= cur) {
                pending.removeAt(0)
                when (next) {
                    is Scheduled.Start -> {
                        next.voice.startFrame = cur
                        if (logStarts) startLog += next.voice.id to cur
                        active += next.voice
                    }
                    is Scheduled.Release -> active.firstOrNull { it.id == next.voiceId }?.release()
                    is Scheduled.Action -> next.action()
                }
                continue
            }
            val segEnd = if (next == null) count else minOf(count.toLong(), next.frame - frame).toInt()
            val len = segEnd - pos
            if (len > 0) {
                for (v in active) v.render(out, pos, len)
                pos = segEnd
            }
        }
        active.removeAll { it.finished }
        val vol = masterVolume
        for (i in 0 until count) {
            val s = out[i] * vol
            out[i] = if (s > 1f) 1f else if (s < -1f) -1f else s
        }
        frame += count
    }
}
