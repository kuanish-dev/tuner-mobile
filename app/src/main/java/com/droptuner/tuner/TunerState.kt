package com.droptuner.tuner

import com.droptuner.music.Tuning

/** Within this many cents a string counts as tuned. */
const val IN_TUNE_CENTS = 1.0

data class TunerUiState(
    val tuning: Tuning,
    /** The user's own tuning, if they have made one. */
    val custom: Tuning?,
    val a4: Int,
    /** Strobe instead of the needle. */
    val strobe: Boolean,
    /** String chosen by hand; null = the string is detected automatically. */
    val locked: Int? = null,
    /** Latest pitch, or null while nothing is sounding. */
    val reading: TunerReading? = null,
    /** Strings confirmed in tune since the tuning was picked. */
    val tuned: Set<Int> = emptySet(),
    /** String whose reference note is playing right now. */
    val playing: Int? = null,
    val editing: Boolean = false,
    val error: String? = null,
)

interface TunerActions {
    fun selectTuning(tuning: Tuning)
    fun toggleLock(string: Int)
    fun playReference(string: Int)
    fun shiftString(string: Int, semitones: Int)
    fun toggleEditing()
    fun toggleStrobe()
    fun setA4(hz: Int)
}
