package com.solfi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.solfi.app.AppState
import com.solfi.app.Route
import com.solfi.app.audio.PlayOptions
import com.solfi.app.logic.PlayTarget
import com.solfi.app.logic.PracticeNotes
import com.solfi.app.logic.ProgressRepository
import com.solfi.app.music.ClefShift
import com.solfi.app.music.Melody
import com.solfi.app.music.Pitch
import com.solfi.app.music.PitchJudge
import com.solfi.app.music.generator.Difficulty
import com.solfi.app.platform.MicStatus
import com.solfi.app.platform.PlatformBackHandler
import com.solfi.app.ui.icons.InstrumentIcon
import com.solfi.app.ui.notation.StaffView
import com.solfi.app.ui.theme.LocalUi
import com.solfi.app.ui.theme.Palette
import com.solfi.app.ui.theme.parseHexColor
import com.solfi.app.ui.widgets.PressCard
import com.solfi.app.ui.widgets.ScreenTitle
import kotlin.math.roundToInt
import kotlin.random.Random

/** "Practica con tu instrumento" tab: instrument summary and the exercises that listen through the microphone. */
@Composable
fun InstrumentPracticeScreen(app: AppState) {
    val ui = LocalUi.current
    val s = ui.strings
    val tuning = app.tuning
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenTitle(s.practiceTitle, subtitle = s.practiceIntro)

        // instrument card
        PressCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InstrumentIcon(app.instrument, 44.dp)
                    Column(Modifier.weight(1f)) {
                        Text(s.instrumentName(app.instrument), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        val c = Pitch(0, 0, 5)
                        Text(
                            s.transposeExplain(tuning.transpose, ui.noteName(c), ui.noteName(tuning.concert(c))),
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp,
                        )
                    }
                }
                TextButton({ app.push(Route.Settings) }) { Text(s.changeInstrument) }
                if (!tuning.octaveFree) {
                    ToggleRow(s.ignoreOctave, app.profile?.settings?.ignoreOctave == true, s.ignoreOctaveDesc) { v -> app.updateSettings { copy(ignoreOctave = v) } }
                }
            }
        }

        PracticeCard("🎚️", s.tunerTitle, s.tunerDesc) { app.push(Route.PracticeTuner) }
        PracticeCard("👀", s.readAndPlayTitle, s.readAndPlayDesc) { app.push(Route.PracticeNotes) }

        Text(s.randomMelodiesTitle, Modifier.padding(start = 20.dp, top = 8.dp), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(s.randomMelodiesDesc, Modifier.padding(horizontal = 20.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Difficulty.entries.forEach { d ->
                FilterChip(false, { app.push(Route.PracticeMelody(Route.PracticeMelody.RANDOM, d.ordinal)) }, { Text(s.difficultyName(d), maxLines = 1, fontSize = 13.sp) })
            }
        }

        Text(s.melodiesTitle, Modifier.padding(start = 20.dp, top = 8.dp), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(s.melodiesDesc, Modifier.padding(horizontal = 20.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        app.levels.forEachIndexed { li, level ->
            if (level == null || !ProgressRepository.isLevelUnlocked(level.level, app.progress)) return@forEachIndexed
            level.solfeos.forEachIndexed { si, sf ->
                PressCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp), onClick = { app.push(Route.PracticeMelody(li, si)) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(parseHexColor(level.color).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Text("🎼", fontSize = 22.sp) }
                        Column(Modifier.weight(1f)) {
                            Text(sf.title[ui.lang], fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(s.levelLabel(level.level), fontSize = 12.sp, color = parseHexColor(level.color))
                        }
                        Text("🎤", fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeCard(emoji: String, title: String, desc: String, onClick: () -> Unit) {
    PressCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp), onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 26.sp) }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Text(desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
            }
        }
    }
}

/** Back arrow + title row used by the practice sub-screens. */
@Composable
private fun PracticeTopBar(app: AppState, title: String, subtitle: String? = null) {
    val s = LocalUi.current.strings
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton({ app.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, s.cdBack) }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        InstrumentIcon(app.instrument, 32.dp, Modifier.padding(end = 12.dp))
    }
}

@Composable
private fun MicStatusText(status: MicStatus?) {
    val s = LocalUi.current.strings
    when (status) {
        MicStatus.DENIED -> Text(s.micDenied, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Palette.Danger)
        MicStatus.UNAVAILABLE -> Text(s.micUnavailable, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Palette.Danger)
        MicStatus.STARTING, null -> Text(s.micStarting, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MicStatus.LISTENING -> {}
    }
}

// ------------------------------------------------------------------ tuner

/** Shows the note being played, written for the learner's instrument and at concert pitch, with a tuning meter. */
@Composable
fun PracticeTunerScreen(app: AppState) {
    val ui = LocalUi.current
    val s = ui.strings
    val tuning = app.tuning
    PlatformBackHandler(true) { app.pop() }
    DisposableEffect(tuning) {
        app.engine.stop()
        val id = app.listener.listen(tuning)
        onDispose { app.listener.stop(id) }
    }
    val status by app.listener.status.collectAsState()
    val reading by app.listener.reading.collectAsState()
    val f = reading?.frequency?.takeIf { (reading?.clarity ?: 0.0) >= 0.75 }
    val concertMidi = f?.let { PitchJudge.midiOf(it).roundToInt() }
    val judgement = if (f != null && concertMidi != null) PitchJudge.judge(f, concertMidi) else null

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        PracticeTopBar(app, s.tunerTitle, s.instrumentName(app.instrument))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            MicStatusText(status)
            if (concertMidi != null) {
                val written = tuning.written(concertMidi)
                Text(s.writtenLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Text(ui.noteName(written, withOctave = true), fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = judgement?.let { verdictColor(it.verdict) } ?: MaterialTheme.colorScheme.onSurface)
                if (tuning.transpose != 0) {
                    Text("${s.concertLabel}: ${ui.noteName(Pitch.fromMidi(concertMidi), withOctave = true)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${(f * 10).roundToInt() / 10.0} Hz", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // staff with the written note
                val clef = PracticeNotes.clefFor(app.effectiveClef)
                val shown = remember(written.midi, clef) { Melody.parse(clef.code, "4/4", "C", 80, "$written:w") }
                PressCard(Modifier.fillMaxWidth()) { Box(Modifier.padding(12.dp)) { StaffView(shown, showTime = false, gap = 12.dp, description = s.cdStaff) } }
            } else if (status == MicStatus.LISTENING) {
                Text("🎵", fontSize = 56.sp)
                Text(s.tunerPlaySomething, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PitchMeter(judgement)
            judgement?.let { Text(s.verdictText(it.verdict, ui.kid), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = verdictColor(it.verdict)) }
        }
    }
}

// ------------------------------------------------------------------ read and play

/** One random written note at a time; the learner plays it and gets high / low / in-tune feedback. */
@Composable
fun PracticeNotesScreen(app: AppState) {
    val ui = LocalUi.current
    val s = ui.strings
    val tuning = app.tuning
    val clef = PracticeNotes.clefFor(app.effectiveClef)
    val rnd = remember { Random(com.solfi.app.platform.currentTimeMillis()) }
    var accidentals by remember { mutableStateOf(false) }
    var note by remember(tuning, clef, accidentals) { mutableStateOf(PracticeNotes.next(tuning, clef, accidentals, rnd)) }
    var round by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    val targets = remember(note, round) { listOf(PlayTarget(listOf(note), 0)) }
    val pa = rememberPlayAlong(app, targets)
    val st by app.listener.follow.collectAsState()
    val melody = remember(note, clef) { Melody.parse(clef.code, "4/4", "C", 80, "$note:w") }
    PlatformBackHandler(true) { app.pop() }
    LaunchedEffect(pa) { pa.start() }

    fun next() { note = PracticeNotes.next(tuning, clef, accidentals, rnd, note); round++ }

    LaunchedEffect(st.done, pa) {
        if (pa.active && st.done && st.results.isNotEmpty()) {
            streak = if (st.results[0].mistakes == 0) streak + 1 else 0
            feedback(app, true)
            kotlinx.coroutines.delay(1200)
            next()
        }
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        PracticeTopBar(app, s.readAndPlayTitle, s.instrumentName(app.instrument))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("🔥 " + s.inARow(streak), fontWeight = FontWeight.Bold, color = Palette.Coral, modifier = Modifier.weight(1f))
                FilterChip(accidentals, { accidentals = !accidentals; round++ }, { Text(s.withAccidentals, fontSize = 13.sp) })
            }
            PressCard(Modifier.fillMaxWidth()) {
                Box(Modifier.padding(12.dp)) {
                    StaffView(melody, showTime = false, gap = 14.dp, highlightIndex = if (st.done) -1 else 0, description = s.cdStaff)
                }
            }
            if (st.done && pa.active) {
                val r = st.results.firstOrNull()
                Text(
                    (r?.let { s.verdictText(it.verdict, ui.kid) + " · " + centsText(it.cents) } ?: "") + "  " + ui.noteName(note),
                    Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp,
                    color = r?.let { verdictColor(it.verdict) } ?: Palette.Success,
                )
            } else {
                PlayAlongPanel(app, pa)
            }
            TextButton({ streak = 0; next() }, Modifier.align(Alignment.CenterHorizontally)) { Text(s.nextNote + " →") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ------------------------------------------------------------------ melodies

/** A lesson solfeo ([levelIndex], [solfeoIndex]) or a random one ([Route.PracticeMelody.RANDOM], difficulty ordinal). */
@Composable
fun PracticeMelodyScreen(app: AppState, levelIndex: Int, index: Int) {
    val ui = LocalUi.current
    val s = ui.strings
    var seed by remember { mutableLongStateOf(Random.nextInt(100_000, 1_000_000).toLong()) }
    val random = levelIndex == Route.PracticeMelody.RANDOM
    val difficulty = Difficulty.entries.getOrNull(index) ?: Difficulty.BASICO
    val solfeo = if (random) null else app.levels.getOrNull(levelIndex)?.solfeos?.getOrNull(index)
    val base: Melody? = remember(levelIndex, index, seed) {
        if (random) generateSafe(difficulty, seed).melody else solfeo?.let { runCatching { it.melody.toMelody() }.getOrNull() }
    }
    val clef = app.effectiveClef
    val melody = remember(base, clef) { base?.let { b -> if (clef != null) ClefShift.toClef(b, clef) else b } }
    PlatformBackHandler(true) { app.pop() }
    if (melody == null) { LaunchedEffect(Unit) { app.pop() }; return }
    val pa = rememberPlayAlong(app, melody)
    val playing by app.engine.isPlaying.collectAsState()
    val engineHi = engineHighlight(app.engine, melody)
    val hi = if (pa.active) pa.highlight(app) else engineHi
    var showNames by remember { mutableStateOf(true) }
    LaunchedEffect(pa) { pa.start() }

    val title = if (random) s.randomTitle(seed) else solfeo?.title?.get(ui.lang) ?: ""
    val subtitle = if (random) s.difficultyName(difficulty) else s.levelLabel(app.levels.getOrNull(levelIndex)?.level ?: 0)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        PracticeTopBar(app, title, subtitle)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PressCard(Modifier.fillMaxWidth()) {
                Box(Modifier.padding(12.dp)) {
                    StaffView(melody, labelFor = if (showNames) ({ p -> ui.noteName(p) }) else null, highlightIndex = hi, description = s.cdStaff)
                }
            }
            PlayAlongPanel(app, pa, onFinished = { app.recordSolfeo() })
            PlayAlongButton(pa)
            com.solfi.app.ui.widgets.SecondaryButton(
                if (playing) "■  " + s.stop else "🔊  " + s.listenAgain,
                {
                    if (playing) app.engine.stop()
                    else { pa.stop(); app.engine.playMelody(melody, PlayOptions(tempo = melody.tempo)) }
                },
            )
            ToggleRow(s.showNoteNames, showNames) { showNames = it }
            if (random) TextButton({ pa.stop(); app.engine.stop(); seed = Random.nextInt(100_000, 1_000_000).toLong() }, Modifier.align(Alignment.CenterHorizontally)) { Text(s.newExercise + " →") }
        }
    }
}
