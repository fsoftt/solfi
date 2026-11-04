package com.melodia.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.melodia.app.audio.AudioEngine
import com.melodia.app.content.ContentRepository
import com.melodia.app.content.LevelContent
import com.melodia.app.data.Profile
import com.melodia.app.data.ProfileSettings
import com.melodia.app.data.ProfileStore
import com.melodia.app.data.Progress
import com.melodia.app.i18n.stringsFor
import com.melodia.app.logic.CompletionResult
import com.melodia.app.logic.ProgressRepository
import com.melodia.app.music.Lang
import com.melodia.app.music.NoteNaming
import com.melodia.app.platform.Haptics
import com.melodia.app.platform.todayEpochDay
import com.melodia.app.ui.theme.UiContext

enum class Tab { HOME, LEARN, SOLFEOS, PIANO, PROGRESS }

sealed interface Route {
    data class Lesson(val levelIndex: Int, val lessonIndex: Int) : Route
    data class Solfeo(val levelIndex: Int, val solfeoIndex: Int) : Route
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
    val stack = mutableStateListOf<Route>()

    val profile: Profile? get() = profiles.firstOrNull { it.id == currentId }

    val ui: UiContext
        get() {
            val s = profile?.settings
            val lang = Lang.fromCode(s?.language ?: systemLanguage)
            val naming = s?.let { runCatching { NoteNaming.valueOf(it.noteNaming) }.getOrNull() } ?: NoteNaming.defaultFor(lang)
            return UiContext(stringsFor(lang), lang, naming, s?.kidMode ?: true)
        }

    val systemLang: Lang get() = Lang.fromCode(systemLanguage)

    val availableLevels: List<LevelContent> get() = levels.filterNotNull()

    suspend fun loadContent() {
        levels = ContentRepository.loadAll()
        contentLoaded = true
    }

    init { applyVolume() }

    private fun applyVolume() { engine.volume = profile?.settings?.volume ?: 0.8f }

    // ------------------------------------------------------------- navigation

    fun push(r: Route) { stack.add(r) }
    fun pop(): Boolean = if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex); engine.stop(); true } else false

    // ------------------------------------------------------------- profiles

    fun createProfile(name: String, avatar: String, kid: Boolean, lang: Lang, naming: NoteNaming): Profile {
        val p = Profile(
            id = "p" + com.melodia.app.platform.currentTimeMillis().toString(36) + profiles.size,
            name = name.trim().ifEmpty { "Melodía" },
            avatar = avatar,
            settings = ProfileSettings(language = lang.code, noteNaming = naming.name, kidMode = kid),
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

    fun completeLesson(levelIndex: Int, lessonIndex: Int, accuracy: Double): CompletionResult? {
        val level = levels.getOrNull(levelIndex) ?: return null
        val r = ProgressRepository.complete(progress, level, lessonIndex, accuracy, todayEpochDay())
        saveProgress(r.progress)
        return r
    }

    fun recordSolfeo() = saveProgress(ProgressRepository.recordSolfeo(progress, todayEpochDay()))

    val streak: Int get() = ProgressRepository.currentStreak(progress, todayEpochDay())
}
