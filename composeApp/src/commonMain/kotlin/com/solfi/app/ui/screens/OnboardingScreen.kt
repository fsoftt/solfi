package com.solfi.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.solfi.app.AppState
import com.solfi.app.i18n.stringsFor
import com.solfi.app.music.Clef
import com.solfi.app.music.ClefPolicy
import com.solfi.app.music.ClefPreference
import com.solfi.app.music.Instrument
import com.solfi.app.music.Lang
import com.solfi.app.music.NoteNaming
import com.solfi.app.ui.theme.LocalUi
import com.solfi.app.ui.theme.UiContext
import com.solfi.app.ui.widgets.AvatarCircle
import com.solfi.app.ui.widgets.BigButton
import com.solfi.app.ui.widgets.Mascot
import com.solfi.app.ui.widgets.Mood
import com.solfi.app.ui.widgets.PressCard
import com.solfi.app.ui.widgets.SecondaryButton

val AVATARS = listOf("🦊", "🐼", "🐯", "🦁", "🐸", "🐵", "🦄", "🐙", "🐧", "🐶", "🐱", "🦉", "🎸", "🎹", "🥁", "🎷")

/** Onboarding: language, name, avatar, mode. Also used to add extra profiles ([onCancel] != null). */
@Composable
fun OnboardingScreen(app: AppState, onCancel: (() -> Unit)? = null) {
    var page by remember { mutableStateOf(0) }
    var lang by remember { mutableStateOf(app.systemLang) }
    var name by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf(AVATARS.first()) }
    var kid by remember { mutableStateOf(true) }
    var instrument by remember { mutableStateOf(Instrument.OTHER) }
    var clefPref by remember { mutableStateOf(ClefPreference.RECOMMENDED) }
    val naming = NoteNaming.defaultFor(lang)
    val ui = UiContext(stringsFor(lang), lang, naming, kid)

    CompositionLocalProvider(LocalUi provides ui) {
        val s = ui.strings
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (page > 0) Text("‹ ${s.back}", Modifier.clickable { page-- }.padding(8.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                else if (onCancel != null) Text("‹ ${s.cancel}", Modifier.clickable { onCancel() }.padding(8.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                else Spacer(Modifier.height(1.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0..5) Box(Modifier.height(8.dp).width(if (i == page) 28.dp else 10.dp).clip(RoundedCornerShape(4.dp)).background(if (i <= page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                }
            }
            AnimatedContent(
                page, Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                }, label = "onboarding",
            ) { p ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (p) {
                        0 -> {
                            Spacer(Modifier.height(12.dp))
                            Mascot(size = 150.dp, mood = Mood.CHEER)
                            Text(s.welcomeTitle, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                            Text(s.welcomeSubtitle, Modifier.padding(top = 6.dp, bottom = 20.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(s.chooseLanguage, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 10.dp))
                            Lang.entries.forEach { l ->
                                val sel = l == lang
                                PressCard(
                                    Modifier.fillMaxWidth().padding(vertical = 5.dp), onClick = { lang = l },
                                    container = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(if (sel) 2.5.dp else 1.5.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(when (l) { Lang.ES -> "🇪🇸"; Lang.EN -> "🇬🇧"; Lang.PT -> "🇧🇷" }, fontSize = 26.sp)
                                        Text(l.label, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                        1 -> {
                            Spacer(Modifier.height(12.dp))
                            AvatarCircle(avatar, 96.dp, selected = true)
                            Text(s.yourName, Modifier.padding(top = 16.dp, bottom = 8.dp), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                            OutlinedTextField(
                                name, { if (it.length <= 20) name = it }, Modifier.fillMaxWidth(), singleLine = true,
                                placeholder = { Text(s.nameHint) }, shape = MaterialTheme.shapes.medium,
                            )
                            Text(s.chooseAvatar, Modifier.padding(top = 20.dp, bottom = 8.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxWidth().height(250.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), userScrollEnabled = false) {
                                items(AVATARS) { a ->
                                    Box(Modifier.clickable { avatar = a }.semantics { contentDescription = a }, contentAlignment = Alignment.Center) { AvatarCircle(a, 64.dp, selected = a == avatar) }
                                }
                            }
                        }
                        2 -> {
                            Spacer(Modifier.height(20.dp))
                            Text(s.chooseMode, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(18.dp))
                            ModeCard("🧒", s.modeKid, s.modeKidDesc, kid) { kid = true }
                            Spacer(Modifier.height(12.dp))
                            ModeCard("🧑", s.modeAdult, s.modeAdultDesc, !kid) { kid = false }
                            Text(s.noteNamesDefaultHint(naming == NoteNaming.SOLFEGE), Modifier.padding(top = 20.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        }
                        3 -> {
                            Spacer(Modifier.height(20.dp))
                            Text(s.instrumentTitle, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(14.dp))
                            Instrument.entries.forEach { ins ->
                                val sel = ins == instrument
                                PressCard(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = { instrument = ins },
                                    container = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(if (sel) 2.5.dp else 1.5.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(instrumentEmoji(ins), fontSize = 26.sp)
                                        Text(s.instrumentName(ins), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                        4 -> {
                            val rec = ClefPolicy.recommended(instrument)
                            val recName = when (rec) { Clef.BASS -> s.clefBass; Clef.TREBLE -> s.clefTreble; null -> null }
                            Spacer(Modifier.height(20.dp))
                            Text(s.clefTitle, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(18.dp))
                            ModeCard("\u2B50", s.clefRecommended, recName?.let { s.clefRecommendedIs(it) + "\n" + s.clefReason(rec) } ?: s.clefReason(null), clefPref == ClefPreference.RECOMMENDED) { clefPref = ClefPreference.RECOMMENDED }
                            Spacer(Modifier.height(12.dp))
                            ModeCard("\uD834\uDD1E", s.clefTreble, "", clefPref == ClefPreference.TREBLE) { clefPref = ClefPreference.TREBLE }
                            Spacer(Modifier.height(12.dp))
                            ModeCard("\uD834\uDD22", s.clefBass, "", clefPref == ClefPreference.BASS) { clefPref = ClefPreference.BASS }
                        }
                        else -> {
                            Spacer(Modifier.height(30.dp))
                            Mascot(size = 160.dp, mood = Mood.CHEER)
                            Text("${avatar} ${name.ifBlank { "Solfi" }}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                            Text(s.tagline, Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            val last = page == 5
            BigButton(
                if (last) s.letsStart else s.next,
                {
                    if (!last) page++ else app.createProfile(name, avatar, kid, lang, naming, instrument, clefPref)
                },
                enabled = page != 1 || name.isNotBlank(),
            )
        }
    }
}

@Composable
private fun ModeCard(emoji: String, title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    PressCard(
        Modifier.fillMaxWidth(), onClick = onClick,
        container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.5.dp else 1.5.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(emoji, fontSize = 40.sp)
            Column {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

fun instrumentEmoji(i: Instrument): String = when (i) {
    Instrument.VOICE -> "\uD83C\uDFA4"
    Instrument.PIANO -> "\uD83C\uDFB9"
    Instrument.GUITAR -> "\uD83C\uDFB8"
    Instrument.VIOLIN -> "\uD83C\uDFBB"
    Instrument.FLUTE -> "\uD83E\uDE88"
    Instrument.CELLO -> "\uD83C\uDFBB"
    Instrument.BASS -> "\uD83C\uDFB8"
    Instrument.BASSOON -> "\uD83C\uDFB7"
    Instrument.TROMBONE -> "\uD83C\uDFBA"
    Instrument.TUBA -> "\uD83C\uDFBA"
    Instrument.OTHER -> "\uD83C\uDFB5"
}
