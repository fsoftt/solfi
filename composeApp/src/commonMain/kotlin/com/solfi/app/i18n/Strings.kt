package com.solfi.app.i18n

import com.solfi.app.music.generator.Difficulty
import com.solfi.app.music.generator.GenMode
import com.solfi.app.music.Lang

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

    // clef / instrument
    val instrumentTitle: String
    fun instrumentName(i: com.solfi.app.music.Instrument): String
    val clefTitle: String
    val clefRecommended: String
    val clefTreble: String
    val clefBass: String
    fun clefRecommendedIs(clefName: String): String
    /** Why this clef is recommended; [clef] null = the lesson's own clef is kept. */
    fun clefReason(clef: com.solfi.app.music.Clef?): String
    val clefSettingsTitle: String
    val instrumentSettingsTitle: String
    fun clefLockedNote(bass: Boolean): String
    val clefAuto: String
    val clefQuickSol: String
    val clefQuickFa: String
    val clefQuickTitle: String

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
    val skipLevelButton: String
    fun skipLevelTitle(n: Int): String
    fun skipLevelText(n: Int, percent: Int): String
    val startSkipTest: String
    fun skipTestLabel(n: Int): String
    fun levelSkipped(n: Int): String

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
    val hintListenExample: String
    val hintListenToEnd: String
    val hintAnswer: String
    val hintPlaySoundFirst: String
    val hintPlayNotes: String
    val hintTapRhythm: String
    val hintWait: String
    val reviewStepLabel: String
    val wrongWillRepeat: String
    fun examNeedPercent(percent: Int): String
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

    // random solfeos
    val solfeosTabLessons: String
    val solfeosTabRandom: String
    val randomIntro: String
    fun difficultyName(d: Difficulty): String
    fun difficultyConcepts(d: Difficulty, keys: String): String
    fun modeName(m: GenMode): String
    fun randomTitle(seed: Long): String
    fun randomHint(kid: Boolean): String
    val practicePulse: String
    val listenSolution: String
    val newExercise: String
    val goToExercise: String
    val goLabel: String
    val howDidItGo: String
    val rateGood: String
    val rateOk: String
    val rateRepeat: String
    val randomCapReached: String
    fun randomRepeatHint(kid: Boolean): String

    // accessibility
    val cdMascot: String
    val cdSettings: String

    // play along with a real instrument
    val navPractice: String
    val practiceTitle: String
    val practiceIntro: String
    val playWithInstrument: String
    val stopListening: String
    val micStarting: String
    val micDenied: String
    val micUnavailable: String
    fun playAlongHint(kid: Boolean): String
    fun verdictText(v: com.solfi.app.music.PitchVerdict, kid: Boolean): String
    /** [heard] was played instead of [expected]; [higher] = the played note is higher. */
    fun wrongNoteDetail(heard: String, expected: String, higher: Boolean): String
    val playItAgain: String
    fun playAlongSummary(total: Int, firstTry: Int, inTune: Int): String
    val playAgain: String
    val hearNote: String
    fun noteProgress(i: Int, n: Int): String
    /** Explains how the instrument transposes: written C ([writtenC]) sounds [soundsAs]. */
    fun transposeExplain(transpose: Int, writtenC: String, soundsAs: String): String
    val ignoreOctave: String
    val ignoreOctaveDesc: String
    val tunerTitle: String
    val tunerDesc: String
    val tunerPlaySomething: String
    val writtenLabel: String
    val concertLabel: String
    val readAndPlayTitle: String
    val readAndPlayDesc: String
    val melodiesTitle: String
    val melodiesDesc: String
    val randomMelodiesTitle: String
    val randomMelodiesDesc: String
    val withAccidentals: String
    fun inARow(n: Int): String
    val nextNote: String
    val changeInstrument: String
    val inputKeyboard: String
    val inputInstrument: String
    val anyOctaveAuto: String

    // NGL: ensemble pieces (one practisable part per instrument and voice)
    val navNgl: String
    val nglTitle: String
    val nglIntro: String
    val nglEmpty: String
    val nglSong: String
    val nglEnsembleTitle: String
    val nglEnsembleDesc: String
    val nglAccompaniment: String
    val nglAccompanimentDesc: String
    val nglStartTake: String
    val nglStopTake: String
    val nglStepByStep: String
    fun nglTakeSummary(total: Int, played: Int, onTime: Int, inTune: Int): String
    fun nglReview(measures: String): String
    val nglAllGood: String
    val nglLegend: String
    fun nglNowPlay(note: String): String
    val nglInstrument: String
    val nglVoice: String
    fun nglVoiceN(n: Int): String
    val nglListenPart: String
    val nglListenAll: String
    val glockenspiel: String
    fun nglPartSummary(measures: Int, tempo: Int): String

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
