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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.solfi.app.AppState
import com.solfi.app.Route
import com.solfi.app.audio.AudioEngine
import com.solfi.app.audio.PlayOptions
import com.solfi.app.logic.ProgressRepository
import com.solfi.app.music.Clef
import com.solfi.app.music.ClefShift
import com.solfi.app.music.NoteNames
import com.solfi.app.music.generator.Difficulty
import com.solfi.app.music.generator.GenMode
import com.solfi.app.music.generator.GeneratedSolfeo
import com.solfi.app.music.generator.SolfeoGenerator
import com.solfi.app.platform.PlatformBackHandler
import com.solfi.app.ui.notation.StaffView
import com.solfi.app.ui.theme.LocalUi
import com.solfi.app.ui.widgets.PressCard
import kotlin.math.roundToInt

private fun difficultyEmoji(d: Difficulty) = when (d) { Difficulty.BASICO -> "🌱"; Difficulty.INTERMEDIO -> "🌿"; Difficulty.AVANZADO -> "🌳"; Difficulty.EXPERTO -> "🏔️" }

/** A short (6 digit) seed to show to the learner. */
private fun newRandomSeed(): Long = kotlin.random.Random.nextInt(100_000, 1_000_000).toLong()

/** Generation never fails in practice; if it ever did, the next seed is tried. */
internal fun generateSafe(d: Difficulty, seed: Long): GeneratedSolfeo {
    var sd = seed
    repeat(20) {
        val g = runCatching { SolfeoGenerator.generate(d, sd) }.getOrNull()
        if (g != null) return g
        sd++
    }
    return SolfeoGenerator.generate(d, sd)
}

@Composable
private fun keysSummary(d: Difficulty): String {
    val ui = LocalUi.current
    val s = ui.strings
    fun n(step: Int) = NoteNames.base(step, ui.naming, ui.lang)
    return when (d) {
        Difficulty.BASICO -> "${n(0)} ${s.modeName(GenMode.MAJOR)}"
        Difficulty.INTERMEDIO -> "${n(0)}, ${n(4)}, ${n(3)} ${s.modeName(GenMode.MAJOR)}"
        Difficulty.AVANZADO -> "${s.modeName(GenMode.MAJOR)}, ${s.modeName(GenMode.HARMONIC_MINOR)}"
        Difficulty.EXPERTO -> listOf(GenMode.MAJOR, GenMode.NATURAL_MINOR, GenMode.DORIAN, GenMode.MIXOLYDIAN).joinToString(", ") { s.modeName(it) }
    }
}

/** Card of the "Aleatorios" section of the Solfeos tab. Always open: random exercises are not gated by level. */
@Composable
fun RandomDifficultyCard(app: AppState, d: Difficulty) {
    val ui = LocalUi.current
    val s = ui.strings
    PressCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), onClick = { app.push(Route.RandomSolfeo(d)) }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Text(difficultyEmoji(d), fontSize = 26.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(s.difficultyName(d), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Text(s.difficultyConcepts(d, keysSummary(d)), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                Text("${d.meters.joinToString(" ")} · ${d.tempoRange.first}–${s.bpm(d.tempoRange.last)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

private const val MODE_NONE = 0
private const val MODE_PULSE = 1
private const val MODE_SOLUTION = 2

/**
 * Random exercise. Does not auto-play: the learner practices with the pulse (count-in, no melody) and then
 * listens to the solution. Self-assessment grants a little XP (capped per day) and never touches lesson stars.
 */
@Composable
fun RandomSolfeoScreen(app: AppState, difficulty: Difficulty, initialSeed: Long? = null) {
    val ui = LocalUi.current
    val s = ui.strings
    val engine = app.engine
    var requestedSeed by remember { mutableStateOf(initialSeed ?: newRandomSeed()) }
    val gen = remember(difficulty, requestedSeed) { generateSafe(difficulty, requestedSeed) }

    // Session clef override: 0 = Auto (profile preference), 1 = treble, 2 = bass.
    var clefMode by remember { mutableIntStateOf(0) }
    val sessionClef: Clef? = when (clefMode) { 1 -> Clef.TREBLE; 2 -> Clef.BASS; else -> app.effectiveClef }
    val melody = remember(gen, sessionClef) { sessionClef?.let { ClefShift.toClef(gen.melody, it) } ?: gen.melody }

    var tempo by remember(gen) { mutableFloatStateOf(gen.tempo.toFloat()) }
    var metronome by remember { mutableStateOf(true) }
    var countIn by remember { mutableStateOf(true) }
    var loop by remember { mutableStateOf(false) }
    var showNames by remember { mutableStateOf(true) }
    var mode by remember { mutableIntStateOf(MODE_NONE) }
    var pausedAt by remember { mutableDoubleStateOf(0.0) }
    var seedText by remember { mutableStateOf("") }
    var locked by remember(gen) { mutableStateOf(false) }
    var feedback by remember(gen) { mutableStateOf<String?>(null) }
    val playing by engine.isPlaying.collectAsState()
    val pos by engine.position.collectAsState()
    val hi = engineHighlight(engine, melody)
    val pa = rememberPlayAlong(app, melody)
    val paHi = if (pa.active) pa.highlight(app) else -1

    PlatformBackHandler(true) { app.pop() }
    LaunchedEffect(Unit) { engine.stop() }

    fun start(newMode: Int, from: Double = 0.0) {
        pa.stop()
        mode = newMode
        val solution = newMode == MODE_SOLUTION
        engine.playMelody(
            melody,
            PlayOptions(tempo = tempo.roundToInt(), metronome = metronome || !solution, countIn = countIn, loop = loop, pulseOnly = !solution, fromBeat = from, onFinished = { mode = MODE_NONE }),
        )
    }
    fun toggle(target: Int) {
        if (playing && mode == target) { engine.stop(); mode = MODE_NONE } else { engine.stop(); start(target) }
    }
    fun restartIfPlaying() { if (playing) start(mode, pos.coerceAtLeast(0.0)) }
    fun newExercise(seed: Long) {
        engine.stop(); mode = MODE_NONE; pausedAt = 0.0
        requestedSeed = seed
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ app.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, s.cdBack) }
            Column(Modifier.weight(1f)) {
                Text(s.randomTitle(gen.seed), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1)
                Text(
                    "${s.difficultyName(difficulty)} · ${ui.noteName(gen.tonic)} ${s.modeName(gen.mode)} · ${melody.time} · ${s.bpm(gen.tempo)}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PressCard(Modifier.fillMaxWidth()) {
                Box(Modifier.padding(12.dp)) {
                    StaffView(
                        melody, labelFor = if (showNames) ({ p -> ui.noteName(p) }) else null,
                        highlightIndex = if (pa.active) paHi else if (mode == MODE_SOLUTION && playing) hi else -1,
                        onNoteClick = { e -> e.pitches.forEach { engine.playPitch(it, 0.7) } }, description = s.cdStaff,
                    )
                }
            }
            Text(s.randomHint(ui.kid), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
            PulseDots(melody.time.clickTicks, melody.time.measureTicks, if (playing) pos else AudioEngine.IDLE)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val pulsePlaying = playing && mode == MODE_PULSE
                val solPlaying = playing && mode == MODE_SOLUTION
                Button({ toggle(MODE_PULSE) }, Modifier.weight(1f).height(56.dp)) {
                    Icon(if (pulsePlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.size(6.dp))
                    Text(if (pulsePlaying) s.stop else s.practicePulse, fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 16.sp, fontSize = 14.sp)
                }
                FilledTonalButton({ toggle(MODE_SOLUTION) }, Modifier.weight(1f).height(56.dp)) {
                    Icon(if (solPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.size(6.dp))
                    Text(if (solPlaying) s.stop else s.listenSolution, fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 16.sp, fontSize = 14.sp)
                }
            }

            PlayAlongButton(pa)
            PlayAlongPanel(app, pa)

            // self-assessment
            PressCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(s.howDidItGo, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        fun rate(r: ProgressRepository.RandomRating) {
                            val xp = app.rateRandomExercise(r)
                            when {
                                r == ProgressRepository.RandomRating.REPEAT -> feedback = s.randomRepeatHint(ui.kid)
                                xp > 0 -> { feedback = s.xpGained(xp); locked = true }
                                else -> { feedback = s.randomCapReached; locked = true }
                            }
                        }
                        Button({ rate(ProgressRepository.RandomRating.GOOD) }, Modifier.weight(1f), enabled = !locked) { Text(s.rateGood, maxLines = 1) }
                        FilledTonalButton({ rate(ProgressRepository.RandomRating.OK) }, Modifier.weight(1f), enabled = !locked) { Text(s.rateOk, maxLines = 1) }
                        OutlinedButton({ rate(ProgressRepository.RandomRating.REPEAT) }, Modifier.weight(1f), enabled = !locked) { Text(s.rateRepeat, maxLines = 1) }
                    }
                    feedback?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                }
            }

            Button({ newExercise(newRandomSeed()) }, Modifier.fillMaxWidth().height(52.dp)) { Text(s.newExercise, fontWeight = FontWeight.Bold) }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    seedText, { v -> seedText = v.filter { it.isDigit() }.take(9) }, Modifier.weight(1f),
                    label = { Text(s.goToExercise) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                FilledTonalButton({ seedText.toLongOrNull()?.let { newExercise(it) } }, enabled = seedText.isNotEmpty()) { Text(s.goLabel) }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.clefQuickTitle, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                listOf(s.clefQuickSol, s.clefQuickFa, s.clefAuto).forEachIndexed { i, label ->
                    val m = when (i) { 0 -> 1; 1 -> 2; else -> 0 }
                    FilterChip(clefMode == m, { clefMode = m; if (playing) { engine.stop(); mode = MODE_NONE } }, { Text(label) })
                }
            }
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(s.tempo, fontWeight = FontWeight.Bold)
                    Text(s.bpm(tempo.roundToInt()), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Slider(tempo, { tempo = it }, valueRange = 40f..160f, onValueChangeFinished = { restartIfPlaying() })
            }
            ToggleRow(s.metronome, metronome, enabled = true) { metronome = it; restartIfPlaying() }
            ToggleRow(s.countIn, countIn) { countIn = it }
            ToggleRow(s.loop, loop) { loop = it; restartIfPlaying() }
            ToggleRow(s.showNoteNames, showNames) { showNames = it }
            Spacer(Modifier.height(16.dp))
        }
    }
}
