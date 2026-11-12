package com.solfi.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.solfi.app.audio.AudioEngine
import com.solfi.app.content.ContentJson
import com.solfi.app.data.ProfileStore
import com.solfi.app.music.Lang
import com.solfi.app.music.NoteNaming
import com.solfi.app.music.generator.Difficulty
import com.solfi.app.platform.Haptics
import com.solfi.app.platform.KeyValueStore
import com.solfi.app.ui.screens.*
import com.solfi.app.ui.theme.LocalUi
import com.solfi.app.ui.theme.SolfiTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders the main screens off-screen with real level-1 content (smoke test + PNGs in build/render). */
class ScreensRenderTest {
    private val kv = KeyValueStore("solfi-render-test")
    private val app = AppState(ProfileStore(kv), AudioEngine(null), Haptics(), "es", com.solfi.app.audio.InstrumentListener(null)).also { a ->
        val dir = listOf("src/commonMain/composeResources/files/content").map(::File).first { it.isDirectory }
        a.useLevels(listOf(ContentJson.decodeLevel(File(dir, "level1.json").readText()), null, null, null, null))
        a.createProfile("Lucía", "🦊", kid = true, lang = Lang.ES, naming = NoteNaming.SOLFEGE)
        for (i in 0..1) {
            val session = com.solfi.app.logic.LessonSession(a.levels[0]!!.lessons[i])
            while (!session.isFinished) {
                val scored = com.solfi.app.logic.LessonSession.isScored(session.currentStep!!)
                session.submit(if (scored) com.solfi.app.logic.StepOutcome.CORRECT else com.solfi.app.logic.StepOutcome.UNSCORED)
            }
            a.completeLesson(0, i, session)
        }
    }

    @AfterTest fun cleanup() { app.profiles.forEach { app.deleteProfile(it.id) }; kv.remove("profiles"); kv.remove("currentProfile") }

    private fun render(name: String, w: Int = 860, h: Int = 1720, theme: String = "LIGHT", content: @Composable () -> Unit) {
        val scene = ImageComposeScene(w, h, Density(2f)) {
            SolfiTheme(theme, kid = app.ui.kid) {
                CompositionLocalProvider(LocalUi provides app.ui) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } }
            }
        }
        val img = scene.render(100_000_000L)
        scene.close()
        File("build/render").mkdirs()
        val bytes = img.encodeToData(EncodedImageFormat.PNG)!!.bytes
        File("build/render/$name.png").writeBytes(bytes)
        assertTrue(bytes.size > 3000, "$name rendered")
    }

    @Test fun home() = render("home") { HomeScreen(app) }
    @Test fun practiceTab() = render("practice", h = 2600) { com.solfi.app.ui.screens.InstrumentPracticeScreen(app) }
    @Test fun practiceTabTrumpet() {
        app.updateSettings { copy(instrument = "TRUMPET") }
        try { render("practice-trumpet", h = 2600, theme = "DARK") { com.solfi.app.ui.screens.InstrumentPracticeScreen(app) } }
        finally { app.updateSettings { copy(instrument = "OTHER") } }
    }
    @Test fun practiceTuner() = render("practice-tuner") { com.solfi.app.ui.screens.PracticeTunerScreen(app) }
    @Test fun practiceNotes() = render("practice-notes") { com.solfi.app.ui.screens.PracticeNotesScreen(app) }
    @Test fun practiceMelodyListening() {
        app.updateSettings { copy(instrument = "SAXOPHONE") }
        try {
            render("practice-melody", h = 2000) {
                com.solfi.app.ui.screens.PracticeMelodyScreen(app, 0, 0)
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    // the alto sax reads the first written note; play it 14 cents sharp at concert pitch
                    val first = com.solfi.app.logic.NoteFollower.targetsOf(app.levels[0]!!.solfeos[0].melody.toMelody()).first().written.first()
                    val f = app.tuning.concert(first).frequency * Math.pow(2.0, 14 / 1200.0)
                    val sr = 44100
                    val buf = FloatArray(sr / 10) { i -> (0.3 * kotlin.math.sin(2 * Math.PI * f * i / sr)).toFloat() }
                    app.listener.feedForTest(buf, buf.size, sr)
                }
            }
        } finally { app.listener.stop(); app.updateSettings { copy(instrument = "OTHER") } }
    }
    @Test fun map() = render("map") { LevelMapScreen(app) }
    @Test fun progress() = render("progress") { ProgressScreen(app) }
    @Test fun settings() = render("settings") { SettingsScreen(app) }
    @Test fun solfeos() = render("solfeos") { SolfeosScreen(app) }
    @Test fun solfeoPlayer() = render("solfeoPlayer") { SolfeoPlayerScreen(app, 0, 2) }
    @Test fun solfeosRandomSection() {
        app.solfeosSection = 1
        try { render("solfeos-random") { SolfeosScreen(app) } } finally { app.solfeosSection = 0 }
    }
    @Test fun randomBasico() = render("random-basico", h = 2300) { RandomSolfeoScreen(app, Difficulty.BASICO, 123456L) }
    @Test fun randomExperto() = render("random-experto", h = 3700) { RandomSolfeoScreen(app, Difficulty.EXPERTO, 654321L) }
    @Test fun randomExpertoDark() = render("random-experto-dark", h = 2300, theme = "DARK") { RandomSolfeoScreen(app, Difficulty.EXPERTO, 654321L) }
    @Test fun solfeoSixteenths() {
        val dir = File("src/commonMain/composeResources/files/content")
        val l4 = ContentJson.decodeLevel(File(dir, "level4.json").readText())
        val saved = app.levels.toList()
        app.useLevels(listOf(saved[0], null, null, l4, null))
        try {
            val idx = l4.solfeos.indexOfFirst { it.id == "s4-sixteenths" }
            assertTrue(idx >= 0)
            render("s4-sixteenths", h = 1400) { SolfeoPlayerScreen(app, 3, idx) }
        } finally { app.useLevels(saved) }
    }

    @Test fun piano() = render("piano") { PianoScreen(app) }
    @Test fun onboarding() = render("onboarding") { OnboardingScreen(app, onCancel = {}) }

    @Test fun onboardingInstrumentsLight() = render("instruments-light", h = 2000, theme = "LIGHT") { OnboardingScreen(app, initialPage = 3) }
    @Test fun onboardingInstrumentsDark() = render("instruments-dark", h = 2000, theme = "DARK") { OnboardingScreen(app, initialPage = 3) }

    @Test fun lessonSteps() {
        // one render per lesson of level 1 (first step) plus the interactive steps
        val level = app.levels[0]!!
        level.lessons.forEachIndexed { li, l ->
            for (si in l.steps.indices) {
                render("lesson-${li + 1}-step-${si + 1}") {
                    StepContent(app, l.steps[si]) {}
                }
            }
        }
    }
}
