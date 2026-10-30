package com.droptuner.dsp

import com.droptuner.music.cents
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

const val INPUT_GATE_DB = -80.0

class PitchTracker(
    private val detector: YinPitchDetector,
    private val floorGateDb: Double = INPUT_GATE_DB,
    private val roomMarginDb: Double = 6.0,
    private val roomRiseDb: Double = 0.1,
    private val medianSize: Int = 5,
    private val maxAverage: Int = 12,
    private val maxMisses: Int = 3,
    private val jumpCents: Double = 20.0,
    private val agreeFrames: Int = 3,
    private val agreeCents: Double = 35.0,
) {
    private val history = ArrayDeque<Double>(medianSize + 1)
    private val recent = ArrayDeque<Double?>(agreeFrames + 1)
    private var misses = 0
    private var smoothed = 0.0
    private var averaged = 0
    private var roomDb = Double.POSITIVE_INFINITY
    val gateDb: Double get() = if (roomDb.isFinite()) max(floorGateDb, roomDb + roomMarginDb) else floorGateDb

    fun process(frame: FloatArray): Double? {
        val level = levelDb(frame)
        val estimate = if (level < gateDb) null else detector.detect(frame)
        recent.addLast(estimate?.frequency)
        if (recent.size > agreeFrames) recent.removeFirst()
        if (estimate == null) {
            if (++misses >= maxMisses) {
                history.clear()
                averaged = 0
            }
            if (history.isEmpty()) roomDb = if (level < roomDb) level else min(level, roomDb + roomRiseDb)
            return null
        }
        misses = 0
        history.addLast(estimate.frequency)
        if (history.size > medianSize) history.removeFirst()
        if (!recentAgree()) return null
        val median = history.sorted()[history.size / 2]
        if (averaged == 0 || abs(cents(median, smoothed)) > jumpCents) {
            smoothed = median
            averaged = 1
        } else {
            averaged = min(averaged + 1, maxAverage)
            smoothed *= (median / smoothed).pow(1.0 / averaged)
        }
        return smoothed
    }

    private fun recentAgree(): Boolean {
        if (recent.size < agreeFrames || recent.any { it == null }) return false
        val pitches = recent.filterNotNull()
        return cents(pitches.max(), pitches.min()) <= agreeCents
    }
}

fun levelDb(frame: FloatArray): Double = 20 * log10(max(rms(frame), 1e-7))

fun rms(frame: FloatArray): Double {
    var sum = 0.0
    for (v in frame) sum += v * v
    return sqrt(sum / frame.size)
}
