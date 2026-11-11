package com.solfi.app.logic

import com.solfi.app.content.BadgeSpec
import com.solfi.app.content.LevelContent
import com.solfi.app.data.LessonResult
import com.solfi.app.data.Progress

data class CompletionResult(
    val progress: Progress,
    val passed: Boolean,
    val stars: Int,
    val xpGained: Int,
    val newBadge: BadgeSpec?,
    val unlockedLevel: Int?,
    val streak: Int,
)

/** Pure progression rules: XP, stars, sequential unlock, exams and daily streak. */
object ProgressRepository {
    const val EXAM_PASS = 0.7
    const val XP_PER_XPLEVEL = 100

    fun starsFor(accuracy: Double): Int = when {
        accuracy >= 0.9 -> 3
        accuracy >= 0.7 -> 2
        else -> 1
    }

    fun xpFor(isExam: Boolean, stars: Int): Int = (if (isExam) 50 else 20) + 10 * stars

    fun xpLevel(xp: Int): Int = xp / XP_PER_XPLEVEL + 1

    /** Streak after activity on [today]. */
    fun streakAfterActivity(p: Progress, today: Int): Int = when {
        p.lastActiveDay == today -> p.streak.coerceAtLeast(1)
        p.lastActiveDay == today - 1 -> p.streak + 1
        else -> 1
    }

    /** Streak to display on [today]: broken streaks show 0. */
    fun currentStreak(p: Progress, today: Int): Int =
        if (p.lastActiveDay == today || p.lastActiveDay == today - 1) p.streak else 0

    fun isLevelUnlocked(levelNumber: Int, p: Progress): Boolean = levelNumber <= 1 || (levelNumber - 1) in p.examsPassed

    fun isLessonCompleted(level: LevelContent, index: Int, p: Progress): Boolean {
        val l = level.lessons.getOrNull(index) ?: return false
        if (level.level in p.skippedLevels) return true
        return p.result(level.level, l.id)?.let { it.completions > 0 } == true
    }

    /** Single guard for every entry point (map, home, deep state): a lesson opens only when its level and predecessor are done. */
    fun canOpenLesson(levels: List<LevelContent?>, levelIndex: Int, lessonIndex: Int, p: Progress): Boolean {
        val level = levels.getOrNull(levelIndex) ?: return false
        if (lessonIndex !in level.lessons.indices) return false
        return isLessonUnlocked(level, lessonIndex, p)
    }

    fun isLessonUnlocked(level: LevelContent, index: Int, p: Progress): Boolean {
        if (!isLevelUnlocked(level.level, p)) return false
        if (index <= 0) return true
        return isLessonCompleted(level, index - 1, p)
    }

    fun levelProgress(level: LevelContent, p: Progress): Pair<Int, Int> =
        level.lessons.indices.count { isLessonCompleted(level, it, p) } to level.lessons.size

    fun totalStars(p: Progress): Int = p.lessons.values.sumOf { it.stars }

    /** First unlocked and not yet completed lesson across the given levels: (levelIndex, lessonIndex). */
    fun nextLesson(levels: List<LevelContent>, p: Progress): Pair<Int, Int>? {
        for ((li, lv) in levels.withIndex()) {
            if (!isLevelUnlocked(lv.level, p)) return null
            for (i in lv.lessons.indices) if (!isLessonCompleted(lv, i, p)) return li to i
        }
        return null
    }

    /** Applies the outcome of a lesson with [accuracy] 0..1. A failed exam (< [EXAM_PASS]) gives a little XP only. */
    fun complete(p: Progress, level: LevelContent, lessonIndex: Int, accuracy: Double, today: Int): CompletionResult {
        val lesson = level.lessons[lessonIndex]
        val passed = !lesson.isExam || accuracy >= EXAM_PASS
        val streak = streakAfterActivity(p, today)
        if (!passed) {
            val np = p.copy(xp = p.xp + 5, streak = streak, bestStreak = maxOf(p.bestStreak, streak), lastActiveDay = today)
            return CompletionResult(np, false, 0, 5, null, null, streak)
        }
        val stars = starsFor(accuracy)
        val key = "${level.level}/${lesson.id}"
        val old = p.lessons[key]
        val newResult = LessonResult(
            stars = maxOf(old?.stars ?: 0, stars),
            bestAccuracy = maxOf(old?.bestAccuracy ?: 0.0, accuracy),
            completions = (old?.completions ?: 0) + 1,
        )
        // Repeating a lesson gives reduced XP.
        val base = xpFor(lesson.isExam, stars)
        val xp = if (old != null) base / 4 else base
        var badges = p.badges
        var exams = p.examsPassed
        var newBadge: BadgeSpec? = null
        var unlocked: Int? = null
        if (lesson.isExam) {
            if (level.level !in exams) { exams = exams + level.level; unlocked = level.level + 1 }
            if (level.badge.id !in badges) { badges = badges + level.badge.id; newBadge = level.badge }
        }
        val np = p.copy(
            xp = p.xp + xp,
            streak = streak,
            bestStreak = maxOf(p.bestStreak, streak),
            lastActiveDay = today,
            lessons = p.lessons + (key to newResult),
            badges = badges,
            examsPassed = exams,
        )
        return CompletionResult(np, true, stars, xp, newBadge, unlocked, streak)
    }

    // ---------------------------------------------------------------- skip test

    /**
     * A level can be skipped by taking its exam directly while it is not passed yet and its exam is not already
     * reachable through the normal path (then the regular exam is taken instead). Works for locked levels too.
     */
    fun canTakeSkipTest(level: LevelContent, p: Progress): Boolean {
        val last = level.lessons.lastIndex
        if (last < 0 || !level.lessons[last].isExam) return false
        if (level.level in p.examsPassed) return false
        return !isLessonUnlocked(level, last, p)
    }

    /**
     * Outcome of a skip test (the level's exam taken directly). Below [EXAM_PASS] it counts like a failed exam.
     * Passing records the exam like [complete] (XP, stars, badge, next level unlocked) and also marks this level and
     * every earlier level that was not passed yet as completed, so the sequential rules stay consistent.
     */
    fun completeSkipTest(p: Progress, level: LevelContent, accuracy: Double, today: Int): CompletionResult {
        val r = complete(p, level, level.lessons.lastIndex, accuracy, today)
        if (!r.passed) return r
        val skipped = (1..level.level).filter { it !in p.examsPassed && it !in p.skippedLevels }
        val np = r.progress.copy(
            examsPassed = (r.progress.examsPassed + (1..level.level)).distinct().sorted(),
            skippedLevels = (p.skippedLevels + skipped).distinct().sorted(),
        )
        return r.copy(progress = np)
    }

    fun recordSolfeo(p: Progress, today: Int): Progress {
        val streak = streakAfterActivity(p, today)
        return p.copy(solfeosPlayed = p.solfeosPlayed + 1, streak = streak, bestStreak = maxOf(p.bestStreak, streak), lastActiveDay = today)
    }

    // ---------------------------------------------------------------- random exercises

    const val RANDOM_DAILY_CAP = 20

    /** Self-assessment after a random exercise. */
    enum class RandomRating(val xp: Int) { GOOD(5), OK(3), REPEAT(0) }

    /** Random-exercise XP awards still available on [today]. */
    fun randomAwardsLeft(p: Progress, today: Int): Int =
        if (p.randomXpDay == today) (RANDOM_DAILY_CAP - p.randomXpAwards).coerceAtLeast(0) else RANDOM_DAILY_CAP

    /**
     * Applies a self-assessment: XP 5/3/0 for Bien/Regular/Repetir, at most [RANDOM_DAILY_CAP] XP-granting awards per day.
     * Never touches lesson results or stars. Returns the new progress and the XP actually granted.
     */
    fun rateRandom(p: Progress, rating: RandomRating, today: Int): Pair<Progress, Int> {
        val streak = streakAfterActivity(p, today)
        val used = if (p.randomXpDay == today) p.randomXpAwards else 0
        val grant = if (rating.xp > 0 && used < RANDOM_DAILY_CAP) rating.xp else 0
        val np = p.copy(
            xp = p.xp + grant,
            streak = streak, bestStreak = maxOf(p.bestStreak, streak), lastActiveDay = today,
            randomXpDay = today, randomXpAwards = used + if (grant > 0) 1 else 0,
        )
        return np to grant
    }
}
