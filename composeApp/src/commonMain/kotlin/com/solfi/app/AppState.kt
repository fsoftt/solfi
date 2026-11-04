package com.solfi.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.solfi.app.audio.AudioEngine
import com.solfi.app.content.ContentRepository
import com.solfi.app.content.LevelContent
import com.solfi.app.data.Profile
import com.solfi.app.data.ProfileSettings
import com.solfi.app.data.ProfileStore
import com.solfi.app.data.Progress
import com.solfi.app.i18n.stringsFor
import com.solfi.app.logic.CompletionResult
import com.solfi.app.logic.LessonSession
import com.solfi.app.logic.ProgressRepository
import com.solfi.app.music.ClefPolicy
import com.solfi.app.music.ClefPreference
import com.solfi.app.music.Instrument
import com.solfi.app.music.Clef
import com.solfi.app.music.Lang
import com.solfi.app.music.NoteNaming
import com.solfi.app.music.generator.Difficulty
import com.solfi.app.platform.Haptics
import com.solfi.app.platform.todayEpochDay
import com.solfi.app.ui.theme.UiContext

enum class Tab { HOME, LEARN, SOLFEOS, PIANO, PROGRESS }

sealed interface Route {
    data class Lesson(val levelIndex: Int, val lessonIndex: Int) : Route
    data class Solfeo(val levelIndex: Int, val solfeoIndex: Int) : Route
    /** Random solfeo of a [difficulty]; [seed] null = pick a fresh one. */
    data class RandomSolfeo(val difficulty: Difficulty, val seed: Long? = null) : Route
    data object Settings : Route
    data object AddProfile : Route
}

/** Root UI state holder (profiles, progress, navigation). Pure state; persistence goes through [ProfileStore]. */
class AppState(
    private val store: ProfileStore,
    val engine: AudioEngine,
    val haptics: Haptics,
    private val systemLanguage: String,
) {
    var levels by mutableStateOf<List<LevelContent?>>(emptyList())
        private set
    var contentLoaded by mutableStateOf(false)
        private set

    var profiles by mutableStateOf(store.loadProfiles())
        private set
    var currentId by mutableStateOf(store.currentId()?.takeIf { id -> profiles.any { it.id == id } } ?: profiles.firstOrNull()?.id)
        private set
    var progress by mutableStateOf(currentId?.let(store::loadProgress) ?: Progress())
        private set

    var tab by mutableStateOf(Tab.HOME)
    /** Section of the Solfeos tab: 0 = lesson solfeos, 1 = random exercises. */
    var solfeosSection by mutableStateOf(0)
    val stack = mutableStateListOf<Route>()

    val profile: Profile? get() = profiles.firstOrNull { it.id == currentId }

    val ui: UiContext
        get() {
            val s = profile?.settings
            val lang = Lang.fromCode(s?.language ?: systemLanguage)
            val naming = s?.let { runCatching { NoteNaming.valueOf(it.noteNaming) }.getOrNull() } ?: NoteNaming.defaultFor(lang)
            return UiContext(stringsFor(lang), lang, naming, s?.kidMode ?: true)
        }

    /** Clef the learner wants to read in, or null to keep each lesson's own clef. */
    val effectiveClef: Clef?
        get() = profile?.settings?.let { ClefPolicy.effective(ClefPreference.fromName(it.clefPreference), Instrument.fromName(it.instrument)) }

    val systemLang: Lang get() = Lang.fromCode(systemLanguage)

    val availableLevels: List<LevelContent> get() = levels.filterNotNull()

    suspend fun loadContent() {
        useLevels(ContentRepository.loadAll())
    }

    /** Sets the loaded levels (index 0 = level 1; null = missing level). */
    fun useLevels(list: List<LevelContent?>) {
        levels = list
        contentLoaded = true
    }

    init { applyVolume() }

    private fun applyVolume() { engine.volume = profile?.settings?.volume ?: 0.8f }

    // ------------------------------------------------------------- navigation

    /** Pushes [r]; locked lessons are refused (returns false). */
    fun push(r: Route): Boolean {
        if (r is Route.Lesson && !canOpenLesson(r.levelIndex, r.lessonIndex)) return false
        stack.add(r)
        return true
    }

    fun canOpenLesson(levelIndex: Int, lessonIndex: Int): Boolean =
        ProgressRepository.canOpenLesson(levels, levelIndex, lessonIndex, progress)
    fun pop(): Boolean = if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex); engine.stop(); true } else false

    // ------------------------------------------------------------- profiles

    fun createProfile(
        name: String, avatar: String, kid: Boolean, lang: Lang, naming: NoteNaming,
        instrument: Instrument = Instrument.OTHER, clefPreference: ClefPreference = ClefPreference.RECOMMENDED,
    ): Profile {
        val p = Profile(
            id = "p" + com.solfi.app.platform.currentTimeMillis().toString(36) + profiles.size,
            name = name.trim().ifEmpty { "Solfi" },
            avatar = avatar,
            settings = ProfileSettings(language = lang.code, noteNaming = naming.name, kidMode = kid,
                instrument = instrument.name, clefPreference = clefPreference.name),
        )
        profiles = profiles + p
        store.saveProfiles(profiles)
        switchProfile(p.id)
        stack.clear()
        return p
    }

    fun switchProfile(id: String) {
        currentId = id
        store.setCurrentId(id)
        progress = store.loadProgress(id)
        tab = Tab.HOME
        applyVolume()
    }

    fun deleteProfile(id: String) {
        store.deleteProfile(id)
        profiles = profiles.filterNot { it.id == id }
        store.saveProfiles(profiles)
        if (currentId == id) {
            val next = profiles.firstOrNull()
            currentId = next?.id
            store.setCurrentId(next?.id)
            progress = next?.let { store.loadProgress(it.id) } ?: Progress()
            stack.clear()
            tab = Tab.HOME
            applyVolume()
        }
    }

    fun updateSettings(change: ProfileSettings.() -> ProfileSettings) {
        val cur = profile ?: return
        val updated = cur.copy(settings = cur.settings.change())
        profiles = profiles.map { if (it.id == cur.id) updated else it }
        store.saveProfiles(profiles)
        applyVolume()
    }

    fun renameProfile(name: String, avatar: String) {
        val cur = profile ?: return
        val updated = cur.copy(name = name.trim().ifEmpty { cur.name }, avatar = avatar)
        profiles = profiles.map { if (it.id == cur.id) updated else it }
        store.saveProfiles(profiles)
    }

    // ------------------------------------------------------------- progress

    private fun saveProgress(p: Progress) {
        progress = p
        currentId?.let { store.saveProgress(it, p) }
    }

    /** The only way lesson progress is written: needs an unlocked lesson and a [session] of that lesson that ran to the end. */
    fun completeLesson(levelIndex: Int, lessonIndex: Int, session: LessonSession): CompletionResult? {
        val level = levels.getOrNull(levelIndex) ?: return null
        if (!canOpenLesson(levelIndex, lessonIndex)) return null
        if (!session.isFinished || session.lesson.id != level.lessons[lessonIndex].id) return null
        val r = ProgressRepository.complete(progress, level, lessonIndex, session.accuracy, todayEpochDay())
        saveProgress(r.progress)
        return r
    }

    fun recordSolfeo() = saveProgress(ProgressRepository.recordSolfeo(progress, todayEpochDay()))

    /** Records the self-assessment of a random exercise; returns the XP granted (0 when capped or "repeat"). */
    fun rateRandomExercise(rating: ProgressRepository.RandomRating): Int {
        val (np, xp) = ProgressRepository.rateRandom(progress, rating, todayEpochDay())
        saveProgress(np)
        return xp
    }

    val randomAwardsLeft: Int get() = ProgressRepository.randomAwardsLeft(progress, todayEpochDay())

    val streak: Int get() = ProgressRepository.currentStreak(progress, todayEpochDay())
}
