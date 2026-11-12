package com.solfi.app.platform

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
        }, "solfi-audio").also { it.isDaemon = true; it.start() }
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

/** Asks the user for RECORD_AUDIO; installed by MainActivity (needs an activity result launcher). */
object MicPermission {
    var requester: ((onResult: (Boolean) -> Unit) -> Unit)? = null
}

/** Microphone through [android.media.AudioRecord] (16-bit mono, voice-recognition source = little processing). */
actual class MicInput actual constructor() {
    @Volatile private var running = false
    @Volatile private var wanted = false
    private var record: android.media.AudioRecord? = null
    private var thread: Thread? = null

    actual fun start(onStatus: (MicStatus) -> Unit, onSamples: (FloatArray, Int, Int) -> Unit) {
        if (running) return
        wanted = true
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(AppContext.app, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) { open(onStatus, onSamples); return }
        val ask = MicPermission.requester ?: run { onStatus(MicStatus.DENIED); return }
        ask { ok -> if (!ok) onStatus(MicStatus.DENIED) else if (wanted) open(onStatus, onSamples) }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun open(onStatus: (MicStatus) -> Unit, onSamples: (FloatArray, Int, Int) -> Unit) {
        if (running) return
        val rate = 44100
        val min = android.media.AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) { onStatus(MicStatus.UNAVAILABLE); return }
        val rec = try {
            // UNPROCESSED keeps the low end (tuba, double bass) that voice processing filters out; it is optional
            // on devices, so fall back to VOICE_RECOGNITION (also lightly processed) when it is not supported.
            val am = AppContext.app.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val unprocessed = am.getProperty(android.media.AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
            val source = if (unprocessed) android.media.MediaRecorder.AudioSource.UNPROCESSED else android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION
            android.media.AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 4096 * 2))
        } catch (e: Exception) { onStatus(MicStatus.UNAVAILABLE); return }
        if (rec.state != android.media.AudioRecord.STATE_INITIALIZED) { rec.release(); onStatus(MicStatus.UNAVAILABLE); return }
        try { rec.startRecording() } catch (e: Exception) { rec.release(); onStatus(MicStatus.UNAVAILABLE); return }
        record = rec
        running = true
        onStatus(MicStatus.LISTENING)
        thread = Thread({
            val frames = 1024
            val s = ShortArray(frames)
            val f = FloatArray(frames)
            while (running) {
                val n = rec.read(s, 0, frames)
                if (n <= 0) continue
                for (i in 0 until n) f[i] = s[i] / 32768f
                onSamples(f, n, rate)
            }
        }, "solfi-mic").also { it.isDaemon = true; it.start() }
    }

    actual fun stop() {
        wanted = false
        running = false
        thread?.join(500)
        record?.let { runCatching { it.stop() }; it.release() }
        record = null
        thread = null
    }
}
