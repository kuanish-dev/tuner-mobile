package com.droptuner.dsp

import com.droptuner.dsp.Signals.plus
import com.droptuner.dsp.Signals.times
import com.droptuner.music.cents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

class PitchTrackerTest {
    private val sampleRate = 48_000
    private val frameSize = 4096
    private val tracker = PitchTracker(YinPitchDetector(sampleRate, frameSize))

    private fun tone(f: Double, amplitude: Double = 0.5) = Signals.sine(f, sampleRate, frameSize, amplitude)

    /** Feeds [frames] and returns the last output. */
    private fun feed(frames: Int, frame: (Int) -> FloatArray): Double? {
        var last: Double? = null
        repeat(frames) { last = tracker.process(frame(it)) }
        return last
    }

    @Test
    fun `input below the gate is ignored`() {
        assertNull(feed(5) { tone(110.0, amplitude = 0.00005) }) // about −89 dBFS
    }

    @Test
    fun `a note is reported once three frames agree`() {
        assertNull(tracker.process(tone(110.0)))
        assertNull(tracker.process(tone(110.0)))
        assertEquals(110.0, tracker.process(tone(110.0))!!, 0.01)
    }

    @Test
    fun `junk that looks periodic at a different pitch every frame never turns into a note`() {
        // What a hum, a fan or a mic path's digital residue near the gate looks like to the detector:
        // each frame seems to have a pitch, just never the same one twice.
        val random = Random(5)
        val frames = (1..300).map {
            val f = 40.0 * 2.0.pow(random.nextDouble() * 4.5) // anywhere from 40 Hz to ~900 Hz
            Signals.sine(f, sampleRate, frameSize, 0.0006) + Signals.noise(frameSize, 0.0003, it)
        }
        val rawHits = frames.count { YinPitchDetector(sampleRate, frameSize).detect(it) != null }
        assertTrue("the junk should fool the bare detector, got $rawHits/300", rawHits > 150)
        val outputs = frames.mapNotNull { tracker.process(it) }
        assertTrue("phantom notes: $outputs", outputs.isEmpty())
    }

    @Test
    fun `an unplugged guitar at arm's length is heard`() {
        // About −66 dBFS at the mic over a −91 dBFS room: what a phone hears from an unplugged electric.
        val (pluck, f) = Signals.pluck(110.0, sampleRate, frameSize)
        val quiet = pluck * (0.0005 / rms(pluck)) + Signals.noise(frameSize, 0.00005)
        assertTrue(levelDb(quiet) in -70.0..-62.0)
        assertEquals(0.0, cents(feed(3) { quiet }!!, f), 1.0)
    }

    @Test
    fun `a held note settles to a fraction of a cent`() {
        // Noisy room (~21 dB SNR): single frames scatter by a cent or more.
        val f = 110.0
        val readings = (1..40).mapNotNull { seed ->
            tracker.process(Signals.sine(f, sampleRate, frameSize, 0.3) + Signals.noise(frameSize, 0.03, seed))
        }
        val settled = readings.drop(18).map { cents(it, f) }
        assertTrue("settled readings $settled", settled.size >= 18 && settled.all { abs(it) < 0.3 })
    }

    @Test
    fun `a note barely above the room settles within a couple of cents`() {
        // ~6 dB SNR: single frames scatter by several cents, the average pulls them in.
        val f = 146.83
        val readings = (1..40).mapNotNull { seed ->
            tracker.process(Signals.sine(f, sampleRate, frameSize, 0.1) + Signals.noise(frameSize, 0.06, seed))
        }
        val settled = readings.drop(18).map { cents(it, f) }
        assertTrue("settled readings $settled", settled.size >= 18 && settled.all { abs(it) < 2.0 })
    }

    @Test
    fun `a single glitch does not move the reading`() {
        feed(5) { tone(110.0) }
        val glitch = tracker.process(tone(220.0))
        assertTrue("glitch gave $glitch", glitch == null || abs(glitch - 110.0) < 0.1)
        assertEquals(110.0, feed(3) { tone(110.0) }!!, 0.1)
    }

    @Test
    fun `a new note after a pause is reported within three frames`() {
        feed(5) { tone(110.0) }
        repeat(3) { assertNull(tracker.process(FloatArray(frameSize))) }
        assertEquals(220.0, feed(3) { tone(220.0) }!!, 0.1)
    }

    @Test
    fun `a jump to a new pitch drops the old average`() {
        feed(20) { tone(110.0) }
        // A semitone up: once three frames agree (and the median flips) the reading is the new pitch, not a blend.
        assertEquals(116.54, feed(3) { tone(116.54) }!!, 0.01)
    }
}
