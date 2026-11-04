package com.melodia.app.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock as kWithLock

/** Holds the application context for the platform actuals; set in MainActivity. */
object AppContext {
    lateinit var app: Context
}

actual class PlatformLock actual constructor() {
    private val lock = ReentrantLock()
    actual fun <T> withLock(block: () -> T): T = lock.kWithLock(block)
}

/** Streaming float PCM through [AudioTrack] in low-latency mode, fed from a dedicated render thread. */
actual class AudioSink actual constructor() {
    private var track: AudioTrack? = null
    @Volatile private var running = false
    private var thread: Thread? = null
    private var bufferFrames = 0

    actual fun start(sampleRate: Int, render: (FloatArray, Int) -> Unit) {
        if (running) return
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val chunk = 256
        val bytes = maxOf(minBytes, chunk * 4 * 2)
        bufferFrames = bytes / 4
        val builder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bytes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        val t = builder.build()
        track = t
        running = true
        t.play()
        thread = Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            val buf = FloatArray(chunk)
            while (running) {
                render(buf, chunk)
                t.write(buf, 0, chunk, AudioTrack.WRITE_BLOCKING)
            }
        }, "melodia-audio").also { it.isDaemon = true; it.start() }
    }

    actual fun stop() {
        running = false
        thread?.join(500)
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
    }

    /** Playback head position (a 32-bit counter that wraps; masked to unsigned). */
    actual fun playedFrames(): Long = (track?.playbackHeadPosition?.toLong() ?: 0L) and 0xFFFFFFFFL

    actual fun latencyFrames(): Int = bufferFrames
}

@Suppress("DEPRECATION")
actual class Haptics actual constructor() {
    private val vibrator: Vibrator? by lazy {
        val ctx = AppContext.app
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    actual fun feedback(success: Boolean) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val ms = if (success) 25L else 60L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            v.vibrate(ms)
        }
    }
}

actual class KeyValueStore actual constructor(name: String) {
    private val prefs = AppContext.app.getSharedPreferences(name, Context.MODE_PRIVATE)
    actual fun getString(key: String): String? = prefs.getString(key, null)
    actual fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    actual fun remove(key: String) { prefs.edit().remove(key).apply() }
}

actual fun systemLanguageCode(): String = Locale.getDefault().language

actual fun todayEpochDay(): Int {
    val now = System.currentTimeMillis()
    return ((now + TimeZone.getDefault().getOffset(now)) / 86_400_000L).toInt()
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
