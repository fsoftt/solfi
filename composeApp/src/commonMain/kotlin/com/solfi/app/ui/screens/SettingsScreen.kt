package com.solfi.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.solfi.app.AppState
import com.solfi.app.Route
import com.solfi.app.data.Profile
import com.solfi.app.music.Clef
import com.solfi.app.music.ClefPolicy
import com.solfi.app.music.ClefPreference
import com.solfi.app.music.Instrument
import com.solfi.app.ui.icons.InstrumentIcon
import com.solfi.app.music.Lang
import com.solfi.app.music.NoteNaming
import com.solfi.app.platform.PlatformBackHandler
import com.solfi.app.ui.theme.LocalUi
import com.solfi.app.ui.widgets.AvatarCircle
import com.solfi.app.ui.widgets.PressCard
import com.solfi.app.ui.widgets.SecondaryButton

@Composable
fun SettingsScreen(app: AppState) {
    val ui = LocalUi.current
    val s = ui.strings
    val profile = app.profile ?: return
    val st = profile.settings
    var toDelete by remember { mutableStateOf<Profile?>(null) }
    PlatformBackHandler(true) { app.pop() }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ app.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, s.cdBack) }
            Text(s.settings, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Section(s.language) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Lang.entries.forEach { l ->
                        FilterChip(st.language == l.code, { app.updateSettings { copy(language = l.code) } }, { Text(l.label) })
                    }
                }
            }
            Section(s.noteNames, s.noteNamesExample) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(st.noteNaming == NoteNaming.SOLFEGE.name, { app.updateSettings { copy(noteNaming = NoteNaming.SOLFEGE.name) } }, { Text(s.solfege) })
                    FilterChip(st.noteNaming == NoteNaming.LETTERS.name, { app.updateSettings { copy(noteNaming = NoteNaming.LETTERS.name) } }, { Text(s.letters) })
                }
            }
            Section(s.mode) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(st.kidMode, { app.updateSettings { copy(kidMode = true) } }, { Text("🧒 " + s.modeKid) })
                    FilterChip(!st.kidMode, { app.updateSettings { copy(kidMode = false) } }, { Text("🧑 " + s.modeAdult) })
                }
            }
            Section(s.instrumentSettingsTitle) {
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Instrument.entries.forEach { ins ->
                        FilterChip(st.instrument == ins.name, { app.updateSettings { copy(instrument = ins.name) } }, { Text(s.instrumentName(ins)) }, leadingIcon = { InstrumentIcon(ins, 24.dp) })
                    }
                }
            }
            Section(s.clefSettingsTitle, ClefPolicy.recommended(Instrument.fromName(st.instrument)).let { r ->
                s.clefRecommendedIs(when (r) { Clef.BASS -> s.clefBass; Clef.TREBLE -> s.clefTreble; null -> s.clefAuto }) + " - " + s.clefReason(r)
            }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ClefPreference.RECOMMENDED to s.clefRecommended, ClefPreference.TREBLE to s.clefTreble, ClefPreference.BASS to s.clefBass).forEach { (k, label) ->
                        FilterChip(ClefPreference.fromName(st.clefPreference) == k, { app.updateSettings { copy(clefPreference = k.name) } }, { Text(label) })
                    }
                }
            }
            Section(s.theme) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("SYSTEM" to s.themeSystem, "LIGHT" to s.themeLight, "DARK" to s.themeDark).forEach { (k, label) ->
                        FilterChip(st.theme == k, { app.updateSettings { copy(theme = k) } }, { Text(label) })
                    }
                }
            }
            Section(s.volume) {
                Slider(st.volume, { v -> app.updateSettings { copy(volume = v) } }, valueRange = 0f..1f, onValueChangeFinished = { app.engine.playPitch(com.solfi.app.music.Pitch.parse("C5"), 0.4) })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(s.haptics, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Switch(st.haptics, { app.updateSettings { copy(haptics = it) } })
            }

            Section(s.profiles) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    app.profiles.forEach { p ->
                        val active = p.id == profile.id
                        PressCard(
                            Modifier.fillMaxWidth(), onClick = { if (!active) app.switchProfile(p.id) },
                            border = BorderStroke(if (active) 2.5.dp else 1.5.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AvatarCircle(p.avatar, 44.dp, selected = active)
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    Text(if (active) s.activeProfile else s.switchProfile, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton({ toDelete = p }) { Icon(Icons.Filled.Delete, s.deleteProfile) }
                            }
                        }
                    }
                    SecondaryButton("+ ${s.addProfile}", { app.push(Route.AddProfile) })
                }
            }
            Text(s.aboutText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
            Spacer(Modifier.height(12.dp))
        }
    }

    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(s.deleteProfile) }, text = { Text(s.deleteProfileConfirm(p.name)) },
            confirmButton = { TextButton({ app.deleteProfile(p.id); toDelete = null }) { Text(s.delete, color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ toDelete = null }) { Text(s.cancel) } },
        )
    }
}

@Composable
private fun Section(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        if (hint != null) Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}
