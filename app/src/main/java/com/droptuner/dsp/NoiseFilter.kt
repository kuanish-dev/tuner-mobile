package com.droptuner.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class NoiseFilter(
    sampleRate: Int,
    private val frameSize: Int,
    private val hopSize: Int,
    highPassHz: Double = 20.0,
    lowPassHz: Double = 2_500.0,
) {
    private val stages = listOf(
        Biquad.highPass(sampleRate, highPassHz, BUTTERWORTH_4TH_Q[0]),
        Biquad.highPass(sampleRate, highPassHz, BUTTERWORTH_4TH_Q[1]),
        Biquad.lowPass(sampleRate, lowPassHz, BUTTERWORTH_4TH_Q[0]),
        Biquad.lowPass(sampleRate, lowPassHz, BUTTERWORTH_4TH_Q[1]),
    )
    private val clean = FloatArray(frameSize)
    private var primed = false

    fun clean(frame: FloatArray): FloatArray {
        require(frame.size == frameSize) { "Expected $frameSize samples, got ${frame.size}" }
        val fresh = if (primed) hopSize else frameSize
        primed = true
        System.arraycopy(clean, fresh, clean, 0, frameSize - fresh)
        for (i in frameSize - fresh until frameSize) {
            var v = frame[i].toDouble()
            for (stage in stages) v = stage.process(v)
            clean[i] = v.toFloat()
        }
        return clean
    }

    private companion object {
        val BUTTERWORTH_4TH_Q = doubleArrayOf(0.5412, 1.3066)
    }
}

class Biquad(private val b0: Double, private val b1: Double, private val b2: Double, private val a1: Double, private val a2: Double) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    companion object {
        fun lowPass(sampleRate: Int, hz: Double, q: Double): Biquad {
            val w = 2 * PI * hz / sampleRate
            val alpha = sin(w) / (2 * q)
            val a0 = 1 + alpha
            return Biquad((1 - cos(w)) / 2 / a0, (1 - cos(w)) / a0, (1 - cos(w)) / 2 / a0, -2 * cos(w) / a0, (1 - alpha) / a0)
        }
        fun highPass(sampleRate: Int, hz: Double, q: Double): Biquad {
            val w = 2 * PI * hz / sampleRate
            val alpha = sin(w) / (2 * q)
            val a0 = 1 + alpha
            return Biquad((1 + cos(w)) / 2 / a0, -(1 + cos(w)) / a0, (1 + cos(w)) / 2 / a0, -2 * cos(w) / a0, (1 - alpha) / a0)
        }
    }
}
