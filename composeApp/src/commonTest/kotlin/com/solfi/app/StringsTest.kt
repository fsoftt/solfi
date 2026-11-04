package com.solfi.app

import com.solfi.app.i18n.*
import com.solfi.app.music.Lang
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StringsTest {
    private val all = Lang.entries.map { it to stringsFor(it) }

    @Test fun allLanguagesPresent() {
        assertEquals(3, all.size)
        assertTrue(stringsFor(Lang.ES) === EsStrings && stringsFor(Lang.EN) === EnStrings && stringsFor(Lang.PT) === PtStrings)
    }

    @Test fun kidAndAdultVariantsAreNonEmptyAndDiffer() {
        for ((lang, s) in all) {
            for (kid in listOf(true, false)) {
                val variants = listOf(s.greeting("Ana", kid), s.correct(kid), s.wrong(kid), s.lessonComplete(kid),
                    s.examPassed(kid), s.examFailed(kid), s.dailyMotivation(kid))
                variants.forEach { assertTrue(it.isNotBlank(), "$lang kid=$kid blank variant") }
            }
            assertNotEquals(s.correct(true), s.correct(false), "$lang correct() should differ by tone")
            assertNotEquals(s.wrong(true), s.wrong(false))
            assertNotEquals(s.lessonComplete(true), s.lessonComplete(false))
        }
    }

    @Test fun parameterizedStringsContainArguments() {
        for ((_, s) in all) {
            assertTrue(s.greeting("Lucía", true).contains("Lucía"))
            assertTrue(s.levelLabel(3).contains("3"))
            assertTrue(s.xpGained(40).contains("40"))
            assertTrue(s.deleteProfileConfirm("Leo").contains("Leo"))
            assertTrue(s.stepOf(2, 5).contains("2") && s.stepOf(2, 5).contains("5"))
        }
    }

    @Test fun languageCodes() {
        assertEquals(Lang.ES, Lang.fromCode(null))
        assertEquals(Lang.EN, Lang.fromCode("en"))
        assertEquals(Lang.PT, Lang.fromCode("pt-BR"))
        assertEquals(Lang.ES, Lang.fromCode("fr"))
    }

    @Test fun everyInstrumentHasIconAndLocalizedName() {
        for (i in com.solfi.app.music.Instrument.entries) {
            assertTrue(com.solfi.app.ui.icons.instrumentIcon(i).root.iterator().hasNext(), "icon $i")
            val names = all.map { (_, s) -> s.instrumentName(i) }
            names.forEach { assertTrue(it.isNotBlank(), "name $i") }
        }
        assertEquals("Trompeta", EsStrings.instrumentName(com.solfi.app.music.Instrument.TRUMPET))
        assertEquals("Saxophone", EnStrings.instrumentName(com.solfi.app.music.Instrument.SAXOPHONE))
        assertEquals("Saxofone", PtStrings.instrumentName(com.solfi.app.music.Instrument.SAXOPHONE))
    }
}
