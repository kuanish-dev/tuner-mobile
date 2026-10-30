package com.droptuner.dsp

import kotlin.math.abs

class SilenceDetector(sampleRate: Int, seconds: Double = 1.5, private val maxAbs: Int = 1) {
    private val needed = (sampleRate * seconds).toLong()
    private var silentRun = 0L
    val isSilent: Boolean get() = silentRun >= needed

    val hearsSomething: Boolean get() = silentRun == 0L

    fun feed(pcm: ShortArray): Boolean {
        silentRun = if (pcm.all { abs(it.toInt()) <= maxAbs }) silentRun + pcm.size else 0
        return isSilent
    }
}
