package com.solfi.app.platform

import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import java.util.prefs.Preferences
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.withLock as kWithLock

actual class PlatformLock actual constructor() {
    private val lock = ReentrantLock()
    actual fun <T> withLock(block: () -> T): T = lock.kWithLock(block)
}

actual class AudioSink actual constructor() {
    private var line: SourceDataLine? = null
    @Volatile private var running = false
    private var thread: Thread? = null

    actual fun start(sampleRate: Int, render: (FloatArray, Int) -> Unit) {
        if (running) return
        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        val l = AudioSystem.getSourceDataLine(format)
        val frames = 512
        l.open(format, frames * 2 * 4)
        l.start()
        line = l
        running = true
        thread = Thread({
            val f = FloatArray(frames)
            val bytes = ByteArray(frames * 2)
            while (running) {
                render(f, frames)
                for (i in 0 until frames) {
                    val v = (f[i] * 32767f).toInt().coerceIn(-32768, 32767)
                    bytes[i * 2] = (v and 0xFF).toByte()
                    bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                }
                l.write(bytes, 0, bytes.size)
            }
        }, "solfi-audio").apply { isDaemon = true; priority = Thread.MAX_PRIORITY; start() }
    }

    actual fun stop() {
        running = false
        thread?.join(500)
        line?.let { it.stop(); it.close() }
        line = null
    }

    actual fun playedFrames(): Long = line?.longFramePosition ?: 0L
    actual fun latencyFrames(): Int = 512 * 4
}

actual class Haptics actual constructor() {
    actual fun feedback(success: Boolean) {}
}

/** java.util.prefs limits a value to 8192 chars, so values are chunked. */
actual class KeyValueStore actual constructor(name: String) {
    private val prefs: Preferences = Preferences.userRoot().node("com/solfi/app/$name")
    private val chunk = 7000

    actual fun getString(key: String): String? {
        val n = prefs.getInt("$key.n", -1)
        if (n < 0) return null
        return buildString { for (i in 0 until n) append(prefs.get("$key.$i", "")) }
    }

    actual fun putString(key: String, value: String) {
        remove(key)
        val parts = value.chunked(chunk).ifEmpty { listOf("") }
        parts.forEachIndexed { i, s -> prefs.put("$key.$i", s) }
        prefs.putInt("$key.n", parts.size)
        prefs.flush()
    }

    actual fun remove(key: String) {
        val n = prefs.getInt("$key.n", 0)
        for (i in 0 until n) prefs.remove("$key.$i")
        prefs.remove("$key.n")
    }
}

actual fun systemLanguageCode(): String = Locale.getDefault().language

actual fun todayEpochDay(): Int = java.time.LocalDate.now().toEpochDay().toInt()

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

/** Microphone through javax.sound [javax.sound.sampled.TargetDataLine] (16-bit mono), read on its own thread. */
actual class MicInput actual constructor() {
    @Volatile private var running = false
    private var line: javax.sound.sampled.TargetDataLine? = null
    private var thread: Thread? = null

    actual fun start(onStatus: (MicStatus) -> Unit, onSamples: (FloatArray, Int, Int) -> Unit) {
        if (running) return
        val rate = 44100
        val format = AudioFormat(rate.toFloat(), 16, 1, true, false)
        val l = try {
            (AudioSystem.getLine(javax.sound.sampled.DataLine.Info(javax.sound.sampled.TargetDataLine::class.java, format)) as javax.sound.sampled.TargetDataLine)
                .also { it.open(format, 4096 * 2); it.start() }
        } catch (e: SecurityException) {
            onStatus(MicStatus.DENIED); return
        } catch (e: Exception) {
            onStatus(MicStatus.UNAVAILABLE); return
        }
        line = l
        running = true
        onStatus(MicStatus.LISTENING)
        thread = Thread({
            val frames = 1024
            val bytes = ByteArray(frames * 2)
            val f = FloatArray(frames)
            while (running) {
                val n = l.read(bytes, 0, bytes.size)
                if (n <= 0) continue
                val k = n / 2
                for (i in 0 until k) f[i] = ((bytes[2 * i].toInt() and 0xFF) or (bytes[2 * i + 1].toInt() shl 8)).toShort() / 32768f
                onSamples(f, k, rate)
            }
        }, "solfi-mic").apply { isDaemon = true; start() }
    }

    actual fun stop() {
        running = false
        line?.let { runCatching { it.stop() }; runCatching { it.close() } }
        thread?.join(500)
        line = null
        thread = null
    }
}

/** Desktop: start with `-Dsolfi.ngl=true` to show the NGL tab. */
actual fun nglEnabled(): Boolean = System.getProperty("solfi.ngl") == "true"
