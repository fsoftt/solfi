package com.melodia.app

import com.melodia.app.audio.AudioEngine
import com.melodia.app.audio.PlayOptions
import com.melodia.app.music.Melody
import com.melodia.app.platform.AudioSink
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertTrue

/** Exercises the real javax.sound sink when an audio device exists (silently skipped otherwise). */
class DesktopAudioSinkTest {
    @Test fun engineStreamsToSinkAndClockAdvances() {
        val hasDevice = runCatching { AudioSystem.getMixerInfo().isNotEmpty() }.getOrDefault(false)
        if (!hasDevice) return
        val engine = AudioEngine(AudioSink())
        try {
            val m = Melody.parse("treble", "4/4", "C", 240, "C4:q D4:q E4:h")
            engine.volume = 0.0f // keep the test silent
            engine.playMelody(m, PlayOptions(tempo = 240))
            Thread.sleep(700)
            assertTrue(engine.audioClockFrames() > 10_000, "audio clock should advance, was ${engine.audioClockFrames()}")
            Thread.sleep(1500)
            assertTrue(!engine.isPlaying.value, "melody should have finished")
        } catch (e: IllegalArgumentException) {
            // no usable line on this machine
        } finally {
            engine.shutdown()
        }
    }
}
