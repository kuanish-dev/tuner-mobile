package com.droptuner.dsp

import com.droptuner.music.cents
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Turns raw frames into a steady pitch: ignores quiet frames, runs the detector, drops glitches
 * with a short median, then averages while the note holds still so the reading settles to a
 * fraction of a cent. A jump to another pitch restarts the average at once.
 */
class PitchTracker(
    private val detector: YinPitchDetector,
    private val gateRms: Double = 0.0015,
    private val medianSize: Int = 5,
    private val maxAverage: Int = 12,
    private val maxMisses: Int = 3,
    private val jumpCents: Double = 20.0,
) {
    private val history = ArrayDeque<Double>(medianSize + 1)
    private var misses = 0
    private var smoothed = 0.0
    private var averaged = 0

    /** Smoothed pitch in Hz, or null while nothing clear is sounding. */
    fun process(frame: FloatArray): Double? {
        val estimate = if (rms(frame) < gateRms) null else detector.detect(frame)
        if (estimate == null) {
            // A short gap keeps the history; a longer one means the next note starts fresh.
            if (++misses >= maxMisses) {
                history.clear()
                averaged = 0
            }
            return null
        }
        misses = 0
        history.addLast(estimate.frequency)
        if (history.size > medianSize) history.removeFirst()
        val median = history.sorted()[history.size / 2]

        if (averaged == 0 || abs(cents(median, smoothed)) > jumpCents) {
            smoothed = median
            averaged = 1
        } else {
            // Running mean in the log domain over the last `maxAverage` readings at most.
            averaged = min(averaged + 1, maxAverage)
            smoothed *= (median / smoothed).pow(1.0 / averaged)
        }
        return smoothed
    }
}

fun rms(frame: FloatArray): Double {
    var sum = 0.0
    for (v in frame) sum += v * v
    return sqrt(sum / frame.size)
}
