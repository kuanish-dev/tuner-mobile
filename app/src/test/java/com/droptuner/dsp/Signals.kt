package com.droptuner.dsp

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Synthetic test signals. */
object Signals {
    fun sine(frequency: Double, sampleRate: Int, length: Int, amplitude: Double = 0.5) =
        harmonics(frequency, sampleRate, length, listOf(amplitude))

    /** Sum of harmonics: amplitudes[0] is the fundamental, [1] the 2nd harmonic, … */
    fun harmonics(frequency: Double, sampleRate: Int, length: Int, amplitudes: List<Double>) =
        FloatArray(length) { i ->
            amplitudes.withIndex().sumOf { (k, a) -> a * sin(2 * PI * frequency * (k + 1) * i / sampleRate) }.toFloat()
        }

    fun noise(length: Int, amplitude: Double, seed: Int = 1): FloatArray {
        val random = Random(seed)
        return FloatArray(length) { ((random.nextDouble() * 2 - 1) * amplitude).toFloat() }
    }

    operator fun FloatArray.plus(other: FloatArray) = FloatArray(size) { this[it] + other[it] }

    operator fun FloatArray.times(gain: Double) = FloatArray(size) { (this[it] * gain).toFloat() }

    /**
     * Karplus–Strong plucked string, skipping the attack. Returns the signal and its true
     * fundamental: each new sample averages the ones `delay` and `delay - 1` back, so the
     * period is delay − 0.5 samples (the delay line itself is a whole number of samples).
     */
    fun pluck(frequency: Double, sampleRate: Int, length: Int, seed: Int = 7): Pair<FloatArray, Double> {
        val delay = (sampleRate / frequency + 0.5).roundToInt()
        val random = Random(seed)
        val line = FloatArray(delay) { (random.nextDouble() * 2 - 1).toFloat() * 0.5f }
        val skip = sampleRate / 10
        val out = FloatArray(length)
        var pos = 0
        for (i in 0 until skip + length) {
            val current = line[pos]
            val next = line[(pos + 1) % delay]
            if (i >= skip) out[i - skip] = current
            line[pos] = 0.996f * 0.5f * (current + next)
            pos = (pos + 1) % delay
        }
        return out to sampleRate / (delay - 0.5)
    }
}
