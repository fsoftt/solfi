package com.solfi.app.music.generator

/**
 * SplitMix64 PRNG implemented in-house so the same seed yields the same sequence on every platform.
 * Only 64-bit integer arithmetic is used (no floating point, no platform RNG).
 */
class SeededRandom(seed: Long) {
    private var state: Long = seed

    fun nextLong(): Long {
        state += GOLDEN
        var z = state
        z = (z xor (z ushr 30)) * MIX1
        z = (z xor (z ushr 27)) * MIX2
        return z xor (z ushr 31)
    }

    /** Uniform-ish integer in `0 until bound`. */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        return ((nextLong() ushr 1) % bound).toInt()
    }

    fun nextInt(range: IntRange): Int = range.first + nextInt(range.last - range.first + 1)

    /** True with probability [percent]/100. */
    fun chance(percent: Int): Boolean = nextInt(100) < percent

    /** Picks one item with probability proportional to its non-negative integer weight, or null if all weights are zero. */
    fun <T> weighted(items: List<T>, weight: (T) -> Int): T? {
        var total = 0
        for (it in items) total += weight(it).coerceAtLeast(0)
        if (total <= 0) return null
        var r = nextInt(total)
        for (it in items) {
            val w = weight(it).coerceAtLeast(0)
            if (r < w) return it
            r -= w
        }
        return items.last()
    }

    private companion object {
        val GOLDEN = 0x9E3779B97F4A7C15UL.toLong()
        val MIX1 = 0xBF58476D1CE4E5B9UL.toLong()
        val MIX2 = 0x94D049BB133111EBUL.toLong()
    }
}
