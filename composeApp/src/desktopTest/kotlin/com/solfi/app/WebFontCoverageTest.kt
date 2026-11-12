package com.solfi.app

import java.awt.Font
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The browser build has no system fonts: every character the app can show must be in one of the bundled (subset)
 * fonts in wasmJsMain/composeResources/font, otherwise it renders as an empty box on the web.
 */
class WebFontCoverageTest {
    private val ignored = setOf(0xFE0F, 0xFE0E, 0x200D)

    @Test fun everyCharacterShownByTheAppIsInTheWebFonts() {
        val fonts = File("src/wasmJsMain/composeResources/font").listFiles { f -> f.extension == "ttf" }!!
            .map { Font.createFont(Font.TRUETYPE_FONT, it) }
        val sources = File("src/commonMain/kotlin").walk().filter { it.extension == "kt" } +
            File("src/commonMain/composeResources/files/content").walk().filter { it.extension == "json" }
        val missing = sortedMapOf<String, MutableList<String>>()
        for (f in sources) {
            f.readLines().forEachIndexed { i, line ->
                val t = line.trim()
                if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) return@forEachIndexed
                var k = 0
                while (k < line.length) {
                    val cp = line.codePointAt(k)
                    k += Character.charCount(cp)
                    if (cp <= 0x7F || cp in ignored) continue
                    if (fonts.none { it.canDisplay(cp) }) missing.getOrPut("U+%04X %s".format(cp, String(Character.toChars(cp)))) { mutableListOf() } += "${f.name}:${i + 1}"
                }
            }
        }
        assertTrue(missing.isEmpty(), "Characters missing from the web fonts: " + missing.entries.joinToString("; ") { "${it.key} at ${it.value.take(3)}" })
    }
}
