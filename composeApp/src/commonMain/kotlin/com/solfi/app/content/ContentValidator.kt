package com.melodia.app.content

import com.melodia.app.music.Clef
import com.melodia.app.music.Melody
import com.melodia.app.music.Pitch

/** Validates a level file. Returns a list of human readable problems (empty = valid). */
object ContentValidator {
    private val HEX = Regex("^#[0-9A-Fa-f]{6}$")

    fun validate(level: LevelContent): List<String> {
        val errors = ArrayList<String>()
        val where = "level${level.level}"
        fun err(msg: String) { errors += "$where: $msg" }

        fun l10n(path: String, t: L10n?) {
            if (t == null) return
            if (t.es.isBlank()) err("$path.es is blank")
            if (t.en.isBlank()) err("$path.en is blank")
            if (t.pt.isBlank()) err("$path.pt is blank")
        }
        fun melody(path: String, m: MelodySpec?) {
            if (m == null) return
            try { m.toMelody() } catch (e: Exception) { err("$path melody invalid: ${e.message}") }
            if (m.tempo !in 30..220) err("$path tempo ${m.tempo} out of 30..220")
        }
        fun pitch(path: String, p: String) { if (Pitch.parseOrNull(p) == null) err("$path invalid pitch '$p'") }
        fun options(path: String, opts: List<Option>, answer: Int) {
            if (opts.size !in 2..6) err("$path needs 2..6 options, has ${opts.size}")
            if (answer !in opts.indices) err("$path answer index $answer invalid (options=${opts.size})")
            opts.forEachIndexed { i, o ->
                if (o.text == null && o.note == null && o.melody == null) err("$path.options[$i] is empty")
                l10n("$path.options[$i].text", o.text)
                o.note?.let { pitch("$path.options[$i].note", it) }
                melody("$path.options[$i]", o.melody)
            }
        }

        if (level.level !in 1..5) err("level number must be 1..5")
        l10n("title", level.title); l10n("description", level.description)
        if (!HEX.matches(level.color)) err("color '${level.color}' is not #RRGGBB")
        if (level.lessons.size !in 6..8) err("needs 6..8 lessons, has ${level.lessons.size}")
        if (level.lessons.isNotEmpty() && !level.lessons.last().isExam) err("last lesson must have isExam=true")
        if (level.lessons.dropLast(1).any { it.isExam }) err("only the last lesson may be an exam")
        if (level.solfeos.size < 4) err("needs at least 4 solfeos, has ${level.solfeos.size}")
        l10n("badge.name", level.badge.name); l10n("badge.description", level.badge.description)
        if (level.badge.id.isBlank()) err("badge id blank")

        val ids = HashSet<String>()
        level.lessons.forEachIndexed { li, lesson ->
            val lp = "lessons[$li:${lesson.id}]"
            if (!ids.add(lesson.id)) err("$lp duplicate id")
            if (lesson.id.isBlank()) err("$lp blank id")
            l10n("$lp.title", lesson.title)
            if (lesson.steps.size !in 3..6) err("$lp needs 3..6 steps, has ${lesson.steps.size}")
            lesson.steps.forEachIndexed { si, step ->
                val p = "$lp.steps[$si]"
                when (step) {
                    is Step.Explain -> { l10n("$p.title", step.title); l10n("$p.text", step.text); melody(p, step.melody) }
                    is Step.Listen -> { l10n("$p.prompt", step.prompt); melody(p, step.melody) }
                    is Step.Quiz -> {
                        l10n("$p.question", step.question); l10n("$p.explanation", step.explanation)
                        melody(p, step.melody); options(p, step.options, step.answer)
                    }
                    is Step.IdentifyNote -> {
                        l10n("$p.prompt", step.prompt); l10n("$p.explanation", step.explanation)
                        pitch(p, step.note)
                        try { Clef.fromCode(step.clef) } catch (e: Exception) { err("$p unknown clef '${step.clef}'") }
                        step.choices?.let { c ->
                            c.forEach { pitch("$p.choices", it) }
                            if (c.size !in 2..6) err("$p choices need 2..6 entries")
                            if (step.note.let { n -> c.none { it == n } }) err("$p choices must include the answer note")
                        }
                    }
                    is Step.EarTraining -> {
                        l10n("$p.prompt", step.prompt); l10n("$p.explanation", step.explanation)
                        if (step.sound.notes.isEmpty()) err("$p sound has no notes")
                        step.sound.notes.forEach { pitch("$p.sound", it) }
                        options(p, step.options, step.answer)
                    }
                    is Step.Piano -> {
                        l10n("$p.prompt", step.prompt)
                        if (step.targets.isEmpty()) err("$p has no targets")
                        step.targets.forEach { pitch("$p.targets", it) }
                    }
                    is Step.RhythmTap -> {
                        l10n("$p.prompt", step.prompt); melody(p, step.melody)
                        if (step.toleranceMs !in 40..400) err("$p toleranceMs out of 40..400")
                        if (step.passScore !in 0.0..1.0) err("$p passScore out of 0..1")
                    }
                }
            }
            if (lesson.isExam && lesson.steps.count { it !is Step.Explain && it !is Step.Listen } < 3) err("$lp exam needs at least 3 scored steps")
        }
        val sids = HashSet<String>()
        level.solfeos.forEachIndexed { i, s ->
            val p = "solfeos[$i:${s.id}]"
            if (!sids.add(s.id)) err("$p duplicate id")
            l10n("$p.title", s.title); l10n("$p.description", s.description); melody(p, s.melody)
        }
        return errors
    }

    /** All melodies in a level (for tests). */
    fun allMelodies(level: LevelContent): List<MelodySpec> {
        val out = ArrayList<MelodySpec>()
        level.lessons.forEach { l -> l.steps.forEach { s ->
            when (s) {
                is Step.Explain -> s.melody?.let(out::add)
                is Step.Listen -> out += s.melody
                is Step.Quiz -> { s.melody?.let(out::add); s.options.forEach { o -> o.melody?.let(out::add) } }
                is Step.EarTraining -> s.options.forEach { o -> o.melody?.let(out::add) }
                is Step.RhythmTap -> out += s.melody
                else -> {}
            }
        } }
        level.solfeos.forEach { out += it.melody }
        return out
    }

    @Suppress("unused")
    private fun parses(m: MelodySpec) = runCatching { Melody.parse(m.clef, m.time, m.key, m.tempo, m.notes) }.isSuccess
}
