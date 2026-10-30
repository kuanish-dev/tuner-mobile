package com.droptuner.tuner

import com.droptuner.music.Note
import com.droptuner.music.Tuning
import com.droptuner.music.cents
import kotlin.math.abs
import kotlin.math.min

data class TunerReading(
    val frequency: Double,
    val stringIndex: Int?,
    val target: Note,
    val cents: Double,
    val heard: Note,
)

class StringMatcher {
    private var current: Int? = null

    fun reset() {
        current = null
    }

    fun match(frequency: Double, tuning: Tuning, a4: Double, locked: Int?): TunerReading? {
        if (tuning.isChromatic) {
            val note = Note.nearest(frequency, a4)
            return TunerReading(frequency, null, note, cents(frequency, note.frequency(a4)), note)
        }
        if (frequency < tuning.strings.minOf { it.frequency(a4) } / 2) return null
        val index = locked ?: pickString(frequency, tuning, a4)
        val target = tuning.strings[index]
        val folded = foldOctave(frequency, target.frequency(a4))
        return TunerReading(folded, index, target, cents(folded, target.frequency(a4)), Note.nearest(folded, a4))
    }

    private fun pickString(frequency: Double, tuning: Tuning, a4: Double): Int {
        fun cost(string: Int) = distance(frequency, tuning.strings[string].frequency(a4))
        val best = tuning.strings.indices.minBy(::cost)
        val previous = current?.takeIf { it in tuning.strings.indices }
        val keep = previous != null && cost(best) > CLEAR_MATCH && cost(previous) - cost(best) < STICKINESS
        return (if (keep) previous!! else best).also { current = it }
    }

    companion object {
        private const val CLEAR_MATCH = 100.0
        private const val STICKINESS = 100.0
        private const val OCTAVE_PENALTY = 150.0
        private const val OCTAVE_WINDOW = 70.0
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
