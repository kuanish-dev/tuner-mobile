package com.droptuner

import android.content.Intent
import com.droptuner.audio.FrameSource
import com.droptuner.audio.InputInfo
import com.droptuner.audio.InputState
import com.droptuner.audio.MicSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

fun demoSource(intent: Intent): FrameSource? {
    val hz = intent.getFloatExtra("demo_hz", 0f)
    if (hz <= 0f) return null
    return SyntheticString(
        frequency = hz.toDouble(),
        levelDb = intent.getFloatExtra("demo_db", -20f).toDouble(),
        noiseDb = intent.getFloatExtra("demo_noise_db", -90f).toDouble(),
    )
}

private class SyntheticString(
    private val frequency: Double,
    levelDb: Double,
    noiseDb: Double,
) : FrameSource {
    override val sampleRate = 48_000
    override val info: StateFlow<InputInfo> = MutableStateFlow(InputInfo("Демо-сигнал", sampleRate, InputState.OK))
    override val availableSources = emptyList<MicSource>()
    override var preferredSource: MicSource? = null
    private val harmonics = listOf(0.35, 0.6, 0.4, 0.25, 0.15, 0.1)
    private val gain = 10.0.pow(levelDb / 20) / sqrt(harmonics.sumOf { it * it } / 2)
    private val noiseAmplitude = 10.0.pow(noiseDb / 20) * sqrt(3.0)

    override fun frames(frameSize: Int, hopSize: Int): Flow<FloatArray> = flow {
        val random = Random(1)
        var start = 0L
        while (true) {
            emit(FloatArray(frameSize) { i -> (sample(start + i) + (random.nextDouble() * 2 - 1) * noiseAmplitude).toFloat() })
            start += hopSize
            delay(hopSize * 1000L / sampleRate)
        }
    }

    private fun sample(n: Long): Double {
        val t = n.toDouble() / sampleRate
        return gain * harmonics.withIndex().sumOf { (k, a) -> a * sin(2 * PI * frequency * (k + 1) * t) }
    }
}
