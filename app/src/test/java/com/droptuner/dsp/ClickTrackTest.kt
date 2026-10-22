package com.droptuner.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ClickTrackTest {
    private val sampleRate = 48_000

    private fun beats(track: ClickTrack, seconds: Int): List<ClickTrack.Beat> {
        val chunk = FloatArray(256)
        return (0 until seconds * sampleRate / chunk.size).flatMap { track.fill(chunk) }
    }

    @Test
    fun `no drift over ten minutes at an awkward tempo`() {
        // 47 bpm = 61276.6 samples per beat: rounding each beat would drift, carrying the fraction doesn't.
        val track = ClickTrack(sampleRate).apply { bpm = 47 }
        val beats = beats(track, 600)
        val interval = sampleRate * 60.0 / 47
        beats.forEachIndexed { k, beat -> assertTrue("beat $k at ${beat.frame}", abs(beat.frame - k * interval) <= 1.0) }
        assertEquals(470, beats.size)
    }

    @Test
    fun `accent on the first beat of every bar`() {
        val track = ClickTrack(sampleRate).apply {
            bpm = 240
            beatsPerBar = 3
        }
        assertEquals(listOf(0, 1, 2, 0, 1, 2, 0, 1), beats(track, 2).map { it.index })
    }

    @Test
    fun `fewer beats per bar takes effect without a stray beat`() {
        val track = ClickTrack(sampleRate).apply { bpm = 240 }
        val chunk = FloatArray(sampleRate * 3 / 4) // three beats
        track.fill(chunk)
        track.beatsPerBar = 2
        assertEquals(listOf(0, 1, 0), track.fill(chunk).map { it.index })
    }
}
