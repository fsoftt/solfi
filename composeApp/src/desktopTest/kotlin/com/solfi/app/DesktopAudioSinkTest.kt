package com.solfi.app

import com.solfi.app.audio.AudioEngine
import com.solfi.app.audio.PlayOptions
import com.solfi.app.music.Melody
import com.solfi.app.platform.AudioSink
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
            // Poll instead of fixed sleeps: opening the audio line can take a while on a loaded machine.
            waitUntil(8_000) { engine.audioClockFrames() > 10_000 }
            assertTrue(engine.audioClockFrames() > 10_000, "audio clock should advance, was ${engine.audioClockFrames()}")
            waitUntil(8_000) { !engine.isPlaying.value }
            assertTrue(!engine.isPlaying.value, "melody should have finished")
        } catch (e: IllegalArgumentException) {
            // no usable line on this machine
        } finally {
            engine.shutdown()
        }
    }

    private fun waitUntil(timeoutMs: Long, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end && !cond()) Thread.sleep(50)
    }
}
