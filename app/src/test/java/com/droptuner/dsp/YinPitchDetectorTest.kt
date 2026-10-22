package com.droptuner.dsp

import com.droptuner.dsp.Signals.plus
import com.droptuner.music.Note
import com.droptuner.music.cents
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class YinPitchDetectorTest {
    private val sampleRate = 48_000
    private val frameSize = 4096
    private val yin = YinPitchDetector(sampleRate, frameSize)

    /** Lowest string of an 8-string Drop C# up to the 12th fret of the high E. */
    private val range = listOf("C#1", "E1", "A1", "B1", "D2", "E2", "A2", "D3", "G3", "B3", "E4", "E5").map { Note.parse(it).frequency() }

    @Test
    fun `pure tones across the guitar range`() {
        for (f in range) assertCents(f, detect(Signals.sine(f, sampleRate, frameSize)), 1.0)
    }

    @Test
    fun `weak fundamental does not jump an octave up`() {
        // Phone mics barely pick up a low string's fundamental; the harmonics carry it.
        for (name in listOf("C#1", "F#1", "A1", "D2", "E2")) {
            val f = Note.parse(name).frequency()
            val signal = Signals.harmonics(f, sampleRate, frameSize, listOf(0.1, 0.6, 0.35, 0.2, 0.1))
            assertCents(f, detect(signal), 2.0)
        }
    }

    @Test
    fun `plucked strings`() {
        for (name in listOf("C#1", "E1", "A1", "B1", "D2", "E2", "A2", "G3", "E4")) {
            val (signal, f) = Signals.pluck(Note.parse(name).frequency(), sampleRate, frameSize)
            assertCents(f, detect(signal), 3.0)
        }
    }

    @Test
    fun `tone in noise`() {
        // ~21 dB SNR, a noisy room; single frame, before the tracker's median smoothing.
        for (f in range) {
            for (seed in 1..5) {
                val signal = Signals.sine(f, sampleRate, frameSize, 0.3) + Signals.noise(frameSize, 0.03, seed)
                assertCents(f, detect(signal), 3.0)
            }
        }
    }

    @Test
    fun `noise and silence have no pitch`() {
        assertNull(yin.detect(Signals.noise(frameSize, 0.3)))
        assertNull(yin.detect(FloatArray(frameSize)))
    }

    private fun detect(signal: FloatArray): Double = requireNotNull(yin.detect(signal)) { "no pitch found" }.frequency

    private fun assertCents(expected: Double, actual: Double, tolerance: Double) {
        val off = cents(actual, expected)
        assertTrue("expected %.2f Hz, got %.2f Hz (%+.2f cents)".format(expected, actual, off), abs(off) <= tolerance)
    }
}
