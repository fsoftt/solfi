package com.solfi.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.solfi.app.AppState
import com.solfi.app.logic.FollowState
import com.solfi.app.logic.NoteFollower
import com.solfi.app.logic.PlayTarget
import com.solfi.app.music.InstrumentTuning
import com.solfi.app.music.Melody
import com.solfi.app.music.PitchJudge
import com.solfi.app.music.PitchJudgement
import com.solfi.app.music.PitchVerdict
import com.solfi.app.platform.MicStatus
import com.solfi.app.ui.theme.LocalUi
import com.solfi.app.ui.theme.Palette
import com.solfi.app.ui.widgets.BigButton
import com.solfi.app.ui.widgets.SecondaryButton
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One "play it on your instrument" session over [targets]. The shared [AppState.listener] does the listening;
 * this only remembers whether this screen started it.
 */
@Stable
class PlayAlong(private val app: AppState, val targets: List<PlayTarget>, val tuning: InstrumentTuning = app.tuning) {
    var active by mutableStateOf(false)
        private set

    /** Some written note is outside the instrument's range: any octave is accepted then. */
    val outOfRange: Boolean = outOfRange(targets, tuning)

    val octaveFree: Boolean get() = app.octaveFree || tuning.octaveFree || outOfRange

    private var session = -1

    fun start() {
        if (targets.isEmpty()) return
        app.engine.stop()
        session = app.listener.follow(targets, tuning, octaveFree)
        active = true
    }

    fun stop() {
        if (active) app.listener.stop(session)
        active = false
    }

    fun toggle() = if (active) stop() else start()

    /** Plays the concert pitch of the current target, muting the microphone meanwhile. */
    fun hearCurrent(index: Int) {
        val t = targets.getOrNull(index) ?: return
        app.listener.mute(1300)
        app.engine.playPitch(tuning.concert(t.written.first()), 0.9)
    }

    companion object {
        fun outOfRange(targets: List<PlayTarget>, t: InstrumentTuning): Boolean =
            targets.any { tg -> tg.written.all { it.midi < t.lowWritten - 2 || it.midi > t.highWritten + 2 } }
    }
}

@Composable
fun rememberPlayAlong(app: AppState, targets: List<PlayTarget>, tuning: InstrumentTuning = app.tuning): PlayAlong {
    val pa = remember(targets, tuning) { PlayAlong(app, targets, tuning) }
    DisposableEffect(pa) { onDispose { pa.stop() } }
    return pa
}

@Composable
fun rememberPlayAlong(app: AppState, melody: Melody?, tuning: InstrumentTuning = app.tuning): PlayAlong {
    val targets = remember(melody) { melody?.let { NoteFollower.targetsOf(it) } ?: emptyList() }
    return rememberPlayAlong(app, targets, tuning)
}

/** Melody event to highlight while playing along, or -1. */
@Composable
fun PlayAlong.highlight(app: AppState): Int {
    val st by app.listener.follow.collectAsState()
    if (!active) return -1
    return targets.getOrNull(st.index)?.eventIndex ?: -1
}

@Composable
fun PlayAlongButton(pa: PlayAlong, modifier: Modifier = Modifier) {
    val s = LocalUi.current.strings
    SecondaryButton(if (pa.active) s.stopListening else "🎶  " + s.playWithInstrument, { pa.toggle() }, modifier)
}

/** Microphone status, live meter and feedback for an active [PlayAlong]. Renders nothing when inactive. */
@Composable
fun PlayAlongPanel(app: AppState, pa: PlayAlong, onFinished: ((FollowState) -> Unit)? = null) {
    if (!pa.active) return
    val ui = LocalUi.current
    val s = ui.strings
    val status by app.listener.status.collectAsState()
    val st by app.listener.follow.collectAsState()

    LaunchedEffect(st.done) { if (st.done && st.total > 0) { feedback(app, true); onFinished?.invoke(st) } }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(if (ui.kid) 24.dp else 16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (status) {
            MicStatus.DENIED, MicStatus.UNAVAILABLE -> {
                Text(if (status == MicStatus.DENIED) s.micDenied else s.micUnavailable, textAlign = TextAlign.Center, color = Palette.Danger, fontWeight = FontWeight.SemiBold)
                TextButton({ pa.stop(); pa.start() }) { Text(s.tryAgain) }
            }
            MicStatus.LISTENING -> if (st.done) {
                Text("🎉", fontSize = 40.sp)
                Text(s.playAlongSummary(st.total, st.firstTry, st.inTune), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                BigButton(s.playAgain, { pa.start() }, leading = "🔁")
            } else {
                Text(s.noteProgress(st.index + 1, st.total), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                PitchMeter(st.live)
                PlayFeedbackLine(app, pa, st)
                Text(s.playAlongHint(ui.kid), textAlign = TextAlign.Center, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (pa.outOfRange && !app.octaveFree && !pa.tuning.octaveFree) Text(s.anyOctaveAuto, textAlign = TextAlign.Center, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton({ pa.hearCurrent(st.index) }) { Text("🎧 " + s.hearNote) }
            }
            else -> Text(s.micStarting, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Too high: you played E…" for a while after a wrong note, otherwise the live verdict. */
@Composable
private fun PlayFeedbackLine(app: AppState, pa: PlayAlong, st: FollowState) {
    val ui = LocalUi.current
    val s = ui.strings
    var shownWrong by remember { mutableStateOf(0) }
    val wrong = st.lastWrong
    LaunchedEffect(wrong?.serial) {
        if (wrong != null) {
            shownWrong = wrong.serial
            if (app.profile?.settings?.haptics != false) app.haptics.feedback(false)
            delay(1800)
            if (shownWrong == wrong.serial) shownWrong = 0
        }
    }
    val target = pa.targets.getOrNull(st.index)
    val (text, color) = when {
        st.waitingRelease -> s.playItAgain to MaterialTheme.colorScheme.onSurface
        wrong != null && shownWrong == wrong.serial && wrong.targetIndex == st.index && target != null -> {
            val heard = pa.tuning.written(wrong.judgement.heardMidi)
            val higher = wrong.judgement.verdict == PitchVerdict.TOO_HIGH
            val oct = !pa.octaveFree && heard.step == target.written.first().step && heard.alter == target.written.first().alter
            (s.verdictText(wrong.judgement.verdict, ui.kid) + " · " + s.wrongNoteDetail(ui.noteName(heard, withOctave = oct), ui.noteName(target.written.first(), withOctave = oct), higher)) to Palette.Danger
        }
        st.live != null -> s.verdictText(st.live.verdict, ui.kid) to verdictColor(st.live.verdict)
        else -> "…" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.ExtraBold, fontSize = if (ui.kid) 20.sp else 18.sp, color = color)
}

fun verdictColor(v: PitchVerdict): Color = when (v) {
    PitchVerdict.IN_TUNE -> Palette.Success
    PitchVerdict.SLIGHTLY_HIGH, PitchVerdict.SLIGHTLY_LOW -> Palette.Gold
    PitchVerdict.TOO_HIGH, PitchVerdict.TOO_LOW -> Palette.Danger
}

/**
 * Horizontal tuning meter from −50 to +50 cents: green centre (in tune), amber sides (slightly off) and a needle.
 * A wrong note pins the needle to the side it is on. [judgement] null = nothing heard.
 */
@Composable
fun PitchMeter(judgement: PitchJudgement?, modifier: Modifier = Modifier) {
    val s = LocalUi.current.strings
    val cents = judgement?.cents?.coerceIn(-60.0, 60.0) ?: 0.0
    val pos by animateFloatAsState((cents / 60.0).toFloat(), label = "needle")
    val track = MaterialTheme.colorScheme.outlineVariant
    val needle = judgement?.let { verdictColor(it.verdict) } ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val desc = judgement?.let { s.verdictText(it.verdict, false) } ?: s.tunerPlaySomething
    Column(modifier.fillMaxWidth().semantics { contentDescription = desc }, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(44.dp)) {
            val w = size.width
            val cy = size.height / 2
            val barH = 12.dp.toPx()
            fun x(c: Double) = (w / 2 + (c / 60.0) * (w / 2 - 8.dp.toPx())).toFloat()
            drawRoundRect(track, Offset(0f, cy - barH / 2), Size(w, barH), CornerRadius(barH / 2))
            drawRect(Palette.Gold.copy(alpha = 0.55f), Offset(x(-PitchJudge.RIGHT_NOTE_CENTS), cy - barH / 2), Size(x(PitchJudge.RIGHT_NOTE_CENTS) - x(-PitchJudge.RIGHT_NOTE_CENTS), barH))
            drawRect(Palette.Success.copy(alpha = 0.8f), Offset(x(-PitchJudge.IN_TUNE_CENTS), cy - barH / 2), Size(x(PitchJudge.IN_TUNE_CENTS) - x(-PitchJudge.IN_TUNE_CENTS), barH))
            val nx = w / 2 + pos * (w / 2 - 8.dp.toPx())
            drawLine(needle, Offset(nx, 4.dp.toPx()), Offset(nx, size.height - 4.dp.toPx()), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("♭", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(judgement?.let { if (it.verdict.isRightNote) centsText(it.cents) else "" } ?: "", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("♯", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun centsText(c: Double): String {
    val v = c.roundToInt()
    return (if (v > 0) "+" else if (v < 0) "-" else "±") + abs(v) + " ¢"
}
