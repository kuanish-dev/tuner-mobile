package com.droptuner.dsp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SilenceDetectorTest {
    private val sampleRate = 48_000
    private val hop = 1024

    private fun zeros() = ShortArray(hop)

    /** The quietest real mic still shows a few LSBs of noise. */
    private fun roomNoise(seed: Int) = Random(seed).let { r -> ShortArray(hop) { (r.nextInt(7) - 3).toShort() } }

    @Test
    fun `exact zeros for a second and a half mean a dead input`() {
        val detector = SilenceDetector(sampleRate)
        val hops = sampleRate * 3 / 2 / hop
        repeat(hops) { assertFalse("too early at hop $it", detector.feed(zeros())) }
        assertTrue(detector.feed(zeros()))
    }

    @Test
    fun `a quiet room is not silence`() {
        val detector = SilenceDetector(sampleRate)
        repeat(200) { assertFalse(detector.feed(roomNoise(it))) }
        assertTrue(detector.hearsSomething)
    }

    @Test
    fun `sound after silence brings the input back`() {
        val detector = SilenceDetector(sampleRate)
        repeat(100) { detector.feed(zeros()) }
        assertTrue(detector.isSilent)
        detector.feed(roomNoise(1))
        assertFalse(detector.isSilent)
        assertTrue(detector.hearsSomething)
    }
}
