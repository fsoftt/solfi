package com.solfi.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.solfi.app.AppState
import com.solfi.app.audio.AudioEngine
import com.solfi.app.audio.PlayOptions
import com.solfi.app.content.MelodySpec
import com.solfi.app.content.Option
import com.solfi.app.content.Step
import com.solfi.app.logic.RhythmScorer
import com.solfi.app.music.Clef
import com.solfi.app.music.ClefShift
import com.solfi.app.music.Melody
import com.solfi.app.music.Pitch
import com.solfi.app.ui.notation.StaffView
import com.solfi.app.logic.StepCompletionPolicy
import com.solfi.app.logic.StepOutcome
import com.solfi.app.logic.StepState
import com.solfi.app.ui.theme.LocalUi
import com.solfi.app.ui.theme.Palette
import com.solfi.app.ui.widgets.BigButton
import com.solfi.app.ui.widgets.FeedbackPanel
import com.solfi.app.ui.widgets.PianoKeyboard
import com.solfi.app.ui.widgets.PressCard
import com.solfi.app.ui.widgets.SecondaryButton
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Feedback side effects shared by every interactive step. */
fun feedback(app: AppState, success: Boolean) {
    app.engine.playFeedback(success)
    if (app.profile?.settings?.haptics != false) app.haptics.feedback(success)
}

/**
 * Renders one lesson step. [onDone] receives the outcome when the learner leaves the step; every step gates its own
 * "Continuar" through [StepCompletionPolicy]. In exams ([isExam]) a wrong answer is not repeated.
 */
@Composable
fun StepContent(app: AppState, step: Step, isExam: Boolean = false, onDone: (StepOutcome) -> Unit) {
    when (step) {
        is Step.Explain -> ExplainStep(app, step) { onDone(StepOutcome.UNSCORED) }
        is Step.Listen -> ListenStep(app, step) { onDone(StepOutcome.UNSCORED) }
        is Step.Quiz -> QuizStep(app, step, isExam, onDone)
        is Step.IdentifyNote -> IdentifyNoteStep(app, step, isExam, onDone)
        is Step.EarTraining -> EarTrainingStep(app, step, isExam, onDone)
        is Step.Piano -> PianoStep(app, step, isExam, onDone)
        is Step.RhythmTap -> RhythmTapStep(app, step, onDone)
    }
}

// ------------------------------------------------------------------ explain / listen

@Composable
private fun ExplainStep(app: AppState, step: Step.Explain, onNext: () -> Unit) {
    val ui = LocalUi.current
    val bounce by animateFloatAsState(1f, spring(Spring.DampingRatioHighBouncy), label = "emoji")
    // A melody that cannot be parsed is not shown, so it cannot be required either.
    val gated = if (step.melody != null && rememberMelody(step.melody, null) == null) step.copy(melody = null) else step
    var st by remember { mutableStateOf(StepState()) }
    LaunchedEffect(Unit) { delay(StepCompletionPolicy.TEXT_ONLY_DELAY_MS); st = st.copy(elapsedMs = StepCompletionPolicy.TEXT_ONLY_DELAY_MS) }
    StepFrame(bottom = { GatedContinue(gated, st) { if (StepCompletionPolicy.canContinue(gated, st)) onNext() } }) {
        step.emoji?.let { Text(it, Modifier.align(Alignment.CenterHorizontally).scale(bounce), fontSize = if (ui.kid) 64.sp else 48.sp) }
        step.title?.let { BigText(it[ui.lang], size = if (ui.kid) 26 else 22) }
        Body(step.text[ui.lang])
        step.melody?.let { MelodyPlayerCard(app, it, onPlayedToEnd = { st = st.copy(playedToEnd = true) }) }
    }
}

@Composable
private fun ListenStep(app: AppState, step: Step.Listen, onNext: () -> Unit) {
    val ui = LocalUi.current
    var st by remember { mutableStateOf(StepState()) }
    StepFrame(bottom = { GatedContinue(step, st) { if (StepCompletionPolicy.canContinue(step, st)) onNext() } }) {
        Text("🎧", Modifier.align(Alignment.CenterHorizontally), fontSize = 52.sp)
        BigText(step.prompt[ui.lang])
        MelodyPlayerCard(app, step.melody, onPlayedToEnd = { st = st.copy(playedToEnd = true) })
    }
}

// ------------------------------------------------------------------ choice based steps

private class ChoiceItem(val label: String?, val melody: Melody?, val tempo: Int = 80, val pitch: Pitch? = null)

@Composable
private fun ChoiceStep(
    app: AppState,
    step: Step,
    options: List<ChoiceItem>,
    answer: Int,
    explanation: String?,
    isExam: Boolean,
    onDone: (StepOutcome) -> Unit,
    /** Ear training: options stay locked until the sound has been played. */
    soundPlayed: Boolean = true,
    header: @Composable ColumnScope.() -> Unit,
) {
    val ui = LocalUi.current
    val s = ui.strings
    var selected by remember { mutableIntStateOf(-1) }
    var checked by remember { mutableStateOf(false) }
    val correct = checked && selected == answer
    val st = StepState(answered = checked, soundPlayed = soundPlayed)
    val unlocked = StepCompletionPolicy.answersEnabled(step, st)

    StepFrame(bottom = {
        AnimatedVisibility(checked, enter = slideInVertically { it } + fadeIn()) {
            FeedbackPanel(
                correct = correct,
                message = if (correct) s.correct(ui.kid) else s.wrong(ui.kid),
                detail = if (correct) explanation else listOfNotNull(explanation, if (isExam) null else s.wrongWillRepeat).joinToString("\n"),
                primaryLabel = s.continueBtn,
                onPrimary = { if (StepCompletionPolicy.canContinue(step, st)) onDone(if (correct) StepOutcome.CORRECT else StepOutcome.WRONG) },
            )
        }
        if (!checked) BottomBar {
            BigButton(s.check, {
                checked = true
                feedback(app, selected == answer)
            }, enabled = unlocked && selected >= 0)
            val hint = hintText(s, StepCompletionPolicy.hint(step, st))
            if (hint.isNotEmpty()) Text(hint, Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }) {
        header()
        options.forEachIndexed { i, o ->
            val state = when {
                checked && i == answer -> ChoiceState.CORRECT
                checked && i == selected -> ChoiceState.WRONG
                else -> ChoiceState.NORMAL
            }
            ChoiceCard(selected = i == selected, state = state, enabled = !checked && unlocked, onClick = {
                selected = i
                o.melody?.let { app.engine.playMelody(it, PlayOptions(tempo = o.tempo)) }
                o.pitch?.let { app.engine.playPitch(it, 0.7) }
            }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (o.melody != null) {
                        Box(Modifier.weight(1f)) { StaffView(o.melody, showTime = false, gap = 8.dp) }
                        Icon(Icons.Filled.VolumeUp, ui.strings.play, tint = MaterialTheme.colorScheme.primary)
                    }
                    if (o.label != null) Text(o.label, fontSize = if (ui.kid) 22.sp else 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun optionItems(opts: List<Option>, clef: Clef? = null): List<ChoiceItem> {
    val ui = LocalUi.current
    return remember(opts, ui.lang, ui.naming, clef) {
        opts.map { o ->
            val pitch = o.note?.let { Pitch.parseOrNull(it) }
            val label = o.text?.get(ui.lang) ?: pitch?.let { ui.noteName(it) }
            val mel = o.melody?.let { runCatching { it.toMelody() }.getOrNull() }?.let { m -> if (clef != null) ClefShift.toClef(m, clef) else m }
            ChoiceItem(label, mel, o.melody?.tempo ?: 80, pitch)
        }
    }
}

@Composable
private fun QuizStep(app: AppState, step: Step.Quiz, isExam: Boolean, onDone: (StepOutcome) -> Unit) {
    val ui = LocalUi.current
    val items = optionItems(step.options, LocalClefOverride.current)
    ChoiceStep(app, step, items, step.answer, step.explanation[ui.lang], isExam, onDone) {
        BigText(step.question[ui.lang])
        step.melody?.let { MelodyPlayerCard(app, it) }
    }
}

private fun distractors(answer: Pitch, clef: Clef): List<Pitch> {
    val seed = answer.midi * 31 + clef.ordinal
    val rnd = Random(seed)
    val steps = listOf(-3, -2, -1, 1, 2, 3).map { answer.diatonic + it }
        .filter { it >= clef.bottomLineDiatonic - 3 && it <= clef.bottomLineDiatonic + 11 }
        .map { d -> Pitch(d % 7, 0, d / 7) }
    return steps.shuffled(rnd).take(3)
}

@Composable
private fun IdentifyNoteStep(app: AppState, step: Step.IdentifyNote, isExam: Boolean, onDone: (StepOutcome) -> Unit) {
    val ui = LocalUi.current
    val answer = Pitch.parse(step.note)
    val authored = runCatching { Clef.fromCode(step.clef) }.getOrDefault(Clef.TREBLE)
    val override = LocalClefOverride.current
    val clef = override ?: authored
    // Whole-octave shift that fits the note on the chosen clef; choices move with it so names stay the same.
    val shift = remember(step, clef) { if (clef == authored) 0 else ClefShift.octaveShift(listOf(answer), clef) }
    val shownAnswer = remember(step, shift) { ClefShift.shiftPitch(answer, shift) }
    val (choices, idx) = remember(step, ui.naming, clef) {
        val list = step.choices?.map { ClefShift.shiftPitch(Pitch.parse(it), shift) } ?: (distractors(shownAnswer, clef) + shownAnswer).shuffled(Random(shownAnswer.midi))
        list to list.indexOfFirst { it.midi == shownAnswer.midi }
    }
    val melody = remember(step, clef, shift) { Melody.parse(clef.code, "4/4", "C", 80, "$shownAnswer:w") }
    val items = remember(choices, ui.naming, ui.lang) { choices.map { ChoiceItem(ui.noteName(it), null, pitch = it) } }
    ChoiceStep(app, step, items, idx, step.explanation?.get(ui.lang), isExam, onDone) {
        BigText((step.prompt?.get(ui.lang)) ?: ui.strings.whichNoteIsThis)
        PressCard(Modifier.fillMaxWidth()) {
            Box(Modifier.padding(12.dp)) {
                StaffView(melody, showTime = false, gap = 13.dp, description = ui.strings.cdStaff, onNoteClick = { app.engine.playPitch(shownAnswer) })
            }
        }
    }
}

@Composable
private fun EarTrainingStep(app: AppState, step: Step.EarTraining, isExam: Boolean, onDone: (StepOutcome) -> Unit) {
    val ui = LocalUi.current
    val items = optionItems(step.options)
    val pitches = remember(step) { step.sound.notes.map { Pitch.parse(it) } }
    val playing by app.engine.isPlaying.collectAsState()
    var soundPlayed by remember { mutableStateOf(false) }
    val play = { app.engine.playPitches(pitches, step.sound.together, step.sound.tempo); soundPlayed = true }
    LaunchedEffect(Unit) { delay(350); play() }
    ChoiceStep(app, step, items, step.answer, step.explanation[ui.lang], isExam, onDone, soundPlayed = soundPlayed) {
        Text("👂", Modifier.align(Alignment.CenterHorizontally), fontSize = 48.sp)
        BigText(step.prompt[ui.lang])
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(if (ui.kid) 104.dp else 88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable { play() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.VolumeUp, ui.strings.playSound, Modifier.size(48.dp), tint = Color.White) }
        }
        Text(ui.strings.pressPlayToListen, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (playing) Spacer(Modifier.height(0.dp))
    }
}

// ------------------------------------------------------------------ piano

@Composable
private fun PianoStep(app: AppState, step: Step.Piano, isExam: Boolean, onDone: (StepOutcome) -> Unit) {
    val ui = LocalUi.current
    val s = ui.strings
    val targets = remember(step) { step.targets.map { Pitch.parse(it) } }
    val clef = runCatching { Clef.fromCode(step.clef) }.getOrDefault(Clef.TREBLE)
    val melody = remember(step) { Melody.parseLoose(targets.joinToString(" ") { "$it:q" }, clef.code) }
    var index by remember { mutableIntStateOf(0) }
    var mistakes by remember { mutableIntStateOf(0) }
    var flashWrong by remember { mutableStateOf<Int?>(null) }
    var flashRight by remember { mutableStateOf<Int?>(null) }
    val held = remember { HashMap<Int, Int>() }
    val done = index >= targets.size

    val lo = targets.minOf { it.midi }
    val hi = targets.maxOf { it.midi }
    var first = (lo / 12) * 12
    if (clef == Clef.TREBLE && first > 60) first = 60.coerceAtLeast((lo / 12) * 12)
    var last = first + 12
    if (hi > last) last = first + 19.coerceAtLeast(((hi - first) / 12 + 1) * 12)
    while (last % 12 !in setOf(0, 2, 4, 5, 7, 9, 11)) last--

    LaunchedEffect(flashWrong) { if (flashWrong != null) { delay(350); flashWrong = null } }
    LaunchedEffect(flashRight) { if (flashRight != null) { delay(250); flashRight = null } }

    StepFrame(bottom = {
        if (done) FeedbackPanel(
            true, s.correct(ui.kid), if (mistakes > 0 && !isExam) s.wrongWillRepeat else null, s.continueBtn,
            { if (StepCompletionPolicy.canContinue(step, StepState(pianoDone = done))) onDone(if (mistakes == 0) StepOutcome.CORRECT else StepOutcome.WRONG) },
        )
        else BottomBar {
            Text(s.pianoPlayNotes + " " + targets.drop(index).take(1).joinToString { if (step.showLabels) ui.noteName(it) else "?" }, Modifier.fillMaxWidth().padding(bottom = 8.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BigButton(s.continueBtn, {}, enabled = false)
            Text(hintText(s, StepCompletionPolicy.hint(step, StepState(pianoDone = done))), Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }) {
        BigText(step.prompt[ui.lang])
        if (step.showStaff) {
            PressCard(Modifier.fillMaxWidth()) {
                Box(Modifier.padding(12.dp)) {
                    StaffView(melody, labelFor = if (step.showLabels) ({ p -> ui.noteName(p) }) else null, highlightIndex = index.coerceAtMost(targets.size - 1), showTime = false, description = s.cdStaff)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        PianoKeyboard(
            firstMidi = first, lastMidi = last, height = if (ui.kid) 190.dp else 170.dp, showLabels = step.showLabels || index >= targets.size,
            highlight = if (mistakes >= 2 && !done) setOf(targets[index].midi) else emptySet(),
            correct = flashRight?.let { setOf(it) } ?: emptySet(),
            wrong = flashWrong?.let { setOf(it) } ?: emptySet(),
            onDown = { p ->
                held[p.midi] = app.engine.noteOn(p)
                if (!done) {
                    if (p.midi == targets[index].midi) { index++; flashRight = p.midi; if (index >= targets.size) feedback(app, true) }
                    else { mistakes++; flashWrong = p.midi; if (app.profile?.settings?.haptics != false) app.haptics.feedback(false) }
                }
            },
            onUp = { p -> held.remove(p.midi)?.let { app.engine.noteOff(it) } },
        )
    }
}

// ------------------------------------------------------------------ rhythm tap

private enum class RhythmPhase { READY, RUNNING, DONE }

@Composable
private fun RhythmTapStep(app: AppState, step: Step.RhythmTap, onDone: (StepOutcome) -> Unit) {
    val ui = LocalUi.current
    val s = ui.strings
    val melody = rememberMelody(step.melody, null) ?: run { onDone(StepOutcome.WRONG); return }
    val tempo = step.melody.tempo
    var phase by remember { mutableStateOf(RhythmPhase.READY) }
    val taps = remember { mutableStateListOf<Double>() }
    var finished by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<com.solfi.app.logic.RhythmResult?>(null) }
    var attempts by remember { mutableIntStateOf(0) }
    var failed by remember { mutableIntStateOf(0) }
    val pos by app.engine.position.collectAsState()
    val running = phase == RhythmPhase.RUNNING
    val hi = if (running && pos > AudioEngine.IDLE + 1) (melody.eventAtBeat(pos)?.index ?: -1) else -1
    val countingIn = running && pos < 0 && pos > AudioEngine.IDLE + 1
    val playing by app.engine.isPlaying.collectAsState()

    LaunchedEffect(finished) {
        if (finished) {
            delay(350)
            val expected = melody.onsetBeats().map { RhythmScorer.beatsToMs(it, tempo) }
            val r = RhythmScorer.score(expected, taps.map { RhythmScorer.beatsToMs(it, tempo) }, step.toleranceMs.toDouble())
            result = r
            phase = RhythmPhase.DONE
            val ok = StepCompletionPolicy.rhythmPassed(step, r.score)
            if (!ok) failed++
            feedback(app, ok)
        }
    }

    fun start() {
        taps.clear(); result = null; finished = false; attempts++
        phase = RhythmPhase.RUNNING
        app.engine.playMelody(melody, PlayOptions(tempo = tempo, countIn = step.countIn, metronome = true, pulseOnly = true, onFinished = { finished = true }))
    }

    val r = result
    val passed = r != null && StepCompletionPolicy.rhythmPassed(step, r.score)
    val gate = StepState(rhythmPassed = passed, rhythmFailedAttempts = failed)
    val canGo = StepCompletionPolicy.canContinue(step, gate)
    StepFrame(bottom = {
        when {
            phase == RhythmPhase.DONE && r != null -> FeedbackPanel(
                correct = passed, message = if (passed) s.rhythmGreat else s.rhythmTryAgain,
                detail = s.rhythmResult(r.hits, r.expected),
                primaryLabel = if (canGo) s.continueBtn else s.tryAgain,
                onPrimary = { if (canGo) onDone(StepCompletionPolicy.rhythmOutcome(gate)) else phase = RhythmPhase.READY },
            )
            else -> BottomBar {
                if (phase == RhythmPhase.READY) {
                    BigButton(s.startRhythm, { start() }, leading = "▶")
                    Spacer(Modifier.height(8.dp))
                    BigButton(s.continueBtn, {}, enabled = false)
                    Text(hintText(s, StepCompletionPolicy.hint(step, gate)), Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            }
        }
    }) {
        Text("🥁", Modifier.align(Alignment.CenterHorizontally), fontSize = 48.sp)
        BigText(step.prompt[ui.lang])
        PressCard(Modifier.fillMaxWidth()) {
            Box(Modifier.padding(12.dp)) { StaffView(melody, highlightIndex = hi, description = s.cdStaff) }
        }
        if (phase == RhythmPhase.READY && attempts == 0) {
            SecondaryButton(s.listenAgain, { app.engine.playMelody(melody, PlayOptions(tempo = tempo, metronome = true)) })
        }
        Text(
            when {
                countingIn -> s.getReady
                running -> s.tapNow
                else -> s.tapRhythmHint
            },
            Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 18.sp,
            color = if (countingIn) Palette.Coral else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TapPad(enabled = running, label = s.tapHere) {
            app.engine.playTap()
            val b = app.engine.beatNow()
            if (b > -0.6) taps += b
        }
        if (!running && playing) Spacer(Modifier.height(0.dp))
    }
}

@Composable
private fun TapPad(enabled: Boolean, label: String, onTap: () -> Unit) {
    var down by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (down) 0.92f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "tap")
    val color = if (enabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(150.dp).scale(sc).clip(CircleShape).background(color)
                .pointerInput(enabled) {
                    detectTapGestures(onPress = {
                        if (enabled) { down = true; onTap() }
                        tryAwaitRelease()
                        down = false
                    })
                },
            contentAlignment = Alignment.Center,
        ) { Text(label, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold) }
    }
}
