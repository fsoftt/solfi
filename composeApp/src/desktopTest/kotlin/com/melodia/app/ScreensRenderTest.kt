package com.melodia.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.melodia.app.audio.AudioEngine
import com.melodia.app.content.ContentJson
import com.melodia.app.data.ProfileStore
import com.melodia.app.music.Lang
import com.melodia.app.music.NoteNaming
import com.melodia.app.platform.Haptics
import com.melodia.app.platform.KeyValueStore
import com.melodia.app.ui.screens.*
import com.melodia.app.ui.theme.LocalUi
import com.melodia.app.ui.theme.MelodiaTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders the main screens off-screen with real level-1 content (smoke test + PNGs in build/render). */
class ScreensRenderTest {
    private val kv = KeyValueStore("melodia-render-test")
    private val app = AppState(ProfileStore(kv), AudioEngine(null), Haptics(), "es").also { a ->
        val dir = listOf("src/commonMain/composeResources/files/content").map(::File).first { it.isDirectory }
        a.useLevels(listOf(ContentJson.decodeLevel(File(dir, "level1.json").readText()), null, null, null, null))
        a.createProfile("Lucía", "🦊", kid = true, lang = Lang.ES, naming = NoteNaming.SOLFEGE)
        a.completeLesson(0, 0, 1.0)
        a.completeLesson(0, 1, 0.8)
    }

    @AfterTest fun cleanup() { app.profiles.forEach { app.deleteProfile(it.id) }; kv.remove("profiles"); kv.remove("currentProfile") }

    private fun render(name: String, w: Int = 860, h: Int = 1720, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(w, h, Density(2f)) {
            MelodiaTheme("LIGHT", kid = app.ui.kid) {
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
    @Test fun map() = render("map") { LevelMapScreen(app) }
    @Test fun progress() = render("progress") { ProgressScreen(app) }
    @Test fun settings() = render("settings") { SettingsScreen(app) }
    @Test fun solfeos() = render("solfeos") { SolfeosScreen(app) }
    @Test fun solfeoPlayer() = render("solfeoPlayer") { SolfeoPlayerScreen(app, 0, 2) }
    @Test fun piano() = render("piano") { PianoScreen(app) }
    @Test fun onboarding() = render("onboarding") { OnboardingScreen(app, onCancel = {}) }

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
