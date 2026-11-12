package com.solfi.app.ui.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.solfi.app.music.Instrument

/**
 * Flat 2-tone instrument icons drawn as vectors (Unicode has no emoji for bassoon, trombone, tuba or cello).
 * Body = brand violet, accent = gold; both read on light and dark surfaces.
 */
private val Body = Color(0xFF7F6FF0)
private val Accent = Color(0xFFFFB400)

private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 48.dp, 48.dp, 48f, 48f).apply(block).build()

private fun ImageVector.Builder.fill(d: String, c: Color) {
    addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(c))
}

private fun ImageVector.Builder.line(d: String, c: Color, w: Float) {
    addPath(
        PathParser().parsePathString(d).toNodes(), fill = null, stroke = SolidColor(c), strokeLineWidth = w,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    )
}

private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} $cy a$r $r 0 1 0 ${2 * r} 0 a$r $r 0 1 0 ${-2 * r} 0 Z"

private fun rr(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r} $y h${w - 2 * r} a$r $r 0 0 1 $r $r v${h - 2 * r} a$r $r 0 0 1 ${-r} $r h${-(w - 2 * r)} a$r $r 0 0 1 ${-r} ${-r} v${-(h - 2 * r)} a$r $r 0 0 1 $r ${-r} Z"

private fun build(i: Instrument): ImageVector = when (i) {
    Instrument.VOICE -> icon("voice") {
        fill(rr(17f, 4f, 14f, 24f, 7f), Body)
        line("M21 12 H27 M21 17 H27 M21 22 H27", Accent, 2f)
        line("M11 22 a13 13 0 0 0 26 0", Accent, 3f)
        line("M24 35 V43 M17 43 H31", Accent, 3f)
    }
    Instrument.PIANO -> icon("piano") {
        fill(rr(4f, 10f, 40f, 28f, 5f), Body)
        fill(rr(8f, 14f, 32f, 20f, 2f), Accent)
        line("M13.3 26 V34 M18.3 26 V34 M22 14 V34 M26.3 26 V34 M31.3 26 V34 M36.3 26 V34", Body, 1.4f)
        for (x in listOf(11.5f, 16.5f, 24.5f, 29.5f, 34.5f)) fill(rr(x, 14f, 3.6f, 12f, 1f), Body)
    }
    Instrument.GUITAR -> icon("guitar") {
        group(rotate = 38f, pivotX = 24f, pivotY = 24f, scaleX = 1.02f, scaleY = 1.02f) {
            fill(rr(21.5f, 6f, 5f, 26f, 1.5f), Accent)
            fill(rr(20.5f, 0.5f, 7f, 8f, 2.5f), Body)
            fill(circle(24f, 32f, 7.5f) + circle(24f, 40f, 9f), Body)
            line("M24 33 m-3.6 0 a3.6 3.6 0 1 0 7.2 0 a3.6 3.6 0 1 0 -7.2 0", Accent, 2f)
            line("M19.5 44 H28.5", Accent, 2.4f)
        }
    }
    Instrument.VIOLIN -> icon("violin") {
        group(rotate = 30f, pivotX = 24f, pivotY = 26f, scaleX = 1.0f, scaleY = 1.0f) {
            fill(rr(22.4f, 8f, 3.2f, 14f, 1f), Accent)
            fill(circle(24f, 5.5f, 3.4f), Accent)
            fill("M24 17 C31 17 33 22 31 25.5 C30 27.5 31 29.5 33 32.5 C36.5 38.5 31 46 24 46 C17 46 11.5 38.5 15 32.5 C17 29.5 18 27.5 17 25.5 C15 22 17 17 24 17 Z", Body)
            line("M20 28 q-1.2 3.5 0 7 M28 28 q1.2 3.5 0 7", Accent, 1.8f)
            line("M21.5 38 H26.5", Accent, 2f)
        }
    }
    Instrument.CELLO -> icon("cello") {
        fill(rr(22.3f, 3f, 3.4f, 14f, 1f), Accent)
        fill(circle(24f, 2.6f, 2.6f), Accent)
        fill("M24 13 C33.5 13 36 19 32.5 23.5 C30.5 26 32 28 34.5 31 C39.5 37.5 33 43.5 24 43.5 C15 43.5 8.5 37.5 13.5 31 C16 28 17.5 26 15.5 23.5 C12 19 14.5 13 24 13 Z", Body)
        line("M19 25 q-1.5 4 0 8 M29 25 q1.5 4 0 8", Accent, 2f)
        line("M20.5 36 H27.5", Accent, 2.2f)
        line("M24 43.5 V47", Accent, 2.6f)
    }
    Instrument.BASS -> icon("bass") {
        fill(rr(22.5f, 8f, 3f, 14f, 1f), Accent)
        fill(rr(21f, 1f, 6f, 9f, 3f), Accent)
        fill("M20 17 H28 L35 25 C34 28 32 29 33 31 C38.5 37 35 45 24 45 C13 45 9.5 37 15 31 C16 29 14 28 13 25 Z", Body)
        line("M19.5 30 q-1 3.5 0 7 M28.5 30 q1 3.5 0 7", Accent, 1.8f)
        line("M21 39 H27", Accent, 2f)
        line("M24 45 V47.5", Accent, 2f)
    }
    Instrument.FLUTE -> icon("flute") {
        group(rotate = -28f, pivotX = 24f, pivotY = 24f) {
            fill(rr(2f, 19.5f, 44f, 9f, 4.5f), Body)
            fill(rr(8f, 15.5f, 8f, 6f, 3f), Accent)
            fill(rr(41f, 19.5f, 5f, 9f, 2.5f), Accent)
            fill(circle(20f, 24f, 2.3f) + circle(26f, 24f, 2.3f) + circle(32f, 24f, 2.3f) + circle(38f, 24f, 2.3f), Accent)
        }
    }
    Instrument.BASSOON -> icon("bassoon") {
        fill(rr(12f, 13f, 8f, 29f, 3f), Body)
        fill(rr(26f, 8f, 7f, 30f, 3f), Body)
        fill(rr(12f, 34f, 21f, 10f, 5f), Body)
        fill(circle(39f, 5f, 2.2f), Accent)
        line("M27 12 V6 C27 2 33 1.5 38 4.5", Accent, 2.4f)
        fill(circle(16f, 19f, 1.7f) + circle(16f, 25f, 1.7f) + circle(16f, 31f, 1.7f), Accent)
        fill(circle(29.5f, 16f, 1.7f) + circle(29.5f, 22f, 1.7f) + circle(29.5f, 28f, 1.7f), Accent)
    }
    Instrument.TROMBONE -> icon("trombone") {
        line("M9 16 H35 M9 26 H35", Body, 3.4f)
        line("M9 16 C2 16 2 26 9 26", Body, 3.4f)
        line("M16 17 V25", Accent, 2.2f)
        fill("M33 14 L44 7 Q46.5 6 46.5 8.5 V33.5 Q46.5 36 44 35 L33 28 Z", Accent)
        line("M3 12 H8", Accent, 3f)
    }
    Instrument.TUBA -> icon("tuba") {
        fill("M19 20 H29 L35 5 H13 Z", Body)
        line("M10.5 4.5 H37.5", Accent, 3f)
        line("M24 34 m-11 0 a11 11 0 1 0 22 0 a11 11 0 1 0 -22 0", Body, 5f)
        line("M20 29 V39 M24 29 V39 M28 29 V39", Accent, 3f)
        line("M35 32 C40 28 40 20 40 14", Body, 3f)
        line("M37.5 11 H43 ", Accent, 3f)
    }
    Instrument.TRUMPET -> icon("trumpet") {
        line("M10 22 H37", Body, 3.4f)
        line("M13 22 V30 a5 5 0 0 0 5 5 H30 a5 5 0 0 0 5 -5 V22", Body, 3.4f)
        fill("M34 19 L44 10 Q46.5 9 46.5 11.5 V32.5 Q46.5 35 44 34 L34 25 Z", Accent)
        line("M3 22 H9", Accent, 3f)
        line("M18 15 V22 M23 15 V22 M28 15 V22", Accent, 2.4f)
        fill(circle(18f, 13f, 2.3f) + circle(23f, 13f, 2.3f) + circle(28f, 13f, 2.3f), Body)
    }
    Instrument.SAXOPHONE -> icon("saxophone") {
        line("M9 5 L17 8", Accent, 3.2f)
        line("M17 8 C24 8 24 11 24 15 V34 C24 43 38 43 38 33", Body, 6.5f)
        fill("M34.5 33 L41.5 33 L47 23 H29 Z", Accent)
        fill(circle(24f, 17f, 1.8f) + circle(24f, 23f, 1.8f) + circle(24f, 29f, 1.8f), Accent)
    }
    Instrument.TENOR_SAX -> icon("tenor_sax") {
        line("M7 3 L13 7 C18 9 19 9 19 15", Accent, 3.2f)
        line("M19 13 V33 C19 44 37 44 37 32", Body, 7.5f)
        fill("M33 32 L41.5 32 L47.5 20 H27 Z", Accent)
        fill(circle(19f, 17f, 2f) + circle(19f, 23.5f, 2f) + circle(19f, 30f, 2f), Accent)
    }
    Instrument.CLARINET -> icon("clarinet") {
        group(rotate = 32f, pivotX = 24f, pivotY = 24f) {
            fill(rr(21.5f, 1f, 5f, 6f, 1.5f), Accent)
            fill(rr(20.5f, 6f, 7f, 32f, 2.5f), Body)
            fill("M20.5 36 H27.5 L32 46.5 H16 Z", Body)
            line("M20.5 16 H27.5 M20.5 27 H27.5", Accent, 1.6f)
            fill(circle(24f, 12f, 1.6f) + circle(24f, 21.5f, 1.6f) + circle(24f, 32f, 1.6f), Accent)
        }
    }
    Instrument.FRENCH_HORN -> icon("french_horn") {
        line("M22 24 m-13 0 a13 13 0 1 0 26 0 a13 13 0 1 0 -26 0", Body, 4.5f)
        line("M22 24 m-6.5 0 a6.5 6.5 0 1 0 13 0 a6.5 6.5 0 1 0 -13 0", Body, 3f)
        fill("M31 31 L42 39 Q46.5 41 46 37 L40 28 Z", Accent)
        line("M9 24 C4 24 3 19 5 15", Accent, 2.6f)
        fill(circle(22f, 12f, 2.2f) + circle(27f, 13.5f, 2.2f), Accent)
    }
    Instrument.OTHER -> icon("music") {
        fill("M19 10 L38 5 V13 L19 18 Z", Accent)
        line("M18.5 35 V11 M37.5 31 V7", Body, 3.2f)
        fill(circle(14f, 36f, 5.5f) + circle(33f, 32f, 5.5f), Body)
    }
}

private val cache = HashMap<Instrument, ImageVector>()

/** Vector icon for [i] (cached). */
fun instrumentIcon(i: Instrument): ImageVector = cache.getOrPut(i) { build(i) }

@Composable
fun InstrumentIcon(i: Instrument, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    Image(instrumentIcon(i), contentDescription = null, modifier = modifier.size(size))
}
