package com.melodia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.melodia.app.i18n.Strings
import com.melodia.app.i18n.EsStrings
import com.melodia.app.music.Lang
import com.melodia.app.music.NoteNaming
import com.melodia.app.music.NoteNames
import com.melodia.app.music.Pitch

/** Everything the UI needs to know about the active profile's presentation settings. */
data class UiContext(
    val strings: Strings,
    val lang: Lang,
    val naming: NoteNaming,
    val kid: Boolean,
) {
    fun noteName(p: Pitch, withOctave: Boolean = false) = NoteNames.short(p, naming, withOctave, lang)
    fun noteNameLong(p: Pitch) = NoteNames.long(p, naming, lang)
}

val LocalUi = staticCompositionLocalOf { UiContext(EsStrings, Lang.ES, NoteNaming.SOLFEGE, true) }

object Palette {
    val Violet = Color(0xFF6C5CE7)
    val Teal = Color(0xFF00B8A9)
    val Coral = Color(0xFFFF7675)
    val Sun = Color(0xFFFDCB6E)
    val Sky = Color(0xFF4FA3FF)
    val Mint = Color(0xFF55D6A0)
    val Pink = Color(0xFFFF8FD0)
    val Success = Color(0xFF2EBD6B)
    val SuccessDark = Color(0xFF1F8F4F)
    val Danger = Color(0xFFEF5350)
    val Gold = Color(0xFFFFB400)
}

private val Light = lightColorScheme(
    primary = Color(0xFF6C5CE7), onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E3FF), onPrimaryContainer = Color(0xFF2A1F8A),
    secondary = Color(0xFF00A99B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFF5F0), onSecondaryContainer = Color(0xFF00403B),
    tertiary = Color(0xFFFF7675), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE1E0), onTertiaryContainer = Color(0xFF5A1313),
    background = Color(0xFFFAF9FF), onBackground = Color(0xFF1D1A2B),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1D1A2B),
    surfaceVariant = Color(0xFFEFEDFA), onSurfaceVariant = Color(0xFF5A5670),
    outline = Color(0xFFB9B5CF), outlineVariant = Color(0xFFDDDAEC),
    surfaceContainer = Color(0xFFF3F1FC), surfaceContainerHigh = Color(0xFFEDEAF9),
    error = Color(0xFFEF5350),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB4A9FF), onPrimary = Color(0xFF241A7A),
    primaryContainer = Color(0xFF3A2FA8), onPrimaryContainer = Color(0xFFE7E3FF),
    secondary = Color(0xFF4DDCCD), onSecondary = Color(0xFF00332F),
    secondaryContainer = Color(0xFF00504A), onSecondaryContainer = Color(0xFFCFF5F0),
    tertiary = Color(0xFFFF9A99), onTertiary = Color(0xFF4A0E0E),
    tertiaryContainer = Color(0xFF7A2323), onTertiaryContainer = Color(0xFFFFE1E0),
    background = Color(0xFF12101C), onBackground = Color(0xFFEAE7F8),
    surface = Color(0xFF1B1829), onSurface = Color(0xFFEAE7F8),
    surfaceVariant = Color(0xFF2A2640), onSurfaceVariant = Color(0xFFC1BDD8),
    outline = Color(0xFF7D7898), outlineVariant = Color(0xFF3B3756),
    surfaceContainer = Color(0xFF211E33), surfaceContainerHigh = Color(0xFF2A2640),
    error = Color(0xFFFF8A80),
)

@Composable
fun MelodiaTheme(themeSetting: String, kid: Boolean, content: @Composable () -> Unit) {
    val dark = when (themeSetting) { "DARK" -> true; "LIGHT" -> false; else -> isSystemInDarkTheme() }
    val r = if (kid) 28.dp else 14.dp
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(r / 3), small = RoundedCornerShape(r / 2), medium = RoundedCornerShape(r),
        large = RoundedCornerShape(r * 1.2f), extraLarge = RoundedCornerShape(r * 1.5f),
    )
    MaterialTheme(colorScheme = if (dark) Dark else Light, shapes = shapes, content = content)
}

/** Parses "#RRGGBB". */
fun parseHexColor(hex: String): Color {
    val v = hex.trim().removePrefix("#").toLongOrNull(16) ?: 0x6C5CE7
    return Color(0xFF000000 or v)
}
