package com.solfi.app

import com.solfi.app.audio.AudioEngine
import com.solfi.app.audio.InstrumentListener
import com.solfi.app.data.ProfileStore
import com.solfi.app.platform.Haptics
import com.solfi.app.platform.KeyValueStore
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** "Accept any octave" is on by default, also for profiles saved before it was. */
class OctaveSettingTest {
    private val kv = KeyValueStore("solfi-octave-test")

    @AfterTest fun cleanup() { kv.remove("profiles"); kv.remove("currentProfile") }

    private fun app() = AppState(ProfileStore(kv), AudioEngine(null), Haptics(), "es", InstrumentListener(null))

    @Test fun onByDefaultAndForOldSavedProfiles() {
        // a profile saved by the previous version, which wrote ignoreOctave = false explicitly
        kv.putString("profiles", """[{"id":"p1","name":"A","avatar":"🦊","settings":{"instrument":"TRUMPET","ignoreOctave":false}}]""")
        kv.putString("currentProfile", "p1")
        val a = app()
        assertTrue(a.octaveFree, "old profile")
        a.updateSettings { copy(strictOctave = true) }
        assertFalse(app().octaveFree, "exact octave once the learner turns it off")
        app().updateSettings { copy(instrument = "VOICE") }
        assertTrue(app().octaveFree, "voice always accepts any octave")
    }

    @Test fun newProfilesAcceptAnyOctave() {
        val a = app()
        a.createProfile("B", "🐼", kid = false, lang = com.solfi.app.music.Lang.ES, naming = com.solfi.app.music.NoteNaming.SOLFEGE, instrument = com.solfi.app.music.Instrument.TRUMPET)
        assertTrue(a.octaveFree)
        a.profiles.forEach { a.deleteProfile(it.id) }
    }
}
