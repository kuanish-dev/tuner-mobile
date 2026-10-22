package com.droptuner.music

enum class TuningFamily(val title: String) {
    CHROMATIC("Хроматический"),
    CUSTOM("Свой строй"),
    STANDARD("Стандартные"),
    DROP("Drop-строи"),
    ALTERNATE("Открытые и другие"),
}

/** [strings] go from the lowest (thickest) string to the highest; none means chromatic (any note). */
data class Tuning(
    val id: String,
    val name: String,
    val family: TuningFamily,
    val strings: List<Note>,
) {
    val notes: String get() = strings.joinToString(" ") { it.name }
    val isChromatic: Boolean get() = strings.isEmpty()
}

object Tunings {
    const val CUSTOM_ID = "custom"
    val CHROMATIC = Tuning("chromatic", TuningFamily.CHROMATIC.title, TuningFamily.CHROMATIC, emptyList())
    val STRING_COUNTS = listOf(6, 7, 8)

    /** Semitones above the lowest string in standard tuning. */
    private val STANDARD_INTERVALS = mapOf(
        6 to listOf(0, 5, 10, 15, 19, 24),         // E A D G B E
        7 to listOf(0, 5, 10, 15, 20, 24, 29),     // B E A D G B E
        8 to listOf(0, 5, 10, 15, 20, 25, 29, 34), // F# B E A D G B E
    )

    /** Lowest-string MIDI notes each instrument gets, from the highest tuning down. */
    private class Range(val strings: Int, val standard: IntProgression, val drop: IntProgression)

    private val RANGES = listOf(
        Range(6, standard = 40 downTo 33, drop = 38 downTo 30), // E…A Standard, Drop D…Drop F#
        Range(7, standard = 35 downTo 30, drop = 33 downTo 28), // B…F# Standard, Drop A…Drop E
        Range(8, standard = 30 downTo 26, drop = 28 downTo 25), // F#…D Standard, Drop E…Drop C#
    )

    private val ALTERNATE = listOf(
        alternate("double-drop-d", "Double Drop D", "D2 A2 D3 G3 B3 D4"),
        alternate("dadgad", "DADGAD", "D2 A2 D3 G3 A3 D4"),
        alternate("open-d", "Open D", "D2 A2 D3 F#3 A3 D4"),
        alternate("open-dm", "Open D minor", "D2 A2 D3 F3 A3 D4"),
        alternate("open-e", "Open E", "E2 B2 E3 G#3 B3 E4"),
        alternate("open-g", "Open G", "D2 G2 D3 G3 B3 D4"),
        alternate("open-a", "Open A", "E2 A2 E3 A3 C#4 E4"),
        alternate("open-c", "Open C", "C2 G2 C3 G3 C4 E4"),
    )

    val all: List<Tuning> = buildList {
        for (range in RANGES) {
            for (midi in range.standard) {
                val root = Note(midi)
                add(Tuning("std${range.strings}-$midi", "${root.nameWithFlat} Standard", TuningFamily.STANDARD, standard(root, range.strings)))
            }
            for (midi in range.drop) {
                val root = Note(midi)
                add(Tuning("drop${range.strings}-$midi", "Drop ${root.nameWithFlat}", TuningFamily.DROP, drop(root, range.strings)))
            }
        }
        addAll(ALTERNATE)
    }

    val default: Tuning = all.first()

    fun byId(id: String?): Tuning? = if (id == CHROMATIC.id) CHROMATIC else all.firstOrNull { it.id == id }

    fun custom(strings: List<Note>) = Tuning(CUSTOM_ID, TuningFamily.CUSTOM.title, TuningFamily.CUSTOM, strings)

    fun standard(lowest: Note, strings: Int): List<Note> =
        STANDARD_INTERVALS.getValue(strings).map { lowest + it }

    /** Drop X: the standard tuning a whole tone above X with only the lowest string dropped to X. */
    fun drop(lowest: Note, strings: Int): List<Note> =
        listOf(lowest) + standard(lowest + 2, strings).drop(1)

    private fun alternate(id: String, name: String, notes: String) =
        Tuning(id, name, TuningFamily.ALTERNATE, notes.split(" ").map { Note.parse(it) })
}
