package com.solfi.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.solfi.app.AppState
import com.solfi.app.logic.CompletionResult
import com.solfi.app.platform.PlatformBackHandler
import com.solfi.app.ui.theme.LocalUi
import com.solfi.app.ui.theme.Palette
import com.solfi.app.ui.theme.parseHexColor
import com.solfi.app.ui.widgets.BigButton
import com.solfi.app.ui.widgets.Confetti
import com.solfi.app.ui.widgets.Mascot
import com.solfi.app.ui.widgets.Mood
import com.solfi.app.ui.widgets.SecondaryButton
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun LessonScreen(app: AppState, levelIndex: Int, lessonIndex: Int) {
    val ui = LocalUi.current
    val s = ui.strings
    val level = app.levels.getOrNull(levelIndex)
    val lesson = level?.lessons?.getOrNull(lessonIndex)
    if (level == null || lesson == null) { app.pop(); return }

    var attempt by remember { mutableIntStateOf(0) }
    var stepIndex by remember(attempt) { mutableIntStateOf(0) }
    val scores = remember(attempt) { mutableStateListOf<Double>() }
    var result by remember(attempt) { mutableStateOf<CompletionResult?>(null) }
    var accuracy by remember(attempt) { mutableStateOf(1.0) }
    var confirmExit by remember { mutableStateOf(false) }
    val color = parseHexColor(level.color)

    PlatformBackHandler(true) { if (result == null) confirmExit = true else app.pop() }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (result == null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ confirmExit = true }) { Icon(Icons.Filled.Close, s.exitLesson) }
                val progress by animateFloatAsState(stepIndex / lesson.steps.size.toFloat(), tween(450), label = "progress")
                LinearProgressIndicator(
                    progress = { progress }, modifier = Modifier.weight(1f).height(if (ui.kid) 14.dp else 10.dp).clip(CircleShape),
                    color = color, trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(s.stepOf((stepIndex + 1).coerceAtMost(lesson.steps.size), lesson.steps.size), Modifier.padding(horizontal = 12.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val r = result
        if (r != null) {
            LessonResultView(app, lesson.isExam, r, accuracy, onRetry = { attempt++ }, onClose = { app.pop() }, levelColor = color)
        } else {
            AnimatedContent(
                stepIndex, Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = { (slideInHorizontally(tween(300)) { it / 2 } + fadeIn(tween(300))) togetherWith (slideOutHorizontally(tween(300)) { -it / 2 } + fadeOut(tween(200))) },
                label = "step",
            ) { idx ->
                val step = lesson.steps[idx]
                StepContent(app, step) { score ->
                    app.engine.stop()
                    if (score != null) scores += score
                    if (idx + 1 >= lesson.steps.size) {
                        accuracy = if (scores.isEmpty()) 1.0 else scores.average()
                        result = app.completeLesson(levelIndex, lessonIndex, accuracy)
                    } else stepIndex = idx + 1
                }
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(s.exitLesson) }, text = { Text(s.exitLessonText) },
            confirmButton = { TextButton({ confirmExit = false; app.pop() }) { Text(s.leave) } },
            dismissButton = { TextButton({ confirmExit = false }) { Text(s.keepGoing) } },
        )
    }
}

@Composable
private fun LessonResultView(
    app: AppState, isExam: Boolean, r: CompletionResult, accuracy: Double,
    onRetry: () -> Unit, onClose: () -> Unit, levelColor: Color,
) {
    val ui = LocalUi.current
    val s = ui.strings
    val badge = r.newBadge
    LaunchedEffect(Unit) { if (r.passed) app.engine.playFeedback(true) }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            Mascot(size = 150.dp, mood = if (r.passed) Mood.CHEER else Mood.THINKING)
            Text(
                if (!r.passed) s.examFailed(ui.kid) else if (isExam) s.examPassed(ui.kid) else s.lessonComplete(ui.kid),
                fontSize = if (ui.kid) 28.sp else 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
            )
            if (r.passed) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 1..3) {
                        val a = remember { Animatable(0f) }
                        LaunchedEffect(Unit) { delay(300L * i); a.animateTo(1f, spring(Spring.DampingRatioHighBouncy)) }
                        Text("★", Modifier.scale(a.value), fontSize = 58.sp, color = if (i <= r.stars) Palette.Gold else MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            Text(s.accuracyLabel((accuracy * 100).roundToInt()), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(s.xpGained(r.xpGained), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Violet)
            if (badge != null) {
                Column(
                    Modifier.clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.tertiaryContainer).padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(badge.emoji, fontSize = 52.sp)
                    Text(s.badgeEarned, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(badge.name[ui.lang], fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                }
            }
            r.unlockedLevel?.let { n ->
                if (app.levels.getOrNull(n - 1) != null) Text("🔓 " + s.levelUnlocked(n), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(8.dp))
            if (!r.passed) BigButton(s.retry, onRetry, color = levelColor)
            BigButton(s.backToMap, onClose, color = if (r.passed) levelColor else MaterialTheme.colorScheme.outline)
            if (r.passed) SecondaryButton(s.retry, onRetry)
        }
        if (r.passed) Confetti(Modifier.fillMaxSize())
    }
}

