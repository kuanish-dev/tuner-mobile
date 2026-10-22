package com.droptuner.dsp

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Metronome audio generated sample by sample: each beat lands on the sample where it is due,
 * and the fractional remainder carries over, so the tempo never drifts however long it plays.
 * [bpm] and [beatsPerBar] may be changed from another thread; changes apply from the next beat.
 */
class ClickTrack(private val sampleRate: Int) {
    @Volatile var bpm: Int = 120
    @Volatile var beatsPerBar: Int = 4

    /** A click starting at [frame] (counted from the start of playback). */
    data class Beat(val frame: Long, val index: Int)

    private val accent = click(2_000.0, gain = 1.0)
    private val normal = click(1_300.0, gain = 0.65)
    private var untilBeat = 0.0
    private var nextIndex = 0
    private var sound: FloatArray? = null
    private var soundPos = 0
    private var written = 0L

    /** Fills [buffer] with the next stretch of audio and returns the beats that start inside it. */
    fun fill(buffer: FloatArray): List<Beat> {
        val beats = ArrayList<Beat>(1)
        for (i in buffer.indices) {
            if (untilBeat <= 0.0) {
                val index = if (nextIndex >= beatsPerBar) 0 else nextIndex
                beats += Beat(written + i, index)
                sound = if (index == 0 && beatsPerBar > 1) accent else normal
                soundPos = 0
                nextIndex = index + 1
                untilBeat += sampleRate * 60.0 / bpm
            }
            val s = sound
            buffer[i] = if (s != null && soundPos < s.size) s[soundPos++] else 0f
            untilBeat -= 1.0
        }
        written += buffer.size
        return beats
    }

    /** Short decaying sine with a little second partial: a clear "tick" even on small speakers. */
    private fun click(frequency: Double, gain: Double): FloatArray {
        val length = sampleRate * 35 / 1000
        return FloatArray(length) { i ->
            val t = i.toDouble() / sampleRate
            val tone = sin(2 * PI * frequency * t) + 0.3 * sin(2 * PI * frequency * 2.01 * t)
            (gain * 0.6 * tone * exp(-t / 0.007)).toFloat()
        }
    }
}
