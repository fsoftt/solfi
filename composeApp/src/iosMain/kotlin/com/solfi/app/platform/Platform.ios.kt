package com.solfi.app.platform

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSourceNode
import platform.AVFAudio.setActive
import platform.Foundation.NSDate
import platform.Foundation.NSLocale
import platform.Foundation.NSRecursiveLock
import platform.Foundation.NSTimeZone
import platform.Foundation.NSUserDefaults
import platform.Foundation.localTimeZone
import platform.Foundation.preferredLanguages
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

actual class PlatformLock actual constructor() {
    private val lock = NSRecursiveLock()
    actual fun <T> withLock(block: () -> T): T {
        lock.lock()
        try { return block() } finally { lock.unlock() }
    }
}

/**
 * AVAudioEngine + AVAudioSourceNode pulling float PCM from the shared render callback.
 * NOTE: written for iOS but not compile-verified on Windows (needs Xcode/macOS).
 */
@OptIn(ExperimentalForeignApi::class)
actual class AudioSink actual constructor() {
    private var engine: AVAudioEngine? = null
    private var sampleRate = 44100
    private var rendered = 0L
    private var latency = 0

    actual fun start(sampleRate: Int, render: (FloatArray, Int) -> Unit) {
        if (engine != null) return
        this.sampleRate = sampleRate
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)
        session.setPreferredIOBufferDuration(0.005, error = null)
        session.setActive(true, error = null)
        latency = ((session.outputLatency + session.IOBufferDuration) * sampleRate).toInt()

        val scratch = FloatArray(4096)
        val format = AVAudioFormat(standardFormatWithSampleRate = sampleRate.toDouble(), channels = 1u)
        val node = AVAudioSourceNode(renderBlock = { _, _, frameCount, outputData ->
            val frames = frameCount.toInt()
            val out = outputData!!.pointed.mBuffers.pointed.mData!!.reinterpret<FloatVar>()
            var done = 0
            while (done < frames) {
                val n = minOf(scratch.size, frames - done)
                render(scratch, n)
                for (i in 0 until n) out[done + i] = scratch[i]
                done += n
            }
            rendered += frames
            0 // noErr
        })
        val e = AVAudioEngine()
        e.attachNode(node)
        e.connect(node, to = e.mainMixerNode, format = format)
        e.startAndReturnError(null)
        engine = e
    }

    actual fun stop() {
        engine?.stop()
        engine = null
    }

    actual fun playedFrames(): Long = maxOf(0L, rendered - latency)
    actual fun latencyFrames(): Int = latency
}

actual class Haptics actual constructor() {
    actual fun feedback(success: Boolean) {
        if (success) {
            UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).impactOccurred()
        } else {
            UINotificationFeedbackGenerator().notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeError)
        }
    }
}

actual class KeyValueStore actual constructor(private val name: String) {
    private val defaults = NSUserDefaults.standardUserDefaults
    private fun k(key: String) = "$name.$key"
    actual fun getString(key: String): String? = defaults.stringForKey(k(key))
    actual fun putString(key: String, value: String) { defaults.setObject(value, forKey = k(key)) }
    actual fun remove(key: String) { defaults.removeObjectForKey(k(key)) }
}

actual fun systemLanguageCode(): String =
    (NSLocale.preferredLanguages.firstOrNull() as? String)?.take(2) ?: "es"

actual fun todayEpochDay(): Int {
    val now = NSDate().timeIntervalSince1970
    return ((now + NSTimeZone.localTimeZone.secondsFromGMT) / 86400.0).toInt()
}

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {}
