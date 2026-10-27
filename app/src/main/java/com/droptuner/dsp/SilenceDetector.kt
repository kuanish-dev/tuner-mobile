package com.droptuner.dsp

import kotlin.math.abs

/**
 * Tells a dead input from a quiet one. A mic that is blocked, muted by the system or given to
 * another app delivers exact zeros; a real mic in the quietest room still has a little noise.
 */
class SilenceDetector(sampleRate: Int, seconds: Double = 1.5, private val maxAbs: Int = 1) {
    private val needed = (sampleRate * seconds).toLong()
    private var silentRun = 0L

    /** True once nothing but (near-)zero samples has arrived for the whole window. */
    val isSilent: Boolean get() = silentRun >= needed

    /** The latest block carried real signal (or real room noise). */
    val hearsSomething: Boolean get() = silentRun == 0L

    fun feed(pcm: ShortArray): Boolean {
        silentRun = if (pcm.all { abs(it.toInt()) <= maxAbs }) silentRun + pcm.size else 0
        return isSilent
    }
}
