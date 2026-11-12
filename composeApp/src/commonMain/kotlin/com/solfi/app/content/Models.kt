package com.solfi.app.content

import com.solfi.app.music.Lang
import com.solfi.app.music.Melody
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Text localized in the three supported languages. All three are mandatory and non-blank. */
@Serializable
data class L10n(val es: String, val en: String, val pt: String) {
    operator fun get(lang: Lang): String = when (lang) { Lang.ES -> es; Lang.EN -> en; Lang.PT -> pt }
    fun all() = listOf(es, en, pt)
}

/** Compact melody notation, see CONTENT.md. */
@Serializable
data class MelodySpec(
    val clef: String = "treble",
    val time: String = "4/4",
    val key: String = "C",
    val tempo: Int = 80,
    val notes: String,
) {
    fun toMelody(): Melody = Melody.parse(clef, time, key, tempo, notes)
}

/** One answer option: text, a note (shown using the note-name setting) and/or a melody to listen to. */
@Serializable
data class Option(
    val text: L10n? = null,
    val note: String? = null,
    val melody: MelodySpec? = null,
)

/** Sound for ear training: one note, an interval/sequence (played one after another) or a chord (`together`). */
@Serializable
data class SoundSpec(
    val notes: List<String>,
    val together: Boolean = false,
    val tempo: Int = 80,
)

@Serializable
sealed class Step {
    /** When true the step keeps its authored clef whatever the learner's clef preference is. */
    abstract val clefLocked: Boolean

    /** Teaching card. Optionally shows (and, with `play`, can play) a melody. */
    @Serializable @SerialName("explain")
    data class Explain(
        val title: L10n? = null,
        val text: L10n,
        val emoji: String? = null,
        val melody: MelodySpec? = null,
        val play: Boolean = false,
        override val clefLocked: Boolean = false,
    ) : Step()

    /** Play a melody while it is highlighted on the staff. */
    @Serializable @SerialName("listen")
    data class Listen(val prompt: L10n, val melody: MelodySpec, override val clefLocked: Boolean = false) : Step()

    /** Multiple choice. Options may be text, notes or melodies. `melody` is shown/played with the question. */
    @Serializable @SerialName("quiz")
    data class Quiz(
        val question: L10n,
        val options: List<Option>,
        val answer: Int,
        val explanation: L10n,
        val melody: MelodySpec? = null,
        override val clefLocked: Boolean = false,
    ) : Step()

    /** A note is drawn on the staff; the learner picks its name. Options are generated unless `choices` is given. */
    @Serializable @SerialName("identifyNote")
    data class IdentifyNote(
        val prompt: L10n? = null,
        val clef: String = "treble",
        val note: String,
        val choices: List<String>? = null,
        val explanation: L10n? = null,
        override val clefLocked: Boolean = false,
    ) : Step()

    /** Play a sound and choose the answer. */
    @Serializable @SerialName("earTraining")
    data class EarTraining(
        val prompt: L10n,
        val sound: SoundSpec,
        val options: List<Option>,
        val answer: Int,
        val explanation: L10n,
        override val clefLocked: Boolean = false,
    ) : Step()

    /** On-screen keyboard: play the `targets` notes in order. */
    @Serializable @SerialName("piano")
    data class Piano(
        val prompt: L10n,
        val targets: List<String>,
        val showStaff: Boolean = true,
        val showLabels: Boolean = true,
        val clef: String = "treble",
        override val clefLocked: Boolean = false,
    ) : Step()

    /** Count-in, then tap the rhythm of `melody` (pitches ignored). */
    @Serializable @SerialName("rhythmTap")
    data class RhythmTap(
        val prompt: L10n,
        val melody: MelodySpec,
        val toleranceMs: Int = 180,
        val countIn: Boolean = true,
        val passScore: Double = 0.6,
        override val clefLocked: Boolean = false,
    ) : Step()
}

@Serializable
data class Lesson(
    val id: String,
    val title: L10n,
    val emoji: String = "🎵",
    val isExam: Boolean = false,
    /** When true every step of the lesson keeps its authored clef (clef/staff-position lessons). */
    val clefLocked: Boolean = false,
    val steps: List<Step>,
)

@Serializable
data class Solfeo(
    val id: String,
    val title: L10n,
    val description: L10n,
    val melody: MelodySpec,
)

@Serializable
data class BadgeSpec(
    val id: String,
    val name: L10n,
    val description: L10n,
    val emoji: String = "🏅",
)

@Serializable
data class LevelContent(
    val level: Int,
    val title: L10n,
    val description: L10n,
    /** "#RRGGBB" */
    val color: String,
    val lessons: List<Lesson>,
    val solfeos: List<Solfeo>,
    val badge: BadgeSpec,
)

object ContentJson {
    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = false
        prettyPrint = true
    }

    fun decodeLevel(text: String): LevelContent = json.decodeFromString(LevelContent.serializer(), text)

    fun decodeEnsembleBook(text: String): EnsembleBook = json.decodeFromString(EnsembleBook.serializer(), text)
}
