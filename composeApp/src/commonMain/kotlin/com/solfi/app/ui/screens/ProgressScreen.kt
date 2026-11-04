package com.melodia.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.melodia.app.AppState
import com.melodia.app.Route
import com.melodia.app.content.ContentRepository
import com.melodia.app.logic.ProgressRepository
import com.melodia.app.ui.theme.LocalUi
import com.melodia.app.ui.theme.Palette
import com.melodia.app.ui.theme.parseHexColor
import com.melodia.app.ui.widgets.PressCard
import com.melodia.app.ui.widgets.StatChip

@Composable
fun ProgressScreen(app: AppState) {
    val ui = LocalUi.current
    val s = ui.strings
    val p = app.progress
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(s.progressTitle, Modifier.weight(1f), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            IconButton({ app.push(Route.Settings) }) { Icon(Icons.Filled.Settings, s.cdSettings) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatChip("⚡", "${p.xp}", s.totalXp, Palette.Violet, Modifier.weight(1f))
            StatChip("🔥", s.streakDays(app.streak), s.currentStreak, Palette.Coral, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatChip("🏆", s.streakDays(p.bestStreak), s.bestStreak, Palette.Gold, Modifier.weight(1f))
            StatChip("⭐", "${ProgressRepository.totalStars(p)}", s.starsLabel, Palette.Sun, Modifier.weight(1f))
        }
        PressCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val lessons = p.lessons.values.count { it.completions > 0 }
                Row { Text(s.lessonsCompleted, Modifier.weight(1f)); Text("$lessons", fontWeight = FontWeight.Bold) }
                Row { Text(s.solfeosPlayed, Modifier.weight(1f)); Text("${p.solfeosPlayed}", fontWeight = FontWeight.Bold) }
                Row { Text(s.xpRank(ProgressRepository.xpLevel(p.xp)), Modifier.weight(1f)); Text("${p.xp % ProgressRepository.XP_PER_XPLEVEL}/${ProgressRepository.XP_PER_XPLEVEL}", fontWeight = FontWeight.Bold) }
                LinearProgressIndicator(
                    progress = { (p.xp % ProgressRepository.XP_PER_XPLEVEL) / ProgressRepository.XP_PER_XPLEVEL.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape), color = Palette.Violet, trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }

        Text(s.levelsProgress, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        for (n in 1..ContentRepository.MAX_LEVELS) {
            val level = app.levels.getOrNull(n - 1)
            PressCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (level == null) {
                        Text("${s.levelLabel(n)} · ${s.comingSoon}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    } else {
                        val (d, t) = ProgressRepository.levelProgress(level, p)
                        Row { Text("${s.levelLabel(n)} · ${level.title[ui.lang]}", Modifier.weight(1f), fontWeight = FontWeight.Bold); Text("$d/$t", fontWeight = FontWeight.Bold) }
                        LinearProgressIndicator(
                            progress = { if (t == 0) 0f else d / t.toFloat() }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            color = parseHexColor(level.color), trackColor = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }

        Text(s.badges, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        val earned = app.availableLevels.filter { it.badge.id in p.badges }
        if (earned.isEmpty()) Text(s.noBadgesYet, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            app.availableLevels.forEach { lv ->
                val has = lv.badge.id in p.badges
                Column(Modifier.weight(1f).alpha(if (has) 1f else 0.35f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) { Text(if (has) lv.badge.emoji else "🔒", fontSize = 30.sp) }
                    Text(lv.badge.name[ui.lang], fontSize = 12.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Box(Modifier.height(16.dp))
    }
}
