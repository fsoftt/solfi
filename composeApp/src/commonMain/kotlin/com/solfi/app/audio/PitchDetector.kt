package com.solfi.app.audio

import kotlin.math.sqrt

/** One analysis frame: [frequency] in Hz (null = no clear pitch), [clarity] 0..1 and [level] (RMS). */
data class PitchReading(val frequency: Double?, val clarity: Double, val level: Double)

/**
 * Monophonic pitch detector (YIN, de Cheveigné & Kawahara 2002) with parabolic interpolation.
 * Pure Kotlin so it runs (and is tested) the same on every platform. The difference function is computed with an
 * FFT cross-correlation, so the full musical range ([FULL_MIN_HZ]..[FULL_MAX_HZ]: tuba to piccolo, any voice) costs
 * a few FFTs per frame even in the single-threaded browser build.
 *
 * Feed it samples with [push]; it analyses a window of [windowSize] samples every [hop] samples and returns the
 * latest reading (or null when no new frame was completed).
 */
class PitchDetector(
    val sampleRate: Int,
    minFrequency: Double = FULL_MIN_HZ,
    maxFrequency: Double = FULL_MAX_HZ,
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

    // FFT work arrays: size covers the window plus the largest lag, so the correlation does not wrap.
    private val fftSize: Int = run { var n = 1; while (n < windowSize + tauMax + 1) n *= 2; n }
    private val xr = DoubleArray(fftSize)
    private val xi = DoubleArray(fftSize)
    private val yr = DoubleArray(fftSize)
    private val yi = DoubleArray(fftSize)
    private val energy = DoubleArray(windowSize + tauMax + 2)
    private val cosT = DoubleArray(fftSize / 2) { kotlin.math.cos(2 * kotlin.math.PI * it / fftSize) }
    private val sinT = DoubleArray(fftSize / 2) { kotlin.math.sin(2 * kotlin.math.PI * it / fftSize) }

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

        differences(x)
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

    /**
     * d(tau) = sum_j (x[j] - x[j+tau])^2 over the window = e(0..w) + e(tau..tau+w) - 2 r(tau), with the
     * cross-correlation r from FFTs and the energies from prefix sums.
     */
    private fun differences(x: FloatArray) {
        val w = windowSize
        val len = w + tauMax
        for (j in 0 until fftSize) {
            val v = if (j < len) x[j].toDouble() else 0.0
            xr[j] = v; xi[j] = 0.0
            yr[j] = if (j < w) v else 0.0; yi[j] = 0.0
        }
        fft(xr, xi, false)
        fft(yr, yi, false)
        // conj(Y) * X
        for (k in 0 until fftSize) {
            val a = yr[k]; val b = yi[k]; val c = xr[k]; val d = xi[k]
            xr[k] = a * c + b * d
            xi[k] = a * d - b * c
        }
        fft(xr, xi, true)
        energy[0] = 0.0
        for (j in 0 until len) energy[j + 1] = energy[j] + x[j].toDouble() * x[j]
        val e0 = energy[w]
        diff[0] = 0.0
        for (tau in 1..tauMax) {
            val r = xr[tau] / fftSize
            diff[tau] = (e0 + energy[tau + w] - energy[tau] - 2 * r).coerceAtLeast(0.0)
        }
    }

    /** In-place iterative radix-2 FFT ([inverse] without the 1/n scaling). */
    private fun fft(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            var start = 0
            while (start < n) {
                var k = 0
                for (m in start until start + half) {
                    val c = cosT[k]
                    val sn = if (inverse) sinT[k] else -sinT[k]
                    val tr = re[m + half] * c - im[m + half] * sn
                    val ti = re[m + half] * sn + im[m + half] * c
                    re[m + half] = re[m] - tr; im[m + half] = im[m] - ti
                    re[m] += tr; im[m] += ti
                    k += step
                }
                start += size
            }
            size *= 2
        }
    }

    private fun interpolate(c: DoubleArray, t: Int): Double {
        if (t <= tauMin || t >= tauMax) return t.toDouble()
        val a = c[t - 1]; val b = c[t]; val d = c[t + 1]
        val den = a - 2 * b + d
        return if (den == 0.0) t.toDouble() else t + 0.5 * (a - d) / den
    }

    companion object {
        /** A0, below the lowest note of a BB♭ tuba (and of any voice). */
        const val FULL_MIN_HZ = 27.5
        /** Above C8 (4186 Hz), the top of the piccolo. */
        const val FULL_MAX_HZ = 5000.0
    }
}
