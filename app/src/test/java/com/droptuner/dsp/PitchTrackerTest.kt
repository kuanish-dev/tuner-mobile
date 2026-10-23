package com.droptuner.dsp

import com.droptuner.dsp.Signals.plus
import com.droptuner.dsp.Signals.times
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
    fun `input below the gate is ignored`() {
        assertNull(tracker.process(tone(110.0, amplitude = 0.00005))) // about −89 dBFS
    }

    @Test
    fun `an unplugged guitar at arm's length is heard`() {
        // About −66 dBFS at the mic over a −91 dBFS room: what a phone hears from an unplugged electric.
        val (pluck, f) = Signals.pluck(110.0, sampleRate, frameSize)
        val quiet = pluck * (0.0005 / rms(pluck)) + Signals.noise(frameSize, 0.00005)
        assertTrue(levelDb(quiet) in -70.0..-62.0)
        assertEquals(0.0, cents(tracker.process(quiet)!!, f), 1.0)
    }

    @Test
    fun `a note barely above the room settles within a couple of cents`() {
        // ~6 dB SNR: single frames scatter by several cents, the average pulls them in.
        val f = 146.83
        val readings = (1..40).map { seed ->
            tracker.process(Signals.sine(f, sampleRate, frameSize, 0.1) + Signals.noise(frameSize, 0.06, seed))!!
        }
        val settled = readings.drop(20).map { cents(it, f) }
        assertTrue("settled readings $settled", settled.all { abs(it) < 2.0 })
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
