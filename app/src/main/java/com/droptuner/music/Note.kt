package com.droptuner.music

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/** A pitch of the equal-tempered scale, stored as a MIDI number (A4 = 69). */
@JvmInline
value class Note(val midi: Int) {
    /** Sharp spelling: C, C#, D … */
    val name: String get() = SHARP_NAMES[midi.mod(12)]

    /** "C#/Db" for accidentals, the plain letter otherwise — how tunings are usually called. */
    val nameWithFlat: String get() = FLAT_ALIASES[name]?.let { "$name/$it" } ?: name

    val octave: Int get() = midi.floorDiv(12) - 1

    val label: String get() = "$name$octave"

    fun frequency(a4: Double = A4_HZ): Double = a4 * 2.0.pow((midi - A4_MIDI) / 12.0)

    operator fun plus(semitones: Int) = Note(midi + semitones)

    override fun toString() = label

    companion object {
        const val A4_HZ = 440.0
        private const val A4_MIDI = 69
        private val SHARP_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        private val FLAT_ALIASES = mapOf("C#" to "Db", "D#" to "Eb", "F#" to "Gb", "G#" to "Ab", "A#" to "Bb")
        private val LABEL = Regex("""([A-G])([#b]?)(-?\d)""")

        /** Parses "E2", "C#3" or "Bb1". */
        fun parse(label: String): Note {
            val match = requireNotNull(LABEL.matchEntire(label)) { "Not a note: $label" }
            val (letter, accidental, octave) = match.destructured
            val semitone = SHARP_NAMES.indexOf(letter) + when (accidental) {
                "#" -> 1
                "b" -> -1
                else -> 0
            }
            return Note((octave.toInt() + 1) * 12 + semitone)
        }

        fun nearest(frequency: Double, a4: Double = A4_HZ): Note =
            Note((A4_MIDI + 12 * log2(frequency / a4)).roundToInt())
    }
}

/** Distance from [reference] to [frequency] in cents (100 per semitone, positive = sharp). */
fun cents(frequency: Double, reference: Double): Double = 1200.0 * log2(frequency / reference)
