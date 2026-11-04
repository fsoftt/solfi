package com.solfi.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.solfi.app.music.Melody
import com.solfi.app.ui.notation.StaffView
import com.solfi.app.ui.theme.SolfiTheme
import com.solfi.app.ui.widgets.Mascot
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders the staff off-screen to make sure drawing works; writes build/render/staff.png for eyeballing. */
class RenderSmokeTest {
    private fun render(name: String, w: Int, h: Int, content: @androidx.compose.runtime.Composable () -> Unit): ByteArray {
        val scene = ImageComposeScene(w, h, Density(2f)) {
            SolfiTheme("LIGHT", kid = true) { Surface(color = MaterialTheme.colorScheme.background) { content() } }
        }
        val img = scene.render(100_000_000L)
        scene.close()
        val data = img.encodeToData(EncodedImageFormat.PNG)!!.bytes
        File("build/render").mkdirs()
        File("build/render/$name.png").writeBytes(data)
        return data
    }

    @Test fun staffRenders() {
        val melodies = listOf(
            Melody.parse("treble", "4/4", "C", 90, "C4:q D4:q E4:q F4:q | G4:h A4:q B4:q | C5:w | [C4 E4 G4]:h r:h"),
            Melody.parse("treble", "3/4", "G", 90, "F#4:8 G4:8 A4:8 B4:8 C5:q | D5:q. E5:8 F#5:q | G5:h. | r:q r:q r:q"),
            Melody.parse("bass", "4/4", "F", 90, "C3:q D3:q Bb2:q A2:q | G2:h C3:h~ | C3:w | E3:16 F3:16 G3:16 A3:16 B3:8 C4:8 D4:h"),
            Melody.parse("treble", "4/4", "C", 90, "C4:q C4:q G4:q G4:q | A4:q A4:q G4:h | F4:q F4:q E4:q E4:q | D4:q D4:q C4:h | E4:q. D4:8 D4:h"),
        )
        val png = render("staff", 860, 1700) {
            Column(Modifier.padding(8.dp)) {
                melodies.forEachIndexed { i, m -> StaffView(m, labelFor = { it.toString() }, highlightIndex = if (i == 0) 2 else -1, gap = 10.dp, modifier = Modifier.padding(bottom = 4.dp)) }
                Mascot(size = 90.dp)
            }
        }
        assertTrue(png.size > 5000)
    }
}
