package com.melodia.app.platform

/** Reentrant mutual-exclusion lock usable from the audio thread and the UI thread. */
expect class PlatformLock() {
    fun <T> withLock(block: () -> T): T
}

/**
 * Streams float PCM (mono, [sampleRate]) produced by a render callback to the device.
 * The callback is invoked on an audio thread and must fill the whole buffer.
 */
expect class AudioSink() {
    /** Starts streaming. [render] is called repeatedly with a buffer to fill. */
    fun start(sampleRate: Int, render: (buffer: FloatArray, frames: Int) -> Unit)
    fun stop()
    /** Frames that have actually been played (audio clock). */
    fun playedFrames(): Long
    /** Approximate output latency in frames. */
    fun latencyFrames(): Int
}

/** Haptic feedback for answers. */
expect class Haptics() {
    fun feedback(success: Boolean)
}

/** Tiny persistent string key/value store (SharedPreferences / NSUserDefaults / java.util.prefs). */
expect class KeyValueStore(name: String) {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

/** BCP-47-ish language code of the system locale, e.g. "es", "en", "pt". */
expect fun systemLanguageCode(): String

/** Days since 1970-01-01 in local time. */
expect fun todayEpochDay(): Int

expect fun currentTimeMillis(): Long
