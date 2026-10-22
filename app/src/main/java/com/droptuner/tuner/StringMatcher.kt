package com.droptuner.tuner

import com.droptuner.music.Note
import com.droptuner.music.Tuning
import com.droptuner.music.cents
import kotlin.math.abs
import kotlin.math.min

data class TunerReading(
    /** What was heard, moved by an octave if the detector jumped to the 2nd harmonic. */
    val frequency: Double,
    /** Null in chromatic mode, where the target is simply the nearest note. */
    val stringIndex: Int?,
    val target: Note,
    /** Deviation from [target]: negative = flat (tighten), positive = sharp (loosen). */
    val cents: Double,
    /** Nearest note of the chromatic scale to [frequency]. */
    val heard: Note,
)

/** Decides which string of the tuning is being played and how far off it is. */
class StringMatcher {
    private var current: Int? = null

    fun reset() {
        current = null
    }

    /** [locked] forces a string; null picks the string automatically. */
    fun match(frequency: Double, tuning: Tuning, a4: Double, locked: Int?): TunerReading {
        if (tuning.isChromatic) {
            val note = Note.nearest(frequency, a4)
            return TunerReading(frequency, null, note, cents(frequency, note.frequency(a4)), note)
        }
        val index = locked ?: pickString(frequency, tuning, a4)
        val target = tuning.strings[index]
        val folded = foldOctave(frequency, target.frequency(a4))
        return TunerReading(folded, index, target, cents(folded, target.frequency(a4)), Note.nearest(folded, a4))
    }

    private fun pickString(frequency: Double, tuning: Tuning, a4: Double): Int {
        fun cost(string: Int) = distance(frequency, tuning.strings[string].frequency(a4))
        val best = tuning.strings.indices.minBy(::cost)
        val previous = current?.takeIf { it in tuning.strings.indices }
        // Far from every string (e.g. retuning to a much lower drop) the choice is ambiguous: don't flicker.
        val keep = previous != null && cost(best) > CLEAR_MATCH && cost(previous) - cost(best) < STICKINESS
        return (if (keep) previous!! else best).also { current = it }
    }

    companion object {
        /** A string this close (in cents) wins immediately. */
        private const val CLEAR_MATCH = 100.0
        /** Otherwise the current string stays unless another one is this much closer. */
        private const val STICKINESS = 100.0
        /** Cost of assuming the detector locked onto the 2nd harmonic of a low string. */
        private const val OCTAVE_PENALTY = 150.0
        /** Readings this close to an exact octave of the target count as octave errors. */
        private const val OCTAVE_WINDOW = 70.0

        /** Cents from [frequency] to a string tuned to [target], allowing for a lost fundamental. */
        internal fun distance(frequency: Double, target: Double): Double =
            min(abs(cents(frequency, target)), abs(cents(frequency / 2, target)) + OCTAVE_PENALTY)

        internal fun foldOctave(frequency: Double, target: Double): Double {
            val c = cents(frequency, target)
            return when {
                abs(c - 1200) < OCTAVE_WINDOW -> frequency / 2
                abs(c + 1200) < OCTAVE_WINDOW -> frequency * 2
                else -> frequency
            }
        }
    }
}
