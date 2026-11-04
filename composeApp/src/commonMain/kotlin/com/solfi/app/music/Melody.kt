package com.solfi.app.music

class MelodyParseException(message: String) : IllegalArgumentException(message)

/** Base note values. One quarter note = 24 ticks so dotted sixteenths are integral. */
enum class NoteValue(val code: String, val ticks: Int) {
    WHOLE("w", 96), HALF("h", 48), QUARTER("q", 24), EIGHTH("8", 12), SIXTEENTH("16", 6);

    companion object {
        fun fromCode(c: String): NoteValue? = entries.firstOrNull { it.code == c }
    }
}

/** A duration: note value plus 0-2 augmentation dots. */
data class Dur(val value: NoteValue, val dots: Int = 0) {
    val ticks: Int get() = when (dots) { 0 -> value.ticks; 1 -> value.ticks * 3 / 2; else -> value.ticks * 7 / 4 }
    val beats: Double get() = ticks / Melody.TICKS_PER_QUARTER.toDouble()
    val code: String get() = value.code + ".".repeat(dots)

    companion object {
        fun parse(code: String): Dur {
            val dots = code.takeLastWhile { it == '.' }.length
            val v = NoteValue.fromCode(code.dropLast(dots)) ?: throw MelodyParseException("Unknown duration '$code'")
            if (dots > 2) throw MelodyParseException("Too many dots in '$code'")
            return Dur(v, dots)
        }
    }
}

data class TimeSignature(val beats: Int, val unit: Int) {
    val measureTicks: Int get() = beats * (96 / unit)

    /** Ticks between metronome clicks. Compound meters (6/8, 9/8, 12/8) click on dotted quarters. */
    val clickTicks: Int get() = if (unit == 8 && beats % 3 == 0 && beats > 3) 36 else 96 / unit

    override fun toString() = "$beats/$unit"

    companion object {
        fun parse(text: String): TimeSignature {
            val p = text.split("/")
            val b = p.getOrNull(0)?.trim()?.toIntOrNull()
            val u = p.getOrNull(1)?.trim()?.toIntOrNull()
            if (p.size != 2 || b == null || u == null || b < 1 || u !in listOf(1, 2, 4, 8, 16)) throw MelodyParseException("Invalid time signature '$text'")
            return TimeSignature(b, u)
        }
    }
}

enum class Clef(val code: String, val bottomLineDiatonic: Int) {
    /** Bottom line E4. */
    TREBLE("treble", 4 * 7 + 2),
    /** Bottom line G2. */
    BASS("bass", 2 * 7 + 4);

    companion object {
        fun fromCode(c: String) = entries.firstOrNull { it.code == c } ?: throw MelodyParseException("Unknown clef '$c'")
    }
}

/** A note, chord (several pitches) or rest ([pitches] empty). */
data class MelodyEvent(
    val index: Int,
    val measure: Int,
    val startTick: Int,
    val dur: Dur,
    val pitches: List<Pitch>,
    val tieNext: Boolean = false,
    val tiePrev: Boolean = false,
) {
    val isRest: Boolean get() = pitches.isEmpty()
    val endTick: Int get() = startTick + dur.ticks
}

/** A sounding note after merging ties. */
data class SoundNote(val startTick: Int, val durTicks: Int, val pitches: List<Pitch>, val firstEvent: Int, val lastEvent: Int) {
    val startBeat: Double get() = startTick / Melody.TICKS_PER_QUARTER.toDouble()
    val durBeats: Double get() = durTicks / Melody.TICKS_PER_QUARTER.toDouble()
}

class Melody(
    val clef: Clef,
    val time: TimeSignature,
    val key: KeySignature,
    val tempo: Int,
    val measures: List<List<MelodyEvent>>,
) {
    val events: List<MelodyEvent> = measures.flatten()
    val totalTicks: Int = events.sumOf { it.dur.ticks }
    val totalBeats: Double get() = totalTicks / TICKS_PER_QUARTER.toDouble()

    /** Event sounding (or resting) at the given beat (quarter-note units), or null outside. */
    fun eventAtBeat(beat: Double): MelodyEvent? {
        val tick = beat * TICKS_PER_QUARTER
        if (tick < 0) return null
        return events.lastOrNull { it.startTick <= tick && tick < it.endTick }
    }

    /** Notes to play, with ties merged and rests skipped. */
    fun soundNotes(): List<SoundNote> {
        val out = ArrayList<SoundNote>()
        var i = 0
        while (i < events.size) {
            val e = events[i]
            if (e.isRest || e.tiePrev) { i++; continue }
            var dur = e.dur.ticks
            var last = i
            while (events[last].tieNext && last + 1 < events.size && events[last + 1].tiePrev) {
                last++
                dur += events[last].dur.ticks
            }
            out += SoundNote(e.startTick, dur, e.pitches, i, last)
            i = last + 1
        }
        return out
    }

    /** Onsets (in beats) of every note that starts a sound (ignores rests and tie continuations). */
    fun onsetBeats(): List<Double> = soundNotes().map { it.startBeat }

    companion object {
        const val TICKS_PER_QUARTER = 24

        fun parse(clef: String, time: String, key: String, tempo: Int, notes: String): Melody {
            val ts = TimeSignature.parse(time)
            val ks = try { KeySignature.parse(key) } catch (e: IllegalArgumentException) { throw MelodyParseException(e.message ?: "bad key") }
            val bars = MelodyParser.parseBars(notes)
            val measures = ArrayList<List<MelodyEvent>>()
            var tick = 0
            var idx = 0
            bars.forEachIndexed { m, bar ->
                val sum = bar.sumOf { it.dur.ticks }
                if (sum != ts.measureTicks) throw MelodyParseException("Measure ${m + 1} has $sum ticks, expected ${ts.measureTicks} for $ts")
                val list = bar.map { r -> MelodyEvent(idx++, m, tick, r.dur, r.pitches, r.tieNext, r.tiePrev).also { tick += r.dur.ticks } }
                measures += list
            }
            if (measures.isEmpty()) throw MelodyParseException("Empty melody")
            val ev = measures.flatten()
            ev.forEachIndexed { i, e ->
                if (e.tieNext) {
                    val n = ev.getOrNull(i + 1)
                    if (n == null || n.isRest || n.pitches != e.pitches) throw MelodyParseException("Tie at event ${i + 1} must join identical pitches")
                }
            }
            return Melody(Clef.fromCode(clef), ts, ks, tempo, measures)
        }

        /** Lenient parse for ear-training snippets: ignores measure length, single measure. */
        fun parseLoose(notes: String, clef: String = "treble", tempo: Int = 80): Melody {
            val flat = MelodyParser.parseBars(notes).flatten()
            var tick = 0
            val ev = flat.mapIndexed { i, r -> MelodyEvent(i, 0, tick, r.dur, r.pitches, r.tieNext, r.tiePrev).also { tick += r.dur.ticks } }
            return Melody(Clef.fromCode(clef), TimeSignature(maxOf(1, (tick + 23) / 24), 4), KeySignature.parse("C"), tempo, listOf(ev))
        }
    }
}

internal data class RawEvent(val dur: Dur, val pitches: List<Pitch>, var tieNext: Boolean, var tiePrev: Boolean = false)

internal object MelodyParser {
    /** Percussion-style placeholder pitch for rhythm-only notation (`x:q`). */
    private val RHYTHM_PITCH = Pitch(6, 0, 4)

    private fun tokenize(s: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c == '|' -> { out += "|"; i++ }
                c == '~' -> { out += "~"; i++ }
                c == '[' -> {
                    val end = s.indexOf(']', i)
                    if (end < 0) throw MelodyParseException("Unclosed chord bracket")
                    var j = end + 1
                    while (j < s.length && !s[j].isWhitespace() && s[j] != '|') j++
                    out += s.substring(i, j); i = j
                }
                else -> {
                    var j = i
                    while (j < s.length && !s[j].isWhitespace() && s[j] != '|') j++
                    out += s.substring(i, j); i = j
                }
            }
        }
        return out
    }

    fun parseBars(notes: String): List<List<RawEvent>> {
        val bars = ArrayList<MutableList<RawEvent>>()
        var cur = ArrayList<RawEvent>()
        var last: RawEvent? = null
        var pendingTie = false
        for (tok in tokenize(notes)) {
            if (tok == "|") { if (cur.isNotEmpty()) { bars += cur; cur = ArrayList() }; continue }
            if (tok == "~") {
                val l = last ?: throw MelodyParseException("Tie '~' without previous note")
                l.tieNext = true; pendingTie = true; continue
            }
            val colon = tok.lastIndexOf(':')
            if (colon < 0) throw MelodyParseException("Missing duration in '$tok' (use e.g. C4:q)")
            val head = tok.substring(0, colon)
            var durText = tok.substring(colon + 1)
            val tie = durText.endsWith("~")
            if (tie) durText = durText.dropLast(1)
            val dur = Dur.parse(durText)
            val pitches = when {
                head == "r" || head == "R" -> emptyList()
                head == "x" -> listOf(RHYTHM_PITCH)
                head.startsWith("[") -> {
                    if (!head.endsWith("]")) throw MelodyParseException("Bad chord '$tok'")
                    head.substring(1, head.length - 1).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map {
                        Pitch.parseOrNull(it) ?: throw MelodyParseException("Bad pitch '$it' in '$tok'")
                    }.also { if (it.isEmpty()) throw MelodyParseException("Empty chord '$tok'") }
                }
                else -> listOf(Pitch.parseOrNull(head) ?: throw MelodyParseException("Bad pitch '$head' in '$tok'"))
            }
            val ev = RawEvent(dur, pitches, tie, pendingTie)
            if (tie && pitches.isEmpty()) throw MelodyParseException("Cannot tie a rest: '$tok'")
            pendingTie = tie
            cur += ev
            last = ev
        }
        if (cur.isNotEmpty()) bars += cur
        return bars
    }
}
