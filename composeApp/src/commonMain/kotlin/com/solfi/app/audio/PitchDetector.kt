package com.solfi.app.audio

import kotlin.math.sqrt

/** One analysis frame: [frequency] in Hz (null = no clear pitch), [clarity] 0..1 and [level] (RMS). */
data class PitchReading(val frequency: Double?, val clarity: Double, val level: Double)

/**
 * Monophonic pitch detector (YIN, de Cheveigné & Kawahara 2002) with parabolic interpolation.
 * Pure Kotlin so it runs (and is tested) the same on every platform.
 *
 * Feed it samples with [push]; it analyses a window of [windowSize] samples every [hop] samples and returns the
 * latest reading (or null when no new frame was completed).
 */
class PitchDetector(
    val sampleRate: Int,
    minFrequency: Double = 60.0,
    maxFrequency: Double = 1600.0,
    /** Readings quieter than this RMS are reported as silence. */
    private val silenceLevel: Double = 0.006,
    /** YIN absolute threshold: lower = stricter. */
    private val threshold: Double = 0.15,
) {
    private val tauMin = (sampleRate / maxFrequency).toInt().coerceAtLeast(2)
    private val tauMax = (sampleRate / minFrequency).toInt().coerceAtLeast(tauMin + 2)

    /** Integration window: at least one period of the lowest note (a power of two, min 1024). */
    val windowSize: Int = run {
        var w = 1024
        while (w < tauMax && w < 8192) w *= 2
        w
    }
    private val buffer = FloatArray(windowSize + tauMax + 1)
    private var filled = 0
    /** Samples between frames: half a window, but never more than ~25 analyses per second. */
    val hop: Int = maxOf(windowSize / 2, sampleRate / 25).coerceAtMost(buffer.size - 1)
    private val diff = DoubleArray(tauMax + 1)
    private val cmnd = DoubleArray(tauMax + 1)

    fun reset() { filled = 0 }

    /** Adds [n] samples from [data]; returns the reading of the last completed frame, if any. */
    fun push(data: FloatArray, n: Int = data.size): PitchReading? {
        var out: PitchReading? = null
        var i = 0
        while (i < n) {
            val take = minOf(buffer.size - filled, n - i)
            data.copyInto(buffer, filled, i, i + take)
            filled += take; i += take
            if (filled == buffer.size) {
                out = analyse(buffer)
                buffer.copyInto(buffer, 0, hop, buffer.size)
                filled -= hop
            }
        }
        return out
    }

    /** Analyses the first `windowSize + tauMax` samples of [x]. */
    fun analyse(x: FloatArray): PitchReading {
        val w = windowSize
        var sum = 0.0
        for (j in 0 until w) sum += x[j] * x[j]
        val rms = sqrt(sum / w)
        if (rms < silenceLevel) return PitchReading(null, 0.0, rms)

        // difference function d(tau)
        for (tau in 1..tauMax) {
            var s = 0.0
            for (j in 0 until w) {
                val d = x[j] - x[j + tau]
                s += d * d
            }
            diff[tau] = s
        }
        // cumulative mean normalised difference
        var running = 0.0
        cmnd[0] = 1.0
        for (tau in 1..tauMax) {
            running += diff[tau]
            cmnd[tau] = if (running == 0.0) 1.0 else diff[tau] * tau / running
        }
        // first dip under the threshold, then walk down to its local minimum
        var best = -1
        var tau = tauMin + 1
        while (tau < tauMax) {
            if (cmnd[tau] < threshold) {
                while (tau + 1 < tauMax && cmnd[tau + 1] < cmnd[tau]) tau++
                best = tau
                break
            }
            tau++
        }
        if (best < 0) {
            // no clear dip: take the global minimum but report low clarity
            var minV = Double.MAX_VALUE
            for (t in tauMin + 1 until tauMax) if (cmnd[t] < minV) { minV = cmnd[t]; best = t }
            val clarity = (1.0 - minV).coerceIn(0.0, 1.0)
            if (minV > 0.35) return PitchReading(null, clarity, rms)
        }
        val period = interpolate(cmnd, best)
        val clarity = (1.0 - cmnd[best]).coerceIn(0.0, 1.0)
        return PitchReading(sampleRate / period, clarity, rms)
    }

    private fun interpolate(c: DoubleArray, t: Int): Double {
        if (t <= tauMin || t >= tauMax) return t.toDouble()
        val a = c[t - 1]; val b = c[t]; val d = c[t + 1]
        val den = a - 2 * b + d
        return if (den == 0.0) t.toDouble() else t + 0.5 * (a - d) / den
    }
}
