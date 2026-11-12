package com.solfi.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.solfi.app.AppState
import com.solfi.app.audio.PlayOptions
import com.solfi.app.content.EnsemblePart
import com.solfi.app.content.EnsemblePiece
import com.solfi.app.i18n.Strings
import com.solfi.app.music.Pitch
import com.solfi.app.ui.icons.InstrumentIcon
import com.solfi.app.ui.notation.StaffView
import com.solfi.app.ui.theme.LocalUi
import com.solfi.app.ui.widgets.PressCard
import com.solfi.app.ui.widgets.ScreenTitle
import com.solfi.app.ui.widgets.SecondaryButton
import kotlin.math.roundToInt

/** Display name of an NGL instrument code. */
fun Strings.ensembleInstrumentName(part: EnsemblePart): String =
    part.profileInstrument?.let { instrumentName(it) } ?: if (part.instrument == EnsemblePart.GLOCKENSPIEL) glockenspiel else part.name

@Composable
private fun EnsembleIcon(part: EnsemblePart, size: androidx.compose.ui.unit.Dp) {
    val i = part.profileInstrument
    if (i != null) InstrumentIcon(i, size) else Text(if (part.instrument == EnsemblePart.GLOCKENSPIEL) "🔔" else "🎵", fontSize = (size.value * 0.7f).sp)
}

/** Part to show first: the saved choice, else voice 1 of the learner's instrument, else the first part. */
internal fun defaultNglPart(app: AppState, piece: EnsemblePiece): EnsemblePart {
    app.nglSelection?.takeIf { it.first == piece.id }?.let { (_, id) -> piece.parts.firstOrNull { it.id == id }?.let { return it } }
    return piece.partsOf(app.instrument.name).firstOrNull() ?: piece.parts.first()
}

/** NGL tab: pick a piece, an instrument and a voice, then read, listen to and play that part. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NglScreen(app: AppState) {
    val ui = LocalUi.current
    val s = ui.strings
    val pieces = app.nglPieces
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenTitle(s.nglTitle, subtitle = s.nglIntro)
        if (pieces.isEmpty()) {
            Text(s.nglEmpty, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        val piece = pieces.firstOrNull { it.id == app.nglSelection?.first } ?: pieces.first()
        val part = defaultNglPart(app, piece)
        fun select(p: EnsemblePiece, pt: EnsemblePart) { app.engine.stop(); app.nglSelection = p.id to pt.id }

        if (pieces.size > 1) {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pieces.forEach { p -> FilterChip(p.id == piece.id, { select(p, defaultNglPart(app, p)) }, { Text(p.title, maxLines = 1) }) }
            }
        }

        Text(s.nglInstrument, Modifier.padding(start = 20.dp, top = 4.dp), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            piece.instruments.forEach { code ->
                val first = piece.partsOf(code).first()
                FilterChip(
                    code == part.instrument,
                    { if (code != part.instrument) select(piece, piece.partsOf(code).firstOrNull { it.voice == part.voice } ?: first) },
                    { Text(s.ensembleInstrumentName(first), maxLines = 1) },
                    leadingIcon = { EnsembleIcon(first, 20.dp) },
                )
            }
        }
        val voices = piece.partsOf(part.instrument)
        if (voices.size > 1) {
            Text(s.nglVoice, Modifier.padding(start = 20.dp, top = 4.dp), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                voices.forEach { v -> FilterChip(v.id == part.id, { select(piece, v) }, { Text(s.nglVoiceN(v.voice), maxLines = 1) }) }
            }
        }

        NglPartPractice(app, piece, part)
    }
}

@Composable
private fun NglPartPractice(app: AppState, piece: EnsemblePiece, part: EnsemblePart) {
    val ui = LocalUi.current
    val s = ui.strings
    val written = remember(piece, part) { part.written(piece) }
    val concert = remember(piece, part) { part.concert(piece) }
    val others = remember(piece, part) { piece.parts.filter { it.id != part.id }.map { it.concert(piece) } }
    val tuning = remember(piece, part) { part.tuning(piece) }
    val pa = rememberPlayAlong(app, written, tuning)
    val playing by app.engine.isPlaying.collectAsState()
    var tempo by remember(piece.id) { mutableFloatStateOf(piece.tempo.toFloat()) }
    var metronome by remember { mutableStateOf(false) }
    var countIn by remember { mutableStateOf(true) }
    var showNames by remember { mutableStateOf(true) }
    var ensemble by remember { mutableStateOf(false) }
    val hi = if (pa.active) pa.highlight(app) else engineHighlight(app.engine, written)

    fun play(all: Boolean) {
        pa.stop()
        ensemble = all
        app.engine.playMelody(
            concert,
            PlayOptions(tempo = tempo.roundToInt(), metronome = metronome, countIn = countIn, accompaniment = if (all) others else emptyList()),
        )
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PressCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EnsembleIcon(part, 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(part.name, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text(piece.title + " · " + s.nglPartSummary(written.measures.size, piece.tempo), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val c = Pitch(0, 0, 5)
                Text(s.transposeExplain(part.transpose, ui.noteName(c), ui.noteName(tuning.concert(c))), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
            }
        }
        PressCard(Modifier.fillMaxWidth()) {
            Box(Modifier.padding(12.dp)) {
                StaffView(written, labelFor = if (showNames) ({ p -> ui.noteName(p) }) else null, highlightIndex = hi, description = s.cdStaff)
            }
        }
        PlayAlongPanel(app, pa, onFinished = { app.recordSolfeo() })
        PlayAlongButton(pa)
        SecondaryButton(if (playing && !ensemble) s.stop else "🎧  " + s.nglListenPart, { if (playing && !ensemble) app.engine.stop() else play(false) })
        SecondaryButton(if (playing && ensemble) s.stop else "🎶  " + s.nglListenAll, { if (playing && ensemble) app.engine.stop() else play(true) })
        PressCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.tempo, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(s.bpm(tempo.roundToInt()), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Slider(tempo, { tempo = it }, valueRange = 40f..200f, onValueChangeFinished = { if (playing) play(ensemble) })
                ToggleRow(s.metronome, metronome) { metronome = it; if (playing) play(ensemble) }
                ToggleRow(s.countIn, countIn) { countIn = it }
                ToggleRow(s.showNoteNames, showNames) { showNames = it }
            }
        }
    }
}
