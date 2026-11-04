package com.melodia.app.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

const val SAMPLE_RATE = 44100

/** A sound source that adds its output into a mix buffer. */
abstract class Voice {
    /** Frame (absolute mixer frame) at which the voice became audible. */
    var startFrame: Long = 0
    var id: Int = 0
    abstract val finished: Boolean
    abstract fun release()
    /** Adds [count] samples into [out] starting at [offset]. */
    abstract fun render(out: FloatArray, offset: Int, count: Int)
}

/**
 * Piano-ish additive voice: a few harmonics with individual decays, two slightly detuned oscillator
 * groups, a short attack and an exponential release.
 * [sustainFrames] is the held length; [Long.MAX_VALUE] means held until [release].
 */
class PianoVoice(
    private val freq: Double,
    private val velocity: Float = 0.8f,
    private var sustainFrames: Long = Long.MAX_VALUE,
    private val sampleRate: Int = SAMPLE_RATE,
) : Voice() {
    private val harmonics = 6
    private val amps = floatArrayOf(1.0f, 0.55f, 0.32f, 0.18f, 0.1f, 0.05f)
    private val decayPerSec = floatArrayOf(1.6f, 2.2f, 3.0f, 4.2f, 5.5f, 7.0f)
    private val phase = DoubleArray(harmonics * 2) { 0.3 }
    private val envState = FloatArray(harmonics) { 1f }
    private val envMul = FloatArray(harmonics) { exp(-decayPerSec[it].toDouble() / sampleRate).toFloat() }
    private val inc = DoubleArray(harmonics * 2)
    private var age = 0L
    private var releasing = false
    private var releaseAge = 0L
    private val attackFrames = (sampleRate * 0.004).toInt().coerceAtLeast(1)
    private val releaseFrames = (sampleRate * 0.12).toInt()
    private var done = false
    private val norm: Float

    init {
        for (h in 0 until harmonics) {
            val f = freq * (h + 1) * (1.0 + 0.00002 * h * h) // slight inharmonicity
            if (f * 1.002 > sampleRate / 2.0) { amps[h] = 0f }
            inc[h * 2] = 2 * PI * f * 0.9994 / sampleRate
            inc[h * 2 + 1] = 2 * PI * f * 1.0006 / sampleRate
        }
        norm = velocity * 0.22f
    }

    override val finished: Boolean get() = done

    override fun release() { if (!releasing) { releasing = true; releaseAge = 0 } }

    override fun render(out: FloatArray, offset: Int, count: Int) {
        for (i in 0 until count) {
            if (done) return
            if (!releasing && age >= sustainFrames) { releasing = true; releaseAge = 0 }
            var s = 0f
            for (h in 0 until harmonics) {
                val a = amps[h]
                if (a == 0f) continue
                val env = envState[h]
                envState[h] = env * envMul[h]
                val p0 = phase[h * 2]; val p1 = phase[h * 2 + 1]
                s += a * env * (sin(p0).toFloat() + sin(p1).toFloat()) * 0.5f
                phase[h * 2] = p0 + inc[h * 2]
                phase[h * 2 + 1] = p1 + inc[h * 2 + 1]
            }
            var gain = if (age < attackFrames) (age + 1).toFloat() / attackFrames else 1f
            if (releasing) {
                gain *= 1f - releaseAge.toFloat() / releaseFrames
                releaseAge++
                if (releaseAge >= releaseFrames) done = true
            }
            // gentle fade after 6 seconds so forgotten voices end
            if (age > sampleRate * 8L) done = true
            out[offset + i] += s * gain * norm
            age++
        }
    }
}

/** Metronome click: short sine burst, higher and louder on the downbeat. */
class ClickVoice(accent: Boolean, private val sampleRate: Int = SAMPLE_RATE) : Voice() {
    private val freq = if (accent) 1760.0 else 1175.0
    private val gain = if (accent) 0.55f else 0.38f
    private val len = (sampleRate * 0.035).toInt()
    private var n = 0
    override val finished: Boolean get() = n >= len
    override fun release() {}
    override fun render(out: FloatArray, offset: Int, count: Int) {
        for (i in 0 until count) {
            if (n >= len) return
            val env = exp(-n * 160.0 / sampleRate).toFloat()
            out[offset + i] += sin(2 * PI * freq * n / sampleRate + 0.5).toFloat() * env * gain
            n++
        }
    }
}

/** Soft pad-like feedback tone, used for correct/incorrect jingles. */
class BlipVoice(private val freq: Double, private val lenFrames: Int, private val sampleRate: Int = SAMPLE_RATE) : Voice() {
    private var n = 0
    override val finished: Boolean get() = n >= lenFrames
    override fun release() {}
    override fun render(out: FloatArray, offset: Int, count: Int) {
        for (i in 0 until count) {
            if (n >= lenFrames) return
            val x = n.toFloat() / lenFrames
            val env = (if (x < 0.05f) x / 0.05f else (1f - x) * 1.05f).coerceIn(0f, 1f)
            out[offset + i] += sin(2 * PI * freq * n / sampleRate + 0.3).toFloat() * env * 0.2f
            n++
        }
    }
}
