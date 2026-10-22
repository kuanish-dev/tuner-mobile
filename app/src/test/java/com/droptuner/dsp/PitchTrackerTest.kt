package com.droptuner.dsp

import com.droptuner.dsp.Signals.plus
import com.droptuner.music.cents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PitchTrackerTest {
    private val sampleRate = 48_000
    private val frameSize = 4096
    private val tracker = PitchTracker(YinPitchDetector(sampleRate, frameSize))

    private fun tone(f: Double, amplitude: Double = 0.5) = Signals.sine(f, sampleRate, frameSize, amplitude)

    @Test
    fun `quiet input is ignored`() {
        assertNull(tracker.process(tone(110.0, amplitude = 0.001)))
    }

    @Test
    fun `a single glitch does not move the reading`() {
        repeat(3) { tracker.process(tone(110.0)) }
        assertEquals(110.0, tracker.process(tone(220.0))!!, 0.1)
    }

    @Test
    fun `a new note after a pause is reported straight away`() {
        repeat(5) { tracker.process(tone(110.0)) }
        repeat(3) { assertNull(tracker.process(FloatArray(frameSize))) }
        assertEquals(220.0, tracker.process(tone(220.0))!!, 0.1)
    }

    @Test
    fun `a held note settles to a fraction of a cent`() {
        // Noisy room (~21 dB SNR): single frames scatter by a cent or more.
        val f = 110.0
        val readings = (1..40).map { seed ->
            tracker.process(Signals.sine(f, sampleRate, frameSize, 0.3) + Signals.noise(frameSize, 0.03, seed))!!
        }
        val settled = readings.drop(20).map { cents(it, f) }
        assertTrue("settled readings $settled", settled.all { abs(it) < 0.3 })
    }

    @Test
    fun `a jump to a new pitch drops the old average`() {
        repeat(20) { tracker.process(tone(110.0)) }
        // A semitone up: once the median flips (3 frames) the reading is the new pitch, not a blend.
        val readings = List(3) { tracker.process(tone(116.54))!! }
        assertEquals(116.54, readings.last(), 0.01)
    }
}
