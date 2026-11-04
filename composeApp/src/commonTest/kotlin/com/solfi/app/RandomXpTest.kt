package com.solfi.app

import com.solfi.app.data.Progress
import com.solfi.app.logic.ProgressRepository as PR
import com.solfi.app.logic.ProgressRepository.RandomRating
import kotlin.test.Test
import kotlin.test.assertEquals

class RandomXpTest {
    @Test fun ratingsGrantFiveThreeZero() {
        var p = Progress()
        val (p1, a) = PR.rateRandom(p, RandomRating.GOOD, 100); p = p1
        val (p2, b) = PR.rateRandom(p, RandomRating.OK, 100); p = p2
        val (p3, c) = PR.rateRandom(p, RandomRating.REPEAT, 100); p = p3
        assertEquals(listOf(5, 3, 0), listOf(a, b, c))
        assertEquals(8, p.xp)
        assertEquals(2, p.randomXpAwards) // "repeat" does not use up the daily allowance
        assertEquals(1, p.streak)
    }

    @Test fun neverTouchesLessonResults() {
        val p = PR.rateRandom(Progress(), RandomRating.GOOD, 5).first
        assertEquals(emptyMap(), p.lessons)
        assertEquals(0, PR.totalStars(p))
        assertEquals(emptyList(), p.examsPassed)
    }

    @Test fun dailyCapStopsXpAfterTwentyAwards() {
        var p = Progress()
        var total = 0
        repeat(PR.RANDOM_DAILY_CAP) {
            val (np, xp) = PR.rateRandom(p, RandomRating.GOOD, 200); p = np; total += xp
        }
        assertEquals(100, total)
        assertEquals(0, PR.randomAwardsLeft(p, 200))
        val (capped, xp) = PR.rateRandom(p, RandomRating.GOOD, 200)
        assertEquals(0, xp)
        assertEquals(p.xp, capped.xp)
        assertEquals(0, PR.rateRandom(capped, RandomRating.OK, 200).second)
    }

    @Test fun capResetsOnTheNextDay() {
        var p = Progress()
        repeat(25) { p = PR.rateRandom(p, RandomRating.OK, 300).first }
        assertEquals(60, p.xp)
        assertEquals(PR.RANDOM_DAILY_CAP, PR.randomAwardsLeft(Progress(randomXpDay = 300, randomXpAwards = 0), 300))
        assertEquals(PR.RANDOM_DAILY_CAP, PR.randomAwardsLeft(p, 301))
        val (next, xp) = PR.rateRandom(p, RandomRating.GOOD, 301)
        assertEquals(5, xp)
        assertEquals(1, next.randomXpAwards)
        assertEquals(301, next.randomXpDay)
    }
}
