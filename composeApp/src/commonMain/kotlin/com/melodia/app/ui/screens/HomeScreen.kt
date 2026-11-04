package com.melodia.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.melodia.app.AppState
import com.melodia.app.Route
import com.melodia.app.Tab
import com.melodia.app.logic.ProgressRepository
import com.melodia.app.ui.theme.LocalUi
import com.melodia.app.ui.theme.Palette
import com.melodia.app.ui.theme.parseHexColor
import com.melodia.app.ui.widgets.AvatarCircle
import com.melodia.app.ui.widgets.BigButton
import com.melodia.app.ui.widgets.Mascot
import com.melodia.app.ui.widgets.Mood
import com.melodia.app.ui.widgets.PressCard
import com.melodia.app.ui.widgets.StatChip

@Composable
fun HomeScreen(app: AppState) {
    val ui = LocalUi.current
    val s = ui.strings
    val profile = app.profile ?: return
    val levels = app.availableLevels
    val next = ProgressRepository.nextLesson(levels, app.progress)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AvatarCircle(profile.avatar, 48.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(s.greeting(profile.name, ui.kid), fontSize = if (ui.kid) 24.sp else 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(s.dailyMotivation(ui.kid), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
            }
            IconButton({ app.push(Route.Settings) }) { Icon(Icons.Filled.Settings, s.cdSettings) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatChip("🔥", s.streakDays(app.streak), s.streakLabel, Palette.Coral, Modifier.weight(1f))
            StatChip("⚡", "${app.progress.xp}", s.xpLabel, Palette.Violet, Modifier.weight(1f))
            StatChip("⭐", "${ProgressRepository.totalStars(app.progress)}", s.starsLabel, Palette.Gold, Modifier.weight(1f))
        }

        // continue card
        val accent = next?.let { parseHexColor(levels[it.first].color) } ?: MaterialTheme.colorScheme.primary
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(if (ui.kid) 32.dp else 20.dp))
                .background(Brush.linearGradient(listOf(accent, androidx.compose.ui.graphics.lerp(accent, Palette.Sky, 0.45f)))),
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (next != null) {
                        val lv = levels[next.first]
                        val lesson = lv.lessons[next.second]
                        Text("${s.levelLabel(lv.level)} · ${lv.title[ui.lang]}", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("${lesson.emoji} ${lesson.title[ui.lang]}", color = Color.White, fontSize = if (ui.kid) 24.sp else 21.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 28.sp)
                        Spacer(Modifier.height(6.dp))
                        BigButton(
                            if (app.progress.lessons.isEmpty()) s.startLearning else s.continueLesson,
                            { app.push(Route.Lesson(next.first, next.second)) }, color = Color.White, content = accent,
                        )
                    } else {
                        Text(if (levels.isEmpty()) s.loading else s.allLessonsDone, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Mascot(size = if (ui.kid) 110.dp else 90.dp, mood = Mood.HAPPY, body = Color.White)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickCard("🎼", s.practiceSolfeos, Modifier.weight(1f)) { app.tab = Tab.SOLFEOS }
            QuickCard("🎹", s.freePiano, Modifier.weight(1f)) { app.tab = Tab.PIANO }
        }
    }
}

@Composable
private fun QuickCard(emoji: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    PressCard(modifier, onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(emoji, fontSize = 34.sp)
            Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}
