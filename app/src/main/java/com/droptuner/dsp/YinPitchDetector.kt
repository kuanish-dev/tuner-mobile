package com.droptuner.dsp

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

data class PitchEstimate(val frequency: Double, val confidence: Double)

class YinPitchDetector(
    private val sampleRate: Int,
    private val frameSize: Int,
    minFrequency: Double = 30.0,
    maxFrequency: Double = 1_200.0,
    private val threshold: Double = 0.15,
    private val maxThreshold: Double = 0.35,
    private val cleanDip: Double = CLEAN_DIP,
    private val noisyDip: Double = NOISY_DIP,
) {
    private val tauMin = max(2, (sampleRate / maxFrequency).toInt())
    private val tauMax = ceil(sampleRate / minFrequency).toInt()
    private val window = frameSize - tauMax
    private val fft = Fft(frameSize)
    private val diff = DoubleArray(tauMax + 1)

    private val cmnd = DoubleArray(tauMax + 1)
    private val energy = DoubleArray(frameSize + 1)
    private val headRe = DoubleArray(frameSize)
    private val headIm = DoubleArray(frameSize)
    private val frameRe = DoubleArray(frameSize)
    private val frameIm = DoubleArray(frameSize)

    init {
        require(window >= tauMax) { "A frame of $frameSize samples is too short for $minFrequency Hz" }
    }

    fun detect(frame: FloatArray): PitchEstimate? {
        require(frame.size >= frameSize) { "Expected $frameSize samples, got ${frame.size}" }
        difference(frame)
        cumulativeMeanNormalize()
        val limit = dipLimit() ?: return null
        val dip = firstDip(limit) ?: return null
        val period = refine(dip)
        if (!period.isFinite() || period >= tauMax - 1) return null
        return PitchEstimate(sampleRate / period, 1.0 - cmnd[dip.lowest])
    }

    private class Dip(val start: Int, val end: Int, val lowest: Int)

    private fun difference(x: FloatArray) {
        energy[0] = 0.0
        for (i in 0 until frameSize) {
            energy[i + 1] = energy[i] + x[i].toDouble() * x[i]
            frameRe[i] = x[i].toDouble()
            frameIm[i] = 0.0
            headRe[i] = if (i < window) x[i].toDouble() else 0.0
            headIm[i] = 0.0
        }
        fft.transform(headRe, headIm)
        fft.transform(frameRe, frameIm)
        for (k in 0 until frameSize) {
            val re = headRe[k] * frameRe[k] + headIm[k] * frameIm[k]
            val im = headRe[k] * frameIm[k] - headIm[k] * frameRe[k]
            frameRe[k] = re
            frameIm[k] = im
        }
        fft.transform(frameRe, frameIm, inverse = true)
        val headEnergy = energy[window]
        for (tau in 1..tauMax) {
            val shiftedEnergy = energy[tau + window] - energy[tau]
            diff[tau] = max(0.0, headEnergy + shiftedEnergy - 2 * frameRe[tau])
        }
    }

    private fun cumulativeMeanNormalize() {
        cmnd[0] = 1.0
        var running = 0.0
        for (tau in 1..tauMax) {
            running += diff[tau]
            cmnd[tau] = if (running == 0.0) 1.0 else diff[tau] * tau / running
        }
    }

    private fun dipLimit(): Double? {
        var deepest = Double.MAX_VALUE
        for (tau in tauMin until tauMax) if (cmnd[tau] < deepest) deepest = cmnd[tau]
        if (deepest >= maxThreshold) return null
        return max(threshold, deepest + NEARLY_AS_DEEP)
    }

    private fun firstDip(limit: Double): Dip? {
        var tau = tauMin
        while (tau < tauMax && cmnd[tau] >= limit) tau++
        if (tau >= tauMax) return null
        val start = tau
        var lowest = tau
        while (tau < tauMax && cmnd[tau] < limit + DIP_HYSTERESIS) {
            if (cmnd[tau] < cmnd[lowest]) lowest = tau
            tau++
        }
        return Dip(start, tau, lowest)
    }

    private fun refine(dip: Dip): Double {
        val narrow = max(2, dip.lowest / 96)
        val depth = cmnd[dip.lowest]
        if (depth < cleanDip) {
            val c = dip.lowest
            return c + parabolaVertex(c, minOf(narrow, c - 1, tauMax - c))
        }
        val noisiness = ((depth - cleanDip) / (noisyDip - cleanDip)).coerceIn(0.0, 1.0)
        val halfWidth = narrow + ((max(narrow, (dip.end - dip.start) / 2) - narrow) * noisiness).roundToInt()
        var center = dip.lowest.toDouble()
        repeat(REFINE_PASSES) {
            val c = center.roundToInt()
            val vertex = (c + parabolaVertex(c, minOf(halfWidth, c - 1, tauMax - c))).coerceIn(1.0, tauMax - 1.0)
            if (abs(vertex - center) < 0.01) return vertex
            center = vertex
        }
        return center
    }

    private fun parabolaVertex(c: Int, k: Int): Double {
        if (k < 1) return 0.0
        var sumY = 0.0
        var sumXY = 0.0
        var sumX2Y = 0.0
        var sumX2 = 0.0
        var sumX4 = 0.0
        for (x in -k..k) {
            val y = diff[c + x]
            val x2 = x.toDouble() * x
            sumY += y
            sumXY += x * y
            sumX2Y += x2 * y
            sumX2 += x2
            sumX4 += x2 * x2
        }
        val n = 2 * k + 1
        val a = (n * sumX2Y - sumX2 * sumY) / (n * sumX4 - sumX2 * sumX2)
        val b = sumXY / sumX2
        return if (a <= 0.0) 0.0 else (-b / (2 * a)).coerceIn(-k.toDouble(), k.toDouble())
    }

    private companion object {
        const val NEARLY_AS_DEEP = 0.05
        const val DIP_HYSTERESIS = 0.03
        const val CLEAN_DIP = 0.003
        const val NOISY_DIP = 0.03
        const val REFINE_PASSES = 4
    }
}
