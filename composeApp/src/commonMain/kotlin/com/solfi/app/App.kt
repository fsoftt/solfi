package com.solfi.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.solfi.app.audio.AudioEngine
import com.solfi.app.data.ProfileStore
import com.solfi.app.platform.AudioSink
import com.solfi.app.platform.Haptics
import com.solfi.app.platform.KeyValueStore
import com.solfi.app.platform.PlatformBackHandler
import com.solfi.app.platform.systemLanguageCode
import com.solfi.app.ui.screens.HomeScreen
import com.solfi.app.ui.screens.InstrumentPracticeScreen
import com.solfi.app.ui.screens.PracticeMelodyScreen
import com.solfi.app.ui.screens.PracticeNotesScreen
import com.solfi.app.ui.screens.PracticeTunerScreen
import com.solfi.app.ui.screens.LessonScreen
import com.solfi.app.ui.screens.LevelMapScreen
import com.solfi.app.ui.screens.OnboardingScreen
import com.solfi.app.ui.screens.PianoScreen
import com.solfi.app.ui.screens.ProgressScreen
import com.solfi.app.ui.screens.RandomSolfeoScreen
import com.solfi.app.ui.screens.SettingsScreen
import com.solfi.app.ui.screens.SolfeoPlayerScreen
import com.solfi.app.ui.screens.SolfeosScreen
import com.solfi.app.ui.theme.LocalUi
import com.solfi.app.ui.theme.SolfiTheme

@Composable
fun App() {
    val app = remember {
        AppState(ProfileStore(KeyValueStore("solfi")), AudioEngine(AudioSink()), Haptics(), systemLanguageCode())
    }
    LaunchedEffect(Unit) { app.loadContent() }
    DisposableEffect(Unit) { onDispose { app.engine.shutdown() } }

    val settings = app.profile?.settings
    SolfiTheme(themeSetting = settings?.theme ?: "SYSTEM", kid = settings?.kidMode ?: true) {
        CompositionLocalProvider(LocalUi provides app.ui) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                // Large screens (tablets, landscape, desktop): keep a centered phone-width column.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = 600.dp).fillMaxSize()) {
                        if (app.profile == null) {
                            OnboardingScreen(app)
                        } else {
                            Root(app)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Root(app: AppState) {
    val top = app.stack.lastOrNull()
    PlatformBackHandler(enabled = top != null) { app.pop() }
    AnimatedContent(
        targetState = top,
        transitionSpec = {
            if (targetState != null) (slideInVertically { it / 8 } + fadeIn()) togetherWith fadeOut()
            else fadeIn() togetherWith (slideOutVertically { it / 8 } + fadeOut())
        },
        label = "route",
    ) { route ->
        when (route) {
            null -> MainTabs(app)
            is Route.Lesson -> LessonScreen(app, route.levelIndex, route.lessonIndex)
            is Route.SkipTest -> LessonScreen(app, route.levelIndex, app.levels.getOrNull(route.levelIndex)?.lessons?.lastIndex ?: -1, skipTest = true)
            is Route.Solfeo -> SolfeoPlayerScreen(app, route.levelIndex, route.solfeoIndex)
            is Route.RandomSolfeo -> RandomSolfeoScreen(app, route.difficulty, route.seed)
            Route.PracticeTuner -> PracticeTunerScreen(app)
            Route.PracticeNotes -> PracticeNotesScreen(app)
            is Route.PracticeMelody -> PracticeMelodyScreen(app, route.levelIndex, route.index)
            Route.Settings -> SettingsScreen(app)
            Route.AddProfile -> OnboardingScreen(app, onCancel = { app.pop() })
        }
    }
}

@Composable
private fun MainTabs(app: AppState) {
    val s = LocalUi.current.strings
    val items = listOf(
        Triple(Tab.HOME, "🏠", s.navHome),
        Triple(Tab.LEARN, "🗺️", s.navLearn),
        Triple(Tab.SOLFEOS, "🎼", s.navSolfeos),
        Triple(Tab.PRACTICE, "", s.navPractice),
        Triple(Tab.PIANO, "🎹", s.navPiano),
        Triple(Tab.PROGRESS, "🏆", s.navProgress),
    )
    // leaving a tab silences whatever was playing
    LaunchedEffect(app.tab) { app.engine.stop(); app.listener.stop() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                items.forEach { (tab, icon, label) ->
                    NavigationBarItem(
                        selected = app.tab == tab, onClick = { app.tab = tab },
                        icon = { if (tab == Tab.PRACTICE) com.solfi.app.ui.icons.InstrumentIcon(app.instrument, 26.dp) else Text(icon, fontSize = 22.sp) }, label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                        alwaysShowLabel = true,
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            AnimatedContent(app.tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tabs") { tab ->
                when (tab) {
                    Tab.HOME -> HomeScreen(app)
                    Tab.LEARN -> LevelMapScreen(app)
                    Tab.SOLFEOS -> SolfeosScreen(app)
                    Tab.PRACTICE -> InstrumentPracticeScreen(app)
                    Tab.PIANO -> PianoScreen(app)
                    Tab.PROGRESS -> ProgressScreen(app)
                }
            }
        }
    }
}
