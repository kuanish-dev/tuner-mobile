package com.droptuner

import android.content.Intent
import com.droptuner.audio.FrameSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Debug builds only: a synthetic string instead of the mic, for screenshots and end-to-end checks
 * without a guitar (the emulator has no usable microphone).
 *
 *     adb shell am start -n com.droptuner/.MainActivity --ef demo_hz 65.25
 */
fun demoSource(intent: Intent): FrameSource? {
    val hz = intent.getFloatExtra("demo_hz", 0f)
    return if (hz > 0f) SyntheticString(hz.toDouble()) else null
}

/** A steady string-like tone (weak fundamental, strong harmonics, a little room noise) in real time. */
private class SyntheticString(private val frequency: Double) : FrameSource {
    override val sampleRate = 48_000
    private val harmonics = listOf(0.35, 0.6, 0.4, 0.25, 0.15, 0.1)

    override fun frames(frameSize: Int, hopSize: Int): Flow<FloatArray> = flow {
        val random = Random(1)
        var start = 0L
        while (true) {
            emit(FloatArray(frameSize) { i -> sample(start + i) + (random.nextFloat() - 0.5f) * 0.01f })
            start += hopSize
            delay(hopSize * 1000L / sampleRate)
        }
    }

    private fun sample(n: Long): Float {
        val t = n.toDouble() / sampleRate
        return (0.3 * harmonics.withIndex().sumOf { (k, a) -> a * sin(2 * PI * frequency * (k + 1) * t) }).toFloat()
    }
}
