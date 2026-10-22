package com.droptuner.dsp

import kotlin.math.ceil
import kotlin.math.max

data class PitchEstimate(val frequency: Double, val confidence: Double)

/**
 * YIN pitch detector (de Cheveigné & Kawahara, 2002) with the difference function computed by FFT.
 * Reuses its buffers between calls, so one instance must not be shared between threads.
 */
class YinPitchDetector(
    private val sampleRate: Int,
    private val frameSize: Int,
    minFrequency: Double = 30.0,
    maxFrequency: Double = 1_200.0,
    private val threshold: Double = 0.15,
) {
    private val tauMin = max(2, (sampleRate / maxFrequency).toInt())
    private val tauMax = ceil(sampleRate / minFrequency).toInt()
    private val window = frameSize - tauMax
    private val fft = Fft(frameSize)

    /** Raw difference function d(τ). */
    private val diff = DoubleArray(tauMax + 1)
    /** Cumulative mean normalized difference d'(τ). */
    private val cmnd = DoubleArray(tauMax + 1)
    private val energy = DoubleArray(frameSize + 1)
    private val headRe = DoubleArray(frameSize)
    private val headIm = DoubleArray(frameSize)
    private val frameRe = DoubleArray(frameSize)
    private val frameIm = DoubleArray(frameSize)

    init {
        require(window >= tauMax) { "A frame of $frameSize samples is too short for $minFrequency Hz" }
    }

    /** Fundamental frequency of [frame], or null if the frame isn't clearly periodic. */
    fun detect(frame: FloatArray): PitchEstimate? {
        require(frame.size >= frameSize) { "Expected $frameSize samples, got ${frame.size}" }
        difference(frame)
        cumulativeMeanNormalize()
        val tau = firstDip() ?: return null
        return PitchEstimate(sampleRate / refine(tau), 1.0 - cmnd[tau])
    }

    /**
     * d(τ) = Σ (x[j] − x[j+τ])² over j < window, expanded into two energies and a cross-correlation.
     * The correlation of the frame's head with the whole frame comes from one FFT round trip;
     * j + τ never passes the frame end, so the circular FFT needs no padding.
     */
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
        // conj(Head) · Frame, written into the frame arrays.
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

    /**
     * Lowest point of the first dip under the threshold. The whole dip is scanned rather than
     * walked downhill: on low notes it is hundreds of lags wide and noise ripples would stop a walk early.
     */
    private fun firstDip(): Int? {
        var tau = tauMin
        while (tau < tauMax && cmnd[tau] >= threshold) tau++
        if (tau >= tauMax) return null
        var best = tau
        while (tau < tauMax && cmnd[tau] < threshold) {
            if (cmnd[tau] < cmnd[best]) best = tau
            tau++
        }
        return best
    }

    /**
     * Sub-sample lag: least-squares parabola through the raw difference around the dip.
     * The raw function is symmetric about the true period (the normalized one is skewed), and the
     * fit widens with the lag, so low notes average over more points and shrug off noise.
     */
    private fun refine(tau: Int): Double {
        val k = minOf(max(2, tau / 96), tau - 1, tauMax - tau)
        var sumY = 0.0
        var sumXY = 0.0
        var sumX2Y = 0.0
        var sumX2 = 0.0
        var sumX4 = 0.0
        for (x in -k..k) {
            val y = diff[tau + x]
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
        return if (a <= 0.0) tau.toDouble() else tau + (-b / (2 * a)).coerceIn(-k.toDouble(), k.toDouble())
    }
}
