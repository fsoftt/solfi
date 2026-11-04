package com.solfi.app.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.solfi.app.AppState
import com.solfi.app.music.Pitch
import com.solfi.app.ui.theme.LocalUi
import com.solfi.app.ui.widgets.PianoKeyboard
import com.solfi.app.ui.widgets.ScreenTitle
import kotlinx.coroutines.launch

/** Free piano: three scrollable octaves (C3-B5), multitouch, labels follow the note-name setting. */
@Composable
fun PianoScreen(app: AppState) {
    val ui = LocalUi.current
    val s = ui.strings
    val keyW = if (ui.kid) 64.dp else 56.dp
    val scroll = remember { ScrollState(0) }
    val scope = rememberCoroutineScope()
    var labels by remember { mutableStateOf(true) }
    var last by remember { mutableStateOf<Pitch?>(null) }
    val held = remember { HashMap<Int, Int>() }
    val density = LocalDensity.current

    // start around middle C (one octave = 7 white keys, C3 is the first key)
    LaunchedEffect(Unit) { scroll.scrollTo(with(density) { (keyW * 7 - 8.dp).toPx() }.toInt()) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle(s.pianoTitle, subtitle = s.pianoHint)
        Text(
            last?.let { ui.noteName(it, withOctave = true) } ?: "♪", Modifier.fillMaxWidth(),
            fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Column(Modifier.padding(horizontal = 20.dp)) {
            val max = scroll.maxValue.coerceAtLeast(1)
            Slider(scroll.value.toFloat() / max, { v -> scope.launch { scroll.scrollTo((v * max).toInt()) } })
        }
        PianoKeyboard(
            firstMidi = 48, lastMidi = 83, keyWidth = keyW, scroll = scroll, height = if (ui.kid) 280.dp else 240.dp, showLabels = labels,
            onDown = { p -> last = p; held[p.midi] = app.engine.noteOn(p) },
            onUp = { p -> held.remove(p.midi)?.let { app.engine.noteOff(it) } },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(s.showLabels, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Switch(labels, { labels = it })
        }
    }
}
