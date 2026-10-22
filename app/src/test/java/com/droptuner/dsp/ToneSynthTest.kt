package com.droptuner.dsp

import com.droptuner.music.Note
import com.droptuner.music.cents
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ToneSynthTest {
    private val sampleRate = 48_000

    @Test
    fun `reference notes are exact`() {
        val yin = YinPitchDetector(sampleRate, 4096)
        for (name in listOf("C#1", "A1", "D2", "E2", "A2", "D3", "G3", "B3", "E4")) {
            val f = Note.parse(name).frequency()
            val tone = ToneSynth.pluck(f, sampleRate, 1.0)
            // A frame from the middle, past the attack.
            val frame = tone.copyOfRange(sampleRate / 4, sampleRate / 4 + 4096)
            val heard = requireNotNull(yin.detect(frame)) { "no pitch in $name" }.frequency
            assertTrue("$name: ${cents(heard, f)} cents", abs(cents(heard, f)) < 0.2)
        }
    }

    @Test
    fun `stays within the speaker's range`() {
        val tone = ToneSynth.pluck(82.41, sampleRate, 1.0)
        assertTrue(tone.all { abs(it) <= 0.8f + 1e-6f })
    }
}
