package com.solfi.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.solfi.app.AppState
import com.solfi.app.audio.AudioEngine
import com.solfi.app.audio.PlayOptions
import com.solfi.app.content.MelodySpec
import com.solfi.app.music.Clef
import com.solfi.app.music.ClefShift
import com.solfi.app.music.Melody
import com.solfi.app.ui.notation.StaffView
import com.solfi.app.ui.theme.LocalUi
import com.solfi.app.ui.widgets.PressCard

/** Clef the current step must be shown in, or null to keep the authored clef (set per step by the lesson screen). */
val LocalClefOverride = androidx.compose.runtime.staticCompositionLocalOf<Clef?> { null }

/** Parses [spec], re-written in [clef] (whole-octave shift) when given. Pass `clef = null` to keep the authored clef. */
@Composable
fun rememberMelody(spec: MelodySpec?, clef: Clef? = LocalClefOverride.current): Melody? = remember(spec, clef) {
    spec?.let { runCatching { it.toMelody() }.getOrNull() }?.let { m -> if (clef != null) ClefShift.toClef(m, clef) else m }
}

/** Page layout of a lesson step: scrolling body plus a pinned bottom area. */
@Composable
fun StepFrame(bottom: @Composable () -> Unit, body: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), content = body,
        )
        bottom()
    }
}

@Composable
fun BottomBar(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), content = content)
}

/** Position of the shared engine as the highlighted event of [melody] (or -1). */
@Composable
fun engineHighlight(engine: AudioEngine, melody: Melody?): Int {
    val pos by engine.position.collectAsState()
    if (melody == null || pos <= AudioEngine.IDLE + 1) return -1
    return melody.eventAtBeat(pos)?.index ?: -1
}

/** Staff + play/stop button that highlights the notes while playing. */
@Composable
fun MelodyPlayerCard(
    app: AppState, spec: MelodySpec, showLabels: Boolean = true, autoLabelOff: Boolean = false, clef: Clef? = LocalClefOverride.current,
    /** Called when the sequencer reaches the end of the melody (not when playback is stopped early). */
    onPlayedToEnd: (() -> Unit)? = null,
) {
    val ui = LocalUi.current
    val melody = rememberMelody(spec, clef) ?: return
    val playing by app.engine.isPlaying.collectAsState()
    val hi = engineHighlight(app.engine, melody)
    PressCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            StaffView(
                melody, labelFor = if (showLabels && !autoLabelOff) ({ p -> ui.noteName(p) }) else null, highlightIndex = hi,
                description = ui.strings.cdStaff,
            )
            PlayButton(playing, onPlay = { app.engine.playMelody(melody, PlayOptions(tempo = spec.tempo, onFinished = onPlayedToEnd)) }, onStop = { app.engine.stop() })
        }
    }
}

/** Localized text for a [StepHint] ("" for [StepHint.NONE]). */
fun hintText(s: com.solfi.app.i18n.Strings, hint: com.solfi.app.logic.StepHint): String = when (hint) {
    com.solfi.app.logic.StepHint.NONE -> ""
    com.solfi.app.logic.StepHint.LISTEN_EXAMPLE -> s.hintListenExample
    com.solfi.app.logic.StepHint.LISTEN_TO_END -> s.hintListenToEnd
    com.solfi.app.logic.StepHint.ANSWER -> s.hintAnswer
    com.solfi.app.logic.StepHint.PLAY_SOUND_FIRST -> s.hintPlaySoundFirst
    com.solfi.app.logic.StepHint.PLAY_NOTES -> s.hintPlayNotes
    com.solfi.app.logic.StepHint.TAP_RHYTHM -> s.hintTapRhythm
    com.solfi.app.logic.StepHint.WAIT -> s.hintWait
}

/** "Continuar" button that stays disabled, with a hint under it, until [StepCompletionPolicy] allows it. */
@Composable
fun GatedContinue(step: com.solfi.app.content.Step, state: com.solfi.app.logic.StepState, onNext: () -> Unit) {
    val s = LocalUi.current.strings
    val can = com.solfi.app.logic.StepCompletionPolicy.canContinue(step, state)
    BottomBar {
        com.solfi.app.ui.widgets.BigButton(s.continueBtn, onNext, enabled = can)
        val hint = hintText(s, com.solfi.app.logic.StepCompletionPolicy.hint(step, state))
        if (hint.isNotEmpty()) Text(hint, Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

@Composable
fun PlayButton(playing: Boolean, onPlay: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 56.dp) {
    val s = LocalUi.current.strings
    FilledIconButton(
        onClick = { if (playing) onStop() else onPlay() }, modifier = modifier.size(size),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Icon(if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, if (playing) s.stop else s.play, Modifier.size(size * 0.55f), tint = Color.White)
    }
}

/** A big selectable option card. */
@Composable
fun ChoiceCard(
    selected: Boolean,
    state: ChoiceState,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val (bg, borderColor) = when (state) {
        ChoiceState.CORRECT -> Color(0xFFD9F5E5) to Color(0xFF2EBD6B)
        ChoiceState.WRONG -> Color(0xFFFFE0DE) to Color(0xFFEF5350)
        ChoiceState.NORMAL -> if (selected) cs.primaryContainer to cs.primary else cs.surface to cs.outlineVariant
    }
    PressCard(
        Modifier.fillMaxWidth(), onClick = if (enabled) onClick else ({}), container = bg,
        border = BorderStroke(if (selected || state != ChoiceState.NORMAL) 2.5.dp else 1.5.dp, borderColor),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (LocalUi.current.kid) 18.dp else 14.dp), contentAlignment = Alignment.CenterStart) { content() }
    }
}

enum class ChoiceState { NORMAL, CORRECT, WRONG }

@Composable
fun BigText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, size: Int = 0) {
    val kid = LocalUi.current.kid
    Text(text, modifier, color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color, fontSize = (if (size > 0) size else if (kid) 22 else 19).sp, lineHeight = ((if (size > 0) size else if (kid) 22 else 19) + 7).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
}

@Composable
fun Body(text: String, modifier: Modifier = Modifier) {
    val kid = LocalUi.current.kid
    Text(text, modifier, fontSize = (if (kid) 18 else 16).sp, lineHeight = (if (kid) 26 else 23).sp, color = MaterialTheme.colorScheme.onSurface)
}
