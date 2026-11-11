package com.solfi.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.ComposeViewport
import com.solfi.app.resources.Res
import com.solfi.app.resources.noto_emoji
import com.solfi.app.resources.noto_music
import com.solfi.app.resources.noto_symbols
import com.solfi.app.resources.noto_symbols2
import com.solfi.app.resources.roboto_400
import com.solfi.app.resources.roboto_500
import com.solfi.app.resources.roboto_600
import com.solfi.app.resources.roboto_700
import com.solfi.app.resources.roboto_800
import com.solfi.app.ui.theme.LocalBaseFontFamily
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadFont

private fun hideSplash(): Unit = js("{ const s = document.getElementById('splash'); if (s) { s.classList.add('gone'); setTimeout(function () { s.remove(); }, 400); } }")

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport("solfi") { WebApp() }
}

/**
 * The browser has no system fonts, so the app's text font (Roboto, as on Android) is bundled and the emoji and
 * music-symbol fonts are preloaded as fallbacks before the app is shown.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
private fun WebApp() {
    val r400 by preloadFont(Res.font.roboto_400, FontWeight.Normal)
    val r500 by preloadFont(Res.font.roboto_500, FontWeight.Medium)
    val r600 by preloadFont(Res.font.roboto_600, FontWeight.SemiBold)
    val r700 by preloadFont(Res.font.roboto_700, FontWeight.Bold)
    val r800 by preloadFont(Res.font.roboto_800, FontWeight.ExtraBold)
    val emoji by preloadFont(Res.font.noto_emoji)
    val music by preloadFont(Res.font.noto_music)
    val sym by preloadFont(Res.font.noto_symbols)
    val sym2 by preloadFont(Res.font.noto_symbols2)

    val resolver = LocalFontFamilyResolver.current
    var fallbacksReady by remember { mutableStateOf(false) }
    val text = listOf(r400, r500, r600, r700, r800)
    val fallbacks = listOf(emoji, music, sym, sym2)

    if (fallbacks.all { it != null }) {
        LaunchedEffect(Unit) {
            fallbacks.forEach { resolver.preload(FontFamily(it!!)) }
            fallbacksReady = true
        }
    }
    if (text.any { it == null } || !fallbacksReady) return

    val family = remember { FontFamily(text.map { it!! }) }
    LaunchedEffect(Unit) { hideSplash() }
    CompositionLocalProvider(LocalBaseFontFamily provides family) {
        App()
    }
}
