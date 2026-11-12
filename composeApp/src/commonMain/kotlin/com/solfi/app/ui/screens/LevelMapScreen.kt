package com.solfi.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.solfi.app.AppState
import com.solfi.app.Route
import com.solfi.app.content.ContentRepository
import com.solfi.app.content.LevelContent
import com.solfi.app.logic.ProgressRepository
import com.solfi.app.ui.theme.LocalUi
import com.solfi.app.ui.theme.Palette
import com.solfi.app.ui.theme.parseHexColor
import com.solfi.app.ui.widgets.ScreenTitle
import com.solfi.app.ui.widgets.Stars
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun LevelMapScreen(app: AppState) {
    val ui = LocalUi.current
    val s = ui.strings
    val levels = app.levels
    var skipAsk by remember { mutableStateOf<LevelContent?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item { ScreenTitle(s.navLearn) }
        if (!app.contentLoaded) item { Text(s.loading, Modifier.padding(20.dp)) }
        for (n in 1..ContentRepository.MAX_LEVELS) {
            val level = levels.getOrNull(n - 1)
            if (level == null) {
                if (app.contentLoaded) item(key = "soon$n") { ComingSoonCard(n) }
                continue
            }
            val unlocked = ProgressRepository.isLevelUnlocked(level.level, app.progress)
            val canSkip = ProgressRepository.canTakeSkipTest(level, app.progress)
            item(key = "head$n") { LevelHeader(level, app, unlocked, onSkip = if (canSkip) ({ skipAsk = level }) else null) }
            level.lessons.forEachIndexed { i, lesson ->
                item(key = "l$n-${lesson.id}") {
                    LessonNode(
                        level = level, index = i, app = app, levelIndex = n - 1,
                        hasNext = i < level.lessons.lastIndex,
                    )
                }
            }
        }
    }

    skipAsk?.let { level ->
        AlertDialog(
            onDismissRequest = { skipAsk = null },
            title = { Text(s.skipLevelTitle(level.level)) },
            text = { Text(s.skipLevelText(level.level, (ProgressRepository.EXAM_PASS * 100).roundToInt())) },
            confirmButton = {
                TextButton({ skipAsk = null; app.push(Route.SkipTest(level.level - 1)) }) { Text(s.startSkipTest) }
            },
            dismissButton = { TextButton({ skipAsk = null }) { Text(s.cancel) } },
        )
    }
}

@Composable
private fun ComingSoonCard(n: Int) {
    val ui = LocalUi.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(20.dp),
    ) {
        Text("${ui.strings.levelLabel(n)} · ${ui.strings.comingSoon}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("🔒 ${ui.strings.comingSoonDesc}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

@Composable
private fun LevelHeader(level: LevelContent, app: AppState, unlocked: Boolean, onSkip: (() -> Unit)?) {
    val ui = LocalUi.current
    val color = parseHexColor(level.color)
    val (done, total) = ProgressRepository.levelProgress(level, app.progress)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).clip(RoundedCornerShape(if (ui.kid) 30.dp else 20.dp))
            .background(if (unlocked) color else MaterialTheme.colorScheme.outline).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(ui.strings.levelLabel(level.level).uppercase(), color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(level.title[ui.lang], color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        Text(level.description[ui.lang], color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, lineHeight = 19.sp)
        if (unlocked) {
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else done / total.toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Color.White, trackColor = Color.White.copy(alpha = 0.3f),
            )
            Text(ui.strings.lessonsDone(done, total), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        } else Text("🔒 ${ui.strings.levelLockedHint}", color = Color.White, fontSize = 13.sp)
        if (onSkip != null) {
            Text(
                "⏭️ ${ui.strings.skipLevelButton}",
                Modifier.padding(top = 4.dp).clip(CircleShape).border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    .clickable(onClick = onSkip).padding(horizontal = 14.dp, vertical = 8.dp),
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun LessonNode(level: LevelContent, index: Int, app: AppState, levelIndex: Int, hasNext: Boolean) {
    val ui = LocalUi.current
    val lesson = level.lessons[index]
    val color = parseHexColor(level.color)
    val unlocked = ProgressRepository.isLessonUnlocked(level, index, app.progress)
    val result = app.progress.result(level.level, lesson.id)
    val completed = ProgressRepository.isLessonCompleted(level, index, app.progress)
    val current = unlocked && !completed
    val nodeSize = (if (ui.kid) 84.dp else 72.dp) + (if (lesson.isExam) 8.dp else 0.dp)
    val rowH = nodeSize + 66.dp
    fun xf(i: Int) = 0.5f + 0.26f * sin(i * 1.15f)
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxWidth().height(rowH)) {
        val w = maxWidth
        val cx = w * xf(index)
        val connector = MaterialTheme.colorScheme.outlineVariant
        if (hasNext) {
            Canvas(Modifier.fillMaxSize()) {
                val x1 = with(density) { cx.toPx() }
                val x2 = with(density) { (w * xf(index + 1)).toPx() }
                val y1 = size.height / 2
                val y2 = size.height * 1.5f
                val p = Path().apply { moveTo(x1, y1); cubicTo(x1, y1 + size.height * 0.5f, x2, y2 - size.height * 0.5f, x2, y2) }
                drawPath(p, connector, style = Stroke(width = 8f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 22f))))
            }
        }
        val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(1f, 1.07f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
        val desc = lesson.title[ui.lang] + if (!unlocked) " – ${ui.strings.cdLocked}" else ""
        Column(
            Modifier.offset(x = cx - nodeSize / 2 - 20.dp).padding(horizontal = 0.dp).size(nodeSize + 40.dp, rowH),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(nodeSize).scale(if (current) pulse else 1f).clip(CircleShape)
                    .background(
                        when {
                            !unlocked -> MaterialTheme.colorScheme.surfaceVariant
                            lesson.isExam -> Palette.Gold
                            else -> color
                        },
                    )
                    .clickable(enabled = unlocked) { app.push(Route.Lesson(levelIndex, index)) }
                    .semantics { contentDescription = desc },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (unlocked) lesson.emoji else "🔒", fontSize = (nodeSize.value * 0.45f).sp)
            }
            if (completed && result != null && result.completions > 0) Stars(result.stars, 15.dp)
            Text(
                lesson.title[ui.lang], fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2,
                color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
