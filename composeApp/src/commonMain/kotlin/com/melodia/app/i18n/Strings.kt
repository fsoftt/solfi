package com.melodia.app.i18n

import com.melodia.app.music.Lang

/**
 * All UI strings. Every language implements this interface, so a missing translation fails to compile.
 * Functions with a `kid` parameter let the tone differ between children and adults.
 */
interface Strings {
    val appName: String
    val tagline: String

    // generic
    val ok: String
    val cancel: String
    val close: String
    val next: String
    val back: String
    val save: String
    val delete: String
    val yes: String
    val no: String
    val loading: String
    val comingSoon: String
    val comingSoonDesc: String

    // onboarding
    val welcomeTitle: String
    val welcomeSubtitle: String
    val chooseLanguage: String
    val yourName: String
    val nameHint: String
    val chooseAvatar: String
    val chooseMode: String
    val modeKid: String
    val modeKidDesc: String
    val modeAdult: String
    val modeAdultDesc: String
    val letsStart: String
    fun noteNamesDefaultHint(solfege: Boolean): String

    // navigation
    val navHome: String
    val navLearn: String
    val navSolfeos: String
    val navPiano: String
    val navProgress: String

    // home
    fun greeting(name: String, kid: Boolean): String
    val continueLesson: String
    val startLearning: String
    val allLessonsDone: String
    fun streakDays(n: Int): String
    val streakLabel: String
    val xpLabel: String
    val starsLabel: String
    fun dailyMotivation(kid: Boolean): String
    val practiceSolfeos: String
    val freePiano: String

    // map
    fun levelLabel(n: Int): String
    val lessonLocked: String
    fun lessonsDone(done: Int, total: Int): String
    val examLabel: String
    val levelLockedHint: String

    // lesson player
    fun stepOf(current: Int, total: Int): String
    val check: String
    val continueBtn: String
    val tryAgain: String
    fun correct(kid: Boolean): String
    fun wrong(kid: Boolean): String
    val play: String
    val pause: String
    val stop: String
    val listenAgain: String
    val playSound: String
    val pressPlayToListen: String
    val whichNoteIsThis: String
    val tapRhythmHint: String
    val getReady: String
    val tapNow: String
    val tapHere: String
    val startRhythm: String
    fun rhythmResult(hits: Int, total: Int): String
    val rhythmGreat: String
    val rhythmTryAgain: String
    val pianoPlayNotes: String
    fun lessonComplete(kid: Boolean): String
    fun examPassed(kid: Boolean): String
    fun examFailed(kid: Boolean): String
    fun xpGained(n: Int): String
    fun accuracyLabel(percent: Int): String
    val badgeEarned: String
    fun levelUnlocked(n: Int): String
    val retry: String
    val backToMap: String
    val exitLesson: String
    val exitLessonText: String
    val keepGoing: String
    val leave: String
    val tipLabel: String

    // solfeos
    val solfeosTitle: String
    val solfeosIntro: String
    val tempo: String
    fun bpm(n: Int): String
    val metronome: String
    val countIn: String
    val loop: String
    val pulseOnly: String
    val pulseOnlyDesc: String
    val showNoteNames: String

    // piano
    val pianoTitle: String
    val pianoHint: String
    val showLabels: String

    // progress
    val progressTitle: String
    val totalXp: String
    val currentStreak: String
    val bestStreak: String
    val badges: String
    val noBadgesYet: String
    val levelsProgress: String
    val lessonsCompleted: String
    val solfeosPlayed: String
    fun xpRank(n: Int): String

    // settings
    val settings: String
    val language: String
    val noteNames: String
    val solfege: String
    val letters: String
    val noteNamesExample: String
    val mode: String
    val theme: String
    val themeSystem: String
    val themeLight: String
    val themeDark: String
    val volume: String
    val haptics: String
    val profiles: String
    val switchProfile: String
    val addProfile: String
    val deleteProfile: String
    fun deleteProfileConfirm(name: String): String
    val activeProfile: String
    val editProfile: String
    val about: String
    val aboutText: String

    // accessibility
    val cdMascot: String
    val cdSettings: String
    val cdBack: String
    val cdPianoKey: String
    val cdStaff: String
    val cdLocked: String
    fun cdStars(n: Int): String
}

fun stringsFor(lang: Lang): Strings = when (lang) {
    Lang.ES -> EsStrings
    Lang.EN -> EnStrings
    Lang.PT -> PtStrings
}
