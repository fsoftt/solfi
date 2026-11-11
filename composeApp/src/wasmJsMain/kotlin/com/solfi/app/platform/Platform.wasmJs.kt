package com.solfi.app.platform

import kotlin.math.max

// The browser runs Kotlin/Wasm on a single thread (UI, coroutines and the audio callback all share the
// main thread), so the lock only needs to call the block.
actual class PlatformLock actual constructor() {
    actual fun <T> withLock(block: () -> T): T = block()
}

// ---------------------------------------------------------------------------------------------- audio

private fun webAudioCreate(sampleRate: Int, bufferSize: Int, onBuffer: (JsAny, Double) -> Unit): JsAny? = js(
    """{
    const C = window.AudioContext || window.webkitAudioContext;
    if (!C) return null;
    let ctx;
    try { ctx = new C({ sampleRate: sampleRate, latencyHint: 'interactive' }); }
    catch (e) { ctx = new C(); }
    const node = ctx.createScriptProcessor(bufferSize, 0, 1);
    node.onaudioprocess = function (e) {
        const t = (typeof e.playbackTime === 'number') ? e.playbackTime : ctx.currentTime;
        onBuffer(e.outputBuffer.getChannelData(0), t);
    };
    node.connect(ctx.destination);
    // Browsers keep the context suspended until a user gesture: resume on any interaction.
    const resume = function () { if (ctx.state !== 'running' && ctx.state !== 'closed') ctx.resume(); };
    ['pointerdown', 'pointerup', 'touchend', 'keydown', 'mousedown'].forEach(function (ev) {
        window.addEventListener(ev, resume, { capture: true, passive: true });
    });
    // iOS Safari: re-activate after the page was hidden
    document.addEventListener('visibilitychange', function () { if (!document.hidden) resume(); });
    resume();
    return { ctx: ctx, node: node, resume: resume };
}"""
)

private fun webAudioClose(handle: JsAny): Unit = js(
    """{
    try { handle.node.disconnect(); handle.node.onaudioprocess = null; } catch (e) {}
    try { handle.ctx.close(); } catch (e) {}
}"""
)

private fun webAudioResume(handle: JsAny): Unit = js("{ handle.resume(); }")
private fun webAudioSampleRate(handle: JsAny): Double = js("handle.ctx.sampleRate")
private fun webAudioTime(handle: JsAny): Double = js("handle.ctx.currentTime")
private fun webAudioOutputLatency(handle: JsAny): Double =
    js("(typeof handle.ctx.outputLatency === 'number' ? handle.ctx.outputLatency : (handle.ctx.baseLatency || 0))")
private fun f32Length(a: JsAny): Int = js("a.length")
private fun f32Set(a: JsAny, i: Int, v: Float): Unit = js("{ a[i] = v; }")

/**
 * Web Audio output. A ScriptProcessorNode pulls blocks from the shared Kotlin mixer on the main thread.
 * When the browser does not honour the requested sample rate, blocks are linearly resampled so pitch and
 * timing stay right. The audio clock is derived from the AudioContext time of each rendered block.
 */
actual class AudioSink actual constructor() {
    private var handle: JsAny? = null
    private var rate = 44100
    private var ctxRate = 44100.0
    private var rendered = 0L

    // mapping (mixer frame at block start) <-> (context time at which that block is played)
    private var blockFrame = 0L
    private var blockTime = 0.0

    // resampling state (only used when the context runs at another rate)
    private val chunk = FloatArray(128)
    private var chunkPos = chunk.size
    private var a = 0f
    private var b = 0f
    private var frac = 0.0

    private fun pull(render: (FloatArray, Int) -> Unit): Float {
        if (chunkPos >= chunk.size) {
            render(chunk, chunk.size)
            rendered += chunk.size
            chunkPos = 0
        }
        return chunk[chunkPos++]
    }

    actual fun start(sampleRate: Int, render: (FloatArray, Int) -> Unit) {
        if (handle != null) { handle?.let(::webAudioResume); return }
        rate = sampleRate
        var direct = FloatArray(BUFFER)
        val h = webAudioCreate(sampleRate, BUFFER) { out, time ->
            val n = f32Length(out)
            blockTime = time
            if (kotlin.math.abs(ctxRate - rate) < 0.5) {
                blockFrame = rendered
                if (direct.size < n) direct = FloatArray(n)
                render(direct, n)
                for (i in 0 until n) f32Set(out, i, direct[i])
                rendered += n
            } else {
                // linear interpolation through the mixer stream, rate/ctxRate source frames per output frame
                blockFrame = rendered - (chunk.size - chunkPos)
                val step = rate / ctxRate
                for (i in 0 until n) {
                    while (frac >= 1.0) { a = b; b = pull(render); frac -= 1.0 }
                    f32Set(out, i, a + (b - a) * frac.toFloat())
                    frac += step
                }
            }
        } ?: return
        handle = h
        ctxRate = webAudioSampleRate(h)
    }

    actual fun stop() {
        handle?.let(::webAudioClose)
        handle = null
    }

    actual fun playedFrames(): Long {
        val h = handle ?: return 0L
        val now = webAudioTime(h) - webAudioOutputLatency(h)
        val f = blockFrame + ((now - blockTime) * rate).toLong()
        return max(0L, minOf(f, rendered))
    }

    actual fun latencyFrames(): Int {
        val h = handle ?: return BUFFER * 2
        return BUFFER + (webAudioOutputLatency(h) * rate).toInt()
    }

    private companion object { const val BUFFER = 2048 }
}

// -------------------------------------------------------------------------------------------- haptics

private fun vibrate(pattern: JsAny): Unit = js("{ try { if (navigator.vibrate) navigator.vibrate(pattern); } catch (e) {} }")
private fun vibratePattern(success: Boolean): JsAny = js("success ? 12 : [30, 40, 30]")

actual class Haptics actual constructor() {
    actual fun feedback(success: Boolean) = vibrate(vibratePattern(success))
}

// ---------------------------------------------------------------------------------------------- store

private fun lsGet(key: String): String? = js("{ try { return window.localStorage.getItem(key); } catch (e) { return null; } }")
private fun lsSet(key: String, value: String): Unit = js("{ try { window.localStorage.setItem(key, value); } catch (e) {} }")
private fun lsRemove(key: String): Unit = js("{ try { window.localStorage.removeItem(key); } catch (e) {} }")

/** Persists in the browser's localStorage, namespaced by [name]. */
actual class KeyValueStore actual constructor(private val name: String) {
    private fun k(key: String) = "$name.$key"
    actual fun getString(key: String): String? = lsGet(k(key))
    actual fun putString(key: String, value: String) = lsSet(k(key), value)
    actual fun remove(key: String) = lsRemove(k(key))
}

// ----------------------------------------------------------------------------------------- clock/locale

private fun navigatorLanguage(): String = js("(navigator.languages && navigator.languages[0]) || navigator.language || 'es'")
private fun nowMillis(): Double = js("Date.now()")
private fun timezoneOffsetMinutes(): Double = js("new Date().getTimezoneOffset()")

actual fun systemLanguageCode(): String = navigatorLanguage().take(2).lowercase()

actual fun todayEpochDay(): Int {
    val local = nowMillis() - timezoneOffsetMinutes() * 60_000.0
    return kotlin.math.floor(local / 86_400_000.0).toInt()
}

actual fun currentTimeMillis(): Long = nowMillis().toLong()
