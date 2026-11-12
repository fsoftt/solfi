package com.solfi.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.solfi.app.audio.AudioEngine
import com.solfi.app.audio.InstrumentListener
import com.solfi.app.audio.SAMPLE_RATE
import com.solfi.app.content.ContentJson
import com.solfi.app.data.ProfileStore
import com.solfi.app.logic.TimedScorer
import com.solfi.app.logic.TimedVerdict
import com.solfi.app.music.Lang
import com.solfi.app.music.NoteNaming
import com.solfi.app.music.Pitch
import com.solfi.app.platform.Haptics
import com.solfi.app.platform.KeyValueStore
import com.solfi.app.ui.notation.StaffView
import com.solfi.app.ui.screens.EnsembleTake
import com.solfi.app.ui.theme.LocalUi
import com.solfi.app.ui.theme.SolfiTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End to end: the engine runs the take (no sound card: the mixer is rendered by hand), a synthetic trumpet is fed to
 * the microphone at the right moments, and the notes are judged on the playback clock.
 */
class EnsembleTakeTest {
    private val kv = KeyValueStore("solfi-take-test")
    private val engine = AudioEngine(null)
    private val app = AppState(ProfileStore(kv), engine, Haptics(), "es", InstrumentListener(null), showNgl = true).also {
        it.createProfile("Test", "🦊", kid = false, lang = Lang.ES, naming = NoteNaming.SOLFEGE)
    }
    private val piece = ContentJson.decodeEnsembleBook(File("src/commonMain/composeResources/files/content/ngl.json").readText()).pieces.first()

    @AfterTest fun cleanup() { app.profiles.forEach { app.deleteProfile(it.id) }; kv.remove("profiles"); kv.remove("currentProfile") }

    /** Runs a take of part [partId] at [tempo]; [play] gives, for note i, (concert MIDI to play or null, onset shift in ms). */
    private fun runTake(partId: String, tempo: Int, play: (Int, Int) -> Pair<Int?, Double>): EnsembleTake {
        val part = piece.parts.single { it.id == partId }
        val written = part.written(piece)
        val take = EnsembleTake(app, written, part.concert(piece), emptyList(), part.tuning(piece), octaveFree = false)
        take.start(tempo, metronome = false, accompaniment = false)
        val notes = take.notes
        val msPerBeat = 60_000.0 / tempo
        val out = FloatArray(1024)
        val mic = FloatArray(1024)
        var phase = 0.0
        var frames = 0L
        while (take.phase.value == EnsembleTake.Phase.RUNNING && frames < SAMPLE_RATE * 60L) {
            engine.mixer.render(out, out.size)
            val beat = engine.beatNow()
            for (k in mic.indices) {
                val b = beat + k / (SAMPLE_RATE * 60.0 / tempo)
                val i = notes.indexOfLast { n -> b >= n.startBeat + play(notes.indexOf(n), n.concertMidis.first()).second / msPerBeat }
                val n = notes.getOrNull(i)
                val midi = n?.let { play(i, it.concertMidis.first()).first }
                // short articulation gap at the end of each note
                val sounding = n != null && midi != null && b < n.endBeat + play(i, n.concertMidis.first()).second / msPerBeat - 60.0 / msPerBeat
                if (sounding) {
                    phase += 2 * PI * Pitch.frequencyOfMidi(midi!!) / SAMPLE_RATE
                    mic[k] = (0.3 * sin(phase) + 0.08 * sin(2 * phase)).toFloat()
                } else mic[k] = 0f
            }
            app.listener.feedForTest(mic, mic.size, SAMPLE_RATE)
            frames += out.size
        }
        assertEquals(EnsembleTake.Phase.DONE, take.phase.value, "take must finish")
        return take
    }

    @Test fun cleanTakeOfTrumpetOneIsOnTime() {
        val take = runTake("p1", 120) { _, midi -> midi to 0.0 }
        val r = take.results.value
        assertEquals(take.notes.size, r.size)
        val sum = TimedScorer.summary(take.notes, r)
        assertTrue(sum.played == sum.total, "every note heard: $r")
        assertTrue(sum.onTime >= sum.total - 1, "on time: $sum")
        assertTrue(sum.inTune >= sum.total - 1, "in tune: $sum")
    }

    @Test fun mistakesAreReportedWhereTheyHappen() {
        // note 2 (an eighth) a whole tone too high, note 5 (a long note) 250 ms late, note 8 left out
        val take = runTake("p1", 120) { i, midi ->
            when (i) { 2 -> (midi + 2) to 0.0; 5 -> midi to 250.0; 8 -> null to 0.0; else -> midi to 0.0 }
        }
        val r = take.results.value
        assertEquals(TimedVerdict.WRONG_NOTE, r[2].verdict, r.toString())
        assertEquals(TimedVerdict.LATE, r[5].verdict, r.toString())
        assertEquals(TimedVerdict.MISSED, r[8].verdict, r.toString())
        assertTrue(r.filterIndexed { i, _ -> i !in setOf(2, 5, 8) }.all { it.verdict == TimedVerdict.ON_TIME }, r.toString())
        assertEquals(listOf(2, 4, 5), TimedScorer.summary(take.notes, r).measuresToReview)
        renderResult(take)
    }

    /** Coloured staff + summary (build/render/ngl-take.png) for eyeballing. */
    private fun renderResult(take: EnsembleTake) {
        val results = take.results.value
        val sum = TimedScorer.summary(take.notes, results)
        val scene = ImageComposeScene(860, 1800, Density(2f)) {
            SolfiTheme("LIGHT", kid = false) {
                CompositionLocalProvider(LocalUi provides app.ui) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column {
                            val s = app.ui.strings
                            Text(s.nglTakeSummary(sum.total, sum.played, sum.onTime, sum.inTune))
                            Text(s.nglReview(sum.measuresToReview.joinToString(", ")))
                            Text(s.nglLegend)
                            StaffView(take.written, labelFor = { app.ui.noteName(it) }, noteColor = take.colorOf(results))
                        }
                    }
                }
            }
        }
        val img = scene.render(100_000_000L)
        scene.close()
        File("build/render").mkdirs()
        File("build/render/ngl-take.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }
}
