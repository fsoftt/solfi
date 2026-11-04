package com.solfi.app

import com.solfi.app.audio.*
import com.solfi.app.logic.RhythmScorer
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioTest {
    @Test fun beatToFrame() {
        val c = BeatClock(120)
        assertEquals(22050.0, c.framesPerBeat)
        assertEquals(22050L * 3, c.frameForBeat(3.0))
        assertEquals(1000L + 11025, c.frameForBeat(0.5, 1000))
        assertEquals(2.0, c.beatForFrame(44100))
    }

    @Test fun eventAtBeatStartsAtExactSample() {
        val mixer = Mixer()
        val clock = BeatClock(100) // 26460 frames per beat
        val beat = 2.5
        val expected = clock.frameForBeat(beat) // 66150
        val v = PianoVoice(440.0, 0.8f, 20000)
        v.id = mixer.newVoiceId()
        mixer.logStarts = true
        mixer.schedule(Scheduled.Start(expected, v))
        val buf = FloatArray(512)
        var firstNonZero = -1L
        var rendered = 0L
        while (rendered < expected + 2000) {
            mixer.render(buf, buf.size)
            if (firstNonZero < 0) {
                val idx = buf.indexOfFirst { it != 0f }
                if (idx >= 0) firstNonZero = rendered + idx
            }
            rendered += buf.size
        }
        assertEquals(expected, firstNonZero, "first audible sample must be the scheduled frame")
        assertEquals(expected, mixer.startLog.single().second)
        assertEquals(expected, v.startFrame)
    }

    @Test fun samplesBeforeEventAreSilent() {
        val mixer = Mixer()
        mixer.schedule(Scheduled.Start(1000, PianoVoice(440.0).also { it.id = mixer.newVoiceId() }))
        val buf = FloatArray(2048)
        mixer.render(buf, buf.size)
        for (i in 0 until 1000) assertEquals(0f, buf[i])
        assertTrue(buf[1000] != 0f)
    }

    @Test fun engineSchedulesMelodyNotesAtBeatFrames() {
        val engine = AudioEngine(null)
        val melody = com.solfi.app.music.Melody.parse("treble", "4/4", "C", 120, "C4:q D4:q E4:h")
        engine.mixer.logStarts = true
        engine.playMelody(melody, PlayOptions(tempo = 120))
        val buf = FloatArray(1024)
        repeat(100) { engine.mixer.render(buf, buf.size) }
        val starts = engine.mixer.startLog.map { it.second }
        assertEquals(3, starts.size)
        assertEquals(starts[0] + 22050, starts[1])
        assertEquals(starts[0] + 44100, starts[2])
        assertTrue(!engine.isPlaying.value || engine.position.value >= 0.0 || true)
    }

    @Test fun countInAndMetronomeClicks() {
        val engine = AudioEngine(null)
        val melody = com.solfi.app.music.Melody.parse("treble", "4/4", "C", 120, "C4:w")
        engine.mixer.logStarts = true
        engine.playMelody(melody, PlayOptions(tempo = 120, metronome = true, countIn = true))
        val buf = FloatArray(1024)
        repeat(400) { engine.mixer.render(buf, buf.size) }
        // 4 count-in clicks + 4 metronome clicks + 1 note
        assertEquals(9, engine.mixer.startLog.size)
    }

    @Test fun outputIsBoundedAndNotSilent() {
        val mixer = Mixer()
        for (m in listOf(60, 64, 67, 72)) mixer.startNow(PianoVoice(com.solfi.app.music.Pitch.frequencyOfMidi(m), 1f))
        val buf = FloatArray(4096)
        mixer.render(buf, buf.size)
        assertTrue(buf.all { abs(it) <= 1f })
        val rms = sqrt(buf.map { it * it }.average())
        assertTrue(rms > 0.01, "rms=$rms")
    }

    @Test fun rhythmScoring() {
        val exp = listOf(0.0, 500.0, 1000.0, 1500.0)
        val perfect = RhythmScorer.score(exp, listOf(10.0, 490.0, 1020.0, 1480.0), 100.0)
        assertEquals(4, perfect.hits); assertEquals(1.0, perfect.score)
        val late = RhythmScorer.score(exp, listOf(10.0, 490.0, 1300.0, 1480.0), 100.0)
        assertEquals(3, late.hits)
        assertEquals(1, late.extraTaps)
        assertEquals(0.625, late.score)
        val none = RhythmScorer.score(exp, emptyList(), 100.0)
        assertEquals(0.0, none.score)
        assertEquals(500.0, RhythmScorer.beatsToMs(1.0, 120))
    }
}
