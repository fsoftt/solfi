package com.solfi.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.solfi.app.AppState
import com.solfi.app.Route
import com.solfi.app.audio.AudioEngine
import com.solfi.app.audio.PlayOptions
import com.solfi.app.logic.ProgressRepository
import com.solfi.app.music.Clef
import com.solfi.app.music.generator.Difficulty
import com.solfi.app.platform.PlatformBackHandler
import com.solfi.app.ui.notation.StaffView
import com.solfi.app.ui.theme.LocalUi
import com.solfi.app.ui.theme.parseHexColor
import com.solfi.app.ui.widgets.PressCard
import com.solfi.app.ui.widgets.ScreenTitle
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun SolfeosScreen(app: AppState) {
    val ui = LocalUi.current
    val s = ui.strings
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item { ScreenTitle(s.solfeosTitle, subtitle = if (app.solfeosSection == 1) s.randomIntro else s.solfeosIntro) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(app.solfeosSection == 0, { app.solfeosSection = 0 }, { Text(s.solfeosTabLessons) })
                FilterChip(app.solfeosSection == 1, { app.solfeosSection = 1 }, { Text(s.solfeosTabRandom) })
            }
        }
        if (app.solfeosSection == 1) {
            items(Difficulty.entries.size, key = { "r$it" }) { i -> RandomDifficultyCard(app, Difficulty.entries[i]) }
            return@LazyColumn
        }
        app.levels.forEachIndexed { li, level ->
            if (level == null || !ProgressRepository.isLevelUnlocked(level.level, app.progress)) return@forEachIndexed
            item(key = "h$li") {
                Text("${s.levelLabel(level.level)} · ${level.title[ui.lang]}", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontWeight = FontWeight.Bold, color = parseHexColor(level.color), fontSize = 15.sp)
            }
            items(level.solfeos.size, key = { "s$li-${level.solfeos[it].id}" }) { i ->
                val sf = level.solfeos[i]
                PressCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), onClick = { app.push(Route.Solfeo(li, i)) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(52.dp).clip(CircleShape).background(parseHexColor(level.color).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Text("🎼", fontSize = 26.sp) }
                        Column(Modifier.weight(1f)) {
                            Text(sf.title[ui.lang], fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                            Text(sf.description[ui.lang], fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                            Text("${sf.melody.time} · ${s.bpm(sf.melody.tempo)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SolfeoPlayerScreen(app: AppState, levelIndex: Int, solfeoIndex: Int) {
    val ui = LocalUi.current
    val s = ui.strings
    val level = app.levels.getOrNull(levelIndex)
    val solfeo = level?.solfeos?.getOrNull(solfeoIndex)
    // Session clef override: 0 = Auto (profile preference), 1 = treble, 2 = bass.
    var clefMode by remember { mutableIntStateOf(0) }
    val sessionClef = when (clefMode) { 1 -> Clef.TREBLE; 2 -> Clef.BASS; else -> app.effectiveClef }
    val melody = rememberMelody(solfeo?.melody, sessionClef)
    if (level == null || solfeo == null || melody == null) { app.pop(); return }
    val engine = app.engine

    var tempo by remember { mutableFloatStateOf(solfeo.melody.tempo.toFloat()) }
    var metronome by remember { mutableStateOf(true) }
    var countIn by remember { mutableStateOf(true) }
    var loop by remember { mutableStateOf(false) }
    var pulseOnly by remember { mutableStateOf(false) }
    var showNames by remember { mutableStateOf(true) }
    var pausedAt by remember { mutableDoubleStateOf(0.0) }
    val playing by engine.isPlaying.collectAsState()
    val pos by engine.position.collectAsState()
    val hi = engineHighlight(engine, melody)

    PlatformBackHandler(true) { app.pop() }
    LaunchedEffect(Unit) { engine.stop() }

    fun start(from: Double) {
        engine.playMelody(
            melody,
            PlayOptions(tempo = tempo.roundToInt(), metronome = metronome || pulseOnly, countIn = countIn, loop = loop, pulseOnly = pulseOnly, fromBeat = from, onFinished = { pausedAt = 0.0; app.recordSolfeo() }),
        )
    }
    fun restartIfPlaying() { if (playing) { val p = pos.coerceAtLeast(0.0); start(p) } }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ app.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, s.cdBack) }
            Column(Modifier.weight(1f)) {
                Text(solfeo.title[ui.lang], fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1)
                Text("${melody.time} · ${s.levelLabel(level.level)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PressCard(Modifier.fillMaxWidth()) {
                Box(Modifier.padding(12.dp)) {
                    StaffView(
                        melody, labelFor = if (showNames) ({ p -> ui.noteName(p) }) else null, highlightIndex = if (pulseOnly) -1 else hi,
                        onNoteClick = { e -> e.pitches.forEach { engine.playPitch(it, 0.7) } }, description = s.cdStaff,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.clefQuickTitle, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                listOf(s.clefQuickSol, s.clefQuickFa, s.clefAuto).forEachIndexed { i, label ->
                    val mode = when (i) { 0 -> 1; 1 -> 2; else -> 0 }
                    FilterChip(clefMode == mode, { clefMode = mode; if (playing) engine.stop() }, { Text(label) })
                }
            }
            PulseDots(melody.time.clickTicks, melody.time.measureTicks, if (playing) pos else AudioEngine.IDLE)

            // transport
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton({ engine.stop(); pausedAt = 0.0 }, Modifier.size(52.dp), colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors()) { Icon(Icons.Filled.Stop, s.stop) }
                Spacer(Modifier.size(20.dp))
                FilledIconButton({
                    if (playing) { pausedAt = pos.coerceAtLeast(0.0); engine.stop() } else start(pausedAt)
                }, Modifier.size(76.dp)) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) s.pause else s.play, Modifier.size(42.dp)) }
            }

            // tempo
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(s.tempo, fontWeight = FontWeight.Bold)
                    Text(s.bpm(tempo.roundToInt()), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Slider(tempo, { tempo = it }, valueRange = 40f..160f, onValueChangeFinished = { restartIfPlaying() })
            }
            ToggleRow(s.metronome, metronome && !pulseOnly, enabled = !pulseOnly) { metronome = it; restartIfPlaying() }
            ToggleRow(s.countIn, countIn) { countIn = it }
            ToggleRow(s.loop, loop) { loop = it; restartIfPlaying() }
            ToggleRow(s.pulseOnly, pulseOnly, s.pulseOnlyDesc) { pulseOnly = it; restartIfPlaying() }
            ToggleRow(s.showNoteNames, showNames) { showNames = it }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun ToggleRow(label: String, checked: Boolean, desc: String? = null, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            if (desc != null) Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange, enabled = enabled)
    }
}

/** Row of dots, one per click of the measure; the current beat is enlarged. */
@Composable
internal fun PulseDots(clickTicks: Int, measureTicks: Int, position: Double) {
    val perMeasure = measureTicks / clickTicks
    val active = if (position <= AudioEngine.IDLE + 1) -1 else floor(((position * 24.0) % measureTicks + measureTicks) % measureTicks / clickTicks).toInt()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        for (i in 0 until perMeasure) {
            val on = i == active
            val sc by animateFloatAsState(if (on) 1.35f else 1f, label = "dot")
            Box(
                Modifier.padding(6.dp).size((16 * sc).dp).clip(CircleShape)
                    .background(if (on) (if (i == 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary) else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}
