package com.solfi.app

import com.solfi.app.content.*
import com.solfi.app.data.Progress
import com.solfi.app.logic.ProgressRepository as PR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressTest {
    private fun l10n(s: String) = L10n(s, s, s)
    private fun lesson(id: String, exam: Boolean = false) = Lesson(id, l10n(id), steps = emptyList(), isExam = exam)
    private fun level(n: Int) = LevelContent(
        n, l10n("L$n"), l10n("d"), "#112233",
        lessons = listOf(lesson("a"), lesson("b"), lesson("c", exam = true)),
        solfeos = emptyList(), badge = BadgeSpec("badge$n", l10n("b"), l10n("b")),
    )

    @Test fun starsByAccuracy() {
        assertEquals(3, PR.starsFor(1.0)); assertEquals(3, PR.starsFor(0.9))
        assertEquals(2, PR.starsFor(0.89)); assertEquals(2, PR.starsFor(0.7))
        assertEquals(1, PR.starsFor(0.69)); assertEquals(1, PR.starsFor(0.0))
    }

    @Test fun sequentialUnlock() {
        val lv = level(1)
        var p = Progress()
        assertTrue(PR.isLessonUnlocked(lv, 0, p))
        assertFalse(PR.isLessonUnlocked(lv, 1, p))
        p = PR.complete(p, lv, 0, 1.0, 100).progress
        assertTrue(PR.isLessonUnlocked(lv, 1, p))
        assertFalse(PR.isLessonUnlocked(lv, 2, p))
        assertEquals(1 to 3, PR.levelProgress(lv, p))
    }

    @Test fun xpAndStarsKeepBest() {
        val lv = level(1)
        val r1 = PR.complete(Progress(), lv, 0, 0.75, 10)
        assertEquals(2, r1.stars)
        assertEquals(PR.xpFor(false, 2), r1.xpGained)
        val r2 = PR.complete(r1.progress, lv, 0, 1.0, 10)
        assertEquals(3, r2.progress.result(1, "a")!!.stars)
        val r3 = PR.complete(r2.progress, lv, 0, 0.5, 10)
        assertEquals(3, r3.progress.result(1, "a")!!.stars)
        assertEquals(3, r3.progress.result(1, "a")!!.completions)
        assertTrue(r3.xpGained < r1.xpGained, "repeat gives less XP")
    }

    @Test fun examUnlocksNextLevelAndGivesBadge() {
        val lv1 = level(1)
        var p = Progress()
        assertFalse(PR.isLevelUnlocked(2, p))
        p = PR.complete(p, lv1, 0, 1.0, 1).progress
        p = PR.complete(p, lv1, 1, 1.0, 1).progress
        val failed = PR.complete(p, lv1, 2, 0.5, 1)
        assertFalse(failed.passed)
        assertNull(failed.newBadge)
        assertFalse(PR.isLevelUnlocked(2, failed.progress))
        assertFalse(PR.isLessonCompleted(lv1, 2, failed.progress))
        val ok = PR.complete(failed.progress, lv1, 2, 0.8, 1)
        assertTrue(ok.passed)
        assertEquals("badge1", ok.newBadge?.id)
        assertEquals(2, ok.unlockedLevel)
        assertTrue(PR.isLevelUnlocked(2, ok.progress))
        assertFalse(PR.isLessonUnlocked(level(3), 0, ok.progress))
        assertTrue(PR.isLessonUnlocked(level(2), 0, ok.progress))
        // doing the exam again does not duplicate the badge
        val again = PR.complete(ok.progress, lv1, 2, 1.0, 1)
        assertNull(again.newBadge)
        assertEquals(listOf("badge1"), again.progress.badges)
    }

    @Test fun streakLogic() {
        val lv = level(1)
        var p = Progress()
        p = PR.complete(p, lv, 0, 1.0, 100).progress
        assertEquals(1, p.streak)
        p = PR.complete(p, lv, 0, 1.0, 100).progress // same day
        assertEquals(1, p.streak)
        p = PR.complete(p, lv, 1, 1.0, 101).progress // next day
        assertEquals(2, p.streak)
        p = PR.complete(p, lv, 1, 1.0, 102).progress
        assertEquals(3, p.streak)
        assertEquals(3, p.bestStreak)
        p = PR.complete(p, lv, 1, 1.0, 105).progress // gap resets
        assertEquals(1, p.streak)
        assertEquals(3, p.bestStreak)
        assertEquals(1, PR.currentStreak(p, 105))
        assertEquals(1, PR.currentStreak(p, 106))
        assertEquals(0, PR.currentStreak(p, 107))
    }

    @Test fun nextLesson() {
        val levels = listOf(level(1), level(2))
        var p = Progress()
        assertEquals(0 to 0, PR.nextLesson(levels, p))
        p = PR.complete(p, levels[0], 0, 1.0, 1).progress
        assertEquals(0 to 1, PR.nextLesson(levels, p))
        p = PR.complete(p, levels[0], 1, 1.0, 1).progress
        p = PR.complete(p, levels[0], 2, 1.0, 1).progress
        assertEquals(1 to 0, PR.nextLesson(levels, p))
        assertNotNull(PR.nextLesson(levels, p))
    }

    @Test fun xpRank() {
        assertEquals(1, PR.xpLevel(0)); assertEquals(1, PR.xpLevel(99)); assertEquals(2, PR.xpLevel(100))
    }
}
