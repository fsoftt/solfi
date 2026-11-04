package com.melodia.app.logic

import kotlin.math.abs

data class RhythmResult(val hits: Int, val expected: Int, val extraTaps: Int, val score: Double, val offsetsMs: List<Double?>)

/** Pure scoring of tap timing. Times are measured on the audio clock, relative to the melody start. */
object RhythmScorer {
    fun beatsToMs(beats: Double, tempo: Int): Double = beats * 60000.0 / tempo

    fun score(expectedMs: List<Double>, tapsMs: List<Double>, toleranceMs: Double): RhythmResult {
        val used = BooleanArray(tapsMs.size)
        val offsets = ArrayList<Double?>()
        var hits = 0
        for (e in expectedMs) {
            var best = -1
            var bestD = Double.MAX_VALUE
            for (i in tapsMs.indices) {
                if (used[i]) continue
                val d = abs(tapsMs[i] - e)
                if (d < bestD) { bestD = d; best = i }
            }
            if (best >= 0 && bestD <= toleranceMs) {
                used[best] = true; hits++; offsets += tapsMs[best] - e
            } else offsets += null
        }
        val extra = used.count { !it }
        val n = expectedMs.size.coerceAtLeast(1)
        val score = ((hits - 0.5 * extra) / n).coerceIn(0.0, 1.0)
        return RhythmResult(hits, expectedMs.size, extra, score, offsets)
    }
}
