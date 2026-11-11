package com.solfi.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

private fun historyPushGuard(): Unit = js("{ history.pushState({ solfiGuard: true }, ''); }")
private fun historyBack(): Unit = js("{ history.back(); }")
private fun onPopState(cb: () -> Unit): Unit = js("{ window.addEventListener('popstate', function () { cb(); }); }")

/**
 * Browser back button / gesture support. While any handler is enabled a "guard" entry is kept on top of the
 * session history; pressing back pops it and the most recently registered enabled handler runs (like Android's
 * BackHandler). When no handler is enabled any more, the guard is removed again.
 */
private object WebBack {
    class Entry(var enabled: Boolean, var onBack: () -> Unit)

    private val entries = ArrayList<Entry>()
    private var guard = false
    private var ignorePops = 0
    private var installed = false

    fun add(e: Entry) { install(); entries += e; sync() }
    fun remove(e: Entry) { entries -= e; sync() }

    fun sync() {
        val want = entries.any { it.enabled }
        if (want && !guard) { historyPushGuard(); guard = true }
        else if (!want && guard) { guard = false; ignorePops++; historyBack() }
    }

    private fun install() {
        if (installed) return
        installed = true
        onPopState {
            if (ignorePops > 0) { ignorePops--; return@onPopState }
            guard = false
            entries.lastOrNull { it.enabled }?.onBack?.invoke()
            sync()
        }
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val entry = remember { WebBack.Entry(enabled, onBack) }
    SideEffect {
        entry.onBack = onBack
        if (entry.enabled != enabled) { entry.enabled = enabled; WebBack.sync() }
    }
    DisposableEffect(entry) {
        WebBack.add(entry)
        onDispose { WebBack.remove(entry) }
    }
}
