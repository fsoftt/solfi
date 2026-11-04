package com.solfi.app.data

import kotlinx.serialization.Serializable

@Serializable
data class ProfileSettings(
    val language: String = "es",
    /** NoteNaming name: SOLFEGE or LETTERS */
    val noteNaming: String = "SOLFEGE",
    val kidMode: Boolean = true,
    /** SYSTEM, LIGHT or DARK */
    val theme: String = "SYSTEM",
    val volume: Float = 0.8f,
    val haptics: Boolean = true,
    /** Instrument enum name (see Instrument); drives the recommended clef. */
    val instrument: String = "OTHER",
    /** ClefPreference name: RECOMMENDED, TREBLE or BASS. */
    val clefPreference: String = "RECOMMENDED",
)

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val avatar: String,
    val settings: ProfileSettings = ProfileSettings(),
)

@Serializable
data class LessonResult(val stars: Int = 0, val bestAccuracy: Double = 0.0, val completions: Int = 0)

@Serializable
data class Progress(
    val xp: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    /** Epoch day of the last activity, -1 when none. */
    val lastActiveDay: Int = -1,
    /** key = "<level>/<lessonId>" */
    val lessons: Map<String, LessonResult> = emptyMap(),
    val badges: List<String> = emptyList(),
    val examsPassed: List<Int> = emptyList(),
    val solfeosPlayed: Int = 0,
) {
    fun result(level: Int, lessonId: String): LessonResult? = lessons["$level/$lessonId"]
}
