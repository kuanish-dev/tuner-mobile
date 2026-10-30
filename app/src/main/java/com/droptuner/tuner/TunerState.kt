package com.droptuner.tuner

import com.droptuner.audio.MicSource
import com.droptuner.music.Tuning

const val IN_TUNE_CENTS = 1.0

data class TunerUiState(
    val tuning: Tuning,
    val custom: Tuning?,
    val a4: Int,
    val strobe: Boolean,
    val micSource: MicSource?,
    val locked: Int? = null,
    val reading: TunerReading? = null,
    val tuned: Set<Int> = emptySet(),
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

    fun setMicSource(source: MicSource?)
}
