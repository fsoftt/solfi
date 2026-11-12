package com.solfi.app.platform

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
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

/**
 * Microphone through AVAudioEngine's input node. While listening the session switches to play-and-record (keeping
 * the loudspeaker as output); it returns to playback afterwards.
 * NOTE: like AudioSink, not compile-verified here (needs Xcode/macOS). The app's Info.plist must contain
 * NSMicrophoneUsageDescription.
 */
@OptIn(ExperimentalForeignApi::class)
actual class MicInput actual constructor() {
    private var engine: AVAudioEngine? = null
    private var wanted = false

    actual fun start(onStatus: (MicStatus) -> Unit, onSamples: (FloatArray, Int, Int) -> Unit) {
        if (engine != null) return
        wanted = true
        AVAudioSession.sharedInstance().requestRecordPermission { granted ->
            platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) {
                if (!granted) onStatus(MicStatus.DENIED) else if (wanted) open(onStatus, onSamples)
            }
        }
    }

    private fun open(onStatus: (MicStatus) -> Unit, onSamples: (FloatArray, Int, Int) -> Unit) {
        if (engine != null) return
        val session = AVAudioSession.sharedInstance()
        session.setCategory(
            AVAudioSessionCategoryPlayAndRecord,
            withOptions = AVAudioSessionCategoryOptionDefaultToSpeaker or AVAudioSessionCategoryOptionMixWithOthers,
            error = null,
        )
        // measurement mode turns off voice processing (high-pass, AGC) so low brass and basses are heard
        session.setMode(platform.AVFAudio.AVAudioSessionModeMeasurement, error = null)
        session.setActive(true, error = null)
        val e = AVAudioEngine()
        val input = e.inputNode
        val format = input.outputFormatForBus(0u)
        val rate = format.sampleRate.toInt()
        if (rate <= 0) { onStatus(MicStatus.UNAVAILABLE); return }
        var scratch = FloatArray(4096)
        input.installTapOnBus(0u, bufferSize = 2048u, format = format) { buffer, _ ->
            val b = buffer ?: return@installTapOnBus
            val n = b.frameLength.toInt()
            val ch = b.floatChannelData?.get(0) ?: return@installTapOnBus
            if (scratch.size < n) scratch = FloatArray(n)
            for (i in 0 until n) scratch[i] = ch[i]
            onSamples(scratch, n, rate)
        }
        e.prepare()
        if (!e.startAndReturnError(null)) { input.removeTapOnBus(0u); onStatus(MicStatus.UNAVAILABLE); return }
        engine = e
        onStatus(MicStatus.LISTENING)
    }

    actual fun stop() {
        wanted = false
        engine?.let { it.inputNode.removeTapOnBus(0u); it.stop() }
        if (engine != null) {
            AVAudioSession.sharedInstance().setMode(platform.AVFAudio.AVAudioSessionModeDefault, error = null)
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null)
        }
        engine = null
    }
}
