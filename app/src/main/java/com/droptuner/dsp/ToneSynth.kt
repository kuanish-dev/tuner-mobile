package com.droptuner.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

object ToneSynth {
    private const val MAX_HARMONICS = 16

    private const val MAX_PARTIAL_HZ = 5_000.0

    private const val ATTACK_S = 0.004

    private const val RELEASE_S = 0.08

    fun pluck(frequency: Double, sampleRate: Int, seconds: Double): FloatArray {
        val harmonics = (1..MAX_HARMONICS).filter { it * frequency < min(MAX_PARTIAL_HZ, sampleRate / 2.5) }
        val length = (seconds * sampleRate).toInt()
        val out = FloatArray(length) { i ->
            val t = i.toDouble() / sampleRate
            var sample = 0.0
            for (k in harmonics) {
                val amplitude = 1.0 / k.toDouble().pow(0.7) * exp(-t * (0.9 + 0.45 * k))
                sample += amplitude * sin(2 * PI * k * frequency * t)
            }
            val envelope = min(1.0, t / ATTACK_S) * min(1.0, (seconds - t) / RELEASE_S)
            (sample * envelope).toFloat()
        }
        val peak = out.maxOf { abs(it) }
        if (peak > 0f) for (i in out.indices) out[i] *= 0.8f / peak
        return out
    }
}
